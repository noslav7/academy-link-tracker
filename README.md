# LinkTracker

`LinkTracker` — multi-module приложение из сервисов:

- `scrapper` — хранит подписки и проверяет обновления внешних ссылок;
- `bot` — принимает команды в Telegram и отправляет пользователю уведомления.

## Архитектура на уровне ролей

1. Пользователь отправляет команду в `bot`.
2. `bot` вызывает API `scrapper` для регистрации/управления подписками.
3. `scrapper` по расписанию проверяет изменения во внешних источниках.
4. При изменениях `scrapper` отправляет уведомление обратно в `bot`.
5. `bot` доставляет уведомление в Telegram-чат.

## Требования

- JDK версии, указанной в `pom.xml` проекта;
- Maven Wrapper из репозитория (`mvnw`, `mvnw.cmd`);
- Kafka-кластер, доступный по `KAFKA_BOOTSTRAP_SERVERS` (по умолчанию `localhost:9092,localhost:9093,localhost:9094`) — уведомления теперь по умолчанию идут через Kafka;
- Schema Registry, доступный по `KAFKA_SCHEMA_REGISTRY_URL` (по умолчанию `http://localhost:8085/apis/registry/v2`) для хранения Avro-схем сообщений;
- Docker Desktop (для Testcontainers/blackbox/E2E тестов).

## Быстрый старт

Из корня репозитория:

Windows PowerShell:

```powershell
.\mvnw.cmd spotless:apply
.\mvnw.cmd verify
.\mvnw.cmd -am -pl scrapper,bot package
```

Unix/macOS:

```shell
./mvnw spotless:apply
./mvnw verify
./mvnw -am -pl scrapper,bot package
```

## Запуск из IDE

Приложение запускается напрямую из IDE без дополнительных зависимостей, кроме заранее поднятой инфраструктуры через Docker Compose.

1. Поднимите инфраструктуру одним набором сервисов:

```powershell
docker compose up -d `
  postgresql liquibase-migrations `
  kafka-1 kafka-2 kafka-3 schema-registry `
  valkey-node-1 valkey-node-2 valkey-node-3 valkey-cluster-init
```

2. Запустите `scrapper` и `bot` из IDE (классы `ScrapperApplication` и `BotApplication`) или через Maven-команды из модульных README.

3. Для локальной отказоустойчивости Valkey используется кластер из 3 нод; данные хранятся в именованных volumes `valkey-node-*-data` (переживают рестарты контейнеров).

## Поведение при старте и Kafka

- Для `scrapper` при `BOT_TRANSPORT=KAFKA` включена проверка доступности Kafka на старте; при недоступности кластера запуск завершается с ошибкой.
- Для `bot` при недоступности Kafka во время инициализации Kafka-инфраструктуры запуск завершается с `ERROR`-логом и исключением (fail-fast).
- Kafka-консьюмер в `bot` можно отключить параметром `APP_KAFKA_ENABLED=false` (по умолчанию включен).

## Документация по сервисам

Корневой `README` — индекс проекта. Детали запуска, сценарии и примеры вынесены в модульные README:

- [`bot/README.md`](bot/README.md) — запуск `bot`, Telegram-команды, сценарии ручной проверки.
- [`scrapper/README.md`](scrapper/README.md) — запуск `scrapper`, HTTP API, примеры `curl`, E2E примечания.

## Сценарии и примеры

- Сценарий диалога `/track`: [`bot/README.md`](bot/README.md)
- Примеры HTTP-запросов для `scrapper`: [`scrapper/README.md`](scrapper/README.md)
- Контейнерные E2E-проверки: [`scrapper/README.md`](scrapper/README.md)

## Полезные ссылки

- Общая справка: [`HELP.md`](HELP.md)
- Модуль `bot`: [`bot/README.md`](bot/README.md)
- Модуль `scrapper`: [`scrapper/README.md`](scrapper/README.md)

