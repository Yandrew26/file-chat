package com.andrewproject.filechat.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
@TableName("system_user")
public class SystemUser {

    @TableId(
            type = IdType.ASSIGN_ID
    )
    private Long id;

    private String userId;

    private String userName;

    private String email;

    private String mobile;

    private LocalDateTime createdDate;
}
