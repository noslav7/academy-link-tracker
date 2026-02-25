package backend.academy.linktracker.bot.configuration;

import backend.academy.linktracker.bot.client.GrpcScrapperClient;
import backend.academy.linktracker.bot.client.RestScrapperClient;
import backend.academy.linktracker.bot.client.ScrapperClient;
import backend.academy.linktracker.bot.properties.ScrapperProperties;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.client.RestClient;

@Configuration
public class ScrapperClientConfiguration {

    @Bean
    RestClient scrapperRestClient(ScrapperProperties properties, RestClient.Builder builder) {
        return builder.baseUrl(properties.getBaseUrl()).build();
    }

    @Bean(destroyMethod = "shutdown")
    ManagedChannel scrapperGrpcChannel(ScrapperProperties properties) {
        return ManagedChannelBuilder.forAddress(properties.getGrpcHost(), properties.getGrpcPort())
                .usePlaintext()
                .build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "app.scrapper", name = "transport", havingValue = "HTTP", matchIfMissing = true)
    ScrapperClient restModeScrapperClient(RestClient scrapperRestClient) {
        return new RestScrapperClient(scrapperRestClient);
    }

    @Bean
    @ConditionalOnProperty(prefix = "app.scrapper", name = "transport", havingValue = "GRPC")
    ScrapperClient grpcModeScrapperClient(ManagedChannel scrapperGrpcChannel) {
        return new GrpcScrapperClient(scrapperGrpcChannel);
    }
}
