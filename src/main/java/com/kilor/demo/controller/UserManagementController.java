package com.kilor.demo.controller;

import com.kilor.demo.entity.LoginLog;
import com.kilor.demo.entity.User;
import com.kilor.demo.service.UserService;
import com.kilor.demo.repository.UserRepository;
import com.kilor.demo.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/users")
public class UserManagementController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private UserService userService;

    private String getUsername(HttpServletRequest request) {
        String token = request.getHeader("Authorization");
        if (token == null || !token.startsWith("Bearer ")) return null;
        token = token.substring(7);
        if (!jwtUtil.validateToken(token)) return null;
        return jwtUtil.getUsernameFromToken(token);
    }

    private boolean isAdmin(HttpServletRequest request) {
        String currentUser = getUsername(request);
        if (currentUser == null) return false;
        User user = userRepository.findByUsername(currentUser).orElse(null);
        return user != null && "admin".equals(user.getRole());
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

    private List<Map<String, Object>> userList(List<User> users) {
        return users.stream().map(u -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", u.getId());
            m.put("username", u.getUsername());
            m.put("role", u.getRole());
            return m;
        }).collect(Collectors.toList());
    }

    // GET /api/admin/users - list all users
    @GetMapping
    public Map<String, Object> listUsers(HttpServletRequest request) {
        if (!isAdmin(request)) return errorResponse("需要管理员权限");
        List<User> users = userRepository.findAll();
        return okResponse("users", userList(users));
    }

    // POST /api/admin/users - create user
    @PostMapping
    public Map<String, Object> createUser(HttpServletRequest request, @RequestBody Map<String, String> payload) {
        if (!isAdmin(request)) return errorResponse("需要管理员权限");
        String username = payload.get("username");
        String password = payload.get("password");
        String role = (String) payload.get("role");
        if (role == null || role.isEmpty()) role = "user";

        if (username == null || username.trim().isEmpty()) return errorResponse("用户名不能为空");
        if (password == null || password.trim().isEmpty()) return errorResponse("密码不能为空");
        if (userRepository.findByUsername(username).isPresent()) return errorResponse("用户名已存在");

        User user = new User();
        user.setUsername(username.trim());
        user.setPassword(password);
        user.setRole(role);
        userRepository.save(user);

        List<User> singleUser = new ArrayList<>();
        singleUser.add(user);
        return okResponse("user", userList(singleUser).get(0));
    }

    // PUT /api/admin/users/{id} - update user
    @PutMapping("/{id}")
    public Map<String, Object> updateUser(HttpServletRequest request, @PathVariable Long id, @RequestBody Map<String, String> payload) {
        if (!isAdmin(request)) return errorResponse("需要管理员权限");
        User user = userRepository.findById(id).orElse(null);
        if (user == null) return errorResponse("用户不存在");

        if (payload.containsKey("password") && payload.get("password") != null && !payload.get("password").isEmpty()) {
            user.setPassword(payload.get("password"));
        }
        if (payload.containsKey("role")) {
            user.setRole(payload.get("role"));
        }
        userRepository.save(user);

        List<User> updatedUser = new ArrayList<>();
        updatedUser.add(user);
        return okResponse("user", userList(updatedUser).get(0));
    }

    // DELETE /api/admin/users/{id} - delete user
    @DeleteMapping("/{id}")
    public Map<String, Object> deleteUser(HttpServletRequest request, @PathVariable Long id) {
        if (!isAdmin(request)) return errorResponse("需要管理员权限");
        User user = userRepository.findById(id).orElse(null);
        if (user == null) return errorResponse("用户不存在");

        userRepository.delete(user);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("ok", true);
        r.put("deletedId", id);
        return r;
    }

    // GET /api/admin/login-logs - recent login logs
    @GetMapping("/login-logs")
    public Map<String, Object> getLoginLogs(HttpServletRequest request,
                                            @RequestParam(defaultValue = "7") int days) {
        if (!isAdmin(request)) return errorResponse("需要管理员权限");
        List<LoginLog> logs = userService.getRecentLoginLogs(days);
        List<Map<String, Object>> result = logs.stream().map(l -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", l.getId());
            m.put("username", l.getUsername());
            m.put("loginTime", l.getLoginTime());
            m.put("ip", l.getIp());
            m.put("success", l.isSuccess());
            return m;
        }).collect(Collectors.toList());
        return okResponse("logs", result);
    }
}
