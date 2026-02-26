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

## Требования

- JDK и Maven Wrapper из проекта (`mvnw`, `pom.xml`);
- запущенный `bot` для отправки уведомлений;
- при необходимости Docker (для контейнерных сценариев).

## Настройка

В корне репозитория создайте `scrapper/.env` (или общий `.env`) и добавьте:

```properties
BOT_BASE_URL=http://localhost:8080
BOT_TRANSPORT=HTTP
SCRAPPER_SCHEDULER_INTERVAL=30s

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

В модуле есть контейнерный E2E-тест `BotScrapperContainersE2ETest`.
Он запускается в составе обычного `test` и требует Docker.
