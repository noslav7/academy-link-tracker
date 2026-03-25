package backend.academy.linktracker.scrapper.properties;

import jakarta.validation.constraints.Positive;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Настройки встроенного gRPC-сервера scrapper-сервиса.
 */
@ConfigurationProperties(prefix = "app.grpc.server")
@Validated
@Getter
@Setter
@EqualsAndHashCode
@NoArgsConstructor
public class GrpcServerProperties {
    /**
     * Флаг включения gRPC-сервера.
     */
    private boolean enabled;
    /**
     * Порт gRPC-сервера.
     */
    @Positive
    private int port = 9091;
}
