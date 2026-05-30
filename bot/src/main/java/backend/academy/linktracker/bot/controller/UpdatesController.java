package backend.academy.linktracker.bot.controller;

import backend.academy.linktracker.bot.client.dto.LinkUpdate;
import backend.academy.linktracker.bot.metrics.BotMetricsRecorder;
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
    private static final String SCOPE_SCRAPPER_ASYNC_API = "scrapper_async_api";
    private static final String SCOPE_TYPE_PROCESS_UPDATE = "processUpdate";

    /**
     * Сервис рассылки уведомлений пользователям бота.
     */
    private final UpdateNotificationService updateNotificationService;

    private final BotMetricsRecorder metricsRecorder;

    /**
     * Создает контроллер приема обновлений.
     *
     * @param updateNotificationService сервис рассылки уведомлений
     */
    public UpdatesController(UpdateNotificationService updateNotificationService, BotMetricsRecorder metricsRecorder) {
        this.updateNotificationService = updateNotificationService;
        this.metricsRecorder = metricsRecorder;
    }

    /**
     * Принимает обновление ссылки и запускает отправку уведомлений пользователям.
     *
     * @param update DTO обновления ссылки
     * @return пустой ответ с кодом 200
     */
    @PostMapping("/updates")
    public ResponseEntity<Void> processUpdate(@RequestBody LinkUpdate update) {
        metricsRecorder.recordDuration(
                SCOPE_SCRAPPER_ASYNC_API,
                SCOPE_TYPE_PROCESS_UPDATE,
                () -> updateNotificationService.notifyAboutUpdate(update));
        return ResponseEntity.ok().build();
    }
}
