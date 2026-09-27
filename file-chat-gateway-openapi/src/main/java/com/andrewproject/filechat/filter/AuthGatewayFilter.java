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
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.net.InetSocketAddress;

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
        InetSocketAddress remoteAddress = request.getRemoteAddress();
        String ip = remoteAddress == null || remoteAddress.getAddress() == null ? "" : remoteAddress.getAddress().getHostAddress();
        String uri = request.getPath().toString();

        log.info("uri: {}, ip: {}", uri, ip);

        if (!StringUtils.hasText(authorization) || !StringUtils.hasText(authId)) {
            // Never log the authorization value: it is a reusable credential
            log.warn("Missing request header(s) - authId present: {}, authorization present: {}",
                    StringUtils.hasText(authId), StringUtils.hasText(authorization));
            return reject(exchange, HttpStatus.UNAUTHORIZED);
        }
        ReqAuthVerifyParam param = ReqAuthVerifyParam.builder()
                .authStr(authorization)
                .authId(authId)
                .uri(uri)
                .ip(ip)
                .build();

        // Feign is blocking, so run it off the Netty event loop instead of waiting on it there
        return Mono.fromCallable(() -> authClient.verify(param))
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(e -> {
                    log.error("Auth service call failed", e);
                    return Mono.empty();
                })
                .map(response -> Boolean.TRUE.equals(response.getVerifyResult())
                        ? HttpStatus.OK
                        : logFailure(response))
                .defaultIfEmpty(HttpStatus.SERVICE_UNAVAILABLE)
                .flatMap(status -> status == HttpStatus.OK ? chain.filter(exchange) : reject(exchange, status));
    }

    private static HttpStatus logFailure(RespAuthVerifyParam response) {
        log.warn("Authorization failed: {}", response.getFailMsg());
        return HttpStatus.FORBIDDEN;
    }

    private static Mono<Void> reject(ServerWebExchange exchange, HttpStatus status) {
        exchange.getResponse().setStatusCode(status);
        return exchange.getResponse().setComplete();
    }

    @Override
    public int getOrder() {
        return 0;
    }
}
