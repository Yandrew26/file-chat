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
public class ChatHistoryPageDTO {

    private String userName;

    private String userId;

    private String conversationId;

    private String content;

    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(
            pattern = "yyyy-MM-dd HH:mm:ss",
            timezone = "GMT+8"
    )
    private LocalDateTime createdDate;

    public ChatHistoryPageDTO(ChatHistory entity) {
        this.userId = entity.getUserId();
        this.userName = entity.getUserName();
        this.conversationId = entity.getConversationId();
        this.content = entity.getContent();
        this.createdDate = entity.getCreatedDate();
    }
}
