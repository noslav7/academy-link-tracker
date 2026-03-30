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

/**
 * Управляет жизненным циклом gRPC-сервера scrapper-сервиса.
 *
 * <p>Используется {@link ApplicationRunner} вместо конструктора/PostConstruct,
 * поскольку gRPC-сервер открывает сетевой порт и начинает принимать запросы —
 * это допустимо только после полной инициализации контекста Spring,
 * чтобы входящие вызовы не попадали на частично инициализированные бины.
 */
@Component
public class ScrapperGrpcServerLifecycle implements ApplicationRunner {

    /**
     * Логгер компонента.
     */
    private static final Logger log = LoggerFactory.getLogger(ScrapperGrpcServerLifecycle.class);

    /**
     * Настройки gRPC-сервера.
     */
    private final GrpcServerProperties grpcServerProperties;
    /**
     * Сервис управления чатами.
     */
    private final ChatService chatService;
    /**
     * Сервис управления ссылками.
     */
    private final LinkService linkService;
    /**
     * Экземпляр gRPC-сервера.
     */
    private Server server;

    /**
     * Создает компонент жизненного цикла gRPC-сервера.
     *
     * @param grpcServerProperties настройки gRPC-сервера
     * @param chatService сервис чатов
     * @param linkService сервис ссылок
     */
    public ScrapperGrpcServerLifecycle(
            GrpcServerProperties grpcServerProperties, ChatService chatService, LinkService linkService) {
        this.grpcServerProperties = grpcServerProperties;
        this.chatService = chatService;
        this.linkService = linkService;
    }

    /**
     * Запускает gRPC-сервер при старте приложения.
     *
     * @param args аргументы запуска приложения
     * @throws IOException при ошибке запуска сервера
     */
    @Override
    public void run(ApplicationArguments args) throws IOException {
        if (!grpcServerProperties.isEnabled()) {
            return;
        }

        try {
            server = ServerBuilder.forPort(grpcServerProperties.getPort())
                    .addService(new ScrapperGrpcService(chatService, linkService))
                    .build();
            server.start();
            log.atInfo()
                    .addKeyValue("grpcServerPort", grpcServerProperties.getPort())
                    .log("Scrapper gRPC server started");
        } catch (IOException exception) {
            log.atError()
                    .setCause(exception)
                    .addKeyValue("grpcServerPort", grpcServerProperties.getPort())
                    .log("Failed to start scrapper gRPC server");
            throw exception;
        }
    }

    /**
     * Останавливает gRPC-сервер при завершении приложения.
     */
    @PreDestroy
    private void stop() {
        if (server != null) {
            server.shutdown();
        }
    }
}
