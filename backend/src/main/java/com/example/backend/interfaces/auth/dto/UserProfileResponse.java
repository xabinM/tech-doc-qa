package com.example.backend.interfaces.auth.dto;

import com.example.backend.domain.auth.User;

import java.time.LocalDateTime;

public record UserProfileResponse(String email, LocalDateTime createdAt) {

    public static UserProfileResponse from(User user) {
        return new UserProfileResponse(user.getEmail(), user.getCreatedAt());
    }
}
