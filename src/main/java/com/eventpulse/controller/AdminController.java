package com.eventpulse.controller;

import com.eventpulse.dto.PageResponse;
import com.eventpulse.dto.UserResponse;
import com.eventpulse.service.UserAdminService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
@Tag(name = "Admin")
public class AdminController {

    private final UserAdminService users;

    public AdminController(UserAdminService users) {
        this.users = users;
    }

    @GetMapping("/users")
    public PageResponse<UserResponse> listUsers(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return users.list(pageable);
    }
}
