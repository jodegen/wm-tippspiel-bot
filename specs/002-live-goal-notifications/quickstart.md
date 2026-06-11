# Quickstart: Live-Tor-Benachrichtigungen (F8)

Additive Erweiterung der bestehenden App — keine neuen Abhängigkeiten, keine
neuen Pflicht-Env-Variablen.

## Konfiguration (optional)

```
APP_JOBS_LIVE_GOAL_POLL_INTERVAL_MS=60000   # Poll-Intervall im Live-Fenster (Default 60s)
```
Voraussetzung wie bei den übrigen Notifications: `DISCORD_NOTIFY_ROLE_ID` und ein
erreichbarer Announce-Channel (Tor-Posts pingen die Rolle). Live-Stände kommen
über den bestehenden `FOOTBALL_DATA_API_KEY`.

## Migration

Beim Start fügt Liquibase die Spalten `notified_home`/`notified_away` (Default 0)
zur `matches`-Tabelle hinzu (Changeset 008). Kein manuelles DDL.

## Pflicht-/Kern-Tests

```bash
mvn -Dtest=GoalDetectorTest test            # Diff, Idempotenz, VAR, Mehrfach-Tore, Recovery
mvn -Dtest=ScoreDiffGoalEventSourceTest test # Fensterfilter + Persistenz des gemeldeten Standes
mvn verify                                   # alles inkl. Testcontainers (notified-score round-trip)
```

## Manueller Smoke-Test

1. Ein Spiel im Live-Fenster (`kickoff` ≤ jetzt ≤ `kickoff + 2,5 h`, Status
   SCHEDULED/IN_PLAY) mit steigendem Stand in der Quelle → nach ≤ ~1 Poll-Intervall
   erscheint „⚽ TOR! …" im Announce-Channel (mit Role-Ping).
2. Zwei Tore zwischen zwei Polls → zwei Tor-Posts.
3. Stand via VAR nach unten → „⛔ Tor aberkannt — jetzt …" (ohne Ping), kein TOR-Post.
4. Bot-Neustart bei gemeldetem Stand → kein erneuter Post; ein in der Downtime
   gefallenes Tor wird beim ersten Poll nachgereicht.
5. Spiel außerhalb des Fensters → keine Abfrage/keine Posts.

## Architektur-Eckpfeiler

- `GoalEventSource` ist die austauschbare Naht (Default: Score-Diff-Polling über
  den bestehenden `FootballDataClient`); Webhook/WebSocket später ohne Änderung
  an `GoalDetector`/`GoalNotifier`.
- `notified_*` (persistiert) trägt Idempotenz, VAR-Korrektur und Neustart-Recovery.
- Posting über die bestehende `AnnounceChannel`-Logik (Tor = mit Ping,
  Korrektur = ohne Ping).
- Zeit-/Fenstermath in UTC (`Clock`).
