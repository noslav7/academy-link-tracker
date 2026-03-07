package backend.academy.linktracker.bot.command;

import backend.academy.linktracker.bot.client.ScrapperClient;
import backend.academy.linktracker.bot.repository.UserRepository;
import org.springframework.stereotype.Component;

/**
 * Обработчик команды {@code /start}.
 */
@Component
public class StartCommandHandler extends AbstractBotCommandHandler {

    /**
     * Репозиторий зарегистрированных пользователей.
     */
    private final UserRepository userRepository;

    /**
     * Клиент взаимодействия с scrapper для регистрации чата.
     */
    private final ScrapperClient scrapperClient;

    /**
     * Создает обработчик команды {@code /start}.
     *
     * @param userRepository репозиторий зарегистрированных пользователей
     * @param scrapperClient клиент взаимодействия со scrapper
     */
    public StartCommandHandler(UserRepository userRepository, ScrapperClient scrapperClient) {
        super("/start", "начало работы");
        this.userRepository = userRepository;
        this.scrapperClient = scrapperClient;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String handle(long chatId, String messageText) {
        boolean isFirstTime = userRepository.registerIfAbsent(chatId);
        scrapperClient.registerChat(chatId);
        return isFirstTime ? BotMessages.START_FIRST_TIME : BotMessages.START_ALREADY_REGISTERED;
    }
}
