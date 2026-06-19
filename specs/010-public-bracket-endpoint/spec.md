# Feature Specification: Öffentlicher Bracket-Endpoint (K.o.-Turnierbaum WM 2026)

**Feature Branch**: `010-public-bracket-endpoint`

**Created**: 2026-06-19

**Status**: Draft

**Input**: User description: "Öffentlicher Bracket-Endpoint für den K.o.-Turnierbaum der WM 2026 auf Basis der festen FIFA-Topologie (WC2026_BRACKET_TOPOLOGY.md). Fünf K.o.-Runden + Spiel um Platz 3, Slot-Zuordnung über kickoff-Reihenfolge, read-only Endpoint /api/public/bracket mit komplettem Baum inkl. Platzhaltern und Gewinner-Fortschritt."

## Clarifications

### Session 2026-06-19

- Q: Wie soll der Sieger eines K.o.-Spiels bestimmt werden, das nach 90 Min. unentschieden stand und erst im Elfmeterschießen entschieden wurde (matches-Tabelle hat bislang nur home_score/away_score)? → A: Schema minimal erweitern — eine zusätzliche nullable Spalte (z. B. `winner`) additiv ergänzen, gefüllt aus football-data `score.winner`. Korrekt für alle Fälle inkl. Elfmeterschießen; lockert die ursprüngliche No-Schema-Change-Vorgabe bewusst zu einer rein additiven Änderung auf.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Kompletten Turnierbaum abrufen (Priority: P1)

Ein Website-Besucher öffnet die Bracket-Ansicht der WM-2026-Tippspiel-Seite. Das Frontend ruft den öffentlichen Bracket-Endpoint auf und erhält den vollständigen K.o.-Baum: alle sechs Runden (Sechzehntelfinale, Achtelfinale, Viertelfinale, Halbfinale, Spiel um Platz 3, Finale) mit allen Spielen, je Spiel die zwei Beteiligten (echte Teams oder Platzhalter-Labels), das aktuelle Ergebnis, den Status und die Verknüpfung zum nächsten Spiel im Baum. Daraus zeichnet das Frontend den kompletten Turnierbaum.

**Why this priority**: Dies ist der Kern des Features. Ohne den vollständigen, korrekt verdrahteten Baum liefert das Feature keinen Wert. Alle anderen Aspekte (Platzhalter, Fortschritt) sind Verfeinerungen dieser einen Antwort.

**Independent Test**: Endpoint aufrufen und prüfen, dass genau 32 K.o.-Spiele in der korrekten Rundenstruktur (16/8/4/2/1 + 1) zurückkommen, jedes Spiel seine FIFA-Match-Nummer (73–104) trägt und die Kanten (welches Spiel speist welches) der Topologie-Definition entsprechen.

**Acceptance Scenarios**:

1. **Given** der Turnierbaum ist abgebildet, **When** der Bracket-Endpoint aufgerufen wird, **Then** enthält die Antwort alle sechs Runden mit der korrekten Spielanzahl je Runde (LAST_32: 16, LAST_16: 8, QUARTER_FINALS: 4, SEMI_FINALS: 2, THIRD_PLACE: 1, FINAL: 1).
2. **Given** ein K.o.-Spiel im Baum, **When** seine Daten betrachtet werden, **Then** trägt es seine stabile FIFA-Match-Nummer und einen Verweis auf das Folge-Spiel, in das sein Gewinner (bzw. beim Halbfinale der Verlierer ins Spiel um Platz 3) einzieht.
3. **Given** ein Achtelfinal-/Viertelfinal-/Halbfinal-/Finalspiel, **When** seine beiden Beteiligten betrachtet werden, **Then** verweisen diese auf die zwei Quell-Spiele (per FIFA-Match-Nummer), aus denen die Teilnehmer hervorgehen.

---

### User Story 2 - Baum vor Ende der Gruppenphase darstellen (Priority: P2)

