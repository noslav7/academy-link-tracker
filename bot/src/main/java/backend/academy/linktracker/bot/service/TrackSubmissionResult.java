package backend.academy.linktracker.bot.service;

import backend.academy.linktracker.bot.command.BotMessages;

/**
 * Результат отправки данных диалога {@code /track} в scrapper.
 */
enum TrackSubmissionResult {
    /** Ссылка успешно добавлена. */
    SUCCESS(BotMessages.TRACK_SUCCESS),
    /** Ссылка уже отслеживается в текущем чате. */
    DUPLICATE(BotMessages.TRACK_DUPLICATE),
    /** Возникла ошибка запроса к scrapper. */
    FAILED(BotMessages.TRACK_FAILED);

    /**
     * Пользовательское сообщение для текущего результата.
     */
    private final String responseMessage;

    /**
     * Создает enum-значение с готовым сообщением для ответа пользователю.
     *
     * @param responseMessage текст ответа пользователю
     */
    TrackSubmissionResult(String responseMessage) {
        this.responseMessage = responseMessage;
    }

    /**
     * Возвращает сообщение, которое нужно показать пользователю.
     *
     * @return текст ответа пользователю
     */
    String responseMessage() {
        return responseMessage;
    }
}
