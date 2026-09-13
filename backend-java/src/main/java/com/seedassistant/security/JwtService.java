package com.seedassistant.security;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;

@Service
public class JwtService {
    public static final long EXPIRES_IN = 900;
    private final JwtEncoder encoder;
    private final Clock clock;
    public JwtService(JwtEncoder encoder, Clock clock) { this.encoder = encoder; this.clock = clock; }
    public String issue(String userId) {
        Instant now = Instant.now(clock);
        var claims = JwtClaimsSet.builder().issuer("seed-assistant").audience(List.of("seed-web"))
                .subject(userId).issuedAt(now).expiresAt(now.plusSeconds(EXPIRES_IN)).build();
        // Token不保存密码或角色，权限和禁用状态每次从数据库读取。
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }
}
