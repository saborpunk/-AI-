package com.seedassistant.dto.command;

import jakarta.validation.constraints.*;
import java.time.LocalDateTime;

public class CategoryUpdateRequest {
    @NotBlank @Size(max=80) private String name;
    @NotNull @Size(max=500) private String description;
    @NotNull @PositiveOrZero private Long version;
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
}
