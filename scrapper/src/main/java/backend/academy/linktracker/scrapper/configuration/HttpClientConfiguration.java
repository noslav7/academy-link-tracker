package backend.academy.linktracker.scrapper.configuration;

import backend.academy.linktracker.scrapper.properties.BotClientProperties;
import backend.academy.linktracker.scrapper.properties.GithubProperties;
import backend.academy.linktracker.scrapper.properties.StackoverflowProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class HttpClientConfiguration {

    @Bean
    RestClient botRestClient(RestClient.Builder builder, BotClientProperties properties) {
        return builder.baseUrl(properties.getBaseUrl()).build();
    }

    @Bean
    RestClient githubRestClient(RestClient.Builder builder, GithubProperties properties) {
        return builder.baseUrl(properties.getBaseUrl()).build();
    }

    @Bean
    RestClient stackoverflowRestClient(RestClient.Builder builder, StackoverflowProperties properties) {
        return builder.baseUrl(properties.getBaseUrl()).build();
    }
}
