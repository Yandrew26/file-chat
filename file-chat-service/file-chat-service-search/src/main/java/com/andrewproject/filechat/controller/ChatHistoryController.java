package com.andrewproject.filechat.controller;

import com.andrewproject.filechat.dto.ChatHistoryDTO;
import com.andrewproject.filechat.dto.ChatHistoryPageDTO;
import com.andrewproject.filechat.dto.PageDTO;
import com.andrewproject.filechat.service.ChatHistoryService;
import jakarta.annotation.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/history")
public class ChatHistoryController {

    @Resource
    private ChatHistoryService chatHistoryService;

    @GetMapping("/getByConversationId")
    public ResponseEntity<List<ChatHistoryDTO>> getByConversationId(@RequestParam("conversationId") String conversationId) {
        return ResponseEntity.ok(chatHistoryService.getByConversationId(conversationId));
    }

    @GetMapping("/pages")
    public ResponseEntity<PageDTO<ChatHistoryPageDTO>> pages(@RequestParam("pageNum") Long pageNum,
                                                             @RequestParam("pageSize") Long pageSize,
                                                             @RequestParam("userId") String userid,
                                                             @RequestParam(value = "dateStart", required = false) String dateStart,
                                                             @RequestParam(value = "dateEnd", required = false) String dateEnd) {
        return ResponseEntity.ok(chatHistoryService.pages(pageNum, pageSize, userid, dateStart, dateEnd));
    }
}
