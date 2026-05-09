package backend.academy.linktracker.scrapper.controller;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Извлекает IP-адрес клиента из HTTP-запроса.
 *
 * <p>Нужен, чтобы все HTTP-обработчики использовали единый и безопасный
 * алгоритм определения клиентского адреса.
 */
final class ClientIpResolver {

    /**
     * Заголовок прокси с исходным IP клиента.
     */
    private static final String FORWARDED_FOR_HEADER = "X-Forwarded-For";

    /**
     * Разделитель списка адресов в заголовке X-Forwarded-For.
     */
    private static final String FORWARDED_FOR_SEPARATOR = ",";

    /**
     * Ограничение длины IP-строки для защиты от некорректного ввода.
     */
    private static final int MAX_IP_LENGTH = 64;

    /**
     * Безопасное fallback-значение, если адрес определить не удалось.
     */
    private static final String UNKNOWN_IP = "unknown";

    /**
     * Утилитный класс не должен создаваться извне.
     */
    private ClientIpResolver() {}

    /**
     * Возвращает клиентский IP из заголовков запроса или remote address.
     *
     * <p>Нужен, чтобы rate limiting всегда работал с валидным идентификатором клиента
     * и не падал на пустых/битых заголовках.
     *
     * @param request HTTP-запрос
     * @return строковое представление IP-адреса
     */
    static String resolve(HttpServletRequest request) {
        String forwardedFor = request.getHeader(FORWARDED_FOR_HEADER);
        String firstForwardedIp = extractFirstForwardedIp(forwardedFor);
        if (isValidClientIp(firstForwardedIp)) {
            return firstForwardedIp;
        }
        String remoteAddr = request.getRemoteAddr();
        if (isValidClientIp(remoteAddr)) {
            return remoteAddr;
        }
        return UNKNOWN_IP;
    }

    /**
     * Извлекает первый IP из заголовка X-Forwarded-For.
     *
     * <p>Нужен, чтобы использовать исходный адрес клиента, а не цепочку прокси.
     *
     * @param forwardedFor значение заголовка X-Forwarded-For
     * @return первый адрес из списка или {@code null}
     */
    private static String extractFirstForwardedIp(String forwardedFor) {
        if (forwardedFor == null || forwardedFor.isBlank()) {
            return null;
        }
        return forwardedFor.split(FORWARDED_FOR_SEPARATOR)[0].trim();
    }

    /**
     * Проверяет базовую корректность IP-значения.
     *
     * <p>Нужен, чтобы отбраковать пустые и аномально длинные строки до использования.
     *
     * @param ipValue строковое значение адреса
     * @return {@code true}, если значение пригодно для идентификации клиента
     */
    private static boolean isValidClientIp(String ipValue) {
        return ipValue != null && !ipValue.isBlank() && ipValue.length() <= MAX_IP_LENGTH;
    }
}
