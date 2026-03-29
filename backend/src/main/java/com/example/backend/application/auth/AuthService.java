package com.example.backend.application.auth;

import com.example.backend.common.exception.CustomException;
import com.example.backend.common.exception.ErrorCode;
import com.example.backend.domain.auth.User;
import com.example.backend.domain.auth.UserRepository;
import com.example.backend.infrastructure.auth.JwtProvider;
import com.example.backend.infrastructure.auth.RedisTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final RedisTokenRepository redisTokenRepository;

    @Transactional
    public void signup(String email, String password) {
        if (userRepository.existsByEmail(email)) {
            throw new CustomException(ErrorCode.AUTH_EMAIL_DUPLICATE);
        }
        User user = User.create(email, passwordEncoder.encode(password));
        userRepository.save(user);
    }

    public TokenResult login(String email, String password) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new CustomException(ErrorCode.AUTH_LOGIN_FAIL));

        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new CustomException(ErrorCode.AUTH_LOGIN_FAIL);
        }

        String accessToken = jwtProvider.generateAccessToken(user.getId());
        String refreshToken = jwtProvider.generateRefreshToken(user.getId());
        redisTokenRepository.saveRefreshToken(user.getId(), refreshToken);

        return new TokenResult(accessToken, refreshToken);
    }

    public TokenResult refresh(String refreshToken) {
        if (!jwtProvider.validateToken(refreshToken)) {
            throw new CustomException(ErrorCode.AUTH_TOKEN_INVALID);
        }

        Long userId = jwtProvider.getUserId(refreshToken);
        String stored = redisTokenRepository.getRefreshToken(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.AUTH_TOKEN_INVALID));

        if (!stored.equals(refreshToken)) {
            throw new CustomException(ErrorCode.AUTH_TOKEN_INVALID);
        }

        String newAccessToken = jwtProvider.generateAccessToken(userId);
        String newRefreshToken = jwtProvider.generateRefreshToken(userId);
        redisTokenRepository.saveRefreshToken(userId, newRefreshToken);

        return new TokenResult(newAccessToken, newRefreshToken);
    }

    public void logout(Long userId) {
        redisTokenRepository.deleteRefreshToken(userId);
    }
}
