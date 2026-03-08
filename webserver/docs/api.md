# API Documentation

## Swagger UI
- URL: `http://localhost:8080/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`

## gRPC Service
- Service: `dns.analytics.v1.DnsAnalyticsService`
- Method: `StreamQueries(stream DnsQueryEvent) returns (Ack)`

### DnsQueryEvent
- `user_id` (string)
- `queried_domain` (string)
- `timestamp_us` (int64)
- `response_time_ms` (int64)

### Ack
- `accepted_count` (int64)
- `rejected_count` (int64)

## REST Endpoints
Base path: `/api/dashboard`

### GET `/users/{userId}/usage`
- Query: `period` = `daily|weekly|monthly` (default `daily`)
- Response: `UsageStatsDto`

### GET `/users/{userId}/domains`
- Query: `startDate` (optional, `yyyy-MM-dd`), `endDate` (optional, `yyyy-MM-dd`)
- Response: `Flux<DomainUsageDto>`

### GET `/users/{userId}/timeline`
- Query: `period` = `daily|weekly|monthly` (default `daily`)
- Response: `Flux<TimelineStatsDto>`

## AI Review Streaming (SSE)

### POST `/api/ai/review/stream`
- `Content-Type: application/json`
- `Accept: text/event-stream`
- Purpose: Sends prompt + usage DTO to AI agent gRPC and relays streamed tokens to client using SSE.

Request body example:
```json
{
  "sessionId": "sess-001",
  "prompt": "Review this user's DNS usage pattern",
  "usage": {
    "userId": "user-123",
    "period": "daily",
    "totalQueries": 154,
    "uniqueDomains": 12,
    "topDomains": [
      {
        "domain": "example.com",
        "requestCount": 50,
        "firstAccess": 1741400000000000,
        "lastAccess": 1741403600000000,
        "totalDuration": 120000000,
        "averageResponseTimeMs": 15
      }
    ]
  }
}
```

SSE event types:
- `token`: partial token payload
- `done`: stream finished
- `error`: stream failed

curl example:
```bash
curl -N -X POST http://localhost:8080/api/ai/review/stream \
  -H 'Content-Type: application/json' \
  -H 'Accept: text/event-stream' \
  -d '{
    "sessionId":"sess-001",
    "prompt":"Summarize suspicious domains",
    "usage":{"userId":"user-123","period":"daily","totalQueries":10,"uniqueDomains":2,"topDomains":[]}
  }'
```