Während die Gruppenphase noch läuft, stehen die K.o.-Teilnehmer noch nicht fest. Ein Besucher öffnet die Bracket-Ansicht trotzdem. Der Endpoint liefert die vollständige leere Baumstruktur, in der jede noch offene Position ein aussagekräftiges Platzhalter-Label trägt (z. B. „Sieger Gruppe A", „Zweiter Gruppe B", „Dritter A/B/C/D/F"). Das Frontend kann den Baum so bereits vollständig zeichnen, bevor ein einziges K.o.-Spiel ausgetragen wurde.

**Why this priority**: Ermöglicht, dass die Bracket-Ansicht über die gesamte Turnierdauer nutzbar ist – auch vor dem ersten K.o.-Spiel. Wichtig für die Nutzererfahrung, aber baut auf der Grundstruktur aus Story 1 auf.

**Independent Test**: Den Endpoint in einem Zustand abrufen, in dem keine oder erst wenige K.o.-Teams feststehen, und prüfen, dass jede offene Spielposition ein nicht-leeres Platzhalter-Label gemäß Topologie-Definition trägt und kein Platz „leer" oder „null" als Anzeigetext erscheint.

**Acceptance Scenarios**:

1. **Given** die Gruppenphase ist noch nicht abgeschlossen, **When** der Bracket-Endpoint aufgerufen wird, **Then** ist die komplette Baumstruktur (alle 32 Positionen über alle Runden) vorhanden und jede noch nicht feststehende Beteiligten-Position trägt ein beschreibendes Platzhalter-Label.
2. **Given** ein Sechzehntelfinal-Spiel, dessen Teams noch nicht feststehen, **When** seine Beteiligten betrachtet werden, **Then** entsprechen die Platzhalter-Labels der offiziellen Schema-Notation der Topologie-Definition (Gruppensieger/Gruppenzweiter/Drittplatzierten-Kombination).

---

### User Story 3 - Gewinner rücken automatisch nach (Priority: P2)

Ein K.o.-Spiel wird ausgetragen und erhält ein Endergebnis. Beim nächsten Abruf des Bracket-Endpoints ist der Gewinner bereits in das jeweilige Folge-Spiel der nächsten Runde eingerückt – ohne dass ein Redakteur etwas pflegen muss. Bei einem Spiel, das erst nach Verlängerung oder Elfmeterschießen entschieden wurde, ist der korrekte tatsächliche Sieger eingetragen (nicht etwa „unentschieden" nach regulärer Spielzeit). Auch das Spiel um Platz 3 erhält automatisch die beiden Halbfinal-Verlierer.

**Why this priority**: Macht den Baum „lebendig" und korrekt während des laufenden K.o.-Turniers. Hoher Nutzerwert, hängt aber von der Baumstruktur und der Kantenlogik aus Story 1 ab.

**Independent Test**: Für ein abgeschlossenes K.o.-Spiel das Folge-Spiel im Endpoint prüfen: Der Gewinner muss dort als Beteiligter erscheinen. Für einen Sieg nach Verlängerung/Elfmeter prüfen, dass der ausgewiesene Sieger der tatsächliche Gesamtsieger ist.

**Acceptance Scenarios**:

1. **Given** ein K.o.-Spiel hat ein Endergebnis mit eindeutigem Sieger, **When** der Endpoint danach abgerufen wird, **Then** erscheint der Gewinner als Beteiligter im verknüpften Folge-Spiel und das Platzhalter-Label an dieser Position ist ersetzt.
2. **Given** ein K.o.-Spiel endete nach regulärer Spielzeit unentschieden und wurde per Verlängerung/Elfmeterschießen entschieden, **When** der Sieger bestimmt wird, **Then** wird der tatsächliche Gesamtsieger (gemäß bereitgestellter Sieger-Information) ins Folge-Spiel übernommen, nicht der Stand nach 90 Minuten.
3. **Given** beide Halbfinals sind abgeschlossen, **When** das Spiel um Platz 3 betrachtet wird, **Then** sind dessen Beteiligte die beiden Halbfinal-Verlierer.
4. **Given** ein K.o.-Spiel ist noch nicht abgeschlossen, **When** das Folge-Spiel betrachtet wird, **Then** bleibt die entsprechende Beteiligten-Position beim Platzhalter-Label (kein vorzeitig eingerückter Sieger).

---

### Edge Cases

- **Slot-/Kickoff-Reihenfolge weicht von der FIFA-Nummerierung ab**: Falls die tatsächliche kickoff-Reihenfolge der hinterlegten K.o.-Spiele nicht exakt der FIFA-Match-Nummer 73–104 entspricht, muss dies vor dem Live-Gang einmal gegen die echten Daten verifiziert werden (siehe Verifikations-Hinweis in der Topologie-Definition). Die Kanten-Logik bleibt unverändert; nur die Slot→Match-Zuordnung wäre anzupassen.
- **K.o.-Spiele noch gar nicht angelegt**: Wenn noch keine K.o.-Spiele existieren, liefert der Endpoint dennoch die vollständige Baumstruktur ausschließlich mit Platzhaltern und ohne Ergebnisse.
- **Unvollständige Stage**: Falls für eine Runde weniger Spiele vorliegen als erwartet (z. B. nur 12 statt 16 Sechzehntelfinals), werden die vorhandenen Spiele ihren Slots zugeordnet und die fehlenden Positionen als Platzhalter dargestellt; die Antwort bleibt strukturell vollständig.
- **Spiel ohne ausgewiesenen Sieger trotz Ende**: Bei einem als abgeschlossen markierten Spiel ohne eindeutige Sieger-Information rückt kein Team nach; die Folge-Position bleibt beim Platzhalter.
- **Doppelter kickoff-Zeitpunkt**: Zwei Spiele derselben Runde mit identischem Anpfiff werden deterministisch über den Tie-Breaker (interne Spiel-ID) sortiert, sodass die Slot-Zuordnung stabil und reproduzierbar ist.
- **Gleichzeitiger Abruf während eines laufenden Spiels**: Ein noch laufendes K.o.-Spiel wird mit aktuellem Zwischenstand und laufendem Status angezeigt; ein Gewinner rückt erst nach Abschluss nach.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Das System MUSS einen öffentlichen, ausschließlich lesenden Abruf des kompletten K.o.-Turnierbaums der WM 2026 unter `/api/public/bracket` bereitstellen.
- **FR-002**: Die Antwort MUSS alle sechs K.o.-Runden in fester Reihenfolge enthalten: Sechzehntelfinale (LAST_32), Achtelfinale (LAST_16), Viertelfinale (QUARTER_FINALS), Halbfinale (SEMI_FINALS), Spiel um Platz 3 (THIRD_PLACE) und Finale (FINAL).
- **FR-003**: Jede Runde MUSS die erwartete Anzahl Spiele enthalten (16 / 8 / 4 / 2 / 1 / 1), unabhängig davon, wie viele K.o.-Spiele bereits in den Daten vorliegen; fehlende Spiele werden als Platzhalter-Positionen ergänzt.
- **FR-004**: Das System MUSS die feste FIFA-Bracket-Topologie (Match 73–104 inklusive aller Quell→Ziel-Kanten) statisch im Backend abbilden; diese Struktur wird NICHT in der Datenbank gespeichert.
- **FR-005**: Das System MUSS die in den Daten vorhandenen K.o.-Spiele je Runde nach Anpfiffzeit (Tie-Breaker: interne Spiel-ID) sortieren und daraus einen Slot-Index 1..n ableiten.
- **FR-006**: Das System MUSS aus dem Slot-Index die stabile FIFA-Match-Nummer berechnen (LAST_32: 72 + Slot, LAST_16: 88 + Slot, QUARTER_FINALS: 96 + Slot, SEMI_FINALS: 100 + Slot, THIRD_PLACE: 103, FINAL: 104).
- **FR-007**: Jedes Spiel in der Antwort MUSS seine FIFA-Match-Nummer und die Verknüpfung zu seinem Folge-Spiel (per FIFA-Match-Nummer) enthalten; das Finale hat kein Folge-Spiel.
- **FR-008**: Jedes Spiel MUSS seine zwei Beteiligten ausweisen – entweder als feststehendes Team oder, solange noch nicht feststehend, als beschreibendes Platzhalter-Label gemäß Topologie-Definition (z. B. „Sieger Gruppe A", „Zweiter Gruppe B", „Dritter A/B/C/D/F", „Sieger Match 89").
- **FR-009**: Jedes Spiel MUSS Ergebnis (sofern vorhanden) und Status (z. B. noch nicht angepfiffen, laufend, abgeschlossen) ausweisen.
- **FR-010**: Das System MUSS den Gewinner eines abgeschlossenen K.o.-Spiels zur Laufzeit berechnen und in das verknüpfte Folge-Spiel einrücken, ohne diesen Fortschritt zu persistieren.
- **FR-011**: Bei einem Spiel, das nach Verlängerung oder Elfmeterschießen entschieden wurde, MUSS das System den tatsächlichen Gesamtsieger bestimmen. Hierzu MUSS eine zur Spiele-Datenhaltung rein additiv ergänzte, leer-erlaubte Sieger-Information (`winner`, befüllt aus der Sieger-Angabe der Datenquelle football-data `score.winner`) ausgewertet werden, sodass auch ein nach regulärer Spielzeit unentschiedenes, per Elfmeterschießen entschiedenes Spiel korrekt aufgelöst wird.
- **FR-012**: Für das Spiel um Platz 3 MUSS das System die beiden Halbfinal-Verlierer als Beteiligte einsetzen, sobald die Halbfinals abgeschlossen sind.
- **FR-013**: Solange ein Quell-Spiel nicht abgeschlossen ist oder keinen eindeutigen Sieger hat, MUSS die abhängige Beteiligten-Position im Folge-Spiel beim Platzhalter-Label bleiben.
- **FR-014**: Der Endpoint DARF ausschließlich unbedenkliche, öffentlich freigegebene Felder ausgeben (Runde, Spiel-/Match-Nummer, Teams bzw. Platzhalter, Ergebnis, Status, Kanten); es DÜRFEN keine sensiblen oder internen Felder austreten.
- **FR-015**: Der Endpoint MUSS read-only sein; es DARF keinen Schreib-, Änderungs- oder Lösch-Pfad über diese Schnittstelle geben.
- **FR-016**: Der Endpoint MUSS dieselbe Cross-Origin-Freigabe (CORS) verwenden wie die übrigen öffentlichen Endpoints.
- **FR-017**: Die feste Bracket-Topologie (Match-Nummern, Kanten) DARF NICHT in der Datenbank gespeichert werden, sondern wird statisch im Backend abgebildet. Die einzige zulässige Änderung an der Spiele-Datenhaltung ist die rein additive, leer-erlaubte Sieger-Spalte aus FR-011 (keine Änderung oder Entfernung bestehender Spalten, kein Datenverlust, keine Migration vorhandener Werte erforderlich).
- **FR-018**: Die Slot→Anpfiff-Zuordnung MUSS vor dem Live-Gang einmal gegen die echten K.o.-Spieldaten verifizierbar sein; weicht die Reihenfolge ab, ist ausschließlich die Slot→Match-Zuordnung anzupassen, nicht die Kanten-Logik.

### Key Entities *(include if feature involves data)*

- **Bracket (Turnierbaum)**: Die gesamte K.o.-Struktur als geordnete Folge von Runden. Statisch in ihrer Form, dynamisch in ihrer Befüllung.
- **Runde (Round/Stage)**: Eine der sechs K.o.-Stufen mit fester erwarteter Spielanzahl und fester Position im Baum.
- **Bracket-Spiel (Bracket Match)**: Eine Position im Baum mit FIFA-Match-Nummer, Slot-Index, zwei Beteiligten, optionalem Ergebnis, Status, Verweisen auf die zwei Quell-Spiele und das Folge-Spiel. Verbindet ein konkretes vorliegendes Spiel (sofern vorhanden) mit seiner festen Topologie-Position.
- **Beteiligter (Participant/Slot)**: Eine der beiden Seiten eines Bracket-Spiels – entweder ein feststehendes Team oder ein Platzhalter-Label.
- **Platzhalter-Label**: Beschreibender Text für eine noch nicht feststehende Beteiligten-Position (Gruppen-Notation bzw. „Sieger/Verlierer von Match X").
- **Bracket-Kante (Edge)**: Die feste Verknüpfung „Quell-Spiel → Folge-Spiel", über die Gewinner (bzw. Halbfinal-Verlierer ins Spiel um Platz 3) nachrücken.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Ein Aufruf des Bracket-Endpoints liefert in jedem Turnierzustand eine strukturell vollständige Antwort mit allen 32 K.o.-Spielpositionen über alle sechs Runden – zu 100 % der Abrufe, auch vor dem ersten ausgetragenen K.o.-Spiel.
- **SC-002**: Die Verknüpfungen (Quell→Folge) jedes der 32 Spiele entsprechen zu 100 % der hinterlegten FIFA-Topologie-Definition (verifizierbar durch Abgleich der Match-Nummern und Kanten).
- **SC-003**: Sobald ein K.o.-Spiel ein Endergebnis mit eindeutigem Sieger hat, erscheint dieser Sieger beim nächsten Abruf zu 100 % korrekt im verknüpften Folge-Spiel.
- **SC-004**: Bei allen Spielen, die nach Verlängerung oder Elfmeterschießen entschieden wurden, wird der tatsächliche Gesamtsieger ausgewiesen – in 100 % der Fälle, nachweisbar an einem Testfall mit Remis nach regulärer Spielzeit.
- **SC-005**: Keine noch offene Beteiligten-Position erscheint jemals als leerer oder technischer Nullwert; jede trägt ein lesbares Platzhalter-Label.
- **SC-006**: Über den Endpoint sind ausschließlich die freigegebenen Felder erreichbar; eine Prüfung der Antwort weist kein sensibles oder internes Feld nach (0 Leckagen).

## Assumptions

- Die K.o.-Spiele liegen in der bestehenden Spiele-Datenhaltung mit Runde/Stage, Anpfiffzeit, beteiligten Teams, Ergebnis und Status vor (wie von den übrigen Features genutzt).
- Die Sieger-Information bei Verlängerung/Elfmeterschießen wird über die in FR-011 ergänzte additive Sieger-Spalte (aus football-data `score.winner`) mitgeführt; ist diese leer, gilt das Spiel als noch nicht entschieden für den Nachrück-Zweck. Bei eindeutiger Tordifferenz (kein Remis) kann der Sieger auch ohne gesetzte Sieger-Spalte aus dem Ergebnis abgeleitet werden.
- Die feste FIFA-Topologie wird vollständig aus `WC2026_BRACKET_TOPOLOGY.md` übernommen; diese Datei ist die maßgebliche Quelle für Slots, Match-Nummern und Kanten.
- Die öffentlichen Endpoints (CORS, Sichtbarkeitsregeln, Auslieferungsweg) aus dem bestehenden Public-API-Feature werden wiederverwendet; der Bracket-Endpoint fügt sich rein additiv ein.
- Es findet keine Persistierung des berechneten Gewinner-Fortschritts statt; der Baum wird bei jedem Abruf aus den aktuellen Spieldaten plus der statischen Topologie zusammengesetzt.
- Drittplatzierten-Platzhalter folgen der offiziellen Schema-Notation; die exakte Gruppen-Kombination konkretisiert sich erst nach Abschluss der Gruppenphase und ist für die reine Baumdarstellung nicht kritisch (die Kanten sind das Entscheidende).
- Die Verifikation der Slot→Anpfiff-Zuordnung gegen echte Daten erfolgt einmalig vor dem Live-Gang; bis dahin gilt die in der Topologie definierte Reihenfolge als Annahme.
