package com.seedassistant.security;

import com.seedassistant.mapper.UserAccountMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.web.filter.OncePerRequestFilter;

public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtDecoder decoder;
    private final UserAccountMapper users;
    private final SecurityErrors errors;
    public JwtAuthenticationFilter(JwtDecoder decoder, UserAccountMapper users, SecurityErrors errors) {
        this.decoder = decoder; this.users = users; this.errors = errors;
    }
    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getServletPath().equals("/api/v1/auth/login")
                || request.getServletPath().equals("/api/v1/auth/register")
                || request.getServletPath().equals("/actuator/health");
    }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null) {
            try {
                if (Collections.list(request.getHeaders("Authorization")).size() != 1
                        || !header.regionMatches(true, 0, "Bearer ", 0, 7) || header.length() > 4096) throw new BadJwtException("Invalid header");
                Jwt jwt = decoder.decode(header.substring(7));
                var account = users.selectById(jwt.getSubject());
                if (account == null || !"ENABLED".equals(account.getStatus())
                        || !("CUSTOMER".equals(account.getRole()) || "MERCHANT".equals(account.getRole()))) throw new BadJwtException("Account unavailable");
                var principal = new CurrentUser(account.getId(), account.getRole());
                var authentication = new UsernamePasswordAuthenticationToken(principal, null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + account.getRole())));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (JwtException | IllegalArgumentException invalid) {
                errors.write(request, response, 401, "UNAUTHORIZED", "登录凭证无效或已过期，请重新登录"); return;
            } catch (DataAccessResourceFailureException unavailable) {
                errors.write(request, response, 503, "DATABASE_UNAVAILABLE", "账号服务暂不可用，请稍后重试"); return;
            } catch (DataAccessException database) {
                // MyBatis可能在连接异常外再包一层，需要检查根因，不能误报为凭证错误。
                for (Throwable cause = database; cause != null; cause = cause.getCause()) {
                    if (cause instanceof DataAccessResourceFailureException
                            || cause instanceof java.sql.SQLTransientConnectionException
                            || (cause instanceof java.sql.SQLException sql && sql.getSQLState() != null && sql.getSQLState().startsWith("08"))) {
                        errors.write(request, response, 503, "DATABASE_UNAVAILABLE", "账号服务暂不可用，请稍后重试"); return;
                    }
                }
                errors.write(request, response, 500, "DATABASE_ERROR", "账号查询失败"); return;
            }
        }
        // 只处理认证阶段异常，不能把后续Controller的业务错误误报为401。
        chain.doFilter(request, response);
    }
}
