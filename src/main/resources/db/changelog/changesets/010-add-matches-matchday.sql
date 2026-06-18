--liquibase formatted sql
--changeset wmtippspiel:010-add-matches-matchday
-- Spieltag-Bezeichner aus football-data.org für den Spieltags-Rückblick (F12).
-- Nullable (manche K.o.-Daten führen keinen matchday). Additiv (Verfassung II).
ALTER TABLE matches ADD COLUMN matchday INT;
--rollback ALTER TABLE matches DROP COLUMN matchday;
