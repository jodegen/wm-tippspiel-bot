# Contract: Rückwirkende Neuberechnung (Startup-Runner)

Einmaliger, idempotenter Neuberechnungsmechanismus, der beim App-Start alle
bereits ausgewerteten Tipps nach dem neuen Schema neu berechnet (FR-008/008a/009/010).

## Komponenten

```java
// com.example.wmtippspiel.evaluation.RecalculationService
RecalcSummary recalculateAll();   // iteriert evaluierte Spiele, schreibt nur bei Abweichung

// com.example.wmtippspiel.recalc.ScoreRecalculationRunner  (implements ApplicationRunner)
//   @ConditionalOnProperty("app.scoring.recalc-on-startup", matchIfMissing = true)
//   run(args) -> recalculationService.recalculateAll()
```

Neue Repository-Methode:

```java
// MatchRepository
List<Match> findEvaluated();   // SELECT * FROM matches WHERE evaluated = TRUE
```

Wiederverwendet (unverändert):

```java
TipRepository.findByMatch(long matchId)
TipRepository.updatePoints(String userId, long matchId, int points)
ScoringService.points(int homeActual, int awayActual, int homeTip, int awayTip)
```

## Ablauf `recalculateAll()`

1. `matches = MatchRepository.findEvaluated()`.
2. Für jedes Match: `tips = TipRepository.findByMatch(match.id)`.
3. Für jeden Tip: `neu = points(match.homeScore, match.awayScore, tip.homeScore, tip.awayScore)`.
4. Wenn `neu != tip.points`: alten/neuen Wert loggen (`user_id`, `match_id`, alt→neu) und `updatePoints(tip.userId, match.id, neu)`.
5. Wenn `neu == tip.points`: **kein** Write (Idempotenz).
6. Abschluss-Log: `RecalcSummary{ matchesScanned, tipsScanned, tipsChanged }`.

## Kontraktbedingungen / Invarianten

- **Idempotenz (FR-009, SC-004)**: Ein zweiter Lauf direkt nach dem ersten ergibt `tipsChanged == 0` und führt 0 `updatePoints`-Aufrufe aus.
- **Evaluated-Guard (FR-010, SC-005)**: Spiele mit `evaluated = false` werden nicht geladen und nicht verändert.
- **Backup (User-Vorgabe)**: Jede Änderung wird vor dem Überschreiben mit altem und neuem Wert geloggt (INFO).
- **Eine Berechnungsquelle (FR-011)**: ausschließlich `ScoringService.points(...)` — keine eigene Punktelogik im Service/Runner.
- **Reihenfolge zum Startup**: Läuft als `ApplicationRunner` nach abgeschlossener Liquibase-Migration und Context-Initialisierung.
- **Schaltbar**: `app.scoring.recalc-on-startup` (Default `true`) erlaubt das Deaktivieren im Betrieb.

## Testbedingungen (RecalculationServiceTest, Mockito)

| Szenario | Erwartung |
|----------|-----------|
| Evaluiertes Spiel, gespeicherter Altwert weicht ab (z. B. 3 → 4) | `updatePoints(..., 4)` genau einmal; tipsChanged erhöht |
| Evaluiertes Spiel, Wert bereits korrekt nach neuem Schema | kein `updatePoints`-Aufruf (Idempotenz) |
| Zwei aufeinanderfolgende Läufe | zweiter Lauf: 0 Writes |
| `findEvaluated()` liefert keine nicht-evaluierten Spiele | nicht-evaluierte Tipps werden nie angefasst |
| Alt 1 (Tendenz) bei zusätzlich richtiger Differenz | → 3; Alt 1 ohne richtige Differenz → 2 |
