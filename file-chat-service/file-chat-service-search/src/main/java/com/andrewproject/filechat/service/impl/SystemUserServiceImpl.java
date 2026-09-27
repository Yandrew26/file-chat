package com.andrewproject.filechat.service.impl;

import com.andrewproject.filechat.dto.SystemUserDTO;
import com.andrewproject.filechat.entity.SystemUser;
import com.andrewproject.filechat.mapper.SystemUserMapper;
import com.andrewproject.filechat.service.SystemUserService;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

@Service
public class SystemUserServiceImpl extends ServiceImpl<SystemUserMapper, SystemUser> implements SystemUserService {
    @Override
    public SystemUserDTO getUserByUserId(String userId) {
        SystemUser systemUser = super.lambdaQuery()
                .eq(SystemUser::getUserId, userId)
                .one();
        return systemUser == null ? null : new SystemUserDTO(systemUser);
    }
}
