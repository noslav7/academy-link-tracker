package backend.academy.linktracker.bot.command;

import static org.junit.jupiter.api.Assertions.assertEquals;

import backend.academy.linktracker.bot.client.ScrapperClient;
import backend.academy.linktracker.bot.client.dto.LinkResponse;
import backend.academy.linktracker.bot.repository.InMemoryUserRepository;
import backend.academy.linktracker.bot.service.TrackDialogInputParser;
import backend.academy.linktracker.bot.service.TrackDialogService;
import backend.academy.linktracker.bot.service.TrackDialogSubmissionService;
import backend.academy.linktracker.bot.service.TrackSubmissionResultMessageResolver;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

class StartHelpCommandsSeparateTest {

    private BotCommandDispatcher dispatcher;
    private CountingScrapperClient scrapperClient;

    @BeforeEach
    void setUp() {
        scrapperClient = new CountingScrapperClient();
        var trackDialogService = new TrackDialogService(
                new TrackDialogInputParser(),
                new TrackDialogSubmissionService(scrapperClient),
                new TrackSubmissionResultMessageResolver());
        var startHandler = new StartCommandHandler(new InMemoryUserRepository(), scrapperClient);
        BotCommandDispatcher[] dispatcherHolder = new BotCommandDispatcher[1];
        var helpHandler = new HelpCommandHandler(() -> dispatcherHolder[0], new CommandHelpMessageFormatter());
        dispatcherHolder[0] = new BotCommandDispatcher(List.of(startHandler, helpHandler), trackDialogService);
        dispatcher = dispatcherHolder[0];
    }

    @Test
    @Tag("sanity")
    void givenNewChatWhenStartCommandDispatchedThenReturnsFirstTimeMessage() {
        // Проверяет smoke-сценарий: первичный /start возвращает welcome-текст.
        String response = dispatcher.dispatch("/start", 911L);

        assertEquals(BotMessages.START_FIRST_TIME, response);
    }

    @Test
    void givenExistingChatWhenStartCommandDispatchedThenReturnsAlreadyRegisteredMessage() {
        // Проверяет повторный /start для уже зарегистрированного пользователя.
        dispatcher.dispatch("/start", 911L);

        String response = dispatcher.dispatch("/start", 911L);

        assertEquals(BotMessages.START_ALREADY_REGISTERED, response);
    }

    @Test
    void givenStartCommandWhenDispatchedTwiceThenScrapperRegisterIsCalledTwice() {
        // Проверяет, что /start вызывает регистрацию чата в scrapper на каждый вызов.
        dispatcher.dispatch("/start", 911L);
        dispatcher.dispatch("/start", 911L);

        assertEquals(2, scrapperClient.registerCalls);
    }

    @Test
    void givenHelpCommandWhenDispatchedThenResponseContainsStartAndHelpDescriptions() {
        // Проверяет отдельный сценарий /help: текст справки содержит команды /start и /help.
        String response = dispatcher.dispatch("/help", 912L);

        assertEquals("""
                Доступные команды:
                /help - список доступных команд
                /start - начало работы
                """, response);
    }

    private static final class CountingScrapperClient implements ScrapperClient {
        private int registerCalls;

        @Override
        public void registerChat(long chatId) {
            registerCalls++;
        }

        @Override
        public LinkResponse addLink(long chatId, String link, List<String> tags, List<String> filters) {
            return new LinkResponse(1L, link, tags, filters);
        }

        @Override
        public LinkResponse removeLink(long chatId, String link) {
            return new LinkResponse(1L, link, List.of(), List.of());
        }

        @Override
        public List<LinkResponse> getLinks(long chatId) {
            return List.of();
        }
    }
}
