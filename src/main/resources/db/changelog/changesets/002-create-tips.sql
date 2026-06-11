--liquibase formatted sql

--changeset wmtippspiel:002-create-tips
CREATE TABLE tips (
    user_id    TEXT        NOT NULL,
    match_id   BIGINT      NOT NULL REFERENCES matches (id),
    username   TEXT        NOT NULL,
    home_score INT         NOT NULL CHECK (home_score >= 0),
    away_score INT         NOT NULL CHECK (away_score >= 0),
    created_at TIMESTAMPTZ NOT NULL,
    points     INT         NOT NULL DEFAULT 0,
    PRIMARY KEY (user_id, match_id)
);
CREATE INDEX idx_tips_match ON tips (match_id);
--rollback DROP TABLE tips;
