# Contract: Website-Link- und Footer-Formate

Dieses Feature exponiert keine programmatische API. Der testbare „Kontrakt" ist das
exakte Format der erzeugten URLs/Texte in den drei Discord-Ausgaben. Diese Formate sind
die Grundlage der Unit-Tests (`WebsiteLinksTest`).

Annahme für Beispiele: `app.website.base-url = https://wm.xenoria.de`.

## C1 — Leaderboard-Profil-URL (/profil)

```
{base}/profil/{publicId}
```

- Beispiel: `https://wm.xenoria.de/profil/Ab12Cd34Ef56Gh78Ij90Kl`
- `publicId` = `PublicIdService.publicId(discordUserId)` (22 Zeichen, Base64Url, ohne Padding).
- Rendering: klickbarer Markdown-Link am Ende der `/profil`-Embed-Description bzw. als
  abschließendes Feld, z. B. `🔗 [Profil auf wm.xenoria.de ansehen](…)`.

## C2 — Leaderboard-Tabellen-URL (/rangliste)

```
{base}/leaderboard
```

- Beispiel: `https://wm.xenoria.de/leaderboard`
- Rendering: klickbare Markdown-Link-Zeile am Ende der `/rangliste`-Embed-Description,
  z. B. `[Vollständige Tabelle ansehen](https://wm.xenoria.de/leaderboard)`.

## C3 — Board-Footer-Hinweis (F11)

```
{bestehender Footer} · Vollständige Tabelle auf {host}
```

- Beispiel: `Alle Zeiten in Europe/Berlin · Vollständige Tabelle auf wm.xenoria.de`
- `host` = Basis-URL ohne Schema/Trailing-Slash. **Nicht klickbar** (Discord-Footer = Text).

## C4 — Normalisierung (FR-008)

| Eingabe `base-url` | `…/leaderboard` |
|--------------------|------------------|
| `https://wm.xenoria.de`  | `https://wm.xenoria.de/leaderboard` |
| `https://wm.xenoria.de/` | `https://wm.xenoria.de/leaderboard` |

Erwartung: kein doppelter Slash, kein fehlender Trenner — identisches Ergebnis mit und
ohne abschließenden `/`.

## C5 — Leerwert-Verhalten (FR-006)

| Eingabe `base-url` | Ergebnis |
|--------------------|----------|
| `""` / nur Whitespace / nicht gesetzt | `leaderboardUrl()/profileUrl()/footerHint()` → `Optional.empty()`; Embeds rendern **ohne** Hinweis/Link, fehlerfrei, kein Platzhalter. |

## Akzeptanzbezug

- C1 ↔ FR-002/FR-003/FR-009, SC-002
- C2 ↔ FR-004
- C3 ↔ FR-001, SC-001
- C4 ↔ FR-008
- C5 ↔ FR-006, SC-003
