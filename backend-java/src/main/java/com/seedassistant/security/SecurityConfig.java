package com.seedassistant.security;

import com.seedassistant.mapper.UserAccountMapper;
import jakarta.servlet.DispatcherType;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.*;

@Configuration
public class SecurityConfig {
    @Bean SecurityFilterChain security(HttpSecurity http, JwtDecoder decoder, UserAccountMapper users, SecurityErrors errors) throws Exception {
        return http
                // 仅从Authorization读取Token，不用Cookie认证，因此无需CSRF表单令牌。
                .csrf(csrf -> csrf.disable()).cors(cors -> cors.configurationSource(cors()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(cache -> cache.disable())
                .formLogin(form -> form.disable()).httpBasic(basic -> basic.disable()).logout(logout -> logout.disable())
                .authorizeHttpRequests(auth -> auth
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/register", "/api/v1/auth/login").permitAll()
                        .requestMatchers(HttpMethod.GET, "/actuator/health").permitAll()
                        .requestMatchers("/api/v1/users/me", "/api/v1/sessions", "/api/v1/sessions/**").hasAnyRole("CUSTOMER", "MERCHANT")
                        .requestMatchers("/api/v1/article-categories", "/api/v1/article-categories/**", "/api/v1/articles", "/api/v1/articles/**",
                                "/api/v1/consultations", "/api/v1/consultations/**", "/api/v1/germination-drafts").hasRole("MERCHANT")
                        .anyRequest().denyAll())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, error) -> errors.write(request, response, 401, "UNAUTHORIZED", "请先登录"))
                        .accessDeniedHandler((request, response, error) -> errors.write(request, response, 403, "FORBIDDEN", "当前账号没有此操作权限")))
                .addFilterBefore(new JwtAuthenticationFilter(decoder, users, errors), UsernamePasswordAuthenticationFilter.class)
                .build();
    }
    private CorsConfigurationSource cors() {
        var config = new CorsConfiguration();
        config.setAllowedOrigins(List.of("http://127.0.0.1:5173", "http://localhost:5173"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        config.setExposedHeaders(List.of("X-Request-Id", "Location"));
        config.setAllowCredentials(false);
        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
