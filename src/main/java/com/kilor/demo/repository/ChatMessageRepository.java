package com.kilor.demo.repository;

import com.kilor.demo.entity.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    @Query("SELECT m FROM ChatMessage m WHERE " +
           "(m.fromUser = :user1 AND m.toUser = :user2) OR " +
           "(m.fromUser = :user2 AND m.toUser = :user1) " +
           "ORDER BY m.timestamp ASC")
    List<ChatMessage> findConversation(@Param("user1") String user1, @Param("user2") String user2);

    @Query("SELECT DISTINCT m.fromUser FROM ChatMessage m WHERE m.toUser = :username AND m.readed = false")
    List<String> findUnreadSenders(@Param("username") String username);

    @Query("SELECT COUNT(m) FROM ChatMessage m WHERE m.toUser = :to AND m.fromUser = :from AND m.readed = false")
    Long countUnread(@Param("to") String to, @Param("from") String from);

    @Modifying
    @Query("UPDATE ChatMessage m SET m.readed = true WHERE m.fromUser = :from AND m.toUser = :to")
    void markAsRead(@Param("from") String from, @Param("to") String to);
}
