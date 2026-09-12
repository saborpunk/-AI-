package com.seedassistant.service;

import com.seedassistant.common.BusinessException;
import com.seedassistant.dto.command.*;
import com.seedassistant.dto.response.PageResponse;
import com.seedassistant.entity.KnowledgeArticle;
import com.seedassistant.mapper.*;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class KnowledgeArticleService {
    private final KnowledgeArticleMapper mapper;
    private final TransactionTemplate transaction;
    private final ArticleCategoryMapper categories;

    public KnowledgeArticleService(KnowledgeArticleMapper mapper, PlatformTransactionManager manager, ArticleCategoryMapper categories) {
        this.mapper = mapper;
        this.transaction = new TransactionTemplate(manager);
        this.categories = categories;
    }

    public KnowledgeArticle create(KnowledgeArticleCreateRequest request) {
        String id=UUID.randomUUID().toString();
        return transaction.execute(tx -> {
            checkCategory(request.getCategoryId());
            mapper.insertRow(id,request.getTitle().strip(), request.getContent(), request.getCategoryId());
            return get(id);
        });
    }

    public KnowledgeArticle get(String id) {
        var row=mapper.selectById(id);
        if(row==null) throw new BusinessException(404,"ARTICLE_NOT_FOUND","记录不存在");
        return row;
    }

    public PageResponse<KnowledgeArticle> search(String keyword,String status,int page,int size) {
        if(status!=null) validateStatus(status);
        var rows=mapper.search(keyword,status,size+1,(page-1)*size);
        return new PageResponse<>(rows.stream().limit(size).toList(),page,size,rows.size()>size);
    }

    public KnowledgeArticle update(String id,KnowledgeArticleUpdateRequest request) {
        get(id);
        return transaction.execute(tx -> {
            checkCategory(request.getCategoryId());
            check(mapper.updateVersioned(id,request.getTitle().strip(), request.getContent(), request.getCategoryId(),request.getVersion()));
            return get(id);
        });
    }

    public KnowledgeArticle status(String id,StatusRequest request) {
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
        if(!"DRAFT".equals(status)&&!"PUBLISHED".equals(status)) throw new BusinessException(400,"INVALID_REQUEST","状态须为DRAFT或PUBLISHED");
    }

    private void check(int rows) {
        if(rows!=1) throw new BusinessException(409,"VERSION_CONFLICT","记录已变化，请重新查询");
    }

    private void checkCategory(String id) {
        // 先给出清楚的404；并发删除分类时，最终仍由数据库外键保护引用。
        if(categories.selectById(id)==null) throw new BusinessException(404,"CATEGORY_NOT_FOUND","分类不存在");
    }

}
