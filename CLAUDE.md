# CLAUDE.md

## After every change

- Check if `README.md` needs updating (features, env vars, API table, SSE events). Propose changes if so.
- Check if `.env.example` needs updating when env vars are added, removed, or renamed.

## Target display

1024×576 touchscreen. All UI must be usable at that resolution with touch input — no hover-only interactions, tap targets large enough. Scrolling is enabled both horizontally and vertically; ideally, all content fits within the display without overflow.

## Dependencies and packages

This project runs in Docker. Add dependencies inside the container:
- **Frontend**: add to `frontend/package.json`, rebuilt via `docker compose ... --build`
- **Backend**: add to `backend/pom.xml`, rebuilt via `docker compose ... --build`

Never install packages on the host for runtime use.

## Running the project

```sh
docker compose --profile dev up --build   # dev: nginx :8080, Vite HMR, Spring Boot
docker compose --profile prod up --build  # prod: static build served via nginx :8080
```

## Architecture

- **Backend**: Spring Boot 3.3.5, Java 21, WebFlux (reactive). Single SSE endpoint `GET /sse` merges named event streams from independent services.
- **Frontend**: React 18, TypeScript (strict), Bootstrap 5, Vite. `useSse` hook registers named EventSource listeners.
- **Routing**: nginx proxies `/api/*` and `/sse` to backend, everything else to frontend.

## Code conventions

**TypeScript**: strict mode with `noUnusedLocals` and `noUnusedParameters` — no unused symbols. Components take typed data props; shared types in `src/types/index.ts`. Pattern: null-check → loading state → render.

**Java/WebFlux**: reactive throughout (`Mono`/`Flux`). Services self-contained: each handles polling, caching, error resilience, and event emission. Use `onErrorResume`/`onErrorReturn` to keep streams alive on upstream failure. Constructor injection only.

**No tests, no linter config.** Don't add test scaffolding unless asked.

## SSE events reference

| Event | Interval | Source |
|-------|----------|--------|
| `stationboard` | 10 s | transport.opendata.ch |
| `groups` | 1 h | Hue Bridge /clip/v2 |
| `light` | 1 s | Hue Bridge SSE stream |
| `weather` | 15 min | Open-Meteo |
| `calendar` | 1 h | openerz / mr-green |
| `wifi` | one-shot | local config |

## Philips Hue

Uses Hue API **v2** (`/clip/v2`). Auth via `HUE_API_KEY` header (`hue-application-key`). Light control targets `grouped_light` resources, supports `on` toggle and `brightness` (0–100).
