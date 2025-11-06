package com.andrewproject.filechat.dto;

import com.andrewproject.filechat.entity.RagAuthBase;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@ToString
@Builder
public class RagAuthBaseDTO {

    private Long id;

    private String authName;

    private String authId;

    private String secretKey;

    private Boolean enabledStatus;

    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(
            pattern = "yyyy-MM-dd HH:mm:ss",
            timezone = "GMT+8"
    )
    private LocalDateTime createdDate;

    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(
            pattern = "yyyy-MM-dd HH:mm:ss",
            timezone = "GMT+8"
    )
    private LocalDateTime updatedDate;

    public RagAuthBaseDTO(RagAuthBase entity) {
        this.id = entity.getId();
        this.authName = entity.getAuthName();
        this.authId = entity.getAuthId();
        this.secretKey = entity.getSecretKey();
        this.enabledStatus = entity.getEnabledStatus();
        this.createdDate = entity.getCreatedDate();
        this.updatedDate = entity.getUpdatedDate();
    }
}
