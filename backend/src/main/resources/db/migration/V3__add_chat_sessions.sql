CREATE TABLE chat_sessions
(
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT       NOT NULL REFERENCES users (id),
    title      VARCHAR(200) NOT NULL,
    created_at TIMESTAMP    NOT NULL
);

CREATE INDEX idx_chat_sessions_user_id_id ON chat_sessions (user_id, id DESC);
