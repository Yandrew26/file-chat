package com.andrewproject.filechat.controller;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.mapping.*;
import co.elastic.clients.elasticsearch.indices.CreateIndexResponse;
import co.elastic.clients.elasticsearch.indices.IndexSettings;
import com.andrewproject.filechat.service.UploadService;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.elasticsearch.autoconfigure.ElasticsearchVectorStoreProperties;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/upload")
public class UploadController {

    private static final Logger logger = LoggerFactory.getLogger(UploadController.class);

    private static final String textField = "content";

    private static final String vectorField = "embedding";

    @Resource
    private UploadService uploadService;

    private final VectorStore vectorStore;

    private final ElasticsearchClient elasticsearchClient;

    private final ElasticsearchVectorStoreProperties options;

    public UploadController(VectorStore vectorStore, ElasticsearchVectorStoreProperties options,
                            ElasticsearchClient elasticsearchClient) {
        this.vectorStore = vectorStore;
        this.options = options;
        this.elasticsearchClient = elasticsearchClient;
    }

    @GetMapping("/pdf")
    public ResponseEntity<String> pdfUpload(@RequestBody byte[] file) {
        ByteArrayResource resource = new ByteArrayResource(file);
        List<Document> pdfDocuments = uploadService.pdfUpload(resource);
        createIndexIfNotExists();
        vectorStore.add(pdfDocuments);
        return ResponseEntity.ok("File uploaded and processed successfully.");
    }

    @GetMapping("/search")
    public ResponseEntity<List<Document>> search(@RequestParam("query") String query) {
        List<Document> search = vectorStore.similaritySearch(SearchRequest
                .builder()
                .query(query)
                .topK(3)
                .build());
        return ResponseEntity.ok(search);
    }

    private void createIndexIfNotExists() {
        try {
            String indexName = options.getIndexName();
            Integer dimsLength = options.getDimensions();

            if (StringUtils.isBlank(indexName)) {
                throw new IllegalArgumentException("Elastic search index name must be provided");
            }

            boolean exists = elasticsearchClient.indices().exists(idx -> idx.index(indexName)).value();
            if (exists) {
                logger.debug("Index {} already exists. Skipping creation.", indexName);
                return;
            }

            String similarityAlgo = options.getSimilarity().name();
            IndexSettings indexSettings = IndexSettings
                    .of(settings -> settings.numberOfShards(String.valueOf(1)).numberOfReplicas(String.valueOf(1)));

            // Maybe using json directly?
            Map<String, Property> properties = new HashMap<>();
            properties.put(vectorField, Property.of(property -> property.denseVector(
                    DenseVectorProperty.of(dense -> dense.index(true).dims(dimsLength).similarity(DenseVectorSimilarity.valueOf(similarityAlgo))))));
            properties.put(textField, Property.of(property -> property.text(TextProperty.of(t -> t))));

            Map<String, Property> metadata = new HashMap<>();
            metadata.put("ref_doc_id", Property.of(property -> property.keyword(KeywordProperty.of(k -> k))));

            properties.put("metadata",
                    Property.of(property -> property.object(ObjectProperty.of(op -> op.properties(metadata)))));

            CreateIndexResponse indexResponse = elasticsearchClient.indices()
                    .create(createIndexBuilder -> createIndexBuilder.index(indexName)
                            .settings(indexSettings)
                            .mappings(TypeMapping.of(mappings -> mappings.properties(properties))));

            if (!indexResponse.acknowledged()) {
                throw new RuntimeException("failed to create index");
            }

            logger.info("create elasticsearch index {} successfully", indexName);
        }
        catch (IOException e) {
            logger.error("failed to create index", e);
            throw new RuntimeException(e);
        }
    }
}
