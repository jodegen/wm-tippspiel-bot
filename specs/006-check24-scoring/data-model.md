# Phase 1 Data Model: CHECK24-Punkteschema

**Keine Schema-Änderung.** Es werden ausschließlich bestehende Tabellen/Spalten
genutzt. Dieses Dokument beschreibt die für das Feature relevanten Felder und die
neue Bewertungssemantik.

## Tabelle `tips` (bestehend)

| Spalte | Typ | Rolle in diesem Feature |
|--------|-----|--------------------------|
| `user_id` | TEXT | Gruppierung im Leaderboard |
| `match_id` | BIGINT (FK → matches.id) | Verknüpfung Tipp ↔ Spiel für Score-Vergleich |
| `username` | TEXT | Anzeige im Leaderboard |
| `home_score` / `away_score` | INT | **Getippte** Tore — Eingabe für `points()` und Exakt-Vergleich |
| `points` | INT (default 0) | **Ergebnis** der Bewertung; trägt nun 0/2/3/4 statt 0/1/3. Wird von F5 gesetzt und vom Neuberechnungs-Runner überschrieben |
| `created_at` | TIMESTAMPTZ | unverändert |

## Tabelle `matches` (bestehend)

| Spalte | Typ | Rolle in diesem Feature |
|--------|-----|--------------------------|
| `id` | BIGINT | Schlüssel |
| `home_score` / `away_score` | INT | **Tatsächliches** Ergebnis — Eingabe für `points()` und Exakt-Vergleich |
| `status` | TEXT | `FINISHED` als Vorbedingung der Auswertung |
| `evaluated` | BOOLEAN | Guard: nur evaluierte Spiele werden neu berechnet / in Exakt-Statistik gezählt |

## Logische Größen (nicht persistiert)

### Punktstufe (Bewertungsergebnis)

Abbildung `(homeTip, awayTip, homeActual, awayActual) → {4,3,2,0}`:

| Stufe | Bedingung (in dieser Reihenfolge geprüft) | Punkte |
|-------|--------------------------------------------|--------|
| Exakt | `homeTip==homeActual && awayTip==awayActual` | 4 |
| Tordifferenz | `(homeTip-awayTip) == (homeActual-awayActual)` (vorzeichenbehaftet) und nicht exakt | 3 |
| Tendenz | `sign(homeTip-awayTip) == sign(homeActual-awayActual)` und keine der obigen | 2 |
| Daneben | sonst | 0 |

`sign(x) = Integer.compare(x, 0)` → {-1 Auswärtssieg-Tendenz, 0 Remis, +1 Heimsieg-Tendenz}.

### Exakt-Treffer (Statistik)

Boolesch je (Tipp, Spiel): `evaluated AND tip.home_score == match.home_score AND tip.away_score == match.away_score`.
Im Leaderboard als `COUNT(*) FILTER (...)` aggregiert — **unabhängig von `points`**.

### LeaderboardEntry (bestehendes Modell, unverändert)

`userId, username, totalPoints (SUM points), tipCount (COUNT tips), exactHits (neue, vom Punktwert entkoppelte Zählung)`.
Sortierung: `totalPoints DESC, exactHits DESC` (Tie-Breaker).

## Validierungs-/Invarianten

- INV-1: `points ∈ {0,2,3,4}` nach Auswertung/Neuberechnung (3/1/0-Altwerte nur vor dem Neuberechnungslauf).
- INV-2: Stufen sind disjunkt durch feste Prüfreihenfolge — kein Tipp kann zwei Stufen gleichzeitig „gewinnen".
- INV-3: Gleiche vorzeichenbehaftete Differenz ⇒ gleiche Tendenz (mathematisch); daher kann Stufe „Tordifferenz" nie eine falsche Tendenz belohnen.
- INV-4: Neuberechnung verändert `points` nur, wenn der neu berechnete Wert vom gespeicherten abweicht (Idempotenz).
- INV-5: Spiele mit `evaluated = false` werden weder neu berechnet noch in `exactHits` gezählt.
