package com.andrewproject.filechat.node;

import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.alibaba.cloud.ai.graph.async.AsyncGenerator;
import com.alibaba.cloud.ai.graph.streaming.StreamingChatGenerator;
import com.andrewproject.filechat.config.advisor.ChatHistoryAdvisor;
import com.andrewproject.filechat.config.memory.SelfMysqlChatMemoryRepository;
import com.andrewproject.filechat.enums.NodeStatus;
import com.andrewproject.filechat.service.ChatHistoryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.core.io.Resource;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Slf4j
public class ChatNode implements NodeAction {

    private final Resource promptTemplateResource;

    private final ChatHistoryService chatHistoryService;

    private final MessageWindowChatMemory messageWindowChatMemory;

    private final ChatClient.Builder chatClientBuilder;

    private final Map<String, NodeStatus> node2Status;

    public static final String NODE_NAME = "rag_chat";

    public static final String NODE_CONTENT = "rag_chat_content";

    public ChatNode(ChatHistoryService chatHistoryService,
                    SelfMysqlChatMemoryRepository selfMysqlChatMemoryRepository,
                    ChatClient.Builder chatClientBuilder,
                    Resource promptTemplateResource,
                    Map<String, NodeStatus> node2Status) {
        this.messageWindowChatMemory = MessageWindowChatMemory.builder()
                .chatMemoryRepository(selfMysqlChatMemoryRepository)
                .maxMessages(10)
                .build();
        this.chatHistoryService = chatHistoryService;
        this.chatClientBuilder = chatClientBuilder;
        this.promptTemplateResource = promptTemplateResource;
        this.node2Status = node2Status;
    }


    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        node2Status.put(NODE_NAME, NodeStatus.RUNNING);

        String message = state.value("message","");
        String conversationId = state.value("conversationId","");
        String traceId = state.value("traceId","");
        String userId = state.value("userId","");
        String userName = state.value("userName","");
        List<String> elasticsearchList = state.value("elasticsearch_list", new ArrayList<>());
        List<String> neo4jResults = state.value(Neo4jSearchNode.NODE_CONTENT, new ArrayList<>());

        Flux<ChatResponse> chatResponseFlux = chatClientBuilder.build()
                .prompt(getPrompt(message, elasticsearchList, neo4jResults))
                .advisors(getAdvisors(conversationId, traceId, userId, userName))
                .stream().chatResponse();

        String responseStr = chatResponseFlux.toString();

        AsyncGenerator<? extends NodeOutput> generator = StreamingChatGenerator.builder()
                .startingNode("rag_chat_stream")
                .startingState(state)
                .mapResult(response -> {
                    String content = response.getResult().getOutput().getText();
                    List<String> queryVariants = Arrays.asList(content.split("\n"));
                    node2Status.put(NODE_CONTENT, NodeStatus.COMPLETED);
                    return Map.of(NODE_CONTENT, queryVariants);
                }).build(chatResponseFlux);
        return Map.of(NODE_CONTENT, generator);
    }

    private List<Advisor> getAdvisors(String conversationId, String traceId, String userId, String userName) {
        List<Advisor> advisors = new ArrayList<>();

        MessageChatMemoryAdvisor messageChatMemoryAdvisor = MessageChatMemoryAdvisor.builder(messageWindowChatMemory)
                .conversationId(conversationId)
                .build();
        advisors.add(messageChatMemoryAdvisor);
        log.info("traceId:{}, conversationId:{} - Started chat short memory", traceId, conversationId);

        ChatHistoryAdvisor chatHistoryAdvisor = ChatHistoryAdvisor.builder()
                .chatHistoryService(chatHistoryService)
                .traceId(traceId)
                .conversationId(conversationId)
                .userId(userId)
                .userName(userName)
                .build();
        advisors.add(chatHistoryAdvisor);
        log.info("traceId:{}, conversationId:{} - Started chat history", traceId, conversationId);

        return advisors;
    }

    private Prompt getPrompt(String message, List<String> elasticsearchList, List<String> neo4jResults) {
        SystemPromptTemplate systemPromptTemplate = new SystemPromptTemplate(promptTemplateResource);
        Message systemMessage = systemPromptTemplate.createMessage(Map.of("name", "filechat",
                "voice", "friendly assistant",
                "elasticsearch_results", elasticsearchList,
                "neo4j_results", neo4jResults));

        log.info("Elasticsearch results: {}", elasticsearchList);
        log.info("Neo4j results: {}", neo4jResults);

        UserMessage userMessage = new UserMessage(message);

        return new Prompt(List.of(
                systemMessage,
                userMessage
        ));
    }
}
