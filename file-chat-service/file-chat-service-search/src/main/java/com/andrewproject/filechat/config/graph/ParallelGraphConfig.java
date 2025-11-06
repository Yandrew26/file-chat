package com.andrewproject.filechat.config.graph;

import com.alibaba.cloud.ai.graph.GraphRepresentation;
import com.alibaba.cloud.ai.graph.KeyStrategy;
import com.alibaba.cloud.ai.graph.KeyStrategyFactory;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;
import com.andrewproject.filechat.config.memory.SelfMysqlChatMemoryRepository;
import com.andrewproject.filechat.enums.NodeStatus;
import com.andrewproject.filechat.feign.UploadFeign;
import com.andrewproject.filechat.node.ChatNode;
import com.andrewproject.filechat.node.MergeNode;
import com.andrewproject.filechat.node.PromptTemplateNode;
import com.andrewproject.filechat.node.VectorSearchNode;
import com.andrewproject.filechat.service.ChatHistoryService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static com.alibaba.cloud.ai.graph.StateGraph.START;
import static com.alibaba.cloud.ai.graph.StateGraph.END;
import static com.alibaba.cloud.ai.graph.action.AsyncNodeAction.node_async;

import java.util.HashMap;
import java.util.Map;

@Configuration
@Slf4j
public class ParallelGraphConfig {

    @Value("classpath:/prompts/prompt-template.st")
    private org.springframework.core.io.Resource promptTemplate;

    @Resource
    private ChatHistoryService chatHistoryService;

    @Bean
    public StateGraph parallelStreamGraph(ChatClient.Builder chatClientBuilder, SelfMysqlChatMemoryRepository selfMysqlChatMemoryRepository) throws GraphStateException {

        KeyStrategyFactory keyStrategyFactory = () -> {
            HashMap<String, KeyStrategy> keyStrategyHashMap = new HashMap<>();

            keyStrategyHashMap.put("message", new ReplaceStrategy());
            keyStrategyHashMap.put("conversation_id", new ReplaceStrategy());
            keyStrategyHashMap.put("trace_id", new ReplaceStrategy());
            keyStrategyHashMap.put("user_id", new ReplaceStrategy());
            keyStrategyHashMap.put("user_name", new ReplaceStrategy());
            keyStrategyHashMap.put("elasticsearch_list", new ReplaceStrategy());

            keyStrategyHashMap.put(ChatNode.NODE_CONTENT, new ReplaceStrategy());
            keyStrategyHashMap.put(PromptTemplateNode.NODE_CONTENT, new ReplaceStrategy());
            keyStrategyHashMap.put(VectorSearchNode.NODE_CONTENT, new ReplaceStrategy());

            return keyStrategyHashMap;
        };

        Map<String, NodeStatus> node2Status = new HashMap<>();

        StateGraph stateGraph = new StateGraph(keyStrategyFactory)
                .addNode(ChatNode.NODE_NAME, node_async(new ChatNode(chatHistoryService, selfMysqlChatMemoryRepository,
                        chatClientBuilder, promptTemplate, node2Status)))
                .addNode(PromptTemplateNode.NODE_NAME, node_async(new PromptTemplateNode(node2Status, promptTemplate)))
                .addNode(VectorSearchNode.NODE_NAME, node_async(new VectorSearchNode(node2Status)))
                .addNode(MergeNode.NODE_NAME, node_async(new MergeNode(node2Status)))

                .addEdge(START, PromptTemplateNode.NODE_NAME)
                .addEdge(START, VectorSearchNode.NODE_NAME)

                .addEdge(PromptTemplateNode.NODE_NAME, ChatNode.NODE_NAME)
                .addEdge(VectorSearchNode.NODE_NAME, ChatNode.NODE_NAME)

                .addEdge(ChatNode.NODE_NAME, MergeNode.NODE_NAME)

                .addEdge(MergeNode.NODE_NAME, END);

        GraphRepresentation representation = stateGraph.getGraph(GraphRepresentation.Type.PLANTUML,
                "graph flow");
        log.info("\n=== graph UML flow ===");
        log.info(representation.content());
        log.info("==================================");

        return stateGraph;
    }
}
