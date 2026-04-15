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

    /**
     * Логгер компонента.
     */
    private static final Logger log = LoggerFactory.getLogger(LinkUpdateListener.class);

    /**
     * Сервис отправки уведомлений в Telegram.
     */
    private final UpdateNotificationService updateNotificationService;

    /**
     * Создает Kafka-слушатель уведомлений.
     *
     * @param updateNotificationService сервис доставки уведомлений
     */
    public LinkUpdateListener(UpdateNotificationService updateNotificationService) {
        this.updateNotificationService = updateNotificationService;
    }

    /**
     * Обрабатывает входящее уведомление об обновлении ссылки из Kafka.
     *
     * @param update уведомление об обновлении ссылки
     */
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
