package backend.academy.linktracker.scrapper.configuration;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import backend.academy.linktracker.scrapper.client.BotNotificationsClient;
import backend.academy.linktracker.scrapper.dto.LinkUpdate;
import backend.academy.linktracker.scrapper.properties.KafkaProperties;
import backend.academy.linktracker.scrapper.service.NotificationDispatcher;
import backend.academy.linktracker.scrapper.service.outbox.NotificationOutboxRepository;
import java.util.List;
import org.apache.avro.generic.GenericRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.kafka.core.KafkaTemplate;

@Tag("sanity")
@Tag("unit")
@DisplayName("Given transport When context starts Then dispatcher uses expected channel")
class NotificationMessagingConfigurationTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner().withUserConfiguration(NotificationMessagingConfiguration.class);

    @Test
    void givenHttpTransportWhenContextStartsThenClientMessageSenderIsUsed() {
        // Проверяет: при HTTP-транспорте dispatcher делегирует в BotNotificationsClient.
        BotNotificationsClient botNotificationsClient = mock(BotNotificationsClient.class);
        NotificationOutboxRepository outboxRepository = mock(NotificationOutboxRepository.class);

        contextRunner
                .withPropertyValues("app.bot.transport=HTTP")
                .withBean(BotNotificationsClient.class, () -> botNotificationsClient)
                .withBean(NotificationOutboxRepository.class, () -> outboxRepository)
                .run(context -> {
                    NotificationDispatcher dispatcher = context.getBean(NotificationDispatcher.class);
                    LinkUpdate update = new LinkUpdate(11L, "https://example.com", "description", List.of(1001L));

                    dispatcher.dispatch(update);

                    verify(botNotificationsClient).sendUpdate(update);
                });
    }

    @Test
    @SuppressWarnings("unchecked")
    void givenKafkaTransportWhenContextStartsThenKafkaMessageSenderIsUsed() {
        // Проверяет: при KAFKA-транспорте dispatcher публикует в ожидаемый топик.
        KafkaTemplate<String, Object> kafkaTemplate = mock(KafkaTemplate.class);
        KafkaProperties kafkaProperties = new KafkaProperties();
        kafkaProperties.setTopic("link-updates-test");
        NotificationOutboxRepository outboxRepository = mock(NotificationOutboxRepository.class);

        contextRunner
                .withPropertyValues("app.bot.transport=KAFKA")
                .withBean(KafkaTemplate.class, () -> kafkaTemplate)
                .withBean(KafkaProperties.class, () -> kafkaProperties)
                .withBean(NotificationOutboxRepository.class, () -> outboxRepository)
                .run(context -> {
                    NotificationDispatcher dispatcher = context.getBean(NotificationDispatcher.class);
                    LinkUpdate update = new LinkUpdate(12L, "https://example.org", "description", List.of(1002L));

                    dispatcher.dispatch(update);

                    ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
                    verify(kafkaTemplate)
                            .send(
                                    org.mockito.ArgumentMatchers.eq("link-updates-test"),
                                    org.mockito.ArgumentMatchers.eq("12"),
                                    eventCaptor.capture());
                    GenericRecord sentEvent = (GenericRecord) eventCaptor.getValue();
                    assertAll(
                            () -> assertEquals(12L, sentEvent.get("id")),
                            () -> assertEquals("https://example.org", String.valueOf(sentEvent.get("url"))),
                            () -> assertEquals("description", String.valueOf(sentEvent.get("description"))),
                            () -> assertEquals(
                                    List.of(1002L),
                                    ((List<?>) sentEvent.get("tgChatIds"))
                                            .stream()
                                                    .map(value -> ((Number) value).longValue())
                                                    .toList()));
                });
    }
}
