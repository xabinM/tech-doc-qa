package com.example.backend.interfaces.auth;

import com.example.backend.application.auth.AuthService;
import com.example.backend.common.response.ApiResponse;
import com.example.backend.interfaces.auth.dto.ChangePasswordRequest;
import com.example.backend.interfaces.auth.dto.DeleteAccountRequest;
import com.example.backend.interfaces.auth.dto.UserProfileResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

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

    @PutMapping("/me/password")
    public ApiResponse<Void> changePassword(
            @AuthenticationPrincipal Long userId,
            @RequestBody @Valid ChangePasswordRequest request
    ) {
        authService.changePassword(userId, request.currentPassword(), request.newPassword());
        return ApiResponse.ok("비밀번호가 변경되었습니다");
    }

    @DeleteMapping("/me")
    public ApiResponse<Void> deleteAccount(
            @AuthenticationPrincipal Long userId,
            @RequestBody @Valid DeleteAccountRequest request
    ) {
        authService.deleteAccount(userId, request.password());
        return ApiResponse.ok("계정이 삭제되었습니다");
    }
}
