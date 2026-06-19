# Feature Specification: Öffentliche Read-only-API

**Feature Branch**: `008-public-readonly-api`

**Created**: 2026-06-19

**Status**: Draft

**Input**: User description: "Füge ein neues Feature hinzu: öffentliche, rein lesende REST-Endpoints, die die Daten des WM-Tippspiels für eine externe Read-only-Website bereitstellen. KEINE Schreibvorgänge, KEINE Tippabgabe über diese Endpoints. Die Website ist komplett öffentlich und ohne Authentifizierung. (Spielplan, Leaderboard, Spielerprofil, Tipps pro Spiel nur nach Anpfiff, Live-Spiele; harte Reveal-Sicherheit; Datenschutz: keine sensiblen Felder nach außen.)"

## Clarifications

### Session 2026-06-19

- Q: Strategie für den stabilen öffentlichen Spieler-Identifier? → A: Deterministischer, nicht zurückrechenbarer Hash/HMAC der `user_id` (serverseitiges Secret), zur Laufzeit abgeleitet — keine Persistenz, keine Schema-Änderung.
- Q: Maßgebliches „angepfiffen"-Signal für die serverseitige Reveal-Prüfung? → A: Konservativ — Tipps nur, wenn `now() (UTC) ≥ kickoff` UND `revealed = true` (beide Bedingungen müssen erfüllt sein).
- Q: Schutz der offenen Endpoints vor Missbrauchslast? → A: Kurzes serverseitiges Response-Caching (wenige Sekunden) verbindlich; explizites Rate-Limiting bleibt Detail der Planungsphase.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Öffentlicher Spielplan & Live-Spiele (Priority: P1)

Ein anonymer Website-Besucher ruft den vollständigen Spielplan ab, um Begegnungen,
Anstoßzeiten, übertragende TV-Sender, Quoten, Gruppen-/Phasenzuordnung sowie –
sofern vorhanden – Ergebnisse und den aktuellen Spielstatus zu sehen. Für gerade
laufende Spiele sieht er den aktuellen Zwischenstand.

**Why this priority**: Der Spielplan ist die Basisdatenmenge der externen Website
und enthält ausschließlich unbedenkliche, öffentlich verfügbare Informationen
(Begegnungen, Zeiten, Sender, Quoten, Ergebnisse). Er liefert sofort Mehrwert,
unabhängig von allen anderen Stories, und enthält keine reveal-kritischen Daten.

**Independent Test**: Spielplan-Endpoint ohne Authentifizierung aufrufen und
prüfen, dass alle Spiele mit den geforderten Feldern (Begegnung, Anstoßzeit in
UTC, TV-Sender, Quote, Ergebnis, Status, Gruppe/Phase) geliefert werden; den
Live-Endpoint aufrufen und prüfen, dass nur laufende Spiele mit aktuellem Stand
erscheinen.

**Acceptance Scenarios**:

1. **Given** ein gefüllter Spielplan, **When** ein anonymer Client den
   Spielplan-Endpoint aufruft, **Then** erhält er alle Spiele mit Begegnung,
   Anstoßzeit (UTC), TV-Sender, Quote, Ergebnis (falls vorhanden), Status und
   Gruppe/Phase – ohne Authentifizierung.
2. **Given** ein noch nicht angepfiffenes Spiel, **When** der Spielplan
   abgerufen wird, **Then** ist das Ergebnis leer/null und der Status zeigt
   "geplant", aber das Spiel selbst (Begegnung, Zeit, Sender, Quote) ist sichtbar.
3. **Given** mindestens ein gerade laufendes Spiel, **When** der Live-Endpoint
   aufgerufen wird, **Then** werden ausschließlich laufende Spiele mit aktuellem
   Zwischenstand zurückgegeben.
4. **Given** kein laufendes Spiel, **When** der Live-Endpoint aufgerufen wird,
   **Then** wird eine leere Liste zurückgegeben (kein Fehler).
5. **Given** ein Filterwunsch (z. B. nach Gruppe, Phase oder Spieltag), **When**
   der Spielplan mit Filterparametern abgerufen wird, **Then** werden nur die
   passenden Spiele zurückgegeben; ohne Filter wird der vollständige Plan geliefert.

