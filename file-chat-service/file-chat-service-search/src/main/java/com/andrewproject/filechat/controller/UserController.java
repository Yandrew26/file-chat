package com.andrewproject.filechat.controller;

import com.andrewproject.filechat.dto.SystemUserDTO;
import com.andrewproject.filechat.service.SystemUserService;
import jakarta.annotation.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/user")
public class UserController {

    @Resource
    private SystemUserService systemUserService;

    @GetMapping("/{userId}")
    public ResponseEntity<SystemUserDTO> getUser(@PathVariable("userId") String userId) {
        SystemUserDTO user = systemUserService.getUserByUserId(userId);
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        }
        return ResponseEntity.ok(user);
    }
}
