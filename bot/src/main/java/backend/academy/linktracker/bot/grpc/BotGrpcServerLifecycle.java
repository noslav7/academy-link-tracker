package backend.academy.linktracker.bot.grpc;

import backend.academy.linktracker.bot.metrics.BotMetricsRecorder;
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

/**
 * Управляет запуском и остановкой gRPC-сервера bot-сервиса.
 *
 * <p>Используется {@link ApplicationRunner} вместо конструктора/PostConstruct,
 * поскольку gRPC-сервер открывает сетевой порт и начинает принимать запросы —
 * это допустимо только после полной инициализации контекста Spring,
 * чтобы входящие вызовы не попадали на частично инициализированные бины.
 */
@Component
public class BotGrpcServerLifecycle implements ApplicationRunner {

    /**
     * Логгер компонента.
     */
    private static final Logger log = LoggerFactory.getLogger(BotGrpcServerLifecycle.class);

    /**
     * Настройки gRPC-сервера.
     */
    private final GrpcServerProperties grpcServerProperties;
    /**
     * Сервис отправки уведомлений.
     */
    private final UpdateNotificationService updateNotificationService;

    private final BotMetricsRecorder metricsRecorder;
    /**
     * Экземпляр gRPC-сервера.
     */
    private Server server;

    /**
     * Создает компонент жизненного цикла gRPC-сервера.
     *
     * @param grpcServerProperties настройки gRPC
     * @param updateNotificationService сервис отправки уведомлений
     */
    public BotGrpcServerLifecycle(
            GrpcServerProperties grpcServerProperties,
            UpdateNotificationService updateNotificationService,
            BotMetricsRecorder metricsRecorder) {
        this.grpcServerProperties = grpcServerProperties;
        this.updateNotificationService = updateNotificationService;
        this.metricsRecorder = metricsRecorder;
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
                    .addService(new BotUpdatesGrpcService(updateNotificationService, metricsRecorder))
                    .build();
            server.start();
            log.atInfo()
                    .addKeyValue("grpcServerPort", grpcServerProperties.getPort())
                    .log("Bot gRPC server started");
        } catch (IOException exception) {
            log.atError()
                    .setCause(exception)
                    .addKeyValue("grpcServerPort", grpcServerProperties.getPort())
                    .log("Failed to start bot gRPC server");
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
