package backend.academy.linktracker.scrapper.properties;

import jakarta.validation.constraints.NotEmpty;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "app.stackoverflow")
@Validated
@Getter
@Setter
@EqualsAndHashCode
@NoArgsConstructor
/**
 * Набор настроек доступа к Stack Overflow API.
 */
public class StackoverflowProperties {

    /**
     * Ключ приложения для Stack Overflow API.
     */
    @NotEmpty
    private String key;

    /**
     * Токен доступа к Stack Overflow API.
     */
    @NotEmpty
    private String accessToken;
}
