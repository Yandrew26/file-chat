package com.andrewproject.filechat.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
@TableName("rag_chat_history")
public class ChatHistory {

    @TableId(
            type = IdType.ASSIGN_ID
    )
    private Long id;

    private String userId;

    private String userName;

    private String conversationId;

    private String traceId;

    private String content;

    private String type;

    private LocalDateTime createdDate;
}
