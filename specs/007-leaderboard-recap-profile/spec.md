# Feature Specification: Live-Leaderboard-Board, Spieltags-Rückblick & /profil

**Feature Branch**: `007-leaderboard-recap-profile`

**Created**: 2026-06-18

**Status**: Draft

**Input**: User description: "Füge drei neue, zusammenhängende Features zu wm-tippspiel-bot-spec.md hinzu (F11, F12, F13). Sie bauen alle auf den bestehenden tips/matches-Daten und dem CHECK24-Punkteschema auf."

## Übersicht

Drei aufeinander aufbauende Features rund um die Sichtbarmachung der Tippspiel-Wertung. Alle bauen auf den vorhandenen `tips`- und `matches`-Daten und dem CHECK24-Punkteschema (4/3/2/0) auf und führen **keine** neue Spiellogik ein — sie aggregieren und präsentieren bestehende Ergebnisse.

- **F11 — Live-Leaderboard-Board:** ein dauerhaftes, selbst-aktualisierendes Ranglisten-Embed in einem read-only Channel (analog zum Spielplan-Board F7).
- **F12 — Spieltags-Rückblick:** automatische Zusammenfassung nach Abschluss aller Spiele eines Spieltags im Announce-Channel.
- **F13 — `/profil [user]`:** persönliche Bilanz eines Nutzers auf Abruf.

## Clarifications

### Session 2026-06-18

- Q: F11 — Vergleichsbasis für die Rang-Veränderung ('seit der letzten Auswertung')? → A: Pro Auswertungs-Batch — Vergleich gegen den Stand vor dem aktuellen `evaluateJob`-Lauf; alle in einem Lauf ausgewerteten Spiele zählen als eine Auswertung.
- Q: F11 — In welchem Channel lebt das Leaderboard-Board? → A: Eigener dedizierter, read-only Ranglisten-Channel, getrennt vom F7-Spielplan-Board.
- Q: F12 — Bester Einzeltipp, wenn niemand am Spieltag exakt traf? → A: Tipp mit der höchsten erreichten Punktzahl des Spieltags; bei Gleichstand das laut Quoten unwahrscheinlichste Ergebnis.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Live-Leaderboard-Board (Priority: P1)

In einem für Mitglieder read-only Channel (z. B. `#wm-rangliste`) steht dauerhaft **ein einziges** Ranglisten-Embed, das der Bot nach jeder Auswertung automatisch per **Edit** aktuell hält — nicht durch wiederholtes Neuposten. Mitglieder sehen jederzeit den aktuellen Stand, ohne einen Command abzusetzen, inklusive der Veränderung gegenüber der letzten Auswertung.

**Why this priority**: Liefert den größten Dauer-Mehrwert für die Community (Always-on-Wettbewerb, Spannung durch Auf-/Abstiege) und ist die direkte Verlängerung des bereits etablierten Board-Musters (F7). Alleine lauffähig und demonstrierbar.

**Independent Test**: Nach einer Spielauswertung (F5) öffnet man den Ranglisten-Channel und sieht ein aktualisiertes Embed mit Rang, Name, Gesamtpunkten, exakten Treffern und Rang-Pfeilen — ohne neue Nachricht und ohne Command.

**Acceptance Scenarios**:

