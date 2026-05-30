package backend.academy.linktracker.bot.properties;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.validator.constraints.URL;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Набор настроек подключения к scrapper-сервису.
 */
@ConfigurationProperties(prefix = "app.scrapper")
@Validated
@Getter
@Setter
@EqualsAndHashCode
@NoArgsConstructor
public class ScrapperProperties {

    /**
     * Базовый URL scrapper HTTP API.
     */
    @NotEmpty
    @URL
    private String baseUrl = "http://localhost:8081";

    /**
     * Транспорт взаимодействия с scrapper.
     */
    private TransportMode transport = TransportMode.HTTP;

    /**
     * Хост gRPC-сервера scrapper.
     */
    @NotBlank
    private String grpcHost = "localhost";

    /**
     * Порт gRPC-сервера scrapper.
     */
    @Min(1)
    @Max(65535)
    private int grpcPort = 9091;

    /**
     * Timeout для HTTP-вызовов и gRPC deadline при обращении к scrapper.
     */
    @NotNull
    @DurationMin(millis = 1)
    private Duration timeout = Duration.ofSeconds(2);
}
