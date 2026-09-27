package com.andrewproject.filechat.controller;

import com.andrewproject.filechat.dto.ChatHistoryDTO;
import com.andrewproject.filechat.dto.ChatHistoryPageDTO;
import com.andrewproject.filechat.dto.PageDTO;
import com.andrewproject.filechat.service.ChatHistoryService;
import jakarta.annotation.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/history")
public class ChatHistoryController {

    private static final Pattern USER_ID_PATTERN = Pattern.compile("[A-Za-z0-9-]{1,64}");

    private static final Pattern CONVERSATION_ID_PATTERN = Pattern.compile("[A-Za-z0-9-]{1,64}_[A-Za-z0-9]{1,64}");

    @Resource
    private ChatHistoryService chatHistoryService;

    @GetMapping("/getByConversationId")
    public ResponseEntity<List<ChatHistoryDTO>> getByConversationId(@RequestParam("conversationId") String conversationId) {
        if (conversationId == null || !CONVERSATION_ID_PATTERN.matcher(conversationId).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid conversationId");
        }
        return ResponseEntity.ok(chatHistoryService.getByConversationId(conversationId));
    }

    @GetMapping("/pages")
    public ResponseEntity<PageDTO<ChatHistoryPageDTO>> pages(@RequestParam("pageNum") Long pageNum,
                                                             @RequestParam("pageSize") Long pageSize,
                                                             @RequestParam("userId") String userid,
                                                             @RequestParam(value = "dateStart", required = false) String dateStart,
                                                             @RequestParam(value = "dateEnd", required = false) String dateEnd) {
        // An empty userId would list every user's conversations
        if (userid == null || !USER_ID_PATTERN.matcher(userid).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid userId");
        }
        if (pageNum < 1 || pageSize < 1 || pageSize > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "pageNum must be >= 1 and pageSize between 1 and 100");
        }
        return ResponseEntity.ok(chatHistoryService.pages(pageNum, pageSize, userid, dateStart, dateEnd));
    }
}
