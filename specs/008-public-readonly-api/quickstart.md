# Quickstart — Öffentliche Read-only-API (Feature 008)

Voraussetzung: laufende PostgreSQL mit Tippspiel-Daten und die üblichen
`SPRING_DATASOURCE_*`-Variablen. Neu für dieses Feature:

```bash
# HMAC-Secret für den öffentlichen Spieler-Identifier (PFLICHT, kein Default)
export PUBLIC_API_ID_SECRET="<langes-zufalls-secret>"
# Frontend-Domain(s) für CORS (kommagetrennt)
export PUBLIC_API_CORS_ALLOWED_ORIGINS="https://<dein-projekt>.vercel.app"
# optional
export SERVER_PORT=8080
export PUBLIC_API_CACHE_TTL_SECONDS=5
```

Starten:

```bash
mvn spring-boot:run        # Discord-Bot + HTTP-Server (Tomcat) im selben Prozess
```

## Endpoints prüfen (alle GET, ohne Auth)

```bash
BASE=http://localhost:8080/api/public

# 1) Spielplan (vollständig + gefiltert)
curl -s $BASE/schedule | jq '.[0]'
curl -s "$BASE/schedule?stage=GROUP_STAGE&group=A" | jq length
curl -s "$BASE/schedule?matchday=1" | jq length

# 2) Live-Spiele (leere Liste, wenn nichts läuft)
curl -s $BASE/matches/live | jq

# 3) Leaderboard
curl -s $BASE/leaderboard | jq '.[0]'

# 4) Tipps eines Spiels — Reveal-Gate
#    vor Anpfiff:  { "released": false, "tips": [] }
#    nach Anpfiff: { "released": true,  "tips": [ … ] }
curl -s $BASE/matches/<MATCH_ID>/tips | jq

# 5) Spielerprofil über publicId (NICHT die Discord-ID)
curl -s $BASE/players/<PUBLIC_ID> | jq
```

## Akzeptanz-/Sicherheits-Checks (entsprechen den Success Criteria)

- **SC-001 (kein Leak)**: In keiner Antwort taucht `user_id`, `email` oder
  `token` auf:
  ```bash
  curl -s $BASE/leaderboard | jq 'tostring | test("user_id|email|token")'  # → false
  ```
- **SC-002 (Reveal)**: Für ein noch nicht angepfiffenes Spiel ist
  `released=false` und `tips=[]` — keine Namen/Ergebnisse im JSON.
- **SC-006 (read-only)**: nicht-GET wird abgewiesen:
  ```bash
  curl -s -o /dev/null -w "%{http_code}" -X POST $BASE/schedule   # → 405
  ```
- **CORS**: Preflight liefert den erlaubten Origin zurück:
  ```bash
  curl -s -D - -o /dev/null -X OPTIONS $BASE/leaderboard \
    -H "Origin: https://<dein-projekt>.vercel.app" \
    -H "Access-Control-Request-Method: GET" | grep -i access-control-allow-origin
  ```

## Regressionsschutz (bestehende Features)

```bash
mvn test     # bestehende 119 Tests bleiben grün; nicht-Web-@SpringBootTests
             # starten dank webEnvironment=NONE keinen Tomcat
```

Erwartung: Discord-Bot (JDA-Gateway), `@Scheduled`-Jobs und Punktewertung
verhalten sich unverändert — der HTTP-Layer ist rein additiv und read-only.
