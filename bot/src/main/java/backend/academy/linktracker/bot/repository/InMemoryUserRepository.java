package backend.academy.linktracker.bot.repository;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Repository;

@Repository
public class InMemoryUserRepository implements UserRepository {

    private final Set<Long> knownUsers = ConcurrentHashMap.newKeySet();

    @Override
    public boolean registerIfAbsent(long chatId) {
        return knownUsers.add(chatId);
    }
}
