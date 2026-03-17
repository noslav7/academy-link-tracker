package backend.academy.linktracker.scrapper.repository;

import backend.academy.linktracker.scrapper.domain.TrackedLink;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Контракт репозитория хранения чатов и отслеживаемых ссылок.
 */
public interface TrackedLinkRepository {
    /**
     * Регистрирует чат.
     *
     * @param chatId идентификатор чата
     * @return {@code true}, если чат создан
     */
    boolean registerChat(long chatId);

    /**
     * Удаляет чат.
     *
     * @param chatId идентификатор чата
     * @return {@code true}, если чат удален
     */
    boolean deleteChat(long chatId);

    /**
     * Проверяет существование чата.
     *
     * @param chatId идентификатор чата
     * @return {@code true}, если чат есть
     */
    boolean chatExists(long chatId);

    /**
     * Ищет ссылку чата по URL.
     *
     * @param chatId идентификатор чата
     * @param url URL ссылки
     * @return найденная ссылка или пусто
     */
    Optional<TrackedLink> findByChatIdAndUrl(long chatId, String url);

    /**
     * Сохраняет новую отслеживаемую ссылку.
     *
     * @param chatId идентификатор чата
     * @param url URL ссылки
     * @param tags теги
     * @param filters фильтры
     * @param updatedAt время последнего обновления ресурса
     * @return сохраненная ссылка
     */
    TrackedLink save(long chatId, String url, List<String> tags, List<String> filters, java.time.Instant updatedAt);

    /**
     * Удаляет ссылку чата по URL.
     *
     * @param chatId идентификатор чата
     * @param url URL ссылки
     * @return {@code true}, если ссылка удалена
     */
    boolean deleteByChatIdAndUrl(long chatId, String url);

    /**
     * Возвращает все ссылки чата.
     *
     * @param chatId идентификатор чата
     * @return список ссылок чата
     */
    List<TrackedLink> findAllByChatId(long chatId);

    /**
     * Возвращает ссылки чата постранично.
     *
     * @param chatId идентификатор чата
     * @param limit размер страницы
     * @param offset смещение
     * @return страница ссылок
     */
    List<TrackedLink> findAllByChatId(long chatId, int limit, int offset);

    /**
     * Возвращает идентификаторы всех чатов.
     *
     * @return множество chatId
     */
    Set<Long> findAllChatIds();

    /**
     * Возвращает все отслеживаемые ссылки постранично.
     *
     * @param limit размер страницы
     * @param offset смещение
     * @return страница ссылок
     */
    List<TrackedLink> findAllTrackedLinks(int limit, int offset);

    /**
     * Обновляет сохраненную ссылку.
     *
     * @param trackedLink обновленная ссылка
     */
    void updateTrackedLink(TrackedLink trackedLink);

    /**
     * Возвращает теги подписки по URL ссылки.
     *
     * @param chatId идентификатор чата
     * @param url URL ссылки
     * @return список тегов
     */
    List<String> findTagsByChatIdAndUrl(long chatId, String url);

    /**
     * Добавляет тег к существующей подписке.
     *
     * @param chatId идентификатор чата
     * @param url URL ссылки
     * @param tag значение тега
     * @return {@code true}, если тег добавлен
     */
    boolean addTag(long chatId, String url, String tag);

    /**
     * Обновляет значение тега у существующей подписки.
     *
     * @param chatId идентификатор чата
     * @param url URL ссылки
     * @param oldTag текущее значение тега
     * @param newTag новое значение тега
     * @return {@code true}, если тег обновлен
     */
    boolean updateTag(long chatId, String url, String oldTag, String newTag);

    /**
     * Удаляет тег у существующей подписки.
     *
     * @param chatId идентификатор чата
     * @param url URL ссылки
     * @param tag значение тега
     * @return {@code true}, если тег удален
     */
    boolean deleteTag(long chatId, String url, String tag);
}
