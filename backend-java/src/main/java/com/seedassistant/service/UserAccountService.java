package com.seedassistant.service;

import com.seedassistant.common.BusinessException;
import com.seedassistant.dto.response.UserResponse;
import com.seedassistant.entity.UserAccount;
import com.seedassistant.mapper.UserAccountMapper;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** V2内部数据服务。尚无公开注册接口，V3在此基础上加入认证。 */
@Service
public class UserAccountService {
    private final UserAccountMapper mapper;
    private final PasswordEncoder passwords;
    private final TransactionTemplate transaction;

    public UserAccountService(UserAccountMapper mapper, PasswordEncoder passwords, PlatformTransactionManager manager) {
        this.mapper = mapper;
        this.passwords = passwords;
        this.transaction = new TransactionTemplate(manager);
    }

    public UserResponse createCustomer(String username, String password, String displayName) {
        String normalized = normalizeUsername(username);
        if (password == null || password.length() < 12 || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw invalid("密码至少12个字符，UTF-8编码最多72字节");
        }
        if (displayName == null || displayName.isBlank() || displayName.length() > 80) {
            throw invalid("昵称不能为空且最多80个字符");
        }
        UserAccount account = new UserAccount();
        account.setId(UUID.randomUUID().toString());
        account.setUsername(normalized);
        account.setDisplayName(displayName.strip());
        // BCrypt刻意消耗CPU，在开启数据库事务前计算，避免占用连接等待。
        account.setPasswordHash(passwords.encode(password));
        try {
            return transaction.execute(tx -> {
                mapper.insertCustomer(account);
                return get(account.getId());
            });
        } catch (DuplicateKeyException conflict) {
            // 唯一索引是并发创建同名用户时的最终保障。
            throw new BusinessException(409, "USERNAME_EXISTS", "用户名已存在");
        }
    }

    public UserResponse get(String id) {
        UserAccount account = mapper.selectById(id);
        if (account == null) throw new BusinessException(404, "USER_NOT_FOUND", "用户不存在");
        UserResponse response = new UserResponse();
        response.setId(account.getId());
        response.setUsername(account.getUsername());
        response.setDisplayName(account.getDisplayName());
        response.setRole(account.getRole());
        response.setStatus(account.getStatus());
        response.setVersion(account.getVersion());
        response.setCreatedAt(account.getCreatedAt());
        return response;
    }

    private String normalizeUsername(String value) {
        if (value == null) throw invalid("用户名不能为空");
        String normalized = value.strip().toLowerCase(Locale.ROOT);
        if (!normalized.matches("[a-z0-9_]{3,32}")) throw invalid("用户名须为3至32位字母、数字或下划线");
        return normalized;
    }

    private BusinessException invalid(String message) {
        return new BusinessException(400, "INVALID_REQUEST", message);
    }
}
