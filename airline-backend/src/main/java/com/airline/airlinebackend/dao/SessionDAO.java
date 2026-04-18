package com.airline.airlinebackend.dao;

import com.airline.airlinebackend.model.Session;
import com.airline.airlinebackend.util.JsonUtil;


import java.sql.*;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class SessionDAO extends BaseDAO {
    private static final String INSERT_SESSION = """
            INSERT INTO sessions (id, user_id, session_data, ip_address, user_agent,
             created_at, last_accessed_at, expires_at)
             VALUES (?, ?, ?::jsonb, ?, ?, ?, ?, ?)
            """;

    private static final String SELECT_BY_ID = """
            SELECT id, user_id, session_data, ip_address, user_agent,
             created_at, last_accessed_at, expires_at FROM sessions WHERE id = ?
            """;

    private static final String UPDATE_SESSION = """
            UPDATE sessions SET session_data = ?::jsonb, last_accessed_at = ?, expires_at = ?
            WHERE id = ?
            """;

    private static final String UPDATE_LAST_ACCESS = """
            UPDATE sessions SET last_accessed_at = ?, expires_at = ?
            WHERE id = ?
            """;

    private static final String DELETE_SESSION = """
            DELETE FROM sessions WHERE id = ?
            """;

    private static final String DELETE_BY_USER_ID = """
            DELETE FROM sessions WHERE user_id = ?
            """;

    private static final String DELETE_EXPIRED = """
            DELETE FROM sessions WHERE expires_at < CURRENT_TIMESTAMP
            """;

    public Session save(Session session) {
        return executeWithConnection(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(INSERT_SESSION)) {
                ps.setString(1, session.getId());
                ps.setObject(2, session.getUserId());
                ps.setString(3, JsonUtil.toJson(session.getSessionData()));
                ps.setString(4, session.getIpAddress());
                ps.setString(5, session.getUserAgent());
                ps.setTimestamp(6, Timestamp.from(session.getCreatedAt()));
                ps.setTimestamp(7, Timestamp.from(session.getLastAccessedAt()));
                ps.setTimestamp(8, Timestamp.from(session.getExpiresAt()));

                ps.execute();
                log.debug("Session created: {}", session.getId());
            }
            return session;
        });
    }

    public Optional<Session> findById(String id) {
        return executeWithConnection(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(SELECT_BY_ID)) {
                ps.setString(1, id);

                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                       return Optional.of(mapResultSetToSession(rs));
                    }
                    return Optional.empty();
                }
            }
        });
    }

    public void update(Session session) {
        executeWithConnection(conn -> {
            try(PreparedStatement ps = conn.prepareStatement(UPDATE_SESSION)) {
                ps.setString(1, JsonUtil.toJson(session.getSessionData()));
                ps.setTimestamp(2, Timestamp.from(session.getLastAccessedAt()));
                ps.setTimestamp(3, Timestamp.from(session.getExpiresAt()));
                ps.setString(4, session.getId());

                ps.executeUpdate();
                log.debug("Session updated: {}", session.getId());
                return null;
            }
        });
    }

    public void updateLastAccessed(String id, Instant lastAccess, Instant expires) {
        executeWithConnection(conn -> {
            try(PreparedStatement ps = conn.prepareStatement(UPDATE_LAST_ACCESS)) {
                ps.setTimestamp(1, Timestamp.from(lastAccess));
                ps.setTimestamp(2, Timestamp.from(expires));
                ps.setString(3, id);

                ps.executeUpdate();
                log.debug("Session updated: {}", id);
                return null;
            }
        });
    }

    public void delete(String id) {
        executeWithConnection(conn -> {
            try(PreparedStatement ps = conn.prepareStatement(DELETE_SESSION)) {
                ps.setString(1, id);
                ps.executeUpdate();
                log.debug("Session deleted: {}", id);
                return null;
            }
        });
    }

    public void deleteByUserId(UUID userId) {
        executeWithConnection(conn -> {
            try(PreparedStatement ps = conn.prepareStatement(DELETE_BY_USER_ID)) {
                ps.setObject(1, userId);
                int deleted = ps.executeUpdate();
                log.debug("Deleted {} sessions by user: {}", deleted, userId);
                return null;
            }
        });
    }

    public int deleteExpired() {
        return executeWithConnection(conn -> {
            try(Statement stat = conn.createStatement()) {
                int deleted = stat.executeUpdate(DELETE_EXPIRED);
                if (deleted > 0) {
                    log.debug("Deleted {} expired sessions", deleted);
                }
                return deleted;
            }
        });
    }

    @SuppressWarnings("unchecked")
    private Session mapResultSetToSession(ResultSet rs) throws SQLException {
        Session session = new Session();

        session.setId(rs.getString("id"));
        session.setUserId(rs.getObject("user_id", UUID.class));

        String sessionJson = rs.getString("session_data");
        Map<String, Object> sessionData = JsonUtil.fromJson(sessionJson, Map.class);
        session.setSessionData(sessionData);

        session.setIpAddress(rs.getString("ip_address"));
        session.setUserAgent(rs.getString("user_agent"));
        session.setCreatedAt(rs.getTimestamp("created_at").toInstant());
        session.setLastAccessedAt(rs.getTimestamp("last_accessed_at").toInstant());
        session.setExpiresAt(rs.getTimestamp("expires_at").toInstant());

        return session;
    }

}
