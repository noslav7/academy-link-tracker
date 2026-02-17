package backend.academy.linktracker.bot.command;

import backend.academy.linktracker.bot.repository.UserRepository;
import org.springframework.stereotype.Component;

@Component
/**
 * Обработчик команды {@code /start}.
 */
public class StartCommandHandler extends AbstractBotCommandHandler {

    /**
     * Репозиторий зарегистрированных пользователей.
     */
    private final UserRepository userRepository;

    /**
     * Создает обработчик команды {@code /start}.
     *
     * @param userRepository репозиторий зарегистрированных пользователей
     */
    public StartCommandHandler(UserRepository userRepository) {
        super("/start", "начало работы");
        this.userRepository = userRepository;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String handle(long chatId) {
        boolean isFirstTime = userRepository.registerIfAbsent(chatId);
        return isFirstTime ? BotMessages.START_FIRST_TIME : BotMessages.START_ALREADY_REGISTERED;
    }
}
