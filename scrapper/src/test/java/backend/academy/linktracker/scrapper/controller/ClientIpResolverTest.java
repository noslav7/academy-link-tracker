package backend.academy.linktracker.scrapper.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
class ClientIpResolverTest {

    @Test
    void givenForwardedForHeaderWhenResolvingThenUsesFirstIp() {
        // Проверяет, что при наличии X-Forwarded-For берется первый клиентский IP.
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-Forwarded-For")).thenReturn("198.51.100.15, 10.0.0.1");
        when(request.getRemoteAddr()).thenReturn("192.0.2.8");

        assertEquals("198.51.100.15", ClientIpResolver.resolve(request));
    }

    @Test
    void givenInvalidForwardedForWhenResolvingThenFallsBackToRemoteAddr() {
        // Проверяет fallback на remoteAddr при пустом/невалидном X-Forwarded-For.
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-Forwarded-For")).thenReturn(" ");
        when(request.getRemoteAddr()).thenReturn("192.0.2.9");

        assertEquals("192.0.2.9", ClientIpResolver.resolve(request));
    }

    @Test
    void givenInvalidHeaderAndRemoteAddrWhenResolvingThenReturnsUnknown() {
        // Проверяет граничный случай: если адрес извлечь нельзя, возвращается "unknown".
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-Forwarded-For")).thenReturn(" ");
        when(request.getRemoteAddr()).thenReturn(null);

        assertEquals("unknown", ClientIpResolver.resolve(request));
    }
}
