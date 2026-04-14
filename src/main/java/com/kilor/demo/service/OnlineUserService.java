package com.kilor.demo.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class OnlineUserService {

    private final Map<String, Integer> onlineUsers = new LinkedHashMap<>();

    @Lazy
    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    public synchronized void userConnected(String username) {
        onlineUsers.put(username, onlineUsers.getOrDefault(username, 0) + 1);
        broadcastStatus(username, true);
    }

    public synchronized void userDisconnected(String username) {
        Integer count = onlineUsers.get(username);
        if (count != null) {
            if (count <= 1) {
                onlineUsers.remove(username);
                broadcastStatus(username, false);
            } else {
                onlineUsers.put(username, count - 1);
            }
        }
    }

    public synchronized boolean isOnline(String username) {
        return onlineUsers.getOrDefault(username, 0) > 0;
    }

    private void broadcastStatus(String username, boolean online) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("username", username);
        payload.put("online", online);
        messagingTemplate.convertAndSend("/topic/chat.online-status", payload);
    }
}
