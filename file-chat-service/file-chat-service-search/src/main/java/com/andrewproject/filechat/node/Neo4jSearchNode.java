package com.andrewproject.filechat.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.andrewproject.filechat.enums.NodeStatus;
import com.andrewproject.filechat.repository.AuthorRepository;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
public class Neo4jSearchNode implements NodeAction {

    public static final String NODE_NAME = "neo4j_search_node";
    public static final String NODE_CONTENT = "neo4j_search_node_content";

    private final Map<String, NodeStatus> node2Status;
    private final AuthorRepository authorRepository;

    public Neo4jSearchNode(Map<String, NodeStatus> node2Status, AuthorRepository authorRepository) {
        this.node2Status = node2Status;
        this.authorRepository = authorRepository;
    }

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        try {
            log.info("Neo4jSearchNode started");
            node2Status.put(NODE_NAME, NodeStatus.RUNNING);

            List<String> authorNames = state.value(AuthorExtractionNode.NODE_CONTENT, new ArrayList<>());

            if (authorNames.isEmpty()) {
                log.info("No authors extracted, skipping Neo4j search");
                node2Status.put(NODE_NAME, NodeStatus.COMPLETED);
                return Map.of(NODE_CONTENT, new ArrayList<>());
            }

            List<String> relatedBooks = new ArrayList<>();

            for (String authorName : authorNames) {
                try {
                    List<String> bookTitles = authorRepository.findEbookTitlesByAuthorName(authorName.trim());
                    if (bookTitles != null && !bookTitles.isEmpty()) {
                        for (String title : bookTitles) {
                            relatedBooks.add("Author: " + authorName + " - Book: " + title);
                        }
                    }
                } catch (Exception e) {
                    log.warn("Error searching for author: {}", authorName, e);
                }
            }

            log.info("Found {} related books from Neo4j", relatedBooks.size());
            node2Status.put(NODE_NAME, NodeStatus.COMPLETED);

            return Map.of(NODE_CONTENT, relatedBooks);

        } catch (Exception e) {
            log.error("Error in Neo4jSearchNode", e);
            node2Status.put(NODE_NAME, NodeStatus.FAILED);
            return Map.of(NODE_CONTENT, new ArrayList<>());
        }
    }
}
