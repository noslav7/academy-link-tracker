package backend.academy.linktracker.scrapper.configuration;

import jakarta.annotation.PostConstruct;
import java.sql.Connection;
import javax.sql.DataSource;
import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.springframework.stereotype.Component;

/**
 * Программный запуск миграций Liquibase при старте приложения.
 */
@Component
public class DatabaseMigrationRunner {

    private static final String CHANGELOG_PATH = "migrations/master.xml";

    private final DataSource dataSource;

    public DatabaseMigrationRunner(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @PostConstruct
    public void migrate() {
        try (Connection connection = dataSource.getConnection()) {
            Database database =
                    DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection));
            try (Liquibase liquibase = new Liquibase(CHANGELOG_PATH, new ClassLoaderResourceAccessor(), database)) {
                liquibase.update(new Contexts(), new LabelExpression());
            }
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to apply database migrations", exception);
        }
    }
}
