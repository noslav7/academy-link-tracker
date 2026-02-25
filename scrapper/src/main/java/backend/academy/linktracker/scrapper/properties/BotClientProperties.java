package backend.academy.linktracker.scrapper.properties;

import jakarta.validation.constraints.NotEmpty;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.validator.constraints.URL;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "app.bot")
@Validated
@Getter
@Setter
@EqualsAndHashCode
@NoArgsConstructor
public class BotClientProperties {

    @NotEmpty
    @URL
    private String baseUrl;

    private TransportMode transport = TransportMode.HTTP;

    private String grpcHost = "localhost";

    private int grpcPort = 9090;
}
