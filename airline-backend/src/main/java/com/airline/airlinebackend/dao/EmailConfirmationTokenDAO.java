package com.airline.airlinebackend.dao;

import com.airline.airlinebackend.model.EmailConfirmationToken;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public class EmailConfirmationTokenDAO extends BaseDAO {

    private static final String INSERT_TOKEN = """
            INSERT INTO email_confirmation_tokens (id, user_id, token_hash, created_at, expires_at)
            VALUES (?, ?, ?, ?, ?)
            """;

    private static final String SELECT_BY_TOKEN_HASH = """
            SELECT id, user_id, token_hash, created_at, expires_at, last_used
            FROM email_confirmation_tokens WHERE token_hash = ?
            """;

    private static final String MARK_USED = """
            UPDATE email_confirmation_tokens SET last_used = ? WHERE id = ?
            """;

    private static final String DELETE_BY_USER_ID = """
            DELETE FROM email_confirmation_tokens WHERE user_id = ?
            """;

    private static final String DELETE_EXPIRED = """
            DELETE FROM email_confirmation_tokens WHERE expires_at < CURRENT_TIMESTAMP
            """;

    public EmailConfirmationToken save(EmailConfirmationToken token) {
        return executeWithConnection(conn -> {
            try(PreparedStatement stmt = conn.prepareStatement(INSERT_TOKEN)) {
                stmt.setObject(1, token.getId());
                stmt.setObject(2, token.getUserId());
                stmt.setString(3, token.getTokenHash());
                stmt.setTimestamp(4, Timestamp.from(token.getCreatedAt()));
                stmt.setTimestamp(5, Timestamp.from(token.getExpiresAt()));

                stmt.executeUpdate();
                log.debug("Created email confirmation token for user: {}", token.getUserId());
                return token;
            }
        });
    }

    public Optional<EmailConfirmationToken> findByTokenHash(String tokenHash) {
        return executeWithConnection(conn -> {
            try(PreparedStatement stmt = conn.prepareStatement(SELECT_BY_TOKEN_HASH)) {
                stmt.setString(1, tokenHash);
                try(ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(mapResultSetToToken(rs));
                    } else {
                        return Optional.empty();
                    }
                }
            }
        });
    }

    public void markUsed(UUID tokenId) {
        executeWithConnection(conn -> {
            try(PreparedStatement stmt = conn.prepareStatement(MARK_USED)) {
                stmt.setTimestamp(1,Timestamp.from(Instant.now()));
                stmt.setObject(2, tokenId);
                stmt.executeUpdate();
                return null;
            }
        });
    }

    public void deleteByUserId(UUID tokenId) {
        executeWithConnection(conn -> {
            try(PreparedStatement stmt = conn.prepareStatement(DELETE_BY_USER_ID)) {
                stmt.setObject(1, tokenId);
                stmt.executeUpdate();
                return null;
            }
        });
    }

    public int deleteExpired() {
        return executeWithConnection(conn -> {
            try(PreparedStatement stmt = conn.prepareStatement(DELETE_EXPIRED)) {
                return stmt.executeUpdate();
            }
        });
    }

    private EmailConfirmationToken mapResultSetToToken(ResultSet rs) throws SQLException {
        EmailConfirmationToken token = new EmailConfirmationToken();
        token.setId(rs.getObject("id", UUID.class));
        token.setUserId(rs.getObject("user_id", UUID.class));
        token.setTokenHash(rs.getString("token_hash"));
        token.setExpiresAt(rs.getTimestamp("expires_at").toInstant());
        token.setCreatedAt(rs.getTimestamp("created_at").toInstant());

        Timestamp lastUsed = rs.getTimestamp("last_used");
        if (lastUsed != null) {
            token.setLastUsedAt(lastUsed.toInstant());
        }
        return token;
    }


}