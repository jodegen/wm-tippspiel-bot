# Feature Specification: Konsolidiertes Live-Spielplan-Board (F7-Redesign)

**Feature Branch**: `003-consolidated-board`

**Created**: 2026-06-13

**Status**: Draft

**Input**: User description: "Ändere Feature F7 (Live-Spielplan-Board) in wm-tippspiel-bot-spec.md. Statt mehrerer Einzel-Embeds (eines pro Tag) enthält der Board-Channel künftig nur EIN konsolidiertes Embed mit den nächsten 12 anstehenden Spielen (Begegnung, Anstoßzeit als Relative-Timestamp, TV-Sender, Quote), dargestellt in der Embed-description als zusammenhängende Liste. Die Filter-Komponente (Select-Menu/Buttons, ephemeral) bleibt unverändert und hängt unter diesem Embed. Ergänze zwei Dinge: (1) eine Cleanup-Logik, die beim Start alte, nicht mehr getrackte Board-Nachrichten des Bots im Channel entfernt, und (2) eine Design-Anforderung, dass das Embed dem visuellen Stil des Info-Channel-Embeds folgt (Akzentfarbe, Header, Footer mit Timestamp, konsistente Emoji-/Strukturierung). bot_messages wird auf einen einzigen Slot board:main reduziert; tagesweise Slots werden migriert/entfernt."

## Kontext

