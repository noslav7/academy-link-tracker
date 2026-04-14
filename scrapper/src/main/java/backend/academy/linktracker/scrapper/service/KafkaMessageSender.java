package backend.academy.linktracker.scrapper.service;

import backend.academy.linktracker.scrapper.dto.LinkUpdate;
import backend.academy.linktracker.scrapper.properties.KafkaProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;

/**
 * Отправитель уведомлений через Kafka.
 */
public class KafkaMessageSender implements MessageSender {

    private static final Logger log = LoggerFactory.getLogger(KafkaMessageSender.class);

    private final KafkaTemplate<String, LinkUpdate> kafkaTemplate;
    private final KafkaProperties kafkaProperties;

    /**
     * Создает отправитель, публикующий сообщения в Kafka.
     *
     * @param kafkaTemplate   шаблон для отправки сообщений
     * @param kafkaProperties настройки подключения и топика
     */
    public KafkaMessageSender(KafkaTemplate<String, LinkUpdate> kafkaTemplate, KafkaProperties kafkaProperties) {
        this.kafkaTemplate = kafkaTemplate;
        this.kafkaProperties = kafkaProperties;
    }

    /**
     * Публикует LinkUpdate в Kafka-топик уведомлений.
     */
    @Override
    public void send(LinkUpdate update) {
        kafkaTemplate.send(kafkaProperties.getTopic(), String.valueOf(update.id()), update);
        log.atDebug()
                .addKeyValue("topic", kafkaProperties.getTopic())
                .addKeyValue("key", update.id())
                .log("Link update notification sent to Kafka");
    }
}
