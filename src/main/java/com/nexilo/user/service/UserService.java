package com.nexilo.user.service;

import com.nexilo.user.dto.UserRequest;
import com.nexilo.user.dto.UserResponse;
import java.util.List;

public interface UserService {
    List<UserResponse> getAllUsers();
    UserResponse getUserById(Long id);
    UserResponse createUser(UserRequest userRequest);
    UserResponse updateUser(Long id, UserRequest userRequest);
    void deleteUser(Long id);
}

