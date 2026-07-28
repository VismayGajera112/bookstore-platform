package com.example.user.service;

import com.example.user.dto.UserResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface UserService {

    UserResponse currentUser();

    Page<UserResponse> listUsers(Pageable pageable);
}
