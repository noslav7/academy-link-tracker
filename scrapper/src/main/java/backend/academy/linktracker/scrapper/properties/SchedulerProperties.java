package backend.academy.linktracker.scrapper.properties;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.convert.DurationUnit;
import org.springframework.stereotype.Component;

@ConfigurationProperties(prefix = "app.scheduler")
@Component("schedulerProperties")
@Getter
@Setter
@EqualsAndHashCode
@NoArgsConstructor
public class SchedulerProperties {

    @DurationUnit(ChronoUnit.SECONDS)
    private Duration interval = Duration.ofSeconds(30);
}
