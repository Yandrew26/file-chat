package com.andrewproject.filechat.service.Impl;

import com.andrewproject.filechat.dto.RagAuthBaseDTO;
import com.andrewproject.filechat.param.ReqAuthVerifyParam;
import com.andrewproject.filechat.param.RespAuthVerifyParam;
import com.andrewproject.filechat.service.AuthService;
import com.andrewproject.filechat.service.RagAuthBaseService;
import jakarta.annotation.Resource;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Slf4j
@Service
public class AuthServiceImpl implements AuthService {

    @Resource
    private RagAuthBaseService ragAuthBaseService;

    @Override
    public RespAuthVerifyParam verifyAuth(ReqAuthVerifyParam reqAuthVerifyParam) {
        RagAuthBaseDTO ragAuthBaseDTO = ragAuthBaseService.getByAuthId(reqAuthVerifyParam.getAuthId());
        if (ObjectUtils.isEmpty(ragAuthBaseDTO)) {
            return RespAuthVerifyParam.builder().verifyResult(Boolean.FALSE).failMsg("Can not find auth_id").build();
        }

        String authVerifyStr = "authId=" + reqAuthVerifyParam.getAuthId()
                + "&secretKey=" + ragAuthBaseDTO.getSecretKey();
        MessageDigest md5Digest = DigestUtils.getMd5Digest();
        byte[] digest = md5Digest.digest(authVerifyStr.getBytes(StandardCharsets.UTF_8));
        String m5dStr = Hex.encodeHexString(digest);
        if (!m5dStr.equals(reqAuthVerifyParam.getAuthStr())) {
            return RespAuthVerifyParam.builder().verifyResult(Boolean.FALSE).failMsg("authorization failed").build();
        }
        return RespAuthVerifyParam.builder().verifyResult(Boolean.TRUE).build();
    }

    public static void main(String[] args) {
        String authVerifyStr = "authId=12345&secretKey=54321";
        MessageDigest md5Digest = DigestUtils.getMd5Digest();
        byte[] digest = md5Digest.digest(authVerifyStr.getBytes(StandardCharsets.UTF_8));
        String m5dStr = Hex.encodeHexString(digest);
        System.out.println(m5dStr);
    }
}
