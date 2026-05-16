# AI Agent Module

Инструкция по запуску, настройке и тестированию модуля `ai-agent`.

## Что делает модуль

`ai-agent` — промежуточный сервис между `scrapper` и `bot`, работающий через Kafka pipeline:

1. получает событие из `link.raw-updates`;
2. фильтрует обновление по стоп-словам;
3. фильтрует обновление по исключенным авторам;
4. игнорирует обновления короче `min-length`;
5. суммаризирует длинные обновления и публикует результат в `link.processed-updates`.

## Границы ответственности компонентов

- `RawLinkUpdateListener` — прием Kafka-сообщения, проверка входного контракта и публикация результата;
- `UpdateProcessingService` — оркестрация этапов фильтрации и суммаризации;
- `UpdateFilter` — правила отсечения шумных обновлений;
- `AiApiUpdateSummarizer` — внешний HTTP-вызов AI API с таймаутами из конфигурации;
- `AiApiSummarizationResponseParser` — извлечение summary из JSON-ответа и валидация результата.

## Kafka topics

- входящий топик: `link.raw-updates` (JSON с `RawLinkUpdate`);
- исходящий топик: `link.processed-updates` (JSON с `ProcessedLinkUpdate`);
- ключ сообщения сохраняется при публикации из входящего топика в исходящий.

## Конфигурация

Настройки задаются в `ai-agent/src/main/resources/application.yaml`, `ai-agent/.env` или общем `.env`.

```yaml
app:
  kafka:
    enabled: true
    bootstrap-servers: localhost:9092
    raw-topic: link.raw-updates
    processed-topic: link.processed-updates
    group-id: ai-agent-link-raw-updates
    client-id: ai-agent
    auto-offset-reset: earliest

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
    api-url: https://api-inference.huggingface.co/models/facebook/bart-large-cnn
    api-token: ${AI_AGENT_SUMMARIZATION_API_TOKEN}
    prompt: Summarize the following update in 2-3 sentences.
    timeout: 30s
```

Переменные окружения:

```properties
APP_KAFKA_ENABLED=true
KAFKA_BOOTSTRAP_SERVERS=localhost:9092,localhost:9093,localhost:9094
KAFKA_RAW_TOPIC=link.raw-updates
KAFKA_PROCESSED_TOPIC=link.processed-updates
KAFKA_GROUP_ID=ai-agent-link-raw-updates
KAFKA_CLIENT_ID=ai-agent
KAFKA_AUTO_OFFSET_RESET=earliest

AI_AGENT_FILTERING_STOP_WORDS=spam,ads,promo
AI_AGENT_FILTERING_EXCLUDED_AUTHORS=bot-user
AI_AGENT_FILTERING_MIN_LENGTH=20
AI_AGENT_SUMMARIZATION_THRESHOLD=500
AI_AGENT_SUMMARIZATION_API_URL=https://api-inference.huggingface.co/models/facebook/bart-large-cnn
AI_AGENT_SUMMARIZATION_API_TOKEN=hf_xxx
AI_AGENT_SUMMARIZATION_PROMPT=Summarize the following update in 2-3 sentences.
AI_AGENT_SUMMARIZATION_TIMEOUT=30s
```

## Пример входного и выходного сообщения

Вход (`link.raw-updates`):

```json
{
  "id": 42,
  "description": "Release notes: new integrations and fixes for API clients",
  "author": "alice",
  "tgChatIds": [1001, 1002]
}
```

Выход (`link.processed-updates`):

```json
{
  "id": 42,
  "description": "Release notes: new integrations and fixes for API clients",
  "tgChatIds": [1001, 1002],
  "priority": "HIGH"
}
```

Если сообщение не проходит фильтрацию или невалидно по JSON/DTO-контракту, публикация в `link.processed-updates` не выполняется, а ошибка логируется.

## Семантика ошибок внешнего AI API

- бизнес-ошибки (`4xx`, некорректная структура бизнес-ответа) маппятся в `AiSummarizationBusinessException`;
- транспортные ошибки (`5xx`, timeout, отсутствие ответа) маппятся в `AiServiceUnavailableException`;
- timeout задается через `AI_AGENT_SUMMARIZATION_TIMEOUT` / `ai-agent.summarization.timeout`.

## Запуск из IDE

1. Поднимите инфраструктуру:

```powershell
docker compose up -d `
  kafka-1 kafka-2 kafka-3 schema-registry `
  postgresql liquibase-migrations `
  valkey-node-1 valkey-node-2 valkey-node-3 valkey-cluster-init
```

2. Запустите класс `backend.academy.linktracker.ai.AiAgentApplication` (порт по умолчанию `8082`).
3. Для реальной суммаризации длинных обновлений задайте `AI_AGENT_SUMMARIZATION_API_TOKEN`.

Из корня репозитория:

```powershell
.\mvnw.cmd -pl ai-agent -am package
java -jar .\ai-agent\target\ai-agent-0.0.1.jar
```

## Тесты

Запуск unit + integration:

```powershell
.\mvnw.cmd -pl ai-agent -am test
```

Покрытие:

- unit: фильтрация, пороги длины, суммаризация и контракт `UpdateProcessingService`;
- unit: HTTP-контракт `AiApiUpdateSummarizer` через WireMock;
- integration (Testcontainers + Kafka):
  - TC-1.1: корректное сообщение из `link.raw-updates` читается и публикуется в `link.processed-updates`;
  - TC-1.2: некорректный формат сообщения не валит сервис, ошибка логируется, последующие валидные события обрабатываются.

