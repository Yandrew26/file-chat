package com.andrewproject.filechat.node;

import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.alibaba.cloud.ai.graph.async.AsyncGenerator;
import com.andrewproject.filechat.config.graph.StreamingGenerator;
import com.andrewproject.filechat.enums.NodeStatus;
import com.andrewproject.filechat.feign.UploadFeign;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class VectorSearchNode implements NodeAction {

    private final Map<String, NodeStatus> node2Status;

    public static final String NODE_NAME = "vector_search";
    public static final String NODE_CONTENT = "vector_search_content";

    public VectorSearchNode(Map<String, NodeStatus> node2Status) {
        this.node2Status = node2Status;
    }

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        node2Status.put(NODE_NAME, NodeStatus.RUNNING);

        List<String> elasticsearchList = state.value("elasticsearch_list", new ArrayList<>());

        AsyncGenerator<? extends NodeOutput> generator = StreamingGenerator.builder()
                .startingNode("vector_search_stream")
                .startingState(state)
                .mapResult(response -> {
                    node2Status.put(NODE_CONTENT, NodeStatus.COMPLETED);
                    return Map.of(NODE_CONTENT, elasticsearchList);
                }).build(elasticsearchList.toString());

        return Map.of(NODE_CONTENT, generator);
    }
}
