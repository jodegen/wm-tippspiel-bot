# Feature Specification: Website-Hinweise in Discord-Ausgaben

**Feature Branch**: `009-website-hints`

**Created**: 2026-06-19

**Status**: Draft

**Input**: User description: "Füge ein kleines Feature hinzu: Hinweise auf die öffentliche Website wm.xenoria.de an den passenden Stellen der Discord-Ausgaben. Konkret: (1) Im Footer des Leaderboard-Boards (F11) ein dezenter Hinweis 'Vollständige Tabelle auf wm.xenoria.de'. (2) Am Ende der /profil- und /rangliste-Antworten ein Link zur ausführlicheren Web-Ansicht (bei /profil ggf. direkt zur Profil-URL des Users). (3) Die Website-Basis-URL als Konfigurationswert (application.yml/ENV), nicht hartcodiert, damit sie zentral änderbar ist. Keine neuen Channels oder automatischen Posts — nur Ergänzungen an bestehenden Ausgaben."

## Clarifications

### Session 2026-06-19

- Q: Pfadschema der Web-Profilseite (Basis-URL + öffentlicher Identifier)? → A: `https://wm.xenoria.de/profil/{publicId}`
- Q: Ziel-Pfad für die vollständige Web-Tabelle (Board-Footer & /rangliste)? → A: `https://wm.xenoria.de/leaderboard`

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Leaderboard-Board verweist auf die vollständige Web-Tabelle (Priority: P1)

Das selbst-aktualisierende Leaderboard-Board (F11) zeigt nur die Top-N-Plätze. Ein
Mitglied, das die vollständige Tabelle oder mehr Details sehen möchte, soll am Board
selbst einen dezenten Hinweis auf die öffentliche Website finden, ohne dass jemand den
Link manuell posten muss.

**Why this priority**: Das Board ist die sichtbarste, dauerhaft gepinnte Ausgabe und
erreicht alle Mitglieder passiv. Es ist der wirkungsvollste Ort für den Verweis und
liefert für sich genommen bereits den Kernnutzen des Features.

**Independent Test**: Board rendern lassen und prüfen, dass der Footer den Hinweistext
mit der konfigurierten Website-Domain enthält — unabhängig von /profil und /rangliste
testbar.

**Acceptance Scenarios**:

1. **Given** ein Leaderboard-Board mit mindestens einer gewerteten Zeile, **When** das
   Board gerendert wird, **Then** enthält der Footer einen dezenten Hinweis im Sinne von
   „Vollständige Tabelle auf wm.xenoria.de“ mit der zentral konfigurierten Domain.
2. **Given** das Board ist leer (noch keine Tipps gewertet), **When** es gerendert wird,
   **Then** bleibt der Website-Hinweis im Footer erhalten (er hängt nicht von Tipp-Daten ab).

---

### User Story 2 - /profil verlinkt die ausführliche Web-Profilansicht (Priority: P2)

Wer `/profil` (für sich oder einen anderen Nutzer) aufruft, soll am Ende der Antwort
einen Link zur ausführlicheren Web-Ansicht erhalten — wenn möglich direkt zur Profilseite
genau dieses Nutzers, sodass kein weiteres Suchen auf der Website nötig ist.

**Why this priority**: Verbindet die Discord-Schnellansicht mit der detaillierten
Web-Ansicht und nutzt den bereits existierenden stabilen öffentlichen Spieler-Identifier.

**Independent Test**: `/profil` für einen bekannten Nutzer ausführen und prüfen, dass die
Antwort einen Link enthält, der auf die Profilseite dieses Nutzers auf der konfigurierten
Domain zeigt.

**Acceptance Scenarios**:

1. **Given** ein Nutzer mit gewerteten Tipps, **When** `/profil` für ihn aufgerufen wird,
   **Then** endet die Antwort mit einem Link zur Web-Profilansicht, der den stabilen
   öffentlichen Identifier dieses Nutzers enthält.
2. **Given** `/profil @anderer-nutzer`, **When** der Befehl ausgeführt wird, **Then**
   zeigt der Link auf die Profilseite des angegebenen Nutzers, nicht auf den Aufrufer.
3. **Given** ein Nutzer ohne abgegebene Tipps, **When** `/profil` aufgerufen wird, **Then**
   wird weiterhin ein Link angeboten (mindestens zur allgemeinen Web-Ansicht), ohne Fehler.

---

### User Story 3 - /rangliste verlinkt die ausführliche Web-Tabelle (Priority: P3)

Wer `/rangliste` aufruft, soll am Ende der Antwort einen Link zur vollständigen
Web-Tabelle erhalten, da der Discord-Befehl nur eine begrenzte Ansicht zeigt.

**Why this priority**: Sinnvolle Ergänzung, aber inhaltlich redundant zum Board-Hinweis
(US1); daher niedrigste Priorität.

**Independent Test**: `/rangliste` ausführen und prüfen, dass die Antwort mit einem Link
zur Web-Tabelle auf der konfigurierten Domain endet.

**Acceptance Scenarios**:

1. **Given** eine nicht-leere Rangliste, **When** `/rangliste` aufgerufen wird, **Then**
   endet die Antwort mit einem Link zur ausführlicheren Web-Tabelle.
2. **Given** eine leere Rangliste, **When** `/rangliste` aufgerufen wird, **Then** wird
   die bestehende Leer-Meldung gezeigt; der Website-Hinweis ist optional, bricht aber
   nichts.

---

### Edge Cases

- **Website-Basis-URL nicht konfiguriert (leer/fehlt)**: Die Ausgaben werden ohne
  Website-Hinweis gerendert (kein leerer/kaputter Link, kein Fehler, keine Platzhalter-URL).
