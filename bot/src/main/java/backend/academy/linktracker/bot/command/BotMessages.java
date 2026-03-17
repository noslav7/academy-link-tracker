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
     * Запрос ссылки на первом шаге сценария {@code /track}.
     */
    public static final String TRACK_ENTER_LINK = "Отправьте ссылку, которую нужно отслеживать.";

    /**
     * Запрос тегов на втором шаге сценария {@code /track}.
     */
    public static final String TRACK_ENTER_TAGS =
            "Теперь отправьте теги через запятую (например: работа, баг) или '-' если без тегов.";

    /**
     * Запрос фильтров на третьем шаге сценария {@code /track}.
     */
    public static final String TRACK_ENTER_FILTERS =
            "Теперь отправьте фильтры через запятую (например: author=me, label=bug) или '-' если без фильтров.";

    /**
     * Ответ после отмены активного сценария добавления ссылки.
     */
    public static final String TRACK_CANCELLED = "Процесс отслеживания отменён.";

    /**
     * Ответ при успешном добавлении ссылки в отслеживание.
     */
    public static final String TRACK_SUCCESS = "Ссылка добавлена в отслеживание.";

    /**
     * Ответ при попытке повторно добавить уже отслеживаемую ссылку.
     */
    public static final String TRACK_DUPLICATE = "Ссылка уже отслеживается";

    /**
     * Ответ при невалидной ссылке в сценарии {@code /track}.
     */
    public static final String TRACK_INVALID_LINK = "Некорректная ссылка. Отправьте корректный URL.";

    /**
     * Ответ при ошибке добавления ссылки в scrapper.
     */
    public static final String TRACK_FAILED = "Не удалось добавить ссылку. Попробуйте позже.";

    /**
     * Подсказка корректного формата команды {@code /untrack}.
     */
    public static final String UNTRACK_USAGE = "Укажите ссылку: /untrack <url>";

    /**
     * Ответ при успешном удалении ссылки из отслеживания.
     */
    public static final String UNTRACK_SUCCESS = "Ссылка удалена из отслеживания.";

    /**
     * Ответ при попытке удалить несуществующую подписку.
     */
    public static final String UNTRACK_NOT_FOUND = "Ссылка не найдена в отслеживании.";

    /**
     * Ответ при ошибке удаления ссылки в scrapper.
     */
    public static final String UNTRACK_FAILED = "Не удалось удалить ссылку. Попробуйте позже.";

    /**
     * Ответ при пустом списке подписок.
     */
    public static final String LIST_EMPTY = "нет ссылок";

    /**
     * Ответ при ошибке получения списка подписок.
     */
    public static final String LIST_FAILED = "Не удалось получить список ссылок. Попробуйте позже.";

    /**
     * Ответ при попытке отмены, когда активного сценария нет.
     */
    public static final String CANCEL_NO_ACTIVE = "Нет активного процесса отслеживания.";

    /**
     * Базовый текст уведомления об обновлении отслеживаемой ссылки.
     */
    public static final String UPDATE_NOTIFICATION = "Обнаружено обновление по отслеживаемой ссылке.";

    /**
     * Скрывает создание утилитного класса.
     */
    private BotMessages() {}
}
