package backend.academy.linktracker.bot.command;

import backend.academy.linktracker.bot.service.TrackDialogService;
import org.springframework.stereotype.Component;

@Component
public class CancelCommandHandler extends AbstractBotCommandHandler {

    private final TrackDialogService trackDialogService;

    public CancelCommandHandler(TrackDialogService trackDialogService) {
        super("/cancel", "прервать текущий процесс");
        this.trackDialogService = trackDialogService;
    }

    @Override
    public String handle(long chatId, String messageText) {
        return trackDialogService.cancel(chatId);
    }
}
