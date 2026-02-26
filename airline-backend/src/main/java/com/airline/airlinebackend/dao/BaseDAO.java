package com.airline.airlinebackend.dao;

import com.airline.airlinebackend.config.DatasourceConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;

public abstract class BaseDAO {
    protected final Logger log = LoggerFactory.getLogger(getClass());

    protected Connection getConnection() throws SQLException {
        return DatasourceConfig.getConnection();
    }

    @FunctionalInterface
    protected interface DatabaseOperation<T> {
        T execute(Connection conn) throws SQLException;
    }

    /**
     * Execute database operation with automatic connection management
     */
    protected <T> T executeWithConnection(DatabaseOperation<T> operation) {
        try(Connection conn = getConnection()) {
            return operation.execute(conn);
        } catch (SQLException e) {
            log.error("Database operation failed", e);
            throw new RuntimeException("Database operation failed",e);
        }
    }

    /**
     * Execute operation with transaction
     */
    protected <T> T executeWithTransaction(DatabaseOperation<T> operation) {
        Connection conn = null;

        try {
            conn = getConnection();
            conn.setAutoCommit(false);

            T result = operation.execute(conn);

            conn.commit();
            return result;
        } catch (SQLException e) {
            if(conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException e1) {
                    log.error("Failed to rollback transaction", e1);
                }
            }
            log.error("Transaction failed", e);
            throw new RuntimeException("Transaction failed",e);
        } finally {
            if(conn != null) {
                try {
                    conn.setAutoCommit(true);
                    conn.close();
                } catch (SQLException e) {
                    log.error("Failed to close connection", e);
                }
            }
        }
    }
}
