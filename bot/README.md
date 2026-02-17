# Bot Module

Инструкция по сборке, запуску и базовому использованию модуля `bot`.

## Что делает модуль

`bot` — Telegram-бот, который:
- принимает команды пользователя;
- регистрирует пользователя по команде `/start`;
- показывает список команд по `/help`;
- регистрирует список команд в Telegram при старте (если это включено конфигурацией).

## Требования

- JDK и Maven-обертка версии из проекта (`pom.xml` + `mvnw`);
- доступ в интернет к Telegram Bot API;
- токен Telegram-бота от `@BotFather`.

## Шаг 1. Сборка проекта

Из корня репозитория:

Для Windows:

```shell
mvnw.cmd clean verify
```

Для Unix/macOS:

```shell
./mvnw clean verify
```

## Шаг 2. Настройка конфигурации `bot`

Основные параметры:
- `app.telegram.url` — URL Telegram API (обычно `https://api.telegram.org`);
- `app.telegram.token` — токен бота;
- `app.telegram.update-listener-sleep` — интервал polling;
- `app.telegram.debug` — debug-режим клиента;
- `app.telegram.polling-enabled` — включение polling;
- `app.telegram.set-my-commands-on-startup` — регистрация команд в Telegram.

Параметры можно передавать через `application.yml`, переменные окружения или аргументы запуска.

## Шаг 3. Запуск модуля `bot`

Из корня проекта:

Для Windows:

```shell
mvnw.cmd -pl bot -am spring-boot:run
```

Для Unix/macOS:

```shell
./mvnw -pl bot -am spring-boot:run
```

## Шаг 4. Проверка работы в Telegram

1. Откройте чат с вашим ботом.
2. Отправьте `/start`.
3. Проверьте ответ:
   - при первом вызове — приветствие и начало работы;
   - при повторном — сообщение, что регистрация уже была.
4. Отправьте `/help`.
5. Проверьте, что бот вернул список доступных команд.

## Примеры команд

```text
/start
/help
```

## Полезные команды для разработки

Запуск тестов модуля `bot`:

Для Windows:

```shell
mvnw.cmd -pl bot -am test
```

Для Unix/macOS:

```shell
./mvnw -pl bot -am test
```

