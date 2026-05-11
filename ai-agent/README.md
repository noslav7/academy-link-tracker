# AI Agent Module

Инструкция по запуску, настройке и тестированию модуля `ai-agent`.

## Что делает модуль

`ai-agent` — промежуточный сервис между `scrapper` и `bot`.
В первой итерации реализовано внутреннее ядро обработки без Kafka wiring:

- фильтрация обновлений по стоп-словам;
- фильтрация по исключённым авторам;
- фильтрация по минимальной длине текста;
- суммаризация длинных обновлений через детерминированную заглушку AI API;
- подготовка DTO для будущего топика `link.processed-updates`.

## Конфигурация

Настройки задаются в `ai-agent/src/main/resources/application.yaml`, `ai-agent/.env` или общем `.env`.

```yaml
ai-agent:
  filtering:
    stop-words:
      - spam
      - ads
      - promo
    excluded-authors:
      - bot-user
    min-length: 20
  summarization:
    threshold: 500
```

Переменные окружения:

```properties
AI_AGENT_FILTERING_STOP_WORDS=spam,ads,promo
AI_AGENT_FILTERING_EXCLUDED_AUTHORS=bot-user
AI_AGENT_FILTERING_MIN_LENGTH=20
AI_AGENT_SUMMARIZATION_THRESHOLD=500
```

Правила:

- `stop-words` — если `description` содержит хотя бы одно стоп-слово, обновление отбрасывается;
- `excluded-authors` — если `author` совпадает с исключённым автором, обновление отбрасывается;
- `min-length` — если очищенный от краевых пробелов текст короче значения, обновление отбрасывается;
- `threshold` — если длина текста больше порога, вызывается `UpdateSummarizer`.

## Контракты данных

Core processing работает с двумя DTO:

- `RawLinkUpdate` — входное сырое обновление: `id >= 0`, `description` обязателен и не длиннее `16_384` символов, `author` не длиннее `512` символов, `tgChatIds` не пустой и содержит только положительные chat id;
- `ProcessedLinkUpdate` — выходное обработанное обновление: сохраняет `id`, обработанный `description`, список `tgChatIds` и текущий приоритет `HIGH`.

Пустые или слишком короткие описания не считаются ошибкой контракта: они проходят валидацию и затем отбрасываются фильтром по `min-length`.

## Запуск

Kafka listener в этой итерации ещё не подключён, поэтому сервис можно запускать из IDE без Kafka-кластера:

- main-класс: `backend.academy.linktracker.ai.AiAgentApplication`;
- профиль не требуется;
- порт по умолчанию: `8082`.

Из корня репозитория:

```powershell
.\mvnw.cmd -pl ai-agent -am package
java -jar .\ai-agent\target\ai-agent-0.0.1.jar
```

## Тесты

Unit-тесты покрывают сценарии TC-2.1, TC-2.2, TC-2.3, TC-2.4, TC-3.1 и TC-3.2:

```powershell
.\mvnw.cmd -pl ai-agent -am test
```

Текущие тесты проверяют core processing без Docker и без Kafka:

- фильтрацию по стоп-словам;
- фильтрацию по автору;
- фильтрацию по минимальной длине;
- пропуск валидного обновления;
- суммаризацию длинного текста;
- передачу короткого текста без изменений.

