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
| `home_score` / `away_score` | INT | Endstand (null bis Abpfiff) |
| `status` | TEXT | SCHEDULED / IN_PLAY / FINISHED |
| `revealed` | BOOLEAN | Tipps bereits offengelegt? |
| `evaluated` | BOOLEAN | Punkte bereits vergeben? |

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
| `key` | TEXT (PK) | Logischer Slot, z. B. `board:today`, `board:day:2026-06-14`, `board:nav` |
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

**Punkteschema:**
- **3 Punkte** — exaktes Ergebnis
- **1 Punkt** — richtige Tendenz (Sieger bzw. Unentschieden), aber falsches Ergebnis
- **0 Punkte** — daneben

### F6 — Leaderboard
**Command:** `/rangliste`
Aggregiert Gesamtpunkte je User, sortiert absteigend. Zusatzspalten: Anzahl abgegebener Tipps, Anzahl exakter Treffer (Tie-Breaker).

### F7 — Live-Spielplan-Board (selbst-aktualisierend)
Statt den Spielplan nur on-demand per Command anzuzeigen, hält der Bot in einem dedizierten, für Mitglieder read-only Channel (z. B. `#wm-spielplan`) ein dauerhaftes Board, das er per **Edit** aktuell hält — nicht durch wiederholtes Posten.

**Mechanik:**
- Der Bot postet beim ersten Start je Board-Slot eine Nachricht und speichert deren `message_id` in `bot_messages`. Bei jedem Sync wird die bestehende Nachricht **editiert** statt neu gepostet, damit der Channel sauber bleibt und das Board ortsfest steht.
- Slots werden nach Tag aufgeteilt (z. B. „Heute" + die nächsten 2–3 Tage), weil 104 Spiele die Embed-Limits sprengen. Ein Slot = eine Nachricht = ein Embed.
- Anzeige je Spiel: Begegnung, Anstoßzeit als Discord-Relative-Timestamp (`<t:UNIX:R>`, läuft client-seitig als Countdown), TV-Sender, Quoten (falls vorhanden), und sobald vorhanden der Live-/Endstand.
- Existiert eine getrackte `message_id` nicht mehr (manuell gelöscht), wird sie neu gepostet und in `bot_messages` aktualisiert.

**Interaktive Filter (Stufe 2, Standard):**
- Unter dem Board hängt eine Navigations-Komponente: ein Select-Menu bzw. Buttons („Heute", „Morgen", „Gruppe A–L", „K.o.-Runde").
- Auswahl löst eine **ephemeral** Antwort aus (nur für die klickende Person sichtbar) mit der gefilterten Ansicht. Das öffentliche Board bleibt dabei unverändert.
- Damit ersetzt F7 die manuellen Commands F1/F2 für den Alltag — diese bleiben als Fallback/Direktzugriff erhalten.

**Wichtige Voraussetzung:** Interaktive Komponenten (Buttons/Select-Menus) erfordern einen dauerhaft per Gateway verbundenen Bot, der Interaction-Events empfängt. Reine `@Scheduled`-Jobs genügen dafür nicht (sie können nur über REST editieren/posten). Die Architektur muss also einen laufenden Gateway-Listener vorsehen, nicht nur Cron.

---

## Hintergrund-Jobs (Spring `@Scheduled`)

| Job | Intervall | Aufgabe |
|---|---|---|
| `syncMatches` | z. B. alle 15 Min | Spielplan & Ergebnisse von football-data.org holen, `matches` upserten |
| `syncOdds` | z. B. alle 6 Std | Quoten von The Odds API holen, per Teamnamen-Heuristik matchen (API-Limits beachten) |
| `revealJob` | minütlich | Tipps anstehender Spiele offenlegen (F4) |
| `evaluateJob` | minütlich | Beendete Spiele auswerten (F5) |
| `boardRefresh` | nach jedem `syncMatches` (bzw. alle 15 Min) | Live-Board-Nachrichten editieren (F7); bei laufenden Spielen optional häufiger für Live-Stände |

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
- **Embed-Limits:** Discord erlaubt max. 25 Felder bzw. 6000 Zeichen pro Embed und 10 Embeds pro Nachricht. Bei 104 Spielen niemals alles in eine Nachricht — F7-Slot-Aufteilung nach Tag/Spieltag ist Pflicht, nicht optional.
- **Board-Recovery:** Manuell gelöschte oder von Discord verworfene Board-Nachrichten müssen erkannt (Edit schlägt mit 404 fehl) und neu gepostet werden; `bot_messages` entsprechend aktualisieren.
