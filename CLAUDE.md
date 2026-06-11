<!-- SPECKIT START -->
For additional context about technologies to be used, project structure,
shell commands, and other important information, read the current plan:
`specs/002-live-goal-notifications/plan.md` (aktuelles Feature F8).
Basis-Feature/Architektur: `specs/001-wm-tippspiel-bot/plan.md`.

Aktuelles Feature: **002-live-goal-notifications** (F8 — Live-Tor-Benachrichtigungen).
Additive Erweiterung: `GoalEventSource` (austauschbar; Default Score-Diff-Polling
über bestehenden `FootballDataClient`), reiner `GoalDetector` (Idempotenz, VAR,
Recovery via persistiertem `notified_*`), `GoalNotifier` über bestehende
`AnnounceChannel`-Logik, `@Scheduled liveGoalPoll` nur im Live-Fenster
(kickoff…+2,5h). Neue Spalten `notified_home/away` via Liquibase-Changeset 008.

Active feature: **001-wm-tippspiel-bot** (WM 2026 Tippspiel Discord-Bot).
Stack: Java 21, Spring Boot 3.x, JDA (dauerhafte Gateway-Verbindung), PostgreSQL
via `JdbcClient`, Liquibase (ein Changeset pro Tabelle), `WebClient` für
football-data.org & The Odds API. Zeit: UTC speichern, Europe/Berlin anzeigen.
Kernlogik (Punktewertung 3/1/0, Reveal-/Eval-Timing) ist test-pflichtig
(Verfassung Prinzip III). Siehe auch `research.md`, `data-model.md`,
`contracts/`, `quickstart.md` im Feature-Ordner.
<!-- SPECKIT END -->
