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

@Configuration
public class HttpClientConfiguration {

    @Bean
    RestClient botRestClient(RestClient.Builder builder, BotClientProperties properties) {
        return builder.baseUrl(properties.getBaseUrl()).build();
    }

    @Bean(destroyMethod = "shutdown")
    ManagedChannel botGrpcChannel(BotClientProperties properties) {
        return ManagedChannelBuilder.forAddress(properties.getGrpcHost(), properties.getGrpcPort())
                .usePlaintext()
                .build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "app.bot", name = "transport", havingValue = "HTTP", matchIfMissing = true)
    BotNotificationsClient restBotNotificationsClient(RestClient botRestClient) {
        return new RestBotNotificationsClient(botRestClient);
    }

    @Bean
    @ConditionalOnProperty(prefix = "app.bot", name = "transport", havingValue = "GRPC")
    BotNotificationsClient grpcBotNotificationsClient(ManagedChannel botGrpcChannel) {
        return new GrpcBotNotificationsClient(botGrpcChannel);
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
