--liquibase formatted sql
--changeset wmtippspiel:013-add-matches-winner
-- Endgültiger Sieger eines Spiels aus football-data score.winner (Feature 010,
-- Bracket-Endpoint). Nötig, da home_score/away_score allein durch Verlängerung/
-- Elfmeterschießen entschiedene K.o.-Spiele nicht auflösen können. Nullable
-- (unbekannt/noch nicht entschieden), additiv (Verfassung II), keine Migration
-- vorhandener Werte erforderlich.
ALTER TABLE matches ADD COLUMN winner TEXT;
--rollback ALTER TABLE matches DROP COLUMN winner;
