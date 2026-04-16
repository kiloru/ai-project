package com.kilor.demo.entity;

import lombok.Data;

import javax.persistence.*;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "login_log", indexes = {
    @Index(name = "idx_login_log_user", columnList = "username"),
    @Index(name = "idx_login_log_time", columnList = "loginTime")
})
public class LoginLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String username;

    @Column(nullable = false)
    private LocalDateTime loginTime;

    private String ip;

    private String userAgent;

    private boolean success;
}
