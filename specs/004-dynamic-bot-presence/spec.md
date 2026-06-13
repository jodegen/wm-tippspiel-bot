# Feature Specification: F9 — Dynamische Bot-Presence

**Feature Branch**: `004-dynamic-bot-presence`

**Created**: 2026-06-13

**Status**: Draft

## Clarifications

### Session 2026-06-13

- Q: Bei mehreren zeitgleich laufenden Spielen — welches Spiel bestimmt den LIVE-Presence-Text? → A: Das **zuletzt veränderte** Spiel (in dem zuletzt ein Tor fiel) gewinnt — maximal event-getrieben, passt zur F8-Anbindung; die Presence folgt der Action.
- Q: Was löst die Presence-Neuberechnung für LIVE-Eintritt (Anpfiff, 0:0) und LIVE-Austritt (Abpfiff) aus, da F8 nur bei Toren feuert? → A: Nach **jedem `liveGoalPoll`-Zyklus** (läuft bereits im Live-Fenster) **und nach `boardRefresh`** wird der Zustand neu bewertet — kein eigener Timer.
- Q: Woher kommen die kompakten Team-Kürzel (z. B. „GER", „FRA")? → A: Aus einer **statisch gepflegten FIFA-Code-Mapping-Tabelle** (analog zum manuellen TV-Sender-Mapping); fehlt ein Kürzel, wird defensiv der gekürzte Klartextname verwendet.
- Q: Wodurch gilt ein Spiel als LIVE — API-Status oder Anpfiffzeit? → A: Über den **API-Status `IN_PLAY`** (dann liegt auch ein Live-Stand vor); bei API-Verzug bleibt die Presence kurz im UPCOMING-Zustand und korrigiert sich selbst.

**Input**: User description: "Füge ein neues Feature F9 — Dynamische Bot-Presence zu wm-tippspiel-bot-spec.md hinzu. Die übrige Anwendung steht bereits; spezifiziere nur F9. Die Bot-Activity spiegelt zustandsgesteuert (NICHT zeitbasiert rotierend) den aktuellen WM-Kontext wider. Drei priorisierte Zustände: (1) LIVE — zeigt den Live-Stand, z.B. \"⚽ LIVE: GER 2:1 FRA\", event-getrieben aktualisiert über den bestehenden F8-Goal-Detector (kein eigener Timer); (2) UPCOMING — zeigt das nächste Spiel, z.B. \"👀 Nächstes: GER vs FRA\", aktualisiert beim boardRefresh bzw. höchstens stündlich; (3) IDLE — statischer Fallback, z.B. \"🏆 WM 2026 /tipp\". Es gewinnt immer der höchstpriorisierte zutreffende Zustand; die Activity wird nur gesetzt, wenn sich der Anzeigetext tatsächlich geändert hat. Discord-Limit: Presence darf nur 5x pro 20 Sekunden geändert werden, sonst 60s-Backoff — ein Throttling muss das garantiert verhindern. Nur Standard-Unicode-Emojis, Activity-Typ watching."

## Überblick

