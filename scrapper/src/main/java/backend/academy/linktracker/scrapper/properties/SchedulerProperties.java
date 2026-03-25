package backend.academy.linktracker.scrapper.properties;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.convert.DurationUnit;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * Настройки периодического планировщика проверки ссылок.
 */
@ConfigurationProperties(prefix = "app.scheduler")
@Component("schedulerProperties")
@Validated
@Getter
@Setter
@EqualsAndHashCode
@NoArgsConstructor
public class SchedulerProperties {

    /**
     * Интервал между запусками проверки обновлений.
     */
    @NotNull
    @DurationUnit(ChronoUnit.SECONDS)
    private Duration interval = Duration.ofSeconds(30);

    /**
     * Размер пакета ссылок для одной итерации загрузки из БД (50–500).
     */
    @Positive
    private int batchSize = 100;

    /**
     * Количество параллельных потоков обработки ссылок внутри батча.
     */
    @Positive
    private int threadCount = 4;
}