---

### User Story 2 - Öffentliches Leaderboard (Priority: P2)

Ein anonymer Besucher ruft die vollständige Rangliste ab, um zu sehen, wer wie
viele Punkte hat, wie viele exakte Treffer erzielt wurden und wie sich die Ränge
seit der letzten Auswertung verändert haben.

**Why this priority**: Das Leaderboard ist das zentrale Wettkampf-Element des
Tippspiels und hoher Anziehungspunkt der Website, baut aber auf der bestehenden
Ranking-Logik auf und ist gegenüber dem Spielplan nachgelagert.

**Independent Test**: Leaderboard-Endpoint ohne Authentifizierung aufrufen und
prüfen, dass jede Zeile Anzeigename, Punkte, Anzahl exakter Treffer und
Rang-Veränderung enthält, sortiert nach Rang – und dass keine Discord-ID oder
sonstigen sensiblen Felder enthalten sind.

**Acceptance Scenarios**:

1. **Given** ausgewertete Spiele, **When** ein anonymer Client das Leaderboard
   abruft, **Then** erhält er die vollständige Rangliste mit Anzeigename,
   Punkten, Anzahl exakter Treffer und Rang-Veränderung, sortiert nach Rang.
2. **Given** eine Leaderboard-Antwort, **When** das JSON inspiziert wird,
   **Then** enthält es weder Discord-user_id noch E-Mail, Tokens oder interne IDs.
3. **Given** die exakten Treffer, **When** sie gezählt werden, **Then** beruhen
   sie auf dem direkten Vergleich Tipp-Ergebnis (Heim & Auswärts identisch) und
   NICHT auf dem Punktwert.
4. **Given** noch keine ausgewerteten Spiele, **When** das Leaderboard abgerufen
   wird, **Then** wird eine konsistente (ggf. leere oder punktlose) Rangliste
   ohne Fehler geliefert.

---

### User Story 3 - Tipps pro Spiel ausschließlich nach Anpfiff (Priority: P3)

Ein anonymer Besucher ruft die abgegebenen Tipps zu einem bestimmten Spiel ab.
Solange das Spiel noch nicht angepfiffen wurde, dürfen ihm keine fremden
Einzeltipps gezeigt werden; erst ab Anpfiff werden die Tipps sichtbar.

**Why this priority**: Diese Story trägt die harte Sicherheitsanforderung des
Features (kein Vorab-Leak von Tipps), ist aber funktional von Spielplan und
Leaderboard unabhängig und liefert für sich genommen abgrenzbaren Mehrwert.

**Independent Test**: Für ein noch nicht angepfiffenes Spiel den Tipps-Endpoint
aufrufen und sicherstellen, dass keinerlei fremde Einzeltipps in der Antwort
enthalten sind (auch nicht versteckt im JSON); danach für ein angepfiffenes
Spiel aufrufen und prüfen, dass die Tipps geliefert werden.

**Acceptance Scenarios**:

1. **Given** ein Spiel, das noch NICHT angepfiffen ist, **When** ein Client die
   Tipps zu diesem Spiel abruft, **Then** enthält die Antwort KEINE fremden
   Einzeltipps – weder sichtbar noch versteckt im JSON (serverseitige Prüfung
   direkt im Endpoint).
2. **Given** ein Spiel, das bereits angepfiffen ist, **When** ein Client die
   Tipps abruft, **Then** werden die abgegebenen Tipps mit Anzeigename und
   getipptem Ergebnis geliefert – ohne Discord-ID oder sonstige sensible Felder.
3. **Given** ein angepfiffenes Spiel ohne abgegebene Tipps, **When** die Tipps
   abgerufen werden, **Then** wird eine leere Liste zurückgegeben (kein Fehler).
4. **Given** eine nicht existierende Spiel-Referenz, **When** die Tipps abgerufen
   werden, **Then** antwortet das System mit "nicht gefunden" ohne Datenleck.

---

