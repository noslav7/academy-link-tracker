package backend.academy.linktracker.scrapper.service;

import backend.academy.linktracker.scrapper.configuration.LinksCacheConfiguration;
import backend.academy.linktracker.scrapper.dto.AddLinkRequest;
import backend.academy.linktracker.scrapper.dto.LinkResponse;
import backend.academy.linktracker.scrapper.dto.ListLinksResponse;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

/**
 * Прослойка между контроллером и доменным сервисом для кэширования ответа GET /list.
 */
@Service
public class CachedLinksService {

    private final LinkService linkService;

    /**
     * Создает фасад кэширования ссылок.
     *
     * @param linkService доменный сервис управления ссылками
     */
    public CachedLinksService(LinkService linkService) {
        this.linkService = linkService;
    }

    /**
     * Возвращает список ссылок чата с кэшированием по ключу chatId.
     *
     * @param chatId идентификатор чата
     * @return список ссылок и их количество
     */
    @Cacheable(cacheNames = LinksCacheConfiguration.LINKS_LIST_CACHE, key = "#chatId")
    public ListLinksResponse list(long chatId) {
        var links = linkService.list(chatId);
        return new ListLinksResponse(links, links.size());
    }

    /**
     * Добавляет ссылку и инвалидирует кэш списка ссылок чата.
     *
     * @param chatId идентификатор чата
     * @param request данные новой ссылки
     * @return добавленная ссылка
     */
    @CacheEvict(cacheNames = LinksCacheConfiguration.LINKS_LIST_CACHE, key = "#chatId")
    public LinkResponse add(long chatId, AddLinkRequest request) {
        return linkService.add(chatId, request);
    }

    /**
     * Удаляет ссылку и инвалидирует кэш списка ссылок чата.
     *
     * @param chatId идентификатор чата
     * @param link ссылка для удаления
     * @return удаленная ссылка
     */
    @CacheEvict(cacheNames = LinksCacheConfiguration.LINKS_LIST_CACHE, key = "#chatId")
    public LinkResponse remove(long chatId, String link) {
        return linkService.remove(chatId, link);
    }
}
