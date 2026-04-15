package backend.academy.linktracker.scrapper.properties;

import jakarta.validation.constraints.NotBlank;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Настройки подключения scrapper к кластеру Kafka для отправки уведомлений.
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
     * Идентификатор клиента-публикатора.
     */
    @NotBlank
    private String clientId = "scrapper";
}
