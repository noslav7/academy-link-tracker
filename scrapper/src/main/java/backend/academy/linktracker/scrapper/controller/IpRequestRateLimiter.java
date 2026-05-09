package backend.academy.linktracker.scrapper.controller;

import backend.academy.linktracker.scrapper.properties.RateLimitingProperties;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Управляет токен-бакетами Resilience4j для IP-адресов клиентов.
 *
 * <p>Нужен, чтобы инкапсулировать детали интеграции с RateLimiterRegistry
 * и переиспользовать единый алгоритм проверки квоты.
 */
@Component
public class IpRequestRateLimiter {

    /**
     * Шаблон очистки имени limiter от недопустимых символов.
     */
    private static final Pattern LIMITER_NAME_SANITIZER = Pattern.compile("[^a-zA-Z0-9._-]");

    /**
     * Реестр limiter-ов с общей конфигурацией периода и квоты.
     */
    private final RateLimiterRegistry rateLimiterRegistry;

    /**
     * Кэш limiter-ов по клиентскому IP, чтобы переиспользовать существующие limiter-ы.
     */
    private final ConcurrentMap<String, RateLimiter> rateLimitersByIp = new ConcurrentHashMap<>();

    /**
     * Создает компонент лимитирования по IP на основе настроек приложения.
     *
     * <p>Нужен, чтобы единообразно применять конфигурацию лимитов ко всем адресам.
     *
     * @param properties свойства ограничения частоты запросов
     */
    public IpRequestRateLimiter(RateLimitingProperties properties) {
        RateLimiterConfig config = RateLimiterConfig.custom()
                .limitForPeriod(properties.getLimitForPeriod())
                .limitRefreshPeriod(properties.getLimitRefreshPeriod())
                .timeoutDuration(properties.getTimeoutDuration())
                .build();
        this.rateLimiterRegistry = RateLimiterRegistry.of(config);
    }

    /**
     * Пытается получить разрешение на обработку запроса для указанного IP.
     *
     * <p>Нужен, чтобы вызывающая сторона принимала решение о допуске без знания
     * внутренних деталей хранения и создания limiter-ов.
     *
     * @param clientIp ip-адрес клиента
     * @return {@code true}, если запрос разрешен
     */
    public boolean tryAcquire(String clientIp) {
        RateLimiter rateLimiter = rateLimitersByIp.computeIfAbsent(clientIp, this::createRateLimiterForIp);
        return rateLimiter.acquirePermission();
    }

    /**
     * Создает или получает limiter для конкретного IP-адреса.
     *
     * <p>Нужен, чтобы преобразовать IP в безопасный ключ реестра и избежать
     * ошибок при именовании limiter-ов.
     *
     * @param clientIp ip-адрес клиента
     * @return limiter с конфигурацией текущего сервиса
     */
    private RateLimiter createRateLimiterForIp(String clientIp) {
        String safeIp = LIMITER_NAME_SANITIZER.matcher(clientIp).replaceAll("_");
        return rateLimiterRegistry.rateLimiter("http-ip-" + safeIp);
    }
}
