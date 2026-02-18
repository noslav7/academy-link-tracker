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

Для Windows (PowerShell):

```powershell
.\mvnw.cmd clean verify
```

Для Unix/macOS:

```shell
./mvnw clean verify
```

## Шаг 2. Настройка конфигурации `bot`

Выполните следующие действия:

1. Получите токен у `@BotFather` (если еще не получали).
2. В корне репозитория создайте файл `bot/.env`.
3. Добавьте в `bot/.env` строку:

```properties
TELEGRAM_TOKEN=ваш_токен_из_BotFather
```

4. Сохраните файл и убедитесь, что токен не попадает в git.

Бот читает этот файл автоматически через `spring.config.import` из `bot/src/main/resources/application.yaml`.

Обычно этого достаточно для запуска (рекомендуется):

Для Windows (PowerShell):

```powershell
.\mvnw.cmd -f .\bot\pom.xml spring-boot:run
```

Если нужно переопределить параметры на запуске, используйте аргументы Spring Boot (опционально, пример).

Для Windows (PowerShell):

```powershell
.\mvnw.cmd -f .\bot\pom.xml spring-boot:run "-Dspring-boot.run.arguments=--app.telegram.debug=true --app.telegram.update-listener-sleep=2s --app.telegram.polling-enabled=true --app.telegram.set-my-commands-on-startup=true"
```

Пояснение по параметрам:
- `app.telegram.url` — URL Telegram API (обычно `https://api.telegram.org/bot`);
- `app.telegram.token` — токен бота (обычно через `TELEGRAM_TOKEN` в `bot/.env`);
- `app.telegram.update-listener-sleep` — интервал polling;
- `app.telegram.debug` — debug-режим клиента;
- `app.telegram.polling-enabled` — включение polling;
- `app.telegram.set-my-commands-on-startup` — регистрация команд в Telegram при старте.

## Шаг 3. Запуск модуля `bot`

Из корня проекта:

Для Windows (PowerShell):

```powershell
.\mvnw.cmd -f .\bot\pom.xml spring-boot:run
```

Для Unix/macOS:

```shell
./mvnw -f ./bot/pom.xml spring-boot:run
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

Для Windows (PowerShell):

```powershell
.\mvnw.cmd -pl bot -am test
```

Для Unix/macOS:

```shell
./mvnw -pl bot -am test
```

