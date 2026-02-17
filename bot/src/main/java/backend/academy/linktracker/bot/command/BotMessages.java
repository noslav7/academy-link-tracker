package backend.academy.linktracker.bot.command;

/**
 * Константы текстовых сообщений бота.
 */
public final class BotMessages {

    /**
     * Ответ на неизвестную или неподдерживаемую команду.
     */
    public static final String UNKNOWN_COMMAND =
            "Неизвестная команда. Воспользуйтесь /help, чтобы посмотреть список доступных команд.";

    /**
     * Ответ при первом запуске команды {@code /start}.
     */
    public static final String START_FIRST_TIME =
            "Добро пожаловать! Используйте /help, чтобы посмотреть доступные команды.";

    /**
     * Ответ при повторном запуске команды {@code /start}.
     */
    public static final String START_ALREADY_REGISTERED =
            "Вы уже начали работу. Используйте /help, чтобы посмотреть доступные команды.";

    /**
     * Скрывает создание утилитного класса.
     */
    private BotMessages() {}
}
