package backend.academy.linktracker.scrapper.service.cache;

import backend.academy.linktracker.scrapper.dto.ListLinksResponse;
import io.lettuce.core.RedisChannelHandler;
import io.lettuce.core.RedisConnectionStateAdapter;
import io.lettuce.core.SetArgs;
import io.lettuce.core.TrackingArgs;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.support.caching.CacheFrontend;
import io.lettuce.core.support.caching.ClientSideCaching;
import java.net.SocketAddress;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.serializer.RedisSerializer;

/**
 * Кэш списка ссылок с server-assisted client-side caching через Valkey/Lettuce.
 */
public final class ValkeyClientSideLinksListCache implements LinksListCache, AutoCloseable {

    /**
     * JSON-сериализатор payload-а кэша.
     */
    private static final RedisSerializer<Object> JSON_SERIALIZER = RedisSerializer.json();
    /**
     * Логгер диагностики работы кэша и реконнекта.
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(ValkeyClientSideLinksListCache.class);

    /**
     * Локальный near-cache с TTL.
     */
    private final ExpiringNearCache nearCache;
    /**
     * Обертка Lettuce для client-side caching.
     */
    private final CacheFrontend<String, byte[]> cacheFrontend;
    /**
     * Соединение с Valkey.
     */
    private final StatefulRedisConnection<String, byte[]> connection;
    /**
     * Время жизни записи в кэше.
     */
    private final Duration ttl;
    /**
     * Параметры client tracking на стороне сервера.
     */
    private final TrackingArgs trackingArgs;

    /**
     * Создает реализацию кэша списка ссылок на базе Valkey и near-cache.
     *
     * @param connection активное соединение с Valkey
     * @param ttl время жизни записей в кэше
     * @implNote Нужно, чтобы объединить server-assisted invalidation и локальный near-cache с TTL для снижения
     *     латентности чтений.
     */
    public ValkeyClientSideLinksListCache(StatefulRedisConnection<String, byte[]> connection, Duration ttl) {
        this.nearCache = new ExpiringNearCache(ttl);
        this.connection = connection;
        this.ttl = ttl;
        this.trackingArgs = TrackingArgs.Builder.enabled().noloop();
        this.cacheFrontend = ClientSideCaching.enable(nearCache, connection, trackingArgs);
        this.connection.addListener(new ReconnectTrackingListener());
    }

    /**
     * Читает список ссылок из кэша по chatId.
     *
     * @param chatId идентификатор чата
     * @return кэшированный ответ или {@code null}, если записи нет
     * @implNote Нужно, чтобы чтение могло обслуживаться локальным near-cache без сетевого round-trip.
     */
    @Override
    public ListLinksResponse get(long chatId) {
        String key = LinksListCacheKeys.byChatId(chatId);
        byte[] payload = cacheFrontend.get(key);
        return deserialize(payload);
    }

    /**
     * Сохраняет список ссылок в локальном near-cache и удаленном Valkey.
     *
     * @param chatId идентификатор чата
     * @param response ответ списка ссылок
     * @implNote Нужно, чтобы после записи последующие чтения использовали near-cache и быстрее отдавали ответ.
     */
    @Override
    public void put(long chatId, ListLinksResponse response) {
        String key = LinksListCacheKeys.byChatId(chatId);
        byte[] payload = JSON_SERIALIZER.serialize(response);
        if (payload == null) {
            return;
        }
        nearCache.put(key, payload);
        connection.sync().set(key, payload, SetArgs.Builder.px(ttl.toMillis()));
    }

    /**
     * Удаляет запись из near-cache и Valkey по chatId.
     *
     * @param chatId идентификатор чата
     * @implNote Нужно, чтобы после мутации данных список пересчитывался из источника истины, а не из устаревшего
     *     кэша.
     */
    @Override
    public void evict(long chatId) {
        String key = LinksListCacheKeys.byChatId(chatId);
        nearCache.evict(key);
        connection.sync().del(key);
    }

    /**
     * Очищает локальный near-cache.
     *
     * @implNote Нужно, чтобы быстро сбросить локальное состояние без full-scan удаленного кэша.
     */
    @Override
    public void clear() {
        nearCache.clear();
    }

    /**
     * Освобождает ресурсы кэша и соединения.
     *
     * @implNote Нужно, чтобы корректно завершать работу и не оставлять открытые сетевые ресурсы.
     */
    @Override
    public void close() {
        clear();
        cacheFrontend.close();
        connection.close();
    }

    /**
     * Переинициализирует tracking после восстановления соединения.
     *
     * @implNote Нужно, потому что сервер после реконнекта теряет client-tracking состояние конкретного соединения.
     */
    private void handleRedisConnected() {
        // После реконнекта сервер теряет tracking-состояние клиента, поэтому пересобираем near-cache и tracking.
        nearCache.clear();
        CompletableFuture<?> future =
                connection.async().clientTracking(trackingArgs).toCompletableFuture();
        future.exceptionally(exception -> {
            LOGGER.warn("Unable to re-enable client tracking after reconnect", exception);
            return null;
        });
    }

    /**
     * Сбрасывает локальный near-cache при потере соединения.
     *
     * @implNote Нужно, чтобы исключить выдачу потенциально устаревших данных в период переподключения.
     */
    private void handleRedisDisconnected() {
        nearCache.clear();
    }

    /**
     * Десериализует бинарный payload в API-ответ.
     *
     * @param payload сериализованное значение из кэша
     * @return десериализованный ответ или {@code null}
     * @implNote Нужно, чтобы безопасно обрабатывать поврежденный/неожиданный формат без выброса исключения наружу.
     */
    private static ListLinksResponse deserialize(byte[] payload) {
        if (payload == null) {
            return null;
        }
        Object value = JSON_SERIALIZER.deserialize(payload);
        if (value instanceof ListLinksResponse response) {
            return response;
        }
        return null;
    }

    private final class ReconnectTrackingListener extends RedisConnectionStateAdapter {

        /**
         * Обрабатывает восстановление соединения и включает tracking повторно.
         *
         * @param connection подключившееся соединение
         * @param socketAddress адрес удаленного узла
         * @implNote Нужно, чтобы после реконнекта восстановить серверные инвалидации near-cache.
         */
        @Override
        public void onRedisConnected(RedisChannelHandler<?, ?> connection, SocketAddress socketAddress) {
            handleRedisConnected();
        }

        /**
         * Обрабатывает потерю соединения и очищает локальный near-cache.
         *
         * @param connection отключенное соединение
         * @implNote Нужно, чтобы не держать потенциально устаревшие данные, пока соединение недоступно.
         */
        @Override
        public void onRedisDisconnected(RedisChannelHandler<?, ?> connection) {
            handleRedisDisconnected();
        }
    }
}
