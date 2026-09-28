package com.andrewproject.filechat.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.andrewproject.filechat.service.DocumentGraphService;

import java.util.List;
import java.util.Map;

/** Looks up catalog ebooks by the authors of the conversation's documents; runs in parallel with vector search. */
public class Neo4jSearchNode implements NodeAction {

    public static final String NODE_NAME = "neo4j_search_node";

    public static final String NODE_CONTENT = "neo4j_search_node_content";

    private final DocumentGraphService documentGraphService;

    public Neo4jSearchNode(DocumentGraphService documentGraphService) {
        this.documentGraphService = documentGraphService;
    }

    @Override
    public Map<String, Object> apply(OverAllState state) {
        String conversationId = state.value("conversationId", "");
        // Plain maps, not records: graph state may be copied through a serializer between nodes
        List<Map<String, Object>> related = documentGraphService.findRelatedWorks(conversationId).stream()
                .map(work -> Map.<String, Object>of("title", work.title(), "authors", work.authors()))
                .toList();
        return Map.of(NODE_CONTENT, related);
    }
}
