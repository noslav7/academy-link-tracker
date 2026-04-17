package backend.academy.linktracker.scrapper.service;

import backend.academy.linktracker.scrapper.dto.LinkUpdate;
import backend.academy.linktracker.scrapper.properties.KafkaProperties;
import com.example.notification.LinkUpdateEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;

/**
 * Отправитель уведомлений через Kafka.
 */
public class KafkaMessageSender implements NotificationDispatcher {

    private static final Logger log = LoggerFactory.getLogger(KafkaMessageSender.class);

    private final KafkaTemplate<String, LinkUpdateEvent> kafkaTemplate;
    private final KafkaProperties kafkaProperties;

    /**
     * Создает отправитель, публикующий сообщения в Kafka.
     *
     * @param kafkaTemplate   шаблон для отправки сообщений
     * @param kafkaProperties настройки подключения и топика
     */
    public KafkaMessageSender(KafkaTemplate<String, LinkUpdateEvent> kafkaTemplate, KafkaProperties kafkaProperties) {
        this.kafkaTemplate = kafkaTemplate;
        this.kafkaProperties = kafkaProperties;
    }

    /**
     * Публикует LinkUpdate в Kafka-топик уведомлений.
     */
    @Override
    public void dispatch(LinkUpdate update) {
        kafkaTemplate.send(kafkaProperties.getTopic(), String.valueOf(update.id()), toEvent(update));
        log.atDebug()
                .addKeyValue("topic", kafkaProperties.getTopic())
                .addKeyValue("key", update.id())
                .log("Link update notification sent to Kafka");
    }

    private static LinkUpdateEvent toEvent(LinkUpdate update) {
        return LinkUpdateEvent.newBuilder()
                .setId(update.id())
                .setUrl(update.url())
                .setDescription(update.description())
                .setTgChatIds(update.tgChatIds())
                .build();
    }
}
