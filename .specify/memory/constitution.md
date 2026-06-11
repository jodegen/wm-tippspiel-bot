<!--
SYNC IMPACT REPORT
==================
Version change: (template, unversioned) → 1.0.0
Bump rationale: Initial ratification of a concrete constitution from the
  placeholder template. First adoption ⇒ MAJOR baseline 1.0.0.

Principles defined (all new):
  - I.   Festgelegter Technologie-Stack (Java 21 / Spring Boot 3.x)
  - II.  Schema-Änderungen nur über Liquibase-Changesets
  - III. Test-First für Kernlogik (NON-NEGOTIABLE)
  - IV.  Zeitbehandlung: UTC speichern, Europe/Berlin anzeigen
  - V.   Discord-Integration über JDA mit dauerhafter Gateway-Verbindung

Sections defined (all new):
  - Technologie- & Datenstandards
  - Entwicklungs-Workflow & Qualitätsgates
  - Governance

Templates reviewed for alignment:
  - .specify/templates/plan-template.md ........ ✅ compatible (Constitution
    Check gate is generic; principles map cleanly to its gates)
  - .specify/templates/spec-template.md ........ ✅ compatible (no constitution
    -mandated section added/removed)
  - .specify/templates/tasks-template.md ....... ✅ compatible (tests are
    OPTIONAL there; Principle III makes them MANDATORY for core logic only —
    enforced at plan/tasks authoring time, no template edit required)

Follow-up TODOs: none. RATIFICATION_DATE set to first adoption (2026-06-11).
-->

# WM-Tippspiel Constitution

## Core Principles

### I. Festgelegter Technologie-Stack

Das Backend MUSS in Java 21 unter Verwendung von Spring Boot 3.x implementiert
werden. Persistente Daten werden in PostgreSQL gehalten. Abweichungen vom
Stack (andere JVM-Sprachversion, anderes Web-Framework, andere primäre
Datenbank) sind nur zulässig, wenn sie in der Complexity-Tracking-Tabelle des
Plans begründet und im Review genehmigt werden.

**Rationale**: Ein einheitlicher, bekannter Stack reduziert Wartungslast,
ermöglicht geteiltes Wissen im Team und verhindert Wildwuchs an Frameworks in
einem Hobby-/Community-Projekt.

### II. Schema-Änderungen nur über Liquibase-Changesets

Jede Änderung am Datenbankschema MUSS über ein Liquibase-Changeset erfolgen.
Manuelle DDL-Eingriffe (CREATE/ALTER/DROP außerhalb von Changesets), `ddl-auto`
mit schemaverändernden Werten (`create`, `create-drop`, `update`) sowie
nachträgliches Editieren bereits angewandter Changesets sind VERBOTEN.
Korrekturen erfolgen ausschließlich durch neue, additive Changesets.

**Rationale**: Versionierte, unveränderliche Migrationen garantieren
reproduzierbare Umgebungen, nachvollziehbare Historie und gefahrlose Rollouts.

### III. Test-First für Kernlogik (NON-NEGOTIABLE)

Für die Kernlogik — insbesondere **Punktewertung** und **Reveal-Timing** —
sind automatisierte Tests PFLICHT. Diese Logik MUSS test-getrieben entwickelt
werden: Tests werden zuerst geschrieben, müssen zunächst fehlschlagen und erst
danach wird implementiert (Red-Green-Refactor). Eine Änderung an Punktewertung
oder Reveal-Timing DARF NICHT gemergt werden, wenn die zugehörigen Tests fehlen
oder fehlschlagen. Tests für Logik außerhalb dieses Kerns sind empfohlen, aber
nicht verpflichtend.

**Rationale**: Punktewertung und Reveal-Timing bestimmen Fairness und Vertrauen
ins Spiel; Fehler dort sind teuer und schwer rückgängig zu machen. Tests sind
die einzige belastbare Absicherung gegen Regressionen.

### IV. Zeitbehandlung: UTC speichern, Europe/Berlin anzeigen

