package backend.academy.linktracker.bot.repository;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Repository;

/**
 * In-memory реализация репозитория пользователей.
 */
@Repository
public class InMemoryUserRepository implements UserRepository {

    /**
     * Множество идентификаторов уже зарегистрированных пользователей.
     */
    private final Set<Long> knownUsers = ConcurrentHashMap.newKeySet();

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean registerIfAbsent(long chatId) {
        return knownUsers.add(chatId);
    }
}
