# WM 2026 Tippspiel Discord-Bot — Feature-Spezifikation

Ein Discord-Bot für eine kleine Community, der rund um die FIFA WM 2026 (11. Juni – 19. Juli 2026, USA/Kanada/Mexiko, 48 Teams, 104 Spiele) einen Spielplan-Überblick gibt, Tipps verwaltet und ein Tippspiel mit Punktewertung betreibt.

## Tech-Stack

- **Sprache/Framework:** Java 21+, Spring Boot 3.x
- **Datenbank:** PostgreSQL (mit Liquibase für Migrations)
- **Discord-Anbindung:** JDA (Java Discord API) oder Discord4J — Slash-Commands
- **Scheduling:** Spring `@Scheduled` für Sync- und Reveal-/Auswertungs-Jobs
- **Externe APIs:**
  - Spielplan & Ergebnisse: [football-data.org](https://www.football-data.org/) (Competition `WC`)
  - Quoten (optional): [The Odds API](https://the-odds-api.com/) (`soccer_fifa_world_cup`, Markt `h2h`)
- **TV-Sender:** kein zuverlässiges API verfügbar → manuell gepflegtes Mapping (ARD, ZDF, MagentaTV; RTL hält 2026 keine Rechte)

## Zeitzonen-Hinweis

Alle Anstoßzeiten werden von der API in UTC geliefert und in der DB als UTC gespeichert. Anzeige immer in `Europe/Berlin`. Durch die Austragung in Nordamerika finden viele Spiele spätabends/nachts deutscher Zeit statt — das ist für Reveal-Timing und Erinnerungen relevant.

---

## Datenmodell

### `matches`
| Feld | Typ | Beschreibung |
|---|---|---|
| `id` | BIGINT (PK) | Match-ID aus der API |
| `home` / `away` | TEXT | Teamnamen (vor Gruppen-Abschluss ggf. "TBD") |
| `kickoff` | TIMESTAMPTZ | Anstoßzeit (UTC) |
| `stage` | TEXT | GROUP_STAGE, LAST_16, QUARTER_FINAL, … |
| `channel` | TEXT | TV-Sender, manuell gepflegt |
| `odds_home` / `odds_draw` / `odds_away` | NUMERIC | Buchmacher-Quoten (nullable) |
| `home_score` / `away_score` | INT | Aktueller/Endstand (null bis Anpfiff; während des Spiels fortlaufend per Sync aktualisiert) |
| `status` | TEXT | SCHEDULED / IN_PLAY / FINISHED |
| `revealed` | BOOLEAN | Tipps bereits offengelegt? |
| `evaluated` | BOOLEAN | Punkte bereits vergeben? |
| `notified_home` / `notified_away` | INT | Zuletzt für Tor-Pings gemeldeter Stand (F8); getrennt vom tatsächlichen `home_score`/`away_score`, damit ein Bot-Neustart keine Tore doppelt/gar nicht meldet (default 0 ab Anpfiff) |

### `tips`
| Feld | Typ | Beschreibung |
|---|---|---|
| `user_id` | TEXT | Discord-User-ID |
| `username` | TEXT | Anzeigename (denormalisiert) |
| `match_id` | BIGINT (FK) | Referenz auf `matches` |
| `home_score` / `away_score` | INT | Getipptes Ergebnis |
| `created_at` | TIMESTAMPTZ | Zeitpunkt der Abgabe |
| `points` | INT | Nach Auswertung gesetzt (default 0) |

Primärschlüssel: `(user_id, match_id)` — ein Tipp pro User pro Spiel, Update bis Anpfiff erlaubt.

### `bot_messages`
Verwaltet die persistenten, vom Bot editierten Board-Nachrichten (für F7).

| Feld | Typ | Beschreibung |
|---|---|---|
| `key` | TEXT (PK) | Logischer Slot. Aktuell genau einer: `board:main` (konsolidiertes Board, F7). Frühere tagesweise Slots (`board:day:<datum>`) und `board:nav` werden migriert/entfernt. |
| `channel_id` | TEXT | Discord-Channel der Nachricht |
| `message_id` | TEXT | Discord-Message-ID (zum Editieren) |
| `updated_at` | TIMESTAMPTZ | Letzter Edit-Zeitpunkt |

---

## Kern-Features (MVP)

### F1 — Spielplan-Übersicht
**Command:** `/spielplan [anzahl]`
Zeigt die nächsten N Spiele (default 5) als Embed: Begegnung, Anstoßzeit (de-DE), TV-Sender, Quoten (falls vorhanden). Vergangene und laufende Spiele werden ausgeblendet.

### F2 — Nächstes Spiel
**Command:** `/naechstes`
Zeigt das unmittelbar nächste Spiel mit Discord-Relative-Timestamp (`<t:UNIX:R>`) als Live-Countdown, Sender und Quoten.

### F3 — Tipp abgeben
**Command:** `/tipp <spiel> <heim> <gast>`
- Auswahl des Spiels über Autocomplete (nur noch nicht angepfiffene Spiele wählbar).
- Speichert/aktualisiert den Tipp; Antwort **ephemeral** (nur für den User sichtbar), damit kein Tipp geleakt wird.
- Ablehnung, wenn Anpfiff bereits erreicht ist.

### F4 — Tipps offenlegen (Reveal)
**Automatisch bei Anpfiff.** Scheduler prüft regelmäßig (z. B. minütlich) auf Spiele mit `kickoff <= now AND revealed = false`. Postet ein Embed mit allen abgegebenen Tipps in den Announce-Channel und setzt `revealed = true`.

### F5 — Auto-Auswertung
**Automatisch nach Abpfiff.** Scheduler prüft auf Spiele mit `status = FINISHED AND evaluated = false`. Berechnet Punkte je Tipp, schreibt sie in `tips.points`, postet eine Ergebnis-/Punkte-Übersicht und setzt `evaluated = true`.

**Punkteschema (CHECK24, vierstufig):** Stufen werden spezifisch → allgemein
geprüft, die erste zutreffende gewinnt; alle Spiele gleich gewertet (keine
Phasen-Gewichtung).
- **4 Punkte** — exaktes Ergebnis
- **3 Punkte** — richtige (vorzeichenbehaftete) Tordifferenz, aber nicht exakt (z. B. Tipp 3:0 / Ergebnis 4:1; schließt Unentschieden mit falscher Höhe ein, z. B. Tipp 2:2 / Ergebnis 1:1)
- **2 Punkte** — richtige Tendenz (Sieger bzw. Unentschieden), aber weder exaktes Ergebnis noch richtige Tordifferenz
- **0 Punkte** — falsche Tendenz

> Siehe `specs/006-check24-scoring/spec.md` (CHECK24-Umstellung inkl. rückwirkender Neuberechnung).

### F6 — Leaderboard
**Command:** `/rangliste`
Aggregiert Gesamtpunkte je User, sortiert absteigend. Zusatzspalten: Anzahl abgegebener Tipps, Anzahl exakter Treffer (Tie-Breaker). Die exakten Treffer werden **direkt aus dem Vergleich Tipp gegen tatsächliches Ergebnis** ermittelt (nicht aus dem Punktwert abgeleitet), damit die Statistik unabhängig vom Punkteschema korrekt bleibt.

### F7 — Live-Spielplan-Board (selbst-aktualisierend)
Statt den Spielplan nur on-demand per Command anzuzeigen, hält der Bot in einem dedizierten, für Mitglieder read-only Channel (z. B. `#wm-spielplan`) **ein einziges, konsolidiertes** Board, das er per **Edit** aktuell hält — nicht durch wiederholtes Posten.

> Detailspezifikation der Überarbeitung: `specs/003-consolidated-board/spec.md` (Feature 003).

**Mechanik:**
- Der Board-Channel enthält **genau ein** konsolidiertes Embed, getrackt unter dem einzigen Slot `board:main` in `bot_messages`. Bei jedem Sync wird diese Nachricht **editiert** statt neu gepostet, damit der Channel sauber bleibt und das Board ortsfest steht.
- Das Embed zeigt die **nächsten 12 anstehenden (noch nicht angepfiffenen) Spiele** als **zusammenhängende Liste in der Embed-Beschreibung** — nicht mehr ein Embed pro Tag.
- Anzeige je Spiel: Begegnung, Anstoßzeit als Discord-Relative-Timestamp (`<t:UNIX:R>`, läuft client-seitig als Countdown), TV-Sender und Quote (jeweils falls vorhanden). Live-/Endstände gehören nicht ins Board (laufen über F8/Announce-Channel), da nur künftige Spiele gelistet werden.
- **Design:** Das Embed folgt dem visuellen Stil des Info-Channel-Embeds — konsistente Akzentfarbe, Header (Author-/Titelzeile mit Emoji), Footer mit Update-Zeitstempel und konsistente Emoji-/Strukturierung.
- **Cleanup beim Start:** Alte, nicht mehr getrackte Board-Nachrichten des Bots im Channel werden beim Start entfernt; die früheren tagesweisen Slots (`board:day:<datum>`) und der separate `board:nav`-Slot werden migriert/entfernt, sodass nur `board:main` verbleibt. Nachrichten anderer Nutzer bleiben unangetastet.
- Existiert die getrackte `board:main`-Nachricht nicht mehr (manuell gelöscht), wird sie neu gepostet und in `bot_messages` aktualisiert.

**Interaktive Filter (Stufe 2, Standard):**
- Direkt **unter dem konsolidierten Board** hängt — unverändert — eine Navigations-Komponente: ein Select-Menu bzw. Buttons („Heute", „Morgen", „Gruppe A–L", „K.o.-Runde").
- Auswahl löst eine **ephemeral** Antwort aus (nur für die klickende Person sichtbar) mit der gefilterten Ansicht. Das öffentliche Board bleibt dabei unverändert.
- Damit ersetzt F7 die manuellen Commands F1/F2 für den Alltag — diese bleiben als Fallback/Direktzugriff erhalten.

**Wichtige Voraussetzung:** Interaktive Komponenten (Buttons/Select-Menus) erfordern einen dauerhaft per Gateway verbundenen Bot, der Interaction-Events empfängt. Reine `@Scheduled`-Jobs genügen dafür nicht (sie können nur über REST editieren/posten). Die Architektur muss also einen laufenden Gateway-Listener vorsehen, nicht nur Cron.

### F8 — Live-Tor-Benachrichtigungen
**Automatisch während laufender Spiele.** Sobald in einem laufenden Spiel ein Tor fällt, postet der Bot zeitnah eine Benachrichtigung in den Announce-Channel (z. B. „⚽ **TOR!** Deutschland 1:0 Curaçao — 23.'"). Datenquelle ist [football-data.org](https://www.football-data.org/) (kostenlos) — der Anbieter liefert **keinen echten Push**; die Echtzeit-Wahrnehmung entsteht ausschließlich backend-seitig durch Polling im Service.

**Architektur-Prinzip:**
- Das Polling liegt **zentral im Spring-Boot-Service**, nicht beim User. Die Discord-User bekommen reines **Push** (den Channel-Ping), keine eigene Abfrage.
- Die Event-Quelle ist hinter einem gemeinsamen Interface `GoalEventSource` abstrahiert. Die konkrete Implementierung (jetzt: **Score-Diff-Polling** gegen football-data.org) muss später gegen **Webhook** oder **WebSocket** austauschbar sein, ohne dass Goal-Detector oder Discord-Posting sich ändern.

**Komponenten:**
- **Live-Polling-Fenster:** Scores werden nur zwischen `kickoff` und `kickoff + 2.5h` abgefragt, und nur für Spiele mit Status `IN_PLAY`/`SCHEDULED` im Zeitfenster. Außerhalb des Fensters findet **kein** Score-Polling statt, um im API-Rate-Limit zu bleiben (football-data.org: 10 Req/Min). Das Pollintervall im Fenster ist konfigurierbar (Default 60s).
- **Goal-Detector:** Hält je Match den zuletzt gemeldeten Spielstand (`notified_home`/`notified_away`). Bei einer Differenz zum neuen Stand wird **je zusätzlichem Tor** ein `GoalEvent` erzeugt (Team, neuer Stand, ggf. Spielminute) und der gespeicherte Stand aktualisiert. Muss **idempotent** sein: ein doppelt geliefertes oder erneut gepolltes Update darf keinen zweiten Ping auslösen. Auch **Stand-Korrekturen nach unten** (z. B. aberkanntes Tor via VAR) müssen sauber behandelt werden — der gemeldete Stand wird angepasst, ohne eine Fehl-Benachrichtigung auszulösen.
- **Discord-Posting:** Eigene Methode, die ein `GoalEvent` als Embed/Nachricht in den Announce-Channel postet. Identisch nutzbar, **egal aus welcher Event-Quelle** (`GoalEventSource`) das Event stammt.

**Wichtige Voraussetzung:** Der zuletzt gemeldete Stand wird persistent gehalten (Datenmodell: `notified_home`/`notified_away`), damit ein Bot-Neustart mitten im Spiel keine Tore doppelt oder gar nicht meldet.

### F9 — Dynamische Bot-Presence
Der Bot spiegelt über seine Discord-Presence (Activity-Typ **watching**, „Sieht sich … an") **zustandsgesteuert** (NICHT zeitbasiert rotierend) den aktuellen WM-Kontext wider. Es gewinnt jederzeit der höchstpriorisierte zutreffende Zustand.

> Detailspezifikation: `specs/004-dynamic-bot-presence/spec.md` (Feature 004).

**Drei priorisierte Zustände (LIVE > UPCOMING > IDLE):**
- **LIVE** — sobald mindestens ein Spiel läuft, zeigt die Presence den Live-Stand, z. B. „⚽ LIVE: GER 2:1 FRA". **Event-getrieben** über den bestehenden F8-Goal-Detector aktualisiert — **kein eigener Timer/Poller** in F9.
- **UPCOMING** — wenn kein Spiel läuft, aber ein künftiges existiert, zeigt die Presence das nächste Spiel, z. B. „👀 Nächstes: GER vs FRA". Aktualisierung beim `boardRefresh`, jedoch **höchstens stündlich**.
- **IDLE** — statischer Fallback, wenn weder ein Spiel läuft noch eines ansteht, z. B. „🏆 WM 2026 /tipp".

**Wichtige Regeln:**
- Die Activity wird **nur gesetzt, wenn sich der Anzeigetext tatsächlich geändert hat** (kein redundantes Update).
- **Throttling:** Discord erlaubt nur **5 Presence-Änderungen pro 20 Sekunden** (sonst 60s-Backoff). Eine Drossel muss das **garantiert** verhindern; überzählige Updates werden verzögert/zusammengefasst und es wird jeweils der aktuellste Text gesendet.
- Nur **Standard-Unicode-Emojis** (keine custom Discord-Emojis).
- Nach Start/Reconnect wird die Presence aus dem aktuellen Zustand neu gesetzt.

---

## Hintergrund-Jobs (Spring `@Scheduled`)

| Job | Intervall | Aufgabe |
|---|---|---|
| `syncMatches` | z. B. alle 15 Min | Spielplan & Ergebnisse von football-data.org holen, `matches` upserten |
| `syncOdds` | z. B. alle 6 Std | Quoten von The Odds API holen, per Teamnamen-Heuristik matchen (API-Limits beachten) |
| `revealJob` | minütlich | Tipps anstehender Spiele offenlegen (F4) |
| `evaluateJob` | minütlich | Beendete Spiele auswerten (F5) |
| `boardRefresh` | nach jedem `syncMatches` (bzw. alle 15 Min) | Konsolidiertes Board-Embed (`board:main`) editieren (F7) |
| `liveGoalPoll` | im Live-Fenster alle ~60s (konfigurierbar), sonst inaktiv | Scores laufender Spiele (`kickoff` … `kickoff + 2.5h`) abfragen, Tore via Goal-Detector erkennen und in den Announce-Channel posten (F8) |

---

## Erweiterungs-Features (Backlog / Phase 2)

### E1 — Tipp-Erinnerungen
Ping/DM an User, die X Minuten vor Anpfiff noch keinen Tipp abgegeben haben.

### E2 — Live-Score-Updates
Tor-Benachrichtigungen im Channel während laufender Spiele (`status = IN_PLAY`), basierend auf häufigerem Sync.

### E3 — Gruppen-/KO-Brackets
User tippen Gruppentabellen bzw. Turnierbaum; eigene Wertung neben der Einzelspiel-Wertung.

### E4 — Mut-Tipp / Underdog-Badge
Badge oder Bonuspunkte für korrekte Tipps gegen hohe Quoten.

### E5 — Saison-Statistiken
`/stats [user]` — beste Trefferquote, Lieblings-Underdog, Punkteverlauf über das Turnier.

### E6 — Admin-Commands
`/setchannel <match> <sender>` zum Pflegen des TV-Mappings; `/resync` für manuellen API-Sync.

### E7 — Board als gerendertes Bild (Stufe 3)
Statt Embeds eine echte Grafik serverseitig rendern (Java2D/`BufferedImage` oder HTML→PNG) und als Bild ins Board posten. Bester Look, aber höchster Pflegeaufwand — erst angehen, wenn F7 (Embed-Board + Filter) stabil läuft. Verliert die client-seitigen Countdown-Timestamps, daher ggf. nur für statischere Übersichten (z. B. Gruppentabellen, Turnierbaum) sinnvoll.

---

## Offene Punkte / Entscheidungen

- **Quoten-Matching:** Teamnamen zwischen football-data.org und The Odds API können abweichen → robustes Mapping (z. B. ID-Mapping-Tabelle statt String-Heuristik) erwägen.
- **API-Rate-Limits:** football-data.org Free-Tier ist limitiert (10 Req/Min). Sync-Intervalle entsprechend wählen; ggf. Caching.
- **Reveal-Edge-Case:** Bei API-Verzögerung könnte ein Spiel angepfiffen sein, bevor der Sync den Status kennt — Reveal hängt bewusst an `kickoff`-Zeit, nicht am API-Status.
- **Mehrere Server (Guilds):** MVP geht von einer Community/einem Announce-Channel aus. Multi-Guild würde Channel-Konfiguration pro Guild erfordern.
- **Gateway statt nur Cron:** Durch F7 (interaktive Komponenten) braucht der Bot eine dauerhafte Gateway-Verbindung, nicht nur `@Scheduled`-Jobs. Beim Deployment beachten: der Prozess muss durchlaufen, ein Neustart/Re-Connect darf Slash-Commands und getrackte `bot_messages` nicht verlieren.
- **Embed-Limits:** Discord erlaubt max. 25 Felder bzw. 6000 Zeichen pro Embed. Das konsolidierte F7-Board zeigt bewusst nur die **nächsten 12 anstehenden Spiele** als Listen-Beschreibung, um sicher unter diesen Grenzen zu bleiben (nicht alle 104 Spiele).
- **Board-Recovery:** Manuell gelöschte oder von Discord verworfene Board-Nachrichten (`board:main`) müssen erkannt (Edit schlägt mit 404 fehl) und neu gepostet werden; `bot_messages` entsprechend aktualisieren.
- **Board-Cleanup beim Start:** Verwaiste, nicht mehr getrackte Board-Nachrichten des Bots (insb. Alt-Slots `board:day:*` / `board:nav` aus dem früheren Modell) werden beim Start entfernt; ausschließlich eigene Bot-Nachrichten, niemals die gültige `board:main`-Nachricht oder Nachrichten anderer Nutzer.
- **F8 — VAR-/Korrekturfälle:** Ein aberkanntes Tor senkt den Stand wieder. Der Goal-Detector darf dann **keine** Benachrichtigung auslösen und muss den gemeldeten Stand (`notified_*`) konsistent nach unten korrigieren. Offen: stilles Zurücksetzen vs. kurze Korrektur-Notiz im Channel.
- **F8 — API-Verzögerung / Lücken:** Fallen zwischen zwei Polls mehrere Tore (oder liefert die API verspätet/lückenhaft), muss die Score-Differenz als **mehrere Einzel-`GoalEvent`s** aufgelöst werden (je Tor ein Ping), nicht als ein Sammel-Update. Spielminute/Reihenfolge können dabei unpräzise sein; ggf. nur Stand statt Minute melden.
- **F8 — Recovery nach Bot-Neustart mitten im Spiel:** Der zuletzt gemeldete Stand (`notified_*`) ist persistent, damit nach einem Neustart weder bereits gemeldete Tore erneut gepingt noch in der Downtime gefallene Tore verschluckt werden. Offen: ob in der Downtime gefallene Tore beim ersten Poll nachgemeldet werden (mehrere Pings auf einmal) oder nur der Stand stillschweigend nachgezogen wird.