Anstoßzeiten und alle zeitkritischen Zeitpunkte MÜSSEN in der Datenbank in UTC
gespeichert werden. Umrechnung in die Zeitzone `Europe/Berlin` erfolgt
ausschließlich an der Darstellungs-/Ausgabegrenze (Discord-Nachrichten, API,
UI). Geschäftslogik (z. B. Reveal-Timing-Entscheidungen) MUSS auf UTC rechnen;
lokale Zeit DARF NICHT persistiert oder als Vergleichsbasis in der Logik
verwendet werden.

**Rationale**: UTC als einzige Wahrheit verhindert Fehler durch Sommer-/
Winterzeit und Zeitzonen-Drift; eine einzige Umrechnungsgrenze hält das
Verhalten vorhersehbar.

### V. Discord-Integration über JDA mit dauerhafter Gateway-Verbindung

Die Discord-Anbindung MUSS über JDA (Java Discord API) mit einer dauerhaften
Gateway-Verbindung realisiert werden. Funktionen, die auf Discord-Ereignisse
reagieren, MÜSSEN ereignisgetrieben über die Gateway-Verbindung arbeiten statt
über Polling. Die Verbindung MUSS Reconnects überstehen, ohne Tipps, Wertungen
oder Reveal-Zustände zu verlieren oder doppelt zu verarbeiten.

**Rationale**: Eine dauerhafte Gateway-Verbindung liefert zeitnahe Interaktion
mit minimaler Latenz und ist die von Discord vorgesehene, ratenlimit-schonende
Betriebsweise für Bots.

## Technologie- & Datenstandards

- Sprache/Laufzeit: Java 21 (LTS); Framework: Spring Boot 3.x.
- Datenbank: PostgreSQL; Schema-Verwaltung ausschließlich via Liquibase.
- Discord: JDA mit persistenter Gateway-Verbindung.
- Zeit: Persistenz in UTC, Anzeige in `Europe/Berlin`.
- Konfiguration (Tokens, Datenbank-Credentials, Zeitzonen-Defaults) MUSS über
  Umgebung/externe Konfiguration erfolgen; Geheimnisse gehören NICHT ins
  Repository.

## Entwicklungs-Workflow & Qualitätsgates

- Jede Feature-Arbeit folgt dem Spec-Kit-Fluss: Spec → Plan → Tasks →
  Implementierung.
- Der **Constitution Check** im Plan MUSS vor Phase 0 bestehen und nach dem
  Design erneut geprüft werden. Verstöße sind in der Complexity-Tracking-
  Tabelle zu begründen oder zu beheben.
- Schemaänderungen erscheinen als eigene Liquibase-Changeset-Tasks und werden
  separat reviewt.
- Pull Requests, die Punktewertung oder Reveal-Timing berühren, MÜSSEN
  passierende Tests gemäß Prinzip III enthalten; ohne diese kein Merge.
- Code-Reviews verifizieren die Einhaltung aller Prinzipien dieser Verfassung.

## Governance

Diese Verfassung hat Vorrang vor allen anderen Entwicklungspraktiken im
Projekt. Bei Konflikten zwischen dieser Verfassung und anderer Dokumentation
gilt die Verfassung.

Änderungen an der Verfassung MÜSSEN dokumentiert, im Review genehmigt und mit
einem Sync-Impact-Report versehen werden, der abhängige Templates und Artefakte
auf Konsistenz prüft. Versionierung folgt Semantic Versioning:

- **MAJOR**: Rückwärtsinkompatible Entfernung oder Neudefinition von Prinzipien
  bzw. Governance-Regeln.
- **MINOR**: Neues Prinzip/Abschnitt oder materiell erweiterte Vorgabe.
- **PATCH**: Klarstellungen, Wortlaut, Tippfehler, nicht-semantische Schärfungen.

Compliance wird in jedem Plan (Constitution Check) und in jedem Code-Review
überprüft. Begründete Ausnahmen werden in der Complexity-Tracking-Tabelle des
betroffenen Plans festgehalten.

**Version**: 1.0.0 | **Ratified**: 2026-06-11 | **Last Amended**: 2026-06-11
