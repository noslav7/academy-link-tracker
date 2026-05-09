package backend.academy.linktracker.scrapper.configuration;

import backend.academy.linktracker.scrapper.controller.IpRateLimitingInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * MVC-конфигурация HTTP-перехватчиков.
 *
 * <p>Нужна, чтобы явно регистрировать пользовательские interceptor-ы
 * в цепочке обработки Spring MVC.
 */
@Configuration
public class WebMvcConfiguration implements WebMvcConfigurer {

    /**
     * Interceptor ограничения запросов по IP.
     */
    private final IpRateLimitingInterceptor ipRateLimitingInterceptor;

    /**
     * Создает MVC-конфигурацию с required interceptor-ом.
     *
     * <p>Нужна, чтобы подключить rate limiting централизованно
     * и не размазывать регистрацию по другим конфигурациям.
     *
     * @param ipRateLimitingInterceptor interceptor контроля частоты запросов
     */
    public WebMvcConfiguration(IpRateLimitingInterceptor ipRateLimitingInterceptor) {
        this.ipRateLimitingInterceptor = ipRateLimitingInterceptor;
    }

    /**
     * Регистрирует interceptor в общем реестре Spring MVC.
     *
     * <p>Нужен, чтобы ограничение запросов применялось ко всем HTTP-вызовам
     * до входа в контроллеры.
     *
     * @param registry реестр MVC interceptor-ов
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(ipRateLimitingInterceptor);
    }
}
