package backend.academy.linktracker.scrapper.properties;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.validator.constraints.URL;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Набор настроек подключения scrapper к bot-сервису.
 */
@ConfigurationProperties(prefix = "app.bot")
@Validated
@Getter
@Setter
@EqualsAndHashCode
@NoArgsConstructor
public class BotClientProperties {

    /**
     * Базовый URL bot HTTP API.
     */
    @NotBlank
    @URL
    private String baseUrl;

    /**
     * Транспорт отправки уведомлений в bot.
     */
    private TransportMode transport = TransportMode.KAFKA;

    /**
     * Хост gRPC-сервера bot.
     */
    @NotBlank
    private String grpcHost = "localhost";

    /**
     * Порт gRPC-сервера bot.
     */
    @Min(1)
    @Max(65535)
    private int grpcPort = 9090;
}
