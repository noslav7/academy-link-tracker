package backend.academy.linktracker.scrapper.exception;

/**
 * Ошибка попытки повторного добавления уже отслеживаемой ссылки.
 */
public class LinkAlreadyTrackedException extends RuntimeException {
    /**
     * Создает исключение повторного добавления ссылки.
     *
     * @param link URL ссылки
     */
    public LinkAlreadyTrackedException(String link) {
        super("Link already tracked: " + link);
    }
}
