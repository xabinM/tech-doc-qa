ALTER TABLE query_logs
    ADD COLUMN session_id BIGINT REFERENCES chat_sessions (id);

CREATE INDEX idx_query_logs_session_id ON query_logs (session_id, id ASC);
