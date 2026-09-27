package com.andrewproject.filechat.config.advisor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.ai.chat.client.ChatClientMessageAggregator;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.chat.client.advisor.api.BaseChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.util.Assert;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

public final class SelfMessageChatMemoryAdvisor implements BaseChatMemoryAdvisor {
    private final ChatMemory chatMemory;
    private final String defaultConversationId;
    private final int order;
    private final Scheduler scheduler;

    private SelfMessageChatMemoryAdvisor(ChatMemory chatMemory, String defaultConversationId, int order, Scheduler scheduler) {
        Assert.notNull(chatMemory, "chatMemory cannot be null");
        Assert.hasText(defaultConversationId, "defaultConversationId cannot be null or empty");
        Assert.notNull(scheduler, "scheduler cannot be null");
        this.chatMemory = chatMemory;
        this.defaultConversationId = defaultConversationId;
        this.order = order;
        this.scheduler = scheduler;
    }

    public int getOrder() {
        return this.order;
    }

    public Scheduler getScheduler() {
        return this.scheduler;
    }

    public ChatClientRequest before(ChatClientRequest chatClientRequest, AdvisorChain advisorChain) {
        String conversationId = this.resolveConversationId(chatClientRequest.context());
        List<Message> memoryMessages = this.chatMemory.get(conversationId);
        List<Message> processedMessages = new ArrayList(memoryMessages);
        processedMessages.addAll(chatClientRequest.prompt().getInstructions());
        ChatClientRequest processedChatClientRequest = chatClientRequest.mutate().prompt(chatClientRequest.prompt().mutate().messages(processedMessages).build()).build();
        UserMessage userMessage = processedChatClientRequest.prompt().getUserMessage();
        this.chatMemory.add(conversationId, userMessage);
        return processedChatClientRequest;
    }

    public ChatClientResponse after(ChatClientResponse chatClientResponse, AdvisorChain advisorChain) {
        List<AssistantMessage> assistantMessages = new ArrayList();
        if (chatClientResponse.chatResponse() != null) {
            assistantMessages = chatClientResponse.chatResponse().getResults().stream().map((g) -> g.getOutput()).toList();
        }
        List<Message> messages = new ArrayList<>(assistantMessages);
        this.chatMemory.add(this.resolveConversationId(chatClientResponse.context()), messages);
        return chatClientResponse;
    }

    private String resolveConversationId(Map<String, Object> context) {
        Object conversationId = context.get(ChatMemory.CONVERSATION_ID);
        return conversationId == null ? this.defaultConversationId : conversationId.toString();
    }

//    public Flux<ChatClientResponse> adviseStream(ChatClientRequest chatClientRequest, StreamAdvisorChain streamAdvisorChain) {
//        Scheduler scheduler = this.getScheduler();
//        Mono var10000 = Mono.just(chatClientRequest).publishOn(scheduler).map((request) -> this.before(request, streamAdvisorChain));
//        Objects.requireNonNull(streamAdvisorChain);
//        return var10000.flatMapMany(streamAdvisorChain::nextStream).transform((flux) -> (new ChatClientMessageAggregator()).aggregateChatClientResponse(flux, (response) -> this.after(response, streamAdvisorChain)));
//    }

    public static Builder builder(ChatMemory chatMemory) {
        return new Builder(chatMemory);
    }

    public static final class Builder {
        private String conversationId = "default";
        private int order = -2147482648;
        private Scheduler scheduler;
        private ChatMemory chatMemory;

        private Builder(ChatMemory chatMemory) {
            this.scheduler = BaseAdvisor.DEFAULT_SCHEDULER;
            this.chatMemory = chatMemory;
        }

        public Builder conversationId(String conversationId) {
            this.conversationId = conversationId;
            return this;
        }

        public Builder order(int order) {
            this.order = order;
            return this;
        }

        public Builder scheduler(Scheduler scheduler) {
            this.scheduler = scheduler;
            return this;
        }

        public SelfMessageChatMemoryAdvisor build() {
            return new SelfMessageChatMemoryAdvisor(this.chatMemory, this.conversationId, this.order, this.scheduler);
        }
    }
}

