# Bot Usage

## Требования

- JDK 25+
- Maven 3.9.12+ (или запуск через `mvnw`)

## Запуск Bot модуля

1. Перейдите в каталог проекта:

```shell
cd hw6-link-tracker
```

2. Укажите токен Telegram бота одним из способов:

- Локально в файле `bot/.env`:

```properties
TELEGRAM_TOKEN=<your_bot_token>
```

- Или через переменную окружения:

```shell
set TELEGRAM_TOKEN=<your_bot_token>
```

3. Запустите сервис бота:

```shell
mvnw.cmd -pl bot -am spring-boot:run
```

Сервис стартует на порту `8080`.

## Полезные команды

- Запуск тестов bot модуля:

```shell
mvnw.cmd -pl bot -am test
```

- Полная проверка проекта:

```shell
mvnw.cmd clean verify
```

