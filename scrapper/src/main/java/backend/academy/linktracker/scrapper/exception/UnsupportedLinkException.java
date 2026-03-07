package backend.academy.linktracker.scrapper.exception;

/**
 * Ошибка неподдерживаемого формата ссылки.
 */
public class UnsupportedLinkException extends RuntimeException {
    /**
     * Создает исключение неподдерживаемой ссылки.
     *
     * @param link URL ссылки
     */
    public UnsupportedLinkException(String link) {
        super("Unsupported link: " + link);
    }
}
