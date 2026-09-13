package com.seedassistant.service;

import com.seedassistant.common.BusinessException;
import com.seedassistant.dto.command.*;
import com.seedassistant.dto.response.PageResponse;
import com.seedassistant.entity.ConsultationSession;
import com.seedassistant.mapper.ConsultationSessionMapper;
import com.seedassistant.security.CurrentUser;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** HTTP入口统一通过这一层检查归属，复用已有CRUD与乐观锁规则。 */
@Service
public class SessionAccessService {
    private final ConsultationSessionService business;
    private final ConsultationSessionMapper mapper;
    private final TransactionTemplate transaction;
    public SessionAccessService(ConsultationSessionService business, ConsultationSessionMapper mapper, PlatformTransactionManager manager) {
        this.business = business; this.mapper = mapper; this.transaction = new TransactionTemplate(manager);
    }
    public ConsultationSession create(ConsultationSessionCreateRequest request, CurrentUser user) {
        String id = UUID.randomUUID().toString();
        return transaction.execute(tx -> {
            mapper.insertOwned(id, request.getTitle().strip(), request.getNotes(), user.getId());
            return business.get(id);
        });
    }
    public ConsultationSession get(String id, CurrentUser user) {
        var row = business.get(id);
        // 对无权访问和不存在返回相同404，不泄露其他客户记录是否存在。
        if (!user.isMerchant() && !user.getId().equals(row.getUserId())) {
            throw new BusinessException(404, "SESSION_NOT_FOUND", "会话不存在");
        }
        return row;
    }
    public PageResponse<ConsultationSession> search(String keyword, String status, int page, int size, CurrentUser user) {
        if (user.isMerchant()) return business.search(keyword, status, page, size);
        if (status != null && !"OPEN".equals(status) && !"CLOSED".equals(status)) {
            throw new BusinessException(400, "INVALID_REQUEST", "状态须为OPEN或CLOSED");
        }
        var rows = mapper.searchOwned(user.getId(), keyword, status, size + 1, (page - 1) * size);
        return new PageResponse<>(rows.stream().limit(size).toList(), page, size, rows.size() > size);
    }
    public ConsultationSession update(String id, ConsultationSessionUpdateRequest request, CurrentUser user) {
        get(id, user);
        return business.update(id, request);
    }
    public ConsultationSession status(String id, StatusRequest request, CurrentUser user) {
        get(id, user);
        return business.status(id, request);
    }
    public void delete(String id, long version, CurrentUser user) {
        get(id, user);
        business.delete(id, version);
    }
}
