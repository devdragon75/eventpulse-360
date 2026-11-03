package com.eventpulse.service;

import com.eventpulse.dto.PageResponse;
import com.eventpulse.dto.UserResponse;
import com.eventpulse.repository.UserRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class UserAdminService {

    private final UserRepository users;

    public UserAdminService(UserRepository users) {
        this.users = users;
    }

    public PageResponse<UserResponse> list(Pageable pageable) {
        return PageResponse.from(users.findAll(pageable), UserResponse::from);
    }
}
