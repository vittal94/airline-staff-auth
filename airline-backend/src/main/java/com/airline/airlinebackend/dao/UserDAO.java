package com.airline.airlinebackend.dao;

import com.airline.airlinebackend.model.User;
import com.airline.airlinebackend.model.emums.Role;
import com.airline.airlinebackend.model.emums.UserStatus;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class UserDAO extends BaseDAO {
    private static final String INSERT_USER = """
        INSERT INTO users (id, email, password_hash, name, role, status, 
                          password_change_required, first_login_completed, created_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;

    private static final String SELECT_BY_ID = """
        SELECT id, email, password_hash, name, role, status, 
               password_change_required, first_login_completed, 
               created_at, updated_at, last_login_at
        FROM users WHERE id = ?
        """;

    private static final String SELECT_BY_EMAIL = """
        SELECT id, email, password_hash, name, role, status, 
               password_change_required, first_login_completed, 
               created_at, updated_at, last_login_at
        FROM users WHERE LOWER(email) = LOWER(?)
        """;

    private static final String SELECT_ALL = """
            SELECT id, email, password_hash, name, role, status, 
               password_change_required, first_login_completed, 
               created_at, updated_at, last_login_at
            FROM users ORDER BY created_at DESC
            """;

    private static final String SELECT_BY_STATUS = """
            SELECT id, email, password_hash, name, role, status, 
               password_change_required, first_login_completed, 
               created_at, updated_at, last_login_at
               FROM users WHERE status = ? ORDER BY created_at DESC
            """;

    private static final String UPDATE_USER = """
            UPDATE users SET email = ?, name = ?, role = ? , status = ?,
            password_change_required = ?, first_login_completed = ?,
            updated_at = ?
            WHERE id = ?
            """;

    private static final String UPDATE_PASSWORD = """
            UPDATE users SET password_hash = ?, password_change_required = ?,
            updated_at = ?
            WHERE id = ?
            """;

    private static final String UPDATE_STATUS = """
            UPDATE users SET status = ?, updated_at = ? WHERE id = ?
            """;

    private static final String UPDATE_LAST_LOGIN = """
            UPDATE users SET last_login_at = ?, updated_at = ? WHERE id = ?
            """;

    private static final String DELETE_USER = """
            DELETE FROM users WHERE id = ?
            """;

    private static final String EXISTS_BY_EMAIL = """
            SELECT EXISTS(SELECT 1 FROM users WHERE LOWER(email) = LOWER(?))
            """;

    private static final String COUNT_BY_STATUS = """
            SELECT COUNT(*) FROM users WHERE status = ?
            """;

    private static final String COUNT_ADMINS = """
            SELECT COUNT(*) FROM users WHERE role = 'ADMIN' AND status != 'BLOCKED'
            """;

    public User save(User user) {
        return executeWithConnection(conn -> {
            if(user.getId() == null) user.setId(UUID.randomUUID());
            if(user.getCreatedAt() == null) user.setCreatedAt(Instant.now());

            try(PreparedStatement ps = conn.prepareStatement(INSERT_USER)) {
                ps.setObject(1,user.getId());
                ps.setString(2,user.getEmail());
                ps.setString(3,user.getPasswordHash());
                ps.setString(4,user.getName());
                ps.setString(5,user.getRole().getValue());
                ps.setString(6,user.getStatus().getValue());
                ps.setBoolean(7,user.isPasswordChangeRequired());
                ps.setBoolean(8,user.isFirstLoginCompleted());
                ps.setTimestamp(9, Timestamp.from(user.getCreatedAt()));

                ps.executeUpdate();
                log.debug("Created user: {}", user.getEmail());
                return user;
            }
        });
    }

    public Optional<User> findById(UUID id) {
        return executeWithConnection(conn -> {
            try(PreparedStatement ps = conn.prepareStatement(SELECT_BY_ID)) {
                ps.setObject(1, id);
                try(ResultSet rs = ps.executeQuery()) {
                    if(rs.next()) {
                        return Optional.of(mapResultSetToUser(rs));
                    }
                    return Optional.empty();
                }
            }
        });
    }

    public Optional<User> findByEmail(String email) {
        return executeWithConnection(conn -> {
            try(PreparedStatement ps = conn.prepareStatement(SELECT_BY_EMAIL)) {
                ps.setString(1, email);
                try(ResultSet rs = ps.executeQuery()) {
                    if(rs.next()) {
                        return Optional.of(mapResultSetToUser(rs));
                    }
                    return Optional.empty();
                }
            }
        });
    }

    public List<User> findAll() {
        return executeWithConnection(conn -> {
            try(PreparedStatement ps = conn.prepareStatement(SELECT_ALL)) {
                List<User> users = new ArrayList<>();
                try(ResultSet rs = ps.executeQuery()) {
                    while(rs.next()) {
                        users.add(mapResultSetToUser(rs));
                    }
                    return users;
                }
            }
        });
    }

    public List<User> findByStatus(UserStatus status) {
        return executeWithConnection(conn -> {
            List<User> users = new ArrayList<>();
            try(PreparedStatement ps = conn.prepareStatement(SELECT_BY_STATUS)) {
                ps.setString(1, status.getValue());
                try(ResultSet rs = ps.executeQuery()) {
                    while(rs.next()) {
                        users.add(mapResultSetToUser(rs));
                    }
                    return users;
                }
            }
        });
    }

    public User update(User user) {
        return executeWithConnection(conn -> {
            user.setUpdatedAt(Instant.now());

            try(PreparedStatement ps = conn.prepareStatement(UPDATE_USER)) {
                ps.setString(1,user.getEmail());
                ps.setString(2,user.getName());
                ps.setString(3,user.getRole().getValue());
                ps.setString(4,user.getStatus().getValue());
                ps.setBoolean(5,user.isPasswordChangeRequired());
                ps.setBoolean(6,user.isFirstLoginCompleted());
                ps.setTimestamp(7,Timestamp.from(user.getUpdatedAt()));
                ps.setObject(8,user.getId());

                ps.executeUpdate();
                log.debug("Updated user: {}", user.getEmail());
                return user;
            }
        });
    }

    public void update_password(UUID id, String passwordHash, boolean changeRequired) {
        executeWithConnection(conn -> {
            try(PreparedStatement ps = conn.prepareStatement(UPDATE_PASSWORD)) {
                ps.setString(1,passwordHash);
                ps.setBoolean(2,changeRequired);
                ps.setTimestamp(3,Timestamp.from(Instant.now()));
                ps.setObject(4,id);

                ps.executeUpdate();
                log.debug("Updated password for user: {}", id);
                return null;
            }
        });
    }

    public void update_status(UUID id, UserStatus status) {
        executeWithConnection(conn -> {
            try(PreparedStatement ps = conn.prepareStatement(UPDATE_STATUS)) {
                ps.setString(1,status.getValue());
                ps.setTimestamp(2,Timestamp.from(Instant.now()));
                ps.setObject(3,id);

                ps.executeUpdate();
                log.debug("Updated status for user: {} to {}", id, status);
                return null;
            }
        });
    }

    public void update_last_login(UUID id) {
        executeWithConnection(conn -> {
            try(PreparedStatement ps = conn.prepareStatement(UPDATE_LAST_LOGIN)) {
                Instant now = Instant.now();
                ps.setTimestamp(1,Timestamp.from(now));
                ps.setTimestamp(2,Timestamp.from(now));
                ps.setObject(3,id);

                ps.executeUpdate();
                return null;
            }
        });
    }

    public void delete(UUID id) {
        executeWithConnection(conn -> {
            try(PreparedStatement ps = conn.prepareStatement(DELETE_USER)) {
                ps.setObject(1,id);

                int affectedRows = ps.executeUpdate();
                log.debug("Deleted user: {}, affected rows {}", id, affectedRows);
                return null;
            }
        });
    }

    public boolean existsByEmail(String email) {
        return executeWithConnection(conn -> {
            try(PreparedStatement ps = conn.prepareStatement(EXISTS_BY_EMAIL)) {
                ps.setString(1,email);

                try(ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    return rs.getBoolean(1);
                }
            }
        });
    }

    public int countByStatus(UserStatus status) {
        return executeWithConnection(conn -> {
            try(PreparedStatement ps = conn.prepareStatement(COUNT_BY_STATUS)) {
                ps.setString(1,status.getValue());

                try(ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    return rs.getInt(1);
                }
            }
        });
    }

    public int countAdmins() {
        return executeWithConnection(conn -> {
            try(PreparedStatement ps = conn.prepareStatement(COUNT_ADMINS)) {
                try(ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    return rs.getInt(1);
                }
            }
        });
    }

    private User mapResultSetToUser(ResultSet rs) throws SQLException {
        User user = new User();
        user.setId(rs.getObject("id", UUID.class));
        user.setEmail(rs.getString("email"));
        user.setName(rs.getString("name"));
        user.setRole(Role.fromValue(rs.getString("role")));
        user.setPasswordHash(rs.getString("password_hash"));
        user.setStatus(UserStatus.fromValue(rs.getString("status")));
        user.setPasswordChangeRequired(rs.getBoolean("password_change_required"));
        user.setFirstLoginCompleted(rs.getBoolean("first_login_completed"));

        Timestamp createdAt = rs.getTimestamp("created_at");
        if(createdAt != null) {
            user.setCreatedAt(createdAt.toInstant());
        }

        Timestamp updatedAt = rs.getTimestamp("updated_at");
        if(updatedAt != null) {
            user.setUpdatedAt(updatedAt.toInstant());
        }

        Timestamp lastLoginAt = rs.getTimestamp("last_login_at");
        if(lastLoginAt != null) {
            user.setLastLoginAt(lastLoginAt.toInstant());
        }

        return user;
    }
}
