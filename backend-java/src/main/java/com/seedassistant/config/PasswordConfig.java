package com.seedassistant.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class PasswordConfig {
    @Bean
    PasswordEncoder passwordEncoder() {
        // 随机盐由编码器生成并包含在哈希内，不保存原始密码或自行管理盐。
        return new BCryptPasswordEncoder(12);
    }
}
