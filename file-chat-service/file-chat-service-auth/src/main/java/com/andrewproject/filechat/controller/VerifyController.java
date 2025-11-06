package com.andrewproject.filechat.controller;

import com.andrewproject.filechat.param.ReqAuthVerifyParam;
import com.andrewproject.filechat.param.RespAuthVerifyParam;
import com.andrewproject.filechat.service.AuthService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/auth")
public class VerifyController {

    @Resource
    private AuthService authService;

    @PostMapping("/verify")
    public RespAuthVerifyParam verify(@RequestBody ReqAuthVerifyParam reqAuthVerifyParam) {
        return authService.verifyAuth(reqAuthVerifyParam);
    }
}
