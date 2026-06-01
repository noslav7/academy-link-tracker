# OBSERVABILITY

Документ описывает мониторинг для `bot` и `scrapper` в MR-2:

- инфраструктура: `Prometheus + Pushgateway + Grafana`;
- визуализации: RED, память, бизнес-метрики;
- фильтры дашбордов: `application` и `app_type` (`bot`/`scrapper`).

## 1) Подключенные метрики в приложениях

### Стандартные (Micrometer/Spring Boot Actuator)

- `http_server_requests_seconds_*` - HTTP RED-метрики (`count`, `sum`, `bucket`);
- `jvm_memory_used_bytes`, `jvm_memory_max_bytes` - память JVM;
- `process_resident_memory_bytes`, `process_virtual_memory_bytes` - память процесса;
- инфраструктурные метрики JVM/процесса/системы (`jvm_gc_*`, `process_*`, `system_*`, `up` и др.).

### Кастомные метрики `scrapper`

- `links_on_track_total{tracked_source="github|stackoverflow"}` - число ссылок на мониторинге;
- `api_requests_total{source="..."}` - число входящих API-запросов;
- `api_requests_errors_total{source="..."}` - число ошибочных API-запросов;
- `request_duration_ms_total_bucket{scope="...",scope_type="..."}` - гистограмма длительности операций.

Для `scrape`-визуализации используется:

- `scope="external_source"`
- `scope_type="github|stackoverflow"`

### Кастомные метрики `bot`

- `command_requests_total{command="..."}` - число команд (пользовательских сообщений-команд);
- `command_duration_ms_total_bucket{scope="...",scope_type="..."}` - гистограмма длительности bot-операций;
- `sent_notification_total` - число отправленных уведомлений.

Для визуализации "время обработки операций в bot":

- `scope=~"scrapper_sync_api|scrapper_async_api"`

## 2) PromQL-запросы дашбордов

Полный набор хранится в `example_pql.txt`.

Ниже сгруппировано по панелям.

### Dashboard: LinkTracker - RED & Memory

- Request rate (RPS):
  - `sum by (application, status) (rate(http_server_requests_seconds_count{application=~"$application", app_type=~"$app_type", uri!="/metrics"}[1m]))`
- Error rate (5xx):
  - `sum by (application) (rate(http_server_requests_seconds_count{application=~"$application", app_type=~"$app_type", status=~"5..", uri!="/metrics"}[5m])) / clamp_min(sum by (application) (rate(http_server_requests_seconds_count{application=~"$application", app_type=~"$app_type", uri!="/metrics"}[5m])), 1e-9)`
- Duration p95:
  - `histogram_quantile(0.95, sum by (application, le) (rate(http_server_requests_seconds_bucket{application=~"$application", app_type=~"$app_type", uri!="/metrics"}[5m])))`
- JVM memory used by area:
  - `sum by (application, area) (jvm_memory_used_bytes{application=~"$application", app_type=~"$app_type"})`
- Process memory RSS/VIRT:
  - `sum by (application) (process_resident_memory_bytes{application=~"$application", app_type=~"$app_type"})`
  - `sum by (application) (process_virtual_memory_bytes{application=~"$application", app_type=~"$app_type"})`

### Dashboard: LinkTracker - Business Metrics

- User messages per second:
  - `sum by (application, command) (rate(command_requests_total{application=~"$application", app_type=~"$app_type"}[1m]))`
- Active tracked links by domain:
  - `max by (application, tracked_source) (links_on_track_total{application=~"$application", app_type=~"$app_type"})`
- Scrape duration p50/p95/p99:
  - `histogram_quantile(0.50, sum by (application, scope_type, le) (rate(request_duration_ms_total_bucket{application=~"$application", app_type=~"$app_type", scope="external_source", scope_type=~"github|stackoverflow"}[5m])))`
  - `histogram_quantile(0.95, sum by (application, scope_type, le) (rate(request_duration_ms_total_bucket{application=~"$application", app_type=~"$app_type", scope="external_source", scope_type=~"github|stackoverflow"}[5m])))`
  - `histogram_quantile(0.99, sum by (application, scope_type, le) (rate(request_duration_ms_total_bucket{application=~"$application", app_type=~"$app_type", scope="external_source", scope_type=~"github|stackoverflow"}[5m])))`
