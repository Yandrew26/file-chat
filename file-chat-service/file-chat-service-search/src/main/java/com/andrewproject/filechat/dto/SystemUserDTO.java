package com.andrewproject.filechat.dto;

import com.andrewproject.filechat.entity.SystemUser;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@ToString
@Builder
public class SystemUserDTO {

    private String userId;

    private String userName;

    public SystemUserDTO(SystemUser systemUser) {
        this.userId = systemUser.getUserId();
        this.userName = systemUser.getUserName();
    }
}
