package com.andrewproject.filechat.service.impl;

import com.andrewproject.filechat.dto.ChatHistoryDTO;
import com.andrewproject.filechat.dto.ChatHistoryPageDTO;
import com.andrewproject.filechat.dto.PageDTO;
import com.andrewproject.filechat.entity.ChatHistory;
import com.andrewproject.filechat.mapper.ChatHistoryMapper;
import com.andrewproject.filechat.service.ChatHistoryService;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class ChatHistoryServiceImpl extends ServiceImpl<ChatHistoryMapper, ChatHistory> implements ChatHistoryService {

    @Resource
    private ChatHistoryMapper chatHistoryMapper;

    @Override
    public void addChatHistory(ChatHistoryDTO chatHistoryDTO) {
        ChatHistory chatHistory = new ChatHistory();
        BeanUtils.copyProperties(chatHistoryDTO, chatHistory);
        super.save(chatHistory);
    }

    @Override
    public List<ChatHistoryDTO> getByConversationId(String conversationId) {
        return super.lambdaQuery()
                .eq(ChatHistory::getConversationId, conversationId)
                .orderByAsc(ChatHistory::getCreatedDate)
                .list()
                .stream()
                .map(ChatHistoryDTO::new)
                .collect(Collectors.toList());
    }

    @Override
    public PageDTO<ChatHistoryPageDTO> pages(Long pageNum, Long pageSize, String userid, String dateStart, String dateEnd) {
        List<ChatHistoryPageDTO> resultList = chatHistoryMapper.selectChatHistoryGroupByConversationID(userid,
                (Math.max(pageNum, 1) - 1) * pageSize, pageSize, dateStart, dateEnd);
        Long count = chatHistoryMapper.selectChatHistoryGroupByConversationIDCount(userid, dateStart, dateEnd);
        return PageDTO.of(resultList, pageNum, pageSize, count, getPages(count, pageSize));
    }

    private Long getPages(Long count, Long pageSize) {
        Long result = 0L;
        if (count == 0) {
            return 0L;
        }
        if (count % pageSize == 0) {
            return count / pageSize;
        } else  {
            return (count / pageSize) + 1;
        }
    }

}
