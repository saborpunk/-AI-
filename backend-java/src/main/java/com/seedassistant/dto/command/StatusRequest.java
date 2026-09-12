package com.seedassistant.dto.command;

import jakarta.validation.constraints.*;
import java.time.LocalDateTime;

public class StatusRequest {
    @NotBlank private String status;
    @NotNull @PositiveOrZero private Long version;
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
}
