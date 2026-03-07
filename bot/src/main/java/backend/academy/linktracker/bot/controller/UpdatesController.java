package backend.academy.linktracker.bot.controller;

import backend.academy.linktracker.bot.client.dto.LinkUpdate;
import backend.academy.linktracker.bot.service.UpdateNotificationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST-контроллер приема уведомлений об обновлениях от scrapper.
 */
@RestController
public class UpdatesController {

    /**
     * Сервис рассылки уведомлений пользователям бота.
     */
    private final UpdateNotificationService updateNotificationService;

    /**
     * Создает контроллер приема обновлений.
     *
     * @param updateNotificationService сервис рассылки уведомлений
     */
    public UpdatesController(UpdateNotificationService updateNotificationService) {
        this.updateNotificationService = updateNotificationService;
    }

    /**
     * Принимает обновление ссылки и запускает отправку уведомлений пользователям.
     *
     * @param update DTO обновления ссылки
     * @return пустой ответ с кодом 200
     */
    @PostMapping("/updates")
    public ResponseEntity<Void> processUpdate(@RequestBody LinkUpdate update) {
        updateNotificationService.notifyAboutUpdate(update);
        return ResponseEntity.ok().build();
    }
}
