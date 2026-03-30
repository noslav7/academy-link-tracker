package backend.academy.linktracker.scrapper.service;

/**
 * Ошибка обработки отдельной ссылки в рамках одного прогона планировщика.
 *
 * @param url адрес ссылки
 * @param chatId идентификатор чата, для которого обрабатывалась ссылка
 * @param reason текст причины ошибки
 */
public record SchedulerFailedLink(String url, long chatId, String reason) {}
