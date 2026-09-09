package com.seedassistant.common;

import jakarta.servlet.http.HttpServletRequest;
import java.net.http.HttpTimeoutException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;

@RestControllerAdvice
public class ApiExceptionHandler {
    public record Error(String code, String message, String requestId) { }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Error> invalid(MethodArgumentNotValidException error, HttpServletRequest request) {
        String message = error.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage()).sorted()
                .findFirst().orElse("请求参数无效");
        return response(400, "INVALID_REQUEST", message, request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<Error> malformed(HttpServletRequest request) {
        return response(400, "INVALID_REQUEST", "请求体必须是合法 JSON，字段类型须符合接口约定", request);
    }

    @ExceptionHandler(ResourceAccessException.class)
    ResponseEntity<Error> unavailable(ResourceAccessException error, HttpServletRequest request) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof HttpTimeoutException || cause instanceof java.net.SocketTimeoutException) {
                return response(504, "AI_TIMEOUT", "模拟服务响应超时，请稍后再试", request);
            }
        }
        return response(503, "AI_UNAVAILABLE", "模拟服务暂不可用，请确认 Python 服务已启动", request);
    }

    @ExceptionHandler(RestClientException.class)
    ResponseEntity<Error> upstream(HttpServletRequest request) {
        // 不透传上游错误正文、内部地址或堆栈。
        return response(502, "AI_BAD_RESPONSE", "模拟服务返回异常，未生成可用草稿", request);
    }

    private ResponseEntity<Error> response(int status, String code, String message, HttpServletRequest request) {
        return ResponseEntity.status(status).body(new Error(code, message, (String) request.getAttribute("requestId")));
    }
}
