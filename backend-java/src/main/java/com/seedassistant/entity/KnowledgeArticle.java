package com.seedassistant.entity;

import jakarta.validation.constraints.*;
import java.time.LocalDateTime;

public class KnowledgeArticle {
    private String id;
    private String title;
    private String content;
    private String categoryId;
    private String status;
    private long version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getCategoryId() { return categoryId; }
    public void setCategoryId(String categoryId) { this.categoryId = categoryId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public long getVersion() { return version; }
    public void setVersion(long version) { this.version = version; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
