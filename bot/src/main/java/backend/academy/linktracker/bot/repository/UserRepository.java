package backend.academy.linktracker.bot.repository;

/**
 * Репозиторий зарегистрированных пользователей бота.
 */
public interface UserRepository {

    /**
     * Регистрирует пользователя, если его еще нет в хранилище.
     *
     * @param chatId идентификатор чата пользователя
     * @return {@code true}, если пользователь добавлен впервые
     */
    boolean registerIfAbsent(long chatId);
}
