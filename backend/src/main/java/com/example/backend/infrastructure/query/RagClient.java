package com.example.backend.infrastructure.query;

import com.example.backend.application.query.port.RagPort;
import com.example.backend.common.exception.CustomException;
import com.example.backend.common.exception.ErrorCode;
import org.springframework.stereotype.Component;

@Component
public class RagClient implements RagPort {

    @Override
    public String ask(String question) {
        // TODO: RAG 서버 연동 구현 예정
        throw new CustomException(ErrorCode.QUERY_RAG_SERVER_ERROR);
    }
}