1. **Given** der Ranglisten-Channel enthält noch kein Board, **When** der Bot startet bzw. die erste Auswertung läuft, **Then** postet der Bot genau ein Leaderboard-Embed und trackt es unter dem Slot `board:leaderboard` in `bot_messages`.
2. **Given** ein getracktes Leaderboard-Board existiert, **When** eine Auswertung (F5) Punkte ändert, **Then** wird dieselbe Nachricht **editiert** (kein Neuposten) und zeigt den neuen Stand.
3. **Given** mehrere User mit unterschiedlichen Gesamtpunkten, **When** das Board aufgebaut wird, **Then** erscheinen die Top-N (Default 15) sortiert nach Gesamtpunkten absteigend mit Rang, Name, Gesamtpunkten und Anzahl exakter Treffer.
4. **Given** ein User ist seit der letzten Auswertung von Rang 5 auf Rang 3 gestiegen, **When** das Board aktualisiert wird, **Then** zeigt seine Zeile eine Aufwärts-Markierung (z. B. „↑2"); bei Abstieg „↓1", bei unverändertem Rang „–".
5. **Given** die getrackte Board-Nachricht wurde manuell gelöscht, **When** die nächste Aktualisierung versucht zu editieren, **Then** erkennt der Bot das Fehlen (Edit schlägt fehl), postet das Board neu und aktualisiert `bot_messages`.
6. **Given** verwaiste, nicht mehr getrackte Bot-Board-Nachrichten liegen im Channel, **When** der Bot startet, **Then** entfernt er ausschließlich seine eigenen verwaisten Board-Nachrichten und lässt Nachrichten anderer Nutzer und das gültige Board unangetastet.

---

### User Story 2 - Spieltags-Rückblick (Priority: P2)

Sobald **alle** Spiele eines Spieltags abgeschlossen und ausgewertet sind, postet der Bot automatisch **einmalig** eine Zusammenfassung in den Announce-Channel: die Top-Punktesammler des Spieltags, den besten Einzeltipp (ein exakter Treffer auf ein unwahrscheinliches Ergebnis) und wer an diesem Spieltag leer ausging.

**Why this priority**: Schafft wiederkehrende Highlight-Momente und Gesprächsstoff, ohne dass jemand aktiv nachsehen muss. Baut auf der Auswertung (F5) auf, ist aber unabhängig vom Live-Board demonstrierbar.

**Independent Test**: Man lässt alle Spiele eines Spieltags auf FINISHED+evaluated laufen und beobachtet, dass genau eine Rückblick-Nachricht im Announce-Channel erscheint — und bei erneutem Job-Lauf keine zweite.

**Acceptance Scenarios**:

1. **Given** ein Spieltag hat noch mindestens ein nicht abgeschlossenes/ausgewertetes Spiel, **When** der Rückblick-Job läuft, **Then** wird **kein** Rückblick gepostet.
2. **Given** alle Spiele eines Spieltags sind FINISHED und evaluated, **When** der Rückblick-Job läuft, **Then** postet der Bot genau eine Zusammenfassung in den Announce-Channel.
3. **Given** für einen Spieltag wurde bereits ein Rückblick gepostet, **When** der Job erneut läuft (z. B. nach Neustart oder erneutem Sync), **Then** wird **kein** weiterer Rückblick gepostet (idempotent).
4. **Given** mehrere User haben am Spieltag Punkte gesammelt, **When** der Rückblick erstellt wird, **Then** nennt er die Top-Punktesammler des Spieltags (nur Punkte aus Spielen dieses Spieltags), den besten Einzeltipp und die User, die an diesem Spieltag 0 Punkte erzielten.
5. **Given** an einem Spieltag wurde gar nicht getippt, **When** der Rückblick erstellt wird, **Then** behandelt der Bot den leeren Fall sauber (keine Falschaussage, keine Fehlermeldung).

---

### User Story 3 - /profil [user]-Command (Priority: P3)

Ein Mitglied ruft `/profil` (optional mit Ziel-User) auf und erhält die persönliche Tippspiel-Bilanz: aktueller Rang, Gesamtpunkte, Anzahl exakter Treffer, Trefferquote, bester und schlechtester Tipp sowie die Verteilung der Punktstufen (wie oft 4 / 3 / 2 / 0).

**Why this priority**: Bequemer On-Demand-Zusatz, der die im Board/Rückblick sichtbaren Aggregate für eine einzelne Person vertieft. Niedrigste Priorität, da rein lesend und unabhängig nutzbar.

**Independent Test**: Man ruft `/profil` ohne Argument auf und sieht die eigene Bilanz; mit `/profil @user` die Bilanz des genannten Users — jeweils öffentlich im Channel.

**Acceptance Scenarios**:

1. **Given** ein User mit ausgewerteten Tipps, **When** er `/profil` ohne Argument aufruft, **Then** zeigt der Bot dessen Rang, Gesamtpunkte, exakte Treffer, Trefferquote, besten/schlechtesten Tipp und die Verteilung der Punktstufen (4/3/2/0).
2. **Given** ein anderer Nutzer wird als Argument übergeben, **When** `/profil @user` aufgerufen wird, **Then** zeigt der Bot die Bilanz dieses Nutzers.
3. **Given** ein beliebiger Aufruf, **When** die Antwort erstellt wird, **Then** ist sie **öffentlich** im Channel sichtbar (nicht ephemeral).
4. **Given** ein User ohne (ausgewertete) Tipps, **When** `/profil` aufgerufen wird, **Then** antwortet der Bot mit einer sauberen „noch keine Daten"-Bilanz statt mit einem Fehler.

---

### Edge Cases

- **Gleichstand (F11/F13):** Mehrere User mit identischen Gesamtpunkten erhalten einen konsistenten, deterministischen Rang; Tie-Breaker ist die Anzahl exakter Treffer (analog F6), danach stabile Reihenfolge.
- **Rang-Veränderung ohne Vorzustand (F11):** Beim allerersten Board (noch keine vorherige Auswertung als Vergleichsbasis) wird statt eines Pfeils ein neutraler Marker („–" bzw. „NEU") gezeigt.
- **Neue Top-N-Einsteiger (F11):** Ein User, der erstmals in die Top-N aufsteigt (vorher außerhalb), wird als Aufsteiger/„NEU" markiert, nicht mit irreführendem Pfeil.
- **Embed-Grenzen (F11):** Die Liste wird kompakt in der Embed-Beschreibung gehalten und defensiv abgeschnitten, sodass die Discord-Limits (max. 6000 Zeichen / 25 Felder, 4096 Zeichen Beschreibung) garantiert nicht überschritten werden.
- **Board-Recovery/Cleanup (F11):** wie F7 — manuell gelöschtes Board wird neu gepostet; verwaiste eigene Board-Nachrichten werden beim Start entfernt; fremde Nachrichten bleiben unangetastet.
- **Spieltag über Mitternacht / verspäteter Sync (F12):** Der Rückblick triggert ausschließlich am Vollständigkeits-Kriterium („alle Spiele des Spieltags FINISHED & evaluated"), nicht an einer Uhrzeit; späte API-Updates verschieben lediglich den Auslösezeitpunkt.
- **Doppel-Trigger nach Neustart (F12):** Ein bereits geposteter Spieltags-Rückblick wird durch einen persistenten Marker geschützt; ein Neustart oder erneuter Job-Lauf löst kein zweites Posting aus.
- **Nachträgliche Korrektur (F12):** Wird ein Ergebnis nach dem Rückblick korrigiert (Neuauswertung), bleibt der einmal gepostete Rückblick bestehen (keine rückwirkende Doppelung); die korrigierten Punkte schlagen sich im Live-Board (F11) nieder.
- **Unbekannter/abwesender User (F13):** `/profil` auf einen User ohne Tipps liefert eine leere, aber gültige Bilanz.

## Requirements *(mandatory)*

### Functional Requirements

#### F11 — Live-Leaderboard-Board

- **FR-001**: Das System MUSS in einem **eigenen, dedizierten**, für Mitglieder read-only Ranglisten-Channel (konfigurierbar, getrennt vom F7-Spielplan-Board-Channel) **genau eine** persistente Leaderboard-Nachricht führen, getrackt unter dem neuen Slot `board:leaderboard` in `bot_messages`.
- **FR-002**: Das System MUSS diese Nachricht bei Aktualisierungen **editieren** statt neu zu posten.
- **FR-003**: Das System MUSS das Leaderboard-Board nach jeder Auswertung (F5), die Punkte verändert, aktualisieren (Trigger = abgeschlossene Auto-Auswertung).
- **FR-004**: Das Board MUSS die Top-N Nutzer (Default 15, konfigurierbar) absteigend nach Gesamtpunkten zeigen, je Zeile mit Rang, Anzeigename, Gesamtpunkten und Anzahl exakter Treffer.
- **FR-005**: Die Anzahl exakter Treffer MUSS direkt aus dem Vergleich Tipp gegen tatsächliches Ergebnis (`tips.home_score = matches.home_score AND tips.away_score = matches.away_score` über ausgewertete Spiele) ermittelt werden — **nicht** aus dem Punktwert abgeleitet.
- **FR-006**: Das Board MUSS je Zeile die Rang-Veränderung gegenüber dem Stand **vor dem aktuellen Auswertungs-Batch** anzeigen (Aufstieg „↑n", Abstieg „↓n", unverändert „–", erstmaliger Einstieg „NEU"). Ein Auswertungs-Batch = alle Spiele, die in einem `evaluateJob`-Lauf ausgewertet werden; mehrere im selben Lauf abgeschlossene Spiele zählen als **eine** Auswertung.
- **FR-007**: Das System MUSS den Rang-Zustand des jeweils vorangegangenen Auswertungs-Batches persistent vorhalten und ihn pro Batch fortschreiben, sodass die Rang-Veränderung einen Bot-Neustart übersteht und korrekt berechnet wird.
- **FR-008**: Das System MUSS die Board-Beschreibung kompakt halten und defensiv abschneiden, sodass Discord-Embed-Limits garantiert nicht überschritten werden.
- **FR-009**: Das System MUSS bei fehlender getrackter Board-Nachricht (Edit schlägt fehl) das Board neu posten und `bot_messages` aktualisieren (Recovery wie F7).
- **FR-010**: Das System MUSS beim Start verwaiste, nicht mehr getrackte **eigene** Board-Nachrichten im Ranglisten-Channel entfernen, ohne Nachrichten anderer Nutzer oder das gültige Board zu berühren (Cleanup wie F7).
- **FR-011**: Das Board MUSS dem etablierten visuellen Stil der übrigen Board-/Info-Embeds folgen (konsistente Akzentfarbe, Header mit Emoji, Footer mit Update-Zeitstempel).

#### F12 — Spieltags-Rückblick

- **FR-012**: Das System MUSS einen Spieltag als die Menge der Spiele mit demselben Spieltag-/`matchday`-Bezeichner aus den Quelldaten (football-data.org) gruppieren.
- **FR-013**: Das System MUSS einen Spieltag erst dann als abgeschlossen betrachten, wenn **alle** zugehörigen Spiele `FINISHED` **und** `evaluated` sind.
- **FR-014**: Das System MUSS nach Abschluss eines Spieltags automatisch **genau eine** Zusammenfassung in den Announce-Channel posten.
- **FR-015**: Die Zusammenfassung MUSS enthalten: Top-Punktesammler des Spieltags (Punkte ausschließlich aus Spielen dieses Spieltags), den besten Einzeltipp des Spieltags und die Nutzer, die am Spieltag 0 Punkte erzielten. Der **beste Einzeltipp** ist primär ein exakter Treffer (4 Punkte); existiert am Spieltag kein exakter Treffer, ist es der Tipp mit der **höchsten erreichten Punktzahl**. Bei Gleichstand wird jeweils das laut Buchmacher-Quoten **unwahrscheinlichste Ergebnis** bevorzugt.
- **FR-016**: Das System MUSS das Posten idempotent gestalten: pro Spieltag wird höchstens **einmal** gepostet, auch über Neustarts und wiederholte Job-Läufe hinweg (persistenter Marker pro Spieltag).
- **FR-017**: Das System MUSS den Fall „keine Tipps am Spieltag" sauber behandeln (keine Falschaussage, kein Fehler).
- **FR-018**: Eine nachträgliche Ergebniskorrektur DARF KEINEN zweiten Rückblick für denselben Spieltag auslösen.

#### F13 — /profil [user]-Command

- **FR-019**: Das System MUSS einen Command `/profil [user]` bereitstellen; ohne Argument bezieht er sich auf den aufrufenden User, mit Argument auf den genannten User.
- **FR-020**: Die Ausgabe MUSS enthalten: aktueller Rang, Gesamtpunkte, Anzahl exakter Treffer, Trefferquote, besten und schlechtesten Tipp sowie die Verteilung der Punktstufen (Häufigkeit von 4 / 3 / 2 / 0 Punkten).
- **FR-021**: Die Anzahl exakter Treffer und die Punktstufen-Verteilung MÜSSEN konsistent zum CHECK24-Schema und zur direkten Tipp-gegen-Ergebnis-Auswertung ermittelt werden (gleiche Quelle wie F11).
- **FR-022**: Die Trefferquote MUSS aus einer klar definierten Basis berechnet werden (exakte Treffer bezogen auf die Anzahl ausgewerteter Tipps des Users) und bei 0 ausgewerteten Tipps ohne Division-durch-Null-Fehler dargestellt werden.
- **FR-023**: Die Antwort MUSS **öffentlich** im Channel gepostet werden (nicht ephemeral).
- **FR-024**: Das System MUSS einen User ohne (ausgewertete) Tipps mit einer gültigen leeren Bilanz beantworten (kein Fehler).

#### Querschnittlich

- **FR-025**: Rang, Gesamtpunkte und exakte Treffer MÜSSEN über F6, F11, F12 und F13 dieselbe Definition und Sortier-/Tie-Breaker-Logik verwenden (konsistente Wertung).
- **FR-026**: Keines der drei Features DARF die Punkteberechnung verändern; sie konsumieren ausschließlich bereits ausgewertete `tips.points` und `matches`-Ergebnisse.

### Key Entities *(include if feature involves data)*

- **Leaderboard-Board-Slot**: neuer logischer `bot_messages`-Slot `board:leaderboard` (Channel-ID, Message-ID, updated_at) — eine einzige getrackte Nachricht, analog `board:main` (F7).
- **Rang-Snapshot**: persistent gehaltener Rang je User aus dem vorangegangenen Auswertungs-Batch, dient als Vergleichsbasis für die Rang-Veränderung im Board (F11) und wird pro Batch fortgeschrieben. Übersteht Neustarts.
- **Spieltags-Rückblick-Marker**: persistenter Datensatz je bereits geposteter Spieltag, der das idempotente einmalige Posten (F12) garantiert.
- **Tipp-Aggregate** (abgeleitet, keine neue Tabelle): pro User aggregierte Kennzahlen (Gesamtpunkte, exakte Treffer, Punktstufen-Verteilung, bester/schlechtester Tipp), berechnet aus `tips` ⨝ `matches`.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Nach jeder Auswertung, die Punkte ändert, spiegelt das Leaderboard-Board den neuen Stand innerhalb desselben Auswertungszyklus wider, ohne dass ein Mitglied einen Command absetzen muss.
- **SC-002**: Im Ranglisten-Channel existiert zu jedem Zeitpunkt **höchstens eine** vom Bot getrackte Leaderboard-Nachricht; wiederholte Aktualisierungen erzeugen keine zusätzlichen Nachrichten.
- **SC-003**: 100 % der angezeigten Rang-Veränderungen stimmen mit dem tatsächlichen Rang-Unterschied zur jeweils letzten Auswertung überein, auch nach einem Bot-Neustart.
- **SC-004**: Das Leaderboard-Board überschreitet unter keinen Umständen die Discord-Embed-Grenzen (kein Render-/Edit-Fehler wegen Längenlimits), unabhängig von der Anzahl Nutzer.
- **SC-005**: Pro abgeschlossenem Spieltag erscheint **genau eine** Rückblick-Nachricht; wiederholte Job-Läufe und Neustarts erzeugen nachweislich kein zweites Posting.
- **SC-006**: Der Spieltags-Rückblick erscheint erst, nachdem das letzte Spiel des Spieltags ausgewertet wurde, und nie davor.
- **SC-007**: `/profil` liefert in über 95 % der Aufrufe eine vollständige Bilanz (alle geforderten Kennzahlen) innerhalb der für Discord üblichen Interaktions-Antwortzeit; Aufrufe auf User ohne Tipps liefern eine gültige leere Bilanz statt eines Fehlers.
- **SC-008**: Die in F11, F12 und F13 angezeigten Werte für Gesamtpunkte und exakte Treffer eines Users sind für denselben Zeitpunkt identisch (konsistente Wertung über alle drei Oberflächen).

## Assumptions

- Es existiert ein **eigener, dedizierter**, read-only Ranglisten-Channel (getrennt vom F7-Spielplan-Board-Channel); die Channel-Konfiguration erfolgt wie bei F7 über externe Konfiguration.
- Top-N des Boards ist standardmäßig 15 und über Konfiguration anpassbar.
- Tie-Breaker für gleiche Punktzahl ist die Anzahl exakter Treffer (konsistent mit F6); bei weiterhin gleichem Stand wird eine stabile, deterministische Reihenfolge gewählt.
- Der „beste Einzeltipp" eines Spieltags ist primär ein exakter Treffer (4 Punkte); fehlt ein solcher, der Tipp mit der höchsten erreichten Punktzahl. Bei Gleichstand wird das laut Buchmacher-Quoten unwahrscheinlichste Ergebnis bevorzugt.
- Der Spieltag-Bezug („matchday") ist aus den football-data.org-Quelldaten ableitbar; falls dieser Bezeichner noch nicht persistiert wird, kann das Persistieren des Spieltag-Felds Teil der Umsetzung sein (additive Schema-Änderung via Liquibase, siehe Plan-Phase).
- „Trefferquote" bezeichnet den Anteil exakter Treffer an den ausgewerteten Tipps des Users.
- Die Features sind rein lesend bezogen auf die Wertung: sie verändern weder `tips.points` noch das CHECK24-Schema und setzen die bestehende Auto-Auswertung (F5) sowie das Board-Muster (F7) voraus.
- Eine dauerhafte JDA-Gateway-Verbindung ist vorhanden (für den `/profil`-Slash-Command und das ortsfeste Board), wie durch die Verfassung (Prinzip V) und F7 ohnehin gefordert.

## Dependencies

- **F5 — Auto-Auswertung** liefert den Trigger für F11 und die Datenbasis (`tips.points`) für alle drei Features.
- **F6 — Leaderboard** definiert die kanonische Sortier-/Tie-Breaker- und Exakt-Treffer-Logik, die F11/F13 wiederverwenden.
- **F7 — Live-Spielplan-Board** liefert das wiederverwendbare Muster für persistente, editierte Board-Nachrichten inkl. Cleanup/Recovery.
- **006-check24-scoring** definiert das CHECK24-Punkteschema (4/3/2/0) und die vom Punktwert entkoppelte Exakt-Treffer-Ermittlung.
