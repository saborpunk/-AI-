package com.seedassistant.dto.response;

public class LoginResponse {
    private final String accessToken;
    private final long expiresIn;
    public LoginResponse(String accessToken, long expiresIn) { this.accessToken = accessToken; this.expiresIn = expiresIn; }
    public String getAccessToken() { return accessToken; }
    public String getTokenType() { return "Bearer"; }
    public long getExpiresIn() { return expiresIn; }
}
