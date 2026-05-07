package backend.academy.linktracker.scrapper.configuration;

import backend.academy.linktracker.scrapper.properties.GithubProperties;
import backend.academy.linktracker.scrapper.properties.HttpResilienceProperties;
import backend.academy.linktracker.scrapper.properties.StackoverflowProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Конфигурация REST-клиентов внешних API (GitHub, Stack Overflow).
 */
@Configuration
public class HttpClientConfiguration {

    /**
     * Создает фабрику HTTP-запросов с единым timeout для внешних API.
     * Нужна для fail-fast поведения при недоступности внешних сервисов.
     *
     * @param properties настройки устойчивости HTTP-вызовов
     * @return фабрика HTTP-запросов для внешних клиентов
     */
    @Bean
    ClientHttpRequestFactory externalClientHttpRequestFactory(HttpResilienceProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.getTimeout());
        requestFactory.setReadTimeout(properties.getTimeout());
        return requestFactory;
    }

    /**
     * REST-клиент для запросов к GitHub API.
     * Нужен как отдельный бин с собственным base URL и общим timeout.
     *
     * @param builder базовый билдер RestClient
     * @param requestFactory фабрика HTTP-запросов с настроенными timeout
     * @param properties конфигурация подключения к GitHub API
     * @return настроенный клиент GitHub API
     */
    @Bean
    RestClient githubRestClient(
            RestClient.Builder builder, ClientHttpRequestFactory requestFactory, GithubProperties properties) {
        return buildExternalRestClient(builder, requestFactory, properties.getBaseUrl());
    }

    /**
     * REST-клиент для запросов к Stack Overflow API.
     * Нужен как отдельный бин с собственным base URL и общим timeout.
     *
     * @param builder базовый билдер RestClient
     * @param requestFactory фабрика HTTP-запросов с настроенными timeout
     * @param properties конфигурация подключения к Stack Overflow API
     * @return настроенный клиент Stack Overflow API
     */
    @Bean
    RestClient stackoverflowRestClient(
            RestClient.Builder builder, ClientHttpRequestFactory requestFactory, StackoverflowProperties properties) {
        return buildExternalRestClient(builder, requestFactory, properties.getBaseUrl());
    }

    /**
     * Собирает типовой RestClient для внешнего API.
     * Нужен для исключения дублирования одинаковой конфигурации в нескольких bean-методах.
     *
     * @param builder базовый билдер RestClient
     * @param requestFactory фабрика HTTP-запросов
     * @param baseUrl базовый URL внешнего API
     * @return настроенный экземпляр RestClient
     */
    private RestClient buildExternalRestClient(
            RestClient.Builder builder, ClientHttpRequestFactory requestFactory, String baseUrl) {
        return builder.clone().requestFactory(requestFactory).baseUrl(baseUrl).build();
    }
}
