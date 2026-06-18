# Contract — `/profil [user]` Slash-Command (F13)

## Command-Definition

| Eigenschaft | Wert |
|---|---|
| Name | `profil` |
| Beschreibung | „Zeigt die Tippspiel-Bilanz eines Nutzers." |
| Option | `user` — Typ `USER`, **optional**. Ohne Angabe: aufrufender User. |
| Sichtbarkeit der Antwort | **öffentlich** (nicht ephemeral) — FR-023 |
| Registrierung | `DiscordCommandRegistrar.registerCommands()` + Dispatch in `InteractionListener.onSlashCommandInteraction()` (`case ProfilCommand.NAME`) |

## Eingabe → Auflösung

- `event.getOption("user")` vorhanden → Ziel = dieser User; sonst `event.getUser()`.
- Antwort-Strategie: `event.deferReply().queue()` (öffentlich), danach `event.getHook().editOriginalEmbeds(embed)` — hält das 3s-Interaktionsfenster ein.

## Ausgabe-Embed (`ProfilEmbed`)

Stil über `EmbedStyle.base("Profil · <Anzeigename>")`. Inhalt:

| Block | Quelle |
|---|---|
| Rang | Position in `TipRepository.leaderboard()` (Standard Competition Ranking) |
| Gesamtpunkte | `LeaderboardEntry.totalPoints` |
| Exakte Treffer | `LeaderboardEntry.exactHits` (direkter Tipp-Ergebnis-Vergleich) |
| Trefferquote | `exactHits / evaluatedTipCount` als Prozent; bei 0 ausgewerteten Tipps: „—" |
| Bester Tipp | höchste `points`; Tie-Break unwahrscheinlichstes Ergebnis (Quote) |
| Schlechtester Tipp | niedrigste `points` unter ausgewerteten Tipps |
| Verteilung | Häufigkeit von 4 / 3 / 2 / 0 Punkten |

## Verhalten / Akzeptanz

- **AS-1**: `/profil` ohne Argument → eigene vollständige Bilanz (FR-019, FR-020).
- **AS-2**: `/profil @user` → Bilanz des genannten Users (FR-019).
- **AS-3**: Antwort ist öffentlich im Channel sichtbar (FR-023).
- **AS-4**: User ohne ausgewertete Tipps → gültige leere Bilanz (Punkte 0, Quote „—", keine besten/schlechtesten Tipps), **kein** Fehler (FR-024, FR-022).
- **Invarianten**: Werte für Gesamtpunkte/exakte Treffer sind identisch zu F11/F12 für denselben Zeitpunkt (FR-025, SC-008). Keine Punkte-Neuberechnung (FR-026).
