package backend.academy.linktracker.bot.client;

import backend.academy.linktracker.bot.client.dto.LinkResponse;
import java.util.List;

public interface ScrapperClient {
    void registerChat(long chatId);

    LinkResponse addLink(long chatId, String link, List<String> tags, List<String> filters);

    LinkResponse removeLink(long chatId, String link);

    List<LinkResponse> getLinks(long chatId);
}
