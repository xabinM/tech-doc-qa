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
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
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
    QueryLogRepository queryLogRepository;

    @Mock
    AnswerStream answerStream;

    @Test
    @DisplayName("RAG 토큰을 순차 중계하고 전체 답변 이력 저장")
    void process_streamsTokensAndSaves() {
        QueryJob job = new QueryJob("job-1", 1L, "Spring이란?", 100L, null);
        given(chatSessionService.loadConversationHistory(100L)).willReturn(List.of());
        doAnswer(inv -> {
            Consumer<String> onToken = inv.getArgument(2);
            onToken.accept("Spring");
            onToken.accept(" Boot");
            return null;
        }).when(ragPort).askStream(eq("Spring이란?"), anyList(), any());

        processor.process(job);

        verify(answerStream).publishToken("job-1", "Spring");
        verify(answerStream).publishToken("job-1", " Boot");
        verify(answerStream).publishDone("job-1");
        verify(answerStream, never()).publishError(anyString(), anyString());

        ArgumentCaptor<QueryLog> captor = ArgumentCaptor.forClass(QueryLog.class);
        verify(queryLogRepository).save(captor.capture());
        assertThat(captor.getValue().getAnswer()).isEqualTo("Spring Boot");
    }

    @Test
    @DisplayName("RAG 오류 시 error 발행, done·이력 저장 안 함")
    void process_ragError_publishesError() {
        QueryJob job = new QueryJob("job-2", 1L, "Spring이란?", 100L, null);
        given(chatSessionService.loadConversationHistory(100L)).willReturn(List.of());
        doThrow(new CustomException(ErrorCode.QUERY_RAG_SERVER_ERROR))
                .when(ragPort).askStream(eq("Spring이란?"), anyList(), any());

        processor.process(job);

        verify(answerStream).publishError("job-2", ErrorCode.QUERY_RAG_SERVER_ERROR.getCode());
        verify(answerStream, never()).publishDone(anyString());
        verify(queryLogRepository, never()).save(any());
    }
}
