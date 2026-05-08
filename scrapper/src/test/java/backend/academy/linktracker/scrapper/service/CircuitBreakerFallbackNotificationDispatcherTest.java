package backend.academy.linktracker.scrapper.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import backend.academy.linktracker.scrapper.dto.LinkUpdate;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
class CircuitBreakerFallbackNotificationDispatcherTest {

    @Test
    void givenStableHttpFailuresWhenThresholdReachedThenBreakerTransitionsToOpenAndShortCircuitsCalls() {
        // TC-4.1: переход в OPEN и short-circuit последующих вызовов без обращения к HTTP.
        NotificationDispatcher primary = mock(NotificationDispatcher.class);
        doThrow(new IllegalStateException("HTTP transport unavailable"))
                .when(primary)
                .dispatch(any(LinkUpdate.class));
        NotificationDispatcher fallback = mock(NotificationDispatcher.class);
        doNothing().when(fallback).dispatch(any(LinkUpdate.class));
        CircuitBreaker breaker = CircuitBreaker.of("cb-open", testConfig(Duration.ofSeconds(1), 4, 4, 2));
        CircuitBreakerFallbackNotificationDispatcher dispatcher =
                new CircuitBreakerFallbackNotificationDispatcher(primary, fallback, breaker);

        for (int i = 0; i < 4; i++) {
            dispatcher.dispatch(update(i + 1L));
        }
        assertEquals(CircuitBreaker.State.OPEN, breaker.getState());

        dispatcher.dispatch(update(5L));

        verify(primary, times(4)).dispatch(any(LinkUpdate.class));
        verify(fallback, times(5)).dispatch(any(LinkUpdate.class));
    }

    @Test
    void givenHalfOpenProbeCallsSuccessfulWhenFailureRateWithinThresholdThenBreakerTransitionsToClosed() {
        // TC-4.2: после OPEN успешные probe-вызовы переводят breaker в CLOSED.
        NotificationDispatcher primary = mock(NotificationDispatcher.class);
        doThrow(
                        new IllegalStateException("HTTP transport unavailable"),
                        new IllegalStateException("HTTP transport unavailable"),
                        new IllegalStateException("HTTP transport unavailable"),
                        new IllegalStateException("HTTP transport unavailable"))
                .doNothing()
                .doNothing()
                .when(primary)
                .dispatch(any(LinkUpdate.class));
        NotificationDispatcher fallback = mock(NotificationDispatcher.class);
        doNothing().when(fallback).dispatch(any(LinkUpdate.class));
        CircuitBreaker breaker = CircuitBreaker.of("cb-half-open-closed", testConfig(Duration.ofMillis(120), 4, 4, 2));
        CircuitBreakerFallbackNotificationDispatcher dispatcher =
                new CircuitBreakerFallbackNotificationDispatcher(primary, fallback, breaker);

        for (int i = 0; i < 4; i++) {
            dispatcher.dispatch(update(i + 1L));
        }
        assertEquals(CircuitBreaker.State.OPEN, breaker.getState());

        breaker.transitionToHalfOpenState();
        assertEquals(CircuitBreaker.State.HALF_OPEN, breaker.getState());

        dispatcher.dispatch(update(10L));
        dispatcher.dispatch(update(11L));

        assertEquals(CircuitBreaker.State.CLOSED, breaker.getState());
        verify(fallback, times(4)).dispatch(any(LinkUpdate.class));
        verify(primary, times(6)).dispatch(any(LinkUpdate.class));
    }

    @Test
    void givenHalfOpenProbeCallsFailWhenFailureRateAboveThresholdThenBreakerReturnsToOpen() {
        // TC-4.3: если probe-вызовы в HALF_OPEN неуспешны, breaker возвращается в OPEN.
        NotificationDispatcher primary = mock(NotificationDispatcher.class);
        doThrow(new IllegalStateException("HTTP transport unavailable"))
                .when(primary)
                .dispatch(any(LinkUpdate.class));
        NotificationDispatcher fallback = mock(NotificationDispatcher.class);
        doNothing().when(fallback).dispatch(any(LinkUpdate.class));
        CircuitBreaker breaker = CircuitBreaker.of("cb-half-open-open", testConfig(Duration.ofMillis(120), 4, 4, 2));
        CircuitBreakerFallbackNotificationDispatcher dispatcher =
                new CircuitBreakerFallbackNotificationDispatcher(primary, fallback, breaker);

        for (int i = 0; i < 4; i++) {
            dispatcher.dispatch(update(i + 1L));
        }
        assertEquals(CircuitBreaker.State.OPEN, breaker.getState());

        breaker.transitionToHalfOpenState();
        assertEquals(CircuitBreaker.State.HALF_OPEN, breaker.getState());
        dispatcher.dispatch(update(20L));
        dispatcher.dispatch(update(21L));

        assertEquals(CircuitBreaker.State.OPEN, breaker.getState());
        verify(fallback, times(6)).dispatch(any(LinkUpdate.class));
    }

    private static CircuitBreakerConfig testConfig(
            Duration waitDurationInOpenState,
            int slidingWindowSize,
            int minimumRequiredCalls,
            int permittedCallsInHalfOpenState) {
        return CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(slidingWindowSize)
                .minimumNumberOfCalls(minimumRequiredCalls)
                .failureRateThreshold(100.0f)
                .permittedNumberOfCallsInHalfOpenState(permittedCallsInHalfOpenState)
                .waitDurationInOpenState(waitDurationInOpenState)
                .recordException(throwable -> true)
                .build();
    }

    private static LinkUpdate update(long id) {
        return new LinkUpdate(id, "https://example.org/%d".formatted(id), "description", List.of(1001L));
    }
}
