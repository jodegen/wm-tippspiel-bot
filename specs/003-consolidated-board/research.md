# Phase 0 — Research: Konsolidiertes Board (F7-Redesign)

Alle offenen Punkte aus der Spec wurden in der Clarify-Session geklärt; es
verbleiben keine `NEEDS CLARIFICATION`. Dieses Dokument hält die technischen
Entscheidungen fest, die das Design tragen.

## D1 — Auswahl der „nächsten 12 anstehenden Spiele"

**Decision**: Bestehende `MatchRepository.findUpcoming(Instant now, int limit)` mit
`limit = 12` wiederverwenden. Sie filtert `kickoff > now AND status NOT IN
('IN_PLAY','FINISHED','CANCELLED')` und sortiert aufsteigend nach `kickoff`.

**Rationale**: Erfüllt FR-002 (Anstoßzeit in der Zukunft) exakt und ist bereits
getestet/eingesetzt. UTC-Vergleich via `Clock` (Prinzip IV). Keine neue Query nötig.

**Alternatives considered**: Neue Query nach Status `SCHEDULED` — verworfen (Clarify:
Filter über Kickoff-Zeit, robust gegen API-Lag). `findBetween` (tagesbasiert) — verworfen
(genau das ist das alte, abzulösende Modell).

## D2 — Ein Embed, Liste in der Beschreibung, defensive Truncation

**Decision**: `BoardEmbed.buildBoard(List<Match>)` rendert **eine** Beschreibung. Pro Spiel
ein kompakter Block (Begegnung fett, Relative-Timestamp-Countdown, optional 📺 Sender,
optional 💰 Quote). Beim Aufbau wird ein Sicherheits-Limit für die Beschreibung von
**4000 Zeichen** (< Discord 4096) geführt; passt der nächste Block nicht mehr, wird
abgebrochen und ein Hinweis „… und N weitere" angehängt. Gesamtembed bleibt damit klar
unter 6000 Zeichen (Titel/Author/Footer sind kurz).

**Rationale**: 12 Spiele liegen praktisch immer weit unter den Limits; die Truncation ist
eine defensive Absicherung gegen Ausreißer (sehr lange Namen/Sender) und erfüllt die
ausdrückliche User-Vorgabe. Reine Funktion → unit-testbar.

**Alternatives considered**: Ein Embed-Feld pro Spiel — verworfen (25-Felder-Limit, und die
Spec verlangt eine zusammenhängende Listen-Beschreibung). Mehrere Embeds in einer Nachricht
— verworfen (Ziel ist genau ein Embed).

## D3 — Keine Live-/Endstände im Board

**Decision**: Das Board zeigt nur künftige Spiele; die frühere
`scoreOrCountdown`-Verzweigung (Live/Endstand) entfällt im Board-Rendering. Es wird stets
der Anpfiff-Countdown gezeigt.

**Rationale**: Per Clarify sind nur noch nicht angepfiffene Spiele gelistet, deren Score
per Definition leer ist. Live-Tore laufen über F8/Announce-Channel. Die Filteransicht
(`buildFiltered`) bleibt unverändert und darf weiterhin Live/Endstand zeigen (Filter ist
explizit unverändert).

