package com.example.backend.interfaces.auth;

import com.example.backend.application.auth.AuthService;
import com.example.backend.application.auth.TokenResult;
import com.example.backend.common.response.ApiResponse;
import com.example.backend.interfaces.auth.dto.AuthLoginRequest;
import com.example.backend.interfaces.auth.dto.AuthLoginResponse;
import com.example.backend.interfaces.auth.dto.AuthSignupRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<Void>> signup(@RequestBody @Valid AuthSignupRequest request) {
        authService.signup(request.email(), request.password());
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthLoginResponse>> login(@RequestBody @Valid AuthLoginRequest request) {
        TokenResult result = authService.login(request.email(), request.password());
        return ResponseEntity.ok(ApiResponse.ok(AuthLoginResponse.from(result)));
    }
}
