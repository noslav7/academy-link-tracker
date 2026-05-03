package backend.academy.linktracker.bot.kafka;

import backend.academy.linktracker.bot.client.dto.LinkUpdate;
import backend.academy.linktracker.bot.service.UpdateNotificationService;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import java.util.Set;
import org.apache.avro.generic.GenericRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Kafka-консьюмер уведомлений об обновлениях ссылок от scrapper.
 */
@Component
@ConditionalOnProperty(prefix = "app.kafka", name = "enabled", havingValue = "true", matchIfMissing = true)
public class LinkUpdateListener {

    /**
     * Логгер компонента.
     */
    private static final Logger log = LoggerFactory.getLogger(LinkUpdateListener.class);

    /**
     * Сервис отправки уведомлений в Telegram.
     */
    private final UpdateNotificationService updateNotificationService;
    /**
     * Валидатор входящего payload.
     */
    private final Validator validator;

    /**
     * Создает Kafka-слушатель уведомлений.
     *
     * @param updateNotificationService сервис доставки уведомлений
     * @param validator валидатор входящих сообщений
     */
    public LinkUpdateListener(UpdateNotificationService updateNotificationService, Validator validator) {
        this.updateNotificationService = updateNotificationService;
        this.validator = validator;
    }

    /**
     * Обрабатывает входящее уведомление об обновлении ссылки из Kafka.
     *
     * @param event Avro-событие обновления ссылки
     */
    @KafkaListener(
            topics = "${app.kafka.topic}",
            groupId = "${app.kafka.group-id}",
            containerFactory = "linkUpdateKafkaListenerContainerFactory")
    public void handleLinkUpdateMessage(GenericRecord event) {
        LinkUpdate update = LinkUpdatePayloadMapper.toLinkUpdate(event);
        if (update == null) {
            throw new InvalidLinkUpdateException("Received null LinkUpdate payload");
        }

        Set<ConstraintViolation<LinkUpdate>> violations = validator.validate(update);
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException("Invalid LinkUpdate payload", violations);
        }

        updateNotificationService.notifyAboutUpdate(update);
        log.atInfo()
                .addKeyValue("linkId", update.id())
                .addKeyValue("url", update.url())
                .log("Link update notification consumed from Kafka");
    }
}
