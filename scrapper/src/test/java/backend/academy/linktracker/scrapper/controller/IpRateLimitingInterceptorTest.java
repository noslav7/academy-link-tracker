package backend.academy.linktracker.scrapper.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import backend.academy.linktracker.scrapper.exception.RateLimitExceededException;
import backend.academy.linktracker.scrapper.properties.RateLimitingProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
class IpRateLimitingInterceptorTest {

    @Test
    void givenRateLimitingDisabledWhenPreHandleThenRequestIsAllowed() {
        // Проверяет, что при отключенном лимитере запрос пропускается без обращения к квотам.
        RateLimitingProperties properties = new RateLimitingProperties();
        properties.setEnabled(false);
        IpRequestRateLimiter limiter = mock(IpRequestRateLimiter.class);
        PublicPathMatcher matcher = mock(PublicPathMatcher.class);
        IpRateLimitingInterceptor interceptor = new IpRateLimitingInterceptor(properties, limiter, matcher);
        HttpServletRequest request = mock(HttpServletRequest.class);

        assertTrue(interceptor.preHandle(request, mock(HttpServletResponse.class), new Object()));
        verifyNoInteractions(limiter);
    }

    @Test
    void givenNonPublicPathWhenPreHandleThenRequestIsAllowed() {
        // Проверяет, что внутренние endpoint не ограничиваются rate limiting.
        RateLimitingProperties properties = new RateLimitingProperties();
        properties.setEnabled(true);
        IpRequestRateLimiter limiter = mock(IpRequestRateLimiter.class);
        PublicPathMatcher matcher = mock(PublicPathMatcher.class);
        IpRateLimitingInterceptor interceptor = new IpRateLimitingInterceptor(properties, limiter, matcher);
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/actuator/health");
        when(matcher.matches("/actuator/health")).thenReturn(false);

        assertTrue(interceptor.preHandle(request, mock(HttpServletResponse.class), new Object()));
        verifyNoInteractions(limiter);
    }

    @Test
    void givenPublicPathAndAvailablePermitWhenPreHandleThenRequestIsAllowed() {
        // Проверяет базовый positive-case: публичный endpoint разрешается при доступной квоте.
        RateLimitingProperties properties = new RateLimitingProperties();
        properties.setEnabled(true);
        IpRequestRateLimiter limiter = mock(IpRequestRateLimiter.class);
        PublicPathMatcher matcher = mock(PublicPathMatcher.class);
        IpRateLimitingInterceptor interceptor = new IpRateLimitingInterceptor(properties, limiter, matcher);
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/tg-chat/7001");
        when(request.getHeader("X-Forwarded-For")).thenReturn("203.0.113.55, 10.0.0.1");
        when(matcher.matches("/tg-chat/7001")).thenReturn(true);
        when(limiter.tryAcquire("203.0.113.55")).thenReturn(true);

        assertTrue(interceptor.preHandle(request, mock(HttpServletResponse.class), new Object()));
        verify(limiter).tryAcquire("203.0.113.55");
    }

    @Test
    void givenPublicPathAndNoPermitWhenPreHandleThenThrowsRateLimitExceeded() {
        // Проверяет error-case: при исчерпанной квоте interceptor выбрасывает доменное исключение.
        RateLimitingProperties properties = new RateLimitingProperties();
        properties.setEnabled(true);
        IpRequestRateLimiter limiter = mock(IpRequestRateLimiter.class);
        PublicPathMatcher matcher = mock(PublicPathMatcher.class);
        IpRateLimitingInterceptor interceptor = new IpRateLimitingInterceptor(properties, limiter, matcher);
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/tg-chat/7002");
        when(request.getHeader("X-Forwarded-For")).thenReturn("198.51.100.77");
        when(matcher.matches("/tg-chat/7002")).thenReturn(true);
        when(limiter.tryAcquire("198.51.100.77")).thenReturn(false);

        RateLimitExceededException exception = assertThrows(
                RateLimitExceededException.class,
                () -> interceptor.preHandle(request, mock(HttpServletResponse.class), new Object()));

        assertEquals("Rate limit exceeded for IP: 198.51.100.77", exception.getMessage());
    }
}
