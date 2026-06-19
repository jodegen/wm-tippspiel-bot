package com.example.wmtippspiel.publicapi;

import java.util.List;

import com.example.wmtippspiel.publicapi.bracket.BracketService;
import com.example.wmtippspiel.publicapi.dto.BracketDto;
import com.example.wmtippspiel.publicapi.dto.LeaderboardRowDto;
import com.example.wmtippspiel.publicapi.dto.LiveMatchDto;
import com.example.wmtippspiel.publicapi.dto.MatchDto;
import com.example.wmtippspiel.publicapi.dto.MatchTipsDto;
import com.example.wmtippspiel.publicapi.dto.ProfileDto;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

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
@Tag(name = "Öffentliche API", description = "Rein lesende Endpoints für die externe Website (ohne Auth, nur GET).")
public class PublicApiController {

    private final PublicQueryService query;
    private final BracketService bracket;

    public PublicApiController(PublicQueryService query, BracketService bracket) {
        this.query = query;
        this.bracket = bracket;
    }

    @GetMapping("/schedule")
    @Operation(summary = "Spielplan",
            description = "Alle Spiele bzw. gefiltert nach Phase/Gruppe/Spieltag. Anstoßzeit in UTC.")
    public List<MatchDto> schedule(
            @Parameter(description = "Turnierphase, z. B. GROUP_STAGE") @RequestParam(required = false) String stage,
            @Parameter(description = "Gruppen-Label, z. B. A") @RequestParam(required = false) String group,
            @Parameter(description = "Spieltag") @RequestParam(required = false) Integer matchday) {
        return query.schedule(stage, group, matchday);
    }

    @GetMapping("/matches/live")
    @Operation(summary = "Live-Spiele",
            description = "Aktuell laufende Spiele mit aktuellem Stand; leere Liste, wenn keines läuft.")
    public List<LiveMatchDto> live() {
        return query.liveMatches();
    }

    @GetMapping("/leaderboard")
    @Operation(summary = "Leaderboard",
            description = "Vollständige Rangliste mit Punkten, exakten Treffern und Rang-Veränderung.")
    public List<LeaderboardRowDto> leaderboard() {
        return query.leaderboard();
    }

    @GetMapping("/bracket")
    @Operation(summary = "K.o.-Turnierbaum (Bracket)",
            description = "Kompletter K.o.-Baum (6 Runden) mit Teams/Platzhaltern, Ergebnis, Status, "
                    + "FIFA-Match-Nr und Kanten. Gewinner rücken zur Laufzeit nach (nicht persistiert).")
    public BracketDto bracket() {
        return bracket.build();
    }

    @GetMapping("/matches/{matchId}/tips")
    @Operation(summary = "Tipps eines Spiels (erst nach Anpfiff)",
            description = "Liefert die abgegebenen Tipps NUR, wenn das Spiel angepfiffen ist "
                    + "(now() ≥ kickoff UND revealed). Davor: released=false und leere Tipp-Liste.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Tipps (released=true) oder reveal-gesperrt (released=false)"),
            @ApiResponse(responseCode = "404", description = "Spiel unbekannt", content = @io.swagger.v3.oas.annotations.media.Content)
    })
    public MatchTipsDto matchTips(@Parameter(description = "Öffentliche Fixture-ID") @PathVariable long matchId) {
        return query.matchTips(matchId);
    }

    @GetMapping("/players/{publicId}")
    @Operation(summary = "Spielerprofil",
            description = "Profil über den stabilen, nicht-sensiblen öffentlichen Identifier (NICHT die Discord-ID).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Profil mit Statistik, Verteilung, best/worst und Historie"),
            @ApiResponse(responseCode = "404", description = "Identifier unbekannt", content = @io.swagger.v3.oas.annotations.media.Content)
    })
    public ProfileDto profile(@Parameter(description = "Öffentlicher Spieler-Identifier") @PathVariable String publicId) {
        return query.profile(publicId);
    }
}
