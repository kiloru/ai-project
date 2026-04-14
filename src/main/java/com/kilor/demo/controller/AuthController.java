package com.kilor.demo.controller;

import com.kilor.demo.dto.LoginRequest;
import com.kilor.demo.dto.LoginResponse;
import com.kilor.demo.entity.User;
import com.kilor.demo.service.UserService;
import com.kilor.demo.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class AuthController {

    @Autowired
    private UserService userService;

    @Autowired
    private JwtUtil jwtUtil;

    @PostMapping("/auth/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        LoginResponse response = userService.login(request);
        if (response != null) {
            return ResponseEntity.ok(response);
        }
        return ResponseEntity.status(401).body(Collections.singletonMap("message", "用户名或密码错误"));
    }

    @GetMapping("/user/info")
    public ResponseEntity<?> getUserInfo(HttpServletRequest request) {
        String token = request.getHeader("Authorization");
        if (token == null || !token.startsWith("Bearer ")) {
            return ResponseEntity.status(401).body(Collections.singletonMap("message", "未提供token"));
        }
        token = token.substring(7);
        if (!jwtUtil.validateToken(token)) {
            return ResponseEntity.status(401).body(Collections.singletonMap("message", "token无效或已过期"));
        }
        String username = jwtUtil.getUsernameFromToken(token);
        User user = userService.getUserInfo(username);
        if (user != null) {
            Map<String, Object> result = new HashMap<>();
            result.put("username", user.getUsername());
            result.put("id", user.getId());
            return ResponseEntity.ok(result);
        }
        return ResponseEntity.status(404).body(Collections.singletonMap("message", "用户不存在"));
    }
}
