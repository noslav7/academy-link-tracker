# /list cache load test report (JMeter)

- Run dates: 2026-05-03 02:50:56-03:28:42 UTC
- Tool: Apache JMeter 5.6.3
- Concurrent users: 16 (~2 * logical CPU)
- Dataset: 1000 chats, 100 links per chat, total 100000 links
- Ramp-up: 60 sec
- Stage: 300 sec
- Operation ratio `GET /list` : `POST /links` : `DELETE /links` ~= 1:51:50
- Scenarios: no cache, Valkey cache, Valkey client-side cache
- Note: task text mentions `POST /list`, but the actual Scrapper API endpoint for adding a tracked link is `POST /links`; removing a tracked link is `DELETE /links`.

## Summary

| Scenario | Total calls | Combined RPS | GET avg, ms | GET P99, ms | POST avg, ms | POST P99, ms | DELETE avg, ms | DELETE P99, ms | HTTP 500 | HTTP 502/504 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| No cache | 61563 | 205.28 | 66.00 | 182.00 | 106.09 | 173.00 | 33.91 | 95.00 | 0 | 0 |
| Valkey cache | 58790 | 196.05 | 114.99 | 228.00 | 106.49 | 168.00 | 38.95 | 104.00 | 0 | 0 |
| Client-side cache | 60715 | 202.47 | 122.01 | 252.00 | 102.23 | 161.00 | 38.18 | 103.00 | 0 | 0 |

## No Cache

- Source report: `scrapper/LOAD_TEST_REPORT_NONE.md`
- Source JTL: `scrapper/target/jmeter/results-none.jtl`
- Run date: 2026-05-03 02:50:56 UTC
- Duration window, sec: 299.89

### GET /list

- Request: `GET /list`
- RPS: 2.00
- Average latency, ms: 66.00
- P50, ms: 61.00
- P99, ms: 182.00
- HTTP 200 count: 601
- HTTP 502/504 count: 0
- HTTP 500 count: 0
- Total calls: 601
- Most frequent errors: none

### POST /links

- Request: `POST /links`
- RPS: 102.49
- Average latency, ms: 106.09
- P50, ms: 100.00
- P99, ms: 173.00
- HTTP 200 count: 30735
- HTTP 502/504 count: 0
- HTTP 500 count: 0
- Total calls: 30735
- Most frequent errors: none

### DELETE /links

- Request: `DELETE /links`
- RPS: 100.79
- Average latency, ms: 33.91
- P50, ms: 29.00
- P99, ms: 95.00
- HTTP 200 count: 30227
- HTTP 502/504 count: 0
- HTTP 500 count: 0
- Total calls: 30227
- Most frequent errors: none

## Valkey Cache

- Source report: `scrapper/LOAD_TEST_REPORT_VALKEY.md`
- Source JTL: `scrapper/target/jmeter/results-valkey.jtl`
- Run date: 2026-05-03 03:15:53 UTC
- Duration window, sec: 299.88

### GET /list

- Request: `GET /list`
- RPS: 1.92
- Average latency, ms: 114.99
- P50, ms: 115.00
- P99, ms: 228.00
- HTTP 200 count: 575
- HTTP 502/504 count: 0
- HTTP 500 count: 0
- Total calls: 575
- Most frequent errors: none

### POST /links

- Request: `POST /links`
- RPS: 98.24
- Average latency, ms: 106.49
- P50, ms: 100.00
- P99, ms: 168.00
- HTTP 200 count: 29461
- HTTP 502/504 count: 0
- HTTP 500 count: 0
- Total calls: 29461
- Most frequent errors: none

### DELETE /links

- Request: `DELETE /links`
- RPS: 95.89
- Average latency, ms: 38.95
- P50, ms: 33.00
- P99, ms: 104.00
- HTTP 200 count: 28754
- HTTP 502/504 count: 0
- HTTP 500 count: 0
- Total calls: 28754
- Most frequent errors: none

## Client-Side Cache

- Source report: `scrapper/LOAD_TEST_REPORT_CLIENT_SIDE.md`
- Source JTL: `scrapper/target/jmeter/results-client-side.jtl`
- Run date: 2026-05-03 03:28:42 UTC
- Duration window, sec: 299.88

### GET /list

- Request: `GET /list`
- RPS: 1.98
- Average latency, ms: 122.01
- P50, ms: 123.00
- P99, ms: 252.00
- HTTP 200 count: 593
- HTTP 502/504 count: 0
- HTTP 500 count: 0
- Total calls: 593
- Most frequent errors: none

### POST /links

- Request: `POST /links`
- RPS: 101.72
- Average latency, ms: 102.23
- P50, ms: 96.00
- P99, ms: 161.00
- HTTP 200 count: 30504
- HTTP 502/504 count: 0
- HTTP 500 count: 0
- Total calls: 30504
- Most frequent errors: none

### DELETE /links

- Request: `DELETE /links`
- RPS: 98.77
- Average latency, ms: 38.18
- P50, ms: 32.00
- P99, ms: 103.00
- HTTP 200 count: 29618
- HTTP 502/504 count: 0
- HTTP 500 count: 0
- Total calls: 29618
- Most frequent errors: none

## Conclusions

- All three scenarios completed without HTTP 500/502/504 responses and without application-level errors in JMeter samples.
- In this write-heavy profile, cache modes do not improve `GET /list` latency because reads are about 1% of traffic and every mutation invalidates the affected chat cache.
- The no-cache scenario has the best `GET /list` latency in this run: 66.00 ms average and 182 ms P99.
- Client-side cache has the best `POST /links` latency: 102.23 ms average and 161 ms P99.
- No cache has the best `DELETE /links` latency: 33.91 ms average and 95 ms P99.
- Plain Valkey cache is the slowest scenario by combined RPS in this run, while client-side cache is close to no cache on write throughput but still slower on `GET /list`.
- In a write-heavy workload, caching the links list does not pay off; evaluating the potential benefit of this cache requires a separate read-heavy benchmark with cache warm-up and hit-rate metrics.
