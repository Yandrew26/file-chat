package com.andrewproject.filechat.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
@TableName("rag_auth_base")
public class RagAuthBase {

    private Long id;

    private String authName;

    private String authId;

    private String secretKey;

    private Boolean enabledStatus;

    private LocalDateTime createdDate;

    private LocalDateTime updatedDate;
}
