package backend.academy.linktracker.scrapper.configuration;

import backend.academy.linktracker.scrapper.client.BotNotificationsClient;
import backend.academy.linktracker.scrapper.client.GrpcBotNotificationsClient;
import backend.academy.linktracker.scrapper.client.RestBotNotificationsClient;
import backend.academy.linktracker.scrapper.properties.BotClientProperties;
import backend.academy.linktracker.scrapper.properties.TransportMode;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Конфигурация транспорта уведомлений до bot-сервиса (REST / gRPC).
 */
@Configuration
public class BotClientConfiguration {

    /**
     * REST-клиент для отправки уведомлений в bot.
     */
    @Bean
    RestClient botRestClient(RestClient.Builder builder, BotClientProperties properties) {
        return builder.baseUrl(properties.getBaseUrl()).build();
    }

    /**
     * gRPC-канал до bot-сервиса.
     */
    @Bean(destroyMethod = "shutdown")
    ManagedChannel botGrpcChannel(BotClientProperties properties) {
        return ManagedChannelBuilder.forAddress(properties.getGrpcHost(), properties.getGrpcPort())
                .usePlaintext()
                .build();
    }

    /**
     * REST-клиент уведомлений (транспорт по умолчанию).
     */
    @Bean
    @ConditionalOnProperty(
            prefix = "app.bot",
            name = "transport",
            havingValue = TransportMode.HTTP_VALUE,
            matchIfMissing = true)
    BotNotificationsClient restBotNotificationsClient(RestClient botRestClient) {
        return new RestBotNotificationsClient(botRestClient);
    }

    /**
     * gRPC-клиент уведомлений (активируется при transport=GRPC).
     */
    @Bean
    @ConditionalOnProperty(prefix = "app.bot", name = "transport", havingValue = TransportMode.GRPC_VALUE)
    BotNotificationsClient grpcBotNotificationsClient(ManagedChannel botGrpcChannel) {
        return new GrpcBotNotificationsClient(botGrpcChannel);
    }
}
