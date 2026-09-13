package com.seedassistant.dto.command;

import jakarta.validation.constraints.*;

public class LoginRequest {
    @NotBlank @Size(max=64)
    private String username;
    @NotBlank @Size(max=256)
    private String password;
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
}
