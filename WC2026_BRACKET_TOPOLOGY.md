# WM 2026 — Statische Bracket-Topologie (für das Backend)

Diese Datei beschreibt die feste, von der FIFA vordefinierte K.o.-Struktur der WM
2026. Sie ändert sich nie — nur welche Teams die Slots füllen, ändert sich im
Turnierverlauf. Sie dient als statische Definition im Backend (NICHT in der DB),
gegen die die `matches`-Einträge gemappt werden.

## Grundlagen (durch Recherche bestätigt)

- Fünf K.o.-Runden + Spiel um Platz 3. football-data.org-`stage`-Enum:
  `LAST_32` (16 Spiele) → `LAST_16` (8) → `QUARTER_FINALS` (4) → `SEMI_FINALS`
  (2) → `FINAL` (1), plus `THIRD_PLACE` (1).
- FIFA nummeriert die K.o.-Spiele fortlaufend als **Match 73–104** in fester
  chronologischer Reihenfolge. Match 73 = erstes K.o.-Spiel, Match 104 = Finale.
- football-data.org liefert KEINE Match-Nummern und KEINE Bracket-Kanten. Das
  Mapping erfolgt über `stage` + `kickoff`-Reihenfolge (Tie-Breaker: `id`).
  Das n-te LAST_32-Spiel nach kickoff entspricht FIFA-Match (72 + n).
- Slots mit Drittplatzierten stehen erst nach Abschluss aller 72 Gruppenspiele
  fest (FIFA-Kombinationsmatrix). Bis dahin Platzhalter-Labels anzeigen.

## Slot-Index-Konvention

Innerhalb jeder Stage werden die Spiele nach `kickoff` (Tie-Breaker `id`)
sortiert und ab 1 durchnummeriert. Dieser Slot-Index + die FIFA-Match-Nummer
sind die stabile Referenz für die Kanten unten.

---

## LAST_32 (Sechzehntelfinale) — FIFA-Match 73–88

Slot-Index (kickoff-sortiert) → FIFA-Match-Nr → Paarung (Platzhalter bis Teams feststehen)

| Slot | Match | Heim (Platzhalter) | Auswärts (Platzhalter) |
|------|-------|--------------------|------------------------|
| 1  | 73 | Sieger Gruppe A | Zweiter Gruppe B |
| 2  | 74 | Sieger Gruppe E | Dritter A/B/C/D/F |
| 3  | 75 | Sieger Gruppe F | Zweiter Gruppe C |
| 4  | 76 | Sieger Gruppe C | Zweiter Gruppe F |
| 5  | 77 | Sieger Gruppe I | Dritter C/D/F/G/H |
| 6  | 78 | Zweiter Gruppe E | Zweiter Gruppe I |
| 7  | 79 | Sieger Gruppe A | Dritter C/E/F/H/I |
| 8  | 80 | Sieger Gruppe L | Dritter E/H/I/J/K |
| 9  | 81 | Sieger Gruppe D | Dritter B/E/F/I/J |
| 10 | 82 | Sieger Gruppe G | Dritter A/E/H/I/J |
| 11 | 83 | Zweiter Gruppe K | Zweiter Gruppe L |
| 12 | 84 | Sieger Gruppe H | Zweiter Gruppe J |
| 13 | 85 | Sieger Gruppe B | Dritter E/F/G/I/J |
| 14 | 86 | Sieger Gruppe J | Zweiter Gruppe H |
| 15 | 87 | Sieger Gruppe K | Dritter D/E/I/J/L |
| 16 | 88 | Zweiter Gruppe D | Zweiter Gruppe G |

> Hinweis: Die exakte Zuordnung der Drittplatzierten-Gruppen hängt von der
> FIFA-Kombinationsmatrix ab und konkretisiert sich nach der Gruppenphase. Die
> Platzhalter oben entsprechen der offiziellen Schema-Notation. Für die reine
> Baum-DARSTELLUNG sind die Kanten (wer-trifft-auf-Sieger-von) das Entscheidende;
> die genauen Gruppenbuchstaben sind nur das Label.

