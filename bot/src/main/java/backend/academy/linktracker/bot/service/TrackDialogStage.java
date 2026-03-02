package backend.academy.linktracker.bot.service;

/**
 * Стадии диалога команды {@code /track}.
 */
enum TrackDialogStage {
    /** Ожидание URL ссылки от пользователя. */
    AWAIT_LINK,
    /** Ожидание списка тегов. */
    AWAIT_TAGS,
    /** Ожидание списка фильтров. */
    AWAIT_FILTERS
}
