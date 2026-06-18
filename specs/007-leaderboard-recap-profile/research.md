# Phase 0 — Research: Live-Leaderboard-Board, Spieltags-Rückblick & /profil

Alle offenen Punkte aus Spec/Technical Context sind hier aufgelöst. Es verbleiben **keine** NEEDS CLARIFICATION.

## R1 — Wiederverwendung der F7-Board-Mechanik (F11)

- **Decision**: Die Upsert-/404-Recovery-/Cleanup-Logik aus `BoardService` (Slot `board:main`, F7) wird in einen wiederverwendbaren Helfer `TrackedBoardPublisher` extrahiert: `editOrPost(channel, key, embed)` und `cleanupOrphans(channel, keepKey)`. `BoardService` (F7) und `LeaderboardBoardService` (F11) nutzen denselben Helfer; `BotMessageRepository` bleibt unverändert (zweiter Slot `board:leaderboard`).
- **Rationale**: Vermeidet Duplikat-Drift bei Recovery/Cleanup; eine einzige korrekte Implementierung. „Analog zur F7-Mechanik" aus dem Auftrag = identische Mechanik, nicht kopierte.
- **Alternatives considered**: (a) Logik in `LeaderboardBoardService` duplizieren — verworfen (Wartungslast, Drift-Risiko). (b) `BoardService` generisch parametrisieren statt extrahieren — verworfen, da es F7-spezifische Aufbaulogik (findUpcoming) mit dem Transport vermischt.

## R2 — Trigger-Granularität: Auswertungs-Batch (F11)

- **Decision**: Das Leaderboard-Board wird **einmal pro `evaluateJob`-Lauf** aktualisiert, nachdem `EvaluationService.evaluateFinishedMatches()` alle in diesem Lauf fälligen Spiele verarbeitet hat und **mindestens eines** ausgewertet wurde. `EvaluationService` gibt die Anzahl/Liste der ausgewerteten Spiele zurück (heute: `int`); `EvaluateJob` ruft danach `LeaderboardBoardService.refreshAfterEvaluation()` auf.
- **Rationale**: Deckt die geklärte Anforderung „pro Auswertungs-Batch" (Clarify 2026-06-18). Verhindert Pfeil-Flackern, wenn mehrere Spiele im selben Lauf fertig werden, und hält die Edit-Frequenz niedrig.
- **Alternatives considered**: Per-Match über den bestehenden `EvaluationPublisher`-Hook — verworfen (zu granular, mehrfaches Diffen/Editieren pro Lauf, widerspricht der Clarify-Entscheidung).

## R3 — Persistenz der Rang-Vergleichsbasis (F11)

- **Decision**: Neue Tabelle `leaderboard_snapshot(user_id TEXT PK, rank INT NOT NULL, captured_at TIMESTAMPTZ NOT NULL)`. Sie hält den Rang **jedes** Users (nicht nur Top-N) aus dem zuletzt abgeschlossenen Batch. Ablauf: aktuelles Ranking (`TipRepository.leaderboard()`) berechnen → gegen Snapshot diffen → Board editieren → Snapshot **komplett ersetzen** (`replaceAll(currentRanks)`).
- **Rationale**: Übersteht Neustart (FR-007, SC-003). Volles Ranking statt nur Top-N nötig, damit Auf-/Absteiger an der Top-N-Grenze korrekte Pfeile bekommen und „NEU" nur bei tatsächlich neuem User erscheint. Eigene Tabelle statt `previous_rank`-Spalte, da es keine `users`-Tabelle gibt (User existieren nur über `tips`).
- **Alternatives considered**: (a) `previous_rank`-Spalte „pro User" — kein User-Stammsatz vorhanden, verworfen. (b) Nur Top-N speichern — verworfen (Grenzfälle an Rang N/N+1 falsch). (c) In-Memory — verworfen (übersteht keinen Neustart, verletzt FR-007).

## R4 — Rang-Diff-Semantik & Tie-Break (F11/F13)

