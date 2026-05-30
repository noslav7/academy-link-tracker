package backend.academy.linktracker.bot.service;

/**
 * Результат отправки данных диалога {@code /track} в scrapper.
 */
enum TrackSubmissionResult {
    /** Ссылка успешно добавлена. */
    SUCCESS,
    /** Ссылка уже отслеживается в текущем чате. */
    DUPLICATE,
    /** Возникла ошибка запроса к scrapper. */
    FAILED
}
