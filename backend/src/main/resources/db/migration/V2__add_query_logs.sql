CREATE TABLE query_logs
(
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT    NOT NULL REFERENCES users (id),
    question   TEXT      NOT NULL,
    answer     TEXT      NOT NULL,
    created_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_query_logs_user_id_id ON query_logs (user_id, id DESC);
