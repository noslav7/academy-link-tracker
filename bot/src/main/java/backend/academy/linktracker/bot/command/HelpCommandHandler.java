package backend.academy.linktracker.bot.command;

import org.springframework.stereotype.Component;

@Component
public class HelpCommandHandler implements BotCommandHandler {

    @Override
    public String command() {
        return "/help";
    }

    @Override
    public String description() {
        return "список доступных команд";
    }

    @Override
    public String handle(long chatId) {
        return BotMessages.HELP;
    }
}
