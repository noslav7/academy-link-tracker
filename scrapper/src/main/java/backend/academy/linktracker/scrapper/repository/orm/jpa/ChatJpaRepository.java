package backend.academy.linktracker.scrapper.repository.orm.jpa;

import backend.academy.linktracker.scrapper.repository.orm.entity.ChatEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ChatJpaRepository extends JpaRepository<ChatEntity, Long> {

    @Query("select c.chatId from ChatEntity c order by c.chatId")
    List<Long> findAllChatIds();
}