### User Story 4 - Öffentliches Spielerprofil (Priority: P3)

Ein anonymer Besucher öffnet das Profil eines Spielers über einen stabilen,
nicht-sensiblen öffentlichen Identifier und sieht dessen Tipp-Historie,
Statistiken, die Verteilung der Punktstufen (4/3/2/0) sowie den besten und
schlechtesten Tipp.

**Why this priority**: Das Profil vertieft das Erlebnis, ist aber eine
Detailansicht, die auf bereits vorhandener Statistik-/Scoring-Logik aufsetzt und
gegenüber Spielplan und Leaderboard nachgelagert ist.

**Independent Test**: Profil-Endpoint mit einem gültigen öffentlichen Identifier
aufrufen und prüfen, dass Tipp-Historie, aggregierte Statistiken,
Punktstufen-Verteilung sowie bester/schlechtester Tipp geliefert werden – ohne
Discord-ID; danach mit einem ungültigen Identifier aufrufen und "nicht gefunden"
erwarten.

**Acceptance Scenarios**:

1. **Given** ein Spieler mit Tipp-Historie, **When** sein Profil über den
   öffentlichen Identifier abgerufen wird, **Then** erhält der Client
   Anzeigename, Tipp-Historie, aggregierte Statistiken, Punktstufen-Verteilung
   (4/3/2/0) sowie besten und schlechtesten Tipp.
2. **Given** eine Profil-Antwort, **When** das JSON inspiziert wird, **Then**
   enthält es weder die Discord-user_id noch E-Mail, Tokens oder interne IDs;
   der Spieler ist ausschließlich über den öffentlichen Identifier adressierbar.
3. **Given** die Tipp-Historie eines Profils, **When** sie reveal-kritische
   Spiele enthält, **Then** werden Einzeltipps zu noch nicht angepfiffenen
   Spielen NICHT preisgegeben (konsistent mit der Reveal-Regel aus Story 3).
4. **Given** ein Spieler ohne abgegebene Tipps, **When** sein Profil abgerufen
   wird, **Then** werden leere/neutrale Statistiken ohne Division-durch-null-
   Fehler geliefert.
5. **Given** ein unbekannter öffentlicher Identifier, **When** das Profil
   abgerufen wird, **Then** antwortet das System mit "nicht gefunden" ohne
   Hinweise auf interne IDs.

---

### Edge Cases

- **Reveal-Gate vor Anpfiff**: Für ein nicht angepfiffenes Spiel dürfen weder der
  Tipps-pro-Spiel-Endpoint noch die Profil-Tipp-Historie fremde Einzeltipps
  enthalten – auch nicht versteckt in JSON-Feldern, Zähler-Aggregaten, die
  Rückschlüsse erlauben, oder Fehlermeldungen.
- **Spiel exakt am Anpfiff-Zeitpunkt / Lücke vor RevealJob**: „Angepfiffen" gilt
  nur, wenn `now() (UTC) ≥ kickoff` UND `revealed = true`. Im Zeitfenster nach
  Anstoß, aber bevor der RevealJob `revealed = true` gesetzt hat, bleiben die
  Tipps weiterhin verborgen (konservativ, kein Leak).
- **Statistik bei 0 Tipps**: Hit-Rate / Durchschnittswerte dürfen keinen
  Division-durch-null-Fehler erzeugen.
- **Leerer Spielplan / kein Live-Spiel / leeres Leaderboard**: liefert leere
  Listen statt Fehler.
- **Unbekannte Spiel- oder Profil-Referenz**: "nicht gefunden" ohne Preisgabe
  interner IDs oder Existenz-Orakel über sensible Schlüssel.
- **Anzeigenamen-Kollision / Umbenennung**: Der öffentliche Identifier bleibt
  stabil, auch wenn sich der Anzeigename ändert oder mehrere Spieler denselben
  Anzeigenamen führen.
- **Hohe Last durch öffentlichen, unauthentifizierten Zugriff**: Endpoints
  müssen vorhersehbar unter Last antworten (siehe Erfolgskriterien).

## Requirements *(mandatory)*

