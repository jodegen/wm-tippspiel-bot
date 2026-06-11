# Feature Specification: WM 2026 Tippspiel Discord-Bot

**Feature Branch**: `001-wm-tippspiel-bot`

**Created**: 2026-06-11

**Status**: Draft

**Input**: User description: "Lies die Datei wm-tippspiel-bot-spec.md im Projektordner und erstelle daraus die Spezifikation. Übernimm die Features F1–F7 als MVP und E1–E7 als Backlog/spätere Phasen. Halte dich an das beschriebene Datenmodell und das 3/1/0-Punkteschema. Das Live-Board (F7) inklusive interaktiver Filter ist Teil des MVP, das Bild-Rendering (E7) bleibt im Backlog."

## Clarifications

### Session 2026-06-11

- Q: Wie soll mit nachträglichen Ergebniskorrekturen umgegangen werden (Endstand eines bereits ausgewerteten Spiels ändert sich)? → A: Automatische Neubewertung — Auswertung zurücksetzen, Punkte neu berechnen, Rangliste aktualisieren, Korrektur-Hinweis posten.
- Q: Sollen Spiele mit noch unbestimmten Teams ("TBD") tippbar sein? → A: Nicht tippbar, bis beide Teams feststehen (aus Auswahl/Autocomplete ausgeblendet).
- Q: Wie sollen abgesagte oder verschobene Spiele behandelt werden? → A: Verschiebung übernimmt die neue Anpfiffzeit (Tippfrist/Reveal verschieben sich mit); Absage löst kein Reveal und keine Auswertung aus.
- Q: Sekundärer Tie-Breaker bei Gleichstand in Punkten UND exakten Treffern? → A: Geteilter Rang (gleiche Platzierung).

## User Scenarios & Testing *(mandatory)*

Die folgenden Nutzer-Journeys sind nach Wichtigkeit priorisiert. Jede ist
eigenständig testbar und liefert für sich genommen Mehrwert. Alle hier
gelisteten Stories (US1–US6) gehören zum MVP (Features F1–F7); E1–E7 sind
explizit Backlog und nicht Teil dieses Specs.

### User Story 1 - Tipp abgeben und aktualisieren (Priority: P1)

Ein Community-Mitglied gibt vor Anpfiff für ein Spiel ein Ergebnis ab und kann
es bis zum Anpfiff beliebig oft korrigieren, ohne dass andere den Tipp sehen.

**Why this priority**: Ohne Tippabgabe gibt es kein Tippspiel. Dies ist der
Einstiegspunkt für jede weitere Wertung und damit der Kern des Produkts.

**Independent Test**: Mitglied wählt ein noch nicht angepfiffenes Spiel,
übermittelt einen Tipp, erhält eine nur für sich sichtbare Bestätigung,
übermittelt erneut einen anderen Wert und sieht, dass der Tipp aktualisiert
(nicht dupliziert) wurde. Ein Tippversuch auf ein bereits angepfiffenes Spiel
wird abgelehnt.

**Acceptance Scenarios**:

1. **Given** ein Spiel mit Anpfiff in der Zukunft, **When** ein Mitglied einen
   Tipp `2:1` abgibt, **Then** wird genau ein Tipp für dieses Mitglied und
   Spiel gespeichert und nur dem Mitglied selbst bestätigt.
2. **Given** ein Mitglied hat bereits `2:1` getippt, **When** es vor Anpfiff
   `0:0` abgibt, **Then** wird der bestehende Tipp auf `0:0` aktualisiert und
   kein zweiter Tipp angelegt.
3. **Given** ein Spiel, dessen Anpfiffzeit erreicht oder überschritten ist,
   **When** ein Mitglied einen Tipp abgeben will, **Then** wird die Abgabe
   abgelehnt mit einem klaren Hinweis, dass die Frist vorbei ist.
4. **Given** die Auswahl eines Spiels, **When** das Mitglied tippen möchte,
   **Then** werden nur noch nicht angepfiffene Spiele zur Auswahl angeboten.

---

### User Story 2 - Tipps automatisch offenlegen bei Anpfiff (Priority: P1)

