package com.andrewproject.filechat.service;

import com.andrewproject.filechat.dto.ChatHistoryDTO;
import com.andrewproject.filechat.dto.ChatHistoryPageDTO;
import com.andrewproject.filechat.dto.PageDTO;
import com.andrewproject.filechat.entity.ChatHistory;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;
import java.util.Map;

public interface ChatHistoryService extends IService<ChatHistory> {
    void addChatHistory(ChatHistoryDTO chatHistoryDTO);
    List<ChatHistoryDTO> getByConversationId(String conversationId);
    PageDTO<ChatHistoryPageDTO> pages(Long pageNum, Long pageSize, String userid, String DateStart, String dateEnd);
}
