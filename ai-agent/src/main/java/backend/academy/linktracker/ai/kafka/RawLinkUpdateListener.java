package backend.academy.linktracker.ai.kafka;

import backend.academy.linktracker.ai.dto.ProcessedLinkUpdate;
import backend.academy.linktracker.ai.dto.RawLinkUpdate;
import backend.academy.linktracker.ai.properties.KafkaProperties;
import backend.academy.linktracker.ai.service.GroupedMessage;
import backend.academy.linktracker.ai.service.UpdateGroupingService;
import backend.academy.linktracker.ai.service.UpdateProcessingService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Получает сырые обновления из Kafka, запускает pipeline обработки и публикует результат.
 */
@Slf4j
@Component
public class RawLinkUpdateListener {

    /**
     * JSON-сериализатор, нужен для преобразования payload в DTO входного события.
     */
    private final ObjectMapper objectMapper;
    /**
     * Оркестратор этапов фильтрации/суммаризации, нужен для применения бизнес-правил обработки.
     */
    private final UpdateProcessingService updateProcessingService;
    /**
     * Сервис оконной группировки, нужен для объединения обновлений одного чата в итоговое сообщение.
     */
    private final UpdateGroupingService updateGroupingService;
    /**
     * Kafka-клиент публикации, нужен для отправки обработанного события в выходной топик.
     */
    private final KafkaTemplate<String, ProcessedLinkUpdate> processedLinkUpdateKafkaTemplate;
    /**
     * Конфигурация Kafka, нужна для получения имен топиков и клиентских параметров.
     */
    private final KafkaProperties kafkaProperties;
    /**
     * Валидатор контракта DTO, нужен для отсечения некорректных входных сообщений.
     */
    private final Validator validator;

    /**
     * Создает Kafka-listener с зависимостями обработки, чтобы входящие события проходили полный pipeline.
     *
     * @param objectMapper сериализатор JSON
     * @param updateProcessingService оркестратор обработки
     * @param processedLinkUpdateKafkaTemplate клиент публикации в выходной топик
     * @param kafkaProperties настройки Kafka pipeline
     * @param validator валидатор контрактов DTO
     */
    public RawLinkUpdateListener(
            ObjectMapper objectMapper,
            UpdateProcessingService updateProcessingService,
            UpdateGroupingService updateGroupingService,
            KafkaTemplate<String, ProcessedLinkUpdate> processedLinkUpdateKafkaTemplate,
            KafkaProperties kafkaProperties,
            Validator validator) {
        this.objectMapper = objectMapper;
        this.updateProcessingService = updateProcessingService;
        this.updateGroupingService = updateGroupingService;
        this.processedLinkUpdateKafkaTemplate = processedLinkUpdateKafkaTemplate;
        this.kafkaProperties = kafkaProperties;
        this.validator = validator;
    }

    /**
     * Обрабатывает входящее Kafka-сообщение:
     * - десериализует JSON в RawLinkUpdate;
     * - валидирует контракт;
     * - запускает фильтрацию/суммаризацию;
     * - публикует итог в processed topic, если обновление прошло фильтры.
     */
    @KafkaListener(
            topics = "${app.kafka.raw-topic:link.raw-updates}",
            groupId = "${app.kafka.group-id:ai-agent-link-raw-updates}",
            containerFactory = "rawLinkUpdateKafkaListenerContainerFactory")
    public void onRawLinkUpdateMessage(
            String payload, @Header(name = KafkaHeaders.RECEIVED_KEY, required = false) String messageKey) {
        if (!StringUtils.hasText(payload)) {
            log.warn("Incoming raw update payload is empty");
            return;
        }

        RawLinkUpdate update = deserializeRawLinkUpdate(payload);
        if (update == null || !isContractValid(update)) {
            return;
        }

        updateProcessingService
                .process(update)
                .ifPresent(processed -> publishGroupedMessages(updateGroupingService.accept(messageKey, processed)));
    }

    /**
     * Периодически сбрасывает истекшие окна группировки, чтобы одиночные и агрегированные сообщения доходили до Kafka.
     */
    @Scheduled(fixedDelayString = "${ai-agent.grouping.window-ms:30000}")
    public void flushExpiredGroups() {
        publishGroupedMessages(updateGroupingService.flushExpired());
    }

    /**
     * Десериализует входной payload, чтобы валидировать и обработать событие в typed-виде.
     *
     * @param payload JSON входного события
     * @return десериализованный DTO или null при ошибке формата
     */
    private RawLinkUpdate deserializeRawLinkUpdate(String payload) {
        try {
            return objectMapper.readValue(payload, RawLinkUpdate.class);
        } catch (JsonProcessingException exception) {
            log.warn("Failed to deserialize incoming raw update payload: {}", payload, exception);
            return null;
        }
    }

    /**
     * Проверяет DTO на ограничения контракта, чтобы не пускать невалидные данные в pipeline.
     *
     * @param update входной DTO
     * @return true, если ограничений не нарушено
     */
    private boolean isContractValid(RawLinkUpdate update) {
        Set<ConstraintViolation<RawLinkUpdate>> violations = validator.validate(update);
        if (!violations.isEmpty()) {
            log.warn("Incoming raw update failed validation: {}", violations);
            return false;
        }
        return true;
    }

    /**
     * Публикует обработанное обновление в выходной топик, чтобы downstream-сервисы получили результат pipeline.
     *
     * @param groupedMessage сгруппированное сообщение с payload и ключом публикации
     */
    private void publishProcessedUpdate(GroupedMessage groupedMessage) {
        publishProcessedUpdate(groupedMessage.processedUpdate(), groupedMessage.messageKey());
    }

    /**
     * Публикует пачку сгруппированных сообщений, чтобы использовать единый путь отправки для real-time и scheduled flush.
     *
     * @param groupedMessages сообщения, готовые к отправке
     */
    private void publishGroupedMessages(Iterable<GroupedMessage> groupedMessages) {
        groupedMessages.forEach(this::publishProcessedUpdate);
    }

    /**
     * Отправляет итоговый payload в Kafka с fallback-ключом, чтобы сообщение не осталось без ключа партиционирования.
     *
     * @param processed обработанное обновление
     * @param messageKey исходный ключ сообщения
     */
    private void publishProcessedUpdate(ProcessedLinkUpdate processed, String messageKey) {
        String resolvedKey = StringUtils.hasText(messageKey) ? messageKey : Long.toString(processed.id());
        processedLinkUpdateKafkaTemplate.send(kafkaProperties.getProcessedTopic(), resolvedKey, processed);
    }
}