- **Trailing Slash / Formatierung der Basis-URL**: Konfigurierte Basis-URL mit oder ohne
  abschließenden Slash führt zu einem korrekt zusammengesetzten Link (keine doppelten
  Slashes, kein fehlender Trenner).
- **/profil für einen Nutzer ohne stabilen öffentlichen Identifier ableitbar**: Es wird
  auf die allgemeine Web-Ansicht zurückgefallen statt eine kaputte Profil-URL zu zeigen.
- **Lange Hinweistexte vs. Embed-/Footer-Limits**: Der Hinweis ist kurz genug, um die
  Discord-Limits nicht zu sprengen und bestehende Inhalte nicht zu verdrängen.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Das Leaderboard-Board (F11) MUSS einen dezenten Hinweis auf die öffentliche
  Website im Footer anzeigen, der zur vollständigen Tabelle (`{Basis-URL}/leaderboard`)
  verweist. (Hinweis: Discord-Embed-Footer sind reiner Text und nicht klickbar — der
  Hinweis nennt die Adresse als Text.)
- **FR-002**: Die `/profil`-Antwort MUSS am Ende einen Link zur ausführlicheren
  Web-Profilansicht enthalten.
- **FR-003**: Der `/profil`-Link MUSS, sofern ein stabiler öffentlicher Spieler-Identifier
  für den betreffenden Nutzer ableitbar ist, direkt auf die Profilseite genau dieses
  Nutzers zeigen, gebildet als `{Basis-URL}/profil/{publicId}` (bei `/profil @user` auf den
  angegebenen Nutzer, nicht auf den Aufrufer).
- **FR-004**: Die `/rangliste`-Antwort MUSS am Ende einen Link zur ausführlicheren
  Web-Tabelle (`{Basis-URL}/leaderboard`) enthalten.
- **FR-005**: Die Website-Basis-URL MUSS ein zentral änderbarer Konfigurationswert sein
  (über application.yml bzw. Umgebungsvariable), nicht im Code hartkodiert.
- **FR-006**: Ist die Website-Basis-URL nicht konfiguriert (leer/fehlend), MÜSSEN alle
  betroffenen Ausgaben weiterhin fehlerfrei gerendert werden — der Website-Hinweis bzw.
  Link wird dann ausgelassen.
- **FR-007**: Das Feature DARF KEINE neuen Channels und KEINE neuen oder automatischen
  Posts/Nachrichten erzeugen; es ergänzt ausschließlich bestehende Ausgaben.
- **FR-008**: Zusammengesetzte Links MÜSSEN unabhängig davon korrekt sein, ob die
  konfigurierte Basis-URL mit oder ohne abschließenden Slash hinterlegt ist.
- **FR-009**: Der `/profil`-Link MUSS denselben stabilen, nicht-sensiblen öffentlichen
  Spieler-Identifier verwenden wie die öffentliche Web-/API-Ansicht, sodass Discord-Link
  und Website auf dieselbe Profilseite zeigen.
- **FR-010**: Die Hinweistexte und Links MÜSSEN so kurz/dezent sein, dass sie bestehende
  Inhalte nicht verdrängen und die Discord-Embed-Limits nicht verletzen.

### Key Entities *(include if feature involves data)*

- **Website-Basis-URL**: Zentral konfigurierter Ursprung der öffentlichen Website (z. B.
  `https://wm.xenoria.de`). Grundlage für alle erzeugten Hinweise und Links.
- **Öffentlicher Spieler-Identifier**: Bereits existierender stabiler, nicht zurück­rechen­barer
  Identifier eines Nutzers (aus Feature 008). Wird zum Bilden der direkten Profil-URL
  wiederverwendet; keine Discord-IDs oder sonstigen sensiblen Daten in den Links.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100 % der gerenderten Leaderboard-Boards mit konfigurierter Basis-URL
  enthalten den Website-Hinweis im Footer.
- **SC-002**: Ein Mitglied gelangt von `/profil` mit höchstens einem Klick zur Web-Ansicht
  des korrekten Nutzers (direkter Profil-Link, wenn Identifier ableitbar).
- **SC-003**: Bei nicht konfigurierter Basis-URL treten in 0 % der Ausgaben Fehler oder
  sichtbar kaputte/leere Links auf.
- **SC-004**: Es werden durch dieses Feature 0 neue Channels und 0 zusätzliche
  automatische Nachrichten erzeugt.
- **SC-005**: Die Website-Domain lässt sich ausschließlich über Konfiguration ändern
  (keine Code-Änderung nötig), verifizierbar durch Wechsel des Konfigurationswerts.

## Assumptions

- Die öffentliche Website ist unter `wm.xenoria.de` erreichbar; der konkrete Wert kommt
  aus der Konfiguration und ist nicht Teil der Code-Logik.
- Die Profilseiten der Website sind über den bereits existierenden öffentlichen
  Spieler-Identifier (Feature 008) adressierbar; das Pfadschema ist `{Basis-URL}/profil/{publicId}`
  (siehe Clarifications).
- Betroffen sind ausschließlich die genannten Ausgaben: Leaderboard-Board (F11),
  `/profil` (F13) und `/rangliste`. Andere Embeds (Spielplan, Reveal, Recap usw.) bleiben
  unverändert.
- Die Hinweise sind rein informativ/navigierend; es werden keine sensiblen Daten
  (Discord-IDs, E-Mails, Tokens) in Links oder Texten offengelegt.
- Es wird kein neues Datenbank-Schema und keine neue Persistenz benötigt; das Feature ist
  additiv zu bestehenden Ausgaben und Konfiguration.
