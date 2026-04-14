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

## Документация по сервисам

Корневой `README` — индекс проекта. Детали запуска, сценарии и примеры вынесены в модульные README:

- `bot/README.md` — запуск `bot`, Telegram-команды, сценарии ручной проверки.
- `scrapper/README.md` — запуск `scrapper`, HTTP API, примеры `curl`, E2E примечания.

## Полезные ссылки

- Общая справка: `HELP.md`
- Модуль `bot`: `bot/README.md`
- Модуль `scrapper`: `scrapper/README.md`

