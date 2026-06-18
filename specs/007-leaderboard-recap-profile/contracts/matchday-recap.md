# Contract — Spieltags-Rückblick (F12)

## Auslöser & Vollständigkeit

- Aufgerufen von `EvaluateJob` nach dem Auswertungs-Batch: `MatchdayRecapService.postCompletedRecaps()`.
- **Recap-Key** je Spiel: `matchday != null → "md:<matchday>"`, sonst `"stage:<STAGE>"`.
- Ein Recap-Key gilt als **abgeschlossen**, wenn **alle** `matches` mit diesem Key `status = FINISHED AND evaluated = TRUE` sind (FR-013).
- Geprüft werden nur die Recap-Keys der im aktuellen Batch ausgewerteten Spiele (kein Vollscan nötig).

## Idempotenz

- Vor dem Posten: `MatchdayRecapRepository.tryClaim(recapKey)` → `INSERT ... ON CONFLICT (recap_key) DO NOTHING`.
- Nur bei tatsächlichem Insert (rowsAffected = 1) wird gepostet (FR-014, FR-016, SC-005).
- Re-Evaluation eines Ergebnisses entfernt den Claim **nicht** → kein zweiter Post (FR-018).

## Posting

| Eigenschaft | Wert |
|---|---|
| Channel | `app.discord.announce-channel-id` |
| Embed | `MatchdayRecapEmbed` über `EmbedStyle.base("Spieltags-Rückblick · <Bezeichnung>")` |

Inhalt (Aggregation rein DB-lesend über `tips`/`matches` dieses Recap-Keys):

- **Top-Punktesammler des Spieltags** — Summe der `points` nur aus Spielen dieses Keys (`TipRepository.matchdayLeaderboard(recapKey)`).
- **Bester Einzeltipp** — primär exakter Treffer (4 Pkt); fehlt ein solcher, der Tipp mit der höchsten `points`-Zahl; Tie-Break unwahrscheinlichstes Ergebnis (Quote).
- **Leer ausgegangen** — User mit 0 Punkten an diesem Spieltag.

## Akzeptanz

- **AS-1**: unvollständiger Spieltag → kein Post (FR-013).
- **AS-2**: vollständig → genau ein Post in Announce-Channel (FR-014, SC-006).
- **AS-3**: erneuter Job-Lauf/Neustart → kein zweiter Post (FR-016, SC-005).
- **AS-4**: Inhalt = Top-Sammler + bester Tipp + Nuller (FR-015).
- **AS-5**: keine Tipps am Spieltag → sauberer Leerfall, kein Fehler (FR-017).
