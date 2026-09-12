package com.seedassistant.service;

import com.seedassistant.common.BusinessException;
import com.seedassistant.dto.command.*;
import com.seedassistant.dto.response.PageResponse;
import com.seedassistant.entity.ArticleCategory;
import com.seedassistant.mapper.ArticleCategoryMapper;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class ArticleCategoryService {
    private final ArticleCategoryMapper mapper;
    private final TransactionTemplate transaction;
    public ArticleCategoryService(ArticleCategoryMapper mapper, PlatformTransactionManager manager) {
        this.mapper = mapper;
        this.transaction = new TransactionTemplate(manager);
    }

    public ArticleCategory create(CategoryCreateRequest request) {
        String id=UUID.randomUUID().toString();
        return transaction.execute(tx -> {
            mapper.insertRow(id,request.getName().strip(),request.getDescription());
            return get(id);
        });
    }

    public ArticleCategory get(String id) {
        ArticleCategory row=mapper.selectById(id);
        if(row==null) throw new BusinessException(404,"CATEGORY_NOT_FOUND","分类不存在");
        return row;
    }

    public PageResponse<ArticleCategory> search(String keyword,String status,int page,int size) {
        if(status!=null) validateStatus(status);
        var rows=mapper.search(keyword,status,size+1,(page-1)*size);
        return new PageResponse<>(rows.stream().limit(size).toList(),page,size,rows.size()>size);
    }

    public ArticleCategory update(String id,CategoryUpdateRequest request) {
        get(id);
        return transaction.execute(tx -> {
            check(mapper.updateVersioned(id,request.getName().strip(),request.getDescription(),request.getVersion()));
            return get(id);
        });
    }

    public ArticleCategory status(String id,StatusRequest request) {
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
        if(!"ENABLED".equals(status)&&!"DISABLED".equals(status)) throw new BusinessException(400,"INVALID_REQUEST","分类状态须为ENABLED或DISABLED");
    }

    private void check(int rows) {
        // 判断SQL实际生效的行数，避免把旧版本更新误报为成功。
        if(rows!=1) throw new BusinessException(409,"VERSION_CONFLICT","记录已变化，请重新查询");
    }
}
