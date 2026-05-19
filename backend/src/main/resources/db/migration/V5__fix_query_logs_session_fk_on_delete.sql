ALTER TABLE query_logs DROP CONSTRAINT IF EXISTS query_logs_session_id_fkey;
ALTER TABLE query_logs
    ADD CONSTRAINT query_logs_session_id_fkey
        FOREIGN KEY (session_id) REFERENCES chat_sessions (id) ON DELETE SET NULL;
