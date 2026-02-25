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

    public static final String TRACK_ENTER_LINK = "Отправьте ссылку, которую нужно отслеживать.";

    public static final String TRACK_ENTER_TAGS =
            "Теперь отправьте теги через запятую (например: работа, баг) или '-' если без тегов.";

    public static final String TRACK_CANCELLED = "Процесс отслеживания отменён.";

    public static final String TRACK_SUCCESS = "Ссылка добавлена в отслеживание.";

    public static final String TRACK_DUPLICATE = "Ссылка уже отслеживается";

    public static final String TRACK_INVALID_LINK = "Некорректная ссылка. Отправьте корректный URL.";

    public static final String TRACK_FAILED = "Не удалось добавить ссылку. Попробуйте позже.";

    public static final String UNTRACK_USAGE = "Укажите ссылку: /untrack <url>";

    public static final String UNTRACK_SUCCESS = "Ссылка удалена из отслеживания.";

    public static final String UNTRACK_NOT_FOUND = "Ссылка не найдена в отслеживании.";

    public static final String UNTRACK_FAILED = "Не удалось удалить ссылку. Попробуйте позже.";

    public static final String LIST_EMPTY = "Список отслеживаемых ссылок пуст.";

    public static final String LIST_FAILED = "Не удалось получить список ссылок. Попробуйте позже.";

    public static final String CANCEL_NO_ACTIVE = "Нет активного процесса отслеживания.";

    public static final String UPDATE_NOTIFICATION = "Обнаружено обновление по отслеживаемой ссылке.";

    /**
     * Скрывает создание утилитного класса.
     */
    private BotMessages() {}
}
