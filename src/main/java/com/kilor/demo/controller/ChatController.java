package com.kilor.demo.controller;

import com.kilor.demo.entity.ChatMessage;
import com.kilor.demo.entity.User;
import com.kilor.demo.repository.ChatMessageRepository;
import com.kilor.demo.repository.UserRepository;
import com.kilor.demo.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;

import org.springframework.transaction.annotation.Transactional;

import javax.servlet.http.HttpServletRequest;
import java.util.*;
import java.util.stream.Collectors;

@RestController
public class ChatController {

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private com.kilor.demo.service.OnlineUserService onlineUserService;

    // WebSocket: send message to user
    @MessageMapping("/chat.send")
    public void sendMessage(@Payload Map<String, String> payload) {
        String from = payload.get("from");
        String to = payload.get("to");
        String content = payload.get("content");

        ChatMessage msg = new ChatMessage();
        msg.setFromUser(from);
        msg.setToUser(to);
        msg.setContent(content);
        msg.setReaded(false);
        chatMessageRepository.save(msg);

        messagingTemplate.convertAndSend("/topic/chat." + to, msg);
    }

    // REST: get conversation history
    @GetMapping("/api/chat/history")
    @Transactional
    public Map<String, Object> getHistory(HttpServletRequest request, @RequestParam String username) {
        String currentUser = getUsername(request);
        if (currentUser == null) return errorResponse("未登录");

        List<ChatMessage> messages = chatMessageRepository.findConversation(currentUser, username);
        chatMessageRepository.markAsRead(username, currentUser);

        List<Map<String, Object>> result = messages.stream().map(m -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", m.getId());
            map.put("from", m.getFromUser());
            map.put("to", m.getToUser());
            map.put("content", m.getContent());
            map.put("time", m.getTimestamp().getTime());
            map.put("readed", m.getReaded());
            return map;
        }).collect(Collectors.toList());

        return okResponse("messages", result);
    }

    // REST: get all users for chat list
    @GetMapping("/api/chat/users")
    public Map<String, Object> getUsers(HttpServletRequest request) {
        String currentUser = getUsername(request);
        if (currentUser == null) return errorResponse("未登录");

        List<User> users = userRepository.findAll();
        List<Map<String, Object>> result = new ArrayList<>();
        for (User u : users) {
            if (u.getUsername().equals(currentUser)) continue;
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("username", u.getUsername());
            item.put("id", u.getId());
            item.put("online", onlineUserService.isOnline(u.getUsername()));
            result.add(item);
        }
        return okResponse("users", result);
    }

    // REST: get unread counts
    @GetMapping("/api/chat/recent")
    public Map<String, Object> getRecent(HttpServletRequest request) {
        String currentUser = getUsername(request);
        if (currentUser == null) return errorResponse("未登录");

        List<String> senders = chatMessageRepository.findUnreadSenders(currentUser);
        Map<String, Object> result = new LinkedHashMap<>();
        for (String sender : senders) {
            Long count = chatMessageRepository.countUnread(currentUser, sender);
            result.put(sender, count);
        }
        return okResponse("recent", result);
    }

    // REST: send message (fallback when WebSocket not connected)
    @PostMapping("/api/chat/send")
    public Map<String, Object> sendHttp(@RequestBody Map<String, String> payload, HttpServletRequest request) {
        String currentUser = getUsername(request);
        if (currentUser == null) return errorResponse("未登录");

        String from = payload.get("from");
        String to = payload.get("to");
        String content = payload.get("content");

        if (!currentUser.equals(from)) return errorResponse("发送者不匹配");
        if (to == null || content == null) return errorResponse("参数不完整");

        ChatMessage msg = new ChatMessage();
        msg.setFromUser(from);
        msg.setToUser(to);
        msg.setContent(content);
        msg.setReaded(false);
        chatMessageRepository.save(msg);

        messagingTemplate.convertAndSend("/topic/chat." + to, msg);
        messagingTemplate.convertAndSend("/topic/chat." + from, msg);

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("ok", true);
        r.put("id", msg.getId());
        return r;
    }

    private String getUsername(HttpServletRequest request) {
        String token = request.getHeader("Authorization");
        if (token == null || !token.startsWith("Bearer ")) return null;
        token = token.substring(7);
        if (!jwtUtil.validateToken(token)) return null;
        return jwtUtil.getUsernameFromToken(token);
    }

    private Map<String, Object> okResponse(String key, Object value) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("ok", true);
        r.put(key, value);
        return r;
    }

    private Map<String, Object> errorResponse(String msg) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("ok", false);
        r.put("error", msg);
        return r;
    }
}
