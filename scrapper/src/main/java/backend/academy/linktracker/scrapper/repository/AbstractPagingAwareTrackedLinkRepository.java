package backend.academy.linktracker.scrapper.repository;

/**
 * Базовый класс репозиториев с общей валидацией параметров пагинации.
 */
public abstract class AbstractPagingAwareTrackedLinkRepository {

    /**
     * Проверяет корректность параметров пагинации.
     *
     * @param limit размер страницы
     * @param offset смещение
     * @return {@code true}, если параметры невалидны
     */
    protected final boolean hasInvalidPaging(int limit, int offset) {
        return limit <= 0 || offset < 0;
    }
}
