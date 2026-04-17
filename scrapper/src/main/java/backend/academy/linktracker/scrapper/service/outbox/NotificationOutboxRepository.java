package backend.academy.linktracker.scrapper.service.outbox;

import backend.academy.linktracker.scrapper.dto.LinkUpdate;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Репозиторий хранения сообщений Transactional Outbox.
 */
@Repository
public class NotificationOutboxRepository {

    private static final org.springframework.jdbc.core.RowMapper<OutboxNotificationMessage> OUTBOX_ROW_MAPPER =
            NotificationOutboxRepository::mapOutboxRow;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    /**
     * Создает репозиторий outbox-сообщений.
     *
     * @param jdbcTemplate JDBC-шаблон
     * @param objectMapper JSON-сериализатор
     */
    public NotificationOutboxRepository(JdbcTemplate jdbcTemplate, ObjectProvider<ObjectMapper> objectMapperProvider) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapperProvider.getIfAvailable(
                () -> JsonMapper.builder().findAndAddModules().build());
    }

    /**
     * Добавляет сообщение в outbox.
     *
     * @param update payload уведомления
     */
    public void enqueue(LinkUpdate update) {
        try {
            String payloadJson = objectMapper.writeValueAsString(update);
            jdbcTemplate.update(
                    "insert into notification_outbox(payload, processed_at) values (cast(? as jsonb), null)",
                    payloadJson);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to enqueue notification message to outbox", exception);
        }
    }

    /**
     * Возвращает батч еще не обработанных outbox-сообщений.
     *
     * @param limit максимальный размер батча
     * @return список сообщений для публикации
     */
    @Transactional
    public List<OutboxNotificationMessage> fetchUnprocessedBatch(int limit) {
        return jdbcTemplate.query("""
                select id, payload::text as payload
                from notification_outbox
                where processed_at is null
                order by id
                limit ?
                for update skip locked
                """, OUTBOX_ROW_MAPPER, limit);
    }

    /**
     * Помечает outbox-сообщение как обработанное.
     *
     * @param outboxMessageId идентификатор outbox-записи
     */
    public void markProcessed(long outboxMessageId) {
        jdbcTemplate.update(
                "update notification_outbox set processed_at = now() where id = ? and processed_at is null",
                outboxMessageId);
    }

    private static OutboxNotificationMessage mapOutboxRow(ResultSet resultSet, int rowNumber) throws SQLException {
        return new OutboxNotificationMessage(resultSet.getLong("id"), resultSet.getString("payload"));
    }
}
