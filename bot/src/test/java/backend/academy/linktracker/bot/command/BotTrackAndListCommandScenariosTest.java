package backend.academy.linktracker.bot.command;

import static org.junit.jupiter.api.Assertions.assertEquals;

import backend.academy.linktracker.bot.client.LinkAlreadyTrackedException;
import backend.academy.linktracker.bot.client.LinkNotFoundException;
import backend.academy.linktracker.bot.client.ScrapperClient;
import backend.academy.linktracker.bot.client.dto.LinkResponse;
import backend.academy.linktracker.bot.service.TrackDialogInputParser;
import backend.academy.linktracker.bot.service.TrackDialogService;
import backend.academy.linktracker.bot.service.TrackDialogSubmissionService;
import backend.academy.linktracker.bot.service.TrackSubmissionResultMessageResolver;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
class BotTrackAndListCommandScenariosTest {

    private static final long CHAT_ID = 101L;

    private BotCommandDispatcher dispatcher;
    private InMemoryScrapperClient scrapperClient;

    @BeforeEach
    void setUp() {
        scrapperClient = new InMemoryScrapperClient();
        var trackDialogService = new TrackDialogService(
                new TrackDialogInputParser(),
                new TrackDialogSubmissionService(scrapperClient),
                new TrackSubmissionResultMessageResolver());
        var trackHandler = new TrackCommandHandler(trackDialogService);
        var listHandler = new ListCommandHandler(scrapperClient);
        var cancelHandler = new CancelCommandHandler(trackDialogService);
        BotCommandDispatcher[] dispatcherHolder = new BotCommandDispatcher[1];
        var helpHandler = new HelpCommandHandler(() -> dispatcherHolder[0], new CommandHelpMessageFormatter());
        dispatcherHolder[0] = new BotCommandDispatcher(
                List.of(trackHandler, listHandler, cancelHandler, helpHandler), trackDialogService);
        dispatcher = dispatcherHolder[0];
    }

    @Test
    void givenTrackDialogAndValidLinkWhenUserProvidesTagsAndFiltersThenReturnsSuccessMessage() {
        // Проверяет, что happy-path диалога /track завершается успешным сообщением.
        assertEquals(BotMessages.TRACK_ENTER_LINK, dispatcher.dispatch("/track", CHAT_ID));
        assertEquals(BotMessages.TRACK_ENTER_TAGS, dispatcher.dispatch("https://github.com/user/repo", CHAT_ID));
        assertEquals(BotMessages.TRACK_ENTER_FILTERS, dispatcher.dispatch("backend, java", CHAT_ID));

        String result = dispatcher.dispatch("author=me, label=bug", CHAT_ID);

        assertEquals(BotMessages.TRACK_SUCCESS, result);
    }

    @Test
    void givenTrackDialogAndValidLinkWhenUserProvidesTagsAndFiltersThenSavesExactLinkState() {
        // Проверяет, что happy-path диалога /track сохраняет точные url, теги и фильтры.
        assertEquals(BotMessages.TRACK_ENTER_LINK, dispatcher.dispatch("/track", CHAT_ID));
        assertEquals(BotMessages.TRACK_ENTER_TAGS, dispatcher.dispatch("https://github.com/user/repo", CHAT_ID));
        assertEquals(BotMessages.TRACK_ENTER_FILTERS, dispatcher.dispatch("backend, java", CHAT_ID));
        dispatcher.dispatch("author=me, label=bug", CHAT_ID);

        List<LinkResponse> links = scrapperClient.getLinks(CHAT_ID);
        assertEquals(1, links.size());
        assertEquals("https://github.com/user/repo", links.getFirst().url());
        assertEquals(List.of("backend", "java"), links.getFirst().tags());
        assertEquals(List.of("author=me", "label=bug"), links.getFirst().filters());
    }

    @Test
    void givenTrackDialogWhenUserProvidesInvalidLinkThenBotReturnsValidationMessage() {
        // Проверяет валидацию URL в диалоге /track.
        assertEquals(BotMessages.TRACK_ENTER_LINK, dispatcher.dispatch("/track", CHAT_ID));

        String result = dispatcher.dispatch("tbank://github.com/user/repo", CHAT_ID);

        assertEquals(BotMessages.TRACK_INVALID_LINK, result);
    }

