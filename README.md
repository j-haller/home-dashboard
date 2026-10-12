# Home Dashboard

A home dashboard built with Spring Boot and React that displays real-time information including train departures, Philips Hue light controls, waste collection calendar, weather forecast, and a WiFi QR code.

## Features

- **Stationboard** — live Swiss train departures via [transport.opendata.ch](https://transport.opendata.ch)
- **Philips Hue** — light group status, toggle controls, and brightness slider via the Hue Bridge API v2
- **Weather** — current conditions plus hourly and daily forecast via [Open-Meteo](https://open-meteo.com)
- **Waste collection calendar** — upcoming pickup dates via [openerz.metaodi.ch](https://openerz.metaodi.ch) and [mr-green.ch](https://mr-green.ch)
- **WiFi QR code** — scan-to-connect QR code for guests
- **Settings** — in-app UI to change station, ZIP, and weather coordinates; persisted in SQLite across restarts

## Architecture

The backend pushes real-time data to the browser via a single SSE endpoint with named events. Each service polls its upstream API independently and caches the latest data for SSE clients.

```
backend/                        # Spring Boot (Java 21, WebFlux, Maven)
  src/main/java/dev/haller/dashboard/
    config/                     # AppProperties, WebClientConfig
    controller/                 # SseController, LightController, SettingsController
    repository/                 # SettingsRepository (SQLite via JdbcClient)
    service/                    # CalendarService, LightService, StationboardService, WifiService, SettingsService
    model/                      # CalendarData, WifiData, TriggerLightRequest

frontend/                       # React 18 + Vite + TypeScript + Bootstrap
  src/
    hooks/useSse.ts             # EventSource hook for named SSE events
    components/                 # StationboardTab, LightsTab, WeatherTab, CalendarTab, WifiTab, SettingsTab
    types/                      # Shared TypeScript interfaces
```

## Environment Variables

Create a `.env` file in the project root:

```env
STATION_NAME=                  # Swiss transport station name (e.g. "Zurich HB")
HUE_API_KEY=                   # Philips Hue API key
HUE_BRIDGE_ADDRESS=            # Fallback IP for the Hue Bridge (used if discovery fails)
ZIP=                           # Swiss postal code for waste collection calendar
WEATHER_LATITUDE=              # Latitude for weather forecast (e.g. 47.37)
WEATHER_LONGITUDE=             # Longitude for weather forecast (e.g. 8.54)
WIFI_SSID=                     # WiFi network name for QR code
WIFI_PASSWORD=                 # WiFi password for QR code
```

## API

### REST

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/trigger-light` | Toggle or dim a light group. Body: `{ "id": "<group-id>", "on": true\|false, "brightness": 0–100 }` |
| `GET` | `/api/settings` | Returns current user settings (DB value or env var fallback) |
| `PATCH` | `/api/settings` | Update one or more settings. Body: `{ "station": "…", "zip": "…", "latitude": "…", "longitude": "…" }` |

### SSE

Single endpoint `GET /sse` with named events:

| Event | Update interval | Description |
|-------|----------------|-------------|
| `stationboard` | 10 seconds | Next train departures |
| `groups` | 1 hour | Hue light groups (rebuilds UI) |
| `light` | 1 second | Real-time Hue light state |
| `weather` | 15 minutes | Current conditions, hourly and daily forecast |
| `calendar` | 1 hour | Upcoming waste collection dates |
| `wifi` | One-shot | WiFi QR code data |

## Docker

### Development

```sh
docker compose --profile dev up --build
```

Starts nginx (port `8080`), Spring Boot backend, and the Vite dev server with hot module replacement.

### Production

```sh
docker compose --profile prod up --build
```

Builds the React app and serves it as static files via nginx alongside the Spring Boot backend. Exposed on port `8080`.

### Persistence

User settings are stored in a SQLite database at `/data/settings.db` inside the backend container. The `settings-data` named Docker volume keeps this file across container restarts and rebuilds. Settings changed via the UI take effect on the next poll cycle for each service.
