package backend.academy.linktracker.scrapper.repository.orm.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "chats")
public class ChatEntity {

    @Id
    @Column(name = "chat_id", nullable = false)
    private Long chatId;

    protected ChatEntity() {}

    public ChatEntity(Long chatId) {
        this.chatId = chatId;
    }

    public Long getChatId() {
        return chatId;
    }
}
