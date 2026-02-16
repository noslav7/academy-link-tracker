package backend.academy.linktracker.bot.command;

import backend.academy.linktracker.bot.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class StartCommandHandler implements BotCommandHandler {

    private final UserRepository userRepository;

    @Override
    public String command() {
        return "/start";
    }

    @Override
    public String description() {
        return "начало работы";
    }

    @Override
    public String handle(long chatId) {
        boolean isFirstTime = userRepository.registerIfAbsent(chatId);
        return isFirstTime ? BotMessages.START_FIRST_TIME : BotMessages.START_ALREADY_REGISTERED;
    }
}
