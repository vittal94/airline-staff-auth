package com.airline.airlinebackend.dao;

import com.airline.airlinebackend.model.RememberMeToken;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public class RememberMeTokenDAO extends BaseDAO {

    private static final String INSERT_TOKEN = """
            INSERT INTO remember_me_tokens(series, user_id, token_hash,
            ip_address, user_agent, created_at, last_used_at, expires_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;
    private static final String SELECT_BY_SERIES = """
            SELECT series, user_id, token_hash, ip_address, user_agent, created_at, last_used_at, expires_at
            FROM remember_me_tokens WHERE series = ?
            """;
    private static final String UPDATE_TOKEN = """
            UPDATE remember_me_tokens SET token_hash = ?, last_used_at = ?, expires_at = ?
            WHERE series = ?
            """;
    private static final String DELETE_BY_SERIES = """
            DELETE FROM remember_me_tokens WHERE series = ?
            """;
    private static final String DELETE_BY_USER_ID = """
            DELETE FROM remember_me_tokens WHERE user_id = ?
            """;
    private static final String DELETE_EXPIRED = """
            DELETE FROM remember_me_tokens WHERE expires_at < now()
            """;

    public RememberMeToken save(RememberMeToken token) {
         return executeWithConnection(conn -> {
            try(PreparedStatement stmt = conn.prepareStatement(INSERT_TOKEN)) {
                stmt.setString(1, token.getSeries());
                stmt.setObject(2, token.getUserId());
                stmt.setString(3, token.getTokenHash());
                stmt.setString(4, token.getIpAddress());
                stmt.setString(5, token.getUserAgent());
                stmt.setTimestamp(6,Timestamp.from(token.getCreatedAt()));
                stmt.setTimestamp(7, Timestamp.from(token.getLastUsedAt()));
                stmt.setTimestamp(8, Timestamp.from(token.getExpiresAt()));
                stmt.executeUpdate();

                log.debug("Create rememberMeToken for user: {}", token.getUserId());
                return token;
            }
         });
    }

    public Optional<RememberMeToken> findBySeries(String series) {
        return executeWithConnection(conn -> {
            try(PreparedStatement stmt = conn.prepareStatement(SELECT_BY_SERIES)) {
                stmt.setString(1, series);
                try(ResultSet rs = stmt.executeQuery()) {
                    if(rs.next()) {
                        return Optional.of(mapResultSetToToken(rs));
                    }
                    return Optional.empty();
                }
            }
        });
    }

    public void update(String series, String newToken, Instant newExpiresAt) {
        executeWithConnection(conn -> {
            try(PreparedStatement stmt = conn.prepareStatement(UPDATE_TOKEN)) {
                stmt.setString(1, newToken);
                stmt.setTimestamp(2, Timestamp.from(Instant.now()));
                stmt.setTimestamp(3, Timestamp.from(newExpiresAt));
                stmt.setString(4, series);

                stmt.executeUpdate();
                return null;
            }
        });
    }

    public void deleteBySeries(String series) {
        executeWithConnection(conn -> {
            try(PreparedStatement stmt = conn.prepareStatement(DELETE_BY_SERIES)) {
                stmt.setString(1, series);

                stmt.executeUpdate();
                return null;
            }
        });
    }

    public void deleteByUserId(UUID userId) {
        executeWithConnection(conn -> {
            try(PreparedStatement stmt = conn.prepareStatement(DELETE_BY_USER_ID)) {
                stmt.setObject(1, userId);

                stmt.executeUpdate();
                return null;
            }
        });
    }

    public int deleteExpired() {
        return executeWithConnection(conn -> {
            try(PreparedStatement stmt = conn.prepareStatement(DELETE_EXPIRED)) {
                int count = stmt.executeUpdate();

                log.debug("Deletes expired rememberMeTokens: {}", count);
                return count;
            }
        });
    }

    private RememberMeToken mapResultSetToToken(ResultSet rs) throws SQLException {
        RememberMeToken token = new RememberMeToken();
        token.setSeries(rs.getString("series"));
        token.setUserId(rs.getObject("user_id",UUID.class));
        token.setTokenHash(rs.getString("token_hash"));
        token.setIpAddress(rs.getString("ip_address"));
        token.setUserAgent(rs.getString("user_agent"));
        token.setCreatedAt(rs.getTimestamp("created_at").toInstant());
        token.setLastUsedAt(rs.getTimestamp("last_used_at").toInstant());
        token.setExpiresAt(rs.getTimestamp("expires_at").toInstant());
        return token;
    }


}
