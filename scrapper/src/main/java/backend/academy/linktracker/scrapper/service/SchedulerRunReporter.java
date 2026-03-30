package backend.academy.linktracker.scrapper.service;

import backend.academy.linktracker.scrapper.dto.LinkUpdate;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Формирует и публикует итоги прогона планировщика проверки ссылок.
 */
@Component
public class SchedulerRunReporter {
    /**
     * Логгер компонента отчётности планировщика.
     */
    private static final Logger log = LoggerFactory.getLogger(SchedulerRunReporter.class);

    /**
     * Отправитель технических уведомлений в bot-сервис.
     */
    private final MessageSender messageSender;

    /**
     * Создаёт компонент публикации результатов прогона планировщика.
     *
     * @param messageSender отправитель уведомлений в bot
     */
    public SchedulerRunReporter(MessageSender messageSender) {
        this.messageSender = messageSender;
    }

    /**
     * Логирует агрегированный отчет и при наличии ошибок отправляет техническое уведомление в bot.
     *
     * @param runId идентификатор прогона планировщика
     * @param processed количество обработанных ссылок
     * @param success количество успешно обработанных ссылок
     * @param failedLinks список неуспешных ссылок
     */
    public void report(String runId, int processed, int success, List<SchedulerFailedLink> failedLinks) {
        logRunReport(runId, processed, success, failedLinks);
        if (!failedLinks.isEmpty()) {
            sendErrorNotification(runId, processed, success, failedLinks);
        }
    }

    /**
     * Пишет агрегированную информацию о прогоне и список неуспешных ссылок в лог.
     *
     * @param runId идентификатор прогона
     * @param processed количество обработанных ссылок
     * @param success количество успешных ссылок
     * @param failedLinks список ссылок с ошибками
     */
    private void logRunReport(String runId, int processed, int success, List<SchedulerFailedLink> failedLinks) {
        log.atInfo()
                .addKeyValue("runId", runId)
                .addKeyValue("processedCount", processed)
                .addKeyValue("successCount", success)
                .addKeyValue("failedCount", failedLinks.size())
                .log("Scheduler run completed");

        for (SchedulerFailedLink failedLink : failedLinks) {
            log.atWarn()
                    .addKeyValue("runId", runId)
                    .addKeyValue("url", failedLink.url())
                    .addKeyValue("chatId", failedLink.chatId())
                    .addKeyValue("reason", failedLink.reason())
                    .log("Failed link in run report");
        }
    }

    /**
     * Формирует и отправляет техническое уведомление об ошибках прогона.
     *
     * @param runId идентификатор прогона
     * @param processed количество обработанных ссылок
     * @param success количество успешных ссылок
     * @param failedLinks список ссылок с ошибками
     */
    private void sendErrorNotification(String runId, int processed, int success, List<SchedulerFailedLink> failedLinks) {
        StringBuilder description = new StringBuilder();
        description.append("⚠ Отчёт планировщика (runId: %s)\n".formatted(runId));
        description.append("Обработано: %d, успешно: %d, с ошибками: %d\n\n".formatted(processed, success, failedLinks.size()));
        description.append("Неуспешные ссылки:\n");

        for (SchedulerFailedLink failedLink : failedLinks) {
            description.append("• %s — %s\n".formatted(failedLink.url(), failedLink.reason()));
        }

        List<Long> affectedChatIds =
                failedLinks.stream().map(SchedulerFailedLink::chatId).distinct().toList();

        messageSender.send(new LinkUpdate(0, "system://scheduler-error-report", description.toString(), affectedChatIds));
    }
}
