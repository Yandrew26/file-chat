package com.andrewproject.filechat.mapper;

import com.andrewproject.filechat.dto.ChatHistoryPageDTO;
import com.andrewproject.filechat.entity.ChatHistory;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ChatHistoryMapper extends BaseMapper<ChatHistory> {
    @Select("WITH ranked_conversations AS (\n" +
            "SELECT *, ROW_NUMBER() OVER (PARTITION BY conversation_id ORDER BY created_date ASC) AS rn\n" +
            "FROM rag_chat_history\n" +
            "WHERE" +
            "(#{user_id} = '' OR #{user_id} IS NULL OR user_id LIKE concat('%',replace(replace(#{user_id},'%','/%'),'_','/_'),'%') ESCAPE '/') " +
            "AND (#{dateStart} IS NULL OR #{dateStart} = '' OR created_date >= #{dateStart})\n" +
            "AND (#{dateEnd} IS NULL OR #{dateEnd} = '' OR created_date <= #{dateEnd})\n" +
            ")\n" +
            "SELECT user_name, user_id, conversation_id, content, created_date\n" +
            "FROM ranked_conversations\n" +
            "WHERE rn = 1\n" +
            "ORDER BY created_date DESC\n" +
            "LIMIT #{limit}\n" +
            "OFFSET #{offset};")
    List<ChatHistoryPageDTO> selectChatHistoryGroupByConversationID(@Param("user_id") String userId,
                                                                    @Param("offset") Long pageNum,
                                                                    @Param("limit") Long pageSize,
                                                                    @Param("dateStart") String dateStart,
                                                                    @Param("dateEnd") String dateEnd);

    @Select("WITH ranked_conversations AS (\n" +
            "SELECT *, ROW_NUMBER() OVER (PARTITION BY conversation_id ORDER BY created_date ASC) AS rn\n" +
            "FROM rag_chat_history\n" +
            "WHERE" +
            "(#{user_id} = '' OR #{user_id} IS NULL OR user_id LIKE concat('%',replace(replace(#{user_id},'%','/%'),'_','/_'),'%') ESCAPE '/') " +
            "AND (#{dateStart} IS NULL OR #{dateStart} = '' OR created_date >= #{dateStart})\n" +
            "AND (#{dateEnd} IS NULL OR #{dateEnd} = '' OR created_date <= #{dateEnd})\n" +
            ")\n" +
            "SELECT COUNT(*)\n" +
            "FROM ranked_conversations\n" +
            "WHERE rn = 1;")
    Long selectChatHistoryGroupByConversationIDCount(@Param("user_id") String userId,
                                                     @Param("dateStart") String dateStart,
                                                     @Param("dateEnd") String dateEnd);}
