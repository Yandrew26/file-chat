package com.andrewproject.filechat.config.graph;

import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.async.AsyncGenerator;
import com.alibaba.cloud.ai.graph.async.FlowGenerator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;

import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import org.reactivestreams.FlowAdapters;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import reactor.core.publisher.Flux;

public interface StreamingGenerator {
    static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Function<ChatResponse, Map<String, Object>> mapResult;
        private String startingNode;
        private OverAllState startingState;

        public Builder mapResult(Function<ChatResponse, Map<String, Object>> mapResult) {
            this.mapResult = mapResult;
            return this;
        }

        public Builder startingNode(String node) {
            this.startingNode = node;
            return this;
        }

        public Builder startingState(OverAllState state) {
            this.startingState = state;
            return this;
        }

//        public AsyncGenerator<? extends NodeOutput> build(Flux<ChatResponse> flux) {
//            return this.buildInternal(flux, (chatResponse) -> new StreamingOutput(chatResponse.getResult().getOutput().getText(), this.startingNode, this.startingState));
//        }
//
//        public AsyncGenerator<? extends NodeOutput> buildWithChatResponse(Flux<ChatResponse> flux) {
//            return this.buildInternal(flux, (chatResponse) -> new StreamingOutput(chatResponse, this.startingNode, this.startingState));
//        }

        public AsyncGenerator<? extends NodeOutput> build(String info) {
            Objects.requireNonNull(this.mapResult, "mapResult cannot be null");
            AtomicReference<ChatResponse> result = new AtomicReference((Object)null);
            Consumer<ChatResponse> mergeMessage = (response) -> result.updateAndGet((lastResponse) -> {
                if (lastResponse == null) {
                    return response;
                } else {
                    AssistantMessage currentMessage = response.getResult().getOutput();
                    if (currentMessage.hasToolCalls()) {
                        return response;
                    } else {
                        String lastMessageText = (String)Objects.requireNonNull(lastResponse.getResult().getOutput().getText(), "lastResponse text cannot be null");
                        String currentMessageText = currentMessage.getText();
                        AssistantMessage newMessage = new AssistantMessage(currentMessageText != null ? lastMessageText.concat(currentMessageText) : lastMessageText, currentMessage.getMetadata(), currentMessage.getToolCalls(), currentMessage.getMedia());
                        Generation newGeneration = new Generation(newMessage, response.getResult().getMetadata());
                        return new ChatResponse(List.of(newGeneration), response.getMetadata());
                    }
                }
            });
            Flux<StreamingOutput> processedFlux = Flux.just(new StreamingOutput(info, startingNode, startingState));
            return FlowGenerator.fromPublisher(FlowAdapters.toFlowPublisher(processedFlux), () -> {
                ChatResponse finalResult = (ChatResponse)result.get();
                System.out.println("StreamingChatGenerator: mapResult called, finalResult: " + (finalResult != null ? "not null" : "null"));
                return finalResult == null ? Map.of() : (Map)this.mapResult.apply(finalResult);
            });
        }
    }
}

