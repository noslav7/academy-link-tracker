package backend.academy.linktracker.scrapper.configuration;

import java.sql.Connection;
import javax.sql.DataSource;
import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Программный запуск миграций Liquibase при старте приложения.
 */
@Component
public class DatabaseMigrationRunner {

    private static final Logger log = LoggerFactory.getLogger(DatabaseMigrationRunner.class);
    private static final String CHANGELOG_PATH = "migrations/master.xml";

    /**
     * Создает раннер миграций и немедленно выполняет их.
     * Зависит только от {@code dataSource} — конструктор является минимально достаточным lifecycle-механизмом.
     *
     * @param dataSource источник подключений к БД
     */
    public DatabaseMigrationRunner(DataSource dataSource) {
        try (Connection connection = dataSource.getConnection()) {
            Database database =
                    DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection));
            try (Liquibase liquibase = new Liquibase(CHANGELOG_PATH, new ClassLoaderResourceAccessor(), database)) {
                liquibase.update(new Contexts(), new LabelExpression());
            }
        } catch (Exception exception) {
            log.atError()
                    .setCause(exception)
                    .addKeyValue("changelogPath", CHANGELOG_PATH)
                    .log("Failed to apply database migrations during startup");
            throw new IllegalStateException("Failed to apply database migrations", exception);
        }
    }
}