Sobald ein Spiel angepfiffen wird, legt das System automatisch alle für dieses
Spiel abgegebenen Tipps öffentlich offen, damit niemand nachträglich
manipulieren kann und alle die Tipps der anderen sehen.

**Why this priority**: Die Offenlegung exakt bei Anpfiff sichert Fairness und
Transparenz; sie ist die Voraussetzung dafür, dass die ephemerale Tippabgabe
überhaupt vertrauenswürdig ist. Reveal-Timing ist verfassungsmäßig
test-pflichtige Kernlogik.

**Independent Test**: Ein Spiel wird auf einen Anpfiff in unmittelbarer
Vergangenheit gesetzt; nach dem nächsten Prüfzyklus erscheint genau eine
öffentliche Übersicht aller Tipps für dieses Spiel, und das Spiel wird als
offengelegt markiert, sodass kein zweites Mal offengelegt wird.

**Acceptance Scenarios**:

1. **Given** ein Spiel, dessen Anpfiffzeit gerade erreicht wurde und das noch
   nicht offengelegt ist, **When** der Offenlegungs-Prüfzyklus läuft, **Then**
   wird eine öffentliche Übersicht aller abgegebenen Tipps veröffentlicht und
   das Spiel als offengelegt markiert.
2. **Given** ein bereits offengelegtes Spiel, **When** der Prüfzyklus erneut
   läuft, **Then** wird keine weitere Offenlegung veröffentlicht.
3. **Given** ein Spiel mit Anpfiff in der Zukunft, **When** der Prüfzyklus
   läuft, **Then** wird dieses Spiel nicht offengelegt.
4. **Given** eine zeitliche Verzögerung der externen Ergebnisquelle, **When**
   der Anpfiffzeitpunkt erreicht ist, **Then** richtet sich die Offenlegung
   ausschließlich nach der gespeicherten Anpfiffzeit, nicht nach einem extern
   gemeldeten Status.

---

### User Story 3 - Automatische Auswertung und Punktevergabe (Priority: P1)

Nach Spielende berechnet das System für jeden Tipp Punkte nach dem 3/1/0-Schema,
speichert sie und veröffentlicht eine Ergebnis- und Punkteübersicht.

**Why this priority**: Die Punktewertung ist der spielentscheidende Mechanismus
und verfassungsmäßig test-pflichtige Kernlogik. Ohne korrekte Auswertung hat
das Tippspiel keinen Sinn.

**Independent Test**: Ein Spiel wird auf „beendet" mit Endstand gesetzt; nach
dem Auswertungszyklus erhält jeder Tipp die korrekten Punkte (3 bei exaktem
Ergebnis, 1 bei richtiger Tendenz, 0 sonst), das Spiel wird als ausgewertet
markiert und eine Punkteübersicht erscheint.

**Acceptance Scenarios**:

1. **Given** ein beendetes Spiel `2:1` und ein Tipp `2:1`, **When** die
   Auswertung läuft, **Then** erhält der Tipp **3 Punkte**.
2. **Given** ein beendetes Spiel `2:1` und ein Tipp `3:0` (gleiche Tendenz
   Heimsieg, falsches Ergebnis), **When** die Auswertung läuft, **Then** erhält
   der Tipp **1 Punkt**.
3. **Given** ein beendetes Spiel `2:2` und ein Tipp `0:0` (Unentschieden
   getippt, falsches Ergebnis), **When** die Auswertung läuft, **Then** erhält
   der Tipp **1 Punkt**.
4. **Given** ein beendetes Spiel `2:1` und ein Tipp `1:2` (falsche Tendenz),
   **When** die Auswertung läuft, **Then** erhält der Tipp **0 Punkte**.
5. **Given** ein bereits ausgewertetes Spiel, **When** der Auswertungszyklus
   erneut läuft, **Then** werden keine Punkte erneut vergeben und keine zweite
   Übersicht veröffentlicht.

---

### User Story 4 - Rangliste (Priority: P2)

