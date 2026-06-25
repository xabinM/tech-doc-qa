package com.example.backend.application.query.port;

import com.example.backend.application.query.ConversationTurn;

import java.util.List;
import java.util.function.Consumer;

public interface RagPort {

    /**
     * RAG 서버에서 답변을 토큰 단위로 스트리밍한다.
     *
     * 각 토큰을 onToken으로 전달하며, 스트림이 끝날 때까지 블로킹한다.
     * 실패 시 CustomException(QUERY_RAG_SERVER_ERROR)을 던진다.
     */
    void askStream(String question, List<ConversationTurn> history, Consumer<String> onToken);
}
