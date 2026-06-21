package com.airline.airlinebackend.dao;

import com.airline.airlinebackend.model.LoginAttempt;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;

public class LoginAttemptDAO extends BaseDAO {

    private final static String INSERT_ATTEMPT = """
            INSERT INTO login_attempts (email, ip_address, attempted_at, success)
            VALUES (?, ?, ?, ?);
            """;

    private final static String COUNT_FAILED_BY_EMAIL = """
            SELECT COUNT(*) FROM login_attempts
            WHERE email = ? AND success = FALSE AND attempted_at > ?;
            """;

    private final static String COUNT_FAILED_BY_IP = """
            SELECT COUNT(*) FROM login_attempts
            WHERE ip_address = ? AND success = FALSE AND attempted_at > ?;
            """;

    private final static String DELETE_OLD = """
            DELETE FROM login_attempts WHERE attempted_at < ?;
            """;

    public void save(LoginAttempt loginAttempt) {
        executeWithConnection(conn -> {
            try(PreparedStatement ps = conn.prepareStatement(INSERT_ATTEMPT)) {
                ps.setString(1, loginAttempt.getEmail());
                ps.setString(2, loginAttempt.getIpAddress());
                ps.setTimestamp(3,Timestamp.from(loginAttempt.getAttemptedAt()));
                ps.setBoolean(4, loginAttempt.isSuccess());

                ps.executeUpdate();
                return null;
            }
        });
    }

    public int countFailedAttemptsByEmail(String email, Instant since) {
        return executeWithConnection( conn -> {
            try(PreparedStatement ps = conn.prepareStatement(COUNT_FAILED_BY_EMAIL)) {
                ps.setString(1, email);
                ps.setTimestamp(2, Timestamp.from(since));

                try(ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    return rs.getInt(1);
                }
            }
        });
    }

    public int countFailedAttemptsByIpAddress(String ipAddress, Instant since) {
        return executeWithConnection( conn -> {
            try(PreparedStatement ps = conn.prepareStatement(COUNT_FAILED_BY_IP)) {
                ps.setString(1, ipAddress);
                ps.setTimestamp(2, Timestamp.from(since));

                try(ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    return rs.getInt(1);
                }
            }
        });
    }

    public int deleteOlderThen(Instant cutoff) {
        return executeWithConnection( conn -> {
            try(PreparedStatement ps = conn.prepareStatement(DELETE_OLD)) {
                ps.setTimestamp(1, Timestamp.from(cutoff));
                return ps.executeUpdate();
            }
        });
    }



}
