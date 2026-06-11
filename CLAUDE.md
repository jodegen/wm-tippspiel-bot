<!-- SPECKIT START -->
For additional context about technologies to be used, project structure,
shell commands, and other important information, read the current plan:
`specs/001-wm-tippspiel-bot/plan.md`

Active feature: **001-wm-tippspiel-bot** (WM 2026 Tippspiel Discord-Bot).
Stack: Java 21, Spring Boot 3.x, JDA (dauerhafte Gateway-Verbindung), PostgreSQL
via `JdbcClient`, Liquibase (ein Changeset pro Tabelle), `WebClient` für
football-data.org & The Odds API. Zeit: UTC speichern, Europe/Berlin anzeigen.
Kernlogik (Punktewertung 3/1/0, Reveal-/Eval-Timing) ist test-pflichtig
(Verfassung Prinzip III). Siehe auch `research.md`, `data-model.md`,
`contracts/`, `quickstart.md` im Feature-Ordner.
<!-- SPECKIT END -->
