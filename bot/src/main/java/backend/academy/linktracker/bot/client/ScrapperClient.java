package backend.academy.linktracker.bot.client;

import backend.academy.linktracker.bot.client.dto.LinkResponse;
import java.util.List;

/**
 * Контракт клиента для взаимодействия с scrapper-сервисом.
 */
public interface ScrapperClient {
    /**
     * Регистрирует чат в scrapper.
     *
     * @param chatId идентификатор чата
     */
    void registerChat(long chatId);

    /**
     * Добавляет ссылку в отслеживание.
     *
     * @param chatId идентификатор чата
     * @param link ссылка для отслеживания
     * @param tags теги подписки
     * @param filters фильтры подписки
     * @return добавленная ссылка
     */
    LinkResponse addLink(long chatId, String link, List<String> tags, List<String> filters);

    /**
     * Удаляет ссылку из отслеживания.
     *
     * @param chatId идентификатор чата
     * @param link ссылка для удаления
     * @return удаленная ссылка
     */
    LinkResponse removeLink(long chatId, String link);

    /**
     * Возвращает список отслеживаемых ссылок чата.
     *
     * @param chatId идентификатор чата
     * @return список ссылок
     */
    List<LinkResponse> getLinks(long chatId);
}
