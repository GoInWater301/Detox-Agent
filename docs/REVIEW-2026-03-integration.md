# Review Notes: 2026-03 Integration

## Scope
- WebServer: dashboard aggregation, blocklist management, Thymeleaf views, env-based runtime config
- Agent: AI review gRPC server, FastAPI integration, generated proto clients
- Frontend: auth flow, dashboard pages, AI review streaming UI
- DoH: UDP timeout to TCP fallback, TLS file validation, Redis filter build fix, integration docs

## What To Check
- Registration returns a usable personal DoH URL and login stores the JWT correctly.
- Dashboard usage, domain ranking, and timeline endpoints return data for `daily`, `weekly`, and `monthly`.
- AI review streaming works through `frontend -> nginx -> webserver -> agent`.
- Blocklist changes sync both PostgreSQL and Redis.
- DoH still forwards successfully when UDP truncation or timeout happens.

## Known Follow-ups
- Frontend production bundle is above Vite's default chunk warning threshold.
- DoH local configure/build still depends on the vcpkg/Boost environment being prepared correctly.
- Agent runtime target is Python 3.13+, but local verification may still run on an older interpreter.