**Alternatives considered**: Live-Stände im Board behalten — verworfen (widerspräche
„anstehend").

## D4 — Gemeinsamer Styling-Helper `EmbedStyle`

**Decision**: Neuer Helper `EmbedStyle` kapselt die gemeinsame „Chrome": Marken-Akzentfarbe,
Author-Zeile („FIFA WM 2026 · 11. Juni – 19. Juli"), Divider-Konstante, Footer-Text-Konvention
und `setTimestamp(clock.instant())`. `EmbedStyle.base(title)` liefert einen vorgestylten
`EmbedBuilder`. `InfoEmbed` und `BoardEmbed` bauen darauf auf. Das Board erhält dieselbe
Akzentfarben-/Header-/Footer-Logik wie das Info-Embed (FR-010/011).

**Rationale**: Eine einzige Stelle für den Look verhindert Stil-Drift und erfüllt die
Design-Anforderung. `InfoEmbed` behält sein sichtbares Ergebnis (nur Refactor der Chrome).

**Alternatives considered**: Styling in jeder Embed-Klasse duplizieren — verworfen
(Drift-Risiko, genau das soll der Helper verhindern). Vererbung statt Helper — verworfen
(JDA `EmbedBuilder` ist final-orientiert; Komposition ist einfacher und testbar).

## D5 — Start-Cleanup verwaister Bot-Nachrichten

**Decision**: Am `ApplicationReadyEvent` (nach `jda.awaitReady()`, analog `InfoChannelService`)
liest der `BoardService` die **letzten 100 Nachrichten** des Board-Channels
(`channel.getHistory().retrievePast(100)` bzw. `getHistoryFromBeginning`-Äquivalent) und
löscht jede Nachricht, deren Author die eigene Bot-User-ID ist (`message.getAuthor().equals(jda.getSelfUser())`)
und deren ID **nicht** die getrackte `board:main`-Message-ID ist. Reihenfolge: Cleanup vor/um
das erste Refresh; die soeben gültige `board:main` wird nie gelöscht (ID-Vergleich gegen den
getrackten Wert; existiert noch keine, wird zuerst gepostet).

**Rationale**: Erfüllt FR-016/018/019/021. Eine History-Seite (≤100) ist gebunden,
rate-limit-freundlich und deckt einen dedizierten Channel praktisch vollständig ab. Nur
eigene Nachrichten werden gelöscht → fremde bleiben unangetastet.

**Alternatives considered**: Vollständiger Historien-Scan — verworfen (Clarify: 100). Löschen
nur „board-artiger" Nachrichten per Inhalts-Heuristik — verworfen (Clarify: alle eigenen außer
board:main, da Channel dediziert). Bulk-Delete via `purgeMessages` — als Implementierungsdetail
zulässig, aber Einzel-`delete` mit Self-Author-Filter ist eindeutig und vermeidet das
14-Tage-Bulk-Limit-Risiko.

## D6 — Migration der Alt-Slots in `bot_messages`

**Decision**: Liquibase-Changeset `009-reduce-board-slots.sql`:
`DELETE FROM bot_messages WHERE key LIKE 'board:day:%' OR key = 'board:nav';`
Die zugehörigen Discord-Nachrichten werden zu Laufzeit vom Start-Cleanup (D5) entfernt, da
sie nach dem Changeset nicht mehr getrackt sind.

**Rationale**: Prinzip II (alle Datenänderungen via Liquibase). Additiv, idempotent
(`DELETE` bleibt korrekt bei wiederholter Anwendung). `board:main` bleibt unberührt.

**Alternatives considered**: Zur-Laufzeit-Löschen der Zeilen im Code — verworfen (umgeht
Liquibase, verstößt gegen Prinzip II). `board:nav` zu `board:main` umbenennen — verworfen
(saubere Neuanlage + Cleanup ist eindeutiger als ID-Recycling).

## D7 — Filter-Komponente an der einen Nachricht

**Decision**: Die Nav-Action-Row (`BoardNavigation.actionRow()`) wird beim Posten **und** bei
jedem Edit der `board:main`-Nachricht via `.setComponents(...)` mitgegeben, sodass sie stets
direkt unter dem konsolidierten Embed hängt. `board:nav` als separater Slot entfällt;
`ensureNav` wird entfernt. `BoardFilterHandler`/`InteractionListener` bleiben unverändert.

**Rationale**: Erfüllt FR-012/015 ohne fachliche Änderung am Filter. Eine Nachricht trägt
Embed + Components — der Edit erhält beide synchron.

**Alternatives considered**: Separate Nav-Nachricht beibehalten — verworfen (würde zwei
Nachrichten erfordern, widerspricht „genau eine").
