package backend.academy.linktracker.bot.controller;

import backend.academy.linktracker.bot.client.dto.LinkUpdate;
import backend.academy.linktracker.bot.service.UpdateNotificationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class UpdatesController {

    private final UpdateNotificationService updateNotificationService;

    public UpdatesController(UpdateNotificationService updateNotificationService) {
        this.updateNotificationService = updateNotificationService;
    }

    @PostMapping("/updates")
    public ResponseEntity<Void> processUpdate(@RequestBody LinkUpdate update) {
        updateNotificationService.notifyAboutUpdate(update);
        return ResponseEntity.ok().build();
    }
}
