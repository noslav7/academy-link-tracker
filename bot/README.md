# Bot Module

Инструкция по запуску, тестированию и ручной проверке модуля `bot`.

## Что делает модуль

`bot` — Telegram-бот, который:
- обрабатывает команды пользователя;
- регистрирует чат через `scrapper` по команде `/start`;
- показывает справку по `/help`;
- запускает диалог добавления ссылки по `/track`;
- удаляет ссылку из отслеживания по `/untrack <url>`;
- показывает отслеживаемые ссылки по `/list` (опционально с фильтром по тегу: `/list <tag>`);
- прерывает текущий диалог по `/cancel`;
- принимает уведомления об обновлениях от `scrapper`; транспорт задаётся конфигурацией, по умолчанию Kafka-консьюмер.

## Требования

- JDK и Maven Wrapper из проекта (`mvnw`, `pom.xml`);
- токен Telegram-бота от `@BotFather`;
- доступ в интернет к Telegram API;
- запущенный `scrapper` для команд `/track`, `/untrack`, `/list` и регистрации чата;
- доступный Kafka-кластер (по умолчанию `localhost:9092,localhost:9093,localhost:9094`) для приёма уведомлений.

## Настройка

1. В корне репозитория создайте файл `bot/.env`.
2. Добавьте минимум:

```properties
TELEGRAM_TOKEN=ваш_токен_из_BotFather

# Куда ходить за scrapper (для REST/gRPC команд)
SCRAPPER_BASE_URL=http://localhost:8081
SCRAPPER_TRANSPORT=HTTP
SCRAPPER_GRPC_HOST=localhost
SCRAPPER_GRPC_PORT=9091

# Kafka для получения уведомлений (по умолчанию используется)
KAFKA_BOOTSTRAP_SERVERS=localhost:9092,localhost:9093,localhost:9094
KAFKA_TOPIC=link-updates
KAFKA_GROUP_ID=bot-link-updates
KAFKA_CLIENT_ID=bot
```

`bot` автоматически читает `bot/.env` через `spring.config.import` в `bot/src/main/resources/application.yaml`.

## Запуск

Из корня репозитория.

Windows (PowerShell):

```powershell
.\mvnw.cmd -f .\bot\pom.xml spring-boot:run
```

Unix/macOS:

```shell
./mvnw -f ./bot/pom.xml spring-boot:run
```

Опционально, запуск с переопределением параметров:

```powershell
.\mvnw.cmd -f .\bot\pom.xml spring-boot:run "-Dspring-boot.run.arguments=--app.telegram.debug=true --app.telegram.update-listener-sleep=2s --app.telegram.polling-enabled=true --app.telegram.set-my-commands-on-startup=true --app.scrapper.transport=HTTP"
```

## Команды в Telegram

```text
/start
/help
/track
/untrack <url>
/list
/list <tag>
/cancel
```

### Сценарий `/track`

1. Отправьте `/track`.
2. Бот запросит ссылку.
3. Отправьте URL (например, `https://github.com/octocat/Hello-World`).
4. Бот запросит теги. Отправьте CSV (`работа, баг`) или `-`.
5. Бот запросит фильтры. Отправьте CSV (`author=me, label=bug`) или `-`.
6. Бот добавит ссылку в `scrapper` и вернет результат.

Во время активного сценария можно прервать процесс командой `/cancel`.

## Как тестировать

### Автоматически

Windows:

```powershell
.\mvnw.cmd -pl bot -am test
```

Unix/macOS:

```shell
./mvnw -pl bot -am test
```

### Проверка "вручную"

1. Запустите `scrapper`.
2. Запустите `bot`.
3. В Telegram проверьте:
   - `/start` (первый и повторный вызовы);
   - `/help` (список команд актуален);
   - полный сценарий `/track`;
   - `/list` и `/list <tag>`;
   - `/untrack <url>`;
   - `/cancel` в активном и неактивном состоянии.

## Примечания

- Если `scrapper` недоступен, команды, завязанные на него (`/track`, `/untrack`, `/list`), будут возвращать сообщения об ошибке.
- Порт `bot` по умолчанию: `8080`.

