package backend.academy.linktracker.bot.client;

/**
 * Ошибка попытки повторного добавления уже отслеживаемой ссылки.
 */
public class LinkAlreadyTrackedException extends ScrapperClientException {
    private static final long serialVersionUID = 1L;

    /**
     * Создает исключение повторного добавления ссылки.
     */
    public LinkAlreadyTrackedException() {
        super("Link already tracked");
    }
}
