package com.andrewproject.filechat.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient(value = "http://localhost:8082")
public interface UploadFeign {

    @GetMapping("/upload/search/string")
    List<String> searchString(@RequestParam("query") String query,
                              @RequestParam("conversationId") String conversationId);

    @PostMapping("/upload/pdf")
    ResponseEntity<String> pdfUpload(@RequestBody byte[] file,
                                     @RequestParam("conversationId") String conversationId);
}
