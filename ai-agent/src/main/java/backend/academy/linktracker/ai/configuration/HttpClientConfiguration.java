package backend.academy.linktracker.ai.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Конфигурирует HTTP-клиенты AI Agent.
 */
@Configuration
public class HttpClientConfiguration {

    /**
     * Поставляет ObjectMapper для компонентов, где нужен общий JSON-парсер.
     */
    @Bean
    @ConditionalOnMissingBean(ObjectMapper.class)
    ObjectMapper objectMapper() {
        return new ObjectMapper();
    }

    /**
     * Поставляет RestClient.Builder для компонентов, вызывающих внешние HTTP API.
     */
    @Bean
    RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }
}
