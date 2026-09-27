package com.andrewproject.filechat.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class VectorSearchNode implements NodeAction {

    public static final String NODE_NAME = "vector_search";
    public static final String NODE_CONTENT = "vector_search_content";

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        List<String> elasticsearchList = state.value("elasticsearch_list", new ArrayList<>());
        return Map.of(NODE_CONTENT, elasticsearchList);
    }
}
