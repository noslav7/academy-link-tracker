package backend.academy.linktracker.bot.client;

/**
 * Ошибка удаления ссылки, отсутствующей в отслеживании.
 */
public class LinkNotFoundException extends ScrapperClientException {
    /**
     * Создает исключение отсутствующей ссылки.
     */
    public LinkNotFoundException() {
        super("Link not found");
    }
}
