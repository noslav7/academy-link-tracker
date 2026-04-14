package backend.academy.linktracker.bot.kafka;

import backend.academy.linktracker.bot.client.dto.LinkUpdate;
import backend.academy.linktracker.bot.service.UpdateNotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Kafka-консьюмер уведомлений об обновлениях ссылок от scrapper.
 */
@Component
public class LinkUpdateListener {

    private static final Logger log = LoggerFactory.getLogger(LinkUpdateListener.class);

    private final UpdateNotificationService updateNotificationService;

    public LinkUpdateListener(UpdateNotificationService updateNotificationService) {
        this.updateNotificationService = updateNotificationService;
    }

    @KafkaListener(
            topics = "${app.kafka.topic}",
            groupId = "${app.kafka.group-id}",
            containerFactory = "linkUpdateKafkaListenerContainerFactory")
    public void onMessage(LinkUpdate update) {
        if (update == null) {
            log.warn("Received null LinkUpdate message, skipping");
            return;
        }
        updateNotificationService.notifyAboutUpdate(update);
        log.atInfo()
                .addKeyValue("linkId", update.id())
                .addKeyValue("url", update.url())
                .log("Link update notification consumed from Kafka");
    }
}
