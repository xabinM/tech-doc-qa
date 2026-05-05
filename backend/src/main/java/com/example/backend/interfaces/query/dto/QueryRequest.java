package com.example.backend.interfaces.query.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record QueryRequest(
        @NotBlank(message = "질문을 입력해 주세요")
        @Size(max = 1000, message = "질문은 1000자 이내로 입력해 주세요")
        String question
) {
}
