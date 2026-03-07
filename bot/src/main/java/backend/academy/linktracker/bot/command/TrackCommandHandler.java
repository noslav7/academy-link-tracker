package backend.academy.linktracker.bot.command;

import backend.academy.linktracker.bot.service.TrackDialogService;
import org.springframework.stereotype.Component;

/**
 * Обработчик команды {@code /track}.
 */
@Component
public class TrackCommandHandler extends AbstractBotCommandHandler {

    /**
     * Сервис пошагового диалога команды /track.
     */
    private final TrackDialogService trackDialogService;

    /**
     * Создает обработчик команды {@code /track}.
     *
     * @param trackDialogService сервис пошагового диалога
     */
    public TrackCommandHandler(TrackDialogService trackDialogService) {
        super("/track", "начать отслеживание ссылки");
        this.trackDialogService = trackDialogService;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String handle(long chatId, String messageText) {
        return trackDialogService.start(chatId);
    }
}