## LAST_16 (Achtelfinale) — FIFA-Match 89–96

Jedes Spiel = Sieger zweier LAST_32-Spiele (per FIFA-Match-Nr referenziert).

| Slot | Match | Heim = Sieger | Auswärts = Sieger |
|------|-------|---------------|-------------------|
| 1 | 89 | Match 74 | Match 77 |
| 2 | 90 | Match 73 | Match 75 |
| 3 | 91 | Match 76 | Match 78 |
| 4 | 92 | Match 79 | Match 80 |
| 5 | 93 | Match 83 | Match 84 |
| 6 | 94 | Match 81 | Match 82 |
| 7 | 95 | Match 86 | Match 88 |
| 8 | 96 | Match 85 | Match 87 |

## QUARTER_FINALS (Viertelfinale) — FIFA-Match 97–100

| Slot | Match | Heim = Sieger | Auswärts = Sieger |
|------|-------|---------------|-------------------|
| 1 | 97  | Match 89 | Match 90 |
| 2 | 98  | Match 93 | Match 94 |
| 3 | 99  | Match 91 | Match 92 |
| 4 | 100 | Match 95 | Match 96 |

## SEMI_FINALS (Halbfinale) — FIFA-Match 101–102

| Slot | Match | Heim = Sieger | Auswärts = Sieger |
|------|-------|---------------|-------------------|
| 1 | 101 | Match 97 | Match 98 |
| 2 | 102 | Match 99 | Match 100 |

## THIRD_PLACE (Spiel um Platz 3) — FIFA-Match 103

| Slot | Match | Heim = Verlierer | Auswärts = Verlierer |
|------|-------|------------------|----------------------|
| 1 | 103 | Match 101 | Match 102 |

## FINAL — FIFA-Match 104

| Slot | Match | Heim = Sieger | Auswärts = Sieger |
|------|-------|---------------|-------------------|
| 1 | 104 | Match 101 | Match 102 |

---

## Wie das Backend das nutzt

1. Lade alle K.o.-Spiele aus `matches` (stage != GROUP_STAGE).
2. Pro Stage nach `kickoff` (Tie-Breaker `id`) sortieren → ergibt Slot-Index 1..n.
3. Slot-Index → FIFA-Match-Nr (LAST_32: 72+slot, LAST_16: 88+slot, QF: 96+slot,
   SF: 100+slot, THIRD_PLACE: 103, FINAL: 104).
4. Über die Kanten-Tabellen oben den Baum verdrahten: jedes Spiel kennt seine zwei
   Quell-Matches (per FIFA-Nr) und sein Ziel-Match.
5. Teams/Ergebnis aus `matches` einsetzen; wo noch unbekannt, Platzhalter-Label.
6. Gewinner-Fortschritt aus `home_score`/`away_score` berechnen (bei Remis →
   Verlängerung/Elfmeter; football-data liefert den finalen `winner` im score-
   Objekt, das ihr ggf. mitführen müsst).

## WICHTIGE VERIFIKATIONS-AUFGABE

Die Kanten oben sind aus offiziellen Quellen (FIFA, Sky, ESPN) zusammengetragen
und in sich konsistent (Halbbaum-Logik geht sauber auf). ABER: Bevor das live
geht, sollten die Slot→kickoff-Zuordnungen einmal gegen die ECHTEN Daten in eurer
`matches`-Tabelle geprüft werden, sobald die K.o.-Spiele dort eingetragen sind —
besonders, ob die kickoff-Reihenfolge tatsächlich der FIFA-Match-Nummer 73..104
entspricht. Falls football-data eine abweichende Reihenfolge hat, nur die
Slot→Match-Zuordnung in Schritt 3 anpassen; die Kanten-Logik bleibt gleich.
