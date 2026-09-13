package com.seedassistant;

import com.seedassistant.entity.UserAccount;
import com.seedassistant.mapper.UserAccountMapper;
import com.seedassistant.security.JwtService;
import java.net.URI;
import java.net.http.*;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties="management.health.db.enabled=false")
class SecurityApiTest extends JwtTestSupport {
    @LocalServerPort int port;
    @Autowired JwtService tokens;
    @Autowired JwtEncoder encoder;
    @Autowired PasswordEncoder passwords;
    @MockitoBean UserAccountMapper users;
    UserAccount account;
    @BeforeEach void setup() {
        account = merchant(); account.setPasswordHash(passwords.encode("synthetic-password"));
        when(users.selectById(USER_ID)).thenReturn(account);
        when(users.findByUsername("synthetic")).thenReturn(account);
    }
    HttpResponse<String> call(String method, String path, String token, String body) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).timeout(java.time.Duration.ofSeconds(8));
        if (token != null) request.header("Authorization", token);
        request.header("Content-Type", "application/json");
        request.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        try (var client = HttpClient.newHttpClient()) { return client.send(request.build(), HttpResponse.BodyHandlers.ofString()); }
    }
    @Test void healthIsPublicButAllLegacyAndNewBusinessRequiresLogin() throws Exception {
        assertThat(call("GET", "/actuator/health", null, null).statusCode()).isEqualTo(200);
        for (String path : List.of("/api/v1/users/me", "/api/v1/sessions", "/api/v1/articles", "/api/v1/article-categories", "/api/v1/consultations")) {
            var response = call("GET", path, null, null);
            assertThat(response.statusCode()).isEqualTo(401);
            assertThat(response.body()).contains("UNAUTHORIZED", "requestId").doesNotContain("null");
            assertThat(response.headers().firstValue("X-Request-Id")).isPresent();
            assertThat(response.headers().firstValue("WWW-Authenticate")).hasValue("Bearer");
        }
        assertThat(call("POST", "/api/v1/germination-drafts", null, "{}").statusCode()).isEqualTo(401);
    }
    @Test void validTokenReturnsSafeCurrentUser() throws Exception {
        var response = call("GET", "/api/v1/users/me", "Bearer " + tokens.issue(USER_ID), null);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains(USER_ID, "MERCHANT").doesNotContain("password", account.getPasswordHash());
        assertThat(response.headers().allValues("Set-Cookie")).isEmpty();
    }
    @Test void customerCannotAccessMerchantOrLegacyRoutes() throws Exception {
        account.setRole("CUSTOMER");
        for (String path : List.of("/api/v1/articles", "/api/v1/article-categories", "/api/v1/consultations")) {
            assertThat(call("GET", path, "Bearer " + tokens.issue(USER_ID), null).statusCode()).isEqualTo(403);
        }
        assertThat(call("POST", "/api/v1/germination-drafts", "Bearer " + tokens.issue(USER_ID), "{}").statusCode()).isEqualTo(403);
    }
    @Test void disabledOrDeletedAccountInvalidatesExistingToken() throws Exception {
        String token = "Bearer " + tokens.issue(USER_ID);
        account.setStatus("DISABLED");
        assertThat(call("GET", "/api/v1/users/me", token, null).statusCode()).isEqualTo(401);
        when(users.selectById(USER_ID)).thenReturn(null);
        assertThat(call("GET", "/api/v1/users/me", token, null).statusCode()).isEqualTo(401);
    }
    @Test void malformedExpiredAndWrongClaimsAreRejectedBeforeDatabaseLookup() throws Exception {
        clearInvocations(users);
        Instant now = Instant.now();
        for (String token : List.of("Basic abc", "Bearer bad", "Bearer " + signed(now.minusSeconds(30), "seed-assistant", "seed-web"),
                "Bearer " + signed(now.plusSeconds(100), "wrong-issuer", "seed-web"),
                "Bearer " + signed(now.plusSeconds(100), "seed-assistant", "wrong-audience"),
                "Bearer " + signed(null, "seed-assistant", "seed-web"))) {
            assertThat(call("GET", "/api/v1/users/me", token, null).statusCode()).isEqualTo(401);
        }
        verifyNoInteractions(users);
    }
    @Test void tamperedSignatureIsRejected() throws Exception {
        String token = tokens.issue(USER_ID);
        int start = token.lastIndexOf('.') + 1;
        String changed = token.substring(0, start) + (token.charAt(start) == 'A' ? 'B' : 'A') + token.substring(start + 1);
        assertThat(call("GET", "/api/v1/users/me", "Bearer " + changed, null).statusCode()).isEqualTo(401);
    }
    @Test void loginUsesHashAndReturnsSameFailureForWrongMissingOrDisabled() throws Exception {
        var success = call("POST", "/api/v1/auth/login", null, "{\"username\":\" SYNTHETIC \",\"password\":\"synthetic-password\"}");
        assertThat(success.statusCode()).isEqualTo(200);
        assertThat(success.body()).contains("accessToken", "Bearer", "900").doesNotContain("synthetic-password");
        String wrong = call("POST", "/api/v1/auth/login", null, "{\"username\":\"synthetic\",\"password\":\"wrong\"}").body();
        assertThat(wrong).contains("BAD_CREDENTIALS");
        for (String name : List.of("missing", "synthetic")) {
            account.setStatus("DISABLED");
            var response = call("POST", "/api/v1/auth/login", null, "{\"username\":\"" + name + "\",\"password\":\"synthetic-password\"}");
            assertThat(response.statusCode()).isEqualTo(401);
            assertThat(response.body()).contains("BAD_CREDENTIALS");
        }
    }
    @Test void authenticationDatabaseFailureIs503Not401() throws Exception {
        when(users.selectById(USER_ID)).thenThrow(new org.springframework.dao.UncategorizedDataAccessException("private details",
                new java.sql.SQLException("private connection", "08001")) {});
        var response = call("GET", "/api/v1/users/me", "Bearer " + tokens.issue(USER_ID), null);
        assertThat(response.statusCode()).isEqualTo(503);
        assertThat(response.body()).contains("DATABASE_UNAVAILABLE").doesNotContain("private");
    }
    @Test void corsAllowsOnlyLocalFrontendOrigin() throws Exception {
        try (var client = HttpClient.newHttpClient()) {
            for (String origin : List.of("http://127.0.0.1:5173", "https://untrusted.example")) {
                var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1/users/me"))
                        .header("Origin", origin).header("Access-Control-Request-Method", "GET")
                        .header("Access-Control-Request-Headers", "authorization").method("OPTIONS", HttpRequest.BodyPublishers.noBody()).build();
                var response = client.send(request, HttpResponse.BodyHandlers.ofString());
                if (origin.contains("127.0.0.1")) {
                    assertThat(response.statusCode()).isEqualTo(200);
                    assertThat(response.headers().firstValue("Access-Control-Allow-Origin")).hasValue(origin);
                } else assertThat(response.statusCode()).isEqualTo(403);
            }
        }
    }
    private String signed(Instant expires, String issuer, String audience) {
        var claims = JwtClaimsSet.builder().subject(USER_ID).issuer(issuer).audience(List.of(audience)).issuedAt(Instant.now().minusSeconds(60));
        if (expires != null) claims.expiresAt(expires);
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims.build())).getTokenValue();
    }
}
