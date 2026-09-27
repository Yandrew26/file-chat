package com.andrewproject.filechat.controller;

import com.alibaba.cloud.ai.graph.*;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import com.andrewproject.filechat.config.graph.GraphProcess;
import com.andrewproject.filechat.dto.AskRequest;
import com.andrewproject.filechat.dto.PassageDTO;
import com.andrewproject.filechat.dto.SystemUserDTO;
import com.andrewproject.filechat.feign.UploadFeign;
import com.andrewproject.filechat.node.ChatNode;
import com.andrewproject.filechat.node.MergeNode;
import com.andrewproject.filechat.node.PromptTemplateNode;
import com.andrewproject.filechat.service.SystemUserService;
import feign.FeignException;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;

@Slf4j
@RestController
@RequestMapping("/graph")
public class ChatGraphController {

    // conversationId is "<userId>_<sessionId>", so a user id must not contain an underscore
    private static final Pattern USER_ID_PATTERN = Pattern.compile("[A-Za-z0-9-]{1,64}");

    private static final Pattern CONVERSATION_ID_PATTERN = Pattern.compile("[A-Za-z0-9-]{1,64}_[A-Za-z0-9]{1,64}");

    public static final int MAX_MESSAGE_LENGTH = 4000;

    private static final byte[] PDF_SIGNATURE = "%PDF-".getBytes(StandardCharsets.US_ASCII);

    @Resource
    private UploadFeign uploadFeign;

    @Resource
    private SystemUserService systemUserService;

    private final CompiledGraph compiledGraph;

    public ChatGraphController(@Qualifier("parallelStreamGraph")StateGraph stateGraph) throws GraphStateException {
        this.compiledGraph = stateGraph.compile();
    }

    @PostMapping(value = "/create-chat", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> create(@RequestParam("userId") String userId,
                                                      @RequestParam("file") MultipartFile file) throws IOException {
        if (!USER_ID_PATTERN.matcher(userId).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid userId");
        }
        String sessionId = UUID.randomUUID().toString().replaceAll("-", "");
        String conversationId = userId + "_" + sessionId;

        String fileName = uploadPdf(file, conversationId);

        return ResponseEntity.ok(Map.of("conversationId", conversationId, "fileName", fileName));
    }

    @PostMapping(value = "/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> addDocument(@RequestParam("conversationId") String conversationId,
                                                           @RequestParam("file") MultipartFile file) throws IOException {
        validateConversationId(conversationId);
        String fileName = uploadPdf(file, conversationId);
        return ResponseEntity.ok(Map.of("conversationId", conversationId, "fileName", fileName));
    }

    @GetMapping("/search")
    public ResponseEntity<List<PassageDTO>> search(@RequestParam("query") String query,
                                                   @RequestParam("conversationId") String conversationId) {
        validateConversationId(conversationId);
        if (!StringUtils.hasText(query)) {
            return ResponseEntity.ok(List.of());
        }
        if (query.length() > MAX_MESSAGE_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "query is too long");
        }
        return ResponseEntity.ok(uploadFeign.searchPassages(query, conversationId));
    }

