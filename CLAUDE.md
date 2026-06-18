<!-- SPECKIT START -->
For additional context about technologies to be used, project structure,
shell commands, and other important information, read the current plan:
`specs/007-leaderboard-recap-profile/plan.md` (aktuelles Feature — F11 Live-Leaderboard-Board, F12 Spieltags-Rückblick, F13 /profil).
Vorheriges Feature: `specs/006-check24-scoring/plan.md` (CHECK24-Punkteschema 4/3/2/0).
Weitere: `specs/004-dynamic-bot-presence/plan.md` (F9), `specs/003-consolidated-board/plan.md` (F7), `specs/002-live-goal-notifications/plan.md` (F8).
Basis-Feature/Architektur: `specs/001-wm-tippspiel-bot/plan.md`.

Aktuelles Feature: **006-check24-scoring** (CHECK24-Punkteschema, vierstufig).
Punktewertung wird von 3/1/0 auf **4/3/2/0** umgestellt: `ScoringService.points(...)`
ist die einzige Berechnungsstelle (Reihenfolge exakt → vorzeichenbehaftete Tordifferenz
→ Tendenz → 0), genutzt von `EvaluationService` (F5) und neuem `RecalculationService`.
Leaderboard-`exact_hits` wird vom Punktwert entkoppelt: live per JOIN-Vergleich
`tips.home_score=matches.home_score AND tips.away_score=matches.away_score` (neue
`MatchRepository.findEvaluated()`). Rückwirkende Neuberechnung als idempotenter
`ScoreRecalculationRunner` (ApplicationRunner, `app.scoring.recalc-on-startup`, Default
true): überschreibt `tips.points` nur bei Abweichung, loggt alt→neu. **Keine
Schema-Änderung, kein Liquibase-Changeset, keine neuen Abhängigkeiten.**

Vorheriges Feature: **004-dynamic-bot-presence** (F9 — Dynamische Bot-Presence).
Neuer `PresenceManager` (`presence`-Paket) kapselt zustandsgesteuerte JDA-Presence
(Activity-Typ `watching`): priorisierte Zustände **LIVE > UPCOMING > IDLE**,
Auswahl bei mehreren Live-Spielen = zuletzt verändertes Spiel (Tie-Breaker Anpfiff).
Reine Helfer `PresenceStateResolver` (Priorität/Textbau) und `PresenceThrottle`
(Mindestabstand `${app.presence.min-update-interval-ms:5000}` + Coalescing, garantiert
≤5/20 s), `TeamCodeResolver` (Ressource `presence/team-codes.properties`, Fallback
Klartext). `setActivity` nur bei tatsächlicher Textänderung. Verdrahtung rein additiv:
`LiveGoalPollJob` + `BoardRefreshJob` rufen zusätzlich `recompute()`; JDA
`onReady`/`onReconnected`/`onSessionRecreate` für Initial-/Reconnect-Setzen.
`ScoreDiffGoalEventSource` persistiert frischen Live-Stand+Status in `matches`
(vorhandene Spalten); neue `MatchRepository`-Methoden `findInPlay()` (read) +
`updateLiveScore()` (write). **Keine Schema-Änderung, kein Liquibase-Changeset,
keine neuen Abhängigkeiten.**

Active feature: **001-wm-tippspiel-bot** (WM 2026 Tippspiel Discord-Bot).
Stack: Java 21, Spring Boot 3.x, JDA (dauerhafte Gateway-Verbindung), PostgreSQL
via `JdbcClient`, Liquibase (ein Changeset pro Tabelle), `WebClient` für
football-data.org & The Odds API. Zeit: UTC speichern, Europe/Berlin anzeigen.
Kernlogik (Punktewertung 4/3/2/0, Reveal-/Eval-Timing) ist test-pflichtig
(Verfassung Prinzip III). Siehe auch `research.md`, `data-model.md`,
`contracts/`, `quickstart.md` im Feature-Ordner.
<!-- SPECKIT END -->
