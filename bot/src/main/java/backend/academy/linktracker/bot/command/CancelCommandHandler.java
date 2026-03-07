package backend.academy.linktracker.bot.command;

import backend.academy.linktracker.bot.service.TrackDialogService;
import org.springframework.stereotype.Component;

/**
 * Обработчик команды {@code /cancel}.
 */
@Component
public class CancelCommandHandler extends AbstractBotCommandHandler {

    /**
     * Сервис управления диалогом /track.
     */
    private final TrackDialogService trackDialogService;

    /**
     * Создает обработчик команды {@code /cancel}.
     *
     * @param trackDialogService сервис управления диалогом
     */
    public CancelCommandHandler(TrackDialogService trackDialogService) {
        super("/cancel", "прервать текущий процесс");
        this.trackDialogService = trackDialogService;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String handle(long chatId, String messageText) {
        return trackDialogService.cancel(chatId);
    }
}
