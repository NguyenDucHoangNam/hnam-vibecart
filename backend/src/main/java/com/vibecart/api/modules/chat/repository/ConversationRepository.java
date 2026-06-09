package com.vibecart.api.modules.chat.repository;

import com.vibecart.api.modules.chat.entity.Conversation;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Kho lưu trữ (Repository) quản lý dữ liệu MongoDB cho thực thể Conversation.
 */
@Repository
public interface ConversationRepository extends MongoRepository<Conversation, String> {

    List<Conversation> findByMemberIdsContainingOrderByUpdatedAtDesc(String memberId);

    @Query("{ 'type': 'DIRECT', 'member_ids': { $all: ?0, $size: 2 } }")
    List<Conversation> findDirectConversationsBetween(Set<String> memberIds);
}
