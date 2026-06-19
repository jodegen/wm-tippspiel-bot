---

description: "Task list for feature: Website-Hinweise in Discord-Ausgaben"
---

# Tasks: Website-Hinweise in Discord-Ausgaben

**Input**: Design documents from `/specs/009-website-hints/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/website-links.md

**Tests**: Unit-Tests für den `WebsiteLinks`-Helfer sind enthalten — von plan.md/research.md
ausdrücklich als Test-Oberfläche vorgesehen (C1–C5). Punktewertung/Reveal-Timing werden
nicht berührt ⇒ keine Test-First-Pflicht nach Verfassung Prinzip III.

**Organization**: Tasks gruppiert nach User Story (P1/P2/P3), unabhängig testbar.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: parallelisierbar (andere Datei, keine offenen Abhängigkeiten)
- **[Story]**: zugehörige User Story (US1/US2/US3)

## Path Conventions

Single project: Quellcode unter `src/main/java/com/example/wmtippspiel/`, Tests unter
`src/test/java/com/example/wmtippspiel/`, Konfiguration unter `src/main/resources/`.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Konfigurationswert für die Website-Basis-URL bereitstellen.

- [X] T001 Nested record `Website(String baseUrl)` zu `AppProperties` hinzufügen und im record-Header ergänzen, in `src/main/java/com/example/wmtippspiel/config/AppProperties.java` (Javadoc analog zu `PublicApi`)
- [X] T002 `app.website.base-url: ${WEBSITE_BASE_URL:}` mit erläuterndem Kommentar in `src/main/resources/application.yml` ergänzen (Default leer; getrennt von `app.public-api.public-base-url`)

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Zentraler, zustandsloser `WebsiteLinks`-Helfer — wird von ALLEN drei User
Stories konsumiert (URL-Normalisierung, Link-/Footer-Erzeugung, Leerwert-Verhalten).

**⚠️ CRITICAL**: Keine User-Story-Arbeit beginnt, bevor diese Phase abgeschlossen ist.

- [X] T003 [P] Unit-Test `WebsiteLinksTest` schreiben in `src/test/java/com/example/wmtippspiel/discord/render/WebsiteLinksTest.java` — deckt ab: `profileUrl` = `{base}/profil/{publicId}` (publicId == `PublicIdService.publicId`), `leaderboardUrl` = `{base}/leaderboard`, Trailing-Slash-Normalisierung (mit/ohne `/` identisch, C4), leere/blank Basis-URL ⇒ `Optional.empty()` (C5), Footer-Host-Ableitung `https://wm.xenoria.de/` → `wm.xenoria.de`
- [X] T004 Komponente `WebsiteLinks` implementieren in `src/main/java/com/example/wmtippspiel/discord/render/WebsiteLinks.java` — injiziert `AppProperties` + `PublicIdService`; Methoden `isConfigured()`, `Optional<String> leaderboardUrl()`, `Optional<String> profileUrl(String discordUserId)`, `Optional<String> footerHint()`; normalisiert genau einen Trailing-Slash, leitet Host (ohne Schema/`/`) ab; bis T003 grün ist

**Checkpoint**: `WebsiteLinks` verfügbar und getestet — User Stories können beginnen.

---

## Phase 3: User Story 1 - Leaderboard-Board verweist auf die Web-Tabelle (Priority: P1) 🎯 MVP

**Goal**: Footer des selbst-aktualisierenden Boards (F11) zeigt bei konfigurierter
Basis-URL einen dezenten Texthinweis „… · Vollständige Tabelle auf wm.xenoria.de".

**Independent Test**: Board rendern (mit gesetzter `WEBSITE_BASE_URL`) → Footer enthält
den Hinweis mit Host; bei leerer URL bleibt der bestehende Footer unverändert.

