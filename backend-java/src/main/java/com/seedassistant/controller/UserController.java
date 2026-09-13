package com.seedassistant.controller;

import com.seedassistant.dto.response.UserResponse;
import com.seedassistant.security.CurrentUser;
import com.seedassistant.service.UserAccountService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {
    private final UserAccountService users;
    public UserController(UserAccountService users) { this.users = users; }
    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal CurrentUser user) { return users.get(user.getId()); }
}
