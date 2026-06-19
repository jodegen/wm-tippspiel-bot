# Phase 0 — Research & Entscheidungen: Öffentlicher Bracket-Endpoint

Alle offenen Punkte aus dem Technical Context sind aufgelöst. Keine `NEEDS CLARIFICATION` verbleiben.

## R1 — Abbildung der festen FIFA-Topologie

**Decision**: Die Topologie aus `WC2026_BRACKET_TOPOLOGY.md` wird als unveränderliche Code-Konstante in `BracketTopology` abgelegt: eine geordnete Liste von Topologie-Einträgen, je Eintrag `(stage, slotIndex, fifaMatchNo, sourceMatchNos[], nextMatchNo)`, plus eine Tabelle der LAST_32-Platzhalter-Labels je Slot (Heim/Auswärts).

**Rationale**: Die Struktur ist FIFA-fix und ändert sich nie (nur die Befüllung ändert sich). Eine Code-Konstante ist versionierbar, durch einen Unit-Test verifizierbar und vermeidet eine überflüssige DB-Tabelle (Spec FR-004: ausdrücklich nicht in der DB). Die Kanten der Datei gehen als Halbbaum sauber auf und werden durch `BracketTopologyConsistencyTest` abgesichert.

**Alternatives considered**:
- *In DB persistieren*: verworfen — widerspricht FR-004, erzeugt Migrations-/Pflegeaufwand für unveränderliche Daten.
- *Aus einer Resource-Datei (YAML) laden*: verworfen — zusätzliche Parsing-/Fehlerquelle ohne Mehrwert; eine getestete Java-Konstante ist robuster und refactoring-sicher.

## R2 — Slot → FIFA-Match-Nr-Zuordnung

**Decision**: `BracketSlotMapper` lädt die K.o.-Spiele (`stage <> GROUP_STAGE`), gruppiert nach Stage, sortiert je Stage nach `kickoff` aufsteigend mit Tie-Breaker `id` aufsteigend und vergibt Slot-Index `1..n`. Daraus die FIFA-Match-Nr: LAST_32 `72+slot`, LAST_16 `88+slot`, QUARTER_FINALS `96+slot`, SEMI_FINALS `100+slot`, THIRD_PLACE `103`, FINAL `104`.

**Rationale**: Exakt die in Spec (FR-005/006) und Topologie-Datei (Schritt 2–3) festgelegte Konvention. Deterministisch und reproduzierbar dank Tie-Breaker.

**Alternatives considered**:
- *Mapping über football-data-Match-Nummern*: nicht möglich — football-data liefert keine Match-Nummern (bestätigt).
- *Feste Zuordnung über Team-Namen*: verworfen — Teams stehen vor der Gruppenphase nicht fest.

**Verifikations-Hinweis (FR-018)**: Bevor live: einmal prüfen, ob die reale `kickoff`-Reihenfolge tatsächlich FIFA-Nr 73..104 entspricht. Falls nicht, ausschließlich die Slot→Match-Zuordnung anpassen; die Kanten-Logik bleibt unverändert. Wird in `quickstart.md` als manueller Check dokumentiert.

## R3 — `Stage`-Enum: fehlendes `LAST_32`

**Decision**: `Stage` um `LAST_32` erweitern und in `FootballDataClient.mapStage()` `"LAST_32" -> Stage.LAST_32` ergänzen. Die internen Singular-Namen (`QUARTER_FINAL`, `SEMI_FINAL`) bleiben unverändert; das Mapping akzeptiert weiterhin sowohl Plural- als auch Singularform der API.

**Rationale**: Die 48-Team-WM 2026 hat eine Runde der letzten 32. Ohne `LAST_32` fällt `mapStage` für diese Spiele still auf `GROUP_STAGE` zurück (latenter Bug). `stage` ist als `TEXT` gespeichert → die Enum-Erweiterung ist rein additiv ohne DDL/Migration.

**Alternatives considered**:
- *Bestehende `LAST_16` zweckentfremden*: verworfen — würde Achtel- und Sechzehntelfinale vermischen und Spielplan/Recap/Board verfälschen.
- *Nur im Bracket-Code ein Pseudo-Stage führen*: verworfen — die Spiele kämen weiter falsch als `GROUP_STAGE` in der DB an; der Fehler bliebe systemweit bestehen.

## R4 — Gewinner-Persistenz für Elfmeter/Verlängerung (`winner`-Spalte)

**Decision** (gemäß Klärung 2026-06-19): Additive, nullable Spalte `matches.winner TEXT` via neuem Changeset `013-add-matches-winner.sql`. Mapping auf Enum `MatchWinner { HOME_TEAM, AWAY_TEAM, DRAW }` (Spalte leer = unbekannt). `FootballDataClient` liest `score.winner`; `MatchRepository.upsert`/`updateLiveScore` persistieren den Wert additiv mit COALESCE-Guard (kein Überschreiben eines bekannten Werts durch transientes `null`).

