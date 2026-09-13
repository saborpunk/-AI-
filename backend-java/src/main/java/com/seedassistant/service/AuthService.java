package com.seedassistant.service;

import com.seedassistant.common.BusinessException;
import com.seedassistant.dto.response.LoginResponse;
import com.seedassistant.mapper.UserAccountMapper;
import com.seedassistant.security.JwtService;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
    private final UserAccountMapper users;
    private final PasswordEncoder passwords;
    private final JwtService tokens;
    private final String dummyHash;
    public AuthService(UserAccountMapper users, PasswordEncoder passwords, JwtService tokens) {
        this.users = users; this.passwords = passwords; this.tokens = tokens;
        this.dummyHash = passwords.encode(UUID.randomUUID().toString());
    }
    public LoginResponse login(String username, String password) {
        if (username == null || password == null || password.isEmpty() || password.getBytes(StandardCharsets.UTF_8).length > 72) throw rejected();
        var account = users.findByUsername(username.strip().toLowerCase(Locale.ROOT));
        // 用户不存在也执行一次哈希比对，不给出“用户名不存在”的不同提示。
        boolean matches = passwords.matches(password, account == null ? dummyHash : account.getPasswordHash());
        if (!matches || account == null || !"ENABLED".equals(account.getStatus())
                || !("CUSTOMER".equals(account.getRole()) || "MERCHANT".equals(account.getRole()))) throw rejected();
        return new LoginResponse(tokens.issue(account.getId()), JwtService.EXPIRES_IN);
    }
    private BusinessException rejected() { return new BusinessException(401, "BAD_CREDENTIALS", "用户名或密码不正确，或账号不可用"); }
}