- **Decision**: Ranking-Reihenfolge = `total_points DESC, exact_hits DESC` (identisch zur bestehenden `TipRepository.leaderboard()`-Query und `RanglisteEmbed`-Standard-Competition-Ranking). Rang-Diff je User: `previousRank == null → "NEU"`; `current < previous → "↑(previous-current)"`; `current > previous → "↓(current-previous)"`; sonst `"–"`. Gleichstände erhalten denselben Rang (Standard Competition Ranking, „1224").
- **Rationale**: Konsistente Wertung über F6/F11/F12/F13 (FR-025). Wiederverwendung der vorhandenen Sortierung verhindert Abweichungen.
- **Alternatives considered**: Dense Ranking (1223) — verworfen zugunsten Konsistenz mit bestehendem `RanglisteEmbed`.

## R5 — `matchday`-Persistenz & Spieltag-Abgrenzung (F12)

- **Decision**: Additive Spalte `matches.matchday INT NULL`. Der Match-Sync übernimmt das `matchday`-Feld aus football-data.org. Die Recap-Einheit ist ein **Recap-Key** (TEXT): bei vorhandenem `matchday` = `"md:<n>"`, sonst (z. B. K.o.-Spiele ohne matchday) Fallback = `"stage:<STAGE>"`. Ein Spieltag/Recap-Key gilt als abgeschlossen, wenn **alle** zugehörigen `matches` `status=FINISHED AND evaluated=TRUE` sind.
- **Rationale**: Setzt die Clarify-Entscheidung „API-matchday-Feld" um. Der Fallback auf `stage` deckt football-data-Daten ab, in denen K.o.-Runden kein `matchday` führen, ohne die Idempotenz-Mechanik zu ändern.
- **Alternatives considered**: (a) Gruppierung über vorhandenes `stage`+`group_label` ohne neue Spalte — verworfen, da die Clarify-Entscheidung explizit auf das API-`matchday`-Feld lautet und Gruppenspieltage 1–3 sonst nicht trennbar sind. (b) Kalendertag — in Clarify verworfen.

## R6 — Idempotentes Posten des Rückblicks (F12)

- **Decision**: Neue Tabelle `matchday_recap(recap_key TEXT PK, posted_at TIMESTAMPTZ NOT NULL)`. Vor dem Posten prüft `MatchdayRecapService` per `INSERT ... ON CONFLICT (recap_key) DO NOTHING`; nur wenn die Zeile **neu** eingefügt wurde (rowsAffected=1), wird gepostet. Das macht das Posten auch bei gleichzeitigen/Neustart-Läufen exakt einmalig.
- **Rationale**: DB-gestützte Idempotenz (FR-016, SC-005) übersteht Neustarts und verhindert Doppel-Posts ohne In-Memory-Zustand. Eine nachträgliche Ergebniskorrektur (Re-Evaluation) lässt die `matchday_recap`-Zeile bestehen → kein zweiter Post (FR-018).
- **Alternatives considered**: Boolean-Flag `matches.recap_posted` je Spiel — verworfen (Flag gehört zum Spieltag, nicht zum Einzelspiel; ON-CONFLICT-Insert ist atomar und einfacher).

## R7 — Auslöser für die Recap-Prüfung (F12)

- **Decision**: `EvaluateJob` ruft nach dem Auswertungs-Batch (gleiche Stelle wie F11) `MatchdayRecapService.postCompletedRecaps()` auf. Der Service ermittelt die Recap-Keys der gerade ausgewerteten Spiele, prüft je Key auf Vollständigkeit und postet idempotent.
- **Rationale**: Kein neuer Scheduler nötig; reagiert genau dann, wenn sich der Auswertungszustand ändert. Rein DB-lesend (kein zusätzlicher API-Verkehr).
- **Alternatives considered**: Eigener `@Scheduled`-Job — verworfen (redundant; Zustand ändert sich nur durch Auswertung).

## R8 — `/profil`-Aggregation & Antwort-Sichtbarkeit (F13)

- **Decision**: `ProfilCommand` nutzt (a) `TipRepository.leaderboard()` für Rang/Gesamtpunkte/exakte Treffer/Tipp-Anzahl und (b) eine neue Methode `TipRepository.findEvaluatedTipsByUser(userId)` (Zeilen: match home/away-Namen, Ergebnis, Tipp, `points`, Quoten) für 4/3/2/0-Verteilung sowie besten/schlechtesten Tipp. Trefferquote = `exactHits / evaluatedTipCount` (bei 0 ausgewerteten Tipps: „—" statt Division). Bester Tipp = höchste `points` (Tie-Break: unwahrscheinlichstes Ergebnis per Quote); schlechtester = niedrigste `points` unter ausgewerteten Tipps. Antwort **öffentlich** (`event.replyEmbeds(...).queue()`, **nicht** ephemeral) gemäß FR-023; bei DB-Last `deferReply()` (öffentlich) nutzen, um im 3s-Fenster zu bleiben.
- **Rationale**: Wiederverwendung der zentralen Wertungsdaten (keine Neuberechnung von Punkten, FR-026). Öffentliche Antwort entspricht der Clarify-Entscheidung. Keine Schema-Änderung nötig.
- **Alternatives considered**: Ephemeral (wie im Codebase-Muster für private Antworten) — verworfen, Spec/Clarify fordert öffentlich. Punkte je Tipp neu berechnen statt `tips.points` lesen — verworfen (verletzt „nur lesen"-Prinzip).

## R9 — Embed-Limits / defensives Abschneiden (F11)

- **Decision**: Leaderboard-Liste als kompakte `description` (eine Zeile pro User), hart auf Top-N (Default 15) begrenzt **und** zusätzlich defensive Zeichen-Obergrenze (analog `BoardEmbed`-Truncation bei ~4000 Zeichen). Konfigurierbar über `app.leaderboard.top-n`.
- **Rationale**: Garantiert Einhaltung der Discord-Grenzen (FR-008, SC-004) unabhängig von Namenslängen.
- **Alternatives considered**: Ein Feld pro User — verworfen (25-Felder-Limit, weniger kompakt).

## R10 — Konfiguration (F11)

- **Decision**: Neue Keys `app.discord.leaderboard-channel-id` (eigener read-only Channel, getrennt von `board-channel-id`) und `app.leaderboard.top-n` (Default 15) in `AppProperties.Discord` bzw. neuem `AppProperties.Leaderboard`.
- **Rationale**: Setzt Clarify-Entscheidung „eigener Channel" um; Top-N konfigurierbar laut Spec-Assumption.
- **Alternatives considered**: Wiederverwendung von `board-channel-id` — verworfen (Clarify: eigener Channel; vereinfacht Cleanup, da pro Channel nur ein Slot erwartet wird).
