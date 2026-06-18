# Quickstart: CHECK24-Punkteschema umsetzen

Voraussetzung: Branch `006-check24-scoring`, Maven, Java 21. Reihenfolge folgt
Verfassung Prinzip III (Test-First für Punktewertung).

## 1. Tests zuerst (rot)

- `ScoringServiceTest`: CSV/Parameter auf 4/3/2/0 umstellen anhand der Matrix in
  [contracts/scoring.md](./contracts/scoring.md) (inkl. Remis-Sonderfälle 2:2/1:1→3,
  0:0/0:0→4 und gespiegelte Differenz 0:2/2:0→0).
- `EvaluationServiceTest`: erwartete Punkte auf neues Schema anpassen.
- `RecalculationServiceTest` (neu): Update-bei-Abweichung, Idempotenz (zweiter
  Lauf = 0 Writes), `evaluated`-Guard — siehe [contracts/recalculation.md](./contracts/recalculation.md).

```bash
./mvnw test -Dtest=ScoringServiceTest,EvaluationServiceTest,RecalculationServiceTest
# erwartet: rot (Logik noch nicht angepasst / Klassen fehlen)
```

## 2. Implementierung (grün)

1. `ScoringService.points(...)`: 4/3/2/0 in der Reihenfolge exakt → Differenz →
   Tendenz → 0 (Contract scoring.md).
2. `MatchRepository.findEvaluated()`: `SELECT * FROM matches WHERE evaluated = TRUE`.
3. `RecalculationService.recalculateAll()`: iterieren, `points()` neu anwenden,
   nur bei Abweichung loggen + `updatePoints(...)`; `RecalcSummary` zurückgeben.
4. `ScoreRecalculationRunner implements ApplicationRunner` mit
   `@ConditionalOnProperty(value="app.scoring.recalc-on-startup", matchIfMissing=true)`.
5. `application.yml`: `app.scoring.recalc-on-startup: true`.
6. `TipRepository.leaderboard()`-SQL auf JOIN-Vergleich umstellen (Contract leaderboard.md).

```bash
./mvnw test    # erwartet: grün
```

## 3. Dokumentation nachziehen (FR-012)

- `wm-tippspiel-bot-spec.md` Abschnitt **F5** (Punkteschema 4/3/2/0) und **F6**
  (Exakt-Treffer per Vergleich) aktualisieren.

## 4. Verifikation gegen Success Criteria

| Check | Erfüllt SC |
|-------|-----------|
| Scoring-Testmatrix vollständig grün | SC-001 |
| Leaderboard `exact_hits` per Score-Vergleich, stimmt mit manueller Zählung | SC-002 |
| Nach erstem Start: alle Altwerte entsprechen `points()` | SC-003 |
| Zweiter Start: `tipsChanged == 0` in der Recalc-Zusammenfassung (Log) | SC-004 |
| Nicht-evaluierte Spiele unverändert | SC-005 |

## 5. Manuelle End-to-End-Prüfung (optional)

1. DB mit Alt-Punkten (3/1/0) auf evaluierten Spielen bereitstellen.
2. App starten → Recalc-Log prüfen (`geprüft=N, geändert=M`, Einzeländerungen alt→neu).
3. `/rangliste` aufrufen → Gesamtpunkte und exakte Treffer plausibilisieren.
4. App erneut starten → Recalc meldet `geändert=0` (Idempotenz).
