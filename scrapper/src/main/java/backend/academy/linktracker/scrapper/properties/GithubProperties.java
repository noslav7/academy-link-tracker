package backend.academy.linktracker.scrapper.properties;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.validator.constraints.URL;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Набор настроек доступа к GitHub API.
 */
@ConfigurationProperties(prefix = "app.github")
@Validated
@Getter
@Setter
@EqualsAndHashCode
@NoArgsConstructor
public class GithubProperties {

    /**
     * Токен для доступа к GitHub API.
     */
    private String token;

    /**
     * Базовый URL GitHub API.
     */
    @URL
    private String baseUrl = "https://api.github.com";
}