Mitglieder rufen eine Rangliste ab, die alle Teilnehmer nach Gesamtpunkten
sortiert anzeigt.

**Why this priority**: Die Rangliste schafft den Wettbewerbsanreiz, ist aber
erst sinnvoll, sobald Tipps abgegeben und ausgewertet werden (US1–US3).

**Independent Test**: Bei vorhandenen ausgewerteten Tipps liefert der Abruf
eine absteigend nach Punkten sortierte Liste mit Anzahl abgegebener Tipps und
Anzahl exakter Treffer; bei Punktgleichheit entscheidet die Anzahl exakter
Treffer.

**Acceptance Scenarios**:

1. **Given** mehrere Mitglieder mit ausgewerteten Tipps, **When** die Rangliste
   abgerufen wird, **Then** erscheinen alle Teilnehmer absteigend nach
   Gesamtpunkten sortiert.
2. **Given** zwei Mitglieder mit gleicher Punktzahl, **When** die Rangliste
   sortiert wird, **Then** steht das Mitglied mit mehr exakten Treffern weiter
   oben.
3. **Given** ein Mitglied mit abgegebenen, aber noch nicht ausgewerteten Tipps,
   **When** die Rangliste abgerufen wird, **Then** wird seine Tippanzahl
   ausgewiesen und seine Punktzahl spiegelt nur ausgewertete Spiele wider.

---

### User Story 5 - Live-Spielplan-Board mit interaktiven Filtern (Priority: P2)

In einem dedizierten, für Mitglieder schreibgeschützten Kanal hält der Bot ein
dauerhaftes, selbst-aktualisierendes Spielplan-Board. Mitglieder können über
eine Navigationskomponente gefilterte Ansichten abrufen, ohne das öffentliche
Board zu verändern.

**Why this priority**: Das Live-Board ersetzt im Alltag die manuellen Abfragen
und ist das herausragende MVP-Feature. Es setzt jedoch vorhandene Spieldaten
voraus und ist gegenüber dem Tipp-/Wertungskern nachrangig.

**Independent Test**: Beim ersten Start postet der Bot je Tages-Slot eine
Nachricht und merkt sich deren Position; bei jeder Aktualisierung wird dieselbe
Nachricht editiert statt neu gepostet. Eine Filterauswahl liefert dem Klickenden
eine nur für ihn sichtbare gefilterte Ansicht, während das öffentliche Board
unverändert bleibt. Eine manuell gelöschte Board-Nachricht wird beim nächsten
Lauf neu gepostet.

**Acceptance Scenarios**:

1. **Given** ein leerer Board-Kanal, **When** der Bot erstmals startet, **Then**
   postet er je Tages-Slot genau eine Board-Nachricht und merkt sich deren
   Position für spätere Edits.
2. **Given** ein bestehendes Board, **When** sich Spieldaten ändern (Spielplan,
   Sender, Quoten, Spielstand), **Then** werden die vorhandenen Nachrichten
   editiert und keine neuen Nachrichten gepostet.
