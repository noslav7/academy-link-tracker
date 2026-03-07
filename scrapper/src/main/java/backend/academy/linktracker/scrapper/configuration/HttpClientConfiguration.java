package backend.academy.linktracker.scrapper.configuration;

import backend.academy.linktracker.scrapper.client.BotNotificationsClient;
import backend.academy.linktracker.scrapper.client.GrpcBotNotificationsClient;
import backend.academy.linktracker.scrapper.client.RestBotNotificationsClient;
import backend.academy.linktracker.scrapper.properties.BotClientProperties;
import backend.academy.linktracker.scrapper.properties.GithubProperties;
import backend.academy.linktracker.scrapper.properties.StackoverflowProperties;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Конфигурация HTTP/gRPC клиентов внешних сервисов scrapper-модуля.
 */
@Configuration
public class HttpClientConfiguration {

    /**
     * Создает REST-клиент для отправки уведомлений в bot.
     *
     * @param builder фабрика REST-клиента
     * @param properties настройки подключения к bot
     * @return REST-клиент bot-сервиса
     */
    @Bean
    RestClient botRestClient(RestClient.Builder builder, BotClientProperties properties) {
        return builder.baseUrl(properties.getBaseUrl()).build();
    }

    /**
     * Создает gRPC-канал до bot-сервиса.
     *
     * @param properties настройки gRPC подключения к bot
     * @return управляемый gRPC-канал
     */
    @Bean(destroyMethod = "shutdown")
    ManagedChannel botGrpcChannel(BotClientProperties properties) {
        return ManagedChannelBuilder.forAddress(properties.getGrpcHost(), properties.getGrpcPort())
                .usePlaintext()
                .build();
    }

    /**
     * Регистрирует REST-клиент уведомлений как основной транспорт до bot.
     *
     * @param botRestClient REST-клиент bot-сервиса
     * @return реализация отправки уведомлений по REST
     */
    @Bean
    @ConditionalOnProperty(prefix = "app.bot", name = "transport", havingValue = "HTTP", matchIfMissing = true)
    BotNotificationsClient restBotNotificationsClient(RestClient botRestClient) {
        return new RestBotNotificationsClient(botRestClient);
    }

    /**
     * Регистрирует gRPC-клиент уведомлений как транспорт до bot.
     *
     * @param botGrpcChannel gRPC-канал до bot
     * @return реализация отправки уведомлений по gRPC
     */
    @Bean
    @ConditionalOnProperty(prefix = "app.bot", name = "transport", havingValue = "GRPC")
    BotNotificationsClient grpcBotNotificationsClient(ManagedChannel botGrpcChannel) {
        return new GrpcBotNotificationsClient(botGrpcChannel);
    }

    /**
     * Создает REST-клиент для запросов к GitHub API.
     *
     * @param builder фабрика REST-клиента
     * @param properties настройки GitHub API
     * @return REST-клиент GitHub
     */
    @Bean
    RestClient githubRestClient(RestClient.Builder builder, GithubProperties properties) {
        return builder.baseUrl(properties.getBaseUrl()).build();
    }

    /**
     * Создает REST-клиент для запросов к Stack Overflow API.
     *
     * @param builder фабрика REST-клиента
     * @param properties настройки Stack Overflow API
     * @return REST-клиент Stack Overflow
     */
    @Bean
    RestClient stackoverflowRestClient(RestClient.Builder builder, StackoverflowProperties properties) {
        return builder.baseUrl(properties.getBaseUrl()).build();
    }
}
