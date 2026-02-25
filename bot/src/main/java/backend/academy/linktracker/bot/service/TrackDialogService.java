package backend.academy.linktracker.bot.service;

import backend.academy.linktracker.bot.client.LinkAlreadyTrackedException;
import backend.academy.linktracker.bot.client.ScrapperClient;
import backend.academy.linktracker.bot.client.ScrapperClientException;
import backend.academy.linktracker.bot.command.BotMessages;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class TrackDialogService {

    private final Map<Long, TrackState> activeDialogs = new ConcurrentHashMap<>();
    private final ScrapperClient scrapperClient;

    public TrackDialogService(ScrapperClient scrapperClient) {
        this.scrapperClient = scrapperClient;
    }

    public String start(long chatId) {
        activeDialogs.put(chatId, new TrackState());
        return BotMessages.TRACK_ENTER_LINK;
    }

    public boolean isActive(long chatId) {
        return activeDialogs.containsKey(chatId);
    }

    public String cancel(long chatId) {
        return activeDialogs.remove(chatId) == null ? BotMessages.CANCEL_NO_ACTIVE : BotMessages.TRACK_CANCELLED;
    }

    public String consume(long chatId, String messageText) {
        TrackState state = activeDialogs.get(chatId);
        if (state == null) {
            return BotMessages.CANCEL_NO_ACTIVE;
        }

        if (state.link == null) {
            if (!isValidUrl(messageText)) {
                return BotMessages.TRACK_INVALID_LINK;
            }
            state.link = messageText.trim();
            return BotMessages.TRACK_ENTER_TAGS;
        }

        List<String> tags = parseTags(messageText);
        try {
            scrapperClient.addLink(chatId, state.link, tags);
            activeDialogs.remove(chatId);
            return BotMessages.TRACK_SUCCESS;
        } catch (LinkAlreadyTrackedException ex) {
            activeDialogs.remove(chatId);
            return BotMessages.TRACK_DUPLICATE;
        } catch (ScrapperClientException ex) {
            activeDialogs.remove(chatId);
            return BotMessages.TRACK_FAILED;
        }
    }

    private static List<String> parseTags(String input) {
        if (input == null) {
            return List.of();
        }
        String normalized = input.trim();
        if (normalized.isEmpty() || "-".equals(normalized)) {
            return List.of();
        }
        return List.of(normalized.split(",")).stream()
                .map(String::trim)
                .filter(tag -> !tag.isBlank())
                .toList();
    }

    private static boolean isValidUrl(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        try {
            URI uri = URI.create(value.trim());
            String scheme = uri.getScheme();
            if (scheme == null || uri.getHost() == null) {
                return false;
            }
            return "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private static final class TrackState {
        private String link;
    }
}
