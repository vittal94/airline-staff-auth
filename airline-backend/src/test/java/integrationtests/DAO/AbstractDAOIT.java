package integrationtests.DAO;

import com.airline.airlinebackend.config.AppConfig;
import com.airline.airlinebackend.config.DatasourceConfig;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

@Testcontainers
public abstract class AbstractDAOIT {

    @Container
    protected static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:16-alpine")
                    .withDatabaseName("airline_tests")
                    .withUsername("test")
                    .withPassword("test")
                    .withReuse(true);

    private static final String DB_URL_PROP = "db.url";
    private static final String DB_USER_PROP = "db.user";
    private static final String DB_PASS_PROP = "db.pass";
    private static final String INITIAL_ADMIN_EMAIL_PROP = "initial_admin_email";

    @BeforeAll
    static void setUpConfig() throws Exception {
        resetStaticSingleton();

        System.setProperty(DB_URL_PROP, POSTGRES.getJdbcUrl());
        System.setProperty(DB_USER_PROP, POSTGRES.getUsername());
        System.setProperty(DB_PASS_PROP, POSTGRES.getPassword());
        System.setProperty(INITIAL_ADMIN_EMAIL_PROP, "admin.integration@airline.test");

        enablePgcryptoExtension();

        DatasourceConfig.runMigration();
    }

    @AfterAll
    static void tearDownConfig() throws Exception {
        DatasourceConfig.shutdown();
        System.clearProperty(DB_URL_PROP);
        System.clearProperty(DB_USER_PROP);
        System.clearProperty(DB_PASS_PROP);
        System.clearProperty(INITIAL_ADMIN_EMAIL_PROP);
}

    @BeforeEach
    void cleanDB() throws SQLException {
        try(Connection conn = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword());
            Statement stmt = conn.createStatement()) {
            stmt.execute("""
                 TRUNCATE TABLE 
                 login_attempts,
                 users,
                 email_confirmation_tokens,
                 remember_me_tokens,
                 sessions
                 RESTART IDENTITY CASCADE
                 """);
        }
    }

    private static void resetStaticSingleton() throws Exception {
        Field appConfigInstanceField = AppConfig.class.getDeclaredField("instance");
        appConfigInstanceField.setAccessible(true);
        appConfigInstanceField.set(null, null);

        Field dataSourceField = DatasourceConfig.class.getDeclaredField("dataSource");
        dataSourceField.setAccessible(true);
        dataSourceField.set(null, null);
    }

    private static void enablePgcryptoExtension() throws SQLException {
        try (Connection conn = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword());
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE EXTENSION IF NOT EXISTS pgcrypto");
        }
    }

}
