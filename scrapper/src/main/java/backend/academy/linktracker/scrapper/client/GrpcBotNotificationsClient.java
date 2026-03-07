package backend.academy.linktracker.scrapper.client;

import backend.academy.linktracker.grpc.BotUpdatesApiGrpc;
import backend.academy.linktracker.grpc.LinkUpdateRequest;
import backend.academy.linktracker.scrapper.dto.LinkUpdate;
import io.grpc.ManagedChannel;

/**
 * gRPC-клиент отправки уведомлений в bot-сервис.
 */
public class GrpcBotNotificationsClient implements BotNotificationsClient {

    /**
     * Блокирующий gRPC stub сервиса уведомлений бота.
     */
    private final BotUpdatesApiGrpc.BotUpdatesApiBlockingStub stub;

    /**
     * Создает gRPC-клиент уведомлений.
     *
     * @param managedChannel gRPC-канал до bot-сервиса
     */
    public GrpcBotNotificationsClient(ManagedChannel managedChannel) {
        this.stub = BotUpdatesApiGrpc.newBlockingStub(managedChannel);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void sendUpdate(LinkUpdate update) {
        stub.sendUpdate(LinkUpdateRequest.newBuilder()
                .setId(update.id())
                .setUrl(update.url() == null ? "" : update.url())
                .setDescription(update.description() == null ? "" : update.description())
                .addAllTgChatIds(update.tgChatIds() == null ? java.util.List.of() : update.tgChatIds())
                .build());
    }
}
