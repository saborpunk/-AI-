package com.seedassistant;

import com.seedassistant.consultation.*;
import com.seedassistant.draft.*;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.web.client.RestClientException;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ConsultationServiceTest {
    ConsultationMapper mapper;
    PythonDraftClient python;
    ConsultationService service;
    PlatformTransactionManager manager;

    @BeforeEach void setup() {
        mapper = mock(ConsultationMapper.class);
        python = mock(PythonDraftClient.class);
        manager = mock(PlatformTransactionManager.class);
        when(manager.getTransaction(any(TransactionDefinition.class))).thenReturn(new SimpleTransactionStatus());
        service = new ConsultationService(mapper, python, manager);
    }

    @Test void missingRecordIs404() {
        assertThatThrownBy(() -> service.get("missing")).isInstanceOfSatisfying(ConsultationException.class,
                e -> assertThat(e.status()).isEqualTo(404));
    }

    @Test void staleVersionNeverCallsPython() {
        when(mapper.find("id")).thenReturn(row("PENDING", 2));
        assertThatThrownBy(() -> service.generate("id", 1, "request"))
                .isInstanceOfSatisfying(ConsultationException.class, e -> assertThat(e.status()).isEqualTo(409));
        verifyNoInteractions(python);
    }

    @Test void pythonFailureDoesNotChangeStoredConsultation() {
        when(mapper.find("id")).thenReturn(row("PENDING", 0));
        when(python.generate(anyString(), any())).thenThrow(new RestClientException("stub failure"));
        assertThatThrownBy(() -> service.generate("id", 0, "request")).isInstanceOf(RestClientException.class);
        verify(mapper, never()).saveDraft(anyString(), anyLong(), anyString());
        verifyNoInteractions(manager);
    }

    @Test void confirmedRecordCannotBeEditedOrRegenerated() {
        when(mapper.find("id")).thenReturn(row("CONFIRMED", 2));
        assertThatThrownBy(() -> service.review("id", new Consultation.Review(2L, "edit", Consultation.ReviewAction.SAVE)))
                .isInstanceOf(ConsultationException.class);
        assertThatThrownBy(() -> service.generate("id", 2, "request")).isInstanceOf(ConsultationException.class);
        verifyNoInteractions(python);
    }

    @Test void lostConcurrentUpdateIsRejectedAndTransactionRollsBack() {
        when(mapper.find("id")).thenReturn(row("DRAFT_READY", 1));
        when(mapper.review(anyString(), anyLong(), anyString(), anyString())).thenReturn(0);
        assertThatThrownBy(() -> service.review("id", new Consultation.Review(1L, "edit", Consultation.ReviewAction.CONFIRM)))
                .isInstanceOfSatisfying(ConsultationException.class, e -> assertThat(e.status()).isEqualTo(409));
        verify(manager).rollback(any());
        verify(manager, never()).commit(any());
    }

    private Consultation.Row row(String status, long version) {
        var now = LocalDateTime.now();
        return new Consultation.Row("id", "question", null, status, null, null, version, now, now, null);
    }
}
