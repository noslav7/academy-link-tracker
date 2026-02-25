package backend.academy.linktracker.bot.service;

import backend.academy.linktracker.bot.client.dto.LinkUpdate;
import backend.academy.linktracker.bot.command.BotMessages;
import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.request.SendMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class UpdateNotificationService {

    private static final Logger log = LoggerFactory.getLogger(UpdateNotificationService.class);
    private final TelegramBot telegramBot;

    public UpdateNotificationService(TelegramBot telegramBot) {
        this.telegramBot = telegramBot;
    }

    public void notifyAboutUpdate(LinkUpdate update) {
        if (update == null || update.tgChatIds() == null) {
            return;
        }

        for (Long chatId : update.tgChatIds()) {
            if (chatId == null) {
                continue;
            }
            telegramBot.execute(new SendMessage(chatId, BotMessages.UPDATE_NOTIFICATION));
            log.atInfo()
                    .addKeyValue("chatId", chatId)
                    .addKeyValue("linkId", update.id())
                    .addKeyValue("url", update.url())
                    .log("Sent update notification");
        }
    }
}