    @Test
    void givenAlreadyTrackedLinkWhenTrackRequestedAgainThenBotReturnsDuplicateMessage() {
        // Проверяет защиту от повторного добавления уже отслеживаемой ссылки.
        trackLink(CHAT_ID, "https://github.com/user/repo", "work", "author=me");

        assertEquals(BotMessages.TRACK_ENTER_LINK, dispatcher.dispatch("/track", CHAT_ID));
        assertEquals(BotMessages.TRACK_ENTER_TAGS, dispatcher.dispatch("https://github.com/user/repo", CHAT_ID));
        assertEquals(BotMessages.TRACK_ENTER_FILTERS, dispatcher.dispatch("work", CHAT_ID));

        String result = dispatcher.dispatch("-", CHAT_ID);

        assertEquals(BotMessages.TRACK_DUPLICATE, result);
    }

    @Test
    void givenUserHasActiveSubscriptionsWhenListRequestedThenBotReturnsTrackedLinks() {
        // Проверяет, что /list возвращает полный эталонный список отслеживаемых ссылок.
        scrapperClient.storeLink(CHAT_ID, "https://github.com/user/repo-a", List.of("work"));
        scrapperClient.storeLink(CHAT_ID, "https://github.com/user/repo-b", List.of("study"));

        String result = dispatcher.dispatch("/list", CHAT_ID);

        String expected = """
                Отслеживаемые ссылки:
                - https://github.com/user/repo-a [work]
                - https://github.com/user/repo-b [study]
                """;
        assertEquals(expected, result);
    }

    @Test
    void givenUserHasNoActiveSubscriptionsWhenListRequestedThenBotReturnsEmptyMessage() {
        // Проверяет, что /list возвращает сообщение об отсутствии ссылок для пустого списка.
        String result = dispatcher.dispatch("/list", CHAT_ID);

        assertEquals(BotMessages.LIST_EMPTY, result);
    }

    @Test
    void givenListByTagWhenUserHasTrackedLinksThenBotReturnsOnlyLinksWithRequestedTag() {
        // Проверяет фильтрацию /list по тегу с полным совпадением эталонного ответа.
        scrapperClient.storeLink(CHAT_ID, "https://github.com/user/repo-a", List.of("work"));
        scrapperClient.storeLink(CHAT_ID, "https://github.com/user/repo-b", List.of("study"));

        String result = dispatcher.dispatch("/list work", CHAT_ID);

        String expected = """
                Отслеживаемые ссылки:
                - https://github.com/user/repo-a [work]
                """;
        assertEquals(expected, result);
    }

    private void trackLink(long chatId, String url, String tags, String filters) {
        dispatcher.dispatch("/track", chatId);
        dispatcher.dispatch(url, chatId);
        dispatcher.dispatch(tags, chatId);
        dispatcher.dispatch(filters, chatId);
    }

    private static final class InMemoryScrapperClient implements ScrapperClient {

        private final Map<Long, Map<String, LinkResponse>> linksByChatId = new LinkedHashMap<>();
        private long idSequence = 1L;

        @Override
        public void registerChat(long chatId) {}

        @Override
        public LinkResponse addLink(long chatId, String link, List<String> tags, List<String> filters) {
            Map<String, LinkResponse> links = linksByChatId.computeIfAbsent(chatId, key -> new LinkedHashMap<>());
            if (links.containsKey(link)) {
                throw new LinkAlreadyTrackedException();
            }
            LinkResponse response = new LinkResponse(idSequence++, link, List.copyOf(tags), List.copyOf(filters));
            links.put(link, response);
            return response;
        }

        @Override
        public LinkResponse removeLink(long chatId, String link) {
            Map<String, LinkResponse> links = linksByChatId.get(chatId);
            if (links == null || !links.containsKey(link)) {
                throw new LinkNotFoundException();
            }
            return links.remove(link);
        }

        @Override
        public List<LinkResponse> getLinks(long chatId) {
            Map<String, LinkResponse> links = linksByChatId.get(chatId);
            if (links == null) {
                return List.of();
            }
            return new ArrayList<>(links.values());
        }

        void storeLink(long chatId, String url, List<String> tags) {
            addLink(chatId, url, tags, List.of());
        }
    }
}
