package com.kilor.demo.repository;

import com.kilor.demo.entity.LoginLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface LoginLogRepository extends JpaRepository<LoginLog, Long> {

    List<LoginLog> findAllByOrderByLoginTimeDesc();

    List<LoginLog> findByUsernameOrderByLoginTimeDesc(String username);

    List<LoginLog> findByLoginTimeAfterOrderByLoginTimeDesc(LocalDateTime after);

    List<LoginLog> findByUsernameAndLoginTimeAfterOrderByLoginTimeDesc(String username, LocalDateTime after);
}
