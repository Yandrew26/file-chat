package com.andrewproject.filechat.service.Impl;

import com.andrewproject.filechat.dto.RagAuthBaseDTO;
import com.andrewproject.filechat.entity.RagAuthBase;
import com.andrewproject.filechat.mapper.RagAuthBaseMapper;
import com.andrewproject.filechat.service.RagAuthBaseService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

@Service
public class RagAuthBaseServiceImpl extends ServiceImpl<RagAuthBaseMapper, RagAuthBase> implements RagAuthBaseService {

    @Override
    public RagAuthBaseDTO getByAuthId(String authId) {
        RagAuthBase ragAuthBase = this.lambdaQuery()
                .eq(RagAuthBase::getAuthId, authId)
                .eq(RagAuthBase::getEnabledStatus, true)
                .one();
        if (ragAuthBase == null) {
            return null;
        }
        return new RagAuthBaseDTO(ragAuthBase);
    }
}
