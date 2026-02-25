package backend.academy.linktracker.bot.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import backend.academy.linktracker.bot.client.ScrapperClient;
import backend.academy.linktracker.bot.client.dto.LinkResponse;
import backend.academy.linktracker.bot.repository.InMemoryUserRepository;
import backend.academy.linktracker.bot.service.TrackDialogService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

class BotCommandDispatcherTest {

    private BotCommandDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        ScrapperClient scrapperClient = new StubScrapperClient();
        var trackDialogService = new TrackDialogService(scrapperClient);
        var startHandler = new StartCommandHandler(new InMemoryUserRepository(), scrapperClient);
        var trackHandler = new TrackCommandHandler(trackDialogService);
        var cancelHandler = new CancelCommandHandler(trackDialogService);
        BotCommandDispatcher[] dispatcherHolder = new BotCommandDispatcher[1];
        var helpHandler = new HelpCommandHandler(() -> dispatcherHolder[0], new CommandHelpMessageFormatter());
        dispatcherHolder[0] =
                new BotCommandDispatcher(List.of(startHandler, helpHandler, trackHandler, cancelHandler), trackDialogService);
        dispatcher = dispatcherHolder[0];
    }

    @Test
    @Tag("sanity")
    void givenNewUserChatIdWhenDispatchStartCommandThenReturnsFirstTimeWelcomeMessage() {
        // Проверяет базовый smoke-сценарий старта для нового пользователя.
        String response = dispatcher.dispatch("/start", 42L);

        assertEquals(BotMessages.START_FIRST_TIME, response);
    }

    @Test
    void givenAlreadyRegisteredUserWhenDispatchStartCommandThenReturnsAlreadyRegisteredMessage() {
        // Проверяет повторный вызов /start для уже зарегистрированного пользователя.
        dispatcher.dispatch("/start", 42L);

        String response = dispatcher.dispatch("/start", 42L);

        assertEquals(BotMessages.START_ALREADY_REGISTERED, response);
    }

    @Test
    void givenRegisteredHandlersWhenDispatchHelpCommandThenReturnsFullCommandsHelpMessage() {
        // Проверяет, что /help возвращает полный эталонный список команд.
        String response = dispatcher.dispatch("/help", 42L);

        assertTrue(response.contains("/help - список доступных команд"));
        assertTrue(response.contains("/start - начало работы"));
        assertTrue(response.contains("/track - начать отслеживание ссылки"));
    }

    @Test
    void givenUnsupportedCommandWhenDispatchThenReturnsUnknownCommandMessage() {
        // Проверяет ответ на неподдерживаемую команду.
        String response = dispatcher.dispatch("/unknown", 42L);

        assertEquals(BotMessages.UNKNOWN_COMMAND, response);
    }

    @Test
    void givenNullMessageWhenDispatchThenReturnsUnknownCommandMessage() {
        // Проверяет обработку null-сообщения как ошибочного ввода.
        String response = dispatcher.dispatch(null, 42L);

        assertEquals(BotMessages.UNKNOWN_COMMAND, response);
    }

    @Test
    void givenBlankMessageWhenDispatchThenReturnsUnknownCommandMessage() {
        // Проверяет обработку пустого/пробельного сообщения.
        String response = dispatcher.dispatch("   ", 42L);

        assertEquals(BotMessages.UNKNOWN_COMMAND, response);
    }

    @Test
    void givenCommandWithMentionAndArgumentsWhenDispatchThenRoutesByCommandToken() {
        // Проверяет нормализацию команды с mention и аргументами.
        String response = dispatcher.dispatch("/start@TestBot extra", 42L);

        assertEquals(BotMessages.START_FIRST_TIME, response);
    }

    @Test
    void givenMessageLongerThanTelegramLimitWhenDispatchThenReturnsUnknownCommandMessage() {
        // Проверяет граничный случай сообщения длиннее допустимого лимита.
        String response = dispatcher.dispatch("/" + "a".repeat(4097), 42L);

        assertEquals(BotMessages.UNKNOWN_COMMAND, response);
    }

    private static final class StubScrapperClient implements ScrapperClient {

        @Override
        public void registerChat(long chatId) {}

        @Override
        public LinkResponse addLink(long chatId, String link, List<String> tags) {
            return new LinkResponse(1L, link, tags, List.of());
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
