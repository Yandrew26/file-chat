package com.andrewproject.filechat.config;

import io.micrometer.observation.ObservationRegistry;
import org.springframework.ai.embedding.BatchingStrategy;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.TokenCountBatchingStrategy;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.observation.VectorStoreObservationConvention;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

public abstract class SelfAbstractVectorStoreBuilder<T extends SelfAbstractVectorStoreBuilder<T>> implements SelfVectorStore.Builder<T> {
    protected final EmbeddingModel embeddingModel;
    protected ObservationRegistry observationRegistry;
    @Nullable
    protected VectorStoreObservationConvention customObservationConvention;
    protected BatchingStrategy batchingStrategy;

    public SelfAbstractVectorStoreBuilder(EmbeddingModel embeddingModel) {
        this.observationRegistry = ObservationRegistry.NOOP;
        this.batchingStrategy = new TokenCountBatchingStrategy();
        Assert.notNull(embeddingModel, "EmbeddingModel must be configured");
        this.embeddingModel = embeddingModel;
    }

    public EmbeddingModel getEmbeddingModel() {
        return this.embeddingModel;
    }

    public BatchingStrategy getBatchingStrategy() {
        return this.batchingStrategy;
    }

    public ObservationRegistry getObservationRegistry() {
        return this.observationRegistry;
    }

    @Nullable
    public VectorStoreObservationConvention getCustomObservationConvention() {
        return this.customObservationConvention;
    }

    protected T self() {
        return (T)this;
    }

    public T observationRegistry(ObservationRegistry observationRegistry) {
        Assert.notNull(observationRegistry, "ObservationRegistry must not be null");
        this.observationRegistry = observationRegistry;
        return (T)this.self();
    }

    public T customObservationConvention(@Nullable VectorStoreObservationConvention convention) {
        this.customObservationConvention = convention;
        return (T)this.self();
    }

    public T batchingStrategy(BatchingStrategy batchingStrategy) {
        Assert.notNull(batchingStrategy, "BatchingStrategy must not be null");
        this.batchingStrategy = batchingStrategy;
        return (T)this.self();
    }
}

