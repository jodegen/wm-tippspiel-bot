--liquibase formatted sql

--changeset wmtippspiel:001-create-matches
CREATE TABLE matches (
    id          BIGINT       PRIMARY KEY,
    home        TEXT         NOT NULL,
    away        TEXT         NOT NULL,
    kickoff     TIMESTAMPTZ  NOT NULL,
    stage       TEXT         NOT NULL,
    group_label TEXT,
    channel     TEXT,
    odds_home   NUMERIC(6,2),
    odds_draw   NUMERIC(6,2),
    odds_away   NUMERIC(6,2),
    home_score  INT,
    away_score  INT,
    status      TEXT         NOT NULL DEFAULT 'SCHEDULED',
    revealed    BOOLEAN      NOT NULL DEFAULT FALSE,
    evaluated   BOOLEAN      NOT NULL DEFAULT FALSE
);
CREATE INDEX idx_matches_kickoff ON matches (kickoff);
CREATE INDEX idx_matches_reveal ON matches (revealed, kickoff);
CREATE INDEX idx_matches_eval ON matches (status, evaluated);
--rollback DROP TABLE matches;
