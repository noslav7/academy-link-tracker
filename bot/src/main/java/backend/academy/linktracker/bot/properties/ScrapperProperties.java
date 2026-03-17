package backend.academy.linktracker.bot.properties;

import jakarta.validation.constraints.NotEmpty;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.validator.constraints.URL;
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
    private String grpcHost = "localhost";

    /**
     * Порт gRPC-сервера scrapper.
     */
    private int grpcPort = 9091;
}
