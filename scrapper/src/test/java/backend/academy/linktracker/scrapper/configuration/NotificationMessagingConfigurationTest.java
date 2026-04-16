package backend.academy.linktracker.scrapper.configuration;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import backend.academy.linktracker.scrapper.client.BotNotificationsClient;
import backend.academy.linktracker.scrapper.dto.LinkUpdate;
import backend.academy.linktracker.scrapper.properties.KafkaProperties;
import backend.academy.linktracker.scrapper.service.MessageSender;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.kafka.core.KafkaTemplate;

@Tag("sanity")
class NotificationMessagingConfigurationTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner().withUserConfiguration(NotificationMessagingConfiguration.class);

    @Test
    void givenHttpTransportWhenContextStartsThenClientMessageSenderIsUsed() {
        // Проверяет: при HTTP-транспорте сообщение делегируется в BotNotificationsClient.
        BotNotificationsClient botNotificationsClient = mock(BotNotificationsClient.class);

        contextRunner
                .withPropertyValues("app.bot.transport=HTTP")
                .withBean(BotNotificationsClient.class, () -> botNotificationsClient)
                .run(context -> {
                    MessageSender messageSender = context.getBean(MessageSender.class);
                    LinkUpdate update = new LinkUpdate(11L, "https://example.com", "description", List.of(1001L));

                    messageSender.send(update);

                    verify(botNotificationsClient).sendUpdate(update);
                });
    }

    @Test
    @SuppressWarnings("unchecked")
    void givenKafkaTransportWhenContextStartsThenKafkaMessageSenderIsUsed() {
        // Проверяет: при KAFKA-транспорте сообщение публикуется в ожидаемый топик.
        KafkaTemplate<String, LinkUpdate> kafkaTemplate = mock(KafkaTemplate.class);
        KafkaProperties kafkaProperties = new KafkaProperties();
        kafkaProperties.setTopic("link-updates-test");

        contextRunner
                .withPropertyValues("app.bot.transport=KAFKA")
                .withBean(KafkaTemplate.class, () -> kafkaTemplate)
                .withBean(KafkaProperties.class, () -> kafkaProperties)
                .run(context -> {
                    MessageSender messageSender = context.getBean(MessageSender.class);
                    LinkUpdate update = new LinkUpdate(12L, "https://example.org", "description", List.of(1002L));

                    messageSender.send(update);

                    verify(kafkaTemplate).send("link-updates-test", "12", update);
                });
    }
}
