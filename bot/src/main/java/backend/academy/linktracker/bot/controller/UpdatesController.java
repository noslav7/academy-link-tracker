package backend.academy.linktracker.bot.controller;

import backend.academy.linktracker.bot.client.dto.LinkUpdate;
import backend.academy.linktracker.bot.command.BotMessages;
import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.request.SendMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class UpdatesController {

    private static final Logger log = LoggerFactory.getLogger(UpdatesController.class);
    private final TelegramBot telegramBot;

    public UpdatesController(TelegramBot telegramBot) {
        this.telegramBot = telegramBot;
    }

    @PostMapping("/updates")
    public ResponseEntity<Void> processUpdate(@RequestBody LinkUpdate update) {
        if (update == null || update.tgChatIds() == null) {
            return ResponseEntity.ok().build();
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
        return ResponseEntity.ok().build();
    }
}
