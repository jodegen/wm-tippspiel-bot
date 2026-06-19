package com.example.wmtippspiel.publicapi;

import java.util.List;

import com.example.wmtippspiel.publicapi.dto.LeaderboardRowDto;
import com.example.wmtippspiel.publicapi.dto.LiveMatchDto;
import com.example.wmtippspiel.publicapi.dto.MatchDto;
import com.example.wmtippspiel.publicapi.dto.MatchTipsDto;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Öffentliche, rein lesende REST-Endpoints (Feature 008) unter {@code /api/public}.
 * Ausschließlich {@code @GetMapping} — es existiert KEIN Schreibpfad (FR-002),
 * jeder nicht-GET-Aufruf ergibt automatisch HTTP 405 (SC-006).
 */
@RestController
@RequestMapping("/api/public")
public class PublicApiController {

    private final PublicQueryService query;

    public PublicApiController(PublicQueryService query) {
        this.query = query;
    }

    /** Vollständiger bzw. gefilterter Spielplan (FR-006/007). */
    @GetMapping("/schedule")
    public List<MatchDto> schedule(@RequestParam(required = false) String stage,
                                   @RequestParam(required = false) String group,
                                   @RequestParam(required = false) Integer matchday) {
        return query.schedule(stage, group, matchday);
    }

    /** Aktuell laufende Spiele mit Stand (FR-008). */
    @GetMapping("/matches/live")
    public List<LiveMatchDto> live() {
        return query.liveMatches();
    }

    /** Vollständige Rangliste (FR-009). */
    @GetMapping("/leaderboard")
    public List<LeaderboardRowDto> leaderboard() {
        return query.leaderboard();
    }

    /**
     * Tipps eines Spiels — die serverseitige Anpfiff-Prüfung erfolgt im Service
     * (FR-012/013); vor Anpfiff enthält die Antwort keine Einzeltipps. Unbekanntes
     * Spiel → HTTP 404 (FR-019).
     */
    @GetMapping("/matches/{matchId}/tips")
    public MatchTipsDto matchTips(@PathVariable long matchId) {
        return query.matchTips(matchId);
    }
}
