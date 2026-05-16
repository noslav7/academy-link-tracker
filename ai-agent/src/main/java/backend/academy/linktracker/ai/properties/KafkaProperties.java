package backend.academy.linktracker.ai.properties;

import jakarta.validation.constraints.NotBlank;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Настройки Kafka-пайплайна AI Agent для приема сырых и публикации обработанных обновлений.
 */
@ConfigurationProperties(prefix = "app.kafka")
@Validated
@Getter
@Setter
@EqualsAndHashCode
@NoArgsConstructor
public class KafkaProperties {

    /**
     * Флаг включения Kafka pipeline.
     */
    private boolean enabled = true;

    /**
     * Адреса bootstrap-серверов Kafka.
     */
    @NotBlank
    private String bootstrapServers = "localhost:9092";

    /**
     * Топик сырых обновлений от scrapper.
     */
    @NotBlank
    private String rawTopic = "link.raw-updates";

    /**
     * Топик обработанных обновлений для downstream-сервисов.
     */
    @NotBlank
    private String processedTopic = "link.processed-updates";

    /**
     * Идентификатор consumer group.
     */
    @NotBlank
    private String groupId = "ai-agent-link-raw-updates";

    /**
     * Идентификатор Kafka клиента.
     */
    @NotBlank
    private String clientId = "ai-agent";

    /**
     * Политика чтения при отсутствии сохраненного offset.
     */
    @NotBlank
    private String autoOffsetReset = "earliest";
}
