# Contract: Leaderboard-Query (Exakt-Treffer entkoppelt vom Punktwert)

`TipRepository.leaderboard()` liefert `List<LeaderboardEntry>`. Die Spalte
`exact_hits` DARF NICHT mehr aus `points` abgeleitet werden (FR-006/FR-006a).

## Vorher (zu ersetzen)

```sql
SELECT user_id,
       MAX(username) AS username,
       COALESCE(SUM(points), 0) AS total_points,
       COUNT(*) AS tip_count,
       COUNT(*) FILTER (WHERE points = 3) AS exact_hits   -- ← aus Punktwert abgeleitet (verboten)
FROM tips
GROUP BY user_id
ORDER BY total_points DESC, exact_hits DESC
```

## Nachher (Vergleich Tipp ↔ tatsächliches Ergebnis)

```sql
SELECT t.user_id,
       MAX(t.username) AS username,
       COALESCE(SUM(t.points), 0) AS total_points,
       COUNT(*) AS tip_count,
       COUNT(*) FILTER (
         WHERE m.evaluated
           AND t.home_score = m.home_score
           AND t.away_score = m.away_score
       ) AS exact_hits
FROM tips t
JOIN matches m ON m.id = t.match_id
GROUP BY t.user_id
ORDER BY total_points DESC, exact_hits DESC
```

## Kontraktbedingungen

- `exact_hits` zählt nur Tipps zu Spielen mit `m.evaluated = TRUE` (gültiges Endergebnis vorhanden).
- `total_points` = Summe der nach neuem Schema vergebenen Punkte.
- `tip_count` = Anzahl abgegebener Tipps des Users (unverändert; INNER JOIN ist unschädlich, da jeder Tipp ein `match_id` referenziert).
- Sortierung unverändert: `total_points DESC, exact_hits DESC` (Tie-Breaker, FR-007).
- Rückgabeobjekt `LeaderboardEntry(userId, username, totalPoints, tipCount, exactHits)` bleibt strukturell unverändert.

## Eigenschaft

Der gemeldete `exact_hits`-Wert ist invariant gegenüber Änderungen des
Punkteschemas (SC-002): Eine hypothetische Verschiebung des Höchstpunktwerts
verändert die Exakt-Zählung nicht, da sie ausschließlich auf dem Score-Vergleich beruht.
