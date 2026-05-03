package backend.academy.linktracker.scrapper.service.cache;

/**
 * Генератор ключей кэша списка ссылок.
 */
public final class LinksListCacheKeys {

    public static final String CACHE_NAME = "links-list";
    private static final String CACHE_KEY_PREFIX = "{cached-links}:" + CACHE_NAME + "::";

    private LinksListCacheKeys() {}

    /**
     * Формирует ключ кэша по идентификатору чата.
     *
     * @param chatId идентификатор чата
     * @return строковый ключ кэша
     * @implNote Нужно, чтобы генерация ключей была единообразной во всех реализациях кэша.
     */
    public static String byChatId(long chatId) {
        if (chatId <= 0) {
            throw new IllegalArgumentException("chatId must be positive");
        }
        return CACHE_KEY_PREFIX + chatId;
    }
}
