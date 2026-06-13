# Quickstart — Konsolidiertes Board (F7-Redesign) verifizieren

Voraussetzung: Bot läuft mit konfiguriertem `DISCORD_BOARD_CHANNEL_ID`, Spielplan
(`matches`) ist synchronisiert, DB-Migration bis Changeset 009 angewandt.

## 1. Migration & Cleanup beim Start

1. Lege vor dem Start im Board-Channel testweise mehrere Alt-/Bot-Nachrichten an
   (z. B. durch eine ältere Bot-Version oder manuelle Bot-Posts) und eine Nachricht
   eines anderen Users.
2. Starte den Bot.
3. **Erwartet**:
   - Changeset 009 entfernt `board:day:*`- und `board:nav`-Zeilen aus `bot_messages`.
   - Beim `ApplicationReadyEvent` werden **alle** eigenen Bot-Nachrichten im Channel
     (innerhalb der letzten 100) gelöscht — **außer** der getrackten `board:main`.
   - Die Nachricht des **anderen** Users bleibt erhalten.
   - Danach steht **genau eine** Board-Nachricht (`board:main`).

## 2. Konsolidiertes Board

1. Sieh dir die verbleibende Board-Nachricht an.
2. **Erwartet**:
   - **Ein** Embed mit den **nächsten bis zu 12** noch nicht angepfiffenen Spielen,
     aufsteigend nach Anstoßzeit, als zusammenhängende Liste in der Beschreibung.
   - Pro Spiel: Begegnung, Anpfiff als mitlaufender Countdown (`<t:…:R>`), 📺 Sender
     und 💰 Quote, sofern hinterlegt.
   - Look wie das Info-Embed: gleiche Akzentfarbe, Author-Header, Footer mit
     Zeitstempel, konsistente Emoji-/Struktur.
   - Keine Live-/Endstände (nur künftige Spiele).

## 3. Edit-statt-Post & Recovery

1. Warte auf einen `boardRefresh`-Lauf (oder triggere den Sync).
2. **Erwartet**: Dieselbe Nachricht wird **editiert** (Message-ID unverändert).
3. Lösche die `board:main`-Nachricht manuell, warte auf den nächsten Refresh.
4. **Erwartet**: Bot erkennt 404, postet neu, `bot_messages.board:main` zeigt die neue ID.

## 4. Filter unverändert unter dem Board

1. Wähle im Select-Menu direkt unter dem Board einen Filter („Heute", „Gruppe A", „K.o.-Runde").
2. **Erwartet**:
   - Nur **du** siehst die Antwort (ephemeral).
   - Das öffentliche `board:main`-Embed bleibt unverändert (Inhalt & Position).

## 5. Leerzustand

1. Zustand ohne anstehende Spiele simulieren (z. B. alle Kickoffs in der Vergangenheit).
2. **Erwartet**: Board zeigt einen freundlichen Leer-Hinweis statt einer leeren Liste.

## Tests (empfohlen, nicht III-pflichtig)

```bash
# Projekt nutzt Maven (mvn), nicht Gradle:
mvn test -Dtest=BoardEmbedTest     # Listenformat, Leerzustand, Truncation (<4096/<6000)
mvn test -Dtest=BoardCleanupTest   # Prädikat: eigene Nachricht & != board:main → löschen
```
