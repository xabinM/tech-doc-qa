package com.example.backend.application.auth;

import com.example.backend.common.exception.CustomException;
import com.example.backend.common.exception.ErrorCode;
import com.example.backend.domain.auth.User;
import com.example.backend.domain.auth.UserRepository;
import com.example.backend.infrastructure.auth.JwtProvider;
import com.example.backend.infrastructure.auth.RedisTokenRepository;
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
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @InjectMocks
    AuthService authService;

    @Mock
    UserRepository userRepository;

    @Mock
    PasswordEncoder passwordEncoder;

    @Mock
    JwtProvider jwtProvider;

    @Mock
    RedisTokenRepository redisTokenRepository;

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
        given(jwtProvider.generateAccessToken(any())).willReturn("access-token");
        given(jwtProvider.generateRefreshToken(any())).willReturn("refresh-token");

        TokenResult result = authService.login("test@example.com", "password1");

        assertThat(result.accessToken()).isEqualTo("access-token");
        assertThat(result.refreshToken()).isEqualTo("refresh-token");
        verify(redisTokenRepository).saveRefreshToken(any(), eq("refresh-token"));
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
}
