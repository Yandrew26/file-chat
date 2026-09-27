package com.andrewproject.filechat.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.mapping.DenseVectorSimilarity;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkResponseItem;
import co.elastic.clients.elasticsearch.core.bulk.DeleteOperation;
import co.elastic.clients.elasticsearch.core.bulk.IndexOperation;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.Version;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import co.elastic.clients.util.ObjectBuilder;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.client.RestClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentMetadata;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingOptions;
import org.springframework.ai.model.EmbeddingUtils;
import org.springframework.ai.observation.conventions.VectorStoreProvider;
import org.springframework.ai.observation.conventions.VectorStoreSimilarityMetric;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.elasticsearch.ElasticsearchAiSearchFilterExpressionConverter;
import org.springframework.ai.vectorstore.elasticsearch.ElasticsearchVectorStoreOptions;
import org.springframework.ai.vectorstore.elasticsearch.SimilarityFunction;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionConverter;
import org.springframework.ai.vectorstore.observation.VectorStoreObservationContext;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.Assert;

@Slf4j
public class SelfElasticsearchVectorStore extends SelfAbstractObservationVectorStore implements InitializingBean {

    @Value("${spring.ai.vectorstore.elasticsearch.top-k}")
    private Integer topK;
    private static final Map<SimilarityFunction, VectorStoreSimilarityMetric> SIMILARITY_TYPE_MAPPING;
    private final ElasticsearchClient elasticsearchClient;
    private final ElasticsearchVectorStoreOptions options;
    private final FilterExpressionConverter filterExpressionConverter;
    private final boolean initializeSchema;

