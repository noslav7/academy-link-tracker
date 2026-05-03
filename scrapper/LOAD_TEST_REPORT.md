# /list cache load test report

- Run date: 2026-05-03 05:41:54 UTC
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
- Average latency, ms: 893.32
- P50, ms: 947.00
- P99, ms: 5000.00
- HTTP 200 count: 73
- HTTP 502/504 count: 0
- HTTP 500 count: 0
- Total calls: 76
- Most frequent errors: -1 / HttpTimeoutException: request timed out (3)

### POST /links

- Request: `POST /links`
- RPS: 9.57
- Average latency, ms: 1183.49
- P50, ms: 1221.00
- P99, ms: 5000.00
- HTTP 200 count: 3353
- HTTP 502/504 count: 0
- HTTP 500 count: 4
- Total calls: 3445
- Most frequent errors: -1 / HttpTimeoutException: request timed out (85); 409 / {"description":"Link already tracked: https://github.com/load/mutation/0","code":"409","exceptionName":"LinkAlreadyTrack... (1); 500 / {"timestamp":"2026-05-03T05:25:48.164Z","status":500,"error":"Internal Server Error","path":"/links"} (1); 500 / {"timestamp":"2026-05-03T05:25:48.064Z","status":500,"error":"Internal Server Error","path":"/links"} (1); 409 / {"description":"Link already tracked: https://github.com/load/mutation/6","code":"409","exceptionName":"LinkAlreadyTrack... (1)

### DELETE /links

- Request: `DELETE /links`
- RPS: 9.53
- Average latency, ms: 340.91
- P50, ms: 91.00
- P99, ms: 5000.00
- HTTP 200 count: 3344
- HTTP 502/504 count: 0
- HTTP 500 count: 0
- Total calls: 3431
- Most frequent errors: -1 / HttpTimeoutException: request timed out (78); 404 / {"description":"Link not found: https://github.com/load/mutation/11","code":"404","exceptionName":"LinkNotFoundException... (1); 404 / {"description":"Link not found: https://github.com/load/mutation/1","code":"404","exceptionName":"LinkNotFoundException"... (1); 404 / {"description":"Link not found: https://github.com/load/mutation/5","code":"404","exceptionName":"LinkNotFoundException"... (1); 404 / {"description":"Link not found: https://github.com/load/mutation/15","code":"404","exceptionName":"LinkNotFoundException... (1)

## Valkey cache

### GET /list

- Request: `GET /list`
- RPS: 0.14
- Average latency, ms: 1070.71
- P50, ms: 998.00
- P99, ms: 5000.00
- HTTP 200 count: 45
- HTTP 502/504 count: 0
- HTTP 500 count: 0
- Total calls: 49
- Most frequent errors: -1 / HttpTimeoutException: request timed out (4)

### POST /links

- Request: `POST /links`
- RPS: 5.76
- Average latency, ms: 1751.29
- P50, ms: 1339.00
- P99, ms: 5000.00
- HTTP 200 count: 1814
- HTTP 502/504 count: 0
- HTTP 500 count: 5
- Total calls: 2073
- Most frequent errors: -1 / HttpTimeoutException: request timed out (248); 409 / {"description":"Link already tracked: https://github.com/load/mutation/0","code":"409","exceptionName":"LinkAlreadyTrack... (1); 409 / {"description":"Link already tracked: https://github.com/load/mutation/14","code":"409","exceptionName":"LinkAlreadyTrac... (1); 500 / {"timestamp":"2026-05-03T05:33:29.342Z","status":500,"error":"Internal Server Error","path":"/links"} (1); 500 / {"timestamp":"2026-05-03T05:31:41.547Z","status":500,"error":"Internal Server Error","path":"/links"} (1)

### DELETE /links

- Request: `DELETE /links`
- RPS: 5.73
- Average latency, ms: 813.90
- P50, ms: 164.00
- P99, ms: 5000.00
- HTTP 200 count: 1822
- HTTP 502/504 count: 0
- HTTP 500 count: 0
- Total calls: 2063
- Most frequent errors: -1 / HttpTimeoutException: request timed out (239); 404 / {"description":"Link not found: https://github.com/load/mutation/11","code":"404","exceptionName":"LinkNotFoundException... (1); 404 / {"description":"Link not found: https://github.com/load/mutation/6","code":"404","exceptionName":"LinkNotFoundException"... (1)

## Client-side cache

### GET /list

- Request: `GET /list`
- RPS: 0.23
- Average latency, ms: 1019.41
- P50, ms: 996.00
- P99, ms: 4213.00
- HTTP 200 count: 82
- HTTP 502/504 count: 0
- HTTP 500 count: 0
- Total calls: 82
- Most frequent errors: none

### POST /links

- Request: `POST /links`
- RPS: 10.04
- Average latency, ms: 1149.05
- P50, ms: 1227.00
- P99, ms: 2844.00
- HTTP 200 count: 3602
- HTTP 502/504 count: 0
- HTTP 500 count: 3
- Total calls: 3615
- Most frequent errors: -1 / HttpTimeoutException: request timed out (7); 500 / {"timestamp":"2026-05-03T05:41:18.763Z","status":500,"error":"Internal Server Error","path":"/links"} (1); 500 / {"timestamp":"2026-05-03T05:39:18.822Z","status":500,"error":"Internal Server Error","path":"/links"} (1); 409 / {"description":"Link already tracked: https://github.com/load/mutation/6","code":"409","exceptionName":"LinkAlreadyTrack... (1); 409 / {"description":"Link already tracked: https://github.com/load/mutation/3","code":"409","exceptionName":"LinkAlreadyTrack... (1)

### DELETE /links

- Request: `DELETE /links`
- RPS: 10.01
- Average latency, ms: 296.85
- P50, ms: 113.00
- P99, ms: 1329.00
- HTTP 200 count: 3596
- HTTP 502/504 count: 0
- HTTP 500 count: 0
- Total calls: 3603
- Most frequent errors: 404 / {"description":"Link not found: https://github.com/load/mutation/5","code":"404","exceptionName":"LinkNotFoundException"... (2); 404 / {"description":"Link not found: https://github.com/load/mutation/3","code":"404","exceptionName":"LinkNotFoundException"... (1); 404 / {"description":"Link not found: https://github.com/load/mutation/1","code":"404","exceptionName":"LinkNotFoundException"... (1); 404 / {"description":"Link not found: https://github.com/load/mutation/0","code":"404","exceptionName":"LinkNotFoundException"... (1); 404 / {"description":"Link not found: https://github.com/load/mutation/6","code":"404","exceptionName":"LinkNotFoundException"... (1)

## Conclusions

- `GET /list`: Valkey mode is slower than no-cache baseline by 1.20x on average.
- `GET /list`: client-side mode is slower than no-cache baseline by 1.14x on average.
- Best `GET /list` RPS scenario: Client-side cache.
- Mutation latency (`POST /links` and `DELETE /links`) is higher than `GET /list` across modes due to DB work and cache invalidation.
- In this run, 5xx errors are absent or rare.

