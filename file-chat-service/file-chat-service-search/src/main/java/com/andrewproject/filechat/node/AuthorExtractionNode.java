package com.andrewproject.filechat.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.andrewproject.filechat.enums.NodeStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.converter.ListOutputConverter;
import org.springframework.core.convert.support.DefaultConversionService;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
public class AuthorExtractionNode implements NodeAction {

    public static final String NODE_NAME = "author_extraction_node";
    public static final String NODE_CONTENT = "author_extraction_node_content";

    private final Map<String, NodeStatus> node2Status;
    private final ChatClient chatClient;

    public AuthorExtractionNode(Map<String, NodeStatus> node2Status, ChatClient.Builder chatClientBuilder) {
        this.node2Status = node2Status;
        this.chatClient = chatClientBuilder.build();
    }

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        try {
            log.info("AuthorExtractionNode started");
            node2Status.put(NODE_NAME, NodeStatus.RUNNING);

            List<String> authorList = state.value("author_list", new ArrayList<>());

            if (authorList.isEmpty()) {
                log.warn("No elasticsearch results available for author extraction");
                node2Status.put(NODE_NAME, NodeStatus.COMPLETED);
                return Map.of(NODE_CONTENT, new ArrayList<>());
            }

            String documentsContent = String.join("\n\n", authorList);

            ListOutputConverter outputConverter = new ListOutputConverter(new DefaultConversionService());

            ChatResponse response = chatClient.prompt()
                .user("Based on the following documents, extract all author names mentioned. Return only the names as a comma-separated list, no additional text:\n\n" + documentsContent)
                .call()
                .chatResponse();

            String content = response.getResult().getOutput().getText();
            List<String> authorNames = outputConverter.convert(content);

            log.info("Extracted authors: {}", authorNames);
            node2Status.put(NODE_NAME, NodeStatus.COMPLETED);

            return Map.of(NODE_CONTENT, authorNames != null ? authorNames : new ArrayList<>());

        } catch (Exception e) {
            log.error("Error in AuthorExtractionNode", e);
            node2Status.put(NODE_NAME, NodeStatus.FAILED);
            return Map.of(NODE_CONTENT, new ArrayList<>());
        }
    }
}
