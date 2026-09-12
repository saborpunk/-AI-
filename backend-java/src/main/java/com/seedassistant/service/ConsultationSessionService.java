package com.seedassistant.service;

import com.seedassistant.common.BusinessException;
import com.seedassistant.dto.command.*;
import com.seedassistant.dto.response.PageResponse;
import com.seedassistant.entity.ConsultationSession;
import com.seedassistant.mapper.*;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class ConsultationSessionService {
    private final ConsultationSessionMapper mapper;
    private final TransactionTemplate transaction;

    public ConsultationSessionService(ConsultationSessionMapper mapper, PlatformTransactionManager manager) {
        this.mapper = mapper;
        this.transaction = new TransactionTemplate(manager);
    }

    public ConsultationSession create(ConsultationSessionCreateRequest request) {
        // 会话是独立业务记录，创建与查询不需要模型或Python服务。
        String id=UUID.randomUUID().toString();
        return transaction.execute(tx -> {
            mapper.insertRow(id,request.getTitle().strip(), request.getNotes());
            return get(id);
        });
    }

    public ConsultationSession get(String id) {
        var row=mapper.selectById(id);
        if(row==null) throw new BusinessException(404,"SESSION_NOT_FOUND","记录不存在");
        return row;
    }

    public PageResponse<ConsultationSession> search(String keyword,String status,int page,int size) {
        if(status!=null) validateStatus(status);
        var rows=mapper.search(keyword,status,size+1,(page-1)*size);
        return new PageResponse<>(rows.stream().limit(size).toList(),page,size,rows.size()>size);
    }

    public ConsultationSession update(String id,ConsultationSessionUpdateRequest request) {
        get(id);
        return transaction.execute(tx -> {
            check(mapper.updateVersioned(id,request.getTitle().strip(), request.getNotes(),request.getVersion()));
            return get(id);
        });
    }

    public ConsultationSession status(String id,StatusRequest request) {
        validateStatus(request.getStatus());
        get(id);
        return transaction.execute(tx -> {
            check(mapper.status(id,request.getStatus(),request.getVersion()));
            return get(id);
        });
    }

    public void delete(String id,long version) {
        get(id);
        transaction.executeWithoutResult(tx -> check(mapper.deleteVersioned(id,version)));
    }

    private void validateStatus(String status) {
        if(!"OPEN".equals(status)&&!"CLOSED".equals(status)) throw new BusinessException(400,"INVALID_REQUEST","状态须为OPEN或CLOSED");
    }

    private void check(int rows) {
        if(rows!=1) throw new BusinessException(409,"VERSION_CONFLICT","记录已变化，请重新查询");
    }

}