    /** Preferred over GET: long or non-ASCII questions overflow the gateway's 4 KB request-line limit. */
    @PostMapping(value = "/rag", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> chatRagPost(@RequestBody AskRequest request) {
        return chatRag(request.message(), request.conversationId());
    }

    @GetMapping("/rag")
    public ResponseEntity<Map<String, Object>> chatRag(@RequestParam("message") String message,
                                                       @RequestParam("conversationId") String conversationId) {
        log.info("start chat");

        validateQuestion(message, conversationId);
        String traceId = newTraceId();
        List<PassageDTO> passages = uploadFeign.searchPassages(message, conversationId);
        Map<String, Object> objectMap = setupGraph(message, conversationId, traceId, passages);

        OverAllState result = compiledGraph.invoke(objectMap)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Graph produced no result"));

        Object merge = result.value(MergeNode.NODE_CONTENT).orElse(null);
        Object answer = merge instanceof Map<?, ?> mergeResult ? mergeResult.get("chat_content") : "";

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("traceId", traceId);
        response.put("conversationId", conversationId);
        response.put("answer", answer);
        response.put("sources", passages);
        response.put("prompt", result.value(PromptTemplateNode.NODE_CONTENT).orElse(""));
        return ResponseEntity.ok(response);
    }

    @PostMapping(value = "/rag/stream", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> chatRagStreamPost(@RequestBody AskRequest request) {
        return chatRagStream(request.message(), request.conversationId());
    }

    @GetMapping(value = "/rag/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> chatRagStream(@RequestParam("message") String message,
                                                       @RequestParam("conversationId") String conversationId) {
        log.info("start chat stream");

        validateQuestion(message, conversationId);
        String traceId = newTraceId();
        List<PassageDTO> passages = uploadFeign.searchPassages(message, conversationId);
        Map<String, Object> objectMap = setupGraph(message, conversationId, traceId, passages);
        RunnableConfig runnableConfig = RunnableConfig.builder().threadId(traceId).build();

        return GraphProcess.toServerSentEvents(compiledGraph.stream(objectMap, runnableConfig), traceId, conversationId, passages)
                .doOnCancel(() -> log.info("Client disconnected from stream"))
                .doOnError(e -> log.info("Error occurred during streaming", e));
    }

    private static void validateQuestion(String message, String conversationId) {
        validateConversationId(conversationId);
        if (!StringUtils.hasText(message)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "message must not be empty");
        }
        if (message.length() > MAX_MESSAGE_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "message must be at most " + MAX_MESSAGE_LENGTH + " characters");
        }
    }

    private Map<String, Object> setupGraph(String message, String conversationId, String traceId, List<PassageDTO> passages) {
        String userId = conversationId.split("_")[0];
        SystemUserDTO user = systemUserService.getUserByUserId(userId);
        String userName = user == null ? userId : user.getUserName();

        Map<String, Object> objectMap = new HashMap<>();
        objectMap.put("message", message);
        objectMap.put("conversationId", conversationId);
        objectMap.put("traceId", traceId);
        objectMap.put("userId", userId);
        objectMap.put("userName", userName);
        objectMap.put("elasticsearch_list", passages.stream().map(PassageDTO::text).toList());
        return objectMap;
    }

    private String uploadPdf(MultipartFile file, String conversationId) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "file must not be empty");
        }
        String fileName = StringUtils.hasText(file.getOriginalFilename()) ? file.getOriginalFilename() : "document.pdf";
        if (!fileName.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Only PDF files are supported");
        }
        byte[] bytes = file.getBytes();
        if (bytes.length < PDF_SIGNATURE.length || !Arrays.equals(bytes, 0, PDF_SIGNATURE.length, PDF_SIGNATURE, 0, PDF_SIGNATURE.length)) {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "That file is not a valid PDF");
        }
        ResponseEntity<String> uploadResponse;
        try {
            uploadResponse = uploadFeign.pdfUpload(bytes, conversationId, fileName);
        } catch (FeignException e) {
            if (e.status() == HttpStatus.PAYLOAD_TOO_LARGE.value()) {
                throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "This PDF is too long to index. Try a shorter document.");
            }
            if (e.status() == HttpStatus.UNSUPPORTED_MEDIA_TYPE.value()) {
                throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "That file is not a valid PDF");
            }
            throw e;
        }
        log.info("conversationId:{} - uploaded {}: {}", conversationId, fileName, uploadResponse.getBody());
        return fileName;
    }

    private static void validateConversationId(String conversationId) {
        if (conversationId == null || !CONVERSATION_ID_PATTERN.matcher(conversationId).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid conversationId");
        }
    }

    private static String newTraceId() {
        return UUID.randomUUID().toString().replaceAll("-", "");
    }
}
