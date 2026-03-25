package backend.academy.linktracker.scrapper.configuration;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
@Tag("integration")
@SuppressWarnings("resource")
class DatabaseMigrationRunnerIntegrationTest {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17")
            .withDatabaseName("link_tracker_migration_test")
            .withUsername("postgres")
            .withPassword("postgres");

    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName(POSTGRES.getDriverClassName());
        dataSource.setUrl(POSTGRES.getJdbcUrl());
        dataSource.setUsername(POSTGRES.getUsername());
        dataSource.setPassword(POSTGRES.getPassword());

        jdbcTemplate = new JdbcTemplate(dataSource);

        jdbcTemplate.execute("drop schema if exists public cascade");
        jdbcTemplate.execute("create schema public");

        new DatabaseMigrationRunner(dataSource);
    }

    // Проверяет, что миграции применяются к чистой БД и фиксируются в databasechangelog.
    @Test
    void givenCleanDatabaseWhenMigrateThenSchemaIsCreated() {
        assertAll(
                () -> assertTrue(tableExists("chats")),
                () -> assertTrue(tableExists("tracked_links")),
                () -> assertTrue(tableExists("tracked_link_tags")),
                () -> assertTrue(tableExists("tracked_link_filters")),
                () -> assertTrue(liquibaseChangesetApplied("001-init-schema", "link-tracker")));
    }

    private boolean tableExists(String tableName) {
        Boolean exists = jdbcTemplate.queryForObject("""
                select exists(
                    select 1
                    from information_schema.tables
                    where table_schema = 'public'
                      and table_name = ?
                )
                """, Boolean.class, tableName);
        return Boolean.TRUE.equals(exists);
    }

    private boolean liquibaseChangesetApplied(String id, String author) {
        Boolean exists = jdbcTemplate.queryForObject("""
                select exists(
                    select 1
                    from public.databasechangelog
                    where id = ?
                      and author = ?
                )
                """, Boolean.class, id, author);
        return Boolean.TRUE.equals(exists);
    }
}
