package com.seedassistant.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import com.fasterxml.jackson.annotation.JsonIgnore;

@TableName("consultation_session")
public class ConsultationSession {
    @TableId(type = IdType.INPUT)
    private String id;
    // 归属由认证上下文设置；隐藏内部字段以保持原API响应结构。
    @JsonIgnore
    private String userId;
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    private String title;
    private String notes;
    private String status;
    private long version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public long getVersion() { return version; }
    public void setVersion(long version) { this.version = version; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
