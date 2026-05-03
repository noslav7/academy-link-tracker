# /list cache load test report (JMeter)

- Run date: 2026-05-03 03:15:53 UTC
- Source JTL: `target/jmeter/results-valkey.jtl`
- Duration window, sec: 299.88

## Valkey cache

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

