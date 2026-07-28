package com.example.user.controller;

import com.example.user.dto.UserResponse;
import com.example.user.service.UserService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    /** USER or ADMIN — the caller's own profile, resolved from the token's {@code uid} claim. */
    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public UserResponse me() {
        return userService.currentUser();
    }

    /** ADMIN only. */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Page<UserResponse> listUsers(@PageableDefault(size = 20, sort = "id",
            direction = Sort.Direction.ASC) Pageable pageable) {
        return userService.listUsers(pageable);
    }
}
