package com.andrewproject.filechat.service;

import com.andrewproject.filechat.param.ReqAuthVerifyParam;
import com.andrewproject.filechat.param.RespAuthVerifyParam;

public interface AuthService {

    RespAuthVerifyParam verifyAuth(ReqAuthVerifyParam reqAuthVerifyParam);
}
