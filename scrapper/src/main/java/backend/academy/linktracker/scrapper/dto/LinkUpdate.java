package backend.academy.linktracker.scrapper.dto;

import java.util.List;

/**
 * DTO уведомления об обновлении отслеживаемой ссылки.
 *
 * @param id идентификатор ссылки
 * @param url URL ссылки
 * @param description описание обновления
 * @param tgChatIds идентификаторы чатов получателей
 */
public record LinkUpdate(long id, String url, String description, List<Long> tgChatIds) {}
