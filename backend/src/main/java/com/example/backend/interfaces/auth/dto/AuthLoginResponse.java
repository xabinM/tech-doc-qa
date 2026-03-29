package com.example.backend.interfaces.auth.dto;

import com.example.backend.application.auth.TokenResult;

public record AuthLoginResponse(String accessToken, String refreshToken) {

    public static AuthLoginResponse from(TokenResult result) {
        return new AuthLoginResponse(result.accessToken(), result.refreshToken());
    }
}