### Functional Requirements

**Allgemein / Read-only & Datenschutz**

- **FR-001**: Das System MUSS alle in diesem Feature beschriebenen Endpoints
  ausschließlich als lesende GET-Operationen unter einem gemeinsamen öffentlichen
  Pfadpräfix (z. B. `/api/public/`) ohne Authentifizierung bereitstellen.
- **FR-002**: Das System DARF über diese Endpoints KEINE schreibenden Operationen
  zulassen (keine Tippabgabe, keine Änderung, kein Löschen) – weder direkt noch
  indirekt.
- **FR-003**: Das System MUSS sicherstellen, dass keine Antwort sensible Felder
  enthält: insbesondere KEINE Discord-user_id, E-Mail-Adressen, Tokens oder
  interne Surrogat-/Datenbank-Schlüssel mit Personenbezug. Erlaubt sind
  ausschließlich unbedenkliche Felder (Anzeigename, Spiel-/Statistikdaten,
  öffentlicher Identifier). Die **Spiel-/Fixture-ID** (`matches.id`, identisch zur
  öffentlichen football-data-ID) gilt als zulässige öffentliche Stammdaten-
  Referenz und darf als Ressourcenschlüssel ausgeliefert werden; sie ist keine
  personenbezogene interne ID.
- **FR-004**: Das System MUSS bestehende Scoring-/Statistik-Logik ausschließlich
  AUSLESEN und DARF über diese Endpoints nichts neu berechnen oder persistieren.
- **FR-005**: Das System MUSS Zeitpunkte (insbesondere Anstoßzeiten) in den
  Antworten in UTC ausliefern; die Anzeigeformatierung obliegt dem Frontend.

**Spielplan & Live**

- **FR-006**: Das System MUSS einen Spielplan-Endpoint bereitstellen, der je
  Spiel Begegnung (Heim/Auswärts), Anstoßzeit (UTC), TV-Sender, Quote, Ergebnis
  (falls vorhanden), Status und Gruppe/Phase liefert.
- **FR-007**: Das System MUSS den Spielplan vollständig liefern können und MUSS
  zusätzlich Filterung (mindestens nach Phase/Gruppe und/oder Spieltag)
  unterstützen.
- **FR-008**: Das System MUSS einen Live-Endpoint bereitstellen, der
  ausschließlich aktuell laufende Spiele mit aktuellem Zwischenstand zurückgibt
  und bei keinem laufenden Spiel eine leere Liste liefert.

**Leaderboard**

- **FR-009**: Das System MUSS ein Leaderboard-Endpoint bereitstellen, das die
  vollständige Rangliste mit Anzeigename, Punkten, Anzahl exakter Treffer und
  Rang-Veränderung liefert, sortiert nach Rang.
- **FR-010**: Das System MUSS die Anzahl exakter Treffer über den direkten
  Vergleich Tipp-Ergebnis (Heim- UND Auswärtstore identisch) ermitteln und NICHT
  aus dem Punktwert ableiten.
- **FR-011**: Das System MUSS die Rang-Veränderung gegenüber dem zuletzt
  festgehaltenen Rang-Stand ausweisen (Auf-/Abstieg/keine Änderung).

**Tipps pro Spiel (reveal-kritisch)**

- **FR-012**: Das System MUSS die abgegebenen Tipps eines Spiels ausschließlich
  dann ausliefern, wenn das Spiel angepfiffen ist; „angepfiffen" gilt KONSERVATIV
  genau dann, wenn BEIDE Bedingungen erfüllt sind: `now() (UTC) ≥ kickoff` UND
  `revealed = true`. Die Prüfung MUSS serverseitig direkt im Endpoint erfolgen;
  ist nur eine der beiden Bedingungen erfüllt, werden KEINE Einzeltipps geliefert.
- **FR-013**: Das System DARF vor Anpfiff KEINE fremden Einzeltipps eines Spiels
  ausliefern – weder sichtbar noch versteckt im JSON, noch über Aggregate, die
  einzelne Tipps rekonstruierbar machen.
