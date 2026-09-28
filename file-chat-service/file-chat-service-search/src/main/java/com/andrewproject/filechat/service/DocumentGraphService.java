package com.andrewproject.filechat.service;

import com.andrewproject.filechat.dto.DocumentMetadata;
import com.andrewproject.filechat.dto.RelatedWork;
import com.andrewproject.filechat.feign.UploadFeign;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

/**
 * Connects uploaded documents to the Neo4j catalog of {@code (:Author)-[:WROTE]->(:Ebook)}.
 *
 * <p>When a PDF is uploaded, the model reads its opening passages once to extract the title and authors, and the
 * document is linked to the matching catalog authors: {@code (:Document)-[:WRITTEN_BY]->(:Author)}. Existing
 * authors are matched by name, case-insensitively; no Author or Ebook nodes are created. Each question then needs
 * a single graph query, with no extra model call.
 *
 * <p>Neo4j is optional: when it is disabled or unreachable, lookups return nothing, and after a failure it is not
 * retried for a minute so an outage cannot slow down every question.
 */
@Slf4j
@Service
public class DocumentGraphService {

    private static final long BACKOFF_MILLIS = 60_000L;

    private static final long INDEX_WAIT_SECONDS = 10;

    private static final String EXTRACTION_PROMPT = """
            You extract bibliographic metadata. From the opening text of a document, return its title and the full
            names of its authors, exactly as written. Use an empty list when no author is named; never guess.
            """;

    private final ChatClient chatClient;
    private final UploadFeign uploadFeign;
    private final Neo4jClient neo4jClient;
    private final boolean enabled;
    private final int maxRecommendations;
    private final ExecutorService executor = Executors.newFixedThreadPool(2);
    private volatile long unavailableUntil;

    public DocumentGraphService(ChatClient.Builder chatClientBuilder,
                                UploadFeign uploadFeign,
                                Neo4jClient neo4jClient,
                                @Value("${filechat.neo4j.enabled:true}") boolean enabled,
                                @Value("${filechat.neo4j.max-recommendations:5}") int maxRecommendations) {
        this.chatClient = chatClientBuilder.build();
        this.uploadFeign = uploadFeign;
        this.neo4jClient = neo4jClient;
        this.enabled = enabled;
        this.maxRecommendations = maxRecommendations;
    }

    /**
     * Links a newly uploaded document to its authors. Waits up to {@link #INDEX_WAIT_SECONDS} seconds so
     * recommendations are ready for the first question; anything slower finishes in the background. Never fails
     * the upload.
     */
    public void indexDocument(String userId, String conversationId, String fileName) {
        if (!enabled) {
            return;
        }
        Future<?> task = executor.submit(() -> {
            try {
                extractAndLink(userId, conversationId, fileName);
            } catch (Exception e) {
                log.warn("conversationId:{} - could not index authors of {}: {}", conversationId, fileName, e.getMessage());
            }
        });
        try {
            task.get(INDEX_WAIT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            log.info("conversationId:{} - author indexing of {} continues in the background", conversationId, fileName);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException e) {
            // already logged by the task
        }
    }

    private void extractAndLink(String userId, String conversationId, String fileName) {
        List<String> passages = uploadFeign.searchAuthor(conversationId, fileName);
        if (passages.isEmpty()) {
            return;
        }
        DocumentMetadata metadata = chatClient.prompt()
                .system(EXTRACTION_PROMPT)
                .user(String.join("\n\n", passages))
                .call()
                .entity(DocumentMetadata.class);
        List<String> authors = metadata == null || metadata.authors() == null ? List.of()
                : metadata.authors().stream().filter(StringUtils::hasText).map(String::trim).distinct().limit(20).toList();
        if (authors.isEmpty()) {
            log.info("conversationId:{} - no authors found in {}", conversationId, fileName);
            return;
        }
        String title = StringUtils.hasText(metadata.title()) ? metadata.title().trim() : titleFromFileName(fileName);
        Long linked = withNeo4j(() -> neo4jClient.query("""
                        MERGE (d:Document {conversationId: $conversationId, fileName: $fileName})
                        SET d.title = $title, d.userId = $userId, d.authors = $authors
                        WITH d
                        MATCH (a:Author) WHERE toLower(a.name) IN [name IN $authors | toLower(name)]
                        MERGE (d)-[:WRITTEN_BY]->(a)
                        RETURN count(a) AS linked
                        """)
                .bindAll(Map.of("conversationId", conversationId, "fileName", fileName, "title", title,
                        "userId", userId, "authors", authors))
                .fetchAs(Long.class)
                .one()
                .orElse(0L), 0L);
        log.info("conversationId:{} - \"{}\" by {}, linked to {} catalog author(s)", conversationId, title, authors, linked);
    }

    /** Catalog ebooks by the authors of this conversation's documents, excluding those documents themselves. */
    public List<RelatedWork> findRelatedWorks(String conversationId) {
        if (!enabled) {
            return List.of();
        }
        return withNeo4j(() -> List.copyOf(neo4jClient.query("""
                        MATCH (d:Document {conversationId: $conversationId})-[:WRITTEN_BY]->(a:Author)-[:WROTE]->(b:Ebook)
                        WITH b, collect(DISTINCT a.name) AS authors, collect(DISTINCT toLower(d.title)) AS own
                        WHERE NOT toLower(b.title) IN own
                        RETURN b.title AS title, authors
                        ORDER BY size(authors) DESC, title
                        LIMIT $limit
                        """)
                .bindAll(Map.of("conversationId", conversationId, "limit", maxRecommendations))
                .fetchAs(RelatedWork.class)
                .mappedBy((types, record) -> new RelatedWork(record.get("title").asString(""),
                        record.get("authors").asList(value -> value.asString())))
                .all()), List.of());
    }

    private <T> T withNeo4j(Supplier<T> query, T fallback) {
        if (System.currentTimeMillis() < unavailableUntil) {
            return fallback;
        }
        try {
            return query.get();
        } catch (RuntimeException e) {
            unavailableUntil = System.currentTimeMillis() + BACKOFF_MILLIS;
            log.warn("Neo4j unavailable, author recommendations are off for {}s: {}", BACKOFF_MILLIS / 1000, e.getMessage());
            return fallback;
        }
    }

    private static String titleFromFileName(String fileName) {
        String name = fileName.replaceAll("(?i)\\.pdf$", "").replaceAll("[_-]+", " ").trim();
        return name.isEmpty() ? fileName : name.substring(0, 1).toUpperCase(Locale.ROOT) + name.substring(1);
    }

    @PreDestroy
    void shutdown() {
        executor.shutdown();
    }
}
