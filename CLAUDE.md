<!-- SPECKIT START -->
For additional context about technologies to be used, project structure,
shell commands, and other important information, read the current plan:
`specs/003-consolidated-board/plan.md` (aktuelles Feature F7-Redesign).
Vorheriges Feature: `specs/002-live-goal-notifications/plan.md` (F8).
Basis-Feature/Architektur: `specs/001-wm-tippspiel-bot/plan.md`.

Aktuelles Feature: **003-consolidated-board** (F7-Redesign — konsolidiertes Board).
Modifikation der bestehenden F7-Implementierung: `bot_messages` auf einen Slot
`board:main` reduziert (Alt-Slots `board:day:*`/`board:nav` via Liquibase-Changeset
009 migriert/entfernt). Ein konsolidiertes Embed mit den nächsten 12 anstehenden
Spielen (`MatchRepository.findUpcoming(now, 12)`) als Liste in der description,
defensiv tronkiert (<4096 desc / <6000 gesamt). Start-Cleanup löscht verwaiste
eigene Bot-Nachrichten (Author = Self, nicht `board:main`) in den letzten 100
Nachrichten. Gemeinsamer Styling-Helper `EmbedStyle` für Info- & Board-Embed
(Akzentfarbe, Author-Header, Footer+Timestamp). Filter-Komponente unverändert,
hängt an `board:main`. `boardRefresh`-Job-Trigger unverändert.

Active feature: **001-wm-tippspiel-bot** (WM 2026 Tippspiel Discord-Bot).
Stack: Java 21, Spring Boot 3.x, JDA (dauerhafte Gateway-Verbindung), PostgreSQL
via `JdbcClient`, Liquibase (ein Changeset pro Tabelle), `WebClient` für
football-data.org & The Odds API. Zeit: UTC speichern, Europe/Berlin anzeigen.
Kernlogik (Punktewertung 3/1/0, Reveal-/Eval-Timing) ist test-pflichtig
(Verfassung Prinzip III). Siehe auch `research.md`, `data-model.md`,
`contracts/`, `quickstart.md` im Feature-Ordner.
<!-- SPECKIT END -->
