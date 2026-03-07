package backend.academy.linktracker.bot.configuration;

import backend.academy.linktracker.bot.client.GrpcScrapperClient;
import backend.academy.linktracker.bot.client.RestScrapperClient;
import backend.academy.linktracker.bot.client.ScrapperClient;
import backend.academy.linktracker.bot.properties.ScrapperProperties;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
        return builder.baseUrl(properties.getBaseUrl()).build();
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
    @ConditionalOnProperty(prefix = "app.scrapper", name = "transport", havingValue = "HTTP", matchIfMissing = true)
    ScrapperClient restModeScrapperClient(RestClient scrapperRestClient) {
        return new RestScrapperClient(scrapperRestClient);
    }

    /**
     * Регистрирует gRPC-реализацию клиента scrapper.
     *
     * @param scrapperGrpcChannel gRPC-канал scrapper
     * @return gRPC-клиент scrapper
     */
    @Bean
    @ConditionalOnProperty(prefix = "app.scrapper", name = "transport", havingValue = "GRPC")
    ScrapperClient grpcModeScrapperClient(ManagedChannel scrapperGrpcChannel) {
        return new GrpcScrapperClient(scrapperGrpcChannel);
    }
}
