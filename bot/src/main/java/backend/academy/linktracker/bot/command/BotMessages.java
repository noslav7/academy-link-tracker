package backend.academy.linktracker.bot.command;

public final class BotMessages {

    public static final String UNKNOWN_COMMAND =
            "Неизвестная команда. Воспользуйтесь /help, чтобы посмотреть список доступных команд.";

    public static final String START_FIRST_TIME =
            "Добро пожаловать! Используйте /help, чтобы посмотреть доступные команды.";

    public static final String START_ALREADY_REGISTERED =
            "Вы уже начали работу. Используйте /help, чтобы посмотреть доступные команды.";

    public static final String HELP = """
            Доступные команды:
            /help - список доступных команд
            /start - начало работы
            """;

    private BotMessages() {}
}
