package com.seedassistant;

import com.seedassistant.common.BusinessException;
import com.seedassistant.dto.command.ConsultationSessionCreateRequest;
import com.seedassistant.mapper.ConsultationSessionMapper;
import com.seedassistant.mapper.UserAccountMapper;
import com.seedassistant.service.*;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.UncategorizedSQLException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.*;

/** Opt-in: connects only to the configured existing MySQL. No embedded replacement database. */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "RUN_MYSQL_TESTS", matches = "true")
class UserPersistenceTest {
    @Autowired UserAccountService users;
    @Autowired UserAccountMapper mapper;
    @Autowired ConsultationSessionService sessions;
    @Autowired ConsultationSessionMapper sessionMapper;
    @Autowired SessionOwnershipService ownership;
    @Autowired PasswordEncoder passwords;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;

    private String username() { return "test_" + UUID.randomUUID().toString().replace("-", "").substring(0, 24); }
    private String session() {
        var request = new ConsultationSessionCreateRequest();
        request.setTitle("SYNTHETIC V2"); request.setNotes("rollback fixture");
        return sessions.create(request).getId();
    }

    @Test @Transactional void userDefaultsMappingHashAndSafeResponse() throws Exception {
        String name = username();
        var response = users.createCustomer(" " + name.toUpperCase(java.util.Locale.ROOT) + " ", "synthetic-password", "测试客户");
        var row = mapper.findByUsername(name);
        assertThat(row.getId()).isEqualTo(response.getId());
        assertThat(row.getRole()).isEqualTo("CUSTOMER");
        assertThat(row.getStatus()).isEqualTo("ENABLED");
        assertThat(row.getVersion()).isZero();
        assertThat(row.getCreatedAt()).isNotNull();
        assertThat(row.getUpdatedAt()).isEqualTo(row.getCreatedAt());
        assertThat(passwords.matches("synthetic-password", row.getPasswordHash())).isTrue();
        assertThat(passwords.matches("wrong-password", row.getPasswordHash())).isFalse();
        assertThat(json.writeValueAsString(response)).doesNotContain("password", row.getPasswordHash());
        assertThat(json.writeValueAsString(row)).doesNotContain("password", row.getPasswordHash());
        var other = users.createCustomer(username(), "synthetic-password", "other");
        assertThat(mapper.selectById(other.getId()).getPasswordHash()).isNotEqualTo(row.getPasswordHash());
        assertThat(mapper.findByUsername("' OR 1=1 --")).isNull();
    }

    @Test @Transactional void ownershipCanOnlyBeAssignedOnce() {
        String id = session();
        var before = sessionMapper.selectById(id);
        assertThat(before.getUserId()).isNull();
        String user = users.createCustomer(username(), "synthetic-password", "owner").getId();
        var assigned = ownership.assignUnowned(id, user, 0);
        assertThat(assigned.getUserId()).isEqualTo(user);
        assertThat(assigned.getVersion()).isEqualTo(1);
        assertThat(assigned.getTitle()).isEqualTo(before.getTitle());
        assertThat(assigned.getCreatedAt()).isEqualTo(before.getCreatedAt());
        assertThatThrownBy(() -> ownership.assignUnowned(id, user, 1))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getStatus()).isEqualTo(409));
        assertThat(sessionMapper.selectById(id).getVersion()).isEqualTo(1);
    }

    @Test @Transactional void missingUserDoesNotClaimSession() {
        String id = session();
        assertThatThrownBy(() -> ownership.assignUnowned(id, UUID.randomUUID().toString(), 0))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getStatus()).isEqualTo(404));
        assertThat(sessionMapper.selectById(id).getUserId()).isNull();
        assertThat(sessionMapper.selectById(id).getVersion()).isZero();
    }

    @Test @Transactional void foreignKeyRejectsMissingOwnerEvenWithoutService() {
        String id = session();
        assertThatThrownBy(() -> sessionMapper.assignUnowned(id, UUID.randomUUID().toString(), 0))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(sessionMapper.selectById(id).getUserId()).isNull();
    }

    @Test @Transactional void referencedUserCannotBeDeleted() {
        String id = session();
        String user = users.createCustomer(username(), "synthetic-password", "owner").getId();
        ownership.assignUnowned(id, user, 0);
        assertThatThrownBy(() -> mapper.deleteById(user)).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(mapper.selectById(user)).isNotNull();
    }

    @Test @Transactional void constraintsRejectPlaintextAndUnknownRole() {
        String user = users.createCustomer(username(), "synthetic-password", "owner").getId();
        // Connector/J reports MySQL CHECK failures as HY000, so assert the actual server error code.
        assertThatThrownBy(() -> jdbc.update("UPDATE user_account SET password_hash=? WHERE id=?", "plaintext", user))
                .isInstanceOfSatisfying(UncategorizedSQLException.class, e -> assertThat(e.getSQLException().getErrorCode()).isEqualTo(3819));
        assertThatThrownBy(() -> jdbc.update("UPDATE user_account SET role=? WHERE id=?", "ADMIN", user))
                .isInstanceOfSatisfying(UncategorizedSQLException.class, e -> assertThat(e.getSQLException().getErrorCode()).isEqualTo(3819));
    }

    @Test void concurrentUsernameCreatesExactlyOneCommittedUser() throws Exception {
        String name = username();
        var gate = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            Callable<Integer> create = () -> {
                gate.await(10, TimeUnit.SECONDS);
                try { users.createCustomer(name, "synthetic-password", "concurrent"); return 201; }
                catch (BusinessException conflict) { return conflict.getStatus(); }
            };
            var first = pool.submit(create); var second = pool.submit(create);
            gate.countDown();
            assertThat(java.util.List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(201, 409);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM user_account WHERE username=?", Integer.class, name)).isEqualTo(1);
        } finally {
            // Only delete this test's random, synthetic account after workers have finished.
            jdbc.update("DELETE FROM user_account WHERE username=?", name);
        }
    }
}
