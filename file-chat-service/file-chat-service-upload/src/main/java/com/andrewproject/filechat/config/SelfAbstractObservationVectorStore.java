package com.andrewproject.filechat.config;

import io.micrometer.observation.ObservationRegistry;
import java.util.List;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.BatchingStrategy;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.AbstractVectorStoreBuilder;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.observation.DefaultVectorStoreObservationConvention;
import org.springframework.ai.vectorstore.observation.VectorStoreObservationContext;
import org.springframework.ai.vectorstore.observation.VectorStoreObservationContext.Operation;
import org.springframework.ai.vectorstore.observation.VectorStoreObservationConvention;
import org.springframework.ai.vectorstore.observation.VectorStoreObservationDocumentation;
import org.springframework.lang.Nullable;

public abstract class SelfAbstractObservationVectorStore implements SelfVectorStore {
    private static final VectorStoreObservationConvention DEFAULT_OBSERVATION_CONVENTION = new DefaultVectorStoreObservationConvention();
    private final ObservationRegistry observationRegistry;
    @Nullable
    private final VectorStoreObservationConvention customObservationConvention;
    protected final EmbeddingModel embeddingModel;
    protected final BatchingStrategy batchingStrategy;

    private SelfAbstractObservationVectorStore(EmbeddingModel embeddingModel, ObservationRegistry observationRegistry, @Nullable VectorStoreObservationConvention customObservationConvention, BatchingStrategy batchingStrategy) {
        this.embeddingModel = embeddingModel;
        this.observationRegistry = observationRegistry;
        this.customObservationConvention = customObservationConvention;
        this.batchingStrategy = batchingStrategy;
    }

    public SelfAbstractObservationVectorStore(SelfAbstractVectorStoreBuilder<?> builder) {
        this(builder.getEmbeddingModel(), builder.getObservationRegistry(), builder.getCustomObservationConvention(), builder.getBatchingStrategy());
    }

    public void add(List<Document> documents) {
        this.validateNonTextDocuments(documents);
        VectorStoreObservationContext observationContext = this.createObservationContextBuilder(Operation.ADD.value()).build();
        VectorStoreObservationDocumentation.AI_VECTOR_STORE.observation(this.customObservationConvention, DEFAULT_OBSERVATION_CONVENTION, () -> observationContext, this.observationRegistry).observe(() -> this.doAdd(documents));
    }

    public void addByConversationId(List<Document> documents, String conversationId) {
        this.validateNonTextDocuments(documents);
        VectorStoreObservationContext observationContext = this.createObservationContextBuilder(Operation.ADD.value()).build();
        VectorStoreObservationDocumentation.AI_VECTOR_STORE.observation(this.customObservationConvention, DEFAULT_OBSERVATION_CONVENTION, () -> observationContext, this.observationRegistry).observe(() -> this.doAddByConversationId(documents, conversationId));
    }

    private void validateNonTextDocuments(List<Document> documents) {
        if (documents != null) {
            for(Document document : documents) {
                if (document != null && !document.isText()) {
                    throw new IllegalArgumentException("Only text documents are supported for now. One of the documents contains non-text content.");
                }
            }

        }
    }

    public void delete(List<String> deleteDocIds) {
        VectorStoreObservationContext observationContext = this.createObservationContextBuilder(Operation.DELETE.value()).build();
        VectorStoreObservationDocumentation.AI_VECTOR_STORE.observation(this.customObservationConvention, DEFAULT_OBSERVATION_CONVENTION, () -> observationContext, this.observationRegistry).observe(() -> this.doDelete(deleteDocIds));
    }

    public void delete(Filter.Expression filterExpression) {
        VectorStoreObservationContext observationContext = this.createObservationContextBuilder(Operation.DELETE.value()).build();
        VectorStoreObservationDocumentation.AI_VECTOR_STORE.observation(this.customObservationConvention, DEFAULT_OBSERVATION_CONVENTION, () -> observationContext, this.observationRegistry).observe(() -> this.doDelete(filterExpression));
    }

    public List<Document> similaritySearch(SearchRequest request) {
        VectorStoreObservationContext searchObservationContext = this.createObservationContextBuilder(Operation.QUERY.value()).queryRequest(request).build();
        return (List)VectorStoreObservationDocumentation.AI_VECTOR_STORE.observation(this.customObservationConvention, DEFAULT_OBSERVATION_CONVENTION, () -> searchObservationContext, this.observationRegistry).observe(() -> {
            List<Document> documents = this.doSimilaritySearch(request);
            searchObservationContext.setQueryResponse(documents);
            return documents;
        });
    }

    public List<Document> similaritySearchByConvId(SearchRequest request, String conversationId) {
        VectorStoreObservationContext searchObservationContext = this.createObservationContextBuilder(Operation.QUERY.value()).queryRequest(request).build();
        return (List)VectorStoreObservationDocumentation.AI_VECTOR_STORE.observation(this.customObservationConvention, DEFAULT_OBSERVATION_CONVENTION, () -> searchObservationContext, this.observationRegistry).observe(() -> {
            List<Document> documents = this.doSimilaritySearchByConvId(request, conversationId);
            searchObservationContext.setQueryResponse(documents);
            return documents;
        });
    }

    public abstract void doAdd(List<Document> documents);

    public abstract void doAddByConversationId(List<Document> documents, String conversationId);

    public abstract void doDelete(List<String> idList);

    protected void doDelete(Filter.Expression filterExpression) {
        throw new UnsupportedOperationException();
    }

    public abstract List<Document> doSimilaritySearch(SearchRequest request);

    public abstract List<Document> doSimilaritySearchByConvId(SearchRequest request, String conversationId);

    public abstract VectorStoreObservationContext.Builder createObservationContextBuilder(String operationName);
}

