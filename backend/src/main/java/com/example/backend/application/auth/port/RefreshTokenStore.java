package com.example.backend.application.auth.port;

import java.util.Optional;

public interface RefreshTokenStore {

    void save(Long userId, String refreshToken);

    void delete(Long userId);

    Optional<String> get(Long userId);
}
