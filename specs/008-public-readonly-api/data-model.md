# Phase 1 — Data Model: Öffentliche Read-only-API

**Keine DB-Schema-Änderung.** Dieses Feature führt keine Tabellen/Spalten ein
(Verfassung Prinzip II bleibt unberührt). Modelliert werden ausschließlich
**Response-DTOs** (Lese-Projektionen) und die Ableitung des öffentlichen
Identifiers. Quelle der Daten sind die bestehenden Tabellen `matches`, `tips`,
`leaderboard_snapshot` (nur lesend).

## Quell-Entities (Bestand, read-only)

| Entity | Tabelle | Genutzte Felder |
|--------|---------|-----------------|
| `Match` | `matches` | id, home, away, kickoff (UTC), stage, group_label, channel, odds_home/draw/away, home_score, away_score, status, revealed, matchday |
| `Tip` | `tips` | user_id *(intern, NIE serialisiert)*, match_id, username, home_score, away_score, points |
| Rang-Snapshot | `leaderboard_snapshot` | user_id, rank *(nur lesend über `findAllRanks()`)* |

## Öffentlicher Identifier (abgeleitet, nicht persistiert)

- `publicId := Base64Url(HMAC-SHA256(key=app.public-api.id-secret, msg=user_id))`,
  stabil gekürzt (z. B. 16–22 Zeichen).
- Eigenschaften: deterministisch, stabil über Umbenennungen, nicht
  zurückrechenbar ohne Secret. Auflösung `publicId → user_id` per Enumeration der
  Teilnehmer (siehe research.md R2).

## Response-DTOs

Alle DTOs sind Java-`record`s im Paket `…publicapi.dto`. **Kein DTO besitzt ein
Feld für `user_id`, E-Mail, Tokens oder interne IDs.** Zeitpunkte werden als
UTC-ISO-8601 serialisiert (FR-005).

### MatchDto (Spielplan-Zeile)

| Feld | Typ | Quelle / Hinweis |
|------|-----|------------------|
| matchId | long | `matches.id` (öffentliche Fixture-Referenz, R4) |
| home / away | String | Begegnung |
| kickoffUtc | Instant (ISO-8601 UTC) | `matches.kickoff` |
| stage | String | Turnierphase (`Stage`) |
| group | String \| null | `group_label` |
| tvChannel | String \| null | `channel` |
| oddsHome / oddsDraw / oddsAway | BigDecimal \| null | Quote |
| homeScore / awayScore | Integer \| null | Ergebnis (null vor/ohne Stand) |
| status | String | `MatchStatus` (SCHEDULED/IN_PLAY/FINISHED/POSTPONED/CANCELLED) |
| matchday | Integer \| null | Spieltag |

### LiveMatchDto (laufendes Spiel)

Teilmenge von `MatchDto` für `status = IN_PLAY`: matchId, home, away, kickoffUtc,
homeScore, awayScore, status (+ optional matchday/group). Aktueller Zwischenstand
aus `home_score/away_score` (von F9 live gepflegt).

### LeaderboardRowDto

| Feld | Typ | Quelle |
|------|-----|--------|
| rank | int | `LeaderboardRanking.compute` |
| displayName | String | `LeaderboardEntry.username` |
| points | int | `LeaderboardEntry.totalPoints` |
| exactHits | int | `LeaderboardEntry.exactHits` (direkter Ergebnisvergleich, FR-010) |
| rankChange | String | `RankDelta.symbol()` — `NEU` / `↑n` / `↓n` / `–` |
| publicId | String | `PublicIdService.publicId(userId)` — selbe HMAC-Ableitung wie `/players/{publicId}` (Verlinkung aufs Profil) |

### MatchTipsDto (reveal-gegated)

| Feld | Typ | Hinweis |
|------|-----|---------|
| matchId | long | Fixture-Referenz |
| released | boolean | `true` nur wenn `now()≥kickoff` UND `revealed` (R3) |
| tips | List\<PublicTipDto\> | LEER solange `released=false` |

### PublicTipDto

| Feld | Typ | Hinweis |
|------|-----|---------|
| displayName | String | `tips.username` |
| tipHome / tipAway | int | getipptes Ergebnis |
| points | Integer \| null | nur bei bereits gewertetem Spiel (sonst null) |

### ProfileDto

| Feld | Typ | Quelle |
|------|-----|--------|
| publicId | String | HMAC (R2) |
| displayName | String | aktueller Anzeigename |
| rank | Integer \| null | gerankte Liste (null wenn nicht in Wertung) |
| points | int | `UserProfile.totalPoints` |
| exactHits | int | `UserProfile.exactHits` |
| evaluatedTips | int | Anzahl gewerteter Tipps |
| hitRatePercent | Integer \| null | `null` bei 0 Tipps (keine Division durch null, FR-018) |
| distribution | PointDistributionDto | Verteilung 4/3/2/0 |
| bestTip / worstTip | ProfileTipDto \| null | aus `ProfileStats` |
| history | List\<ProfileTipDto\> | nur **evaluated** Tipps ⇒ Reveal-Regel automatisch gewahrt (FR-017) |

### ProfileTipDto

home, away, tipHome, tipAway, resultHome (Integer\|null), resultAway
(Integer\|null), points.

### PointDistributionDto

`p4`, `p3`, `p2`, `p0` (int) — aus `UserProfile`.

## Validierungs-/Verhaltensregeln (aus Requirements)

- **Reveal-Gate** (FR-012/013, R3): Vor Anpfiff KEINE `PublicTipDto` im Baum.
- **Kein Leak** (FR-003, SC-001): DTO-Struktur ohne sensible Felder + JSON-
  Verbotslistentest.
- **Leere Mengen** (FR-020): leere Listen statt Fehler.
- **Unbekannte Referenz** (FR-019): `matchId`/`publicId` unbekannt ⇒ HTTP 404
  ohne interne Details.
- **Read-only** (FR-002/SC-006): nur GET; nicht-GET ⇒ 405.

## Additiv benötigte Repository-Methode (read-only)

- `MatchRepository.findAll()` — alle Spiele (z. B. exkl. `CANCELLED`), nach
  `kickoff` sortiert, als Basis für den vollständigen Spielplan und die
  In-Memory-Filterung. Reine `SELECT`-Erweiterung, kein Schreibpfad.
