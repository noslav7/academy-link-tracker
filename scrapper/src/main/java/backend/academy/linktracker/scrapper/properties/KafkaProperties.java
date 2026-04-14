package backend.academy.linktracker.scrapper.properties;

import jakarta.validation.constraints.NotEmpty;
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
    @NotEmpty
    private String bootstrapServers = "localhost:9092";

    /**
     * Имя топика уведомлений.
     */
    @NotEmpty
    private String topic = "link-updates";

    /**
     * Идентификатор клиента-публикатора.
     */
    private String clientId = "scrapper";
}
