package com.andrewproject.filechat.config;

import org.elasticsearch.client.RestClient;
import org.springframework.ai.vectorstore.elasticsearch.autoconfigure.ElasticsearchVectorStoreProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.elasticsearch.ElasticsearchVectorStoreOptions;
import org.springframework.context.annotation.Primary;

@Configuration
public class SelfElasticsearchVectorStoreConfig {

    @Bean
    @Primary
    public SelfElasticsearchVectorStore selfElasticsearchVectorStore(
            RestClient restClient,
            EmbeddingModel embeddingModel,
            ElasticsearchVectorStoreProperties properties) {

        ElasticsearchVectorStoreOptions options = new ElasticsearchVectorStoreOptions();
        options.setIndexName(properties.getIndexName());
        options.setDimensions(properties.getDimensions() != null ? properties.getDimensions() : 1536);
        options.setEmbeddingFieldName(properties.getEmbeddingFieldName() != null ? properties.getEmbeddingFieldName() : "embedding");
        // set other options if needed on options

        // Build your SelfElasticsearchVectorStore using available constructor or builder.
        // Adjust this call to match the actual SelfElasticsearchVectorStore API in your project.
        return SelfElasticsearchVectorStore
                .builder(restClient, embeddingModel)
                .options(options)
                .initializeSchema(true)
                .build();
    }
}
