package backend.academy.linktracker.scrapper.exception;

/**
 * Ошибка обращения к отсутствующему тегу подписки.
 */
public class TagNotFoundException extends RuntimeException {
    /**
     * Создает исключение отсутствующего тега.
     *
     * @param tag значение тега
     */
    public TagNotFoundException(String tag) {
        super("Tag not found: " + tag);
    }
}
