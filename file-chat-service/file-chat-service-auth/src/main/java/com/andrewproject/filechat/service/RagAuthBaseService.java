package com.andrewproject.filechat.service;

import com.andrewproject.filechat.dto.RagAuthBaseDTO;
import com.andrewproject.filechat.entity.RagAuthBase;
import com.baomidou.mybatisplus.spring.service.IService;

public interface RagAuthBaseService extends IService<RagAuthBase> {

    RagAuthBaseDTO getByAuthId(String authId);
}
