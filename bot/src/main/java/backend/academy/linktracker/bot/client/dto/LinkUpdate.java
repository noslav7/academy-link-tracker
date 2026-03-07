package backend.academy.linktracker.bot.client.dto;

import java.util.List;

/**
 * DTO входящего уведомления об обновлении ссылки.
 *
 * @param id идентификатор ссылки
 * @param url URL ссылки
 * @param description описание обновления
 * @param tgChatIds получатели уведомления
 */
public record LinkUpdate(long id, String url, String description, List<Long> tgChatIds) {}
