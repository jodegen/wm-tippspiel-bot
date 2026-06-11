--liquibase formatted sql

--changeset wmtippspiel:005-create-reminder-log
-- Merkt sich, für welche Spiele die Tipp-Erinnerung bereits verschickt wurde
-- (Idempotenz: kein zweiter Reminder pro Spiel).
CREATE TABLE reminder_log (
    match_id    BIGINT      PRIMARY KEY REFERENCES matches (id),
    reminded_at TIMESTAMPTZ NOT NULL
);
--rollback DROP TABLE reminder_log;
