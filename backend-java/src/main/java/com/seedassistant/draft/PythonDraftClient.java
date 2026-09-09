package com.seedassistant.draft;

import jakarta.validation.Validator;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Service
public class PythonDraftClient {
    private final RestClient client;
    private final Validator validator;

    public PythonDraftClient(@Value("${ai.base-url}") String baseUrl,
                             @Value("${ai.connect-timeout}") Duration connectTimeout,
                             @Value("${ai.read-timeout}") Duration readTimeout,
                             Validator validator) {
        // 本地 Uvicorn 使用 HTTP/1.1；无需尝试 h2c 升级或安装 WebSocket 依赖。
        var http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(connectTimeout).build();
        var factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(readTimeout);
        this.client = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
        this.validator = validator;
    }

    public DraftModels.Result generate(String requestId, DraftModels.Request request) {
        var result = client.post().uri("/internal/v1/germination-drafts")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new DraftModels.PythonRequest(requestId, request.question().strip(), request.batchCode()))
                .retrieve().body(DraftModels.Result.class);
        // 跨服务结果也要检查；HTTP 200 并不等于返回内容可信。
        List<String> expectedMissing = request.batchCode() == null
                ? List.of("batchCode", "batchEvidence") : List.of("batchEvidence");
        if (result == null || !validator.validate(result).isEmpty()
                || !requestId.equals(result.requestId()) || !"mock".equals(result.mode())
                || !Boolean.TRUE.equals(result.needsHumanReview())
                || !expectedMissing.equals(result.missingFields())) {
            throw new RestClientException("Invalid Python response contract");
        }
        return result;
    }
}
