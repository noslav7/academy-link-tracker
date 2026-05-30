package backend.academy.linktracker.bot.configuration;

import backend.academy.linktracker.bot.client.GrpcScrapperClient;
import backend.academy.linktracker.bot.client.RestScrapperClient;
import backend.academy.linktracker.bot.client.ScrapperClient;
import backend.academy.linktracker.bot.metrics.BotMetricsRecorder;
import backend.academy.linktracker.bot.properties.ScrapperProperties;
import backend.academy.linktracker.bot.properties.TransportMode;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Конфигурация HTTP/gRPC клиентов для взаимодействия бота со scrapper.
 */
@Configuration
public class ScrapperClientConfiguration {

    /**
     * Создает REST-клиент scrapper-сервиса.
     *
     * @param properties настройки scrapper
     * @param builder фабрика REST-клиента
     * @return REST-клиент scrapper
     */
    @Bean
    RestClient scrapperRestClient(ScrapperProperties properties, RestClient.Builder builder) {
        return builder.requestFactory(clientHttpRequestFactory(properties))
                .baseUrl(properties.getBaseUrl())
                .build();
    }

    /**
     * Создает gRPC-канал до scrapper-сервиса.
     *
     * @param properties настройки scrapper
     * @return gRPC-канал до scrapper
     */
    @Bean(destroyMethod = "shutdown")
    ManagedChannel scrapperGrpcChannel(ScrapperProperties properties) {
        return ManagedChannelBuilder.forAddress(properties.getGrpcHost(), properties.getGrpcPort())
                .usePlaintext()
                .build();
    }

    /**
     * Регистрирует HTTP-реализацию клиента scrapper.
     *
     * @param scrapperRestClient REST-клиент scrapper
     * @return HTTP-клиент scrapper
     */
    @Bean
    @ConditionalOnProperty(
            prefix = "app.scrapper",
            name = "transport",
            havingValue = TransportMode.HTTP_VALUE,
            matchIfMissing = true)
    ScrapperClient restModeScrapperClient(RestClient scrapperRestClient, BotMetricsRecorder metricsRecorder) {
        return new RestScrapperClient(scrapperRestClient, metricsRecorder);
    }

    /**
     * Регистрирует gRPC-реализацию клиента scrapper.
     *
     * @param scrapperGrpcChannel gRPC-канал scrapper
     * @return gRPC-клиент scrapper
     */
    @Bean
    @ConditionalOnProperty(prefix = "app.scrapper", name = "transport", havingValue = TransportMode.GRPC_VALUE)
    ScrapperClient grpcModeScrapperClient(
            ManagedChannel scrapperGrpcChannel, ScrapperProperties properties, BotMetricsRecorder metricsRecorder) {
        return new GrpcScrapperClient(scrapperGrpcChannel, properties.getTimeout(), metricsRecorder);
    }

    private ClientHttpRequestFactory clientHttpRequestFactory(ScrapperProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.getTimeout());
        requestFactory.setReadTimeout(properties.getTimeout());
        return requestFactory;
    }
}
