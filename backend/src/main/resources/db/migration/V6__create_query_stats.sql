CREATE TABLE query_stats
(
    id          BIGSERIAL PRIMARY KEY,
    stat_date   DATE      NOT NULL,
    user_id     BIGINT    NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    query_count INT       NOT NULL DEFAULT 0,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (stat_date, user_id)
);

CREATE INDEX idx_query_stats_date    ON query_stats (stat_date);
CREATE INDEX idx_query_stats_user_id ON query_stats (user_id);
