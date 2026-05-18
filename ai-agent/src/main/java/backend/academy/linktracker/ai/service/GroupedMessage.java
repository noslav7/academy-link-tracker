package backend.academy.linktracker.ai.service;

import backend.academy.linktracker.ai.dto.ProcessedLinkUpdate;

/**
 * Сообщение, готовое к отправке в Kafka после группировки.
 *
 * @param messageKey ключ сообщения для партиционирования/идемпотентности публикации
 * @param processedUpdate агрегированное или одиночное обработанное обновление
 */
public record GroupedMessage(String messageKey, ProcessedLinkUpdate processedUpdate) {}
