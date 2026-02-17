package backend.academy.linktracker.bot.command;

/**
 * Базовый обработчик Telegram-команды с общим шаблоном имени и описания.
 */
public abstract class AbstractBotCommandHandler implements BotCommandHandler {

    /**
     * Каноническое имя команды в формате Telegram.
     */
    private final String command;
    /**
     * Краткое описание команды для меню Telegram.
     */
    private final String description;

    /**
     * Создает базовый обработчик команды.
     *
     * @param command каноническое имя команды
     * @param description краткое описание команды
     */
    protected AbstractBotCommandHandler(String command, String description) {
        this.command = command;
        this.description = description;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final String command() {
        return command;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final String description() {
        return description;
    }
}