- **FR-014**: Das System MUSS pro Tipp ausschließlich Anzeigename und getipptes
  Ergebnis (sowie ggf. erzielte Punkte des bereits gewerteten Spiels) liefern,
  ohne sensible Felder.

**Spielerprofil**

- **FR-015**: Das System MUSS jeden Spieler über einen stabilen, nicht-sensiblen
  öffentlichen Identifier adressierbar machen, der NICHT die Discord-user_id ist
  und über Umbenennungen des Anzeigenamens hinweg stabil bleibt. Der Identifier
  MUSS als deterministischer, nicht zurückrechenbarer Hash/HMAC der internen
  `user_id` (mit serverseitigem Secret) zur Laufzeit abgeleitet werden; er wird
  NICHT persistiert und erfordert KEINE Schema-Änderung.
- **FR-016**: Das System MUSS ein Profil-Endpoint bereitstellen, das Anzeigename,
  Tipp-Historie, aggregierte Statistiken, Punktstufen-Verteilung (Anzahl der
  Tipps mit 4, 3, 2 und 0 Punkten) sowie besten und schlechtesten Tipp liefert.
- **FR-017**: Das System MUSS in der Profil-Tipp-Historie die Reveal-Regel
  (FR-012/FR-013) wahren: Einzeltipps zu noch nicht angepfiffenen Spielen werden
  nicht preisgegeben.
- **FR-018**: Das System MUSS Profil-Statistiken auch bei null Tipps fehlerfrei
  (ohne Division durch null) als leere/neutrale Werte liefern.

**Fehler- & Randverhalten**

- **FR-019**: Das System MUSS für unbekannte Spiel- oder Profil-Referenzen "nicht
  gefunden" zurückgeben, ohne interne IDs preiszugeben oder die Existenz
  sensibler Schlüssel zu bestätigen.
- **FR-020**: Das System MUSS leere Ergebnismengen (leerer Spielplan, kein
  Live-Spiel, leeres Leaderboard, Spiel ohne Tipps) als leere Listen statt als
  Fehler ausliefern.
- **FR-021**: Das System MUSS die öffentlichen Endpoints durch kurzes
  serverseitiges Response-Caching (Größenordnung wenige Sekunden) gegen
  Missbrauchslast/Scraping absichern, ohne legitime Besucher zu blockieren.
  Explizites Rate-Limiting pro Client ist optional und wird in der Planungsphase
  entschieden.

### Key Entities *(include if feature involves data)*

- **Spiel (öffentliche Sicht)**: Begegnung (Heim/Auswärts), Anstoßzeit (UTC),
  TV-Sender, Quote (Heim/Unentschieden/Auswärts), Ergebnis (Heim-/Auswärtstore,
  falls vorhanden), Status (z. B. geplant/laufend/beendet), Gruppe/Phase,
  Spieltag. Keine internen technischen Verweise nach außen.
- **Live-Spiel (öffentliche Sicht)**: Teilmenge der Spiele mit Status "laufend"
  inkl. aktuellem Zwischenstand.
- **Leaderboard-Eintrag (öffentliche Sicht)**: Rang, Anzeigename, Punkte, Anzahl
  exakter Treffer (aus direktem Ergebnisvergleich), Rang-Veränderung. Kein
  Discord-Identifikator.
- **Spieler (öffentliche Sicht)**: stabiler, nicht-sensibler öffentlicher
  Identifier und Anzeigename; KEINE Discord-user_id, E-Mail oder interne IDs.
- **Tipp (öffentliche Sicht)**: zugehöriges Spiel, Anzeigename des Tippenden,
  getipptes Ergebnis und – bei gewertetem Spiel – erzielte Punkte. Sichtbar nur,
  wenn das zugehörige Spiel bereits angepfiffen ist.
- **Profil-Statistik (öffentliche Sicht)**: aggregierte Kennzahlen über die
  Tipps eines Spielers, Punktstufen-Verteilung (4/3/2/0), bester und
  schlechtester Tipp. Nur lesend aus bestehender Scoring-Logik abgeleitet.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100 % der Antworten aller fünf Endpoints enthalten keine sensiblen
  Felder (keine Discord-user_id, E-Mail, Tokens, interne IDs) – verifizierbar
  über automatisierte Prüfung der Response-Strukturen.
