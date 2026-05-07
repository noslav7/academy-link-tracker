# Scrapper Module

Инструкция по запуску, тестированию и проверке модуля `scrapper`.

## Что делает модуль

`scrapper` — сервис, который:
- хранит чаты и отслеживаемые ссылки;
- предоставляет HTTP API для регистрации чатов и управления ссылками;
- периодически проверяет обновления у внешних источников (GitHub/StackOverflow);
- автоматически отправляет уведомления в `bot`; транспорт выбирается конфигурацией, по умолчанию Kafka.

## Важно про режим работы

- Запуск `scrapper` выполняется вручную (локально или в контейнере).
- После запуска проверка ссылок работает автоматически по scheduler.
- Интервал проверки задается параметром `SCRAPPER_SCHEDULER_INTERVAL` (по умолчанию `30s`).
- Ссылки загружаются пакетами (batch), размер пакета — `SCRAPPER_SCHEDULER_BATCH_SIZE` (по умолчанию `100`, допустимо 50–500).
- Внутри каждого пакета ссылки обрабатываются параллельно — `SCRAPPER_SCHEDULER_THREAD_COUNT` потоков (по умолчанию `4`).
- По итогам каждого запуска формируется отчёт: `runId`, `processedCount`, `successCount`, `failedCount`.

## Требования

- JDK и Maven Wrapper из проекта (`mvnw`, `pom.xml`);
- запущенный `bot` для отправки уведомлений;
- доступный Kafka-кластер (по умолчанию `localhost:9092,localhost:9093,localhost:9094`);
- при необходимости Docker (для контейнерных сценариев/Testcontainers).

## Настройка

В корне репозитория создайте `scrapper/.env` (или общий `.env`) и добавьте:

```properties
DATASOURCE_URL=jdbc:postgresql://localhost:5432/link_tracker
DATASOURCE_USERNAME=postgres
DATASOURCE_PASSWORD=postgres
ACCESS_TYPE=SQL
LIQUIBASE_ENABLED=true

BOT_BASE_URL=http://localhost:8080
BOT_TRANSPORT=KAFKA
BOT_GRPC_HOST=localhost
BOT_GRPC_PORT=9090

# Kafka (отправка уведомлений по умолчанию)
KAFKA_BOOTSTRAP_SERVERS=localhost:9092,localhost:9093,localhost:9094
KAFKA_SCHEMA_REGISTRY_URL=http://localhost:8085/apis/registry/v2
KAFKA_TOPIC=link-updates
KAFKA_CLIENT_ID=scrapper
SCRAPPER_SCHEDULER_INTERVAL=30s
SCRAPPER_SCHEDULER_BATCH_SIZE=100
SCRAPPER_SCHEDULER_THREAD_COUNT=4
OUTBOX_PUBLISH_INTERVAL=1s
OUTBOX_BATCH_SIZE=100

# Опционально:
GITHUB_TOKEN=
STACKOVERFLOW_KEY=
STACKOVERFLOW_ACCESS_KEY=

# HTTP resilience для внешних API (GitHub/StackOverflow)
HTTP_CLIENT_TIMEOUT=2s
HTTP_RETRY_MAX_ATTEMPTS=3
HTTP_RETRY_DELAY=200ms
HTTP_RETRY_BACKOFF_STRATEGY=CONSTANT
HTTP_RETRY_EXPONENTIAL_MULTIPLIER=2.0
HTTP_RETRYABLE_STATUSES=500,502,503,504
```

Если хотите сменить транспорт:

- HTTP:

```properties
BOT_TRANSPORT=HTTP
```

- gRPC:

```properties
BOT_TRANSPORT=GRPC
BOT_GRPC_HOST=localhost
BOT_GRPC_PORT=9090
```

### Настройка HTTP resilience

Параметры `HTTP_*` управляют повторными попытками и timeout для внешних HTTP-вызовов:

- `HTTP_CLIENT_TIMEOUT` — общий timeout connect/read для GitHub и StackOverflow;
- `HTTP_RETRY_MAX_ATTEMPTS` — максимальное число попыток;
- `HTTP_RETRY_DELAY` — базовая пауза между попытками;
- `HTTP_RETRY_BACKOFF_STRATEGY` — `CONSTANT` или `EXPONENTIAL`;
- `HTTP_RETRY_EXPONENTIAL_MULTIPLIER` — множитель для `EXPONENTIAL`;
- `HTTP_RETRYABLE_STATUSES` — список HTTP-статусов, которые считаются временными и ретраятся.

### Поднять Postgres + миграции локально

Из корня репозитория:

```powershell
docker compose up -d postgresql liquibase-migrations
```

Миграции лежат в `migrations/` и применяются через Liquibase.
Для смены типа доступа к БД используйте `ACCESS_TYPE=SQL` или `ACCESS_TYPE=ORM`.

## Запуск

Из корня репозитория.

Windows (PowerShell):

```powershell
.\mvnw.cmd -f .\scrapper\pom.xml spring-boot:run
```

Unix/macOS:

```shell
./mvnw -f ./scrapper/pom.xml spring-boot:run
```

### Проверка fail-fast старта Kafka

При `BOT_TRANSPORT=KAFKA` сервис проверяет доступность Kafka во время инициализации producer-инфраструктуры.
Если Kafka недоступна, `scrapper` завершает запуск с `ERROR`-логом и исключением.
Для Kafka используется Avro-сериализация; схема `LinkUpdateEvent` регистрируется в Schema Registry по `KAFKA_SCHEMA_REGISTRY_URL`.

### Отправка уведомлений через Transactional Outbox

