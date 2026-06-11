--liquibase formatted sql

--changeset wmtippspiel:008-add-matches-notified-score
-- Zuletzt für Tor-Pings gemeldeter Stand (F8), getrennt vom tatsächlichen
-- home_score/away_score. Default 0; sichert Idempotenz & Neustart-Recovery.
ALTER TABLE matches ADD COLUMN notified_home INT NOT NULL DEFAULT 0;
ALTER TABLE matches ADD COLUMN notified_away INT NOT NULL DEFAULT 0;
--rollback ALTER TABLE matches DROP COLUMN notified_home; ALTER TABLE matches DROP COLUMN notified_away;
