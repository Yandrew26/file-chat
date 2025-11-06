package com.andrewproject.filechat.config;

import com.andrewproject.filechat.filter.AuthGatewayFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AuthGatewayConfig {

    @Bean
    public AuthGatewayFilter getAuthGatewayFilter() {
        return new AuthGatewayFilter();
    }
}