- [X] T005 [US1] `LeaderboardBoardEmbed` um `WebsiteLinks` erweitern in `src/main/java/com/example/wmtippspiel/discord/render/LeaderboardBoardEmbed.java` — Konstruktor-Injection `WebsiteLinks`; in `build(...)` Footer via `EmbedBuilder.setFooter` gezielt überschreiben zu `EmbedStyle.FOOTER_BASE + " · " + footerHint()` **nur wenn** `footerHint()` vorhanden; `EmbedStyle.FOOTER_BASE` global unverändert lassen (kein Seiteneffekt auf andere Embeds); Leer- und Befüllt-Fall robust (auch bei leerem Board); Hinweistext ist eine kurze, feste Konstante (kein dynamischer Langtext) → bleibt sicher unter den Discord-Footer-/Embed-Limits (FR-010)
- [X] T006 [US1] `LeaderboardBoardEmbedTest` erweitern in `src/test/java/com/example/wmtippspiel/discord/render/LeaderboardBoardEmbedTest.java` — Footer enthält Hinweis bei konfigurierter Basis-URL (auch im Leer-Board-Fall, FR-001/SC-001) und bleibt unverändert bei leerer Basis-URL (FR-006)

**Checkpoint**: US1 vollständig und unabhängig testbar (MVP).

---

## Phase 4: User Story 2 - /profil verlinkt die Web-Profilansicht (Priority: P2)

**Goal**: `/profil`-Antwort endet mit klickbarem Link `{base}/profil/{publicId}` zum
Profil genau des angezeigten Nutzers (auch bei `/profil @user`).

**Independent Test**: `/profil` bzw. `/profil @user` ausführen → klickbarer Link mit dem
publicId des Ziel-Nutzers (== Public-API). Leere Basis-URL ⇒ kein Link, kein Fehler.

- [X] T007 [US2] `ProfilEmbed.build(...)` um optionalen `profileUrl`-Parameter erweitern in `src/main/java/com/example/wmtippspiel/discord/render/ProfilEmbed.java` — bei vorhandener URL eine abschließende, klickbare Markdown-Link-Zeile/-Feld anhängen (fester Wortlaut: `🔗 [Profil auf {host} ansehen]({profileUrl})`, host aus `WebsiteLinks`); bei `null`/leer nichts anhängen; auch im „noch keine Tipps"-Pfad konsistent; Hinweistext ist eine kurze, feste Konstante → bleibt sicher unter den Discord-Embed-Limits (FR-002, FR-003, FR-010)
- [X] T008 [US2] `ProfilCommand` anpassen in `src/main/java/com/example/wmtippspiel/discord/commands/ProfilCommand.java` — `WebsiteLinks` injizieren, `profileUrl(target.getId())` berechnen und an `embed.build(profile, avatarUrl, profileUrl)` durchreichen (Discord-ID nur als Eingabe für `publicId`, nie in der URL)
- [X] T009 [P] [US2] Test in `src/test/java/com/example/wmtippspiel/discord/commands/` ergänzen — verifiziert, dass der Profil-Link den `publicId` des Ziel-Nutzers (nicht des Aufrufers) trägt und mit `PublicIdService.publicId(targetId)` übereinstimmt (FR-009, SC-002); Leerfall ⇒ kein Link

**Checkpoint**: US1 und US2 funktionieren unabhängig.

---

## Phase 5: User Story 3 - /rangliste verlinkt die Web-Tabelle (Priority: P3)

**Goal**: `/rangliste`-Antwort endet mit klickbarem Link `{base}/leaderboard`.

**Independent Test**: `/rangliste` ausführen → klickbare Link-Zeile am Ende; leere
Basis-URL ⇒ kein Link, bestehende (auch leere) Ausgabe unverändert funktionsfähig.

- [X] T010 [US3] `RanglisteEmbed.build(...)` um optionalen `leaderboardUrl`-Parameter erweitern in `src/main/java/com/example/wmtippspiel/discord/render/RanglisteEmbed.java` — bei vorhandener URL abschließende Markdown-Link-Zeile an die Description anhängen (`[Vollständige Tabelle ansehen](url)`); bei `null`/leer und im Leer-Ranglisten-Fall nichts anhängen (FR-004, FR-006)
- [X] T011 [US3] `RanglisteCommand` anpassen in `src/main/java/com/example/wmtippspiel/discord/commands/RanglisteCommand.java` — `WebsiteLinks` injizieren und `leaderboardUrl()` an `embed.build(tips.leaderboard(), leaderboardUrl)` durchreichen