Der Bot soll auf einen Blick — über seine Discord-Presence (Status-Zeile „sieht
sich X an") — den aktuellen WM-Kontext widerspiegeln. Die Anzeige ist
**zustandsgesteuert**, nicht zeitbasiert rotierend: Es gibt drei priorisierte
Zustände, und der höchstpriorisierte zutreffende Zustand bestimmt jederzeit den
Anzeigetext. Die Presence ist passive, server-getriebene Information — kein
Command, keine Nutzerinteraktion.

Dieses Spec beschreibt **ausschließlich F9**. Die übrigen Features (F1–F8,
Datenmodell, Hintergrund-Jobs) stehen bereits und werden nur als Abhängigkeiten
referenziert (insbesondere der F8-Goal-Detector und der `boardRefresh`-Job).

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Live-Stand in der Bot-Presence (Priority: P1)

Während ein WM-Spiel läuft, sehen alle Mitglieder am Bot-Status auf einen Blick
den aktuellen Spielstand, z. B. „sieht sich an: ⚽ LIVE: GER 2:1 FRA". Der Stand
aktualisiert sich, sobald ein Tor fällt — getrieben vom bestehenden
F8-Goal-Detector, ohne eigenen Timer.

**Why this priority**: Der Live-Zustand liefert den höchsten unmittelbaren
Mehrwert und ist der Daseinsgrund der dynamischen Presence: Mitglieder erkennen
ohne Klick, dass gerade gespielt wird und wie es steht. Er hat die höchste
Priorität unter den drei Zuständen.

**Independent Test**: Ein Spiel wird auf Status „laufend" mit einem Stand
gesetzt; die Bot-Presence zeigt den Live-Stand. Bei einem erkannten Tor
(F8-Ereignis) ändert sich die Presence auf den neuen Stand. Ein erneut
geliefertes/identisches Ereignis löst **keine** Presence-Änderung aus.

**Acceptance Scenarios**:

1. **Given** mindestens ein Spiel mit Status „laufend", **When** der
   Presence-Zustand bestimmt wird, **Then** zeigt die Presence den LIVE-Text mit
   aktuellem Stand (Activity-Typ „watching", führendes Standard-Emoji).
2. **Given** ein laufendes Spiel mit Stand `1:0` wird angezeigt, **When** der
   F8-Goal-Detector ein Tor zu `2:0` meldet, **Then** wechselt die Presence auf
   den neuen Stand — ohne einen eigenen Polling-Timer in F9.
3. **Given** der LIVE-Text ist bereits gesetzt, **When** dasselbe Tor-Ereignis
   erneut/idempotent geliefert wird (gleicher Anzeigetext), **Then** wird **kein**
   erneutes Presence-Update an Discord gesendet.
4. **Given** ein Tor wird per VAR aberkannt (Stand sinkt), **When** F8 den
   korrigierten Stand meldet, **Then** zeigt die Presence den korrigierten Stand
   und es entsteht keine fehlerhafte Anzeige.

---

### User Story 2 - Nächstes Spiel in der Bot-Presence (Priority: P2)

Wenn gerade kein Spiel läuft, zeigt der Bot-Status das nächste anstehende Spiel,
z. B. „sieht sich an: 👀 Nächstes: GER vs FRA". Die Anzeige wird beim
`boardRefresh` aktualisiert, höchstens jedoch stündlich.

**Why this priority**: Auch außerhalb laufender Spiele bleibt der Bot
informativ und weckt Vorfreude auf die nächste Begegnung. Der Zustand ist
nachrangig zu LIVE, aber wichtiger als der statische Fallback.

**Independent Test**: Es läuft kein Spiel, es existiert ein künftiges Spiel; die
Presence zeigt das nächste Spiel. Wird der nächste Gegner-/Spiel-Wechsel beim
`boardRefresh` wirksam, ändert sich die Presence entsprechend — aber nicht
häufiger als stündlich.

**Acceptance Scenarios**:

1. **Given** kein Spiel mit Status „laufend" und mindestens ein künftiges Spiel,
   **When** der Presence-Zustand bestimmt wird, **Then** zeigt die Presence den
   UPCOMING-Text mit der nächsten Begegnung.
2. **Given** der UPCOMING-Zustand ist aktiv, **When** `boardRefresh` läuft und
   das nächste Spiel unverändert ist, **Then** wird **kein** erneutes
   Presence-Update gesendet (Text unverändert).
3. **Given** ein laufendes Spiel beendet sich und kein weiteres Spiel läuft,
   **When** der Zustand neu bestimmt wird, **Then** wechselt die Presence von
   LIVE auf UPCOMING (bzw. IDLE, falls kein künftiges Spiel existiert).

---

### User Story 3 - Statischer Fallback (IDLE) (Priority: P3)

Wenn weder ein Spiel läuft noch ein künftiges Spiel ansteht (z. B. vor
Turnierbeginn oder nach dem Finale), zeigt der Bot einen statischen Fallback,
z. B. „sieht sich an: 🏆 WM 2026 /tipp".

**Why this priority**: Stellt sicher, dass die Presence nie leer oder veraltet
ist und immer einen sinnvollen, markenkonformen Default zeigt. Niedrigste
Priorität, da reiner Rückfall.

**Independent Test**: Es läuft kein Spiel und es existiert kein künftiges Spiel;
die Presence zeigt den statischen IDLE-Text.

**Acceptance Scenarios**:

1. **Given** kein laufendes und kein künftiges Spiel, **When** der Zustand
   bestimmt wird, **Then** zeigt die Presence den statischen IDLE-Fallback.
2. **Given** der IDLE-Text ist bereits gesetzt, **When** der Zustand erneut
   bestimmt wird und weiterhin IDLE gilt, **Then** wird **kein** erneutes
   Presence-Update gesendet.

---

### Edge Cases

- **Mehrere gleichzeitig laufende Spiele** (in der WM-Endrunde stoßen die
  letzten beiden Gruppenspiele zeitgleich an): Das **zuletzt veränderte** Spiel
  (in dem zuletzt ein Tor fiel) bestimmt den LIVE-Text (siehe FR-013). Bei
  Gleichstand ohne jüngstes Tor-Ereignis dient der frühere Anpfiff als
  stabiler Tie-Breaker.
- **Tor-Burst / mehrere Tore in kurzer Folge**: Fallen in kurzer Zeit mehr als
  fünf relevante Änderungen an (mehrere Spiele, schnelle Tore), muss die
  Drossel die Updates so zusammenfassen/verzögern, dass das Discord-Limit (5
  Änderungen / 20 s) garantiert nicht überschritten wird — am Ende steht der
  jeweils aktuellste Anzeigetext.
- **Bot-Neustart / Gateway-Reconnect mitten im Spiel**: Nach (Wieder-)Verbindung
  muss die Presence aus dem aktuellen Zustand neu gesetzt werden, damit sie nicht
  leer oder veraltet bleibt.
- **Übergang LIVE → UPCOMING → IDLE**: Nach Spielende wird der Zustand neu
  bewertet; existiert kein laufendes Spiel mehr, greift der nächstpriorisierte
  zutreffende Zustand.
- **Sehr lange Team-/Anzeigenamen**: Der Anzeigetext muss innerhalb der von
  Discord für Activity-Namen erlaubten Länge (**max. 128 Zeichen**) bleiben
  (defensiv kürzen, z. B. via kurzer Team-Kürzel wie „GER"/„FRA").
- **Identischer Anzeigetext trotz Zustandswechsel**: Liefert ein anderer Zustand
  zufällig denselben Text, wird trotzdem kein Update gesendet (Vergleich erfolgt
  rein auf dem Anzeigetext).

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Der Bot MUSS seine Discord-Presence als Activity vom Typ
  „watching" setzen.
- **FR-002**: Die Presence MUSS aus genau einem von drei priorisierten Zuständen
  abgeleitet werden — **LIVE > UPCOMING > IDLE**. Es gewinnt jederzeit der
  höchstpriorisierte **zutreffende** Zustand.
- **FR-003**: Der **LIVE**-Zustand MUSS gelten, sobald mindestens ein Spiel den
  API-Status **`IN_PLAY`** hat, und den aktuellen Spielstand anzeigen (z. B.
  „⚽ LIVE: GER 2:1 FRA"). Ein Spiel, dessen Anpfiffzeit zwar erreicht ist, das
  aber noch nicht als `IN_PLAY` gemeldet wurde (API-Verzug), gilt **nicht** als
  LIVE; die Presence bleibt solange im nächstniedrigeren Zustand und korrigiert
  sich beim nächsten Sync selbst.
- **FR-004**: Der LIVE-**Stand** MUSS **event-getrieben** über den bestehenden
  F8-Goal-Detector aktualisiert werden. F9 MUSS **keinen eigenen Timer/Poller**
  für Live-Stände einführen.
- **FR-004a**: Die Presence-Neuberechnung (Zustandswechsel, insbesondere
  LIVE-**Eintritt** bei Anpfiff/Stand 0:0 und LIVE-**Austritt** bei Abpfiff)
  MUSS nach jedem `liveGoalPoll`-Zyklus (läuft bereits im Live-Fenster) sowie
  nach `boardRefresh` ausgelöst werden — F9 führt dafür **keinen eigenen
  Scheduler** ein.
- **FR-005**: Der **UPCOMING**-Zustand MUSS gelten, wenn kein Spiel läuft und
  mindestens ein künftiges (noch nicht angepfiffenes) Spiel existiert, und das
  nächste Spiel anzeigen (z. B. „👀 Nächstes: GER vs FRA").
- **FR-006**: Der UPCOMING-Text MUSS beim `boardRefresh`-Takt (Default ~15 min)
  **neu bewertet** und nur bei tatsächlicher Textänderung gesetzt werden (FR-008).
  Eine eigene harte Stundengrenze ist nicht erforderlich, da sich das „nächste
  Spiel" selten ändert und FR-008 (Dedup) zusammen mit FR-009 (Throttle) jedes
  Übermaß verhindern.
- **FR-007**: Der **IDLE**-Zustand MUSS als statischer Fallback gelten, wenn
  weder ein Spiel läuft noch ein künftiges Spiel existiert (z. B.
  „🏆 WM 2026 /tipp").
- **FR-008**: Der Bot MUSS ein Presence-Update **nur dann** an Discord senden,
  wenn sich der berechnete **Anzeigetext** gegenüber dem aktuell gesetzten Text
  tatsächlich geändert hat (kein redundantes Update).
- **FR-009**: Der Bot MUSS Presence-Updates so drosseln, dass das Discord-Limit
  von **5 Änderungen pro 20 Sekunden garantiert nie überschritten** wird (und
  damit der 60-Sekunden-Backoff nie ausgelöst wird). Überzählige Updates werden
  verzögert/zusammengefasst; gesendet wird jeweils der zuletzt gültige
  Anzeigetext.
- **FR-010**: Der Anzeigetext DARF ausschließlich **Standard-Unicode-Emojis**
  verwenden (keine custom Discord-Emojis).
- **FR-011**: Nach Bot-Start bzw. Gateway-Reconnect MUSS die Presence aus dem
  aktuell zutreffenden Zustand (neu) gesetzt werden.
- **FR-012**: Endet das laufende Spiel und läuft kein weiteres, MUSS die
  Presence auf den nächstpriorisierten zutreffenden Zustand (UPCOMING bzw. IDLE)
  wechseln.
- **FR-013**: Laufen mehrere Spiele gleichzeitig, MUSS der LIVE-Text das
  **zuletzt veränderte** Spiel zeigen (jenes mit dem jüngsten Tor-Ereignis).
  Liegt kein unterscheidendes Tor-Ereignis vor, dient der frühere Anpfiff als
  deterministischer Tie-Breaker.

### Key Entities *(include if feature involves data)*

- **Presence-Zustand**: Repräsentiert den aktuell anzuzeigenden WM-Kontext.
  Attribute: Zustandstyp (LIVE | UPCOMING | IDLE), abgeleiteter Anzeigetext.
  Wird aus bestehenden Daten berechnet (laufende/künftige Spiele, Live-Stände
  aus F8) — **kein neues persistentes Datenmodell** erforderlich. Der zuletzt
  gesendete Anzeigetext wird (mindestens prozesslokal) gehalten, um redundante
  Updates zu vermeiden (FR-008).
- **Team-Kürzel-Mapping**: Statisch gepflegte Zuordnung von Teamname → kurzem
  FIFA-3-Letter-Code (z. B. „Deutschland" → „GER"), analog zum bereits manuell
  gepflegten TV-Sender-Mapping. Read-only Nachschlagewerk für kompakte
  Presence-Texte; fehlt ein Eintrag, greift der defensiv gekürzte Klartextname.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Nachdem ein Tor im `liveGoalPoll`-Zyklus erkannt wurde, spiegelt die
  Bot-Presence den neuen Live-Stand spätestens innerhalb **eines Poll-Intervalls
  (Default 30 s) plus eines Throttle-Mindestabstands (Default 5 s)** wider. Die
  Presence folgt dem nächsten Poll — es gibt bewusst keinen separaten Echtzeit-Push
  (F8 pollt).
- **SC-002**: Über eine komplette WM-Spielphase (inkl. zeitgleicher Spiele und
  Tor-Bursts) löst der Bot **null** Discord-Presence-Backoffs aus (das
  5-Änderungen-pro-20-Sekunden-Limit wird nie überschritten).
- **SC-003**: Die Anzahl tatsächlich an Discord gesendeter Presence-Updates
  entspricht der Anzahl echter Anzeigetext-Wechsel — **kein** Update bei
  unverändertem Text.
- **SC-004**: Zu jedem Zeitpunkt zeigt die Presence den höchstpriorisierten
  zutreffenden Zustand; läuft ein Spiel, ist niemals UPCOMING/IDLE sichtbar.
- **SC-005**: Außerhalb von Live- und künftigen Spielen zeigt die Presence zu
  100 % den IDLE-Fallback (nie leer, nie veraltet).

## Assumptions

- **Activity-Typ**: „watching" (Discord zeigt „Sieht sich … an"). Andere
  Activity-Typen sind nicht Teil von F9.
- **Team-Kürzel**: Kompakte LIVE-/UPCOMING-Texte verwenden kurze Team-Kürzel
  (z. B. „GER", „FRA") aus einer **statisch gepflegten FIFA-Code-Mapping-Tabelle**
  (siehe Key Entity „Team-Kürzel-Mapping"); bei fehlendem Eintrag wird defensiv
  der (ggf. gekürzte) Klartextname verwendet.
- **Datenquelle**: F9 nutzt ausschließlich bereits vorhandene Daten (Spiele,
  Status, Stände, F8-Tor-Ereignisse) und führt keine eigene externe Abfrage ein.
- **Trigger UPCOMING**: Die Aktualisierung des UPCOMING-Texts hängt am
  bestehenden `boardRefresh`-Job (kein neuer Scheduler); die „höchstens
  stündlich"-Grenze begrenzt die Aktualisierungsfrequenz unabhängig vom
  `boardRefresh`-Intervall.
- **Single-Guild**: Wie das übrige MVP geht F9 von einer Community/einem Bot
  aus; die Presence ist global pro Bot (nicht pro Guild differenziert).
- **Kein neues persistentes Datenmodell**: Der zuletzt gesetzte Anzeigetext darf
  prozesslokal gehalten werden; nach Neustart wird die Presence ohnehin aus dem
  aktuellen Zustand neu gesetzt (FR-011).
