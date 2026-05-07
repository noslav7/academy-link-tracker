package backend.academy.linktracker.scrapper.client;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import backend.academy.linktracker.scrapper.dto.LinkUpdate;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

@Tag("unit")
class RestBotNotificationsClientTest {

    @Test
    void givenLinkUpdateWhenSendUpdateCalledThenRestClientPostsExactPayloadToUpdatesEndpoint() {
        // Проверяет REST-канал уведомлений: отправка идёт в /updates с полным payload.
        RestClient restClient = mock(RestClient.class);
        RestClient.RequestBodyUriSpec requestBodyUriSpec = mock(RestClient.RequestBodyUriSpec.class);
        RestClient.RequestBodySpec requestBodySpec = mock(RestClient.RequestBodySpec.class);
        RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);
        LinkUpdate update = new LinkUpdate(17L, "https://example.com/resource", "updated", List.of(101L, 202L));

        when(restClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri("/updates")).thenReturn(requestBodySpec);
        when(requestBodySpec.body(update)).thenReturn(requestBodySpec);
        when(requestBodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.toBodilessEntity()).thenReturn(ResponseEntity.ok().build());

        RestBotNotificationsClient client = new RestBotNotificationsClient(restClient);
        client.sendUpdate(update);

        InOrder inOrder = inOrder(restClient, requestBodyUriSpec, requestBodySpec, responseSpec);
        assertAll(
                () -> inOrder.verify(restClient).post(),
                () -> inOrder.verify(requestBodyUriSpec).uri("/updates"),
                () -> inOrder.verify(requestBodySpec).body(update),
                () -> inOrder.verify(requestBodySpec).retrieve(),
                () -> inOrder.verify(responseSpec).toBodilessEntity());
    }
}
