package com.example.backend.application.auth;

import com.example.backend.application.auth.port.RefreshTokenStore;
import com.example.backend.application.auth.port.TokenManager;
import com.example.backend.common.exception.CustomException;
import com.example.backend.common.exception.ErrorCode;
import com.example.backend.domain.auth.User;
import com.example.backend.domain.auth.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @InjectMocks
    AuthService authService;

    @Mock
    UserRepository userRepository;

    @Mock
    PasswordEncoder passwordEncoder;

    @Mock
    TokenManager tokenManager;

    @Mock
    RefreshTokenStore refreshTokenStore;

    @Test
    @DisplayName("회원가입 성공")
    void signup_success() {
        given(userRepository.existsByEmail("test@example.com")).willReturn(false);
        given(passwordEncoder.encode("password1")).willReturn("encoded");
        given(userRepository.save(any(User.class))).willAnswer(inv -> inv.getArgument(0));

        authService.signup("test@example.com", "password1");

        verify(userRepository).save(any(User.class));
    }

    @Test
    @DisplayName("중복 이메일로 회원가입 시 AUTH_EMAIL_DUPLICATE 예외 발생")
    void signup_duplicateEmail() {
        given(userRepository.existsByEmail("test@example.com")).willReturn(true);

        assertThatThrownBy(() -> authService.signup("test@example.com", "password1"))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                        .isEqualTo(ErrorCode.AUTH_EMAIL_DUPLICATE));

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("로그인 성공 - 액세스/리프레시 토큰 반환")
    void login_success() {
        User user = User.create("test@example.com", "encoded");
        given(userRepository.findByEmail("test@example.com")).willReturn(Optional.of(user));
        given(passwordEncoder.matches("password1", "encoded")).willReturn(true);
        given(tokenManager.generateAccessToken(any())).willReturn("access-token");
        given(tokenManager.generateRefreshToken(any())).willReturn("refresh-token");

        TokenResult result = authService.login("test@example.com", "password1");

        assertThat(result.accessToken()).isEqualTo("access-token");
        assertThat(result.refreshToken()).isEqualTo("refresh-token");
        verify(refreshTokenStore).save(any(), eq("refresh-token"));
    }

    @Test
    @DisplayName("존재하지 않는 이메일로 로그인 시 AUTH_LOGIN_FAIL 예외 발생")
    void login_userNotFound() {
        given(userRepository.findByEmail(anyString())).willReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login("notfound@example.com", "password1"))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                        .isEqualTo(ErrorCode.AUTH_LOGIN_FAIL));
    }

    @Test
    @DisplayName("비밀번호 불일치 시 AUTH_LOGIN_FAIL 예외 발생")
    void login_wrongPassword() {
        User user = User.create("test@example.com", "encoded");
        given(userRepository.findByEmail("test@example.com")).willReturn(Optional.of(user));
        given(passwordEncoder.matches("wrong", "encoded")).willReturn(false);

        assertThatThrownBy(() -> authService.login("test@example.com", "wrong"))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                        .isEqualTo(ErrorCode.AUTH_LOGIN_FAIL));
    }

    @Test
    @DisplayName("토큰 갱신 성공 - 새 액세스/리프레시 토큰 반환")
    void refresh_success() {
        given(refreshTokenStore.get(1L)).willReturn(Optional.of("valid-refresh"));
        given(tokenManager.getUserId("valid-refresh")).willReturn(1L);
        given(tokenManager.generateAccessToken(1L)).willReturn("new-access");
        given(tokenManager.generateRefreshToken(1L)).willReturn("new-refresh");

        TokenResult result = authService.refresh("valid-refresh");

        assertThat(result.accessToken()).isEqualTo("new-access");
        assertThat(result.refreshToken()).isEqualTo("new-refresh");
        verify(refreshTokenStore).save(1L, "new-refresh");
    }

    @Test
    @DisplayName("유효하지 않은 리프레시 토큰으로 갱신 시 AUTH_TOKEN_INVALID 예외 발생")
    void refresh_invalidToken() {
        willThrow(new CustomException(ErrorCode.AUTH_TOKEN_INVALID))
                .given(tokenManager).validateRefreshToken("invalid");

        assertThatThrownBy(() -> authService.refresh("invalid"))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                        .isEqualTo(ErrorCode.AUTH_TOKEN_INVALID));
    }

    @Test
    @DisplayName("만료된 리프레시 토큰으로 갱신 시 AUTH_TOKEN_EXPIRED 예외 발생")
    void refresh_expiredToken() {
        willThrow(new CustomException(ErrorCode.AUTH_TOKEN_EXPIRED))
                .given(tokenManager).validateRefreshToken("expired-refresh");

        assertThatThrownBy(() -> authService.refresh("expired-refresh"))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                        .isEqualTo(ErrorCode.AUTH_TOKEN_EXPIRED));
    }

    @Test
    @DisplayName("Redis에 없는 리프레시 토큰으로 갱신 시 AUTH_TOKEN_INVALID 예외 발생")
    void refresh_tokenNotInRedis() {
        given(tokenManager.getUserId("valid-refresh")).willReturn(1L);
        given(refreshTokenStore.get(1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refresh("valid-refresh"))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                        .isEqualTo(ErrorCode.AUTH_TOKEN_INVALID));
    }

    @Test
    @DisplayName("로그아웃 시 Redis에서 리프레시 토큰 삭제")
    void logout_success() {
        authService.logout(1L);

        verify(refreshTokenStore).delete(1L);
    }
}
