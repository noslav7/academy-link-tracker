package backend.academy.linktracker.scrapper.grpc;

import backend.academy.linktracker.scrapper.properties.GrpcServerProperties;
import backend.academy.linktracker.scrapper.service.ChatService;
import backend.academy.linktracker.scrapper.service.LinkService;
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
public class ScrapperGrpcServerLifecycle implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ScrapperGrpcServerLifecycle.class);

    private final GrpcServerProperties grpcServerProperties;
    private final ChatService chatService;
    private final LinkService linkService;
    private Server server;

    public ScrapperGrpcServerLifecycle(
            GrpcServerProperties grpcServerProperties, ChatService chatService, LinkService linkService) {
        this.grpcServerProperties = grpcServerProperties;
        this.chatService = chatService;
        this.linkService = linkService;
    }

    @Override
    public void run(ApplicationArguments args) throws IOException {
        if (!grpcServerProperties.isEnabled()) {
            return;
        }

        server = ServerBuilder.forPort(grpcServerProperties.getPort())
                .addService(new ScrapperGrpcService(chatService, linkService))
                .build();
        server.start();
        log.atInfo()
                .addKeyValue("grpcServerPort", grpcServerProperties.getPort())
                .log("Scrapper gRPC server started");
    }

    @PreDestroy
    public void stop() {
        if (server != null) {
            server.shutdown();
        }
    }
}
