package com.seedassistant;

import com.seedassistant.common.BusinessException;
import com.seedassistant.dto.command.*;
import com.seedassistant.entity.*;
import com.seedassistant.mapper.*;
import com.seedassistant.service.*;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TraditionalBusinessTest {
    ArticleCategoryMapper categories = mock(ArticleCategoryMapper.class);
    KnowledgeArticleMapper articles = mock(KnowledgeArticleMapper.class);
    ConsultationSessionMapper sessions = mock(ConsultationSessionMapper.class);
    PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
    ArticleCategoryService categoryService;
    KnowledgeArticleService articleService;
    ConsultationSessionService sessionService;

    @BeforeEach void setup() {
        when(manager.getTransaction(any(TransactionDefinition.class))).thenReturn(new SimpleTransactionStatus());
        categoryService = new ArticleCategoryService(categories, manager);
        articleService = new KnowledgeArticleService(articles, manager, categories);
        sessionService = new ConsultationSessionService(sessions, manager);
    }

    @Test void missingCategoryPreventsArticleInsertAndRollsBack() {
        var request = new KnowledgeArticleCreateRequest();
        request.setTitle("synthetic"); request.setContent("text"); request.setCategoryId("missing");
        assertThatThrownBy(() -> articleService.create(request)).isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getCode()).isEqualTo("CATEGORY_NOT_FOUND"));
        verifyNoInteractions(articles);
        verify(manager).rollback(any());
    }

    @Test void categoryStaleUpdateDoesNotClaimSuccess() {
        when(categories.find("id")).thenReturn(new ArticleCategory());
        var request = new CategoryUpdateRequest();
        request.setName("name"); request.setDescription("text"); request.setVersion(0L);
        assertThatThrownBy(() -> categoryService.update("id", request)).isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getStatus()).isEqualTo(409));
        verify(manager).rollback(any());
        verify(manager, never()).commit(any());
    }

    @Test void invalidStatusIsRejectedBeforeDatabaseAccess() {
        var request = new StatusRequest(); request.setStatus("INVALID"); request.setVersion(0L);
        assertThatThrownBy(() -> sessionService.status("id", request)).isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getStatus()).isEqualTo(400));
        verifyNoInteractions(sessions, manager);
    }

    @Test void missingSessionIs404() {
        assertThatThrownBy(() -> sessionService.get("missing")).isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getStatus()).isEqualTo(404));
    }

    @Test void pageReadsOneExtraRowButDoesNotReturnIt() {
        when(articles.search("seed", "DRAFT", 2, 1)).thenReturn(List.of(new KnowledgeArticle(),new KnowledgeArticle()));
        var page = articleService.search("seed", "DRAFT", 2, 1);
        assertThat(page.getItems()).hasSize(1);
        assertThat(page.isHasMore()).isTrue();
        assertThat(page.getPage()).isEqualTo(2);
    }

    @Test void staleSessionDeleteRollsBack() {
        when(sessions.find("id")).thenReturn(new ConsultationSession());
        assertThatThrownBy(() -> sessionService.delete("id", 1)).isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getStatus()).isEqualTo(409));
        verify(manager).rollback(any());
    }
}
