package com.seedassistant;

import com.seedassistant.common.BusinessException;
import com.seedassistant.entity.UserAccount;
import com.seedassistant.mapper.UserAccountMapper;
import com.seedassistant.service.UserAccountService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class UserAccountServiceTest {
    private final UserAccountMapper mapper = mock(UserAccountMapper.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);
    private final PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
    private final UserAccountService service = new UserAccountService(mapper, encoder, manager);

    @BeforeEach void setup() {
        when(manager.getTransaction(any(TransactionDefinition.class))).thenReturn(new SimpleTransactionStatus());
    }

    @Test void invalidUsernameNeverAccessesDatabase() {
        assertThatThrownBy(() -> service.createCustomer("' OR 1=1", "synthetic-password", "demo"))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getStatus()).isEqualTo(400));
        verifyNoInteractions(mapper, encoder, manager);
    }

    @Test void bcryptLengthLimitIsBytesNotCharacters() {
        assertThatThrownBy(() -> service.createCustomer("demo", "种".repeat(25), "demo"))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getStatus()).isEqualTo(400));
        verifyNoInteractions(mapper, encoder, manager);
    }

    @Test void shortPasswordAndBlankNameAreRejected() {
        assertThatThrownBy(() -> service.createCustomer("demo", "short", "demo")).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.createCustomer("demo", "synthetic-password", " ")).isInstanceOf(BusinessException.class);
        verifyNoInteractions(mapper, encoder, manager);
    }

    @Test void hashesBeforeTransactionAndReturnsNoPassword() {
        when(encoder.encode("synthetic-password")).thenReturn("test-hash");
        when(mapper.selectById(anyString())).thenReturn(new UserAccount());
        service.createCustomer(" DEMO ", "synthetic-password", " demo ");
        var order = inOrder(encoder, manager, mapper);
        order.verify(encoder).encode("synthetic-password");
        order.verify(manager).getTransaction(any());
        order.verify(mapper).insertCustomer(argThat(a -> a.getUsername().equals("demo")
                && a.getPasswordHash().equals("test-hash") && a.getDisplayName().equals("demo") && a.getRole() == null));
        order.verify(mapper).selectById(anyString());
        order.verify(manager).commit(any());
    }

    @Test void duplicateUsernameRollsBackAndBecomes409() {
        when(mapper.insertCustomer(any())).thenThrow(new DuplicateKeyException("synthetic duplicate"));
        assertThatThrownBy(() -> service.createCustomer("demo", "synthetic-password", "demo"))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getCode()).isEqualTo("USERNAME_EXISTS"));
        verify(manager).rollback(any());
        verify(manager, never()).commit(any());
    }

    @Test void missingUserIs404() {
        assertThatThrownBy(() -> service.get("missing"))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getStatus()).isEqualTo(404));
    }
}
