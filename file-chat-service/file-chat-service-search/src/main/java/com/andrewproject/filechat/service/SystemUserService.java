package com.andrewproject.filechat.service;

import com.andrewproject.filechat.dto.SystemUserDTO;
import com.andrewproject.filechat.entity.SystemUser;
import com.baomidou.mybatisplus.spring.service.IService;

public interface SystemUserService extends IService<SystemUser> {

    SystemUserDTO getUserByUserId(String userId);
}
