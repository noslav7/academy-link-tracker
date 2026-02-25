package backend.academy.linktracker.bot.properties;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.grpc.server")
@Getter
@Setter
@EqualsAndHashCode
@NoArgsConstructor
public class GrpcServerProperties {
    private boolean enabled;
    private int port = 9090;
}
