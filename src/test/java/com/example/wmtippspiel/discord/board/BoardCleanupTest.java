package com.example.wmtippspiel.discord.board;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit-Tests für das Start-Cleanup-Prädikat (F7-Redesign, FR-016/018/019):
 * eigene Nachrichten außer {@code board:main} werden gelöscht; fremde und die
 * gültige Board-Nachricht bleiben erhalten. (Empfohlen, nicht III-pflichtig.)
 */
class BoardCleanupTest {

    private static final long SELF = 999L;
    private static final long OTHER = 123L;
    private static final String BOARD_MAIN = "board-main-msg-id";

    @Test
    @DisplayName("Eigene, nicht-board:main Nachricht wird gelöscht")
    void deletesOwnOrphan() {
        assertThat(BoardService.shouldDelete(SELF, SELF, "some-old-msg", BOARD_MAIN)).isTrue();
    }

    @Test
    @DisplayName("Fremde Nachricht bleibt unangetastet")
    void keepsForeignMessage() {
        assertThat(BoardService.shouldDelete(SELF, OTHER, "foreign-msg", BOARD_MAIN)).isFalse();
    }

    @Test
    @DisplayName("Gültige board:main-Nachricht wird nie gelöscht")
    void keepsBoardMain() {
        assertThat(BoardService.shouldDelete(SELF, SELF, BOARD_MAIN, BOARD_MAIN)).isFalse();
    }

    @Test
    @DisplayName("Erststart ohne getracktes Board: eigene Alt-Nachrichten werden gelöscht")
    void deletesOwnWhenNoBoardTracked() {
        assertThat(BoardService.shouldDelete(SELF, SELF, "old-day-slot", null)).isTrue();
    }
}
