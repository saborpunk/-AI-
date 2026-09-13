package com.seedassistant;

import java.net.URI;
import java.net.http.*;
import java.sql.SQLTransientConnectionException;
import java.time.Duration;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DatabaseUnavailableApiTest extends JwtTestSupport {
    @LocalServerPort int port;
    @org.springframework.beans.factory.annotation.Autowired com.seedassistant.security.JwtService tokens;
    @org.springframework.test.context.bean.override.mockito.MockitoBean com.seedassistant.mapper.UserAccountMapper users;
    // 只在测试中模拟 JDBC 连接中断，不停止用户现有的 MySQL 服务。
    @MockitoBean DataSource database;

    @BeforeEach void disconnect() throws Exception {
        when(users.selectById(USER_ID)).thenReturn(merchant());
        when(database.getConnection()).thenThrow(new SQLTransientConnectionException("test disconnected", "08001"));
    }

    @Test void createAndHistoryReturn503WithoutLeakingDatabaseDetails() throws Exception {
        try (var client = HttpClient.newHttpClient()) {
            for (var request : new HttpRequest[]{
                    HttpRequest.newBuilder(URI.create(base())).header("Authorization", "Bearer " + tokens.issue(USER_ID)).timeout(Duration.ofSeconds(5)).GET().build(),
                    HttpRequest.newBuilder(URI.create(base())).header("Authorization", "Bearer " + tokens.issue(USER_ID)).timeout(Duration.ofSeconds(5))
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString("{\"question\":\"demo\"}")).build()}) {
                var response = client.send(request, HttpResponse.BodyHandlers.ofString());
                assertThat(response.statusCode()).isEqualTo(503);
                assertThat(response.body()).contains("DATABASE_UNAVAILABLE", "requestId")
                        .doesNotContain("test disconnected", "jdbc:mysql", "password");
            }
        }
    }

    @Test void invalidRequestsAreStillRejectedBeforeDatabaseAccess() throws Exception {
        try (var client = HttpClient.newHttpClient()) {
            var response = client.send(HttpRequest.newBuilder(URI.create(base()))
                    .header("Authorization", "Bearer " + tokens.issue(USER_ID)).timeout(Duration.ofSeconds(5)).header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("{\"question\":\" \"}")).build(),
                    HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(400);
        }
    }

    private String base() { return "http://127.0.0.1:" + port + "/api/v1/consultations"; }

    @Test void traditionalModuleQueriesAlsoReturn503() throws Exception {
        try (var client = HttpClient.newHttpClient()) {
            for (String path : new String[]{"article-categories", "articles", "sessions"}) {
                var response = client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1/" + path))
                        .header("Authorization", "Bearer " + tokens.issue(USER_ID)).timeout(Duration.ofSeconds(5)).GET().build(), HttpResponse.BodyHandlers.ofString());
                assertThat(response.statusCode()).isEqualTo(503);
                assertThat(response.body()).contains("DATABASE_UNAVAILABLE").doesNotContain("test disconnected");
            }
        }
    }
}
