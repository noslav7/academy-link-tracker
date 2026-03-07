package backend.academy.linktracker.bot.telegram;

import backend.academy.linktracker.bot.command.BotCommandDispatcher;
import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.request.SendMessage;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Обрабатывает входящие Telegram-обновления и отправляет ответы пользователю.
 */
@Component
@RequiredArgsConstructor
public class TelegramUpdateProcessor {

    /**
     * Логгер класса.
     */
    private static final Logger log = LoggerFactory.getLogger(TelegramUpdateProcessor.class);

    /**
     * Клиент Telegram API.
     */
    private final TelegramBot telegramBot;
    /**
     * Диспетчер для маршрутизации команд к обработчикам.
     */
    private final BotCommandDispatcher commandDispatcher;

    /**
     * Извлекает команду из входящего обновления, обрабатывает ее и отправляет ответ.
     *
     * @param update входящее обновление Telegram
     */
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