3. **Given** ein angezeigtes Board mit Navigationskomponente, **When** ein
   Mitglied einen Filter wählt (z. B. „Morgen", „Gruppe A", „K.o.-Runde"),
   **Then** erhält nur diese Person eine gefilterte, nur für sie sichtbare
   Ansicht und das öffentliche Board bleibt unverändert.
4. **Given** eine getrackte Board-Nachricht wurde manuell gelöscht, **When** das
   nächste Update läuft, **Then** erkennt der Bot das Fehlen, postet die
   Nachricht neu und merkt sich die neue Position.
5. **Given** mehr Spiele, als in eine einzelne Nachricht passen, **When** das
   Board aufgebaut wird, **Then** werden die Spiele über mehrere Tages-Slots
   verteilt, sodass keine Anzeigegrenze überschritten wird.

---

### User Story 6 - Spielplan-Übersicht und nächstes Spiel on-demand (Priority: P3)

Mitglieder rufen auf Wunsch direkt die nächsten Spiele oder das unmittelbar
nächste Spiel ab — als Direktzugriff und Fallback neben dem Live-Board.

**Why this priority**: Reine Komfort-/Fallback-Funktion; im Alltag durch das
Live-Board (US5) ersetzt, daher niedrigste MVP-Priorität.

**Independent Test**: Der Abruf der Spielplan-Übersicht zeigt die nächsten N
anstehenden Spiele (Standard 5) und blendet vergangene/laufende aus; der Abruf
des nächsten Spiels zeigt genau das zeitlich nächste anstehende Spiel mit
mitlaufendem Countdown.

**Acceptance Scenarios**:

1. **Given** eine Menge anstehender Spiele, **When** ein Mitglied die Übersicht
   ohne Anzahl abruft, **Then** werden die nächsten 5 anstehenden Spiele mit
   Begegnung, Anstoßzeit, TV-Sender und (falls vorhanden) Quoten angezeigt.
2. **Given** ein gewünschtes Anzahl-Argument N, **When** die Übersicht abgerufen
   wird, **Then** werden die nächsten N anstehenden Spiele angezeigt.
3. **Given** vergangene und laufende Spiele, **When** die Übersicht abgerufen
   wird, **Then** erscheinen diese nicht.
4. **Given** anstehende Spiele, **When** „nächstes Spiel" abgerufen wird,
   **Then** erscheint genau das zeitlich nächste Spiel mit einem mitlaufenden
   Countdown bis zum Anpfiff.

---

### Edge Cases

- **Anpfiff trotz Datenverzug**: Reveal hängt an der gespeicherten Anpfiffzeit,
  nicht an einem extern gemeldeten Status — ein Spiel gilt als angepfiffen,
  sobald seine Anpfiffzeit erreicht ist.
- **Keine Tipps für ein Spiel**: Offenlegung bei Anpfiff erfolgt auch dann
  (mit dem Hinweis, dass keine Tipps vorliegen), und die Auswertung vergibt
  keine Punkte.
- **Nachträgliche Ergebniskorrektur**: Ändert sich der Endstand eines bereits
  ausgewerteten Spiels, wird die Auswertung zurückgesetzt, neu berechnet und ein
  Korrektur-Hinweis veröffentlicht (FR-017a).
- **Abgesagtes Spiel**: Kein Reveal, keine Auswertung, keine Punkte (FR-004b).
- **Verschobenes Spiel**: Neue Anpfiffzeit wird übernommen; Tippfrist und Reveal
  verschieben sich mit (FR-004a).
- **TBD-Begegnung**: Nicht tippbar, bis beide Teams feststehen (FR-009); im Board
  weiterhin sichtbar.
- **Gelöschte/verworfene Board-Nachricht**: Wird beim nächsten Update erkannt
  und neu gepostet.
- **Anzeigegrenzen**: 104 Spiele dürfen niemals in eine Nachricht — Aufteilung
  nach Tag/Slot ist verpflichtend.
- **„TBD"-Teams**: Vor Abschluss der Gruppenphase können Begegnungen noch
  unbestimmte Teilnehmer haben; Anzeige und Tippbarkeit müssen damit umgehen.
- **Spätabendliche/nächtliche Anpfiffzeiten** (deutsche Zeit) durch Austragung
  in Nordamerika — relevant für Offenlegungs- und Erinnerungszeitpunkte.
- **Zeitumstellung / Zeitzonen**: Speicherung in UTC, Anzeige in `Europe/Berlin`
  muss über Sommer-/Winterzeit korrekt bleiben.
- **Doppelte Verarbeitung nach Neustart/Reconnect**: Offenlegung und Auswertung
  dürfen ein Spiel nach Unterbrechung nicht erneut verarbeiten.

## Requirements *(mandatory)*

### Functional Requirements

**Spieldaten (Grundlage)**

- **FR-001**: Das System MUSS Spielplan und Ergebnisse aus einer externen Quelle
  beziehen und den lokalen Spielbestand regelmäßig aktualisieren (Begegnungen,
  Anstoßzeit, Turnierphase, Spielstand, Status).
- **FR-002**: Das System MUSS Anstoßzeiten in UTC vorhalten und alle Zeiten in
  der Anzeige in `Europe/Berlin` darstellen.
- **FR-003**: Das System SOLL Buchmacher-Quoten (Heim/Unentschieden/Auswärts)
  ergänzen, falls verfügbar; fehlende Quoten dürfen die übrigen Funktionen nicht
  blockieren.
- **FR-004**: Das System MUSS TV-Sender je Spiel anzeigen können, basierend auf
  einem manuell gepflegten Mapping.
- **FR-004a**: Bei einer Verschiebung MUSS das System die neue Anpfiffzeit aus
  der externen Quelle übernehmen; Tippfrist und Offenlegungszeitpunkt richten
  sich automatisch nach der aktualisierten Anpfiffzeit. Solange ein verschobenes
  Spiel noch nicht (neu) angepfiffen ist, bleibt es tippbar.
- **FR-004b**: Bei einer Absage MUSS das System weder offenlegen noch auswerten
  und keine Punkte vergeben; das Spiel wird als abgesagt kenntlich gemacht.

**Tippabgabe (US1)**

- **FR-005**: Mitglieder MÜSSEN für ein noch nicht angepfiffenes Spiel ein
  Ergebnis (Heim/Gast) abgeben können.
- **FR-006**: Das System MUSS pro Mitglied und Spiel höchstens einen Tipp halten;
  erneute Abgabe vor Anpfiff aktualisiert den bestehenden Tipp.
- **FR-007**: Das System MUSS eine Tippabgabe oder -änderung ablehnen, sobald die
  Anpfiffzeit des Spiels erreicht ist.
- **FR-008**: Die Tippabgabe-Bestätigung MUSS ausschließlich für das abgebende
  Mitglied sichtbar sein, damit kein Tipp vorzeitig bekannt wird.
- **FR-009**: Die Spielauswahl bei der Tippabgabe MUSS auf noch nicht
  angepfiffene Spiele beschränkt sein, deren beide Teilnehmer feststehen; Spiele
  mit noch unbestimmten Teams ("TBD") werden nicht zur Auswahl angeboten und
  sind erst tippbar, sobald beide Teams feststehen.
- **FR-010**: Das System MUSS den Anzeigenamen des Mitglieds zum Zeitpunkt der
  Abgabe für spätere Anzeigen festhalten.

**Offenlegung (US2)**

- **FR-011**: Das System MUSS regelmäßig prüfen, ob Spiele angepfiffen wurden,
  deren Tipps noch nicht offengelegt sind, und für diese alle abgegebenen Tipps
  öffentlich veröffentlichen.
- **FR-012**: Das System MUSS jedes Spiel nach erfolgter Offenlegung als
  offengelegt markieren und eine erneute Offenlegung verhindern.
- **FR-013**: Die Offenlegung MUSS sich allein an der gespeicherten Anpfiffzeit
  orientieren, nicht an einem extern gemeldeten Spielstatus.

**Auswertung & Punkte (US3)**

- **FR-014**: Das System MUSS nach Spielende für jeden Tipp Punkte nach dem
  3/1/0-Schema berechnen: **3** bei exaktem Ergebnis, **1** bei richtiger
  Tendenz (Heimsieg, Auswärtssieg oder Unentschieden) bei falschem Ergebnis,
  **0** sonst.
- **FR-015**: Das System MUSS die berechneten Punkte je Tipp persistent
  festhalten (Standardwert 0 vor Auswertung).
- **FR-016**: Das System MUSS jedes Spiel nach erfolgter Auswertung als
  ausgewertet markieren und eine erneute Punktevergabe verhindern.
- **FR-017**: Das System MUSS nach der Auswertung eine Ergebnis- und
  Punkteübersicht veröffentlichen.
- **FR-017a**: Ändert sich der Endstand eines bereits ausgewerteten Spiels,
  MUSS das System die Auswertung dieses Spiels zurücksetzen, die Punkte aller
  betroffenen Tipps neu nach dem 3/1/0-Schema berechnen, die Rangliste
  entsprechend aktualisieren und einen Korrektur-Hinweis veröffentlichen.

**Rangliste (US4)**

- **FR-018**: Das System MUSS auf Abruf eine nach Gesamtpunkten absteigend
  sortierte Rangliste aller Teilnehmer liefern.
- **FR-019**: Die Rangliste MUSS je Teilnehmer die Anzahl abgegebener Tipps und
  die Anzahl exakter Treffer ausweisen.
- **FR-020**: Bei Punktgleichheit MUSS die Anzahl exakter Treffer als
  Tie-Breaker dienen. Sind auch die exakten Treffer gleich, teilen sich die
  betroffenen Teilnehmer denselben Rang (geteilte Platzierung).

**Live-Board (US5)**

- **FR-021**: Das System MUSS in einem dedizierten Kanal ein dauerhaftes Board
  führen, das bei Aktualisierungen bestehende Nachrichten editiert statt neue zu
  posten.
- **FR-022**: Das System MUSS die Position seiner Board-Nachrichten persistent
  vorhalten, sodass nach Neustart/Reconnect dieselben Nachrichten weiter
  editiert werden.
- **FR-023**: Das System MUSS den Spielplan über mehrere Tages-Slots verteilen,
  sodass keine Anzeigegrenze überschritten wird (eine Nachricht = ein Slot).
- **FR-024**: Das System MUSS je Spiel Begegnung, Anstoßzeit als mitlaufenden
  Countdown, TV-Sender, ggf. Quoten und – sobald vorhanden – den Live-/Endstand
  anzeigen. Der Live-/Endstand wird mit der Latenz des Spielplan-Syncs
  (~15 Min) aktualisiert; echtzeitnahe Tor-Aktualisierungen sind nicht Teil des
  MVP (Backlog E2).
- **FR-025**: Das System MUSS unter dem Board eine Navigationskomponente bieten,
  über die Mitglieder gefilterte Ansichten abrufen können (mindestens: Heute,
  Folgetag(e), Gruppen, K.o.-Runde).
- **FR-026**: Eine Filterauswahl MUSS eine nur für die auswählende Person
  sichtbare Ansicht liefern und das öffentliche Board unverändert lassen.
- **FR-027**: Das System MUSS erkennen, wenn eine getrackte Board-Nachricht
  nicht mehr existiert, sie neu posten und die gespeicherte Position
  aktualisieren.
- **FR-028**: Das System MUSS interaktive Komponenten in Echtzeit bedienen
  können (dauerhafte Empfangsbereitschaft für Interaktionen), nicht nur zeit-
  gesteuert posten/editieren.

**Spielplan on-demand (US6)**

- **FR-029**: Mitglieder MÜSSEN auf Abruf die nächsten N anstehenden Spiele
  (Standard 5) mit Begegnung, Anstoßzeit, TV-Sender und ggf. Quoten erhalten;
  vergangene und laufende Spiele werden ausgeblendet.
- **FR-030**: Mitglieder MÜSSEN auf Abruf das unmittelbar nächste anstehende
  Spiel mit mitlaufendem Countdown, Sender und ggf. Quoten erhalten.

**Robustheit**

- **FR-031**: Offenlegung und Auswertung MÜSSEN nach Unterbrechung
  (Neustart/Reconnect) idempotent sein und kein bereits verarbeitetes Spiel
  erneut verarbeiten.
- **FR-032**: Externe Datenquellen-Limits MÜSSEN respektiert werden, sodass der
  Betrieb nicht durch Überschreiten von Abruf-Limits gestört wird.

### Key Entities *(include if feature involves data)*

- **Spiel (matches)**: Eine WM-Begegnung. Attribute u. a.: Heim-/Gastteam (vor
  Gruppenabschluss ggf. unbestimmt), Anstoßzeit (UTC), Turnierphase, TV-Sender,
  Quoten (optional), Endstand (offen bis Abpfiff), Status
  (geplant/laufend/beendet), Offenlegungs-Markierung, Auswertungs-Markierung.
  Identität stammt aus der externen Quelle.
- **Tipp (tips)**: Vorhersage eines Mitglieds für ein Spiel. Attribute:
  Mitglieds-Kennung, Anzeigename (denormalisiert), Spielbezug, getipptes
  Ergebnis (Heim/Gast), Abgabezeitpunkt, erzielte Punkte (Standard 0). Eindeutig
  je Mitglied und Spiel; bis Anpfiff aktualisierbar.
- **Board-Nachricht (bot_messages)**: Verwaltet die persistenten, vom Bot
  editierten Board-Nachrichten. Attribute: logischer Slot-Schlüssel (z. B.
  „heute" oder ein Datum), Kanal, Nachrichten-Position, letzter
  Aktualisierungszeitpunkt.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Ein Mitglied kann einen Tipp in unter 30 Sekunden abgeben oder
  ändern, ohne dass andere den Inhalt vor Anpfiff sehen können.
- **SC-002**: 100 % der angepfiffenen Spiele werden innerhalb von höchstens 2
  Minuten nach Anpfiff genau einmal offengelegt.
- **SC-003**: 100 % der Tipps werden nach Spielende korrekt nach dem
  3/1/0-Schema bewertet; in einer Stichprobe von Auswertungen treten null
  Fehlbewertungen auf.
- **SC-004**: Kein Spiel wird mehr als einmal offengelegt oder ausgewertet, auch
  nicht nach einem Neustart oder Verbindungsabbruch.
- **SC-005**: Die Rangliste ist nach jeder Auswertung aktuell und für jedes
  teilnehmende Mitglied korrekt nach Punkten und Tie-Breaker sortiert.
- **SC-006**: Das Live-Board bleibt ortsfest — bei Aktualisierungen entstehen
  keine neuen Nachrichten; eine Filterauswahl verändert das öffentliche Board
  nicht.
- **SC-007**: Eine Filterauswahl liefert dem Mitglied innerhalb von 3 Sekunden
  eine nur für es sichtbare gefilterte Ansicht.
- **SC-008**: Alle Anzeigen zeigen Zeiten korrekt in `Europe/Berlin`, auch über
  die Sommer-/Winterzeit-Grenze hinweg.
- **SC-009**: Eine manuell gelöschte Board-Nachricht ist nach spätestens einem
  Aktualisierungszyklus wiederhergestellt.

## Assumptions

- **Eine Community / ein Server**: Das MVP geht von genau einer Discord-Community
  mit einem Ankündigungs-Kanal und einem Board-Kanal aus. Mehrere Server
  (Multi-Guild) sind nicht Teil des MVP.
- **Teilnahmeberechtigung**: Jedes Mitglied des Servers darf tippen; es gibt
  keine separate Anmeldung. Punkte/Rangliste umfassen alle, die mindestens einen
  Tipp abgegeben haben.
- **Externe Datenquelle als Wahrheit**: Spielplan, Status und Endergebnisse
  stammen aus der externen Ergebnisquelle; TV-Sender werden manuell gepflegt;
  Quoten sind optional.
- **Reveal-/Eval-Frequenz**: Offenlegungs- und Auswertungsprüfungen laufen
  minütlich; Spielplan-Sync seltener (im Minuten-/Stundenbereich) unter
  Beachtung der Abruf-Limits.
- **Nachträgliche Ergebniskorrekturen**: Ändert die externe Quelle den Endstand
  eines bereits ausgewerteten Spiels, erfolgt eine automatische Neubewertung
  (FR-017a) — die erste Auswertung ist also nicht endgültig.
- **Tipp-Erinnerungen, Live-Tor-Benachrichtigungen, Brackets, Underdog-Badges,
  Saison-Statistiken, Admin-Commands und gerendertes Bild-Board (E1–E7)** sind
  ausdrücklich Backlog und nicht Teil dieses Specs. Insbesondere bleibt das
  Bild-Rendering (E7) im Backlog, während das Embed-basierte Live-Board mit
  Filtern (F7) Teil des MVP ist.
- **Persistenz über Neustarts**: Getrackte Board-Nachrichten und der
  Offenlegungs-/Auswertungsstand überleben Neustarts, sodass kein doppeltes
  Posten/Werten erfolgt.
