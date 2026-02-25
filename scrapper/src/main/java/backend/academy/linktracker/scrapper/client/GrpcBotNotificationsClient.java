package backend.academy.linktracker.scrapper.client;

import backend.academy.linktracker.grpc.BotUpdatesApiGrpc;
import backend.academy.linktracker.grpc.LinkUpdateRequest;
import backend.academy.linktracker.scrapper.dto.LinkUpdate;
import io.grpc.ManagedChannel;

public class GrpcBotNotificationsClient implements BotNotificationsClient {

    private final BotUpdatesApiGrpc.BotUpdatesApiBlockingStub stub;

    public GrpcBotNotificationsClient(ManagedChannel managedChannel) {
        this.stub = BotUpdatesApiGrpc.newBlockingStub(managedChannel);
    }

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
