package com.example.backend.interfaces.auth;

import com.example.backend.application.auth.AuthService;
import com.example.backend.common.response.ApiResponse;
import com.example.backend.interfaces.auth.dto.UserProfileResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final AuthService authService;

    @GetMapping("/me")
    public ApiResponse<UserProfileResponse> me(@AuthenticationPrincipal Long userId) {
        var user = authService.getUserProfile(userId);
        return ApiResponse.ok("프로필이 조회되었습니다", UserProfileResponse.from(user));
    }
}
