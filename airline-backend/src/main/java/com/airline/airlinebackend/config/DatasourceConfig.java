package com.airline.airlinebackend.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;

public final class DatasourceConfig {

    private static final Logger log = LoggerFactory.getLogger(DatasourceConfig.class);
    private static volatile HikariDataSource dataSource;
    private DatasourceConfig() {
    }

    public static DataSource getDataSource() {
        if (dataSource == null) {
            synchronized (DatasourceConfig.class) {
                if (dataSource == null) {
                    dataSource = createDatasource();
                }
            }
        }
        return dataSource;
    }

    public static Connection getConnection() throws SQLException {
        return getDataSource().getConnection();
    }

    private static HikariDataSource createDatasource() {
        AppConfig config = AppConfig.getInstance();

        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl(config.getDbUrl());
        hikariConfig.setUsername(config.getDbUsername());
        hikariConfig.setPassword(config.getDbPassword());
        hikariConfig.setMaximumPoolSize(config.getDbPoolSize());
        hikariConfig.setMinimumIdle(config.getDbPoolMinIdle());
        hikariConfig.setIdleTimeout(300000); // 5 minutes
        hikariConfig.setMaxLifetime(600000); // 10 minutes
        hikariConfig.setConnectionTimeout(30000); // 30 seconds
        hikariConfig.setPoolName("AirlinePool");

        // Postgres optimizations
        hikariConfig.addDataSourceProperty("cachePrepStmts", "true");
        hikariConfig.addDataSourceProperty("prepStmtCacheSize", "250");
        hikariConfig.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        hikariConfig.addDataSourceProperty("useServerPrepStmts", "true");

        log.info("Creating Hikari connection pool for: {}", config.getDbUrl());
        return new HikariDataSource(hikariConfig);
    }

    public static void runMigration() {
        log.info("Migration for Flyway started...");

        AppConfig config = AppConfig.getInstance();

        Flyway flyway = Flyway.configure()
                .dataSource(config.getDbUrl(),config.getDbUsername(),config.getDbPassword())
                .locations("classpath:db/migration")
                .baselineOnMigrate(true) // if there is existing db with tables and data
                .placeholders(
                        Map.of("INITIAL_ADMIN_EMAIL",config.getInitialAdminEmail())
                ) // enables dynamically insert values in sql script
                .load();

        flyway.migrate();
        log.info("Migration complete successfully!.");
    }

    public static void shutdown() {
      if (dataSource != null && !dataSource.isClosed()) {
          log.info("Shutting down datasource...");
          dataSource.close();
      }
    }
}
