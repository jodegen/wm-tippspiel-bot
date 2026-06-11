package com.example.wmtippspiel.scheduling;

import com.example.wmtippspiel.discord.board.BoardService;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Aktualisiert das Live-Board (~15 Min, im Takt des Spielplan-Syncs; F7).
 * Häufigere Live-Stand-Aktualisierung während laufender Spiele ist bewusst nicht
 * im MVP – echte Live-Tor-Updates sind Backlog (E2).
 */
@Component
public class BoardRefreshJob {

    private final BoardService boardService;

    public BoardRefreshJob(BoardService boardService) {
        this.boardService = boardService;
    }

    @Scheduled(fixedDelayString = "${app.jobs.board-refresh-interval-ms:900000}", initialDelay = 30_000)
    public void refreshBoard() {
        boardService.refresh();
    }
}
