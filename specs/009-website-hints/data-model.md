# Phase 1 Data Model: Website-Hinweise

Dieses Feature führt **keine** persistenten Entitäten und **kein** DB-Schema ein. Die
„Modelle" sind reine Konfigurations- und Laufzeit-Hilfskonstrukte.

## Konfigurations-„Entität": Website

| Feld | Typ | Quelle | Pflicht | Beschreibung |
|------|-----|--------|---------|--------------|
| `baseUrl` | `String` | `app.website.base-url` (ENV `WEBSITE_BASE_URL`) | Nein (Default `""`) | Basis-URL der öffentlichen Website, z. B. `https://wm.xenoria.de`. Leer ⇒ Hinweise/Links werden ausgelassen. |

Bindung: neues nested record `Website(String baseUrl)` in `AppProperties` (Prefix `app`,
`@ConfigurationPropertiesScan` bereits aktiv).

## Laufzeit-Helfer: WebsiteLinks (zustandslos)

Kapselt Normalisierung und Link-/Hinweis-Erzeugung. Eingaben aus `AppProperties.Website`
und `PublicIdService`.

| Methode | Rückgabe | Verhalten |
|---------|----------|-----------|
| `boolean isConfigured()` | — | `true`, wenn `baseUrl` nicht blank. |
| `Optional<String> leaderboardUrl()` | klickbare URL | `{base}/leaderboard`; leer, wenn nicht konfiguriert. |
| `Optional<String> profileUrl(String discordUserId)` | klickbare URL | `{base}/profil/{publicId}` mit `publicId = PublicIdService.publicId(discordUserId)`; leer, wenn nicht konfiguriert. |
| `Optional<String> footerHint()` | Footer-Text-Zusatz | z. B. `"Vollständige Tabelle auf {host}"` (Host als Klartext); leer, wenn nicht konfiguriert. |

**Normalisierungsregeln (FR-008)**:

- Genau ein abschließender `/` der Basis-URL wird entfernt, bevor Pfade angehängt werden
  (kein doppelter Slash, kein fehlender Trenner).
- Pfadsegmente sind feste Konstanten: `/leaderboard`, `/profil/{publicId}`.
- `host` für den Footer wird aus der Basis-URL abgeleitet (Schema/`www`/Trailing-Slash
  entfernt), z. B. `https://wm.xenoria.de/` → `wm.xenoria.de`.

## Identitäts-/Sicherheitsregeln

- Der in URLs verwendete Identifier ist ausschließlich der **öffentliche `publicId`**
  (HMAC-SHA256 über die interne User-ID, gekürzt — Feature 008). **Keine** Discord-ID,
  E-Mail, Token oder Anzeigename in URLs/Texten (FR-009, Datenschutz).
- `publicId` ist deterministisch und stabil über Anzeigenamen-Umbenennungen ⇒ Discord-Link
  und Website-Profil zeigen auf dieselbe Seite.

## State / Lifecycle

Keine Zustände, keine Übergänge, keine Volumen-/Skalierungsannahmen (synchroner
String-Aufbau zur Embed-Erzeugung).
