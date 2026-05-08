package backend.academy.linktracker.scrapper.configuration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import backend.academy.linktracker.scrapper.client.BotNotificationsClient;
import backend.academy.linktracker.scrapper.dto.LinkUpdate;
import backend.academy.linktracker.scrapper.properties.BotClientProperties;
import backend.academy.linktracker.scrapper.properties.KafkaProperties;
import backend.academy.linktracker.scrapper.service.NotificationDispatcher;
import backend.academy.linktracker.scrapper.service.outbox.NotificationOutboxRepository;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.apache.avro.generic.GenericRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.kafka.core.KafkaTemplate;

@Tag("unit")
@DisplayName("Given transport When context starts Then dispatcher uses expected channel")
class NotificationMessagingConfigurationTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner().withUserConfiguration(NotificationMessagingConfiguration.class);

    @Test
    @Tag("sanity")
    @DisplayName(
            "Given HTTP transport without fallback When context starts Then notification dispatcher bean is created")
    void givenHttpTransportWithoutFallbackWhenContextStartsThenNotificationDispatcherBeanIsCreated() {
        // Smoke: быстрый контроль, что базовый HTTP dispatcher поднимается в контексте.
        BotNotificationsClient botNotificationsClient = mock(BotNotificationsClient.class);
        NotificationOutboxRepository outboxRepository = mock(NotificationOutboxRepository.class);

        contextRunner
                .withPropertyValues("app.bot.transport=HTTP", "app.bot.http-fallback-to-kafka-enabled=false")
                .withBean(BotNotificationsClient.class, () -> botNotificationsClient)
                .withBean(NotificationOutboxRepository.class, () -> outboxRepository)
                .run(context -> assertNotNull(context.getBean(NotificationDispatcher.class)));
    }

    @ParameterizedTest
    @MethodSource("httpTransportProperties")
    void givenHttpTransportWhenDispatchingThenClientMessageSenderIsUsed(String[] propertyValues) {
        // Проверяет: при HTTP-транспорте (с fallback или без него) dispatcher делегирует в BotNotificationsClient.
        BotNotificationsClient botNotificationsClient = mock(BotNotificationsClient.class);
        NotificationOutboxRepository outboxRepository = mock(NotificationOutboxRepository.class);

        contextRunner
                .withPropertyValues(propertyValues)
                .withBean(BotNotificationsClient.class, () -> botNotificationsClient)
                .withBean(KafkaTemplate.class, () -> mock(KafkaTemplate.class))
                .withBean(KafkaProperties.class, () -> {
                    KafkaProperties properties = new KafkaProperties();
                    properties.setTopic("link-updates-test");
                    return properties;
                })
                .withBean(BotClientProperties.class, BotClientProperties::new)
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
    void givenHttpTransportWhenPrimaryClientFailsThenHttpClientIsCalled() {
        // Проверяет, что при fallback-сценарии сначала вызывается HTTP-клиент.
        BotNotificationsClient botNotificationsClient = mock(BotNotificationsClient.class);
        KafkaTemplate<String, Object> kafkaTemplate = mock(KafkaTemplate.class);
        NotificationOutboxRepository outboxRepository = mock(NotificationOutboxRepository.class);
        KafkaProperties kafkaProperties = new KafkaProperties();
        kafkaProperties.setTopic("link-updates-fallback");
        BotClientProperties botClientProperties = new BotClientProperties();

        contextRunner
                .withPropertyValues("app.bot.transport=HTTP")
                .withBean(BotNotificationsClient.class, () -> botNotificationsClient)
                .withBean(KafkaTemplate.class, () -> kafkaTemplate)
                .withBean(KafkaProperties.class, () -> kafkaProperties)
                .withBean(BotClientProperties.class, () -> botClientProperties)
                .withBean(NotificationOutboxRepository.class, () -> outboxRepository)
                .run(context -> {
                    doThrow(new IllegalStateException("HTTP unavailable"))
                            .when(botNotificationsClient)
                            .sendUpdate(org.mockito.ArgumentMatchers.any(LinkUpdate.class));
                    NotificationDispatcher dispatcher = context.getBean(NotificationDispatcher.class);
                    LinkUpdate update = new LinkUpdate(13L, "https://example.net", "description", List.of(1003L));

                    dispatcher.dispatch(update);

                    verify(botNotificationsClient).sendUpdate(update);
                    verify(kafkaTemplate)
                            .send(
                                    org.mockito.ArgumentMatchers.eq("link-updates-fallback"),
                                    org.mockito.ArgumentMatchers.eq("13"),
                                    org.mockito.ArgumentMatchers.any(GenericRecord.class));
                });
    }

    @Test
    @SuppressWarnings("unchecked")
    void givenHttpTransportWhenPrimaryClientFailsThenKafkaFallbackIsCalled() {
        // Проверяет, что при ошибке HTTP-клиента отправка уходит в Kafka fallback.
        BotNotificationsClient botNotificationsClient = mock(BotNotificationsClient.class);
        KafkaTemplate<String, Object> kafkaTemplate = mock(KafkaTemplate.class);
        NotificationOutboxRepository outboxRepository = mock(NotificationOutboxRepository.class);
        KafkaProperties kafkaProperties = new KafkaProperties();
        kafkaProperties.setTopic("link-updates-fallback");
        BotClientProperties botClientProperties = new BotClientProperties();

        contextRunner
                .withPropertyValues("app.bot.transport=HTTP")
                .withBean(BotNotificationsClient.class, () -> botNotificationsClient)
                .withBean(KafkaTemplate.class, () -> kafkaTemplate)
                .withBean(KafkaProperties.class, () -> kafkaProperties)
                .withBean(BotClientProperties.class, () -> botClientProperties)
                .withBean(NotificationOutboxRepository.class, () -> outboxRepository)
                .run(context -> {
                    doThrow(new IllegalStateException("HTTP unavailable"))
                            .when(botNotificationsClient)
                            .sendUpdate(org.mockito.ArgumentMatchers.any(LinkUpdate.class));
                    NotificationDispatcher dispatcher = context.getBean(NotificationDispatcher.class);
                    LinkUpdate update = new LinkUpdate(14L, "https://example.io", "description", List.of(1004L));

                    dispatcher.dispatch(update);

                    verify(kafkaTemplate)
                            .send(
                                    org.mockito.ArgumentMatchers.eq("link-updates-fallback"),
                                    org.mockito.ArgumentMatchers.eq("14"),
                                    org.mockito.ArgumentMatchers.any(GenericRecord.class));
                });
    }

    @Test
    @SuppressWarnings("unchecked")
    void givenKafkaTransportWhenDispatchingThenMessageIsPublishedWithConfiguredTopicAndKey() {
        // Проверяет, что при KAFKA-транспорте сообщение уходит в нужный топик с ключом linkId.
        KafkaTemplate<String, Object> kafkaTemplate = mock(KafkaTemplate.class);
        KafkaProperties kafkaProperties = new KafkaProperties();
        kafkaProperties.setTopic("link-updates-test");
        NotificationOutboxRepository outboxRepository = mock(NotificationOutboxRepository.class);

        contextRunner
                .withPropertyValues("app.bot.transport=KAFKA")
                .withBean(KafkaTemplate.class, () -> kafkaTemplate)
                .withBean(KafkaProperties.class, () -> kafkaProperties)
                .withBean(BotClientProperties.class, BotClientProperties::new)
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
                });
    }

    @Test
    @SuppressWarnings("unchecked")
    void givenKafkaTransportWhenDispatchingThenPublishedEventContainsExpectedPayload() {
        // Проверяет, что Kafka-событие содержит ожидаемый payload.
        KafkaTemplate<String, Object> kafkaTemplate = mock(KafkaTemplate.class);
        KafkaProperties kafkaProperties = new KafkaProperties();
        kafkaProperties.setTopic("link-updates-test");
        NotificationOutboxRepository outboxRepository = mock(NotificationOutboxRepository.class);

        contextRunner
                .withPropertyValues("app.bot.transport=KAFKA")
                .withBean(KafkaTemplate.class, () -> kafkaTemplate)
                .withBean(KafkaProperties.class, () -> kafkaProperties)
                .withBean(BotClientProperties.class, BotClientProperties::new)
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
                    Map<String, Object> actualPayload = Map.of(
                            "id", sentEvent.get("id"),
                            "url", String.valueOf(sentEvent.get("url")),
                            "description", String.valueOf(sentEvent.get("description")),
                            "tgChatIds",
                                    ((List<?>) sentEvent.get("tgChatIds"))
                                            .stream()
                                                    .map(value -> ((Number) value).longValue())
                                                    .toList());
                    Map<String, Object> expectedPayload = Map.of(
                            "id",
                            12L,
                            "url",
                            "https://example.org",
                            "description",
                            "description",
                            "tgChatIds",
                            List.of(1002L));

                    assertEquals(expectedPayload, actualPayload);
                });
    }

    private static Stream<Arguments> httpTransportProperties() {
        return Stream.of(Arguments.of((Object) new String[] {"app.bot.transport=HTTP"}), Arguments.of((Object)
                new String[] {"app.bot.transport=HTTP", "app.bot.http-fallback-to-kafka-enabled=false"}));
    }
}
