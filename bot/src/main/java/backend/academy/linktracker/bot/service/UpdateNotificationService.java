package backend.academy.linktracker.bot.service;

import backend.academy.linktracker.bot.client.dto.LinkUpdate;
import backend.academy.linktracker.bot.command.BotMessages;
import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.request.SendMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Сервис отправки уведомлений пользователям Telegram-бота.
 */
@Service
public class UpdateNotificationService {
    private static final String DETAILS_PREFIX = "\n\n";

    /**
     * Логгер компонента.
     */
    private static final Logger log = LoggerFactory.getLogger(UpdateNotificationService.class);
    /**
     * Клиент Telegram API.
     */
    private final TelegramBot telegramBot;

    /**
     * Создает сервис отправки уведомлений.
     *
     * @param telegramBot клиент Telegram API
     */
    public UpdateNotificationService(TelegramBot telegramBot) {
        this.telegramBot = telegramBot;
    }

    /**
     * Отправляет уведомление всем чатам из входящего обновления.
     *
     * @param update данные обновления ссылки
     */
    public void notifyAboutUpdate(LinkUpdate update) {
        if (update == null || update.tgChatIds() == null) {
            return;
        }

        String notificationText = buildNotificationText(update);

        for (Long chatId : update.tgChatIds()) {
            if (chatId == null) {
                continue;
            }
            telegramBot.execute(new SendMessage(String.valueOf(chatId), notificationText));
            log.atInfo()
                    .addKeyValue("chatId", chatId)
                    .addKeyValue("linkId", update.id())
                    .addKeyValue("url", update.url())
                    .log("Sent update notification");
        }
    }

    private static String buildNotificationText(LinkUpdate update) {
        if (update.description() == null || update.description().isBlank()) {
            return BotMessages.UPDATE_NOTIFICATION;
        }
        return BotMessages.UPDATE_NOTIFICATION + DETAILS_PREFIX + update.description();
    }
}
