# Feature Specification: Live-Tor-Benachrichtigungen (F8)

**Feature Branch**: `002-live-goal-notifications`

**Created**: 2026-06-11

**Status**: Draft

**Input**: User description: "Implementiere das neue Feature F8 — Live-Tor-Benachrichtigungen aus wm-tippspiel-bot-spec.md. Spezifiziere nur F8: zentrales Score-Diff-Polling nur im Live-Fenster (kickoff bis +2.5h), idempotenter Goal-Detector mit Neustart-Recovery und VAR-Korrektur-Behandlung, Discord-Posting des GoalEvents, GoalEventSource als austauschbares Interface. Nutze das bestehende Datenmodell und die für F8 ergänzten Felder."

## Kontext

Die WM-Tippspiel-Anwendung (F1–F7) steht bereits: Spielplan-Sync, Tippabgabe,
Reveal, Auswertung, Rangliste, Live-Board und die Benachrichtigungen über die
WM-Notify-Rolle. Diese Spezifikation ergänzt **ausschließlich F8** — die
Live-Tor-Benachrichtigung während laufender Spiele. Sie nutzt das bestehende
Datenmodell inklusive der bereits in `wm-tippspiel-bot-spec.md` ergänzten Felder
`notified_home`/`notified_away` der `matches`-Tabelle.

## Clarifications

### Session 2026-06-11

