--liquibase formatted sql
--changeset wmtippspiel:012-create-matchday-recap
-- Idempotenz-Marker für den Spieltags-Rückblick (F12): ein Datensatz je bereits
-- gepostetem Recap-Key ('md:<n>' bzw. Fallback 'stage:<STAGE>'). Garantiert das
-- einmalige Posten je Spieltag (FR-016). Additiv (Verfassung II).
CREATE TABLE matchday_recap (
    recap_key TEXT        PRIMARY KEY,
    posted_at TIMESTAMPTZ NOT NULL
);
--rollback DROP TABLE matchday_recap;