Dies ist eine Überarbeitung des bestehenden Features **F7 — Live-Spielplan-Board**
(siehe `wm-tippspiel-bot-spec.md`). Heute pflegt der Bot je Tag eine eigene
Board-Nachricht (`board:day:<datum>` für „heute" + 3 Folgetage) plus eine separate
Navigations-/Filter-Nachricht (`board:nav`). Das erzeugt mehrere Embeds im
Channel, deren Anzahl mit dem Datum „wandert" und bei jedem Redeploy/Modellwechsel
verwaiste Alt-Nachrichten hinterlassen kann.

Ziel: Der Board-Channel zeigt künftig **genau eine** dauerhaft gepflegte Nachricht
mit den nächsten 12 anstehenden Spielen als zusammenhängende Liste, optisch im
Stil des bestehenden Info-Channel-Embeds, mit der unveränderten Filter-Komponente
direkt darunter. Verwaiste Board-Nachrichten werden beim Start automatisch
entfernt.

## Clarifications

### Session 2026-06-13

- Q: Welche Bot-Nachrichten soll der Start-Cleanup im Board-Channel löschen? → A: Alle vom Bot stammenden Nachrichten im Board-Channel außer der getrackten `board:main` (Channel ist dediziert).
- Q: Wie weit zurück soll der Cleanup den Channel-Verlauf scannen? → A: Die letzten 100 Nachrichten (eine Discord-History-Seite).
- Q: Wann gilt ein Spiel als „anstehend" für das Board? → A: Anstoßzeit in der Zukunft (now < kickoff), unabhängig vom API-Status — konsistent mit der Reveal-Logik (F4).

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Konsolidiertes Spielplan-Board (Priority: P1)

Als Community-Mitglied öffne ich den Board-Channel und sehe **eine** aufgeräumte,
immer aktuelle Nachricht mit den nächsten 12 anstehenden Spielen. Pro Spiel sehe
ich die Begegnung, einen mitlaufenden Countdown bis zum Anpfiff, den TV-Sender und
– falls vorhanden – die Quote, alles als zusammenhängende Liste in einem Embed.

**Why this priority**: Das ist der Kern der Überarbeitung und liefert für sich
genommen schon den vollen Mehrwert: ein ortsfestes, sauberes, sofort verständliches
Board. Ohne diese Story gibt es kein Feature.

**Independent Test**: Board-Channel konfigurieren, Bot starten, Sync abwarten →
genau eine Board-Nachricht mit bis zu 12 künftigen Spielen in korrekter zeitlicher
Reihenfolge erscheint und wird bei Folge-Syncs editiert (nicht neu gepostet).

**Acceptance Scenarios**:

1. **Given** der Board-Channel ist konfiguriert und es existiert noch keine
   getrackte `board:main`-Nachricht, **When** der Board-Refresh läuft, **Then**
   postet der Bot genau eine Nachricht und speichert deren Position unter dem Slot
   `board:main`.
2. **Given** eine getrackte `board:main`-Nachricht existiert, **When** der
   Board-Refresh erneut läuft, **Then** wird dieselbe Nachricht **editiert** (gleiche
   Message-ID), nicht neu gepostet.
3. **Given** es gibt mehr als 12 anstehende Spiele, **When** das Board gerendert
   wird, **Then** zeigt das Embed exakt die 12 zeitlich nächsten noch nicht
   angepfiffenen Spiele, aufsteigend nach Anstoßzeit.
4. **Given** ein Spiel hat einen hinterlegten TV-Sender und/oder eine Quote,
   **When** das Board gerendert wird, **Then** erscheinen Sender bzw. Quote in der
   Zeile des Spiels; fehlen sie, wird die Zeile ohne diese Angaben (ohne Platzhalter-
   Fehler) dargestellt.
5. **Given** weniger als 12 anstehende Spiele existieren (z. B. gegen Turnierende),
   **When** das Board gerendert wird, **Then** werden alle verbleibenden Spiele
   gezeigt; gibt es keine, zeigt das Embed einen freundlichen Leer-Hinweis.
6. **Given** die getrackte `board:main`-Nachricht wurde manuell gelöscht, **When**
   der Board-Refresh läuft, **Then** erkennt der Bot das, postet neu und aktualisiert
   die getrackte Position.

---

### User Story 2 - Migration & Cleanup verwaister Board-Nachrichten (Priority: P2)

Als Betreiber möchte ich, dass nach der Umstellung (und nach jedem Neustart) keine
veralteten Board-Nachrichten des Bots mehr im Channel herumliegen — weder die alten
tagesweisen Slots noch verwaiste Filter-/Navigationsnachrichten.

**Why this priority**: Ohne Cleanup würde die Umstellung sichtbaren Müll
hinterlassen (alte `board:day:*`- und `board:nav`-Nachrichten), was den
Hauptnutzen „ein sauberes Board" untergräbt. Setzt auf US1 auf.

**Independent Test**: Channel mit mehreren alten, vom Bot stammenden
Board-Nachrichten vorbereiten, Bot starten → nach dem Start verbleibt nur die eine
`board:main`-Nachricht; alle übrigen vom Bot stammenden Board-Nachrichten sind
entfernt und ihre Slot-Einträge bereinigt.

**Acceptance Scenarios**:

1. **Given** es existieren getrackte Alt-Slots (`board:day:*`, `board:nav`) aus dem
   früheren Modell, **When** der Bot startet, **Then** werden die zugehörigen
   Nachrichten gelöscht und die Slot-Einträge entfernt, sodass nur `board:main`
   übrig bleibt.
2. **Given** im Board-Channel liegen vom Bot stammende, **nicht** (mehr) getrackte
   Board-Nachrichten, **When** der Cleanup beim Start läuft, **Then** werden diese
   Bot-Nachrichten entfernt.
3. **Given** im Board-Channel liegen Nachrichten **anderer** Nutzer, **When** der
   Cleanup läuft, **Then** bleiben diese unangetastet (der Bot löscht ausschließlich
   eigene Nachrichten).
4. **Given** die aktuell getrackte `board:main`-Nachricht existiert, **When** der
   Cleanup läuft, **Then** wird sie **nicht** gelöscht.

---

### User Story 3 - Filter-Komponente unter dem konsolidierten Board (Priority: P3)

Als Mitglied klicke ich die Filter-Komponente (Select-Menu/Buttons) direkt unter
dem konsolidierten Board und bekomme die gefilterte Ansicht (z. B. „Heute",
„Gruppe A–L", „K.o.-Runde") als **ephemeral**-Antwort, ohne dass das öffentliche
Board sich verändert.

**Why this priority**: Die Filter-Funktion bleibt fachlich unverändert; sie muss
lediglich am neuen, einzigen Board hängen statt an der früheren separaten
Navigationsnachricht. Wert besteht, ist aber inkrementell gegenüber US1.

**Independent Test**: Board steht, Filter unter dem Board auswählen → die filternde
Person erhält eine nur für sie sichtbare gefilterte Liste; das öffentliche
`board:main`-Embed bleibt unverändert.

**Acceptance Scenarios**:

1. **Given** das konsolidierte Board steht, **When** ein Nutzer einen Filter
   auswählt, **Then** erhält **nur dieser Nutzer** eine ephemeral-Antwort mit der
   gefilterten Ansicht.
2. **Given** ein Nutzer nutzt den Filter, **When** die ephemeral-Antwort erscheint,
   **Then** bleibt das öffentliche `board:main`-Embed (Inhalt und Position)
   unverändert.
3. **Given** das Board wird neu gepostet (Recovery oder erstmalig), **When** die
   Nachricht erstellt wird, **Then** hängt die Filter-Komponente direkt unter
   demselben Board und ist sofort bedienbar.

---

### Edge Cases

- **Keine anstehenden Spiele** (Turnierende): Das Board zeigt einen freundlichen
  Leer-Hinweis statt einer leeren Liste.
- **Board-Channel nicht konfiguriert / nicht gefunden / fehlende Rechte**: Es wird
  nichts gepostet/gelöscht; eine Warnung wird geloggt, der Bot läuft weiter.
- **Manuell gelöschtes `board:main`**: Beim nächsten Refresh erkannt, neu gepostet,
  getrackte Position aktualisiert (bestehendes Recovery-Verhalten bleibt).
- **Embed-Limits**: 12 Spiele in einer Beschreibung müssen sicher unter den
  Discord-Grenzen (6000 Zeichen / Embed) bleiben — bei unerwartet langen Inhalten
  wird defensiv gekürzt.
- **Cleanup bei großem Channel-Verlauf**: Der Start-Cleanup darf den Start nicht
  blockieren oder in API-Rate-Limits laufen; er beschränkt sich auf die letzten 100
  Nachrichten (eine History-Seite). Verwaiste Nachrichten jenseits dieser Grenze
  werden nicht erfasst (akzeptiert für einen dedizierten Board-Channel).
- **Race zwischen Cleanup und erstem Refresh**: Cleanup darf die soeben/gerade
  gültige `board:main`-Nachricht nicht löschen.
- **Doppelter Start / mehrere Instanzen**: Cleanup und Single-Slot-Logik dürfen
  nicht zu Endlos-Neuposten oder versehentlicher Löschung des gültigen Boards
  führen (Single-Instance wird vorausgesetzt, siehe Assumptions).

## Requirements *(mandatory)*

### Functional Requirements

#### Konsolidiertes Board (US1)

- **FR-001**: Das System MUSS im Board-Channel genau **eine** dauerhaft gepflegte
  Board-Nachricht führen, getrackt unter dem einzigen Slot `board:main`.
- **FR-002**: Das System MUSS in dieser Nachricht die **nächsten bis zu 12 noch
  nicht angepfiffenen Spiele** anzeigen, aufsteigend nach Anstoßzeit sortiert. „Noch
  nicht angepfiffen" bestimmt sich über die **Anstoßzeit in der Zukunft**
  (now < kickoff), unabhängig vom gemeldeten API-Status — konsistent mit der
  Reveal-Logik (F4).
- **FR-003**: Das System MUSS die Spiele als **zusammenhängende Liste in der
  Embed-Beschreibung** darstellen (nicht als ein Embed-Feld pro Spiel und nicht als
  mehrere Embeds).
- **FR-004**: Jede Spielzeile MUSS enthalten: die Begegnung (Heim–Gast), die
  Anstoßzeit als **Discord-Relative-Timestamp** (mitlaufender Countdown), den
  TV-Sender (falls hinterlegt) und die Quote (falls hinterlegt).
- **FR-005**: Fehlende optionale Angaben (TV-Sender, Quote) MÜSSEN ohne Fehler und
  ohne störende Platzhalter ausgelassen werden.
- **FR-006**: Bei jedem Board-Refresh MUSS die bestehende `board:main`-Nachricht
  **editiert** statt neu gepostet werden (ortsfestes Board).
- **FR-007**: Existiert noch keine getrackte `board:main`-Nachricht, MUSS das System
  genau eine posten und deren Position unter `board:main` persistieren.
- **FR-008**: Ist die getrackte `board:main`-Nachricht nicht mehr vorhanden (manuell
  gelöscht / von Discord verworfen), MUSS das System dies erkennen, neu posten und
  die getrackte Position aktualisieren.
- **FR-009**: Gibt es keine anstehenden Spiele, MUSS das Board einen verständlichen
  Leer-Hinweis anzeigen statt einer leeren Liste.

#### Design / visueller Stil (US1)

- **FR-010**: Das Board-Embed MUSS dem visuellen Stil des Info-Channel-Embeds
  folgen: konsistente **Akzentfarbe**, **Header** (Author/Titel-Zeile mit Emoji),
  **Footer mit Zeitstempel** des letzten Updates und konsistente Emoji-/Struktur-
  Sprache.
- **FR-011**: Das Board-Embed MUSS einen erkennbaren Aktualisierungs-Zeitpunkt
  ausweisen (Footer-Timestamp), sodass Mitglieder sehen, wie aktuell die Anzeige ist.

#### Filter-Komponente (US3)

- **FR-012**: Die bestehende Filter-Komponente (Select-Menu/Buttons) MUSS fachlich
  **unverändert** bleiben und direkt **unter dem konsolidierten Board** hängen.
- **FR-013**: Eine Filter-Auswahl MUSS eine **ephemeral**-Antwort (nur für die
  klickende Person sichtbar) mit der gefilterten Ansicht erzeugen.
- **FR-014**: Eine Filter-Auswahl DARF das öffentliche `board:main`-Embed (Inhalt
  und Position) **nicht** verändern.
- **FR-015**: Beim (Neu-)Posten des Boards MUSS die Filter-Komponente an derselben
  `board:main`-Nachricht hängen und sofort bedienbar sein.

#### Cleanup & Migration (US2)

- **FR-016**: Beim Start MUSS das System **alle vom Bot stammenden** Nachrichten im
  Board-Channel entfernen — **mit Ausnahme** der aktuell getrackten
  `board:main`-Nachricht. (Der Board-Channel ist dediziert; andere Bot-Nachrichten
  sind dort nicht vorgesehen.)
- **FR-017**: Beim Start MÜSSEN die Alt-Slots des früheren Modells (`board:day:*`,
  `board:nav`) inklusive ihrer zugehörigen Nachrichten entfernt/migriert werden,
  sodass nur `board:main` als Slot verbleibt.
- **FR-018**: Der Cleanup MUSS ausschließlich **eigene** Nachrichten des Bots
  löschen; Nachrichten anderer Nutzer MÜSSEN unangetastet bleiben.
- **FR-019**: Der Cleanup DARF die aktuell gültige, getrackte `board:main`-Nachricht
  **nicht** löschen.
- **FR-020**: Der Cleanup MUSS bei fehlendem/unzugänglichem Channel oder fehlenden
  Rechten ohne Abbruch des Bot-Starts ablaufen (Warnung loggen, weiterlaufen) und
  innerhalb der geltenden API-Rate-Limits bleiben.
- **FR-021**: Der Start-Cleanup MUSS den Channel-Verlauf auf die **letzten 100
  Nachrichten** (eine Discord-History-Seite) beschränken; ein vollständiger
  Historien-Scan findet nicht statt.

### Key Entities *(include if feature involves data)*

- **Board-Nachricht (`bot_messages`)**: Verweist auf die vom Bot gepflegte
  Board-Nachricht. Wird auf einen **einzigen** logischen Slot `board:main` reduziert.
  Attribute: Slot-Schlüssel, Channel, Message-ID, Zeitpunkt des letzten Updates.
  Bisherige Slots (`board:day:<datum>`, `board:nav`) entfallen und werden migriert.
- **Spiel (`matches`)**: Liefert die im Board angezeigten Daten (Begegnung,
  Anstoßzeit, Status, TV-Sender, Quote). Für das Board relevant sind die zeitlich
  nächsten noch nicht angepfiffenen Spiele.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Nach einem abgeschlossenen Board-Refresh enthält der Board-Channel
  **genau eine** vom Bot gepflegte Board-Nachricht.
- **SC-002**: Diese Nachricht zeigt die **12 zeitlich nächsten** anstehenden Spiele
  (bzw. alle verbleibenden, wenn weniger als 12), korrekt aufsteigend sortiert.
- **SC-003**: Bei wiederholten Refreshes bleibt die **Message-ID des Boards
  unverändert** (Edit statt Neu-Post), solange die Nachricht existiert.
- **SC-004**: Nach einem Bot-Neustart in einem Channel mit Alt-Board-Nachrichten
  verbleibt **0** verwaiste Bot-Board-Nachricht; Nachrichten anderer Nutzer bleiben
  zu **100 %** erhalten.
- **SC-005**: Eine Filter-Auswahl erzeugt in **100 %** der Fälle eine nur für die
  klickende Person sichtbare Antwort und lässt das öffentliche Board unverändert.
- **SC-006**: Das Board-Embed ist visuell als zur selben „Familie" wie das
  Info-Embed erkennbar (gleiche Akzentfarben-Logik, Header-Stil, Footer mit
  Zeitstempel) — verifizierbar an den Stil-Merkmalen aus FR-010/FR-011.
- **SC-007**: Mitglieder erfassen die nächsten anstehenden Spiele in **einer**
  Nachricht, ohne zwischen mehreren Tages-Embeds scrollen zu müssen.

## Assumptions

- **Anstehend = noch nicht angepfiffen**: Das Board listet ausschließlich Spiele mit
  Anstoßzeit in der Zukunft. Live-/Endstände werden im Board **nicht** dargestellt
  (Live-Tore laufen über F8/Announce-Channel); dadurch entfällt die frühere
  Live-/Endstand-Anzeige im Board.
- **Anzahl 12 ist fix** als Obergrenze; bei weniger anstehenden Spielen werden alle
  gezeigt.
- **Cleanup-Reichweite**: Der Start-Cleanup löscht **alle** vom Bot selbst im
  Board-Channel erstellten Nachrichten außer der getrackten `board:main`, geprüft
  über die letzten 100 Nachrichten des Channel-Verlaufs (kein vollständiger
  Historien-Scan), um Rate-Limits und Startzeit nicht zu sprengen. Der Board-Channel
  gilt als dediziert (read-only für Mitglieder), sodass dort keine anderen
  gewollten Bot-Nachrichten existieren.
- **Single-Instance-Betrieb**: Es läuft genau eine Bot-Instanz pro Community/Guild
  (vgl. Basis-Spec, Multi-Guild ist außer Scope). Damit ist die Single-Slot- und
  Cleanup-Logik eindeutig.
- **Filterumfang unverändert**: Die Filteroptionen und ihr Verhalten bleiben exakt
  wie im bestehenden F7; nur ihr Aufhängungsort (am `board:main`-Embed) ändert sich.
- **Bestehendes Recovery-Verhalten** für fehlende Board-Nachrichten bleibt erhalten
  und gilt nun für den `board:main`-Slot.
- **Stack-Konformität** (Java 21 / Spring Boot 3.x, Schemaänderungen nur via
  Liquibase, JDA-Gateway, UTC speichern / Europe/Berlin anzeigen) gemäß
  Projekt-Constitution; eine etwaige Migration/Bereinigung der `bot_messages`-Slots
  erfolgt additiv über ein Liquibase-Changeset bzw. zur Laufzeit beim Start.
