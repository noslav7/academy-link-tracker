package backend.academy.linktracker.scrapper.service;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import backend.academy.linktracker.scrapper.dto.LinkUpdate;
import backend.academy.linktracker.scrapper.properties.KafkaProperties;
import java.util.List;
import org.apache.avro.generic.GenericRecord;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;

@Tag("unit")
class KafkaMessageSenderTest {

    @Test
    @SuppressWarnings("unchecked")
    void givenLinkUpdateWhenDispatchThenKafkaTemplateReceivesExpectedAvroRecord() {
        // Проверяет Kafka mapping без поднятия Docker-инфраструктуры.
        KafkaTemplate<String, GenericRecord> kafkaTemplate = mock(KafkaTemplate.class);
        KafkaProperties kafkaProperties = new KafkaProperties();
        kafkaProperties.setTopic("link-updates-scrapper-it");
        KafkaMessageSender sender = new KafkaMessageSender(kafkaTemplate, kafkaProperties);
        LinkUpdate update = new LinkUpdate(
                77L, "https://github.com/octocat/hello-world", "Repository updated", List.of(501L, 777L));

        sender.dispatch(update);

        ArgumentCaptor<GenericRecord> eventCaptor = ArgumentCaptor.forClass(GenericRecord.class);
        verify(kafkaTemplate).send(eq("link-updates-scrapper-it"), eq("77"), eventCaptor.capture());
        GenericRecord event = eventCaptor.getValue();
        assertAll(
                () -> assertEquals(77L, event.get("id")),
                () -> assertEquals("https://github.com/octocat/hello-world", String.valueOf(event.get("url"))),
                () -> assertEquals("Repository updated", String.valueOf(event.get("description"))),
                () -> assertEquals(
                        List.of(501L, 777L),
                        ((List<?>) event.get("tgChatIds"))
                                .stream()
                                        .map(value -> ((Number) value).longValue())
                                        .toList()));
    }
}
