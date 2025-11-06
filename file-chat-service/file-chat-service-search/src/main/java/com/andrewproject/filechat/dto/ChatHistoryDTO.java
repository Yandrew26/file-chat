package com.andrewproject.filechat.dto;

import com.andrewproject.filechat.entity.ChatHistory;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@ToString
@Builder
public class ChatHistoryDTO {

    private Long id;

    private String userName;

    private String userId;

    private String conversationId;

    private String traceId;

    private String content;

    private String type;

    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(
            pattern = "yyyy-MM-dd HH:mm:ss",
            timezone = "GMT+8"
    )
    private LocalDateTime createdDate;

    public ChatHistoryDTO(ChatHistory chatHistory) {
        this.id = chatHistory.getId();
        this.userId = chatHistory.getUserId();
        this.userName = chatHistory.getUserName();
        this.conversationId = chatHistory.getConversationId();
        this.traceId = chatHistory.getTraceId();
        this.content = chatHistory.getContent();
        this.type = chatHistory.getType();
        this.createdDate = chatHistory.getCreatedDate();
    }
}
