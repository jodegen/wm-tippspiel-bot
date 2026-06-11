# Contract: Discord Slash-Commands & Interaktionen

Schnittstelle des Bots gegenüber Discord-Nutzern. Alle Zeiten werden in der
Anzeige als `Europe/Berlin` bzw. als Discord-Relative-Timestamp dargestellt
(Prinzip IV). Registrierung guild-scoped beim Start.

## Slash-Commands

### `/spielplan [anzahl]` — Spielplan-Übersicht (US6 / F1)

- **Optionen**: `anzahl` (Integer, optional, Default 5, min 1, max 25).
- **Sichtbarkeit**: öffentlich.
- **Verhalten**: Zeigt die nächsten N anstehenden Spiele (`kickoff > now()`,
  `status NOT IN (FINISHED, IN_PLAY, CANCELLED)`), sortiert nach `kickoff`.
- **Antwort (Embed je Spiel/Feld)**: Begegnung, Anstoßzeit (`<t:UNIX:F>`),
  TV-Sender (falls vorhanden), Quoten H/U/A (falls vorhanden).
- **FRs**: FR-029.

### `/naechstes` — Nächstes Spiel (US6 / F2)

- **Optionen**: keine.
- **Sichtbarkeit**: öffentlich.
- **Verhalten**: Genau das zeitlich nächste anstehende Spiel.
- **Antwort**: Begegnung, Countdown (`<t:UNIX:R>`), Anstoßzeit (`<t:UNIX:F>`),
  Sender, Quoten (falls vorhanden).
- **Leerfall**: Hinweis „Kein anstehendes Spiel".
- **FRs**: FR-030.

### `/tipp <spiel> <heim> <gast>` — Tipp abgeben (US1 / F3)

- **Optionen**:
  - `spiel` (String, **required**, **Autocomplete**) — nur tippbare Spiele
    (`kickoff > now()`, beide Teams bekannt, nicht abgesagt); Value = `match_id`.
  - `heim` (Integer, required, min 0) — getippte Heim-Tore.
  - `gast` (Integer, required, min 0) — getippte Gast-Tore.
- **Sichtbarkeit**: **ephemeral** (nur für den abgebenden User; FR-008).
- **Verhalten**: Upsert auf `(user_id, match_id)`; `username`/`created_at`
  aktualisiert. Ablehnung (ephemeral) wenn Spiel nicht (mehr) tippbar (FR-007).
- **Autocomplete-Antwort**: bis zu 25 Choices `"Heim vs Gast — <t-Berlin>"` →
  `match_id`.
- **FRs**: FR-005, FR-006, FR-007, FR-008, FR-009, FR-010.

### `/rangliste` — Leaderboard (US4 / F6)

- **Optionen**: keine.
- **Sichtbarkeit**: öffentlich.
- **Verhalten**: Aggregierte Rangliste (Gesamtpunkte ↓, exakte Treffer ↓,
  Gleichstand ⇒ geteilter Rang). Spalten: Rang, Name, Punkte, Tipps, exakte
  Treffer.
- **FRs**: FR-018, FR-019, FR-020.

## Komponenten-Interaktionen (Board-Navigation, US5 / F7)

### Navigationskomponente (`board:nav`)

- **Custom-IDs**: `board:filter` (StringSelectMenu) mit Optionen:
  `today`, `tomorrow`, `day:<YYYY-MM-DD>`, `group:<A..L>`, `ko` (K.o.-Runde).
- **Verhalten**: `deferReply(ephemeral=true)` innerhalb < 3 s, dann
  `editOriginal` mit gefilterter Ansicht. Öffentliches Board bleibt unverändert.
- **FRs**: FR-025, FR-026; SC-007.

## Automatisch gepostete Nachrichten (kein Command)

### Reveal-Post (US2 / F4)

- **Trigger**: `revealJob`, Spiel mit `kickoff <= now() AND revealed = false`.
- **Inhalt**: Embed mit allen abgegebenen Tipps des Spiels (Name → Tipp); Hinweis
  bei „keine Tipps". Kanal: Announce-Channel.
- **FRs**: FR-011, FR-012, FR-013.

### Auswertungs-Post (US3 / F5)

- **Trigger**: `evaluateJob`, Spiel `status = FINISHED AND evaluated = false`.
- **Inhalt**: Endstand + Punkteübersicht je Tipp (0/1/3).
- **Korrektur-Post (FR-017a)**: bei Neubewertung zusätzlicher Hinweis mit
  aktualisierten Punkten.
- **FRs**: FR-014, FR-015, FR-016, FR-017, FR-017a.

### Live-Board (US5 / F7)

- **Trigger**: `boardRefresh` nach jedem Sync.
- **Mechanik**: je Tages-Slot eine via `bot_messages` getrackte Nachricht;
  **Edit** statt Neu-Post; bei 404 (`UnknownMessage`) Neu-Post + Update der
  `message_id`. Slot = ein Embed; Aufteilung nach Tag hält Embed-Limits ein.
- **Inhalt je Spiel**: Begegnung, `<t:UNIX:R>`-Countdown, Sender, Quoten,
  Live-/Endstand (sobald vorhanden).
- **FRs**: FR-021, FR-022, FR-023, FR-024, FR-027; SC-006, SC-009.
