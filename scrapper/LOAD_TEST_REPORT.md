# /list cache load test report

- Run date: 2026-05-02 09:42:16 UTC
- Concurrent users: 16 (~2 * logical CPU)
- Dataset: 1000 chats, 100 links per chat, total 100000 links
- Ramp-up: 1 min
- Stage: 5 min
- Operation ratio `GET /list` : `POST/DELETE /links` = 1:100
- Note: task text mentions `POST /list`, but the actual API endpoint is `POST /links` (`DELETE /links` for remove).

## No cache

### GET /list

- Request: `GET /list`
- RPS: 0.21
- Average latency, ms: 885.78
- P50, ms: 919.00
- P99, ms: 5000.00
- HTTP 200 count: 71
- HTTP 502/504 count: 0
- HTTP 500 count: 0
- Total calls: 74
- Most frequent errors: -1 / HttpTimeoutException: request timed out (3)

### POST /links

- Request: `POST /links`
- RPS: 9.26
- Average latency, ms: 1210.35
- P50, ms: 1248.00
- P99, ms: 5000.00
- HTTP 200 count: 3231
- HTTP 502/504 count: 0
- HTTP 500 count: 1
- Total calls: 3335
- Most frequent errors: -1 / HttpTimeoutException: request timed out (96); 409 / {"description":"Link already tracked: https://github.com/load/mutation/0","code":"409","exceptionName":"LinkAlreadyTrack... (1); 409 / {"description":"Link already tracked: https://github.com/load/mutation/6","code":"409","exceptionName":"LinkAlreadyTrack... (1); 409 / {"description":"Link already tracked: https://github.com/load/mutation/1","code":"409","exceptionName":"LinkAlreadyTrack... (1); 409 / {"description":"Link already tracked: https://github.com/load/mutation/12","code":"409","exceptionName":"LinkAlreadyTrac... (1)

## Valkey cache

### GET /list

- Request: `GET /list`
- RPS: 0.20
- Average latency, ms: 986.79
- P50, ms: 1099.00
- P99, ms: 5000.00
- HTTP 200 count: 69
- HTTP 502/504 count: 0
- HTTP 500 count: 0
- Total calls: 72
- Most frequent errors: -1 / HttpTimeoutException: request timed out (3)

### POST /links

- Request: `POST /links`
- RPS: 8.97
- Average latency, ms: 1258.01
- P50, ms: 1244.00
- P99, ms: 5000.00
- HTTP 200 count: 3128
- HTTP 502/504 count: 0
- HTTP 500 count: 6
- Total calls: 3229
- Most frequent errors: -1 / HttpTimeoutException: request timed out (95); 500 / {"timestamp":"2026-05-02T09:32:34.386Z","status":500,"error":"Internal Server Error","path":"/links"} (2); 500 / {"timestamp":"2026-05-02T09:32:34.403Z","status":500,"error":"Internal Server Error","path":"/links"} (1); 500 / {"timestamp":"2026-05-02T09:32:34.324Z","status":500,"error":"Internal Server Error","path":"/links"} (1); 500 / {"timestamp":"2026-05-02T09:32:34.391Z","status":500,"error":"Internal Server Error","path":"/links"} (1)

## Client-side cache

### GET /list

- Request: `GET /list`
- RPS: 0.23
- Average latency, ms: 811.06
- P50, ms: 847.00
- P99, ms: 1999.00
- HTTP 200 count: 81
- HTTP 502/504 count: 0
- HTTP 500 count: 0
- Total calls: 81
- Most frequent errors: none

### POST /links

- Request: `POST /links`
- RPS: 10.11
- Average latency, ms: 1196.58
- P50, ms: 1334.00
- P99, ms: 2093.00
- HTTP 200 count: 3638
- HTTP 502/504 count: 0
- HTTP 500 count: 0
- Total calls: 3638
- Most frequent errors: none

## Conclusions

- `GET /list`: Valkey mode is slower than no-cache baseline by 1.11x on average.
- `GET /list`: client-side mode is faster than no-cache baseline by 1.09x on average.
- Best `GET /list` RPS scenario: Client-side cache.
- Mutation latency (`POST /links`) is higher than `GET /list` across modes due to DB work and cache invalidation.
- In this run, 5xx errors are absent or rare.

