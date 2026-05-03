# /list cache load test report (JMeter)

- Run date: 2026-05-03 02:50:56 UTC
- Source JTL: `target/jmeter/results-none.jtl`
- Duration window, sec: 299.89

## No cache

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

