# Contract: Punkteberechnung (`ScoringService.points`)

**Einzige** Stelle der Punkteberechnung (FR-011). Genutzt von `EvaluationService`
(F5) und `RecalculationService` (rückwirkend).

## Signatur

```java
// com.example.wmtippspiel.domain.scoring.ScoringService
int points(int homeActual, int awayActual, int homeTip, int awayTip)
```

## Verhalten (Reihenfolge spezifisch → allgemein, erste zutreffende gewinnt)

1. **4** — exakt: `homeTip == homeActual && awayTip == awayActual`
2. **3** — richtige vorzeichenbehaftete Tordifferenz, nicht exakt: `(homeTip - awayTip) == (homeActual - awayActual)`
3. **2** — richtige Tendenz: `Integer.compare(homeTip, awayTip) == Integer.compare(homeActual, awayActual)`
4. **0** — sonst

## Referenz-Testmatrix (für ScoringServiceTest — test-first, Prinzip III)

| Ergebnis (H:A) | Tipp (H:A) | Erwartet | Stufe |
|----------------|------------|----------|-------|
| 2:1 | 2:1 | 4 | exakt |
| 0:0 | 0:0 | 4 | exakt (Remis) |
| 4:1 | 3:0 | 3 | Differenz (+3) |
| 1:1 | 2:2 | 3 | Differenz (0, Remis falsche Höhe) |
| 3:3 | 0:0 | 3 | Differenz (0, Remis falsche Höhe) |
| 2:0 | 1:0 | 2 | Tendenz Heimsieg, Differenz ≠ |
| 3:1 | 1:0 | 2 | Tendenz Heimsieg, Differenz ≠ |
| 1:2 | 0:3 | 2 | Tendenz Auswärtssieg, Differenz ≠ |
| 2:0 | 0:2 | 0 | gespiegelte Differenz ⇒ falsche Tendenz |
| 2:2 | 1:2 | 0 | Remis vs. Auswärtssieg ⇒ falsche Tendenz |
| 1:0 | 0:1 | 0 | falsche Tendenz |

## Invarianten

- Rückgabe ∈ {0,2,3,4}.
- Deterministisch (gleiche Eingabe → gleicher Wert) — Voraussetzung für idempotente Neuberechnung.
- Keine Phasen-/Gewichtungsfaktoren (FR-004): Eingabe sind ausschließlich die vier Torzahlen.
