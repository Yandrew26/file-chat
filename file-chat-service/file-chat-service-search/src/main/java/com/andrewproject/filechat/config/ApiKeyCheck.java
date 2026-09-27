package com.andrewproject.filechat.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Fails startup when no DashScope API key is configured. Spring binds an unresolved ${API_KEY} as literal text,
 * which would otherwise only surface as failed model calls. Each deployment must supply its own key.
 */
@Component
public class ApiKeyCheck {

    @Value("${API_KEY:}")
    private String apiKey;

    @PostConstruct
    void verify() {
        if (!StringUtils.hasText(apiKey)) {
            throw new IllegalStateException(
                    "API_KEY is not set. Put your own DashScope API key in the environment or in .env (see .env.example).");
        }
    }
}
