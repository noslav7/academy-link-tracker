package backend.academy.linktracker.bot.command;

import backend.academy.linktracker.bot.client.ScrapperClient;
import backend.academy.linktracker.bot.client.ScrapperClientException;
import backend.academy.linktracker.bot.client.dto.LinkResponse;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ListCommandHandler extends AbstractBotCommandHandler {

    private final ScrapperClient scrapperClient;

    public ListCommandHandler(ScrapperClient scrapperClient) {
        super("/list", "показать отслеживаемые ссылки");
        this.scrapperClient = scrapperClient;
    }

    @Override
    public String handle(long chatId, String messageText) {
        try {
            String optionalTag = extractOptionalTag(messageText);
            List<LinkResponse> links = scrapperClient.getLinks(chatId).stream()
                    .filter(link -> optionalTag == null || (link.tags() != null && link.tags().contains(optionalTag)))
                    .toList();
            if (links.isEmpty()) {
                return BotMessages.LIST_EMPTY;
            }

            StringBuilder builder = new StringBuilder("Отслеживаемые ссылки:\n");
            for (LinkResponse link : links) {
                builder.append("- ").append(link.url());
                if (link.tags() != null && !link.tags().isEmpty()) {
                    builder.append(" [").append(String.join(", ", link.tags())).append(']');
                }
                builder.append('\n');
            }
            return builder.toString();
        } catch (ScrapperClientException ex) {
            return BotMessages.LIST_FAILED;
        }
    }

    private static String extractOptionalTag(String messageText) {
        String[] tokens = messageText.trim().split("\\s+", 3);
        if (tokens.length < 2 || tokens[1].isBlank()) {
            return null;
        }
        return tokens[1].trim();
    }
}