- Q: Sollen Live-Tor-Posts die WM-Notify-Rolle anpingen? → A: Ja — wie Reveal/Auswertung/Anpfiff-Hinweis (Role-Ping bei jedem Tor-Post).
- Q: Umgang mit VAR-Korrektur nach unten? → A: Kurze Korrektur-Notiz im Channel (z. B. „⛔ Tor aberkannt — jetzt 0:0") und gemeldeten Stand nach unten korrigieren; kein „TOR!"-Post.
- Q: Tore während einer Bot-Downtime (Neustart mitten im Spiel)? → A: Nachmelden — verpasste, noch nicht gemeldete Tore werden nach dem Neustart als einzelne Posts nachgereicht.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Live-Tor-Benachrichtigung erhalten (Priority: P1)

Ein Community-Mitglied verfolgt das Tippspiel und möchte ohne eigene App/Abfrage
sofort mitbekommen, wenn in einem laufenden Spiel ein Tor fällt — als kurze
Nachricht im Announce-Channel.

**Why this priority**: Das ist der Kern und einzige sichtbare Mehrwert von F8;
ohne den zeitnahen Tor-Post hat das Feature keinen Nutzen.

**Independent Test**: Ein laufendes Spiel wird auf einen neuen, höheren
Spielstand gebracht; kurze Zeit später erscheint genau ein Tor-Post im
Announce-Channel mit dem korrekten neuen Stand und Torschützen-Team.

**Acceptance Scenarios**:

1. **Given** ein laufendes Spiel steht 0:0, **When** der Stand auf 1:0 wechselt,
   **Then** erscheint im Announce-Channel **eine** Nachricht im Stil
   „⚽ TOR! Deutschland 1:0 Curaçao" (mit Spielminute, falls verfügbar).
2. **Given** dasselbe Spiel steht inzwischen 1:0, **When** der Stand auf 2:1
   wechselt (zwei zusätzliche Tore seit dem letzten gemeldeten Stand), **Then**
   erscheinen **zwei** Tor-Posts (je ein Post pro zusätzlichem Tor).
3. **Given** ein Spiel außerhalb seines Live-Fensters (noch nicht angepfiffen
   oder länger als 2,5 h nach Anpfiff), **When** das System läuft, **Then**
   werden für dieses Spiel **keine** Spielstände abgefragt und keine Tor-Posts
   erzeugt.
4. **Given** der gleiche Spielstand wird erneut abgefragt, **When** sich nichts
   geändert hat, **Then** wird **keine** weitere Benachrichtigung erzeugt.

---

### User Story 2 - Keine falschen oder doppelten Tor-Posts (Priority: P2)

Mitglieder sollen dem Bot vertrauen können: keine Geister-Tore und keine
Dopplungen — auch bei VAR-Entscheidungen oder mehrfach gelieferten Updates.

**Why this priority**: Falsche/doppelte Tor-Pings untergraben das Vertrauen und
sind im Live-Geschehen besonders störend; Korrektheit ist direkt nach der
Grundfunktion am wichtigsten.

**Independent Test**: Ein laufendes Spiel steht 1:0; der Stand wird (VAR) wieder
auf 0:0 korrigiert und später erneut auf 1:0 gesetzt — es entstehen keine
Fehl-Posts bei der Rücknahme und genau ein Post beim erneuten Tor.

**Acceptance Scenarios**:

1. **Given** ein gemeldeter Stand von 1:0, **When** der Stand (aberkanntes Tor)
   auf 0:0 zurückgeht, **Then** wird **keine** Tor-Benachrichtigung ausgelöst und
   der gemerkte Stand konsistent auf 0:0 korrigiert.
2. **Given** der Stand wurde via VAR auf 0:0 zurückgesetzt, **When** danach
   regulär ein Tor zum 1:0 fällt, **Then** erscheint **genau ein** Tor-Post.
3. **Given** dieselbe Spielstands-Aktualisierung wird doppelt geliefert/abgefragt,
   **When** der Detektor sie verarbeitet, **Then** entsteht **kein** zweiter Post.

---

### User Story 3 - Verlässlich über Bot-Neustarts hinweg (Priority: P3)

Auch wenn der Bot mitten in einem Spiel neu startet, soll kein bereits
gemeldetes Tor erneut gepostet werden.

**Why this priority**: Betriebsrealität (Deploys/Neustarts); ohne Persistenz des
gemeldeten Standes käme es sonst zu einer Flut alter Tor-Posts.

**Independent Test**: Bei gemeldetem Stand 2:1 wird der Bot neu gestartet;
nach dem Neustart und der nächsten Abfrage entstehen für die bereits gemeldeten
Tore **keine** erneuten Posts.

**Acceptance Scenarios**:

1. **Given** ein gemeldeter Stand von 2:1 vor dem Neustart, **When** der Bot neu
   startet und erneut abfragt (Stand weiterhin 2:1), **Then** wird **keine**
   Benachrichtigung erzeugt.
2. **Given** ein neues Tor zum 3:1 fällt nach dem Neustart, **When** abgefragt
   wird, **Then** erscheint **genau ein** Tor-Post für das 3:1.
3. **Given** der gemeldete Stand war 1:0 und während der Downtime fiel das 2:0,
   **When** der Bot neu startet und abfragt (Stand jetzt 2:0), **Then** wird das
   verpasste Tor zum 2:0 **nachgereicht** (ein Post).

---

### Edge Cases

- **Mehrere Tore zwischen zwei Abfragen**: Die Differenz wird in einzelne
  Tor-Ereignisse aufgelöst (ein Post pro zusätzlichem Tor), nicht als ein
  Sammel-Update.
- **VAR-/Korrektur nach unten**: Stand sinkt wieder → kein „TOR!"-Post, aber eine
  kurze Korrektur-Notiz; gemerkter Stand wird nach unten korrigiert.
- **Spielminute fehlt/ungenau**: Falls die Datenquelle keine verlässliche Minute
  liefert, wird der Post ohne Minute (nur mit Stand) ausgegeben.
- **Datenquelle verzögert/lückenhaft**: Tore werden erkannt, sobald der Stand
  sich gegenüber dem gemerkten Stand ändert; zeitliche Genauigkeit hängt vom
  Abfrageintervall und der Frische der Quelle ab.
- **Spiel endet / verlässt das Fenster**: Nach `kickoff + 2.5h` bzw. bei
  Spielende wird nicht mehr abgefragt; ein evtl. allerletztes Tor knapp vor
  Fensterende wird nur erfasst, wenn es noch in eine Abfrage fällt.
- **Abgesagtes/verschobenes Spiel**: Außerhalb von Status/Fenster → keine Abfrage.

## Requirements *(mandatory)*

### Functional Requirements

**Live-Polling-Fenster**

- **FR-001**: Das System MUSS Spielstände laufender Spiele nur innerhalb des
  Zeitfensters von `kickoff` bis `kickoff + 2,5 h` abfragen, und nur für Spiele
  mit Status „angepfiffen/laufend" bzw. „geplant" im Fenster.
- **FR-002**: Außerhalb dieses Fensters DARF das System keine Live-Spielstände
  abfragen (Schonung des Abruf-Limits der externen Quelle).
- **FR-003**: Das Abfrageintervall im Fenster MUSS konfigurierbar sein
  (Standard 60 Sekunden).
- **FR-004**: Das Live-Polling MUSS so dimensioniert sein, dass das Abruf-Limit
  der externen Quelle (10 Anfragen/Minute) nicht überschritten wird.

**Tor-Erkennung (Goal-Detector)**

- **FR-005**: Das System MUSS je Spiel den zuletzt **gemeldeten** Spielstand
  vorhalten — getrennt vom tatsächlichen aktuellen Spielstand.
- **FR-006**: Steigt der Spielstand gegenüber dem gemeldeten Stand, MUSS das
  System **je zusätzlichem Tor** ein Tor-Ereignis erzeugen (Team, Stand,
  Spielminute falls verfügbar) und den gemeldeten Stand anschließend
  aktualisieren. Der angegebene Stand je Ereignis ist der **laufende Stand nach
  diesem Tor** (bei Mehrfach-Toren inkrementell); die Reihenfolge zwischen
  Heim-/Auswärtstoren und die Minute sind dabei best-effort.
- **FR-007**: Die Erkennung MUSS **idempotent** sein: ein erneut abgefragter oder
  doppelt gelieferter, unveränderter Stand DARF keine weitere Benachrichtigung
  auslösen.
- **FR-008**: Sinkt der Spielstand (z. B. aberkanntes Tor per VAR), MUSS das
  System den gemeldeten Stand nach unten korrigieren und DARF **keinen**
  „TOR!"-Post auslösen; stattdessen MUSS es eine **kurze Korrektur-Notiz** in
  den Announce-Channel posten (z. B. „⛔ Tor aberkannt — jetzt {neuer Stand}").
  Die Korrektur-Notiz pingt die WM-Notify-Rolle **nicht** (reine Information).
- **FR-009**: Der gemeldete Stand MUSS persistent gehalten werden, sodass ein
  Neustart mitten im Spiel kein bereits gemeldetes Tor erneut meldet.
- **FR-009a**: Tore, die während einer Bot-Downtime gefallen und noch **nicht
  gemeldet** wurden, MÜSSEN nach dem Neustart nachgereicht werden — als einzelne
  Tor-Posts (je verpasstem Tor ein Post).

**Benachrichtigung (Discord-Posting)**

- **FR-010**: Das System MUSS je erkanntem Tor-Ereignis eine Nachricht in den
  Announce-Channel posten (Begegnung, neuer Stand, Spielminute falls verfügbar)
  und dabei die **WM-Notify-Rolle anpingen** (analog zu den übrigen
  Announce-Posts).
- **FR-011**: Die Posting-Funktion MUSS unabhängig von der Herkunft des
  Tor-Ereignisses funktionieren (gleiche Ausgabe, egal aus welcher Quelle das
  Ereignis stammt).

**Austauschbare Event-Quelle**

- **FR-012**: Die Quelle der Tor-Ereignisse MUSS hinter einer einheitlichen
  Abstraktion gekapselt sein, sodass die aktuelle Umsetzung (Score-Diff-Polling)
  später gegen eine andere Bezugsart (z. B. Push/Webhook/laufende Verbindung)
  ausgetauscht werden kann, **ohne** dass Tor-Erkennung oder Discord-Posting
  geändert werden müssen.
- **FR-013**: Das Polling/die Datenbeschaffung MUSS zentral im Backend liegen;
  Mitglieder erhalten ausschließlich Push (den Channel-Post) und führen keine
  eigene Abfrage durch.

**Robustheit**

- **FR-014**: Fallen mehrere Tore zwischen zwei Abfragen, MUSS das System die
  Differenz als mehrere Einzel-Tor-Ereignisse auflösen.
- **FR-015**: Fehler/Verzögerungen der externen Quelle DÜRFEN den Betrieb nicht
  stören; eine fehlgeschlagene Abfrage wird übersprungen, ohne Fehl-Posts.

### Key Entities *(include if feature involves data)*

- **Spiel (matches)** — bestehende Entität, für F8 genutzt: tatsächlicher Stand
  (`home_score`/`away_score`, per Sync aktualisiert) sowie der zuletzt
  **gemeldete** Stand (`notified_home`/`notified_away`) als Basis der
  Tor-Erkennung. Anpfiffzeit und Status bestimmen das Live-Fenster.
- **Tor-Ereignis (GoalEvent)** — flüchtiges Ereignis (keine eigene Tabelle
  zwingend): Spielbezug, welches Team getroffen hat, neuer Stand, Spielminute
  (optional). Wird von der Event-Quelle erzeugt und an das Discord-Posting
  übergeben.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Ein Tor wird nach Aktualisierung des Standes in der Datenquelle
  innerhalb von höchstens einem Abfrageintervall + kurzer Verarbeitungszeit
  (Standard: ≤ ~90 Sekunden) im Announce-Channel gepostet.
- **SC-002**: Für jedes tatsächlich gefallene (und in der Quelle reflektierte)
  Tor erscheint **genau ein** Post — keine Dopplungen, auch nicht bei erneuter
  Abfrage oder Neustart.
- **SC-003**: Bei einer Stand-Korrektur nach unten (VAR) entsteht **null**
  fälschliche Tor-Benachrichtigung.
- **SC-004**: Fallen N zusätzliche Tore zwischen zwei Abfragen, erscheinen genau
  N Tor-Posts.
- **SC-005**: Außerhalb des Live-Fensters erfolgt **keine** Spielstand-Abfrage
  (verifizierbar: keine Abrufe für nicht-laufende Spiele).
- **SC-006**: Nach einem Bot-Neustart mit gemeldetem Stand wird **kein** bereits
  gemeldetes Tor erneut gepostet.

## Assumptions

- **Bestehende Architektur**: F1–F7 inkl. Announce-Channel, WM-Notify-Rolle und
  Spielplan-Sync sind vorhanden und werden wiederverwendet. F8 ergänzt nur die
  Live-Tor-Erkennung und -Benachrichtigung.
- **Felder vorhanden**: `notified_home`/`notified_away` an `matches` (laut
  ergänztem Datenmodell) dienen als „zuletzt gemeldeter Stand".
- **VAR-Rücknahme** (geklärt): kurze Korrektur-Notiz im Announce-Channel + nach
  unten korrigierter gemeldeter Stand; kein „TOR!"-Post (FR-008).
- **Downtime-Verhalten** (geklärt): während einer Downtime gefallene, noch nicht
  gemeldete Tore werden nach dem Neustart als einzelne Posts **nachgereicht**
  (FR-009a).
- **Role-Ping** (geklärt): Tor-Posts pingen die WM-Notify-Rolle wie die übrigen
  Announce-Posts (FR-010).
- **Datenquelle**: Live-Stände stammen aus derselben externen Quelle wie der
  reguläre Sync; deren Frische bei Live-Spielen ist nicht garantiert und begrenzt
  die erreichbare Echtzeitnähe.
