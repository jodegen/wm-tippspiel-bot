# Quickstart: Website-Hinweise in Discord-Ausgaben

## Konfiguration

Neuer, optionaler Konfigurationswert (Default leer):

```yaml
app:
  website:
    base-url: ${WEBSITE_BASE_URL:}
```

ENV im Betrieb (Docker-Compose `.env`):

```
WEBSITE_BASE_URL=https://wm.xenoria.de
```

- **Gesetzt** ⇒ Board-Footer zeigt den Hinweis, `/profil` und `/rangliste` enthalten
  klickbare Links.
- **Leer/nicht gesetzt** ⇒ alle Ausgaben rendern unverändert ohne Hinweis/Link (FR-006).
  Kein Fehler, kein Platzhalter, keine kaputten Links.

## Manuelle Verifikation

1. `WEBSITE_BASE_URL=https://wm.xenoria.de` setzen, Bot starten.
2. **Board (F11)**: Im Ranglisten-Channel zeigt der Footer
   `… · Vollständige Tabelle auf wm.xenoria.de` (reiner Text).
3. **/profil**: Befehl ausführen → am Ende ein klickbarer Link
   `…/profil/{publicId}`. Mit `/profil @anderer` zeigt der Link auf **dessen** publicId.
   Quergegen die Public-API/Website prüfen: gleiche `publicId` ⇒ gleiche Profilseite.
4. **/rangliste**: Befehl ausführen → am Ende klickbarer Link
   `https://wm.xenoria.de/leaderboard`.
5. **Leerfall**: `WEBSITE_BASE_URL=` leeren, neu starten → keine Hinweise/Links, alles
   rendert fehlerfrei.

## Tests

```
./mvnw test -Dtest=WebsiteLinksTest
```

Abgedeckt (siehe `contracts/website-links.md`):

- Profil-URL `{base}/profil/{publicId}` mit korrektem publicId (== `PublicIdService`).
- Leaderboard-URL `{base}/leaderboard`.
- Trailing-Slash-Normalisierung (mit/ohne `/` identisch).
- Leere Basis-URL ⇒ `Optional.empty()` / keine Hinweise.
- Footer-Host-Ableitung (`https://wm.xenoria.de/` → `wm.xenoria.de`).

## Sicherheits-/Konsistenz-Hinweise

- In URLs erscheint ausschließlich der nicht-reversible `publicId`, **nie** die Discord-ID.
- `PublicIdService` ist bereits Pflicht-Bean (F008): ohne `PUBLIC_API_ID_SECRET` bootet die
  App nicht — dieses Feature ändert daran nichts.
- Keine Schema-Änderung, keine neuen Channels, keine automatischen Posts.
