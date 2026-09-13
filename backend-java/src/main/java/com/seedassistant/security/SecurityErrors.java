package com.seedassistant.security;

import com.seedassistant.common.ApiExceptionHandler;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class SecurityErrors {
    private final ObjectMapper json;
    public SecurityErrors(ObjectMapper json) { this.json = json; }
    public void write(HttpServletRequest request, HttpServletResponse response, int status, String code, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Cache-Control", "no-store");
        if (status == 401) response.setHeader("WWW-Authenticate", "Bearer");
        json.writeValue(response.getOutputStream(), new ApiExceptionHandler.Error(code, message, (String) request.getAttribute("requestId")));
    }
}
