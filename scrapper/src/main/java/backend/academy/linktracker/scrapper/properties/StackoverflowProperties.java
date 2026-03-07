package backend.academy.linktracker.scrapper.properties;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.validator.constraints.URL;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Набор настроек доступа к Stack Overflow API.
 */
@ConfigurationProperties(prefix = "app.stackoverflow")
@Validated
@Getter
@Setter
@EqualsAndHashCode
@NoArgsConstructor
public class StackoverflowProperties {

    /**
     * Ключ приложения для Stack Overflow API.
     */
    private String key;

    /**
     * Токен доступа к Stack Overflow API.
     */
    private String accessToken;

    /**
     * Базовый URL Stack Overflow API.
     */
    @URL
    private String baseUrl = "https://api.stackexchange.com/2.3";
}
