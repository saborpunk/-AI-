package com.seedassistant.dto.command;

import jakarta.validation.constraints.*;
import java.time.LocalDateTime;

public class KnowledgeArticleUpdateRequest {
    @NotBlank @Size(max=200) private String title;
    @NotBlank @Size(max=20000) private String content;
    @NotBlank @Pattern(regexp="[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}") private String categoryId;
    @NotNull @PositiveOrZero private Long version;
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getCategoryId() { return categoryId; }
    public void setCategoryId(String categoryId) { this.categoryId = categoryId; }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
}
