package com.andrewproject.filechat.controller;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.indices.CreateIndexResponse;
import com.andrewproject.filechat.config.SelfElasticsearchVectorStore;
import com.andrewproject.filechat.dto.PassageDTO;
import com.andrewproject.filechat.service.UploadService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.elasticsearch.autoconfigure.ElasticsearchVectorStoreProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import jakarta.servlet.http.HttpServletRequest;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

@Slf4j
@RestController
@RequestMapping("/upload")
public class UploadController {

    @Value("${spring.ai.vectorstore.elasticsearch.top-k}")
    private Integer topK;

    private static final String textField = "content";

    private static final String vectorField = "embedding";

    private static final int MAX_UPLOAD_BYTES = 50 * 1024 * 1024;

    private static final byte[] PDF_SIGNATURE = "%PDF-".getBytes(StandardCharsets.US_ASCII);

    private static final Pattern CONVERSATION_ID_PATTERN = Pattern.compile("[A-Za-z0-9_-]{1,128}");

    @Resource
    private UploadService uploadService;

    private final SelfElasticsearchVectorStore selfElasticsearchVectorStore;

    private final ElasticsearchClient elasticsearchClient;

    private final ElasticsearchVectorStoreProperties options;

    public UploadController(SelfElasticsearchVectorStore selfElasticsearchVectorStore, ElasticsearchVectorStoreProperties options,
                            ElasticsearchClient elasticsearchClient) {
        this.selfElasticsearchVectorStore = selfElasticsearchVectorStore;
        this.options = options;
        this.elasticsearchClient = elasticsearchClient;
    }

    @PostMapping("/pdf")
    public ResponseEntity<String> pdfUpload(HttpServletRequest request,
                                            @RequestParam("conversationId") String conversationId,
                                            @RequestParam(value = "fileName", required = false) String fileName) throws IOException {
        conversationFilter(conversationId);
        byte[] file = readPdfBody(request);
        ByteArrayResource resource = new ByteArrayResource(file);
        List<Document> pdfDocuments = uploadService.pdfUpload(resource);
        if (StringUtils.isNotBlank(fileName)) {
            pdfDocuments.forEach(document -> document.getMetadata().put("file_name", fileName));
        }
        createIndexIfNotExists();
        selfElasticsearchVectorStore.addByConversationId(pdfDocuments, conversationId);
        return ResponseEntity.ok("File uploaded and processed successfully.");
    }

    @GetMapping("/search")
    public ResponseEntity<List<Document>> search(@RequestParam("query") String query,
                                                 @RequestParam("conversationId") String conversationId) {
        List<Document> search = selfElasticsearchVectorStore.similaritySearch(SearchRequest
                .builder()
                .query(query)
                .topK(topK)
                .filterExpression(conversationFilter(conversationId))
                .build());
        return ResponseEntity.ok(search);
    }

    public record PassageSearchRequest(String query, String conversationId, Integer topK) {
    }

    /** POST variant: long or non-ASCII queries overflow Tomcat's 8 KB request-line limit on GET. */
    @PostMapping("/search/passages")
    public List<PassageDTO> searchPassagesPost(@RequestBody PassageSearchRequest request) {
        return searchPassages(request.query(), request.conversationId(), request.topK());
    }

    @GetMapping("/search/passages")
    public List<PassageDTO> searchPassages(@RequestParam("query") String query,
                                           @RequestParam("conversationId") String conversationId,
                                           @RequestParam(value = "topK", required = false) Integer requestedTopK) {
        int limit = requestedTopK == null ? topK : Math.min(Math.max(requestedTopK, 1), 20);
        List<Document> search = selfElasticsearchVectorStore.similaritySearch(SearchRequest
                .builder()
                .query(query)
                .topK(limit)
                .filterExpression(conversationFilter(conversationId))
                .build());
        return search.stream().map(PassageDTO::from).toList();
    }

    @GetMapping("/search/string")
    public List<String> searchString(@RequestParam("query") String query,
                                     @RequestParam("conversationId") String conversationId) {
        log.info("search begin");
        List<Document> search = selfElasticsearchVectorStore.similaritySearch(SearchRequest
                .builder()
                .query(query)
                .topK(5)
                .filterExpression(conversationFilter(conversationId))
                .build());
        return search.stream().map(Document::getText).toList();
    }

    /**
     * Reads the raw request body with a size cap (a plain {@code @RequestBody byte[]} has none, and this endpoint is
     * reachable through the OpenAPI gateway) and checks that it is a PDF.
     */
    private static byte[] readPdfBody(HttpServletRequest request) throws IOException {
        if (request.getContentLengthLong() > MAX_UPLOAD_BYTES) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "The file is larger than 50 MB");
        }
        byte[] body;
        try (InputStream in = request.getInputStream()) {
            body = in.readNBytes(MAX_UPLOAD_BYTES + 1);
        }
        if (body.length > MAX_UPLOAD_BYTES) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "The file is larger than 50 MB");
        }
        if (body.length < PDF_SIGNATURE.length || !Arrays.equals(body, 0, PDF_SIGNATURE.length, PDF_SIGNATURE, 0, PDF_SIGNATURE.length)) {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "The file is not a PDF");
        }
        return body;
    }

    /**
     * Builds the metadata filter for a conversation. The id is validated first because it is
     * interpolated into the filter expression.
     */
    private static String conversationFilter(String conversationId) {
        if (conversationId == null || !CONVERSATION_ID_PATTERN.matcher(conversationId).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid conversationId");
        }
        return "conversation_id == '" + conversationId + "'";
    }

    private void createIndexIfNotExists() {
        try {
            String indexName = options.getIndexName();
            Integer dimsLength = options.getDimensions();

            if (StringUtils.isBlank(indexName)) {
                throw new IllegalArgumentException("Elastic search index name must be provided");
            }

            boolean exists = selfElasticsearchVectorStore.indexExists();
            if (exists) {
                log.debug("Index {} already exists. Skipping creation.", indexName);
                return;
            }

            CreateIndexResponse indexResponse = elasticsearchClient.indices()
                    .create(createIndexBuilder -> createIndexBuilder.index(indexName));

            if (!indexResponse.acknowledged()) {
                throw new RuntimeException("failed to create index");
            }

            log.info("create elasticsearch index {} successfully", indexName);
        }
        catch (IOException e) {
            log.error("failed to create index", e);
            throw new RuntimeException(e);
        }
    }

//    public void syncEmbeddings() {
//        try {
//            String indexName = options.getIndexName();
//            var response = elasticsearchClient.search(s -> s.index(indexName).size(10_000), Map.class);
//            if (response.hits() == null) return;
//
//            for (Hit<Map> hit : response.hits().hits()) {
//                Map source = hit.source();
//                if (source == null) continue;
//
//                String content = source.get("content") != null ? source.get("content").toString() : null;
//                Object embeddingObj = source.get("embedding"); // dense vector as list
//                String embeddingJson = objectMapper.writeValueAsString(embeddingObj);
//
//                String docId = null;
//                Object metadataObj = source.get("metadata");
//                if (metadataObj instanceof Map) {
//                    Object ref = ((Map<?, ?>) metadataObj).get("ref_doc_id");
//                    if (ref != null) docId = ref.toString();
//                }
//
//                DocumentEmbedding e = new DocumentEmbedding();
//                e.setDocId(docId != null ? docId : hit.id());
//                e.setContent(content);
//                e.setEmbeddingJson(embeddingJson);
//
//                repository.save(e);
//            }
//        }
//        catch (IOException ex) {
//            throw new RuntimeException("Failed to sync embeddings from Elasticsearch", ex);
//        }
//    }
}
