# Phase 0 Research: Website-Hinweise in Discord-Ausgaben

Alle offenen Entscheidungen sind aufgelöst; es verbleiben keine `NEEDS CLARIFICATION`.

## D1 — Konfiguration der Website-Basis-URL

- **Decision**: Neuer Wert `app.website.base-url`, gebunden über ein neues nested record
  `Website(String baseUrl)` in `AppProperties` (`@ConfigurationPropertiesScan` ist bereits
  aktiv). In `application.yml`: `base-url: ${WEBSITE_BASE_URL:}` (Default leer).
- **Rationale**: Folgt dem etablierten Muster (alle externen Werte unter `app.*`, ENV-
  Override, Geheimnisse/Config nicht hartkodiert — Verfassung). Default leer ⇒ FR-006:
  ohne Konfiguration werden Hinweise ausgelassen statt zu brechen.
- **Alternatives considered**: Wiederverwendung von `app.public-api.public-base-url` —
  verworfen, da dies die **API**-Host-URL (`api.xenoria.de`) für die OpenAPI-Server-URL
  ist und semantisch von der **Website** (`wm.xenoria.de`) getrennt bleiben muss.

## D2 — Board-Footer-Hinweis ohne Bruch bestehender Embeds

- **Decision**: `LeaderboardBoardEmbed` überschreibt den Footer-Text gezielt für das Board
  (z. B. `"Alle Zeiten in Europe/Berlin · Vollständige Tabelle auf <host>"`), nur wenn eine
  Basis-URL konfiguriert ist; sonst bleibt der bestehende Footer unverändert. Der Hinweis
  zeigt den **Host als Klartext** (`wm.xenoria.de`), da Discord-Embed-**Footer keine
  klickbaren Links** unterstützen.
- **Rationale**: `EmbedStyle.FOOTER_BASE` ist von mehreren Embeds (Info/Board) geteilt;
  eine globale Änderung würde fremde Embeds mit verändern. Lokales Überschreiben im
  Board-Embed hält den Effekt eingegrenzt (FR-007/keine Seiteneffekte).
- **Alternatives considered**: `FOOTER_BASE` global erweitern — verworfen (betrifft auch
  Info-Embed u. a.). Eigenes Embed-Feld statt Footer — verworfen, Spec verlangt explizit
  den Footer für das Board.

## D3 — `/profil`-Profil-URL aus öffentlichem Identifier

- **Decision**: `ProfilCommand` berechnet `publicId = PublicIdService.publicId(target.getId())`
  und reicht die fertige URL `{base}/profil/{publicId}` an `ProfilEmbed` durch. Der Link
  wird als **klickbarer Markdown-Link** in einem abschließenden Feld/Zeile der Description
  gerendert (Description/Fields unterstützen Markdown, anders als der Footer). Fester
  Wortlaut: `🔗 [Profil auf {host} ansehen]({profileUrl})` (konsistent mit Contract C1).
- **Rationale**: Identitätsgleichheit mit den Public-Endpoints (FR-009), keine Discord-ID
  in der URL (nicht reversibel). `PublicIdService` ist bereits Pflicht-Bean (F008) und
  damit verfügbar; kein neuer Code für die ID-Ableitung.
- **Alternatives considered**: Discord-ID oder Anzeigename in der URL — verworfen
  (Datenschutz / Instabilität bei Umbenennung). Eigener Identifier — verworfen
  (Duplizierung, Inkonsistenz zur Website).

## D4 — `/rangliste`-Link

- **Decision**: `RanglisteEmbed` hängt — wenn Basis-URL gesetzt — eine abschließende
  Markdown-Link-Zeile (`[Vollständige Tabelle ansehen]({base}/leaderboard)`) an die
  Description an. `RanglisteCommand` reicht den Helfer/URL durch.
- **Rationale**: Description ist klickbar; minimaler, additiver Eingriff.
- **Alternatives considered**: Separater Footer wie beim Board — inkonsistent, da hier ein
  echter klickbarer Link sinnvoll und möglich ist.

## D5 — URL-Normalisierung (Trailing Slash) & Leerwert-Verhalten

- **Decision**: Zentraler, zustandsloser Helfer `WebsiteLinks` trimmt einen evtl.
  abschließenden `/` der Basis-URL und setzt Pfade kontrolliert zusammen (FR-008). Bei
  leerer/blank Basis-URL liefern die Methoden `Optional.empty()` bzw. werden Hinweise
  weggelassen (FR-006). Der Host-Klartext für den Footer wird aus der Basis-URL abgeleitet.
- **Rationale**: Eine einzige normalisierende Stelle ⇒ konsistent und gut unit-testbar.
- **Alternatives considered**: Normalisierung in jedem Embed einzeln — verworfen
  (Duplizierung, Fehleranfälligkeit).

## Querschnitt

- **Keine neue Persistenz / kein Liquibase-Changeset / keine neuen Dependencies.**
- **Keine Berührung von Punktewertung oder Reveal-Timing** ⇒ Verfassung Prinzip III
  (Test-First-Pflicht) nicht ausgelöst; Unit-Tests für die Helfer dennoch vorgesehen.
