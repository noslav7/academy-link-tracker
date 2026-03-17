package backend.academy.linktracker.bot.command;

import backend.academy.linktracker.bot.client.LinkNotFoundException;
import backend.academy.linktracker.bot.client.ScrapperClient;
import backend.academy.linktracker.bot.client.ScrapperClientException;
import backend.academy.linktracker.bot.service.TrackDialogInputParser;
import org.springframework.stereotype.Component;

/**
 * Обработчик команды {@code /untrack}.
 */
@Component
public class UntrackCommandHandler extends AbstractBotCommandHandler {

    /**
     * Клиент удаления подписок в scrapper.
     */
    private final ScrapperClient scrapperClient;
    /**
     * Компонент валидации пользовательского URL-ввода.
     */
    private final TrackDialogInputParser inputParser;

    /**
     * Создает обработчик команды {@code /untrack}.
     *
     * @param scrapperClient клиент scrapper-сервиса
     * @param inputParser компонент валидации URL
     */
    public UntrackCommandHandler(ScrapperClient scrapperClient, TrackDialogInputParser inputParser) {
        super("/untrack", "прекратить отслеживание ссылки");
        this.scrapperClient = scrapperClient;
        this.inputParser = inputParser;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String handle(long chatId, String messageText) {
        String[] tokens = messageText.trim().split("\\s+", 2);
        if (tokens.length < 2 || !inputParser.isValidUrl(tokens[1])) {
            return BotMessages.UNTRACK_USAGE;
        }

        try {
            scrapperClient.removeLink(chatId, tokens[1].trim());
            return BotMessages.UNTRACK_SUCCESS;
        } catch (LinkNotFoundException ex) {
            return BotMessages.UNTRACK_NOT_FOUND;
        } catch (ScrapperClientException ex) {
            return BotMessages.UNTRACK_FAILED;
        }
    }
}
