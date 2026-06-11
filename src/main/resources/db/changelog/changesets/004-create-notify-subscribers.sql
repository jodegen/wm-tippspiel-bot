--liquibase formatted sql

--changeset wmtippspiel:004-create-notify-subscribers
CREATE TABLE notify_subscribers (
    user_id       TEXT        PRIMARY KEY,
    username      TEXT        NOT NULL,
    subscribed_at TIMESTAMPTZ NOT NULL
);
--rollback DROP TABLE notify_subscribers;
