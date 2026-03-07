package backend.academy.linktracker.scrapper.controller;

import backend.academy.linktracker.scrapper.service.ChatService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST-контроллер регистрации и удаления Telegram-чатов.
 */
@RestController
@RequestMapping("/tg-chat")
public class TgChatController {

    /**
     * Сервис управления жизненным циклом чатов.
     */
    private final ChatService chatService;

    /**
     * Создает контроллер управления чатами.
     *
     * @param chatService сервис управления чатами
     */
    public TgChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    /**
     * Регистрирует новый чат в системе отслеживания.
     *
     * @param chatId идентификатор Telegram-чата
     * @return пустой ответ с кодом 200
     */
    @PostMapping("/{id}")
    public ResponseEntity<Void> register(@PathVariable("id") long chatId) {
        chatService.register(chatId);
        return ResponseEntity.ok().build();
    }

    /**
     * Удаляет чат и связанные подписки из системы.
     *
     * @param chatId идентификатор Telegram-чата
     * @return пустой ответ с кодом 200
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") long chatId) {
        chatService.remove(chatId);
        return ResponseEntity.ok().build();
    }
}
