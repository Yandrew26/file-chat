package com.andrewproject.filechat.feign;

import com.andrewproject.filechat.param.ReqAuthVerifyParam;
import com.andrewproject.filechat.param.RespAuthVerifyParam;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "auth", url = "http://localhost:8084")
public interface AuthClient {

    @PostMapping("/auth/verify")
    RespAuthVerifyParam verify(@RequestBody ReqAuthVerifyParam reqAuthVerifyParam);
}
