# Feature Specification: CHECK24-Punkteschema (vierstufige Staffelung)

**Feature Branch**: `006-check24-scoring`

**Created**: 2026-06-18

**Status**: Draft

**Input**: User description: "Ändere das Punktesystem (Feature F5 — Auto-Auswertung) auf das CHECK24-Schema mit vierstufiger Staffelung (4/3/2/0). Statistik 'exakte Treffer' muss unabhängig vom Punktwert direkt aus Tipp-vs-Ergebnis berechnet werden; Leaderboard (F6) entsprechend anpassen. Zusätzlich ein einmaliger rückwirkender Neuberechnungs-Mechanismus, der die Punkte aller bereits ausgewerteten Tipps nach dem neuen Schema neu berechnet und überschreibt."

## Clarifications

### Session 2026-06-18

- Q: Wie soll der einmalige rückwirkende Neuberechnungs-Mechanismus ausgelöst werden? → A: Automatisch beim App-Start (idempotenter Runner, keine neue Bedienoberfläche)
- Q: Woher bezieht das Leaderboard die Anzahl exakter Treffer pro User? → A: Live aus dem Vergleich Tipp-vs-Ergebnis bei jedem Ranglisten-Abruf (kein neues persistiertes Feld)

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Tipps werden nach dem CHECK24-Schema bewertet (Priority: P1)

Nach Abpfiff eines Spiels sollen die abgegebenen Tipps der Mitspieler nach einer
feineren, vierstufigen Punktestaffelung bewertet werden, sodass eine knapp
verfehlte, aber qualitativ gute Vorhersage (richtige Tordifferenz) mehr belohnt
wird als ein bloßer Tendenz-Treffer.

**Why this priority**: Dies ist der Kern der Änderung. Ohne die neue Staffelung
liefert das Tippspiel keinen zusätzlichen Wert. Alle anderen Stories bauen darauf
auf.

**Independent Test**: Für ein abgeschlossenes Spiel mit bekanntem Endstand werden
mehrere Tipps unterschiedlicher Qualität (exakt, richtige Differenz, richtige
Tendenz, daneben) ausgewertet und die vergebenen Punkte gegen die erwarteten Werte
4/3/2/0 geprüft.

**Acceptance Scenarios**:

1. **Given** Endstand 2:1 und ein Tipp 2:1, **When** das Spiel ausgewertet wird, **Then** erhält der Tipp **4 Punkte** (exaktes Ergebnis).
2. **Given** Endstand 4:1 und ein Tipp 3:0, **When** das Spiel ausgewertet wird, **Then** erhält der Tipp **3 Punkte** (richtige Tordifferenz +3, aber nicht exakt).
3. **Given** Endstand 1:1 und ein Tipp 2:2, **When** das Spiel ausgewertet wird, **Then** erhält der Tipp **3 Punkte** (Unentschieden mit falscher Höhe = richtige Tordifferenz 0).
4. **Given** Endstand 3:1 und ein Tipp 1:0, **When** das Spiel ausgewertet wird, **Then** erhält der Tipp **2 Punkte** (richtige Tendenz Heimsieg, aber weder exakt noch gleiche Differenz).
5. **Given** Endstand 2:2 und ein Tipp 1:2, **When** das Spiel ausgewertet wird, **Then** erhält der Tipp **0 Punkte** (falsche Tendenz: Unentschieden vs. Auswärtssieg).
6. **Given** Endstand 0:0 und ein Tipp 0:0, **When** das Spiel ausgewertet wird, **Then** erhält der Tipp **4 Punkte** (exaktes Unentschieden).

---

### User Story 2 - Leaderboard zeigt korrekte exakte Treffer unabhängig vom Punktwert (Priority: P2)

Die Rangliste (`/rangliste`) führt als Zusatzspalte und Tie-Breaker die Anzahl der
exakt richtig getippten Spiele je User. Diese Zahl muss korrekt bleiben, egal wie
das Punkteschema gewichtet ist, und darf nicht aus dem Punktwert abgeleitet werden.

**Why this priority**: Direkt sichtbarer Effekt für die Nutzer und Voraussetzung
dafür, dass die Rangfolge bei Punktgleichstand korrekt aufgelöst wird. Hängt vom
neuen Schema (P1) ab, weil sich sonst der frühere Shortcut "points == Höchstwert"
heimlich falsch verhält.

