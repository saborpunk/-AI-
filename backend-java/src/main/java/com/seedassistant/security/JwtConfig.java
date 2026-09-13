package com.seedassistant.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;

@Configuration
public class JwtConfig {
    @Bean Clock clock() { return Clock.systemUTC(); }

    @Bean SecretKey jwtKey(@Value("${auth.jwt.secret-base64:}") String configured) {
        byte[] bytes;
        try { bytes = Base64.getDecoder().decode(configured); }
        catch (IllegalArgumentException error) { throw new IllegalStateException("JWT key must be Base64; run scripts/initialize-auth.ps1"); }
        if (bytes.length < 32) throw new IllegalStateException("JWT key must contain at least 32 random bytes; run scripts/initialize-auth.ps1");
        return new SecretKeySpec(bytes, "HmacSHA256");
    }

    @Bean JwtEncoder jwtEncoder(SecretKey key) { return new NimbusJwtEncoder(new ImmutableSecret<>(key)); }

    @Bean JwtDecoder jwtDecoder(SecretKey key, Clock clock) {
        var decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        var time = new JwtTimestampValidator(Duration.ZERO);
        time.setClock(clock);
        OAuth2TokenValidator<Jwt> required = jwt -> {
            try {
                UUID.fromString(jwt.getSubject());
                if (jwt.getExpiresAt() == null || jwt.getIssuedAt() == null
                        || jwt.getIssuedAt().isAfter(Instant.now(clock))
                        || !jwt.getAudience().contains("seed-web")) throw new IllegalArgumentException();
                return OAuth2TokenValidatorResult.success();
            } catch (RuntimeException error) {
                return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token"));
            }
        };
        // 明确校验签名算法、颁发者、用途和到期时间，不信任仅能解码的JWT。
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(time, new JwtIssuerValidator("seed-assistant"), required));
        return decoder;
    }
}
