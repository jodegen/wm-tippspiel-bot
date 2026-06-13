--liquibase formatted sql

--changeset wmtippspiel:009-reduce-board-slots
-- F7-Redesign: bot_messages auf den einzigen Slot board:main reduzieren.
-- Die zugehörigen Discord-Nachrichten der Alt-Slots werden beim Start vom
-- Board-Cleanup entfernt (sie gelten danach als nicht mehr getrackt).
DELETE FROM bot_messages WHERE key LIKE 'board:day:%' OR key = 'board:nav';
--rollback SELECT 1;
