package com.example.user.service;

import com.example.common.exception.ResourceNotFoundException;
import com.example.common.security.AuthenticatedUser;
import com.example.common.security.CurrentUser;
import com.example.user.dto.UserResponse;
import com.example.user.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;

    public UserServiceImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse currentUser() {
        AuthenticatedUser caller = CurrentUser.require();
        return userRepository.findById(caller.userId())
                .map(UserResponse::from)
                .orElseThrow(() -> ResourceNotFoundException.of("User", caller.userId()));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<UserResponse> listUsers(Pageable pageable) {
        return userRepository.findAllByOrderByIdAsc(pageable).map(UserResponse::from);
    }
}
