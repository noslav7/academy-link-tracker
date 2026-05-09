package backend.academy.linktracker.scrapper.controller;

import backend.academy.linktracker.scrapper.exception.RateLimitExceededException;
import backend.academy.linktracker.scrapper.properties.RateLimitingProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Применяет ограничение частоты к входящим HTTP-запросам.
 *
 * <p>Нужен, чтобы защитить публичные endpoint от чрезмерной нагрузки по IP
 * и сохранить предсказуемое время ответа сервиса.
 */
@Component
public class IpRateLimitingInterceptor implements HandlerInterceptor {

    /**
     * Настройки лимитирования, определяющие включение механизма.
     */
    private final RateLimitingProperties properties;

    /**
     * Компонент выдачи разрешений на обработку запросов по IP.
     */
    private final IpRequestRateLimiter ipRequestRateLimiter;

    /**
     * Компонент проверки, относится ли URI к публичным endpoint.
     */
    private final PublicPathMatcher publicPathMatcher;

    /**
     * Создает interceptor с зависимостями для принятия решения о пропуске запроса.
     *
     * <p>Нужен, чтобы централизованно оркестрировать проверку публичного пути и квоты.
     *
     * @param properties настройки rate limiting
     * @param ipRequestRateLimiter компонент ограничения запросов по IP
     * @param publicPathMatcher компонент матчинга публичных URI
     */
    public IpRateLimitingInterceptor(
            RateLimitingProperties properties,
            IpRequestRateLimiter ipRequestRateLimiter,
            PublicPathMatcher publicPathMatcher) {
        this.properties = properties;
        this.ipRequestRateLimiter = ipRequestRateLimiter;
        this.publicPathMatcher = publicPathMatcher;
    }

    /**
     * Разрешает или отклоняет запрос до попадания в контроллер.
     *
     * <p>Нужен, чтобы остановить превышающие лимит запросы как можно раньше
     * и не тратить ресурсы на дальнейшую обработку.
     *
     * @param request входящий HTTP-запрос
     * @param response HTTP-ответ
     * @param handler выбранный Spring MVC обработчик
     * @return {@code true}, если запрос можно продолжать обрабатывать
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!properties.isEnabled() || !publicPathMatcher.matches(request.getRequestURI())) {
            return true;
        }

        String clientIp = ClientIpResolver.resolve(request);
        if (!ipRequestRateLimiter.tryAcquire(clientIp)) {
            throw new RateLimitExceededException("Rate limit exceeded for IP: " + clientIp);
        }
        return true;
    }
}
