package backend.academy.linktracker.bot.telegram;

import backend.academy.linktracker.bot.command.BotCommandDispatcher;
import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.request.SendMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class TelegramUpdateProcessor {

    private final TelegramBot telegramBot;
    private final BotCommandDispatcher commandDispatcher;

    public void process(Update update) {
        if (update == null || update.message() == null) {
            return;
        }

        Message message = update.message();
        if (message.chat() == null) {
            return;
        }

        long chatId = message.chat().id();
        String messageText = message.text();

        String responseText = commandDispatcher.dispatch(messageText, chatId);
        telegramBot.execute(new SendMessage(chatId, responseText));

        log.atInfo()
                .addKeyValue("chatId", chatId)
                .addKeyValue("messageText", messageText)
                .addKeyValue("responseText", responseText)
                .log("Processed telegram update");
    }
}
