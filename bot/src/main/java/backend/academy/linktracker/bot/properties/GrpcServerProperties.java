package backend.academy.linktracker.bot.properties;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Настройки встроенного gRPC-сервера bot-сервиса.
 */
@ConfigurationProperties(prefix = "app.grpc.server")
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
    private int port = 9090;
}
