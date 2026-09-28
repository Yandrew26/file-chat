package com.andrewproject.filechat.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import org.springframework.ai.chat.messages.AbstractMessage;

import java.util.List;
import java.util.Map;

public class MergeNode implements NodeAction {

    public static final String NODE_NAME = "merge_node";

    public static final String NODE_CONTENT = "merge_result";

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        // When ChatNode streams, the graph stores the aggregated AssistantMessage under its key
        Object chatContent = state.value(ChatNode.NODE_CONTENT)
                .map(value -> value instanceof AbstractMessage message ? message.getText() : value)
                .orElse("");
        Object promptTemplateContent = state.value(PromptTemplateNode.NODE_CONTENT).orElse("");
        Object vectorSearchContent = state.value(VectorSearchNode.NODE_CONTENT).orElse("");
        Object neo4jSearchContent = state.value(Neo4jSearchNode.NODE_CONTENT).orElse(List.of());

        return Map.of(NODE_CONTENT, Map.of("chat_content", chatContent,
                "prompt_template_content", promptTemplateContent,
                "vector_search_content", vectorSearchContent,
                "neo4j_search_content", neo4jSearchContent));
    }
}
