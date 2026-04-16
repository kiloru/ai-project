package com.kilor.demo.service;

import com.kilor.demo.dto.LoginRequest;
import com.kilor.demo.dto.LoginResponse;
import com.kilor.demo.entity.LoginLog;
import com.kilor.demo.entity.User;
import com.kilor.demo.repository.LoginLogRepository;
import com.kilor.demo.repository.UserRepository;
import com.kilor.demo.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class UserService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private LoginLogRepository loginLogRepository;

    public LoginResponse login(LoginRequest request) {
        Optional<User> userOpt = userRepository.findByUsername(request.getUsername());
        if (userOpt.isPresent() && userOpt.get().getPassword().equals(request.getPassword())) {
            User user = userOpt.get();
            String token = jwtUtil.generateToken(request.getUsername());
            return new LoginResponse(token, request.getUsername(), user.getRole());
        }
        return null;
    }

    public User getUserInfo(String username) {
        return userRepository.findByUsername(username).orElse(null);
    }

    public void recordLogin(String username, String ip, String userAgent, boolean success) {
        LoginLog log = new LoginLog();
        log.setUsername(username);
        log.setLoginTime(LocalDateTime.now());
        log.setIp(ip);
        log.setUserAgent(userAgent != null && userAgent.length() > 255 ? userAgent.substring(0, 255) : userAgent);
        log.setSuccess(success);
        loginLogRepository.save(log);
    }

    public List<LoginLog> getRecentLoginLogs(int days) {
        if (days <= 0) days = 7;
        LocalDateTime cutoff = LocalDateTime.now().minusDays(days);
        return loginLogRepository.findByLoginTimeAfterOrderByLoginTimeDesc(cutoff);
    }
}
