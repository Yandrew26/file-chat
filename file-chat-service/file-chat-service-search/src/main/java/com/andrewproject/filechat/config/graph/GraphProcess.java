package com.andrewproject.filechat.config.graph;

import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.streaming.OutputType;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import com.andrewproject.filechat.node.ChatNode;
import com.andrewproject.filechat.node.PromptTemplateNode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Translates graph node outputs into the server-sent events consumed by the web client.
 *
 * <ul>
 *   <li>{@code meta}    – {@code {"traceId", "conversationId"}}, sent first</li>
 *   <li>{@code sources} – the retrieved passages, as a JSON array</li>
 *   <li>{@code prompt}  – the system prompt template, as a JSON string</li>
 *   <li>{@code token}   – {@code {"text"}}, one per streamed chunk of the answer</li>
 *   <li>{@code done}    – the answer has finished streaming</li>
 *   <li>{@code error}   – {@code {"message"}}, the stream failed</li>
 * </ul>
 */
public class GraphProcess {

    private static final Logger logger = LoggerFactory.getLogger(GraphProcess.class);

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private GraphProcess() {
    }

    public static Flux<ServerSentEvent<String>> toServerSentEvents(Flux<NodeOutput> outputs,
                                                                  String traceId,
                                                                  String conversationId,
                                                                  List<?> sources) {
        AtomicBoolean promptSent = new AtomicBoolean(false);

        Flux<ServerSentEvent<String>> head = Flux.just(
                event("meta", Map.of("traceId", traceId, "conversationId", conversationId)),
                event("sources", sources));

        Flux<ServerSentEvent<String>> body = outputs.concatMap(output -> {
            if (output instanceof StreamingOutput<?> streamingOutput) {
                if (streamingOutput.getOutputType() != OutputType.GRAPH_NODE_STREAMING
                        || !ChatNode.NODE_NAME.equals(streamingOutput.node())) {
                    return Flux.empty();
                }
                String text = streamingOutput.message() != null
                        ? streamingOutput.message().getText()
                        : streamingOutput.chunk();
                return text == null || text.isEmpty() ? Flux.empty() : Flux.just(event("token", Map.of("text", text)));
            }
            OverAllState state = output.state();
            if (state != null && !promptSent.get()) {
                Object prompt = state.value(PromptTemplateNode.NODE_CONTENT).orElse(null);
                if (prompt != null && promptSent.compareAndSet(false, true)) {
                    return Flux.just(event("prompt", prompt));
                }
            }
            return Flux.empty();
        });

        return head
                .concatWith(body)
                .concatWith(Flux.just(event("done", Map.of("traceId", traceId))))
                .onErrorResume(e -> {
                    logger.error("traceId:{} - graph stream failed", traceId, e);
                    // Details stay in the log; exception messages can expose internal hosts
                    return Flux.just(event("error", Map.of("message", "The answer could not be generated. Please try again.")));
                });
    }

    private static ServerSentEvent<String> event(String name, Object payload) {
        try {
            return ServerSentEvent.builder(objectMapper.writeValueAsString(payload)).event(name).build();
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
