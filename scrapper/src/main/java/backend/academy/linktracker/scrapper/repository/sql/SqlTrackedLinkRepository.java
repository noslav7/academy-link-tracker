package backend.academy.linktracker.scrapper.repository.sql;

import backend.academy.linktracker.scrapper.domain.TrackedLink;
import backend.academy.linktracker.scrapper.repository.AbstractPagingAwareTrackedLinkRepository;
import backend.academy.linktracker.scrapper.repository.TrackedLinkRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Низкоуровневая SQL-реализация репозитория отслеживаемых ссылок.
 */
@Repository
@ConditionalOnProperty(prefix = "app.database", name = "access-type", havingValue = "SQL", matchIfMissing = true)
public class SqlTrackedLinkRepository extends AbstractPagingAwareTrackedLinkRepository
        implements TrackedLinkRepository {

    private static final RowMapper<LinkRow> LINK_ROW_MAPPER = SqlTrackedLinkRepository::mapLinkRow;
    private static final String INSERT_TRACKED_LINK_TAG = """
            insert into tracked_link_tags(tracked_link_id, tag)
            values (?, ?)
            on conflict do nothing
            """;
    private static final String INSERT_TRACKED_LINK_FILTER = """
            insert into tracked_link_filters(tracked_link_id, filter_value)
            values (?, ?)
            on conflict do nothing
            """;
    private static final Map<String, String> INSERT_SQL_BY_TABLE_AND_COLUMN = Map.of(
            "tracked_link_tags:tag", INSERT_TRACKED_LINK_TAG,
            "tracked_link_filters:filter_value", INSERT_TRACKED_LINK_FILTER);

    private final JdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    public SqlTrackedLinkRepository(JdbcTemplate jdbcTemplate, NamedParameterJdbcTemplate namedParameterJdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
    }

    @Override
    @Transactional
    public boolean registerChat(long chatId) {
        int updated = jdbcTemplate.update("insert into chats(chat_id) values (?) on conflict do nothing", chatId);
        return updated > 0;
    }

    @Override
    @Transactional
    public boolean deleteChat(long chatId) {
        return jdbcTemplate.update("delete from chats where chat_id = ?", chatId) > 0;
    }

    @Override
    public boolean chatExists(long chatId) {
        Boolean exists = jdbcTemplate.queryForObject(
                "select exists(select 1 from chats where chat_id = ?)", Boolean.class, chatId);
        return Boolean.TRUE.equals(exists);
    }

    @Override
    public Optional<TrackedLink> findByChatIdAndUrl(long chatId, String url) {
        List<LinkRow> rows = jdbcTemplate.query("""
                select id, chat_id, url, last_checked_at, last_updated_at
                from tracked_links
                where chat_id = ? and url = ?
                """, LINK_ROW_MAPPER, chatId, url);
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        LinkRow row = rows.getFirst();
        return Optional.of(
                toDomain(row, tagsByTrackedLinkIds(List.of(row.id())), filtersByTrackedLinkIds(List.of(row.id()))));
    }

    @Override
    @Transactional
    public TrackedLink save(long chatId, String url, List<String> tags, List<String> filters, Instant updatedAt) {
        Instant now = Instant.now();
        Long id = jdbcTemplate.queryForObject(
                """
                insert into tracked_links(chat_id, url, last_checked_at, last_updated_at)
                values (?, ?, ?, ?)
                returning id
                """, Long.class, chatId, url, Timestamp.from(now), Timestamp.from(updatedAt));
        if (id == null) {
            throw new IllegalStateException("Failed to save tracked link");
        }

        batchInsertValues("tracked_link_tags", "tag", id, tags);
        batchInsertValues("tracked_link_filters", "filter_value", id, filters);
        return new TrackedLink(id, chatId, url, List.copyOf(tags), List.copyOf(filters), now, updatedAt);
    }

    @Override
    @Transactional
    public boolean deleteByChatIdAndUrl(long chatId, String url) {
        return jdbcTemplate.update("delete from tracked_links where chat_id = ? and url = ?", chatId, url) > 0;
    }

    @Override
    public List<TrackedLink> findAllByChatId(long chatId) {
        return findAllByChatId(chatId, Integer.MAX_VALUE, 0);
    }

    @Override
    public List<TrackedLink> findAllByChatId(long chatId, int limit, int offset) {
        if (hasInvalidPaging(limit, offset)) {
            return List.of();
        }
        List<LinkRow> rows = jdbcTemplate.query("""
                select id, chat_id, url, last_checked_at, last_updated_at
                from tracked_links
                where chat_id = ?
                order by id
                limit ? offset ?
                """, LINK_ROW_MAPPER, chatId, limit, offset);
        return toTrackedLinks(rows);
    }

    @Override
    public Set<Long> findAllChatIds() {
        return Set.copyOf(jdbcTemplate.query(
                "select chat_id from chats order by chat_id", (rs, rowNum) -> rs.getLong("chat_id")));
    }

    @Override
    public List<TrackedLink> findAllTrackedLinks(int limit, int offset) {
        if (hasInvalidPaging(limit, offset)) {
            return List.of();
        }
        List<LinkRow> rows = jdbcTemplate.query("""
                select id, chat_id, url, last_checked_at, last_updated_at
                from tracked_links
                order by id
                limit ? offset ?
                """, LINK_ROW_MAPPER, limit, offset);
        return toTrackedLinks(rows);
    }

    @Override
    @Transactional
    public void updateTrackedLink(TrackedLink trackedLink) {
        jdbcTemplate.update(
                """
                update tracked_links
                set last_checked_at = ?, last_updated_at = ?
                where id = ?
                """,
                Timestamp.from(trackedLink.lastCheckedAt()),
                Timestamp.from(trackedLink.lastUpdatedAt()),
                trackedLink.id());
    }

    @Override
    public List<String> findTagsByChatIdAndUrl(long chatId, String url) {
        Long trackedLinkId = trackedLinkId(chatId, url);
        if (trackedLinkId == null) {
            return List.of();
        }
        return jdbcTemplate.query(
                "select tag from tracked_link_tags where tracked_link_id = ? order by tag",
                (rs, rowNum) -> rs.getString("tag"),
                trackedLinkId);
    }

    @Override
    @Transactional
    public boolean addTag(long chatId, String url, String tag) {
        Long trackedLinkId = trackedLinkId(chatId, url);
        if (trackedLinkId == null) {
            return false;
        }
        return jdbcTemplate.update(
                        "insert into tracked_link_tags(tracked_link_id, tag) values (?, ?) on conflict do nothing",
                        trackedLinkId,
                        tag)
                > 0;
    }

    @Override
    @Transactional
    public boolean updateTag(long chatId, String url, String oldTag, String newTag) {
        Long trackedLinkId = trackedLinkId(chatId, url);
        if (trackedLinkId == null) {
            return false;
        }
        if (oldTag.equals(newTag)) {
            return true;
        }
        Boolean newTagExists = jdbcTemplate.queryForObject(
                "select exists(select 1 from tracked_link_tags where tracked_link_id = ? and tag = ?)",
                Boolean.class,
                trackedLinkId,
                newTag);
        if (Boolean.TRUE.equals(newTagExists)) {
            return false;
        }
        return jdbcTemplate.update(
                        "update tracked_link_tags set tag = ? where tracked_link_id = ? and tag = ?",
                        newTag,
                        trackedLinkId,
                        oldTag)
                > 0;
    }

    @Override
    @Transactional
    public boolean deleteTag(long chatId, String url, String tag) {
        Long trackedLinkId = trackedLinkId(chatId, url);
        if (trackedLinkId == null) {
            return false;
        }
        return jdbcTemplate.update(
                        "delete from tracked_link_tags where tracked_link_id = ? and tag = ?", trackedLinkId, tag)
                > 0;
    }

    private List<TrackedLink> toTrackedLinks(List<LinkRow> rows) {
        if (rows.isEmpty()) {
            return List.of();
        }
        List<Long> ids = rows.stream().map(LinkRow::id).toList();
        Map<Long, List<String>> tagsById = tagsByTrackedLinkIds(ids);
        Map<Long, List<String>> filtersById = filtersByTrackedLinkIds(ids);

        List<TrackedLink> links = new ArrayList<>(rows.size());
        for (LinkRow row : rows) {
            links.add(toDomain(row, tagsById, filtersById));
        }
        return links;
    }

    private TrackedLink toDomain(LinkRow row, Map<Long, List<String>> tagsById, Map<Long, List<String>> filtersById) {
        return new TrackedLink(
                row.id(),
                row.chatId(),
                row.url(),
                tagsById.getOrDefault(row.id(), List.of()),
                filtersById.getOrDefault(row.id(), List.of()),
                row.lastCheckedAt(),
                row.lastUpdatedAt());
    }

    private Map<Long, List<String>> tagsByTrackedLinkIds(List<Long> trackedLinkIds) {
        return valuesByTrackedLinkIds("tracked_link_tags", "tag", trackedLinkIds);
    }

    private Map<Long, List<String>> filtersByTrackedLinkIds(List<Long> trackedLinkIds) {
        return valuesByTrackedLinkIds("tracked_link_filters", "filter_value", trackedLinkIds);
    }

    private Map<Long, List<String>> valuesByTrackedLinkIds(
            String table, String valueColumn, List<Long> trackedLinkIds) {
        if (trackedLinkIds.isEmpty()) {
            return Map.of();
        }

        Map<Long, List<String>> values = new LinkedHashMap<>();
        MapSqlParameterSource params = new MapSqlParameterSource("ids", trackedLinkIds);
        List<Map<String, Object>> rows = namedParameterJdbcTemplate.queryForList(
                "select tracked_link_id, " + valueColumn + " from " + table
                        + " where tracked_link_id in (:ids) order by tracked_link_id",
                params);
        for (Map<String, Object> row : rows) {
            Long trackedLinkId = ((Number) row.get("tracked_link_id")).longValue();
            String value = (String) row.get(valueColumn);
            values.computeIfAbsent(trackedLinkId, key -> new ArrayList<>()).add(value);
        }
        return values;
    }

    private void batchInsertValues(String table, String valueColumn, long trackedLinkId, List<String> values) {
        if (values == null || values.isEmpty()) {
            return;
        }
        String tableAndColumn = table + ":" + valueColumn;
        String insertSql = INSERT_SQL_BY_TABLE_AND_COLUMN.get(tableAndColumn);
        if (insertSql == null) {
            throw new IllegalArgumentException("Unsupported table/value column pair");
        }
        jdbcTemplate.batchUpdate(insertSql, values, values.size(), (ps, value) -> {
            ps.setLong(1, trackedLinkId);
            ps.setString(2, value);
        });
    }

    private Long trackedLinkId(long chatId, String url) {
        List<Long> ids = jdbcTemplate.query(
                "select id from tracked_links where chat_id = ? and url = ?",
                (rs, rowNum) -> rs.getLong("id"),
                chatId,
                url);
        return ids.isEmpty() ? null : ids.getFirst();
    }

    // `rowNum` требуется сигнатурой RowMapper, но в этом маппере не используется.
    @SuppressWarnings("PMD.UnusedFormalParameter")
    private static LinkRow mapLinkRow(ResultSet rs, int rowNum) throws SQLException {
        return new LinkRow(
                rs.getLong("id"),
                rs.getLong("chat_id"),
                rs.getString("url"),
                rs.getTimestamp("last_checked_at").toInstant(),
                rs.getTimestamp("last_updated_at").toInstant());
    }

    private record LinkRow(long id, long chatId, String url, Instant lastCheckedAt, Instant lastUpdatedAt) {}
}
