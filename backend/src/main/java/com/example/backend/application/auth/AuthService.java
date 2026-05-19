package com.example.backend.application.auth;

import com.example.backend.application.auth.port.RefreshTokenStore;
import com.example.backend.application.auth.port.TokenManager;
import com.example.backend.application.query.ChatSessionService;
import com.example.backend.common.exception.CustomException;
import com.example.backend.common.exception.ErrorCode;
import com.example.backend.domain.auth.User;
import com.example.backend.domain.auth.UserRepository;
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
    private final TokenManager tokenManager;
    private final RefreshTokenStore refreshTokenStore;
    private final ChatSessionService chatSessionService;

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

        String accessToken = tokenManager.generateAccessToken(user.getId());
        String refreshToken = tokenManager.generateRefreshToken(user.getId());
        refreshTokenStore.save(user.getId(), refreshToken);

        return new TokenResult(accessToken, refreshToken);
    }

    public TokenResult refresh(String refreshToken) {
        tokenManager.validateRefreshToken(refreshToken);

        Long userId = tokenManager.getUserId(refreshToken);
        String stored = refreshTokenStore.get(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.AUTH_TOKEN_INVALID));

        if (!stored.equals(refreshToken)) {
            throw new CustomException(ErrorCode.AUTH_TOKEN_INVALID);
        }

        String newAccessToken = tokenManager.generateAccessToken(userId);
        String newRefreshToken = tokenManager.generateRefreshToken(userId);
        refreshTokenStore.save(userId, newRefreshToken);

        return new TokenResult(newAccessToken, newRefreshToken);
    }

    public void logout(Long userId) {
        refreshTokenStore.delete(userId);
    }

    public User getUserProfile(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
    }

    @Transactional
    public void changePassword(Long userId, String currentPassword, String newPassword) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new CustomException(ErrorCode.AUTH_PASSWORD_MISMATCH);
        }
        user.changePassword(passwordEncoder.encode(newPassword));
    }

    @Transactional
    public void deleteAccount(Long userId, String password) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new CustomException(ErrorCode.AUTH_PASSWORD_MISMATCH);
        }
        chatSessionService.deleteUserData(userId);
        refreshTokenStore.delete(userId);
        userRepository.deleteById(userId);
    }
}
