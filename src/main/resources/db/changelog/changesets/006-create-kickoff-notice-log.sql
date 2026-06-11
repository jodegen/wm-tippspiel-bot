--liquibase formatted sql

--changeset wmtippspiel:006-create-kickoff-notice-log
-- Merkt sich, für welche Spiele die "Anpfiff steht bevor"-Nachricht schon
-- gepostet wurde (Idempotenz: genau ein Post pro Spiel).
CREATE TABLE kickoff_notice_log (
    match_id    BIGINT      PRIMARY KEY REFERENCES matches (id),
    notified_at TIMESTAMPTZ NOT NULL
);
--rollback DROP TABLE kickoff_notice_log;
