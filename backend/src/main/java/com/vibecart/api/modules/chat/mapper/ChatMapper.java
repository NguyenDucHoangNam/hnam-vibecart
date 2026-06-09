package com.vibecart.api.modules.chat.mapper;

import com.vibecart.api.modules.chat.entity.Conversation;
import com.vibecart.api.modules.chat.entity.Message;
import com.vibecart.api.modules.chat.dto.response.ConversationResponse;
import com.vibecart.api.modules.chat.dto.response.MessageResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Lớp ánh xạ (Mapper) chuyển đổi giữa các thực thể Chat và các đối tượng phản hồi DTO.
 */
@Mapper(componentModel = "spring")
public interface ChatMapper {

    MessageResponse toMessageResponse(Message message);

    @Mapping(target = "members", ignore = true)
    ConversationResponse toConversationResponse(Conversation conversation);
}
