package backend.academy.linktracker.bot.properties;

import jakarta.validation.constraints.NotBlank;
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
}
