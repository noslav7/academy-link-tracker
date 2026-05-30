package backend.academy.linktracker.bot.service;

import backend.academy.linktracker.bot.command.BotMessages;
import org.springframework.stereotype.Component;

/**
 * Отвечает за преобразование результата сабмита /track в сообщение пользователю.
 */
@Component
public class TrackSubmissionResultMessageResolver {

    /**
     * Возвращает пользовательский текст для результата сабмита.
     *
     * @param result результат отправки ссылки в scrapper
     * @return сообщение для пользователя
     */
    public String resolve(TrackSubmissionResult result) {
        return switch (result) {
            case SUCCESS -> BotMessages.TRACK_SUCCESS;
            case DUPLICATE -> BotMessages.TRACK_DUPLICATE;
            case FAILED -> BotMessages.TRACK_FAILED;
        };
    }
}
