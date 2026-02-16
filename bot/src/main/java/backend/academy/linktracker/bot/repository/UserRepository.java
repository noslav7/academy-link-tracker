package backend.academy.linktracker.bot.repository;

public interface UserRepository {

    boolean registerIfAbsent(long chatId);
}
