package backend.academy.linktracker.scrapper.properties;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.validator.constraints.URL;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "app.github")
@Validated
@Getter
@Setter
@EqualsAndHashCode
@NoArgsConstructor
/**
 * Набор настроек доступа к GitHub API.
 */
public class GithubProperties {

    /**
     * Токен для доступа к GitHub API.
     */
    private String token;

    @URL
    private String baseUrl = "https://api.github.com";
}
