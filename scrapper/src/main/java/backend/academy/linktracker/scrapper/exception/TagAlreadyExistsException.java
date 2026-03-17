package backend.academy.linktracker.scrapper.exception;

/**
 * Ошибка добавления дублирующего тега к подписке.
 */
public class TagAlreadyExistsException extends RuntimeException {
    /**
     * Создает исключение существующего тега.
     *
     * @param tag значение тега
     */
    public TagAlreadyExistsException(String tag) {
        super("Tag already exists: " + tag);
    }
}
