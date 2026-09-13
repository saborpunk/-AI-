package com.seedassistant.controller;

import com.seedassistant.dto.command.*;
import com.seedassistant.dto.response.*;
import com.seedassistant.service.*;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final UserAccountService users;
    private final AuthService auth;
    public AuthController(UserAccountService users, AuthService auth) { this.users = users; this.auth = auth; }
    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        var user = users.createCustomer(request.getUsername(), request.getPassword(), request.getDisplayName());
        return ResponseEntity.created(URI.create("/api/v1/users/me")).body(user);
    }
    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) { return auth.login(request.getUsername(), request.getPassword()); }
}
