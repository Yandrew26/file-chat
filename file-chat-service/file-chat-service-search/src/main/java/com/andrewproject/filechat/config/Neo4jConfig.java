package com.andrewproject.filechat.config;

import org.neo4j.driver.Driver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.neo4j.core.DatabaseSelectionProvider;
import org.springframework.data.neo4j.core.transaction.Neo4jTransactionManager;
import org.springframework.data.neo4j.repository.config.EnableNeo4jRepositories;
import org.springframework.jdbc.support.JdbcTransactionManager;

import javax.sql.DataSource;

/**
 * This service uses two stores: MySQL (MyBatis) and Neo4j (the Author/Ebook catalog). Spring Boot configures only
 * one transaction manager, and with Spring Data Neo4j on the classpath that could be the Neo4j one, silently taking
 * over @Transactional for MySQL. Both are declared here: JDBC is the default, and the Neo4j repositories use theirs.
 */
@Configuration
@EnableNeo4jRepositories(basePackages = "com.andrewproject.filechat.repository", transactionManagerRef = "neo4jTransactionManager")
public class Neo4jConfig {

    @Bean
    @Primary
    public JdbcTransactionManager transactionManager(DataSource dataSource) {
        return new JdbcTransactionManager(dataSource);
    }

    @Bean
    public Neo4jTransactionManager neo4jTransactionManager(Driver driver, DatabaseSelectionProvider databaseSelectionProvider) {
        return Neo4jTransactionManager.with(driver).withDatabaseSelectionProvider(databaseSelectionProvider).build();
    }
}
