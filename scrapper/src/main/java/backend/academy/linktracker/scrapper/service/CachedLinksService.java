package backend.academy.linktracker.scrapper.service;

import backend.academy.linktracker.scrapper.dto.AddLinkRequest;
import backend.academy.linktracker.scrapper.dto.LinkResponse;
import backend.academy.linktracker.scrapper.dto.ListLinksResponse;
import backend.academy.linktracker.scrapper.service.cache.InMemoryLinksListCache;
import backend.academy.linktracker.scrapper.service.cache.LinksListCache;
import java.util.Objects;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Прослойка между контроллером и доменным сервисом для кэширования ответа GET /list.
 */
@Service
public class CachedLinksService {

    private final LinkService linkService;
    private final LinksListCache linksListCache;

    /**
     * Создает фасад кэширования ссылок.
     *
     * @param linkService доменный сервис управления ссылками
     */
    @Autowired
    public CachedLinksService(LinkService linkService, LinksListCache linksListCache) {
        this.linkService = linkService;
        this.linksListCache = linksListCache;
    }

    /**
     * Оставлен для обратной совместимости с ручным созданием сервиса в тестах.
     */
    public CachedLinksService(LinkService linkService) {
        this(linkService, new InMemoryLinksListCache());
    }

    /**
     * Возвращает список ссылок чата с кэшированием по ключу chatId.
     *
     * @param chatId идентификатор чата
     * @return список ссылок и их количество
     * @implNote Нужно, чтобы снизить нагрузку на хранилище при частых вызовах `GET /list` для одного и того же чата.
     */
    public ListLinksResponse list(long chatId) {
        validateChatId(chatId);
        ListLinksResponse cached = linksListCache.get(chatId);
        if (cached != null) {
            return cached;
        }
        var links = linkService.list(chatId);
        ListLinksResponse response = new ListLinksResponse(links, links.size());
        linksListCache.put(chatId, response);
        return response;
    }

    /**
     * Добавляет ссылку и инвалидирует кэш списка ссылок чата.
     *
     * @param chatId идентификатор чата
     * @param request данные новой ссылки
     * @return добавленная ссылка
     * @implNote Нужно, чтобы после мутации не отдавать устаревший кэшированный список ссылок.
     */
    public LinkResponse add(long chatId, AddLinkRequest request) {
        validateChatId(chatId);
        Objects.requireNonNull(request, "request must not be null");
        return evictAfterMutation(chatId, () -> linkService.add(chatId, request));
    }

    /**
     * Удаляет ссылку и инвалидирует кэш списка ссылок чата.
     *
     * @param chatId идентификатор чата
     * @param link ссылка для удаления
     * @return удаленная ссылка
     * @implNote Нужно, чтобы после удаления ссылки кэш не содержал устаревшую запись.
     */
    public LinkResponse remove(long chatId, String link) {
        validateChatId(chatId);
        if (link == null || link.isBlank()) {
            throw new IllegalArgumentException("link must not be blank");
        }
        return evictAfterMutation(chatId, () -> linkService.remove(chatId, link));
    }

    /**
     * Выполняет мутацию и инвалидирует кэш чата после успешного изменения.
     *
     * @param chatId идентификатор чата
     * @param mutation операция изменения данных
     * @return результат операции изменения
     * @param <T> тип результата операции
     * @implNote Нужно, чтобы централизовать шаблон "изменить данные -> сбросить кэш" в одном месте.
     */
    private <T> T evictAfterMutation(long chatId, Supplier<T> mutation) {
        T response = mutation.get();
        linksListCache.evict(chatId);
        return response;
    }

    /**
     * Проверяет корректность идентификатора чата.
     *
     * @param chatId идентификатор чата
     * @implNote Нужно, чтобы отклонять некорректный вход до обращения к доменному сервису и кэшу.
     */
    private static void validateChatId(long chatId) {
        if (chatId <= 0) {
            throw new IllegalArgumentException("chatId must be positive");
        }
    }
}
