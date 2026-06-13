# Quickstart — Dynamische Bot-Presence (F9)

## Was es tut
Der Bot-Status („Sieht sich … an") spiegelt zustandsgesteuert den WM-Kontext:
- **LIVE** `⚽ LIVE: GER 2:1 FRA` — sobald ein Spiel `IN_PLAY` ist
- **UPCOMING** `👀 Nächstes: GER vs FRA` — wenn kein Spiel läuft
- **IDLE** `🏆 WM 2026 /tipp` — statischer Fallback

Es gewinnt immer der höchstpriorisierte zutreffende Zustand (LIVE > UPCOMING > IDLE).

## Konfiguration
`application.yml` (alle optional, mit Defaults):
```yaml
app:
  presence:
    min-update-interval-ms: ${PRESENCE_MIN_UPDATE_INTERVAL_MS:5000}  # Throttle ≤4/20s
    idle-text: ${PRESENCE_IDLE_TEXT:🏆 WM 2026 /tipp}
```
Team-Kürzel pflegen in `src/main/resources/presence/team-codes.properties`:
```properties
Deutschland=GER
Frankreich=FRA
# ... 48 Teams
```
Fehlt ein Eintrag, nutzt der Bot den (gekürzten) Klartextnamen.

## Lokal verifizieren
1. Bot starten (`mvn spring-boot:run`, mit gültigem `DISCORD_TOKEN` etc.).
2. **IDLE**: ohne laufendes/künftiges Spiel zeigt der Bot-Status `🏆 WM 2026 /tipp`.
3. **UPCOMING**: ein künftiges Spiel in `matches` (Status `SCHEDULED`, `kickoff` in
   der Zukunft) → nach dem nächsten `boardRefresh` zeigt der Status
   `👀 Nächstes: <Heim> vs <Gast>`.
4. **LIVE**: ein Spiel auf `status = 'IN_PLAY'` mit Stand setzen → nach dem nächsten
   `liveGoalPoll`-Zyklus zeigt der Status `⚽ LIVE: <Heim> h:a <Gast>`. Stand erhöhen
   → der Status folgt (gedrosselt).
5. **Priorität**: läuft ein Spiel, ist nie UPCOMING/IDLE sichtbar.

## Tests
```bash
mvn -Dtest='Presence*Test,TeamCodeResolverTest' test
```
- `PresenceStateResolverTest` — Priorität, Multi-Live-Auswahl (zuletzt verändert +
  Tie-Breaker Anpfiff), Textformat/Emoji.
- `PresenceThrottleTest` — nie >5/20 s (Test-`Clock`), Coalescing „letzter gewinnt",
  kein Senden bei unverändertem Text.
- `TeamCodeResolverTest` — Mapping-Treffer + Fallback.
- `PresenceManagerTest` — `setActivity` nur bei tatsächlicher Textänderung.

## Was bewusst NICHT passiert
- Keine DB-Schema-Änderung (kein Liquibase-Changeset).
- Kein eigener Timer/Scheduler für F9 (nutzt `liveGoalPoll`/`boardRefresh`/JDA-Events).
- Keine zusätzlichen externen API-Aufrufe.
- Keine Uhrzeit im Presence-Text (nur Kürzel + Stand).
