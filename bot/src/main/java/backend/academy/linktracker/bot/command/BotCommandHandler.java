package backend.academy.linktracker.bot.command;

public interface BotCommandHandler {

    String command();

    String description();

    String handle(long chatId);
}
