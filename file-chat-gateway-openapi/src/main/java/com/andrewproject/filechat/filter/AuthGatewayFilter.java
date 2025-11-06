package com.andrewproject.filechat.filter;

import com.andrewproject.filechat.feign.AuthClient;
import com.andrewproject.filechat.param.ReqAuthVerifyParam;
import com.andrewproject.filechat.param.RespAuthVerifyParam;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@Slf4j
public class AuthGatewayFilter implements GatewayFilter, Ordered {

    @Resource
    @Lazy
    private AuthClient authClient;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String authorization = request.getHeaders().getFirst("authorization");
        String authId = request.getHeaders().getFirst("authID");
        String ip = request.getRemoteAddress().getAddress().getHostAddress();
        String uri = request.getPath().toString();

        log.info("uri: {}, ip: {}", uri, ip);

        if (!StringUtils.hasText(authorization) || !StringUtils.hasText(authId)) {
            log.warn("Missing request header(s) - authId: {}, authorization: {}", authId, authorization);
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }
        ReqAuthVerifyParam param = ReqAuthVerifyParam.builder()
                .authStr(authorization)
                .authId(authId)
                .uri(uri)
                .ip(ip)
                .build();

        CompletableFuture<RespAuthVerifyParam> task = CompletableFuture.supplyAsync(() -> {
            return authClient.verify(param);
        });

        RespAuthVerifyParam authResponse = null;
        try {
            authResponse = task.get();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        }

        if (ObjectUtils.isEmpty(authResponse)) {
            log.error("Auth service returned null response");
            exchange.getResponse().setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR);
            return exchange.getResponse().setComplete();
        }

        if (!authResponse.getVerifyResult()) {
            log.warn("Authorization failed: {}", authResponse.getFailMsg());
            exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
            return exchange.getResponse().setComplete();
        }

        ServerWebExchange build = exchange.mutate().request(request).build();
        return chain.filter(build);
    }

    @Override
    public int getOrder() {
        return 0;
    }
}
