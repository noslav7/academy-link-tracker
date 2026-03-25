# Scrapper Module

Инструкция по запуску, тестированию и проверке модуля `scrapper`.

## Что делает модуль

`scrapper` — сервис, который:
- хранит чаты и отслеживаемые ссылки;
- предоставляет HTTP API для регистрации чатов и управления ссылками;
- периодически проверяет обновления у внешних источников (GitHub/StackOverflow);
- автоматически отправляет уведомления в `bot` по HTTP или gRPC.

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
- при необходимости Docker (для контейнерных сценариев).

## Настройка

В корне репозитория создайте `scrapper/.env` (или общий `.env`) и добавьте:

```properties
DATASOURCE_URL=jdbc:postgresql://localhost:5432/link_tracker
DATASOURCE_USERNAME=postgres
DATASOURCE_PASSWORD=postgres
ACCESS_TYPE=SQL
LIQUIBASE_ENABLED=true

BOT_BASE_URL=http://localhost:8080
BOT_TRANSPORT=HTTP
SCRAPPER_SCHEDULER_INTERVAL=30s
SCRAPPER_SCHEDULER_BATCH_SIZE=100
SCRAPPER_SCHEDULER_THREAD_COUNT=4

# Опционально:
GITHUB_TOKEN=
STACKOVERFLOW_KEY=
STACKOVERFLOW_ACCESS_KEY=
```

Если используете gRPC-канал до `bot`:

```properties
BOT_TRANSPORT=GRPC
BOT_GRPC_HOST=localhost
BOT_GRPC_PORT=9090
```

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

Порт `scrapper` по умолчанию: `8081`.

## HTTP API (базово)

- `POST /tg-chat/{id}` — зарегистрировать чат.
- `DELETE /tg-chat/{id}` — удалить чат.
- `GET /links` + заголовок `Tg-Chat-Id` — получить список ссылок чата.
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
5. Подождите один-два интервала scheduler и проверьте, что в `bot` ушло уведомление при изменении источника.

## Контейнерный E2E

В модуле есть контейнерные E2E-тесты в `BotScrapperContainersE2ETest`.
Они запускаются в составе обычного `test` и требуют Docker.
