package com.andrewproject.filechat.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Caps the requests that cost money (model answers and embeddings) so a public deployment cannot be used to run up
 * the owner's DashScope bill. Each kind of call has a per-client limit and a global daily budget; all are configurable
 * (see .env.example).
 *
 * <p>Clients are identified by IP. The gateway appends the caller's address to X-Forwarded-For, so the entry
 * {@code trusted-proxies} positions from the end is used; entries before it are client-supplied and ignored.
 * This service must only be reachable through the gateway.
 */
@Slf4j
@Component
public class UsageLimitFilter extends OncePerRequestFilter {

    private enum Kind {
        QUESTION("questions", 60_000L, "Too many questions. Please wait a minute and try again."),
        SEARCH("searches", 60_000L, "Too many searches. Please wait a minute and try again."),
        UPLOAD("uploads", 3_600_000L, "Too many uploads. Please try again later.");

        final String label;
        final long windowMillis;
        final String message;

        Kind(String label, long windowMillis, String message) {
            this.label = label;
            this.windowMillis = windowMillis;
            this.message = message;
        }
    }

    private record Window(long start, AtomicInteger count) {
    }

    private static final int MAX_TRACKED_CLIENTS = 50_000;

    private final Map<Kind, Integer> perClientLimits;
    private final Map<Kind, Integer> dailyLimits;
    private final int trustedProxies;

    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    private final Map<Kind, AtomicInteger> dailyCounts = new ConcurrentHashMap<>();
    private volatile LocalDate day = LocalDate.now();

    public UsageLimitFilter(@Value("${filechat.limits.questions-per-minute:10}") int questionsPerMinute,
                            @Value("${filechat.limits.searches-per-minute:30}") int searchesPerMinute,
                            @Value("${filechat.limits.uploads-per-hour:10}") int uploadsPerHour,
                            @Value("${filechat.limits.questions-per-day:500}") int questionsPerDay,
                            @Value("${filechat.limits.searches-per-day:2000}") int searchesPerDay,
                            @Value("${filechat.limits.uploads-per-day:100}") int uploadsPerDay,
                            @Value("${filechat.limits.trusted-proxies:1}") int trustedProxies) {
        this.perClientLimits = Map.of(Kind.QUESTION, questionsPerMinute, Kind.SEARCH, searchesPerMinute, Kind.UPLOAD, uploadsPerHour);
        this.dailyLimits = Map.of(Kind.QUESTION, questionsPerDay, Kind.SEARCH, searchesPerDay, Kind.UPLOAD, uploadsPerDay);
        this.trustedProxies = Math.max(trustedProxies, 0);
        for (Kind kind : Kind.values()) {
            dailyCounts.put(kind, new AtomicInteger());
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return kindOf(request) == null;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Kind kind = kindOf(request);
        String client = clientAddress(request);

        long now = System.currentTimeMillis();
        String key = kind + "|" + client;
        if (windows.size() > MAX_TRACKED_CLIENTS) {
            windows.values().removeIf(w -> now - w.start() > Kind.UPLOAD.windowMillis);
        }
        Window window = windows.compute(key, (k, w) ->
                w == null || now - w.start() >= kind.windowMillis ? new Window(now, new AtomicInteger()) : w);
        if (window.count().incrementAndGet() > perClientLimits.get(kind)) {
            long retryAfter = Math.max(1, (window.start() + kind.windowMillis - now) / 1000);
            reject(response, retryAfter, kind.message);
            return;
        }

        rollDay();
        if (dailyCounts.get(kind).incrementAndGet() > dailyLimits.get(kind)) {
            log.warn("Daily {} budget of {} reached", kind.label, dailyLimits.get(kind));
            reject(response, secondsUntilTomorrow(), "FileChat has reached its daily limit. Please try again tomorrow.");
            return;
        }
        chain.doFilter(request, response);
    }

    private static Kind kindOf(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path.equals("/rag") || path.startsWith("/graph/rag")) {
            return Kind.QUESTION;
        }
        if (path.equals("/graph/search")) {
            return Kind.SEARCH;
        }
        if (path.equals("/graph/create-chat") || path.equals("/graph/documents")) {
            return Kind.UPLOAD;
        }
        return null;
    }

    private String clientAddress(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && trustedProxies > 0) {
            String[] hops = forwarded.split(",");
            int index = Math.max(0, hops.length - trustedProxies);
            return hops[index].trim();
        }
        return request.getRemoteAddr();
    }

    private void rollDay() {
        LocalDate today = LocalDate.now();
        if (!today.equals(day)) {
            synchronized (this) {
                if (!today.equals(day)) {
                    dailyCounts.values().forEach(count -> count.set(0));
                    day = today;
                }
            }
        }
    }

    private static long secondsUntilTomorrow() {
        return java.time.Duration.between(java.time.LocalDateTime.now(), LocalDate.now().plusDays(1).atStartOfDay()).toSeconds();
    }

    private static void reject(HttpServletResponse response, long retryAfterSeconds, String message) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"message\":\"" + message + "\"}");
    }
}