- Bot operation duration p50/p95/p99:
  - `histogram_quantile(0.50, sum by (application, scope_type, le) (rate(command_duration_ms_total_bucket{application=~"$application", app_type=~"$app_type", scope=~"scrapper_sync_api|scrapper_async_api"}[5m])))`
  - `histogram_quantile(0.95, sum by (application, scope_type, le) (rate(command_duration_ms_total_bucket{application=~"$application", app_type=~"$app_type", scope=~"scrapper_sync_api|scrapper_async_api"}[5m])))`
  - `histogram_quantile(0.99, sum by (application, scope_type, le) (rate(command_duration_ms_total_bucket{application=~"$application", app_type=~"$app_type", scope=~"scrapper_sync_api|scrapper_async_api"}[5m])))`
- Telegram requests to bot:
  - `sum by (application) (rate(command_requests_total{application=~"$application", app_type=~"$app_type"}[1m]))`
- Sent notifications:
  - `sum by (application) (rate(sent_notification_total{application=~"$application", app_type=~"$app_type"}[1m]))`

## 3) Запуск мониторинга

### 3.1 Поднять инфраструктуру

Из корня репозитория:

```powershell
docker compose up -d `
  postgresql liquibase-migrations `
  kafka-1 kafka-2 kafka-3 schema-registry `
  valkey-node-1 valkey-node-2 valkey-node-3 valkey-cluster-init `
  pushgateway prometheus grafana
```

### 3.2 Запустить приложения

Запустить `scrapper` и `bot` из IDE (или Maven-командами из модульных `README`).

Если Telegram API недоступен в вашей сети, запускайте `bot` в локальном режиме без Telegram:

```properties
TELEGRAM_POLLING_ENABLED=false
TELEGRAM_SET_MY_COMMANDS_ON_STARTUP=false
KAFKA_ENABLED=false
```

Эти параметры добавляются в `bot/.env` и позволяют поднять `bot` для целей мониторинга (метрики и дашборды) без внешних вызовов в Telegram.

Важно:

- `bot` должен отдавать `/metrics` на `8011`;
- `scrapper` должен отдавать `/metrics` на `8081`.

Prometheus скрапит приложения через `host.docker.internal`:

- `host.docker.internal:8011/metrics` (`bot`);
- `host.docker.internal:8081/metrics` (`scrapper`).

### 3.3 Проверка DoD

1. `docker compose up` выполнен, контейнеры `prometheus/grafana/pushgateway` в `Up`;
2. Prometheus targets `bot`, `scrapper`, `pushgateway` имеют статус `UP`;
3. Grafana доступна на `http://localhost:3000` (`admin/admin`);
4. Дашборды автоматически подхватились из provisioning:
   - `LinkTracker - RED & Memory`;
   - `LinkTracker - Business Metrics`;
5. Файл `example_pql.txt` заполнен запросами для панелей;
6. Этот документ заполнен и описывает метрики, запросы и запуск.

## 4) Alerting (RAM)

В provisioning добавлено правило:

- файл: `observability/grafana/provisioning/alerting/ram-alerts.yml`;
- алерт: `LinkTracker RAM usage is high`;
- условие: RAM > 85% в течение 5 минут;
- метрика: `process_resident_memory_bytes` относительно `jvm_memory_max_bytes{area="heap"}`.

При необходимости в UI Grafana можно добавить Contact point (например, Telegram) и Notification policy без изменения репозитория.

## 5) Что приложить в MR (не коммитить в репозиторий)

- скриншоты графиков/панелей и результатов запросов;
- экспорт JSON дашбордов из Grafana (как артефакт описания MR).

