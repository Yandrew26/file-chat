package com.andrewproject.filechat.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.core.io.Resource;

import java.util.Map;

@Slf4j
public class PromptTemplateNode implements NodeAction {

    private final Resource promptTemplate;

    public static final String NODE_NAME = "prompt_template";

    public static final String NODE_CONTENT = "prompt_template_content";

    public PromptTemplateNode(Resource promptTemplate) {
        this.promptTemplate = promptTemplate;
    }

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        SystemPromptTemplate systemPromptTemplate = new SystemPromptTemplate(promptTemplate);
        return Map.of(NODE_CONTENT, systemPromptTemplate.getTemplate());
    }
}
