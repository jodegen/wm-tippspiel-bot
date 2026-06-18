--liquibase formatted sql
--changeset wmtippspiel:011-create-leaderboard-snapshot
-- Vergleichsbasis für die Rang-Veränderung im Leaderboard-Board (F11): Rang je
-- Teilnehmer aus dem zuletzt abgeschlossenen Auswertungs-Batch. Übersteht
-- Neustarts (FR-007). Additiv, keine bestehende Tabelle berührt (Verfassung II).
CREATE TABLE leaderboard_snapshot (
    user_id     TEXT        PRIMARY KEY,
    rank        INT         NOT NULL,
    captured_at TIMESTAMPTZ NOT NULL
);
--rollback DROP TABLE leaderboard_snapshot;
