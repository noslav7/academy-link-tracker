package backend.academy.linktracker.scrapper.configuration;

import backend.academy.linktracker.scrapper.dto.LinkUpdate;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.serialization.Serializer;

/**
 * JSON-сериализатор LinkUpdate без заголовков типизации.
 */
final class LinkUpdateSerializer implements Serializer<LinkUpdate> {

    /**
     * ObjectMapper для сериализации payload уведомления.
     */
    private final ObjectMapper objectMapper;

    /**
     * Создает сериализатор уведомлений об обновлении ссылок.
     *
     * @param objectMapper mapper для преобразования DTO в JSON
     */
    LinkUpdateSerializer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Сериализует уведомление LinkUpdate в JSON-байты.
     *
     * @param topic имя Kafka-топика
     * @param data DTO уведомления
     * @return сериализованный payload или {@code null}, если {@code data == null}
     */
    @Override
    public byte[] serialize(String topic, LinkUpdate data) {
        if (data == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsBytes(data);
        } catch (JsonProcessingException exception) {
            throw new SerializationException("Failed to serialize LinkUpdate", exception);
        }
    }
}