**Independent Test**: Für einen User mit gemischten Tipps wird die im Leaderboard
ausgewiesene Anzahl exakter Treffer mit der direkt aus Tipp-vs-Ergebnis ermittelten
Anzahl verglichen; beide müssen übereinstimmen, auch wenn der Höchstpunktwert sich
ändert.

**Acceptance Scenarios**:

1. **Given** ein User hat drei ausgewertete Tipps, von denen genau einer exakt war, **When** `/rangliste` aufgerufen wird, **Then** weist die Spalte "exakte Treffer" für diesen User **1** aus.
2. **Given** zwei User mit identischer Gesamtpunktzahl, aber unterschiedlicher Anzahl exakter Treffer, **When** `/rangliste` aufgerufen wird, **Then** steht der User mit mehr exakten Treffern weiter oben.
3. **Given** die exakte-Treffer-Zahl wird ausschließlich aus dem Vergleich Tipp gegen tatsächliches Ergebnis bestimmt, **When** der Höchstpunktwert des Schemas (heute 4) sich künftig ändert, **Then** bleibt die ausgewiesene Anzahl exakter Treffer unverändert korrekt.

---

### User Story 3 - Einmalige rückwirkende Neuberechnung bestehender Auswertungen (Priority: P1)

Alle bereits nach dem alten 3/1/0-Schema ausgewerteten Tipps sollen einmalig nach
dem neuen CHECK24-Schema neu berechnet und überschrieben werden, damit Alt- und
Neudaten konsistent in derselben Skala stehen und die Rangliste nicht aus
gemischten Schemata aggregiert.

**Why this priority**: Ohne die Migration entstehen inkonsistente Punktestände
(manche Spiele 3/1/0, manche 4/3/2/0), was die Rangliste verfälscht. Da bereits
Spiele der laufenden WM ausgewertet sein können, ist dies für die Korrektheit
kritisch.

**Independent Test**: Ein Datenbestand mit nach altem Schema ausgewerteten Tipps
wird durch den Mechanismus geführt; danach entsprechen alle Punktwerte exakt dem,
was die neue Bewertungslogik für denselben Tipp/Endstand liefert, und die
Gesamtpunkte je User ändern sich entsprechend.

**Acceptance Scenarios**:

1. **Given** ein bereits ausgewerteter Tipp mit altem Wert 3 (exaktes Ergebnis), **When** die Neuberechnung läuft, **Then** steht der Wert danach auf **4**.
2. **Given** ein bereits ausgewerteter Tipp mit altem Wert 1 (richtige Tendenz), **When** die Neuberechnung läuft, **Then** steht der Wert danach auf **2** oder **3** je nachdem, ob zusätzlich die Tordifferenz stimmt.
3. **Given** der Mechanismus wurde bereits einmal ausgeführt, **When** er erneut ausgelöst wird, **Then** verändert ein zweiter Durchlauf die bereits korrekt nach neuem Schema stehenden Werte nicht (idempotent).
4. **Given** ein Spiel ist noch nicht ausgewertet (`evaluated = false`), **When** die Neuberechnung läuft, **Then** werden dessen Tipps nicht angefasst.

---

### Edge Cases

