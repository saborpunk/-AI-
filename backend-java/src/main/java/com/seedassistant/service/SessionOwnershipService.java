package com.seedassistant.service;

import com.seedassistant.common.BusinessException;
import com.seedassistant.entity.ConsultationSession;
import com.seedassistant.mapper.ConsultationSessionMapper;
import com.seedassistant.mapper.UserAccountMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 内部迁移入口，不暴露HTTP接口。V3须从认证上下文取得用户，不能信任前端传来的用户ID。 */
@Service
public class SessionOwnershipService {
    private final ConsultationSessionMapper sessions;
    private final UserAccountMapper users;
    private final TransactionTemplate transaction;

    public SessionOwnershipService(ConsultationSessionMapper sessions, UserAccountMapper users,
                                   PlatformTransactionManager manager) {
        this.sessions = sessions;
        this.users = users;
        this.transaction = new TransactionTemplate(manager);
    }

    public ConsultationSession assignUnowned(String sessionId, String userId, long version) {
        if (version < 0 || sessionId == null || userId == null) {
            throw new BusinessException(400, "INVALID_REQUEST", "用户、会话和版本不能为空且版本须非负");
        }
        return transaction.execute(tx -> {
            if (sessions.selectById(sessionId) == null) {
                throw new BusinessException(404, "SESSION_NOT_FOUND", "会话不存在");
            }
            // 本阶段只迁移已核实的归属关系，账号禁用与访问控制在认证阶段处理。
            if (users.selectById(userId) == null) {
                throw new BusinessException(404, "USER_NOT_FOUND", "用户不存在");
            }
            if (sessions.assignUnowned(sessionId, userId, version) != 1) {
                throw new BusinessException(409, "VERSION_CONFLICT", "会话已变化或已有归属");
            }
            return sessions.selectById(sessionId);
        });
    }
}
