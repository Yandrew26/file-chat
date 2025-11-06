package com.andrewproject.filechat.controller;

import com.andrewproject.filechat.config.advisor.ChatHistoryAdvisor;
import com.andrewproject.filechat.config.memory.SelfMysqlChatMemoryRepository;
import com.andrewproject.filechat.feign.UploadFeign;
import com.andrewproject.filechat.service.ChatHistoryService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;

@Slf4j
@RestController
public class ChatController {

    @Value("classpath:/prompts/prompt-template.st")
    private org.springframework.core.io.Resource promptTemplate;

    @Resource
    private ChatHistoryService chatHistoryService;

    @Resource
    private UploadFeign uploadFeign;

    private final MessageWindowChatMemory messageWindowChatMemory;

    private final ChatClient.Builder chatClientBuilder;

    public ChatController(ChatClient.Builder builder, SelfMysqlChatMemoryRepository mysqlChatMemoryRepository) {
        this.messageWindowChatMemory = MessageWindowChatMemory.builder()
                .chatMemoryRepository(mysqlChatMemoryRepository)
                .maxMessages(10)
                .build();
        this.chatClientBuilder = builder;
    }

    @GetMapping(value = "/rag")
    public ResponseEntity<String> chatRag(@RequestParam(name = "message") String message,
                                          @RequestParam(name = "conversationId") String conversationId) {
        log.info("start chat");
        String traceId = UUID.randomUUID().toString().replaceAll("-","");

        String response = this.chatClientBuilder.build()
                .prompt(getPrompt(message, conversationId))
                .advisors(getAdvisors(conversationId, traceId))
                .call()
                .content();
        return ResponseEntity.ok(response);
    }

    private List<Advisor> getAdvisors(String conversationId, String traceId) {
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
                .userId("12345678")
                .userName("Andrew")
                .build();
        advisors.add(chatHistoryAdvisor);
        log.info("traceId:{}, conversationId:{} - Started chat history", traceId, conversationId);

        return advisors;
    }

    private Prompt getPrompt(String message, String conversationId) {
        SystemPromptTemplate systemPromptTemplate = new SystemPromptTemplate(promptTemplate);
        String elasticsearchList = getElasticsearchList(message, conversationId);
        Message systemMessage = systemPromptTemplate.createMessage(Map.of("name", "filechat",
                "voice", "friendly assistant",
                "elasticsearch_results", elasticsearchList));

        log.info("Elasticsearch results: {}", elasticsearchList);

        UserMessage userMessage = new UserMessage(message);

        return new Prompt(List.of(
                systemMessage,
                userMessage
        ));
    }

    private String getElasticsearchList(String message, String conversationId) {
        List<String> searchResults = uploadFeign.searchString(message, conversationId);
        if (CollectionUtils.isEmpty(searchResults)) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < searchResults.size(); i++) {
            sb.append((i + 1)).append(": ").append(searchResults.get(i)).append("\n");
        }
        return sb.toString();
    }
}
