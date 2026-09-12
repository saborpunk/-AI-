package com.seedassistant.dto.command;

import jakarta.validation.constraints.*;
import java.time.LocalDateTime;

public class ConsultationSessionCreateRequest {
    @NotBlank @Size(max=200) private String title;
    @NotNull @Size(max=2000) private String notes;
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