- **SC-002**: In 100 % der Fälle, in denen ein Spiel noch nicht angepfiffen ist,
  enthält die Antwort des Tipps-pro-Spiel-Endpoints (und der Profil-Tipp-
  Historie) keinerlei fremde Einzeltipps – verifizierbar über automatisierte
  Vor-/Nach-Anpfiff-Tests.
- **SC-003**: Ein externer Website-Besucher kann ohne Anmeldung den vollständigen
  Spielplan, das Leaderboard, laufende Spiele, die Tipps eines angepfiffenen
  Spiels sowie ein Spielerprofil abrufen – jeweils in einem einzigen Aufruf.
- **SC-004**: Die Anzahl exakter Treffer im Leaderboard stimmt in 100 % der
  Stichproben mit dem direkten Vergleich Tipp-Ergebnis überein (unabhängig vom
  Punktwert).
- **SC-005**: Alle Endpoints liefern bei typischer Last die angeforderten Daten
  schnell genug für eine flüssige Website-Nutzung (95 % der Abrufe in unter
  1 Sekunde) und antworten bei leeren Datenmengen mit leeren Listen statt Fehlern.
- **SC-006**: Über die Endpoints ist keine schreibende Operation möglich; jeder
  nicht-GET-Versuch wird abgelehnt (verifizierbar über automatisierte Tests).

## Assumptions

- **Spielerbegriff**: Ein "Spieler" wird aktuell ausschließlich implizit über
  abgegebene Tipps repräsentiert (Discord-Identität + Anzeigename); es existiert
  keine separate Spielerverwaltung. Das Feature leitet die öffentliche Sicht aus
  diesen vorhandenen Daten ab.
- **Öffentlicher Identifier** (geklärt 2026-06-19): Der öffentliche Identifier
  wird als deterministischer, nicht zurückrechenbarer Hash/HMAC der
  unveränderlichen internen `user_id` (mit serverseitigem Secret) zur Laufzeit
  abgeleitet – ohne Persistenz und ohne Schema-Änderung. Das Secret gehört in die
  externe Konfiguration (nicht ins Repository).
- **Anzeigename ist veröffentlichbar**: Der vorhandene Anzeigename gilt als
  unbedenkliches, öffentlich anzeigbares Feld; ein Opt-out/Opt-in einzelner
  Spieler ist nicht Teil dieses Features.
- **Rang-Veränderung**: Die Rang-Veränderung nutzt den bereits vorhandenen
  Rang-Stand-/Snapshot-Mechanismus aus dem Leaderboard-Feature (F11); es wird
  kein neuer Snapshot-Mechanismus eingeführt.
- **„Angepfiffen"** (geklärt 2026-06-19): Ein Spiel gilt konservativ genau dann
  als angepfiffen, wenn `now() (UTC) ≥ kickoff` UND `revealed = true`; die
  Prüfung erfolgt auf UTC-Basis direkt im Endpoint.
- **Live-Daten**: Aktuelle Zwischenstände stammen aus den bereits vorhandenen,
  durch das Live-Update vorgehaltenen Spielständen; dieses Feature liest sie nur.
- **Quoten/TV-Sender/Ergebnisse/Status/Gruppe-Phase** sind bereits im Bestand
  vorhanden und werden nur ausgelesen.
- **Betrieb** (teilgeklärt 2026-06-19): Kurzes serverseitiges Response-Caching
  ist verbindlich (siehe FR-021); explizites Rate-Limiting und die Cross-Origin-
  (CORS-)Konfiguration der Website werden in der Planungsphase festgelegt.
- **Bestandsdaten unverändert**: Das Feature kommt ohne neue verpflichtende
  Schema-Änderung an Kerntabellen aus; falls für den öffentlichen Identifier oder
  Caching eine additive Struktur nötig wird, erfolgt sie regelkonform über ein
  Liquibase-Changeset (Entscheidung in der Planungsphase).
