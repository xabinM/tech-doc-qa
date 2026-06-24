package com.example.backend.application.query;

import com.example.backend.application.query.port.AnswerStream;
import com.example.backend.application.query.port.RagPort;
import com.example.backend.common.exception.CustomException;
import com.example.backend.common.exception.ErrorCode;
import com.example.backend.domain.query.QueryLog;
import com.example.backend.domain.query.QueryLogRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class QueryJobProcessorTest {

    @InjectMocks
    QueryJobProcessor processor;

    @Mock
    ChatSessionService chatSessionService;

    @Mock
    RagPort ragPort;

    @Mock
    QueryCacheService queryCacheService;

    @Mock
    QueryLogRepository queryLogRepository;

    @Mock
    AnswerStream answerStream;

    @Test
    @DisplayName("정상 처리 시 답변 토큰·완료 발행 및 이력 저장")
    void process_success_publishesAnswerAndSavesHistory() {
        QueryJob job = new QueryJob("job-1", 1L, "Spring이란?", 100L, null);
        given(chatSessionService.loadConversationHistory(100L)).willReturn(List.of());
        given(queryCacheService.getOrCompute(anyString(), any())).willReturn("Spring 답변");

        processor.process(job);

        verify(answerStream).publishToken("job-1", "Spring 답변");
        verify(answerStream).publishDone("job-1");
        verify(answerStream, never()).publishError(anyString(), anyString());

        ArgumentCaptor<QueryLog> captor = ArgumentCaptor.forClass(QueryLog.class);
        verify(queryLogRepository).save(captor.capture());
        assertThat(captor.getValue().getQuestion()).isEqualTo("Spring이란?");
        assertThat(captor.getValue().getAnswer()).isEqualTo("Spring 답변");
    }

    @Test
    @DisplayName("RAG 오류 시 error 발행, done·이력 저장 안 함")
    void process_ragError_publishesError() {
        QueryJob job = new QueryJob("job-2", 1L, "Spring이란?", 100L, null);
        given(chatSessionService.loadConversationHistory(100L)).willReturn(List.of());
        given(queryCacheService.getOrCompute(anyString(), any()))
                .willThrow(new CustomException(ErrorCode.QUERY_RAG_SERVER_ERROR));

        processor.process(job);

        verify(answerStream).publishError("job-2", ErrorCode.QUERY_RAG_SERVER_ERROR.getCode());
        verify(answerStream, never()).publishDone(anyString());
        verify(queryLogRepository, never()).save(any());
    }
}
