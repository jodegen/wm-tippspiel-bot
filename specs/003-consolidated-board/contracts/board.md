# Phase 1 — Contracts: Konsolidiertes Board (F7-Redesign)

Interne Java-Kontrakte (kein externes API). Signaturen sind Zielzustand; bestehende
Aufrufer der unveränderten Pfade (Filter) bleiben kompatibel.

## EmbedStyle (NEU) — gemeinsamer Styling-Helper

```java
@Component
public class EmbedStyle {
    // Marken-Akzentfarbe (gemeinsame "Familie" für Info & Board)
    public static final Color ACCENT = new Color(0xF1C40F);
    public static final String DIVIDER = "▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬";

    // Vorgestylter Builder: Akzentfarbe + Author-Header + Footer-Text + Timestamp(now)
    EmbedBuilder base(String title);

    // Variante ohne Author-Zeile (z. B. ephemerale Filteransichten), gleiche Akzentfarbe/Footer
    EmbedBuilder bare(String title);
}
```

**Vertrag**:
- `base(title)` setzt: `setColor(ACCENT)`, `setAuthor("FIFA WM 2026 · 11. Juni – 19. Juli")`,
  `setTitle(title)`, `setFooter("Alle Zeiten in Europe/Berlin · …")`,
  `setTimestamp(clock.instant())`.
- Nutzt die injizierte `Clock` (Prinzip IV) — kein `Instant.now()` im Code.
- `InfoEmbed` MUSS nach Umstellung dasselbe sichtbare Ergebnis liefern wie zuvor.

## BoardEmbed (GEÄNDERT)

```java
@Component
public class BoardEmbed {
    // NEU: konsolidiertes Board — EIN Embed, Liste in description, defensive Truncation
    MessageEmbed buildBoard(List<Match> upcoming);

    // UNVERÄNDERT: ephemerale Filteransicht (Tag/Gruppe/K.o.) — darf Live/Endstand zeigen
    MessageEmbed buildFiltered(String title, List<Match> matches);

    // ENTFÄLLT: buildDay(LocalDate, List<Match>)  (Tages-Slot-Modell abgelöst)
}
```

**Vertrag `buildBoard`**:
- Baut auf `EmbedStyle.base(...)` (Info-Look).
- Pro Spiel ein Block: `**Heim vs Gast**` + Zeile mit Anpfiff-Relative-Timestamp,
  optional `📺 <Sender>` (nur wenn gesetzt), optional `💰 H/U/A` (nur wenn alle drei Quoten gesetzt).
- **Truncation**: Beschreibung wird beim Aufbau überwacht; überschreitet das Hinzufügen des
  nächsten Blocks `SAFE_DESC_LIMIT = 4000`, wird abgebrochen und `… und N weitere` angehängt.
  Beschreibung ≤ 4096, Gesamtembed ≤ 6000 (garantiert durch das Limit + kurze Chrome).
- **Leerzustand**: ist `upcoming` leer, freundlicher Hinweis (z. B. „Aktuell keine
  anstehenden Spiele. 🏁") statt leerer Liste (FR-009).
- Zeigt **keine** Live-/Endstände (nur Countdown).

## BoardService (GEÄNDERT)

```java
@Service
public class BoardService {
    static final String BOARD_KEY = "board:main";   // einziger Slot
    static final int    UPCOMING_LIMIT = 12;
    static final int    CLEANUP_SCAN = 100;

    // bestehend (vom boardRefresh-Job aufgerufen): rendert/aktualisiert die EINE Nachricht
    void refresh();

    // NEU: am ApplicationReadyEvent — verwaiste eigene Nachrichten entfernen, dann refresh()
    @EventListener(ApplicationReadyEvent.class) void onStartup();
}
```

**Vertrag `refresh()`**:
- Lädt `matches.findUpcoming(clock.instant(), UPCOMING_LIMIT)`.
- Edit-in-place der getrackten `board:main`-Nachricht **mit** `setComponents(navigation.actionRow())`
  (Embed + Filter zusammen). Fehlt die Nachricht (404 `UNKNOWN_MESSAGE`) → Neu-Post mit denselben
  Components, `bot_messages.upsert("board:main", …)`.
- Existiert noch kein `board:main` → Neu-Post.
- Channel fehlt/unkonfiguriert/keine Rechte → Warnung loggen, kein Abbruch.

**Vertrag `onStartup()`** (Reihenfolge wichtig):
1. `jda.awaitReady()`; Channel auflösen (sonst Warnung + return).
2. Getrackte `board:main`-Message-ID laden (falls vorhanden).
3. Letzte `CLEANUP_SCAN` (=100) Nachrichten lesen; jede Nachricht löschen, für die gilt:
   `author == jda.getSelfUser()` **und** `messageId != board:main-Message-ID`.
4. `refresh()` aufrufen (postet/aktualisiert `board:main`).
- Cleanup DARF die gültige `board:main`-Nachricht nicht löschen; fremde Nachrichten nie.
- Fehler beim Löschen einzelner Nachrichten werden geloggt, brechen den Start nicht ab.

## BotMessageRepository (ERWEITERT)

```java
// NEU – für Migration/Recovery-Komfort (optional, falls im Code benötigt)
void deleteByKey(String key);
```
- `findByKey` / `upsert` bleiben unverändert. Die eigentliche Alt-Slot-Migration läuft über
  Changeset 009, nicht über diese Methode.

## Unveränderte Kontrakte (Regressionsschutz)

- `BoardNavigation.actionRow()` / `FILTER_ID` — identisch.
- `InteractionListener.onStringSelectInteraction` Routing auf `BoardFilterHandler` — identisch.
- `BoardFilterHandler.handle(...)` — identisch (ephemerale Antwort, `buildFiltered`).
- `BoardRefreshJob` — Trigger/Intervall unverändert; ruft weiterhin `boardService.refresh()`.
