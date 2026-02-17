package backend.academy.linktracker.scrapper.properties;

import jakarta.validation.constraints.NotEmpty;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
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
    @NotEmpty
    private String token;
}
