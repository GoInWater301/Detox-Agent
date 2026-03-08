# DNS Analytics Features

## Overview
This service ingests DNS query events via gRPC client-side streaming, tracks near real-time usage in Redis, and persists events/snapshots in PostgreSQL.

## Implemented Components
- gRPC stream endpoint: `DnsAnalyticsService/StreamQueries`
- Redis cache and counters:
  - `usage:{userId}:{domain}` domain hash
  - `usage:index:user:{userId}` domain index set
  - `stats:daily|weekly|monthly:{userId}:{bucket}` period stats hash
- PostgreSQL persistence:
  - `dns_usage_events` raw event records
  - `usage_snapshots` aggregated snapshot records
- WebFlux dashboard APIs:
  - `/api/dashboard/users/{userId}/usage`
  - `/api/dashboard/users/{userId}/domains`
  - `/api/dashboard/users/{userId}/timeline`
- Scheduled aggregation every 60 seconds from Redis stats to PostgreSQL snapshots.

## Runtime Stack
- Spring Boot WebFlux
- Spring gRPC server/client
- Redis Reactive (`ReactiveStringRedisTemplate`)
- R2DBC PostgreSQL
- Swagger UI (`springdoc-openapi`)

## AI Review Tunnel
- New gRPC contract: `AiAgentReviewService/StreamReview`
- Input payload includes `UsageDto` mirrored from dashboard usage DTO.
- WebFlux SSE endpoint `/api/ai/review/stream` forwards each gRPC token chunk to SSE clients.
- Chosen transport to user: SSE (keeps server push simple and avoids WebSocket overhead for one-way token stream).
