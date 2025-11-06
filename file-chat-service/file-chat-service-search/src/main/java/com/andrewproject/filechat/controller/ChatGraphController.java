package com.andrewproject.filechat.controller;

import com.alibaba.cloud.ai.graph.*;
import com.alibaba.cloud.ai.graph.async.AsyncGenerator;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import com.andrewproject.filechat.config.graph.GraphProcess;
import com.andrewproject.filechat.feign.UploadFeign;
import com.andrewproject.filechat.service.SystemUserService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/graph")
public class ChatGraphController {

    @Resource
    private UploadFeign uploadFeign;

    @Resource
    private SystemUserService systemUserService;

    private final CompiledGraph compiledGraph;

    public ChatGraphController(@Qualifier("parallelStreamGraph")StateGraph stateGraph) throws GraphStateException {
        this.compiledGraph = stateGraph.compile();
    }

    @GetMapping("/create-chat")
    public ResponseEntity<String> create(@RequestParam("userId") String userId,
                                         @RequestBody byte[] file) {
        StringBuilder resultConversationId = new StringBuilder();
        String sessionId = UUID.randomUUID().toString().replaceAll("-", "");
        String conversationId = resultConversationId.append(userId).append("_").append(sessionId).toString();

        ResponseEntity<String> stringResponseEntity = uploadFeign.pdfUpload(file, conversationId);
        log.info(stringResponseEntity.toString());

        return ResponseEntity.ok("chat started successfully at conversation_id: " + conversationId);
    }

    @GetMapping("/rag")
    public ResponseEntity<Map<String, Object>> chatRag(@RequestParam("message") String message,
                                                       @RequestParam("conversationId") String conversationId) throws GraphRunnerException {
            log.info("start chat");

            String userId = conversationId.split("_")[0];
            String userName = systemUserService.getUserByUserId(userId).getUserName();
            String traceId = UUID.randomUUID().toString().replaceAll("-", "");
            List<String> elasticsearchList = uploadFeign.searchString(message, conversationId);

            Map<String, Object> objectMap = setupGraph(message, conversationId, traceId, userId, userName, elasticsearchList);

            OverAllState result = compiledGraph.invoke(objectMap).get();

            return ResponseEntity.ok(result.data());
    }

    @GetMapping(value = "/rag/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> chatRagStream(@RequestParam("message") String message,
                                                       @RequestParam("conversationId") String conversationId) throws GraphRunnerException {
        log.info("start chat stream");

        String userId = conversationId.split("_")[0];
        String userName = systemUserService.getUserByUserId(userId).getUserName();
        String traceId = UUID.randomUUID().toString().replaceAll("-", "");
        List<String> elasticsearchList = uploadFeign.searchString(message, conversationId);
        RunnableConfig runnableConfig = RunnableConfig.builder().threadId(traceId).build();

        Map<String, Object> objectMap = setupGraph(message, conversationId, traceId, userId, userName, elasticsearchList);

        GraphProcess graphProcess = new GraphProcess(compiledGraph);
        Sinks.Many<ServerSentEvent<String>> sink = Sinks.many().unicast().onBackpressureBuffer();
        AsyncGenerator<NodeOutput> resultFuture = compiledGraph.stream(objectMap, runnableConfig);
        graphProcess.processStream(resultFuture, sink);

        return sink.asFlux()
                .doOnCancel(() -> log.info("Client disconnected from stream"))
                .doOnError(e -> log.info("Error occurred during streaming", e));
    }

    private Map<String, Object> setupGraph(String message, String conversationId, String traceId, String userId, String userName, List<String> elasticsearchList) {
        Map<String, Object> objectMap = new HashMap<>();
        objectMap.put("message", message);
        objectMap.put("conversationId", conversationId);
        objectMap.put("traceId", traceId);
        objectMap.put("userId", userId);
        objectMap.put("userName", userName);
        objectMap.put("elasticsearch_list", elasticsearchList);
        return objectMap;
    }
}
