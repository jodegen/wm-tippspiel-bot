# Phase 0 Research: CHECK24-Punkteschema

Alle Punkte aus dem Spec/Clarify-Schritt sind aufgelöst; es verbleiben keine
NEEDS-CLARIFICATION-Marker. Die folgenden Entscheidungen fixieren die offenen
Designfragen aus dem User-Input des `/speckit-plan`-Aufrufs.

## D1 — Eine einzige Punkteberechnungs-Stelle (`scoreTip`)

- **Decision**: Die bestehende Methode `ScoringService.points(int homeActual, int awayActual, int homeTip, int awayTip)` wird auf 4/3/2/0 umgestellt und bleibt die **einzige** Stelle, an der Punkte berechnet werden. Sowohl `EvaluationService` (F5) als auch der neue `RecalculationService` rufen sie auf.
- **Rationale**: Erfüllt FR-011 (eine Quelle der Wahrheit) und Verfassung Prinzip III (zentral testbar). Die Signatur ist bereits im Einsatz (`EvaluationService`), keine Aufrufer-Änderung nötig.
- **Alternatives considered**: Separate Methode `scoreTip(Tip, Match)` mit Objektparametern — abgelehnt, weil sie die bestehende, getestete Signatur dupliziert und einen zweiten Einstieg schüfe (Risiko zweier Berechnungspfade). Stattdessen bleibt die int-Signatur die Kernfunktion; Aufrufer mappen `Tip`/`Match`-Felder darauf.

## D2 — Stufen-Reihenfolge und vorzeichenbehaftete Tordifferenz

- **Decision**: Prüfung strikt spezifisch→allgemein: (1) exakt `homeActual==homeTip && awayActual==awayTip` → **4**; (2) gleiche **vorzeichenbehaftete** Differenz `(homeTip-awayTip)==(homeActual-awayActual)` → **3**; (3) gleiche Tendenz `Integer.compare(homeTip,awayTip)==Integer.compare(homeActual,awayActual)` → **2**; (4) sonst **0**. Erste zutreffende Stufe gewinnt.
- **Rationale**: FR-001/FR-002/FR-003. Gleiche vorzeichenbehaftete Differenz impliziert gleiche Tendenz, daher ist die Reihenfolge konsistent und überschneidungsfrei. Gespiegelte Differenz (Tipp 0:2, Ergebnis 2:0) hat `-2 != +2` → fällt korrekt auf Tendenz/0 zurück.
- **Alternatives considered**: Differenz über Betrag `abs()` — abgelehnt, würde 0:2 bei 2:0 fälschlich als „richtige Differenz" werten (verletzt FR-003).

## D3 — Exakt-Statistik live per Score-Vergleich (kein neues Feld)

- **Decision**: Das `exact_hits`-Aggregat im Leaderboard-SQL wird von `COUNT(*) FILTER (WHERE points = 3)` auf einen JOIN-Vergleich umgestellt: `COUNT(*) FILTER (WHERE m.evaluated AND t.home_score = m.home_score AND t.away_score = m.away_score)`.
- **Rationale**: FR-006/FR-006a — Entkopplung vom Punktwert; bleibt korrekt, egal welcher Höchstpunktwert gilt. Kein persistiertes Exakt-Flag → keine Schema-Änderung (Prinzip II).
- **Alternatives considered**: (a) Persistiertes boolesches `exact`-Flag je Tipp — abgelehnt (Clarify-Entscheidung, neues Feld + Pflegeaufwand). (b) `FILTER (WHERE points = 4)` — abgelehnt, das ist genau die verbotene Ableitung aus dem Punktwert.

## D4 — Rückwirkende Neuberechnung als idempotenter Startup-Runner

- **Decision**: `RecalculationService.recalculateAll()` iteriert über alle evaluierten Spiele (`MatchRepository.findEvaluated()`), berechnet je Tipp `points()` neu und schreibt via `TipRepository.updatePoints(...)` **nur** bei Abweichung vom gespeicherten Wert. Aufgerufen durch `ScoreRecalculationRunner implements ApplicationRunner` beim App-Start, schaltbar über `app.scoring.recalc-on-startup` (Default `true`).
- **Rationale**: Clarify-Entscheidung (automatisch beim Start, keine UI). Idempotenz folgt daraus, dass `points()` deterministisch ist und nur bei echtem Unterschied geschrieben wird → zweiter Lauf = 0 Writes (FR-009, SC-004). `evaluated`-Guard erfüllt FR-010/SC-005. Liquibase ist beim Lauf eines ApplicationRunners bereits angewandt (Liquibase-Bean wird während des Context-Startups vor Runnern ausgeführt).
- **Alternatives considered**: (a) Liquibase-Changeset mit Java-/SQL-Migration — abgelehnt: Punkte-Backfill ist kein Schema-Wandel; eine Datenmigration in Liquibase würde die Punktelogik außerhalb von `ScoringService` duplizieren (verletzt FR-011) und ließe sich nicht so leicht mit JUnit testen. (b) Admin-Slash-Command — abgelehnt (Clarify: keine Bedienoberfläche). (c) Persistenter „bereits gelaufen"-Flag — unnötig, da der Lauf idempotent und billig ist (~104 Spiele).

## D5 — Sicherung der alten Punktstände vor dem Überschreiben

- **Decision**: Vor jedem Überschreiben wird die Änderung strukturiert geloggt: pro geändertem Tipp `user_id`, `match_id`, alter Punktwert, neuer Punktwert (INFO-Level), plus eine Zusammenfassung am Ende (`geprüft=N, geändert=M`). Kein neues Backup-Tabelle/Schema.
- **Rationale**: User-Vorgabe „Log oder Backup". Logging genügt zur Wiederherstellbarkeit und vermeidet eine Schema-Änderung (Prinzip II). Da nur Abweichungen geschrieben werden, ist das Log-Volumen gering und enthält genau das Audit-Delta.
- **Alternatives considered**: Backup-Tabelle `tips_points_backup` via Liquibase — abgelehnt für den Standardfall (Schema-Zuwachs für einen Einmalvorgang); kann bei Bedarf später additiv ergänzt werden. Optionaler CSV-Dump in konfigurierbaren Pfad als spätere Erweiterung notiert, nicht Teil dieses Plans.

## D6 — `/stats`-Command

- **Decision**: `/stats` (E5) existiert noch nicht und wird in diesem Feature **nicht** neu gebaut. Sollte es später entstehen, MUSS es die Exakt-Zählung ebenfalls per Score-Vergleich (D3), nicht über den Punktwert, bilden.
- **Rationale**: Scope-Begrenzung; FR-006 gilt für jede künftige Trefferquoten-Anzeige, aber der einzige heutige Konsument der Exakt-Statistik ist das Leaderboard.
- **Alternatives considered**: `/stats` mit umsetzen — abgelehnt (nicht im Spec-Scope; vermeidet Scope-Creep).
