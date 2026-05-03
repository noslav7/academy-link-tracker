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

    /**
     * Создает исключение повторного добавления ссылки с исходной причиной.
     *
     * @param link URL ссылки
     * @param cause исходная ошибка хранения
     */
    public LinkAlreadyTrackedException(String link, Throwable cause) {
        super("Link already tracked: " + link, cause);
    }
}
