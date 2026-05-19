package com.example.backend.application.query.port;

import com.example.backend.application.query.ConversationTurn;

import java.util.List;

public interface RagPort {

    String ask(String question, List<ConversationTurn> history);
}
