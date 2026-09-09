package com.seedassistant;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class HealthEndpointTest {
    @LocalServerPort
    private int port;

    @Test
    void healthIsReachableButInternalConfigurationIsNotExposed() throws Exception {
        // 走真实 HTTP 和随机端口，验证应用启动与端点暴露配置一起生效。
        try (var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build()) {
            var health = client.send(request("/actuator/health"), HttpResponse.BodyHandlers.ofString());
            assertThat(health.statusCode()).isEqualTo(200);
            assertThat(health.body()).contains("\"status\":\"UP\"").doesNotContain("components");
            var env = client.send(request("/actuator/env"), HttpResponse.BodyHandlers.ofString());
            assertThat(env.statusCode()).isEqualTo(404);
        }
    }

    private HttpRequest request(String path) {
        return HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(5)).GET().build();
    }
}
