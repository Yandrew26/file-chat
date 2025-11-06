package com.andrewproject.filechat.config.memory;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

@Configuration
@ConditionalOnProperty(
        prefix = "spring.ai.memory.self-mysql",
        name = {"enabled"},
        havingValue = "true",
        matchIfMissing = false
)
public class SelfMysqlMemoryConfig {

    @Value("${spring.ai.memory.self-mysql.jdbc-url}")
    private String mysqlJdbcUrl;
    @Value("${spring.ai.memory.self-mysql.username}")
    private String mysqlUsername;
    @Value("${spring.ai.memory.self-mysql.password}")
    private String mysqlPassword;
    @Value("${spring.ai.memory.self-mysql.driver-class-name}")
    private String mysqlDriverClassName;

    @Bean
    public SelfMysqlChatMemoryRepository selfMysqlChatMemoryRepository() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName(mysqlDriverClassName);
        dataSource.setUrl(mysqlJdbcUrl);
        dataSource.setUsername(mysqlUsername);
        dataSource.setPassword(mysqlPassword);
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        return SelfMysqlChatMemoryRepository.mysqlBuilder()
                .jdbcTemplate(jdbcTemplate)
                .build();
    }
}
