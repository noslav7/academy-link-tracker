package backend.academy.linktracker.scrapper.service;

import backend.academy.linktracker.scrapper.dto.LinkUpdate;
import backend.academy.linktracker.scrapper.properties.KafkaProperties;
import java.io.IOException;
import java.io.InputStream;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;

/**
 * Отправитель уведомлений через Kafka.
 */
public class KafkaMessageSender implements NotificationDispatcher {

    private static final Logger log = LoggerFactory.getLogger(KafkaMessageSender.class);
    private static final String LINK_UPDATE_EVENT_SCHEMA_RESOURCE = "LinkUpdateEvent.avsc";
    private static final Schema LINK_UPDATE_EVENT_SCHEMA = loadSchema();

    private final KafkaTemplate<String, GenericRecord> kafkaTemplate;
    private final KafkaProperties kafkaProperties;

    /**
     * Создает отправитель, публикующий сообщения в Kafka.
     *
     * @param kafkaTemplate   шаблон для отправки сообщений
     * @param kafkaProperties настройки подключения и топика
     */
    public KafkaMessageSender(KafkaTemplate<String, GenericRecord> kafkaTemplate, KafkaProperties kafkaProperties) {
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

    private static GenericRecord toEvent(LinkUpdate update) {
        GenericRecord event = new GenericData.Record(LINK_UPDATE_EVENT_SCHEMA);
        event.put("id", update.id());
        event.put("url", update.url());
        event.put("description", update.description());
        event.put("tgChatIds", update.tgChatIds());
        return event;
    }

    private static Schema loadSchema() {
        ClassLoader classLoader = KafkaMessageSender.class.getClassLoader();
        try (InputStream inputStream = classLoader.getResourceAsStream(LINK_UPDATE_EVENT_SCHEMA_RESOURCE)) {
            if (inputStream == null) {
                throw new IllegalStateException("Avro schema resource not found: " + LINK_UPDATE_EVENT_SCHEMA_RESOURCE);
            }
            return new Schema.Parser().parse(inputStream);
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Failed to load Avro schema resource: " + LINK_UPDATE_EVENT_SCHEMA_RESOURCE, exception);
        }
    }
}