- `scrapper` не отправляет уведомления напрямую из бизнес-транзакции.
- Сначала уведомление пишется в таблицу `notification_outbox` в той же транзакции, где фиксируется обновление `tracked_links`.
- Отдельный фоновой publisher читает outbox и отправляет сообщение в выбранный транспорт (`HTTP`, `gRPC`, `KAFKA`).
- Параметры publisher-а:
  - `OUTBOX_PUBLISH_INTERVAL` — интервал публикации;
  - `OUTBOX_BATCH_SIZE` — размер батча сообщений за запуск.

Порт `scrapper` по умолчанию: `8081`.

## HTTP API (базово)

- `POST /tg-chat/{id}` — зарегистрировать чат.
- `DELETE /tg-chat/{id}` — удалить чат.
- `GET /list` + заголовок `Tg-Chat-Id` — получить список ссылок чата.
- `POST /links` + заголовок `Tg-Chat-Id` — добавить ссылку.
- `DELETE /links` + заголовок `Tg-Chat-Id` — удалить ссылку.
- `GET /tags?link=...` + заголовок `Tg-Chat-Id` — получить теги конкретной подписки.
- `POST /tags` + заголовок `Tg-Chat-Id` — добавить тег к подписке.
- `PUT /tags` + заголовок `Tg-Chat-Id` — переименовать тег подписки.
- `DELETE /tags` + заголовок `Tg-Chat-Id` — удалить тег у подписки.

Пример тела `POST /links`:

```json
{
  "link": "https://github.com/octocat/Hello-World",
  "tags": ["work"],
  "filters": []
}
```

Пример тела `DELETE /links`:

```json
{
  "link": "https://github.com/octocat/Hello-World"
}
```

Пример тела `POST /tags`:

```json
{
  "link": "https://github.com/octocat/Hello-World",
  "tag": "important"
}
```

## Как тестировать

### Автоматически

Windows:

```powershell
.\mvnw.cmd -pl scrapper -am test
```

Unix/macOS:

```shell
./mvnw -pl scrapper -am test
```

### Вручную

1. Запустите `bot`.
2. Запустите `scrapper`.
3. Зарегистрируйте чат через `POST /tg-chat/{id}`.
4. Добавьте ссылку через `POST /links`.
5. Подождите один-два интервала scheduler и проверьте, что в `bot` ушло уведомление при изменении источника (при Kafka убедитесь в доступности брокера и топика).

## Нагрузочное тестирование в Apache JMeter

Для соответствия рекомендациям можно запускать нагрузочный сценарий через Apache JMeter:

- План теста: `scrapper/src/test/jmeter/links-cache-load-test.jmx`
- SQL-подготовка данных: `scrapper/src/test/jmeter/sql/seed-load-data.sql`
- Профиль нагрузки в плане: примерно `1 x GET /list` на `100 x (POST/DELETE) /links`; `POST /links` и `DELETE /links` сохраняются отдельными sampler labels для отчета.
- Параметры запуска вынесены в `-J...` свойства (`host`, `port`, `users`, `rampUpSeconds`, `durationSeconds`, `chatCount`)

### Подготовка датасета (1000 чатов x 100 ссылок)

Перед прогоном JMeter заполните БД тестовыми данными SQL-скриптом:

```powershell
Get-Content .\scrapper\src\test\jmeter\sql\seed-load-data.sql | docker compose exec -T postgresql psql -U postgres -d link_tracker
```

Если не используете Docker Compose, выполните тот же SQL через локальный `psql`.

### Прогон в non-GUI режиме

```powershell
jmeter -n `
  -t .\scrapper\src\test\jmeter\links-cache-load-test.jmx `
  -l .\scrapper\target\jmeter\results.jtl `
  -e -o .\scrapper\target\jmeter\html-report `
  -Jjmeter.save.saveservice.output_format=csv `
  -Jjmeter.save.saveservice.print_field_names=true `
  -Jjmeter.save.saveservice.label=true `
  -Jjmeter.save.saveservice.response_code=true `
  -Jjmeter.save.saveservice.response_message=true `
  -Jjmeter.save.saveservice.elapsed=true `
  -Jjmeter.save.saveservice.timestamp_format=ms `
  -Jhost=localhost `
  -Jport=8081 `
  -Jusers=16 `
  -JrampUpSeconds=60 `
  -JdurationSeconds=300 `
  -JchatCount=1000
```

После выполнения откройте HTML-отчёт: `scrapper/target/jmeter/html-report/index.html`.

### Генерация markdown-отчёта (`LOAD_TEST_REPORT.md`) из JMeter

```powershell
.\mvnw.cmd -pl scrapper -DskipTests test-compile
.\mvnw.cmd -pl scrapper org.codehaus.mojo:exec-maven-plugin:3.5.0:java `
  -Dexec.classpathScope=test `
  -Dexec.mainClass=backend.academy.linktracker.scrapper.performance.JmeterMarkdownReportGenerator `
  -Djmeter.report.input=scrapper/target/jmeter/results.jtl `
  -Djmeter.report.output=scrapper/LOAD_TEST_REPORT.md `
  -Djmeter.report.scenario="JMeter run"
```

Итоговый markdown-отчёт будет сохранён в `scrapper/LOAD_TEST_REPORT.md`.

## Контейнерный E2E

В модуле есть контейнерные E2E-тесты в `BotScrapperContainersE2ETest`.
Они запускаются через `failsafe` на фазах `integration-test/verify` и требуют Docker.

Пример запуска:

```powershell
.\mvnw.cmd -pl scrapper -am verify
```

