--liquibase formatted sql

--changeset wmtippspiel:003-create-bot-messages
CREATE TABLE bot_messages (
    key        TEXT        PRIMARY KEY,
    channel_id TEXT        NOT NULL,
    message_id TEXT        NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);
--rollback DROP TABLE bot_messages;
