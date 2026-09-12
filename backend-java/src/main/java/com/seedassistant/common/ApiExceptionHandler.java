package com.seedassistant.common;

import jakarta.servlet.http.HttpServletRequest;
import com.seedassistant.consultation.ConsultationException;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
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

    @ExceptionHandler(BusinessException.class)
    ResponseEntity<Error> business(BusinessException error, HttpServletRequest request) {
        return response(error.getStatus(), error.getCode(), error.getMessage(), request);
    }

    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    ResponseEntity<Error> integrity(HttpServletRequest request) {
        return response(409, "DATA_CONFLICT", "记录存在关联或数据约束冲突，请检查后重试", request);
    }

    @ExceptionHandler(ConsultationException.class)
    ResponseEntity<Error> consultation(ConsultationException error, HttpServletRequest request) {
        return response(error.status(), error.code(), error.getMessage(), request);
    }

    @ExceptionHandler({DataAccessException.class, CannotCreateTransactionException.class})
    ResponseEntity<Error> database(Exception error, HttpServletRequest request) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof CannotCreateTransactionException
                    || cause instanceof org.springframework.dao.DataAccessResourceFailureException
                    || cause instanceof java.sql.SQLTransientConnectionException
                    || cause instanceof java.sql.SQLTimeoutException
                    || (cause instanceof java.sql.SQLException sql && sql.getSQLState() != null
                        && (sql.getSQLState().startsWith("08") || sql.getSQLState().equals("28000")))) {
                return response(503, "DATABASE_UNAVAILABLE", "数据库暂不可用，操作未确认成功，请稍后查询记录状态", request);
            }
        }
        // 建表遗漏或 SQL 错误不冒充连接中断，也不向调用方暴露 SQL 内容。
        return response(500, "DATABASE_ERROR", "数据库操作失败，请检查项目表结构与服务日志", request);
    }

    @ExceptionHandler({HandlerMethodValidationException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<Error> invalidParameters(HttpServletRequest request) {
        return response(400, "INVALID_REQUEST", "路径或分页参数无效", request);
    }

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
