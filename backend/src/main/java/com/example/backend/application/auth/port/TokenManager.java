package com.example.backend.application.auth.port;

public interface TokenManager {

    String generateAccessToken(Long userId);

    String generateRefreshToken(Long userId);

    /**
     * 리프레시 토큰을 검증한다.
     * 만료된 경우 AUTH_TOKEN_EXPIRED, 위변조된 경우 AUTH_TOKEN_INVALID 예외를 던진다.
     */
    void validateRefreshToken(String token);

    /**
     * 액세스 토큰을 검증한다. (필터용 - 예외 없이 boolean 반환)
     */
    boolean validateAccessToken(String token);

    /**
     * 액세스 토큰이 만료된 경우 true를 반환한다. 서명이 유효하지 않으면 false.
     */
    boolean isAccessTokenExpired(String token);

    Long getUserId(String token);
}
