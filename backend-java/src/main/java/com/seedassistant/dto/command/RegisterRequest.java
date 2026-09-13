package com.seedassistant.dto.command;

import jakarta.validation.constraints.*;

public class RegisterRequest {
    @NotBlank @Size(max=64)
    private String username;
    @NotBlank @Size(min=12,max=72)
    private String password;
    @NotBlank @Size(max=80)
    private String displayName;
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
}
