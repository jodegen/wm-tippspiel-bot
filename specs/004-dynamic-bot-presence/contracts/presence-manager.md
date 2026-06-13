# Contracts — Dynamische Bot-Presence (F9)

F9 stellt **keine** externen Schnittstellen (Slash-Command, REST, Component)
bereit — die Presence ist passive, server-getriebene Anzeige. Die „Kontrakte"
hier sind die **internen Komponenten-Verträge** und Invarianten, gegen die getestet
wird.

## `PresenceStateResolver` (rein, ohne Seiteneffekte)

```java
PresenceState resolve(
        List<LiveMatchView> inPlay,      // laufende Spiele inkl. Stand + kickoff + lastChange
        Optional<Match> nextUpcoming,    // findUpcoming(now,1)
        String idleText);                // konfigurierter IDLE-Fallback
```

**Invarianten / Verhalten**
- C1 (Priorität, FR-002): `inPlay` nicht leer ⇒ `LIVE`; sonst `nextUpcoming`
  vorhanden ⇒ `UPCOMING`; sonst ⇒ `IDLE`. Nie ein niedrigerer Zustand, wenn ein
  höherer zutrifft.
- C2 (Auswahl, FR-013): bei mehreren `inPlay` gewinnt der Eintrag mit maximalem
  `lastChange`; Tie-Breaker frühester `kickoff`.
- C3 (Text, FR-003/005/007/010): exakt
  - LIVE: `⚽ LIVE: {codeHome} {h}:{a} {codeAway}`
  - UPCOMING: `👀 Nächstes: {codeHome} vs {codeAway}`
  - IDLE: `{idleText}` (Default `🏆 WM 2026 /tipp`)
  Nur Standard-Unicode-Emojis; Text defensiv längenbegrenzt.

## `TeamCodeResolver` (rein)

```java
String code(String teamName);   // "Deutschland" -> "GER"
```
- C4: Treffer in der Mapping-Ressource ⇒ Kürzel. Kein Treffer ⇒ defensiv
  gekürzter Klartextname (nie `null`, nie überlang).

## `PresenceThrottle` (rein, `Clock`-basiert)

```java
// liefert den jetzt zu sendenden Text oder leer (= jetzt nichts senden);
// plant intern ggf. einen verzögerten Flush
Optional<String> submit(String desiredText, Instant now);
Optional<String> flush(Instant now);   // vom verzögerten Task aufgerufen
```

**Invarianten**
- C5 (Idempotenz, FR-008): `desiredText == lastSentText` ⇒ nie senden.
- C6 (Rate-Limit-Garantie, FR-009): zwischen zwei tatsächlich gesendeten Texten
  liegen **≥ `MIN_INTERVAL`** (Default 5000 ms). Daraus folgt höchstens 4 Updates
  pro beliebigem 20-s-Fenster — strikt unter dem Discord-Limit (5/20 s), 60-s-
  Backoff wird nie ausgelöst.
- C7 (Coalescing, „letzter Zustand gewinnt"): treffen während der Sperrzeit mehrere
  `submit` ein, wird nur der **letzte** Text beim Flush gesendet; Zwischenstände
  werden verworfen.

## `PresenceManager` (@Component, Orchestrierung)

```java
void recompute();                 // thread-safe; aus jedem Trigger aufrufbar
// JDA-Listener (ListenerAdapter):
void onReady(ReadyEvent e);            // initiales Setzen
void onReconnected(...); void onSessionRecreate(...);  // FR-011
```

**Verhalten**
- C8: `recompute()` liest `findInPlay()` + ggf. `findUpcoming(now,1)`, aktualisiert
  die `ObservedLiveMatch`-Map, ruft `PresenceStateResolver.resolve(...)` und reicht
  den Text durch `PresenceThrottle` an `jda.getPresence().setActivity(Activity.watching(text))`.
- C9 (Thread-Safety): Aufrufe aus `liveGoalPoll`-Thread, `boardRefresh`-Thread,
  JDA-Event-Thread und dem Throttle-Flush-Executor werden serialisiert (Lock); kein
  Lost-Update auf `lastSentText`/`pending`.
- C10 (Reconnect-fest): nach `onReady`/`onReconnected`/`onSessionRecreate` wird die
  Presence aus dem aktuellen Zustand neu gesetzt (nie leer/veraltet).

## Trigger-Kontrakte (bestehende Komponenten, additiv)

- T1 `LiveGoalPollJob.postLiveGoals()`: ruft am **Ende** `presenceManager.recompute()`
  (nach dem Goal-Posting). Bestehendes Goal-Verhalten unverändert.
- T2 `BoardRefreshJob.refreshBoard()`: ruft nach `boardService.refresh()`
  `presenceManager.recompute()`. Board-Verhalten unverändert.
- T3 `ScoreDiffGoalEventSource`: persistiert für Spiele im Live-Fenster den frischen
  Stand+Status via `MatchRepository.updateLiveScore(...)` (vorhandene Spalten).
  Goal-Erkennung/`notified_*`-Buchführung unverändert.
