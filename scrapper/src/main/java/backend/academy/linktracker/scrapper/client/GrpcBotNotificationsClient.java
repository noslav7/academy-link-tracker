package backend.academy.linktracker.scrapper.client;

import backend.academy.linktracker.grpc.BotUpdatesApiGrpc;
import backend.academy.linktracker.grpc.LinkUpdateRequest;
import backend.academy.linktracker.scrapper.dto.LinkUpdate;
import backend.academy.linktracker.scrapper.metrics.ScrapperMetricsRecorder;
import io.grpc.ManagedChannel;
import io.grpc.StatusRuntimeException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * gRPC-клиент отправки уведомлений в bot-сервис.
 */
public class GrpcBotNotificationsClient implements BotNotificationsClient {
    private static final String SCOPE_EXTERNAL_SOURCE = "external_source";
    private static final String SCOPE_TYPE_BOT_GRPC = "bot_grpc";
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(2);

    /**
     * Блокирующий gRPC stub сервиса уведомлений бота.
     */
    private final BotUpdatesApiGrpc.BotUpdatesApiBlockingStub stub;

    private final ScrapperMetricsRecorder metricsRecorder;

    /**
     * Создает gRPC-клиент уведомлений.
     *
     * @param managedChannel gRPC-канал до bot-сервиса
     */
    public GrpcBotNotificationsClient(ManagedChannel managedChannel) {
        this(managedChannel, ScrapperMetricsRecorder.noop());
    }

    public GrpcBotNotificationsClient(ManagedChannel managedChannel, ScrapperMetricsRecorder metricsRecorder) {
        this(managedChannel, DEFAULT_TIMEOUT, metricsRecorder);
    }

    public GrpcBotNotificationsClient(
            ManagedChannel managedChannel, Duration timeout, ScrapperMetricsRecorder metricsRecorder) {
        this.stub = BotUpdatesApiGrpc.newBlockingStub(managedChannel)
                .withDeadlineAfter(timeout.toMillis(), TimeUnit.MILLISECONDS);
        this.metricsRecorder = metricsRecorder;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void sendUpdate(LinkUpdate update) {
        metricsRecorder.recordDuration(SCOPE_EXTERNAL_SOURCE, SCOPE_TYPE_BOT_GRPC, () -> {
            try {
                return stub.sendUpdate(LinkUpdateRequest.newBuilder()
                        .setId(update.id())
                        .setUrl(update.url() == null ? "" : update.url())
                        .setDescription(update.description() == null ? "" : update.description())
                        .addAllTgChatIds(update.tgChatIds() == null ? java.util.List.of() : update.tgChatIds())
                        .build());
            } catch (StatusRuntimeException exception) {
                throw new BotServiceUnavailableException("Failed to send update to bot over gRPC", exception);
            }
        });
    }
}
