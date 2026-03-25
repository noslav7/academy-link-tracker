package backend.academy.linktracker.scrapper.configuration;

import backend.academy.linktracker.scrapper.properties.GithubProperties;
import backend.academy.linktracker.scrapper.properties.StackoverflowProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Конфигурация REST-клиентов внешних API (GitHub, Stack Overflow).
 */
@Configuration
public class HttpClientConfiguration {

    /**
     * REST-клиент для запросов к GitHub API.
     */
    @Bean
    RestClient githubRestClient(RestClient.Builder builder, GithubProperties properties) {
        return builder.baseUrl(properties.getBaseUrl()).build();
    }

    /**
     * REST-клиент для запросов к Stack Overflow API.
     */
    @Bean
    RestClient stackoverflowRestClient(RestClient.Builder builder, StackoverflowProperties properties) {
        return builder.baseUrl(properties.getBaseUrl()).build();
    }
}
