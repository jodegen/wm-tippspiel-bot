# Contract: F8 Live-Tor-Komponenten

Interne Kontrakte (kein externer API-Endpoint). Definiert die stabile Naht
zwischen Event-Quelle, Erkennung und Posting.

## `GoalEventSource` (austauschbare Quelle — FR-012)

```text
interface GoalEventSource:
    List<GoalEvent> fetchEvents()
        # Liefert alle aktuell fälligen Tor-/Korrektur-Ereignisse.
        # Default: Score-Diff-Polling. Später: Webhook/WebSocket -> gleiche Signatur.
        # Konsumenten (LiveGoalPollJob, GoalNotifier) hängen NUR an diesem Interface
        # bzw. an GoalEvent — nicht an der konkreten Quelle.
```

**Default-Implementierung `ScoreDiffGoalEventSource`**:
- Holt frische Stände via bestehendem `FootballDataClient.fetchMatches()`.
- Filtert Live-Fenster: `kickoff <= now <= kickoff + 2,5 h` und Status SCHEDULED/IN_PLAY.
- Pro Spiel: `getNotifiedScore` → `GoalDetector.detect(...)` → Events sammeln →
  `updateNotifiedScore(current)`.
- Fehler der Quelle → leere Liste (Poll übersprungen), keine Fehl-Posts (FR-015).

## `GoalDetector` (reine Logik)

```text
List<GoalEvent> detect(int notifiedHome, int notifiedAway,
                       int currentHome, int currentAway, Match match)
    # currentSumme == notifiedSumme & gleich       -> []   (idempotent)
    # Anstieg (kein Wert kleiner)                   -> N GOAL-Events (je Tor eines)
    # mind. ein Wert kleiner (VAR)                  -> [CORRECTION]
    # setzt selbst nichts; Persistenz macht der Aufrufer
```

Eigenschaften (testbar): idempotent, Mehrfach-Tore → mehrere Events,
Abwärtskorrektur → genau ein CORRECTION ohne GOAL, Recovery via persistiertem
`notified_*` (Differenz nach Neustart).

## `GoalNotifier` (Posting, herkunftsunabhängig — FR-010/011)

```text
void post(GoalEvent event)
    # GOAL       -> AnnounceChannel.post(goalEmbed)      (MIT Role-Ping)
    #               "⚽ TOR! {home} {newHome}:{newAway} {away}" (+ Minute, falls vorhanden)
    # CORRECTION -> AnnounceChannel.postPlain(corrEmbed) (OHNE Role-Ping)
    #               "⛔ Tor aberkannt — jetzt {newHome}:{newAway}"
```

## `LiveGoalPollJob` (Scheduler)

```text
@Scheduled(fixedDelayString = "${app.jobs.live-goal-poll-interval-ms:60000}")
postLiveGoals():
    for event in goalEventSource.fetchEvents():
        goalNotifier.post(event)
    # Nur im Live-Fenster entstehen überhaupt Events (Filter in der Quelle).
```

## Erweiterung `AnnounceChannel` (additiv)

```text
post(MessageEmbed)        # bestehend: pingt ggf. die Notify-Rolle
postPlain(MessageEmbed)   # NEU: postet ohne Role-Ping (für Korrektur-Notiz)
postUserPing(String)      # bestehend (Tipp-Erinnerung)
```
