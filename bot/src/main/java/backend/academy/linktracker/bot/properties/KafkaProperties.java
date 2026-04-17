package backend.academy.linktracker.bot.properties;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Настройки подключения бота к Kafka для приема уведомлений.
 */
@ConfigurationProperties(prefix = "app.kafka")
@Validated
@Getter
@Setter
@EqualsAndHashCode
@NoArgsConstructor
public class KafkaProperties {

    /**
     * Адреса bootstrap-серверов Kafka.
     */
    @NotBlank
    private String bootstrapServers = "localhost:9092";

    /**
     * URL Schema Registry для Avro-схем Kafka-сообщений.
     */
    @NotBlank
    private String schemaRegistryUrl = "http://localhost:8085/apis/registry/v2";

    /**
     * Имя топика уведомлений.
     */
    @NotBlank
    private String topic = "link-updates";

    /**
     * Идентификатор consumer group.
     */
    @NotBlank
    private String groupId = "bot-link-updates";

    /**
     * Идентификатор клиента-консьюмера.
     */
    @NotBlank
    private String clientId = "bot";

    /**
     * Количество попыток обработки сообщения (включая первую).
     */
    @Positive
    private int retryAttempts = 3;

    /**
     * Задержка между повторными попытками обработки, мс.
     */
    @PositiveOrZero
    private long retryBackoffMs = 1000L;

    /**
     * Имя dead-letter топика для сообщений, не прошедших обработку.
     */
    @NotBlank
    private String dlqTopic = "link-updates-dlq";
}
