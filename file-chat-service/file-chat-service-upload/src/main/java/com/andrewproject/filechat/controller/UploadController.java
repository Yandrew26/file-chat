package com.andrewproject.filechat.controller;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.indices.CreateIndexResponse;
import com.andrewproject.filechat.config.SelfElasticsearchVectorStore;
import com.andrewproject.filechat.service.UploadService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.elasticsearch.autoconfigure.ElasticsearchVectorStoreProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/upload")
public class UploadController {

    @Value("${spring.ai.vectorstore.elasticsearch.top-k}")
    private Integer topK;

    private static final String textField = "content";

    private static final String vectorField = "embedding";

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
    public ResponseEntity<String> pdfUpload(@RequestBody byte[] file,
                                            @RequestParam("conversationId") String conversationId) {
        ByteArrayResource resource = new ByteArrayResource(file);
        List<Document> pdfDocuments = uploadService.pdfUpload(resource);
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
                .filterExpression("conversation_id == '" + conversationId + "'")
                .build());
        return ResponseEntity.ok(search);
    }

    @GetMapping("/search/string")
    public List<String> searchString(@RequestParam("query") String query,
                                     @RequestParam("conversationId") String conversationId) {
        log.info("search begin");
        List<Document> search = selfElasticsearchVectorStore.similaritySearch(SearchRequest
                .builder()
                .query(query)
                .topK(5)
                .filterExpression("conversation_id == '" + conversationId + "'")
                .build());
        return search.stream().map(Document::getText).toList();
    }

    @GetMapping("/search/author")
    public List<String> searchAuthor(@RequestParam("conversationId") String conversationId) {
        log.info("author search begin");
        List<Document> search = selfElasticsearchVectorStore.similaritySearch(SearchRequest
                .builder()
                .query("who is the author of the document?")
                .topK(1)
                .filterExpression("conversation_id == '" + conversationId + "'")
                .build());
        return search.stream().map(Document::getText).toList();
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