- **Richtige Differenz vs. exakt bei Unentschieden**: Tipp 2:2 / Endstand 1:1 ist *nicht* exakt, hat aber dieselbe Tordifferenz (0) → 3 Punkte. Tipp 1:1 / Endstand 1:1 ist exakt → 4 Punkte. Die Stufen dürfen sich nicht überschneiden, weil sie spezifisch-zu-allgemein in fester Reihenfolge geprüft werden und die erste zutreffende gewinnt.
- **Torreiches Unentschieden vs. torloses**: Tipp 0:0 / Endstand 3:3 → 3 Punkte (richtige Differenz 0, richtige Tendenz Remis, nicht exakt).
- **Differenz gleich, aber Tendenz gedreht**: kann nicht eintreten — gleiche vorzeichenbehaftete Differenz impliziert gleiche Tendenz. Die Differenz-Prüfung muss daher das **Vorzeichen** berücksichtigen (Tipp 0:2 bei Endstand 2:0 hat NICHT die richtige Differenz, sondern falsche Tendenz → 0 Punkte).
- **Fehlender Endstand**: Ein Tipp kann nur bewertet werden, wenn ein gültiger Endstand vorliegt (Spiel `FINISHED`/`evaluated`). Spiele ohne Endstand bleiben unbewertet.
- **Wiederholter Migrationslauf**: Der rückwirkende Mechanismus muss mehrfach gefahrlos auslösbar sein, ohne Werte zu verfälschen (siehe US3 #3).
- **Leaderboard ohne ausgewertete Spiele**: Ein User ohne ausgewertete Tipps erscheint mit 0 Punkten und 0 exakten Treffern, nicht als Fehler.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Das System MUSS jeden Tipp eines ausgewerteten Spiels nach folgender vierstufiger Staffelung bewerten:
  - **4 Punkte** — exaktes Ergebnis (getippte Heim- und Auswärtstore == tatsächliche).
  - **3 Punkte** — richtige (vorzeichenbehaftete) Tordifferenz, aber nicht exakt; schließt Unentschieden mit falscher Höhe ein (z. B. Tipp 2:2 bei Endstand 1:1).
  - **2 Punkte** — richtige Tendenz (Heimsieg, Auswärtssieg oder Unentschieden), aber weder exaktes Ergebnis noch richtige Tordifferenz.
  - **0 Punkte** — falsche Tendenz.
- **FR-002**: Die Bewertung MUSS die Stufen von spezifisch zu allgemein prüfen (1. exakt, 2. Tordifferenz, 3. Tendenz) und die **erste zutreffende Stufe** vergeben; spätere Stufen dürfen eine bereits getroffene nicht überschreiben.
- **FR-003**: Die Tordifferenz-Prüfung MUSS vorzeichenbehaftet sein (Heim minus Auswärts), sodass eine gespiegelte Differenz (z. B. Tipp 0:2 bei Endstand 2:0) nicht als richtige Differenz, sondern gemäß Tendenz bewertet wird.
- **FR-004**: Alle Spiele MÜSSEN gleich gewertet werden; es darf **keine** Phasen- oder Gewichtungsfaktoren geben (kein Bonus für K.-o.-Runde o. Ä.).
- **FR-005**: Das System MUSS das bisherige 3/1/0-Schema in der Auto-Auswertung (F5) vollständig durch das neue 4/3/2/0-Schema ersetzen; nach Abpfiff ausgewertete neue Spiele MÜSSEN das neue Schema verwenden.
- **FR-006**: Die Statistik "Anzahl exakt richtig getippter Spiele" je User MUSS direkt aus dem Vergleich Tipp gegen tatsächliches Ergebnis berechnet werden und DARF NICHT aus dem Punktwert (z. B. `points == 4`) abgeleitet werden.
- **FR-006a**: Die Anzahl exakter Treffer MUSS bei jedem Ranglisten-Abruf **live** aus dem Vergleich Tipp gegen Endstand der ausgewerteten Spiele ermittelt werden; es DARF KEIN zusätzliches persistiertes "war exakt"-Feld eingeführt werden (kein Schema-Zusatz für diese Statistik).
- **FR-007**: Das Leaderboard (F6, `/rangliste`) MUSS die nach FR-006/FR-006a berechnete Anzahl exakter Treffer als Zusatzspalte und als Tie-Breaker bei Punktgleichstand verwenden.
- **FR-008**: Das System MUSS einen rückwirkenden Neuberechnungs-Mechanismus bereitstellen, der die Punkte **aller bereits ausgewerteten Tipps** (`evaluated = true`) nach dem neuen Schema neu berechnet und die gespeicherten Punktwerte überschreibt.
- **FR-008a**: Der rückwirkende Mechanismus MUSS **automatisch beim Start der Anwendung** ablaufen (Startup-Runner) und DARF KEINE zusätzliche Bedienoberfläche oder manuellen Auslöseschritt erfordern. Da der Lauf nach FR-009 idempotent ist, ist ein Lauf bei jedem Start unschädlich.
- **FR-009**: Der rückwirkende Mechanismus MUSS idempotent sein: ein wiederholter Lauf darf bereits nach neuem Schema korrekte Werte nicht verändern.
- **FR-010**: Der rückwirkende Mechanismus DARF noch nicht ausgewertete Spiele/Tipps (`evaluated = false`) nicht verändern.
- **FR-011**: Die Auswertungslogik MUSS so gekapselt sein, dass dieselbe Bewertungsfunktion sowohl von der laufenden Auto-Auswertung (F5) als auch vom rückwirkenden Mechanismus genutzt wird (eine einzige Quelle der Wahrheit für die Punktevergabe).
- **FR-012**: Die Spezifikationsdokumentation (`wm-tippspiel-bot-spec.md`, Abschnitte F5 und F6) MUSS auf das neue Schema und die unabhängige Berechnung der exakten Treffer aktualisiert werden.
- **FR-013**: Die in der Auswertung gepostete Ergebnis-/Punkte-Übersicht MUSS die nach neuem Schema vergebenen Punkte ausweisen.

### Key Entities *(include if feature involves data)*

- **Tipp**: Vorhergesagtes Ergebnis eines Users für ein Spiel (getippte Heim-/Auswärtstore, vergebene Punkte, Auswertungs-Zeitbezug über das zugehörige Spiel). Der Punktwert wird durch die Bewertungslogik gesetzt/überschrieben.
- **Spiel**: Begegnung mit tatsächlichem Endstand (Heim-/Auswärtstore), Status und Flag, ob bereits ausgewertet. Liefert die Vergleichsbasis für die Bewertung.
- **Bewertungsergebnis (logisch)**: Abbildung (Tipp, Endstand) → Punktstufe {4,3,2,0} plus boolesches "war exakt", das die exakte-Treffer-Statistik speist.
- **Leaderboard-Eintrag (logisch)**: Aggregation je User aus Gesamtpunkten, Anzahl abgegebener Tipps und Anzahl exakter Treffer (Tie-Breaker).

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Für eine repräsentative Matrix aus Tipp/Endstand-Kombinationen (mindestens je ein Fall pro Stufe inkl. der Unentschieden- und Vorzeichen-Grenzfälle) vergibt die Auswertung in **100 %** der Fälle die erwartete Punktstufe (4/3/2/0).
- **SC-002**: Die im Leaderboard ausgewiesene Anzahl exakter Treffer stimmt für **100 %** der User mit der direkt aus Tipp-vs-Ergebnis ermittelten Anzahl überein und bleibt unverändert, wenn der Höchstpunktwert des Schemas hypothetisch geändert wird.
- **SC-003**: Nach einem Lauf des rückwirkenden Mechanismus entsprechen **100 %** der zuvor ausgewerteten Tipps dem Wert, den die neue Bewertungslogik für denselben Tipp/Endstand liefert.
- **SC-004**: Ein zweiter Lauf des rückwirkenden Mechanismus ändert **0** Punktwerte (Idempotenz nachweisbar).
- **SC-005**: Noch nicht ausgewertete Spiele bleiben durch den rückwirkenden Lauf zu **100 %** unverändert (keine vorzeitige Punktvergabe).

## Assumptions

- Tipps und Endstände liegen als getrennte Heim-/Auswärts-Torzahlen vor; daraus lassen sich Tendenz (Vorzeichen der Differenz) und Tordifferenz eindeutig ableiten.
- "Tendenz" umfasst drei Ausprägungen: Heimsieg, Auswärtssieg, Unentschieden. Gleiche Tendenz bedeutet, dass Tipp und Endstand zur selben dieser drei Klassen gehören.
- Es gibt keine Sonderregeln für Verlängerung/Elfmeterschießen in der Punktevergabe; bewertet wird der für das Spiel gespeicherte Endstand (90/120 Minuten gemäß bestehender Datenhaltung).
- Der rückwirkende Mechanismus läuft automatisch beim App-Start; nach dem ersten Start nach Deployment sind alle Werte korrekt, weitere Starts ändern dank Idempotenz nichts. Eine nutzer- oder adminseitige Bedienoberfläche ist nicht erforderlich.
- Die bestehende Persistenz für `tips.points`, Endstände und das `evaluated`-Flag bleibt unverändert nutzbar; es wird keine Schemaänderung vorausgesetzt.
- Die Punktespanne (max. 4) hat keine Obergrenzen-Konflikte mit bestehender Anzeige/Aggregation.
