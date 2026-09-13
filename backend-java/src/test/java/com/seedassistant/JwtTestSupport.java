package com.seedassistant;

import com.seedassistant.entity.UserAccount;
import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

abstract class JwtTestSupport {
    static final String USER_ID = "00000000-0000-0000-0000-000000000001";
    private static final String KEY;
    static {
        byte[] bytes = new byte[32]; new SecureRandom().nextBytes(bytes);
        KEY = Base64.getEncoder().encodeToString(bytes);
    }
    @DynamicPropertySource static void testJwtKey(DynamicPropertyRegistry registry) {
        // Tests never rely on a developer's real signing key or commit a reusable secret.
        registry.add("auth.jwt.secret-base64", () -> KEY);
    }
    static UserAccount merchant() {
        var account = new UserAccount();
        account.setId(USER_ID); account.setUsername("synthetic");
        account.setRole("MERCHANT"); account.setStatus("ENABLED");
        return account;
    }
}
