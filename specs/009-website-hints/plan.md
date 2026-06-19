# Implementation Plan: Website-Hinweise in Discord-Ausgaben

**Branch**: `009-website-hints` | **Date**: 2026-06-19 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/009-website-hints/spec.md`

## Summary

Additive, rein darstellungsseitige Ergänzung dreier bestehender Discord-Ausgaben um
Verweise auf die öffentliche Website: (1) Footer des Leaderboard-Boards (F11) mit
dezentem Texthinweis auf `…/leaderboard`, (2) klickbarer Link am Ende der
`/profil`-Antwort zur Profilseite `…/profil/{publicId}` (publicId = derselbe
HMAC-Identifier wie in den Public-Endpoints, **keine** Discord-ID), (3) klickbarer Link
am Ende der `/rangliste`-Antwort zu `…/leaderboard`. Die Website-Basis-URL kommt aus
zentraler Konfiguration (`app.website.base-url` / `WEBSITE_BASE_URL`); ist sie leer,
entfallen Hinweis/Link fehlerfrei. Keine neue Persistenz, kein Schema, keine neuen
Abhängigkeiten, keine neuen Channels oder automatischen Posts.

## Technical Context

**Language/Version**: Java 21 (LTS)

**Primary Dependencies**: Spring Boot 3.x, JDA (vorhandene Embed-Builder), bestehender
`PublicIdService` (Feature 008) für den öffentlichen Identifier. Keine neuen Dependencies.

**Storage**: N/A — keine Persistenz, kein Liquibase-Changeset (rein additive Ausgabe-/
Konfigurationsänderung).

**Testing**: JUnit 5 (vorhandenes Setup); Unit-Tests für die URL-/Footer-Zusammensetzung
und für das Verhalten bei leerer Basis-URL.

**Target Platform**: Linux-Server (Docker-Compose-Betrieb), bestehender Bot-Prozess.

**Project Type**: Single project (Spring-Boot-Bot mit additivem Servlet-Stack aus F008).

**Performance Goals**: Keine — synchroner Stringaufbau bei Embed-Erzeugung, vernachlässigbar.

**Constraints**: Discord-Limits beachten (Embed-Footer = reiner Text, nicht klickbar →
Board-Hinweis als Text; Description/Fields unterstützen Markdown-Links → `/profil` und
`/rangliste` klickbar). Links/Texte kurz halten, bestehende Inhalte nicht verdrängen.

**Scale/Scope**: 3 berührte Ausgaben, 1 neuer Konfigurationswert, 1 kleiner Helfer.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Prinzip | Status | Begründung |
|---------|--------|------------|
| I. Festgelegter Stack (Java 21 / Spring Boot 3.x) | ✅ Pass | Keine Stack-Abweichung, keine neuen Frameworks/Dependencies. |
| II. Schema-Änderungen nur via Liquibase | ✅ Pass | Keine Schema-Änderung, kein Changeset (rein Ausgabe + Konfiguration). |
| III. Test-First für Kernlogik (Punktewertung/Reveal-Timing) | ✅ Pass (n/a) | Feature berührt weder Punktewertung noch Reveal-Timing. Tests für die URL-Helfer werden dennoch ergänzt (empfohlen, nicht Kern). |
| IV. Zeit UTC speichern / Europe/Berlin anzeigen | ✅ Pass | Keine Zeitlogik berührt; bestehender Footer-Timestamp via `Clock` bleibt unverändert. |
| V. Discord über JDA, Gateway-Verbindung | ✅ Pass | Nur Embed-Inhalte erweitert; keine Änderung an Verbindung/Eventflow, keine neuen Posts. |

**Ergebnis**: Alle Gates bestanden, keine Einträge in Complexity Tracking nötig.

## Project Structure

### Documentation (this feature)

```text
specs/009-website-hints/
├── plan.md              # Diese Datei (/speckit-plan)
├── research.md          # Phase 0
├── data-model.md        # Phase 1
├── quickstart.md        # Phase 1
├── contracts/
│   └── website-links.md # Phase 1 — Link-/Footer-Formate als testbarer Kontrakt
├── checklists/
│   └── requirements.md  # aus /speckit-specify + /speckit-clarify
└── tasks.md             # Phase 2 (/speckit-tasks — NICHT von /speckit-plan)
```

### Source Code (repository root)

```text
src/main/java/com/example/wmtippspiel/
├── config/
│   └── AppProperties.java          # + nested record Website(String baseUrl)
├── discord/
│   ├── render/
│   │   ├── EmbedStyle.java          # ggf. Footer-Hilfe (unverändert lassen, s. research)
│   │   ├── LeaderboardBoardEmbed.java  # Footer-Hinweis ergänzen (configgesteuert)
│   │   ├── RanglisteEmbed.java         # Link-Zeile am Ende der Description ergänzen
│   │   └── ProfilEmbed.java            # Link-Feld/Zeile am Ende ergänzen
│   ├── commands/
│   │   ├── ProfilCommand.java          # publicId(target.getId()) → Profil-URL durchreichen
│   │   └── RanglisteCommand.java       # Leaderboard-URL durchreichen
│   └── render/WebsiteLinks.java        # NEU: zentraler URL-/Hinweis-Helfer
└── publicapi/
    └── PublicIdService.java            # vorhanden, wiederverwendet (publicId)

src/main/resources/
└── application.yml                     # + app.website.base-url: ${WEBSITE_BASE_URL:}

src/test/java/com/example/wmtippspiel/
└── discord/render/WebsiteLinksTest.java  # NEU: URL-Bau, Trailing-Slash, leere Basis-URL
    (+ ggf. LeaderboardBoardEmbedTest erweitern)
```

**Structure Decision**: Single-Project-Layout (Bestand). Neuer, kleiner zustandsloser
Helfer `WebsiteLinks` (Package `discord.render`) kapselt Basis-URL-Normalisierung und
Link-/Footer-Erzeugung; die drei Embed-/Command-Stellen konsumieren ihn. So bleibt die
Logik an einer Stelle testbar und die bestehenden Embed-Builder werden nur minimal
erweitert (keine API-Brüche an `build(...)`-Signaturen über das Nötige hinaus).

## Complexity Tracking

> Keine Verfassungsverstöße — Tabelle entfällt.
