package backend.academy.linktracker.scrapper.exception;

/**
 * Ошибка удаления несуществующей отслеживаемой ссылки.
 */
public class LinkNotFoundException extends RuntimeException {
    /**
     * Создает исключение отсутствующей ссылки.
     *
     * @param link URL ссылки
     */
    public LinkNotFoundException(String link) {
        super("Link not found: " + link);
    }
}
