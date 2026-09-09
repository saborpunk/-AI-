package com.seedassistant;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DraftApiTest {
    static final JsonMapper JSON = JsonMapper.builder().build();
    static final AtomicInteger CALLS = new AtomicInteger();
    static final ExecutorService EXECUTOR = Executors.newCachedThreadPool();
    static HttpServer python;
    static volatile String mode = "ok";
    @LocalServerPort int port;

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) throws Exception {
        python = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        python.setExecutor(EXECUTOR);
        python.createContext("/internal/v1/germination-drafts", exchange -> {
            CALLS.incrementAndGet();
            var input = JSON.readTree(exchange.getRequestBody().readAllBytes());
            String currentMode = mode;
            if (currentMode.equals("slow")) {
                try { Thread.sleep(2500); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }
            boolean hasBatch = input.hasNonNull("batchCode");
            String body = JSON.writeValueAsString(Map.of(
                    "requestId", currentMode.equals("wrong-id") ? "wrong" : input.get("requestId").asText(),
                    "answerDraft", "【模拟草稿】请核对批次资料",
                    "missingFields", hasBatch ? List.of("batchEvidence") : List.of("batchCode", "batchEvidence"),
                    "needsHumanReview", !currentMode.equals("no-review"), "mode", "mock"));
            if (currentMode.equals("malformed")) { body = "{broken"; }
            if (currentMode.equals("missing")) { body = "{}"; }
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            try {
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(currentMode.equals("error") ? 500 : 200, bytes.length);
                exchange.getResponseBody().write(bytes);
            } finally { exchange.close(); }
        });
        python.start();
        registry.add("ai.base-url", () -> "http://127.0.0.1:" + python.getAddress().getPort());
        registry.add("ai.read-timeout", () -> "1s");
    }

    @BeforeEach void reset() { mode = "ok"; CALLS.set(0); }
    @AfterAll static void stop() { python.stop(0); EXECUTOR.shutdownNow(); }

    @Test void requestPassesThroughJavaAndReturnsValidatedPythonResult() throws Exception {
        var response = post("{\"question\":\"这个种子发芽率多高？\",\"batchCode\":\"DEMO-001\"}");
        assertThat(response.statusCode()).isEqualTo(200);
        var json = JSON.readTree(response.body());
        assertThat(json.get("requestId").asText()).isEqualTo(json.at("/data/requestId").asText())
                .isEqualTo(response.headers().firstValue("X-Request-Id").orElseThrow());
        assertThat(json.at("/data/mode").asText()).isEqualTo("mock");
        assertThat(json.at("/data/needsHumanReview").asBoolean()).isTrue();
        assertThat(CALLS.get()).isEqualTo(1);
    }

    @Test void missingBatchRemainsAnExplicitQuestion() throws Exception {
        var response = post("{\"question\":\"发芽率？\"}");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(JSON.readTree(response.body()).at("/data/missingFields/0").asText()).isEqualTo("batchCode");
    }

    @Test void invalidInputsNeverReachPython() throws Exception {
        for (String body : List.of("{}", "{broken", "{\"question\":\"  \"}",
                "{\"question\":\"x\",\"batchCode\":\"../bad\"}",
                JSON.writeValueAsString(Map.of("question", "a".repeat(2001))))) {
            var response = post(body);
            assertThat(response.statusCode()).isEqualTo(400);
            assertThat(JSON.readTree(response.body()).get("code").asText()).isEqualTo("INVALID_REQUEST");
        }
        assertThat(CALLS.get()).isZero();
    }

    @Test void untrustedOrBrokenPythonResultsAreNotReportedAsSuccess() throws Exception {
        for (String failure : List.of("wrong-id", "no-review", "malformed", "missing", "error")) {
            mode = failure;
            var response = post("{\"question\":\"发芽率？\"}");
            assertThat(response.statusCode()).as(failure).isEqualTo(502);
            assertThat(JSON.readTree(response.body()).get("code").asText()).isEqualTo("AI_BAD_RESPONSE");
        }
    }

    @Test void slowPythonHasABoundedWait() throws Exception {
        mode = "slow";
        var response = post("{\"question\":\"发芽率？\"}");
        assertThat(response.statusCode()).isEqualTo(504);
        assertThat(JSON.readTree(response.body()).get("code").asText()).isEqualTo("AI_TIMEOUT");
        assertThat(CALLS.get()).isEqualTo(1);
    }

    private HttpResponse<String> post(String body) throws Exception {
        try (var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build()) {
            return client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1/germination-drafts"))
                    .timeout(Duration.ofSeconds(6)).header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)).build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        }
    }
}
