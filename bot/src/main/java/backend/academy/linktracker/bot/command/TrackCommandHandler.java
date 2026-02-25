package backend.academy.linktracker.bot.command;

import backend.academy.linktracker.bot.service.TrackDialogService;
import org.springframework.stereotype.Component;

@Component
public class TrackCommandHandler extends AbstractBotCommandHandler {

    private final TrackDialogService trackDialogService;

    public TrackCommandHandler(TrackDialogService trackDialogService) {
        super("/track", "начать отслеживание ссылки");
        this.trackDialogService = trackDialogService;
    }

    @Override
    public String handle(long chatId, String messageText) {
        return trackDialogService.start(chatId);
    }
}
