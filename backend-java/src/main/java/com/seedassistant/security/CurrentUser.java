package com.seedassistant.security;

public final class CurrentUser {
    private final String id;
    private final String role;
    public CurrentUser(String id, String role) { this.id = id; this.role = role; }
    public String getId() { return id; }
    public String getRole() { return role; }
    public boolean isMerchant() { return "MERCHANT".equals(role); }
}
