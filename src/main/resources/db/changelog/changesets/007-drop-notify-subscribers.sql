--liquibase formatted sql

--changeset wmtippspiel:007-drop-notify-subscribers
-- Abonnenten-Liste entfällt: Benachrichtigungen laufen ausschließlich über die
-- Discord-Rolle (Mitgliedschaft = Empfänger). Tabelle wird nicht mehr benötigt.
DROP TABLE IF EXISTS notify_subscribers;
--rollback CREATE TABLE notify_subscribers (user_id TEXT PRIMARY KEY, username TEXT NOT NULL, subscribed_at TIMESTAMPTZ NOT NULL);