    protected SelfElasticsearchVectorStore(Builder builder) {
        super(builder);
        Assert.notNull(builder.restClient, "RestClient must not be null");
        this.initializeSchema = builder.initializeSchema;
        this.options = builder.options;
        this.filterExpressionConverter = builder.filterExpressionConverter;
        String version = Version.VERSION == null ? "Unknown" : Version.VERSION.toString();
        this.elasticsearchClient = (ElasticsearchClient)(new ElasticsearchClient(new RestClientTransport(builder.restClient, new JacksonJsonpMapper((new ObjectMapper()).configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false))))).withTransportOptions((t) -> t.addHeader("user-agent", "spring-ai elastic-java/" + version));
    }

    public void doAdd(List<Document> documents) {
        if (!this.indexExists()) {
            throw new IllegalArgumentException("Index not found");
        } else {
            BulkRequest.Builder bulkRequestBuilder = new BulkRequest.Builder();
            List<float[]> embeddings = this.embeddingModel.embed(documents, EmbeddingOptions.builder().build(), this.batchingStrategy);

            for(int i = 0; i < embeddings.size(); ++i) {
                Document document = (Document)documents.get(i);
                float[] embedding = (float[])embeddings.get(i);
                bulkRequestBuilder.operations((op) -> op.index((idx) -> ((IndexOperation.Builder)((IndexOperation.Builder)idx.index(this.options.getIndexName())).id(document.getId())).document(this.getDocument(document, embedding, this.options.getEmbeddingFieldName()))));
            }

            BulkResponse bulkRequest = this.bulkRequest(bulkRequestBuilder.build());
            if (bulkRequest.errors()) {
                for(BulkResponseItem bulkResponseItem : bulkRequest.items()) {
                    if (bulkResponseItem.error() != null) {
                        throw new IllegalStateException(bulkResponseItem.error().reason());
                    }
                }
            }

        }
    }

    public void doAddByConversationId(List<Document> documents, String conversationId) {
        if (!this.indexExists()) {
            throw new IllegalArgumentException("Index not found");
        } else {
            BulkRequest.Builder bulkRequestBuilder = new BulkRequest.Builder();
            List<float[]> embeddings = this.embeddingModel.embed(documents, EmbeddingOptions.builder().build(), this.batchingStrategy);

            for(int i = 0; i < embeddings.size(); ++i) {
                Document document = (Document)documents.get(i);
                float[] embedding = (float[])embeddings.get(i);
                bulkRequestBuilder.operations((op) -> op.index((idx) -> ((IndexOperation.Builder)((IndexOperation.Builder)idx.index(this.options.getIndexName())).id(document.getId())).document(this.getDocumentWithConversationId(document, embedding, this.options.getEmbeddingFieldName(), conversationId))));
            }

            BulkResponse bulkRequest = this.bulkRequest(bulkRequestBuilder.build());
            if (bulkRequest.errors()) {
                for(BulkResponseItem bulkResponseItem : bulkRequest.items()) {
                    if (bulkResponseItem.error() != null) {
                        throw new IllegalStateException(bulkResponseItem.error().reason());
                    }
                }
            }

        }
    }

    private Object getDocument(Document document, float[] embedding, String embeddingFieldName) {
        Assert.notNull(document.getText(), "document's text must not be null");
        return Map.of("id", document.getId(), "content", document.getText(), "metadata", document.getMetadata(), embeddingFieldName, embedding);
    }

    private Object getDocumentWithConversationId(Document document, float[] embedding, String embeddingFieldName, String conversationId) {
        Assert.notNull(document.getText(), "document's text must not be null");
        Map<String, Object> metadata = document.getMetadata();
        metadata.put("conversation_id", conversationId);
        return Map.of("id", document.getId(), "content", document.getText(), "metadata", metadata, embeddingFieldName, embedding);
    }

    public void doDelete(List<String> idList) {
        BulkRequest.Builder bulkRequestBuilder = new BulkRequest.Builder();
        if (!this.indexExists()) {
            throw new IllegalArgumentException("Index not found");
        } else {
            for(String id : idList) {
                bulkRequestBuilder.operations((op) -> op.delete((idx) -> (ObjectBuilder)((DeleteOperation.Builder)idx.index(this.options.getIndexName())).id(id)));
            }

            if (this.bulkRequest(bulkRequestBuilder.build()).errors()) {
                throw new IllegalStateException("Delete operation failed");
            }
        }
    }

    public void doDelete(Filter.Expression filterExpression) {
        if (!this.indexExists()) {
            throw new IllegalArgumentException("Index not found");
        } else {
            try {
                this.elasticsearchClient.deleteByQuery((d) -> d.index(this.options.getIndexName(), new String[0]).query((q) -> q.queryString((qs) -> qs.query(this.getElasticsearchQueryString(filterExpression)))));
            } catch (Exception e) {
                throw new IllegalStateException("Failed to delete documents by filter", e);
            }
        }
    }

    private BulkResponse bulkRequest(BulkRequest bulkRequest) {
        try {
            return this.elasticsearchClient.bulk(bulkRequest);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public List<Document> doSimilaritySearch(SearchRequest searchRequest) {
        Assert.notNull(searchRequest, "The search request must not be null.");

        try {
            float threshold = 0;
            if (this.options.getSimilarity().equals(SimilarityFunction.l2_norm)) {
                threshold = 1.0F - threshold;
            } else {
                threshold = (float) searchRequest.getSimilarityThreshold();
            }

            float[] vectors = this.embeddingModel.embed(searchRequest.getQuery());
            float finalThreshold = threshold;
            SearchResponse<Document> res = this.elasticsearchClient.search((sr) -> sr
                    .index(this.options.getIndexName())
                    .knn((knn) -> knn.queryVector(EmbeddingUtils.toList(vectors)).similarity(finalThreshold).k(searchRequest.getTopK()).field(this.options.getEmbeddingFieldName()).numCandidates((int)((double)1.5F * (double)searchRequest.getTopK())).filter((fl) -> fl.queryString((qs) -> qs.query(this.getElasticsearchQueryString(searchRequest.getFilterExpression())))))
                    .size(searchRequest.getTopK()), Document.class);
            return (List)res.hits().hits().stream().map(this::toDocument).collect(Collectors.toList());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public List<Document> doSimilaritySearchByConvId(SearchRequest searchRequest, String conversationId) {
        Assert.notNull(searchRequest, "The search request must not be null.");

        try {
            float threshold = 0;
            if (this.options.getSimilarity().equals(SimilarityFunction.l2_norm)) {
                threshold = 1.0F - threshold;
            } else {
                threshold = (float) searchRequest.getSimilarityThreshold();
            }

            float[] vectors = this.embeddingModel.embed(searchRequest.getQuery());
            float finalThreshold = threshold;
            List<Document> documents = getDocumentsByConvId(conversationId);
            if (documents.size() > searchRequest.getTopK()) {
                return documents;
            }
            SearchResponse<Document> res = this.elasticsearchClient.search((sr) -> sr
                    .index(this.options.getIndexName())
                    .query(q -> q.term(t -> t.field("conversation_id").value(v -> v.stringValue(conversationId))))
                    .knn((knn) -> knn.queryVector(EmbeddingUtils.toList(vectors)).similarity(finalThreshold).k(searchRequest.getTopK()).field(this.options.getEmbeddingFieldName()).numCandidates((int)((double)1.5F * (double)searchRequest.getTopK())).filter((fl) -> fl.queryString((qs) -> qs.query(this.getElasticsearchQueryString(searchRequest.getFilterExpression())))))
                    .size(searchRequest.getTopK()), Document.class);
            return (List)res.hits().hits().stream().map(this::toDocument).collect(Collectors.toList());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private List<Document> getDocumentsByConvId(String conversationId) throws IOException {
        SearchResponse<Document> response = this.elasticsearchClient.search(s -> s
                        .index(options.getIndexName())
                        .query(q -> q
                                .term(t -> t
                                        .field("conversation_id")
                                        .value(v -> v.stringValue(conversationId))
                                )
                        ),
                Document.class
        );
        return response.hits().hits().stream()
                .map(Hit::source)
                .filter(Objects::nonNull)
                .toList();
    }

    private String getElasticsearchQueryString(Filter.Expression filterExpression) {
        return Objects.isNull(filterExpression) ? "*" : this.filterExpressionConverter.convertExpression(filterExpression);
    }

    private Document toDocument(Hit<Document> hit) {
        Document document = (Document)hit.source();
        Document.Builder documentBuilder = document.mutate();
        if (hit.score() != null) {
            documentBuilder.metadata(DocumentMetadata.DISTANCE.value(), (double)1.0F - this.normalizeSimilarityScore(hit.score()));
            documentBuilder.score(this.normalizeSimilarityScore(hit.score()));
        }

        return documentBuilder.build();
    }

    private double normalizeSimilarityScore(double score) {
        switch (this.options.getSimilarity()) {
            case l2_norm -> {
                return (double)1.0F - Math.sqrt((double)1.0F / score - (double)1.0F);
            }
            default -> {
                return (double)2.0F * score - (double)1.0F;
            }
        }
    }

    public boolean indexExists() {
        try {
            return this.elasticsearchClient.indices().exists((ex) -> ex.index(this.options.getIndexName(), new String[0])).value();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void createIndexMapping() {
        try {
            this.elasticsearchClient.indices().create((cr) -> cr.index(this.options.getIndexName()).mappings((map) -> map.properties(this.options.getEmbeddingFieldName(), (p) -> p.denseVector((dv) -> dv.similarity(this.parseSimilarity(this.options.getSimilarity().toString())).dims(this.options.getDimensions())))));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private DenseVectorSimilarity parseSimilarity(String similarity) {
        for(DenseVectorSimilarity sim : DenseVectorSimilarity.values()) {
            if (sim.jsonValue().equalsIgnoreCase(similarity)) {
                return sim;
            }
        }

        throw new IllegalArgumentException("Unsupported similarity: " + similarity);
    }

    public void afterPropertiesSet() {
        if (this.initializeSchema) {
            if (!this.indexExists()) {
                this.createIndexMapping();
            }

        }
    }

    public VectorStoreObservationContext.Builder createObservationContextBuilder(String operationName) {
        return VectorStoreObservationContext.builder(VectorStoreProvider.ELASTICSEARCH.value(), operationName).collectionName(this.options.getIndexName()).dimensions(this.embeddingModel.dimensions()).similarityMetric(this.getSimilarityMetric());
    }

    private String getSimilarityMetric() {
        return !SIMILARITY_TYPE_MAPPING.containsKey(this.options.getSimilarity()) ? this.options.getSimilarity().name() : ((VectorStoreSimilarityMetric)SIMILARITY_TYPE_MAPPING.get(this.options.getSimilarity())).value();
    }

    public <T> Optional<T> getNativeClient() {
        T client = (T)this.elasticsearchClient;
        return Optional.of(client);
    }

    public static Builder builder(RestClient restClient, EmbeddingModel embeddingModel) {
        return new Builder(restClient, embeddingModel);
    }

    static {
        SIMILARITY_TYPE_MAPPING = Map.of(SimilarityFunction.cosine, VectorStoreSimilarityMetric.COSINE, SimilarityFunction.l2_norm, VectorStoreSimilarityMetric.EUCLIDEAN, SimilarityFunction.dot_product, VectorStoreSimilarityMetric.DOT);
    }

    public static class Builder extends SelfAbstractVectorStoreBuilder<Builder> {
        private final RestClient restClient;
        private ElasticsearchVectorStoreOptions options = new ElasticsearchVectorStoreOptions();
        private boolean initializeSchema = false;
        private FilterExpressionConverter filterExpressionConverter = new ElasticsearchAiSearchFilterExpressionConverter();

        public Builder(RestClient restClient, EmbeddingModel embeddingModel) {
            super(embeddingModel);
            Assert.notNull(restClient, "RestClient must not be null");
            this.restClient = restClient;
        }

        public Builder options(ElasticsearchVectorStoreOptions options) {
            Assert.notNull(options, "options must not be null");
            this.options = options;
            return this;
        }

        public Builder initializeSchema(boolean initializeSchema) {
            this.initializeSchema = initializeSchema;
            return this;
        }

        public Builder filterExpressionConverter(FilterExpressionConverter converter) {
            Assert.notNull(converter, "filterExpressionConverter must not be null");
            this.filterExpressionConverter = converter;
            return this;
        }

        public SelfElasticsearchVectorStore build() {
            return new SelfElasticsearchVectorStore(this);
        }
    }
}



