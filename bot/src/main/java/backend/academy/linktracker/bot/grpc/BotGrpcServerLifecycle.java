package backend.academy.linktracker.bot.grpc;

import backend.academy.linktracker.bot.properties.GrpcServerProperties;
import backend.academy.linktracker.bot.service.UpdateNotificationService;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class BotGrpcServerLifecycle implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BotGrpcServerLifecycle.class);

    private final GrpcServerProperties grpcServerProperties;
    private final UpdateNotificationService updateNotificationService;
    private Server server;

    public BotGrpcServerLifecycle(
            GrpcServerProperties grpcServerProperties, UpdateNotificationService updateNotificationService) {
        this.grpcServerProperties = grpcServerProperties;
        this.updateNotificationService = updateNotificationService;
    }

    @Override
    public void run(ApplicationArguments args) throws IOException {
        if (!grpcServerProperties.isEnabled()) {
            return;
        }

        server = ServerBuilder.forPort(grpcServerProperties.getPort())
                .addService(new BotUpdatesGrpcService(updateNotificationService))
                .build();
        server.start();
        log.atInfo()
                .addKeyValue("grpcServerPort", grpcServerProperties.getPort())
                .log("Bot gRPC server started");
    }

    @PreDestroy
    public void stop() {
        if (server != null) {
            server.shutdown();
        }
    }
}
