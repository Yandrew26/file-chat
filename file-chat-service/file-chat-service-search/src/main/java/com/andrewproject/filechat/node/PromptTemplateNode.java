package com.andrewproject.filechat.node;

import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.alibaba.cloud.ai.graph.async.AsyncGenerator;
import com.andrewproject.filechat.config.graph.StreamingGenerator;
import com.andrewproject.filechat.enums.NodeStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;

import java.util.Map;
import java.util.Optional;

@Slf4j
public class PromptTemplateNode implements NodeAction {

    private final Map<String, NodeStatus> node2Status;

    private final Resource promptTemplate;

    public static final String NODE_NAME = "prompt_template";

    public static final String NODE_CONTENT = "prompt_template_content";

    public PromptTemplateNode(Map<String, NodeStatus> node2Status, Resource promptTemplate) {
        this.node2Status = node2Status;
        this.promptTemplate = promptTemplate;
    }

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        node2Status.put(NODE_NAME, NodeStatus.RUNNING);

        SystemPromptTemplate systemPromptTemplate = new SystemPromptTemplate(promptTemplate);
        String promptTemplateContent = systemPromptTemplate.getTemplate();

        AsyncGenerator<? extends NodeOutput> generator = StreamingGenerator.builder()
                .startingNode("prompt_template_stream")
                .startingState(state)
                .mapResult(response -> {
                    node2Status.put(NODE_CONTENT, NodeStatus.COMPLETED);
                    return Map.of(NODE_CONTENT, promptTemplateContent);
                }).build(promptTemplateContent);

        return Map.of(NODE_CONTENT, generator);

    }
}
