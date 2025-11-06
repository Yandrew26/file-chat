package com.andrewproject.filechat.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.andrewproject.filechat.enums.NodeStatus;

import java.util.Map;

public class MergeNode implements NodeAction {

    private final Map<String, NodeStatus> node2Status;

    public static final String NODE_NAME = "merge_node";

    public MergeNode(Map<String, NodeStatus> node2Status) {
        this.node2Status = node2Status;
    }

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        if (!isDone(node2Status)) {
            return Map.of();
        }

        Object chatContent = state.value(ChatNode.NODE_CONTENT).orElse("");
        Object promptTemplateContent = state.value(PromptTemplateNode.NODE_CONTENT).orElse("");
        Object vectorSearchContent = state.value(VectorSearchNode.NODE_CONTENT).orElse("");

        return Map.of("merge_result", Map.of("chat_content", chatContent,
                "prompt_template_content", promptTemplateContent,
                "vector_search_content", vectorSearchContent));
    }

    private boolean isDone(Map<String, NodeStatus> node2Status) {
        return node2Status.get(ChatNode.NODE_NAME) == NodeStatus.COMPLETED
                && node2Status.get(PromptTemplateNode.NODE_NAME) == NodeStatus.COMPLETED
                && node2Status.get(VectorSearchNode.NODE_NAME) == NodeStatus.COMPLETED;
    }
}
