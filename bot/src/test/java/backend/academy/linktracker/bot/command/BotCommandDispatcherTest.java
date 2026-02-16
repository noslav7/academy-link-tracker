package backend.academy.linktracker.bot.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import backend.academy.linktracker.bot.repository.InMemoryUserRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BotCommandDispatcherTest {

    private BotCommandDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        var startHandler = new StartCommandHandler(new InMemoryUserRepository());
        var helpHandler = new HelpCommandHandler();
        dispatcher = new BotCommandDispatcher(List.of(startHandler, helpHandler));
    }

    @Test
    void startCommandReturnsWelcomeMessage() {
        String response = dispatcher.dispatch("/start", 42L);

        assertEquals(BotMessages.START_FIRST_TIME, response);
    }

    @Test
    void helpCommandReturnsSupportedCommandsList() {
        String response = dispatcher.dispatch("/help", 42L);

        assertTrue(response.contains("/start"));
        assertTrue(response.contains("/help"));
    }

    @Test
    void unknownCommandReturnsErrorMessage() {
        String response = dispatcher.dispatch("/unknown", 42L);

        assertEquals(BotMessages.UNKNOWN_COMMAND, response);
    }
}
