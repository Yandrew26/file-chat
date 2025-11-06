package com.andrewproject.filechat.config.advisor;

import com.andrewproject.filechat.dto.ChatHistoryDTO;
import com.andrewproject.filechat.service.ChatHistoryService;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.ai.chat.client.ChatClientMessageAggregator;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import reactor.core.publisher.Flux;

@Slf4j
public class ChatHistoryAdvisor implements CallAdvisor, StreamAdvisor {

    @Getter
    private final int order;
    private final String conversationId;
    private final String traceId;
    private final String userId;
    private final String userName;
    private final ChatHistoryService chatHistoryService;

    public ChatHistoryAdvisor(int order, String conversationId, String traceId, String userId, String userName, ChatHistoryService chatHistoryService) {
        this.order = order;
        this.conversationId = conversationId;
        this.traceId = traceId;
        this.userId = userId;
        this.userName = userName;
        this.chatHistoryService = chatHistoryService;
    }

    private void logRequest(ChatClientRequest request) {
        try {
            String content = request.prompt().getUserMessage().getText();
            chatHistoryService.addChatHistory(ChatHistoryDTO.builder()
                    .userId(userId)
                    .userName(userName)
                    .conversationId(conversationId)
                    .traceId(traceId)
                    .type("USER")
                    .content(content)
                    .build());
            log.info("ChatHistoryAdvisor - Request: conversationId:{}, traceId:{}, userId:{}, userName:{}, prompt:{}",
                    this.conversationId, this.traceId, this.userId, this.userName, content);
        } catch (Exception e) {
            log.error("Error logging ChatHistoryAdvisor request traceId:{}", this.traceId, e);
        }
    }

    private void logResponse(ChatClientResponse response) {
        try {
            String content = response.chatResponse().getResult().getOutput().getText();
            chatHistoryService.addChatHistory(ChatHistoryDTO.builder()
                    .userId(userId)
                    .userName(userName)
                    .conversationId(conversationId)
                    .traceId(traceId)
                    .type("ASSISTANT")
                    .content(content)
                    .build());
            log.info("ChatHistoryAdvisor - Response: conversationId:{}, traceId:{}, userId:{}, userName:{}, Response:{}",
                    this.conversationId, this.traceId, this.userId, this.userName, content);
        } catch (Exception e) {
            log.error("Error logging ChatHistoryAdvisor response traceId:{}", this.traceId, e);
        }
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest chatClientRequest, CallAdvisorChain callAdvisorChain) {
        logRequest(chatClientRequest);
        ChatClientResponse response = callAdvisorChain.nextCall(chatClientRequest);
        logResponse(response);
        return response;
    }

    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest chatClientRequest, StreamAdvisorChain streamAdvisorChain) {
        logRequest(chatClientRequest);
        Flux<ChatClientResponse> responseFlux = streamAdvisorChain.nextStream(chatClientRequest);
        return (new ChatClientMessageAggregator()).aggregateChatClientResponse(responseFlux, this::logResponse);
    }

    @NotNull
    public String getName() {
        return this.getClass().getSimpleName();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private int order = 0;
        private String conversationId;
        private String traceId;
        private String userId;
        private String userName;
        private ChatHistoryService chatHistoryService;

        public Builder() {
        }

        public Builder order(int order) {
            this.order = order;
            return this;
        }

        public Builder conversationId(String conversationId) {
            this.conversationId = conversationId;
            return this;
        }

        public Builder traceId(String traceId) {
            this.traceId = traceId;
            return this;
        }

        public Builder userId(String userId) {
            this.userId = userId;
            return this;
        }

        public Builder userName(String userName) {
            this.userName = userName;
            return this;
        }

        public Builder chatHistoryService(ChatHistoryService chatHistoryService) {
            this.chatHistoryService = chatHistoryService;
            return  this;
        }

        public ChatHistoryAdvisor build() {
            return new ChatHistoryAdvisor(order, conversationId, traceId, userId, userName, chatHistoryService);
        }
    }
}