**Checkpoint**: Alle drei User Stories unabhängig funktionsfähig.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Verifikation und Abschluss.

- [X] T012 [P] `quickstart.md`-Verifikation durchführen (manuelle Schritte 1–5) und Ergebnis kurz festhalten
- [X] T013 [P] README/Deployment-Doku um `WEBSITE_BASE_URL` ergänzen (ENV-Tabelle / `.env`-Beispiel), in `README.md`
- [X] T014 Vollständigen Testlauf ausführen: `./mvnw test` — alle bestehenden Embed-/Command-Tests grün (keine Regression, FR-006/FR-007). Zusätzlich per Diff-Review bestätigen, dass kein neuer Nachrichten-Sende-Pfad (`queue()`/`sendMessage`/`reply…`) und keine Channel-Erzeugung hinzukam — nur bestehende Ausgaben wurden erweitert (FR-007/SC-004)

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: keine Abhängigkeiten — sofort startbar (T001, T002).
- **Foundational (Phase 2)**: hängt von Phase 1 ab; **blockiert alle User Stories**.
- **User Stories (Phase 3–5)**: alle hängen nur von Phase 2 ab; danach untereinander
  unabhängig (verschiedene Dateien) — parallelisierbar oder in Prioritätsreihenfolge.
- **Polish (Phase 6)**: nach den gewünschten User Stories.

### User Story Dependencies

- **US1 (P1)**: nur Phase 2 nötig. Berührt ausschließlich `LeaderboardBoardEmbed`.
- **US2 (P2)**: nur Phase 2 nötig. Berührt `ProfilEmbed` + `ProfilCommand`.
- **US3 (P3)**: nur Phase 2 nötig. Berührt `RanglisteEmbed` + `RanglisteCommand`.

Keine Cross-Story-Abhängigkeiten — jede Story ist eigenständig deploybar.

### Within Each User Story

- Embed-Signatur/-Logik vor Command-Verdrahtung (US2: T007 → T008; US3: T010 → T011).
- Tests (T006, T009) testen das jeweilige Story-Verhalten.

### Parallel Opportunities

- T001 und T002 (verschiedene Dateien) parallel.
- Nach Phase 2: US1, US2, US3 vollständig parallel von verschiedenen Personen bearbeitbar.
- T012, T013 parallel; T014 abschließend.

---

## Parallel Example: nach Foundational

```bash
# Sobald Phase 2 (WebsiteLinks) steht, parallel startbar:
Developer A: US1 → T005, T006   (LeaderboardBoardEmbed)
Developer B: US2 → T007, T008, T009   (ProfilEmbed/ProfilCommand)
Developer C: US3 → T010, T011   (RanglisteEmbed/RanglisteCommand)
```

---

## Implementation Strategy

### MVP First (User Story 1)

1. Phase 1 (Setup) + Phase 2 (WebsiteLinks) abschließen.
2. Phase 3 (US1 — Board-Footer) umsetzen.
3. **STOP & VALIDATE**: Board-Footer mit/ohne `WEBSITE_BASE_URL` prüfen.
4. Deploy/Demo als MVP.

### Incremental Delivery

1. Setup + Foundational → Fundament steht.
2. US1 → testen → Deploy (MVP).
3. US2 → testen → Deploy.
4. US3 → testen → Deploy.
5. Jede Story liefert Mehrwert, ohne vorherige zu brechen.

---

## Notes

- [P] = verschiedene Dateien, keine offenen Abhängigkeiten.
- Keine Schema-Änderung, kein Liquibase-Changeset, keine neuen Dependencies, keine neuen
  Channels/automatischen Posts (FR-007).
- In URLs ausschließlich `publicId` (nie Discord-ID/E-Mail/Anzeigename) — FR-009.
- Footer = reiner Text (nicht klickbar); Description/Fields = klickbare Markdown-Links.
- Nach jeder Aufgabe committen; an Checkpoints Story unabhängig validieren.
