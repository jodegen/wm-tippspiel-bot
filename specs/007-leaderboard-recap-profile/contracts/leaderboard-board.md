# Contract — Live-Leaderboard-Board (F11)

## Tracked Message

| Eigenschaft | Wert |
|---|---|
| Slot-Key (`bot_messages.key`) | `board:leaderboard` |
| Channel | `app.discord.leaderboard-channel-id` (eigener, read-only Channel) |
| Mechanik | Edit-statt-Post über `TrackedBoardPublisher.editOrPost(channel, "board:leaderboard", embed)` |
| Recovery | Edit-404 (`UNKNOWN_MESSAGE`) → Neu-Post + `bot_messages`-Update |
| Cleanup | beim Start: `cleanupOrphans(channel, keepKey="board:leaderboard")` — nur eigene verwaiste Bot-Nachrichten |

## Trigger & Ablauf (`LeaderboardBoardService.refreshAfterEvaluation()`)

Aufgerufen von `EvaluateJob` **nach** dem Auswertungs-Batch, wenn ≥1 Spiel ausgewertet wurde:

1. `current = TipRepository.leaderboard()` (sortiert `total_points DESC, exact_hits DESC`).
2. `previous = LeaderboardSnapshotRepository.findAllRanks()`.
3. Pro User `RankDelta` berechnen (siehe Pfeil-Regeln).
4. `LeaderboardBoardEmbed.build(top-N, deltas)` → `editOrPost(...)`.
5. `LeaderboardSnapshotRepository.replaceAll(currentRanks)` (alle User, nicht nur Top-N).

Zusätzlich: Initial-Build beim Start (`ApplicationReadyEvent`) analog F7, ohne Snapshot-Neuschreiben, wenn keine Auswertung lief.

## Embed-Inhalt (`LeaderboardBoardEmbed`)

- `EmbedStyle.base("Rangliste")`, Footer mit Update-Zeitstempel (Europe/Berlin).
- Kompakte `description`, eine Zeile je User: `#Rang  Name — Punkte Pkt · X exakt  <Pfeil>`.
- Hart auf Top-N (`app.leaderboard.top-n`, Default 15) **und** defensive Zeichen-Obergrenze (~4000) begrenzt.

## Pfeil-Regeln (`RankDelta.arrow`)

| Bedingung | Anzeige |
|---|---|
| `previousRank == null` | `NEU` |
| `current < previous` | `↑(previous − current)` |
| `current > previous` | `↓(current − previous)` |
| `current == previous` | `–` |

## Akzeptanz

- **AS-1**: kein Board vorhanden → genau ein Board unter `board:leaderboard` (FR-001).
- **AS-2**: Punkteänderung → dieselbe Nachricht editiert, kein Neu-Post (FR-002, FR-003, SC-002).
- **AS-3**: Top-N sortiert mit Rang/Name/Punkten/exakten Treffern (FR-004, FR-005).
- **AS-4**: Auf-/Abstieg seit letztem Batch korrekt als Pfeil (FR-006).
- **AS-5**: manuell gelöschtes Board → Neu-Post + `bot_messages`-Update (FR-009).
- **AS-6**: verwaiste eigene Board-Nachrichten beim Start entfernt; fremde unangetastet (FR-010).
- **Invarianten**: Rang-Diff korrekt auch nach Neustart (FR-007, SC-003); Embed-Limits nie überschritten (FR-008, SC-004).
