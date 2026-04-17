package backend.academy.linktracker.scrapper.properties;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * Настройки фонового publisher-а Transactional Outbox.
 */
@ConfigurationProperties(prefix = "app.outbox")
@Component("outboxProperties")
@Validated
@Getter
@Setter
@EqualsAndHashCode
@NoArgsConstructor
public class OutboxProperties {

    /**
     * Интервал между запусками публикации из outbox.
     */
    @NotNull
    private Duration publishInterval = Duration.ofSeconds(1);

    /**
     * Максимальное число сообщений, публикуемых за один запуск.
     */
    @Positive
    private int batchSize = 100;
}
