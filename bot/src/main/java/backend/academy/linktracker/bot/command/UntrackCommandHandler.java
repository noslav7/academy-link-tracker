package backend.academy.linktracker.bot.command;

import backend.academy.linktracker.bot.client.LinkNotFoundException;
import backend.academy.linktracker.bot.client.ScrapperClient;
import backend.academy.linktracker.bot.client.ScrapperClientException;
import java.net.URI;
import org.springframework.stereotype.Component;

@Component
public class UntrackCommandHandler extends AbstractBotCommandHandler {

    private final ScrapperClient scrapperClient;

    public UntrackCommandHandler(ScrapperClient scrapperClient) {
        super("/untrack", "прекратить отслеживание ссылки");
        this.scrapperClient = scrapperClient;
    }

    @Override
    public String handle(long chatId, String messageText) {
        String[] tokens = messageText.trim().split("\\s+", 2);
        if (tokens.length < 2 || !isValidUrl(tokens[1])) {
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

    private static boolean isValidUrl(String value) {
        try {
            URI uri = URI.create(value.trim());
            return uri.getScheme() != null && uri.getHost() != null;
        } catch (RuntimeException ex) {
            return false;
        }
    }
}
