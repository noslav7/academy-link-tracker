package backend.academy.linktracker.bot.command;

/**
 * Контракт обработчика Telegram-команды.
 */
public interface BotCommandHandler {

    /**
     * Возвращает каноническое имя команды.
     *
     * @return имя команды в формате Telegram, например {@code /start}
     */
    String command();

    /**
     * Возвращает короткое описание команды для меню бота.
     *
     * @return текст описания команды
     */
    String description();

    /**
     * Обрабатывает команду для указанного чата.
     *
     * @param chatId идентификатор чата
     * @param messageText исходный текст сообщения
     * @return текст ответа пользователю
     */
    String handle(long chatId, String messageText);
}