**Rationale**: `fullTime.home/away` allein kann ein per Elfmeterschießen entschiedenes 1:1 nicht auflösen. `score.winner` (HOME_TEAM/AWAY_TEAM/DRAW) ist die maßgebliche football-data-Angabe. Die additive Spalte respektiert Verfassung Prinzip II (Liquibase) und FR-017 (nur additive Änderung).

**Alternatives considered**:
- *Nur Scores nutzen*: verworfen in der Klärung — durch Elfmeter entschiedene Spiele (inkl. Finale!) würden nie aufgelöst.
- *Effektiven Score post-Elfmeter speichern*: verworfen — verfälscht den angezeigten Spielstand und die Punktewertung.

## R5 — Gewinner-Ableitung zur Laufzeit (im Builder, nicht persistiert)

**Decision**: Reine Funktion `winnerOf(match)`:
1. Spiel nicht `FINISHED` → kein Gewinner (Folge-Slot bleibt Platzhalter).
2. `winner`-Spalte = `HOME_TEAM`/`AWAY_TEAM` → diese Seite gewinnt (deckt ET/Elfmeter ab).
3. sonst, falls `homeScore != awayScore` → höhere Seite gewinnt.
4. sonst (Remis-Score ohne `winner`-Info, oder `winner = DRAW`) → unentschieden ⇒ kein Nachrücken.

Für THIRD_PLACE rückt der **Verlierer** der jeweiligen Quell-Halbfinals nach (sobald diese entschieden sind).

**Rationale**: Erfüllt FR-010/011/012/013 und SC-003/004. Der Fortschritt wird ausschließlich in der Antwort berechnet (keine Persistenz des Baumzustands).

**Alternatives considered**:
- *Tordifferenz vor `winner`-Spalte priorisieren*: verworfen — bei ET ist `fullTime` ggf. bereits entscheidend, aber bei Elfmeter ist nur `winner` korrekt; `winner` zuerst zu prüfen ist die sichere Reihenfolge.

## R6 — Endpoint-, CORS- & Caching-Konventionen (Reuse F008)

**Decision**: Neue Methode `bracket()` in `PublicApiController` unter `GET /api/public/bracket`, Swagger-annotiert wie die übrigen Endpoints. CORS ist bereits über `PublicApiConfig.addCorsMappings("/api/public/**", GET/OPTIONS, no-credentials)` abgedeckt — keine Änderung nötig. Caching optional analog `schedule` (kurze TTL); Standard zunächst ohne Cache, da die Berechnung trivial ist und Frische bei laufenden Spielen erwünscht ist.

**Rationale**: Maximale Konsistenz mit Feature 008, minimale neue Oberfläche, keine sensiblen Felder (reine DTO-Mapper, kein direkter Entity-Serialisierungspfad).

**Alternatives considered**:
- *Eigener Controller/eigene CORS-Konfiguration*: verworfen — dupliziert bestehende Konventionen ohne Nutzen.

## R7 — DTO-Form & Round-Stage-Vokabular

**Decision**: Die DTO-`stage`-Werte folgen dem football-data-Vokabular der Spec (`LAST_32`, `LAST_16`, `QUARTER_FINALS`, `SEMI_FINALS`, `THIRD_PLACE`, `FINAL`) — ein stabiles Bracket-Vokabular, das `BracketTopology` aus der internen `Stage`-Enum ableitet (intern Singular → API-Plural). Jedes Spiel führt `fifaMatchNo`, optional `matchId` (reale football-data-Fixture, sofern vorhanden), zwei `BracketParticipantDto` (genau eines von `teamName`/`placeholder` gesetzt), `homeScore`/`awayScore`, `status`, optional `winner`, `sourceMatchNos[]` und `nextMatchNo`.

**Rationale**: Das Frontend zeichnet anhand stabiler Match-Nummern und Kanten; das einheitliche Stage-Vokabular entkoppelt die API von der internen Singular/Plural-Inkonsistenz. Platzhalter-vs-Team als sich ausschließende Felder verhindert mehrdeutige Anzeige (SC-005).

**Alternatives considered**:
- *Internen Enum-Namen ausliefern (`QUARTER_FINAL`)*: verworfen — widerspricht dem in der Spec genannten football-data-Vokabular und wäre für Konsumenten verwirrend.
- *Team und Platzhalter in einem Feld*: verworfen — erschwert dem Frontend die Unterscheidung „steht fest" vs. „noch offen".
