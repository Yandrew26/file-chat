package com.andrewproject.filechat.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.andrewproject.filechat.config.advisor.ChatHistoryAdvisor;
import com.andrewproject.filechat.config.memory.SelfMysqlChatMemoryRepository;
import com.andrewproject.filechat.service.ChatHistoryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.core.io.Resource;
import org.springframework.util.CollectionUtils;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
public class ChatNode implements NodeAction {

    private final Resource promptTemplateResource;

    private final ChatHistoryService chatHistoryService;

    private final MessageWindowChatMemory messageWindowChatMemory;

    private final ChatClient.Builder chatClientBuilder;

    public static final String NODE_NAME = "rag_chat";

    public static final String NODE_CONTENT = "rag_chat_content";

    public ChatNode(ChatHistoryService chatHistoryService,
                    SelfMysqlChatMemoryRepository selfMysqlChatMemoryRepository,
                    ChatClient.Builder chatClientBuilder,
                    Resource promptTemplateResource) {
        this.messageWindowChatMemory = MessageWindowChatMemory.builder()
                .chatMemoryRepository(selfMysqlChatMemoryRepository)
                .maxMessages(10)
                .build();
        this.chatHistoryService = chatHistoryService;
        this.chatClientBuilder = chatClientBuilder;
        this.promptTemplateResource = promptTemplateResource;
    }


    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        String message = state.value("message","");
        String conversationId = state.value("conversationId","");
        String traceId = state.value("traceId","");
        String userId = state.value("userId","");
        String userName = state.value("userName","");
        List<String> elasticsearchList = state.value("elasticsearch_list", new ArrayList<>());

        Flux<ChatResponse> chatResponseFlux = chatClientBuilder.build()
                .prompt(getPrompt(message, elasticsearchList))
                .advisors(spec -> spec.advisors(getAdvisors(conversationId, traceId, userId, userName))
                        .param(ChatMemory.CONVERSATION_ID, conversationId))
                .stream().chatResponse();

        // The graph executor streams the Flux chunk by chunk and stores the aggregated message under NODE_CONTENT
        return Map.of(NODE_CONTENT, chatResponseFlux);
    }

    private List<Advisor> getAdvisors(String conversationId, String traceId, String userId, String userName) {
        List<Advisor> advisors = new ArrayList<>();

        MessageChatMemoryAdvisor messageChatMemoryAdvisor = MessageChatMemoryAdvisor.builder(messageWindowChatMemory).build();
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

    private Prompt getPrompt(String message, List<String> elasticsearchList) {
        SystemPromptTemplate systemPromptTemplate = new SystemPromptTemplate(promptTemplateResource);
        Message systemMessage = systemPromptTemplate.createMessage(Map.of("name", "filechat",
                "voice", "friendly assistant",
                "elasticsearch_results", numberPassages(elasticsearchList)));

        log.info("Elasticsearch results: {}", elasticsearchList);

        UserMessage userMessage = new UserMessage(message);

        return new Prompt(List.of(
                systemMessage,
                userMessage
        ));
    }

    /**
     * Numbers passages as "[1] ...", matching the order of the sources sent to the client,
     * so the model's citations can be linked back to the passage they came from.
     */
    private static String numberPassages(List<String> passages) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < passages.size(); i++) {
            sb.append('[').append(i + 1).append("] ").append(passages.get(i)).append("\n\n");
        }
        return sb.toString();
    }
}
