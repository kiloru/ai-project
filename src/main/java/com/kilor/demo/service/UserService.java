package com.kilor.demo.service;

import com.kilor.demo.dto.LoginRequest;
import com.kilor.demo.dto.LoginResponse;
import com.kilor.demo.entity.User;
import com.kilor.demo.repository.UserRepository;
import com.kilor.demo.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class UserService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtUtil jwtUtil;

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
}
