import org.bouncycastle.crypto.generators.Argon2BytesGenerator;
import org.bouncycastle.crypto.params.Argon2Parameters;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.SecureRandom;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class PasswordTest {

    @Test
    public void generatePassword() {
        String hashPass = PasswordEncoderTest.encode("Admin@Initial123!");
        boolean isVerified = PasswordEncoderTest.verify(
                "initialPass!",
                "$argon2id$v=19$m=65536,t=3,p=4$7+6AJoMQYaWeBndc6YTcEw$ssZ3oYTNtPr4qspKA7TpRXmZ3uQjqygGFHL9QJwxpU4");
        System.out.println(hashPass);
        assertTrue(isVerified);
    }
    public static final class PasswordEncoderTest {

        private static final Logger logger = LoggerFactory.getLogger(PasswordEncoderTest.class);

        // Argon2id parameters (OWASP recommendations)
        private static final int MEMORY_COST = 65536; // 64 MB
        private static final int ITERATIONS = 3;
        private static final int PARALLELISM = 4;
        private static final int HASH_LENGTH = 32;
        private static final int SALT_LENGTH = 16;

        private static final SecureRandom secureRandom = new SecureRandom();

        private PasswordEncoderTest() {
            // Utility class
        }

        /**
         * Hashes a password using Argon2id.
         *
         * @param password Plain text password
         * @return Encoded hash string in format: $argon2id$v=19$m=65536,t=3,p=4$salt$hash
         */
        public static String encode(String password) {
            byte[] salt = new byte[SALT_LENGTH];
            secureRandom.nextBytes(salt);

            Argon2Parameters params = new Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
                    .withSalt(salt)
                    .withMemoryAsKB(MEMORY_COST)
                    .withIterations(ITERATIONS)
                    .withParallelism(PARALLELISM)
                    .build();

            Argon2BytesGenerator generator = new Argon2BytesGenerator();
            generator.init(params);

            byte[] hash = new byte[HASH_LENGTH];
            generator.generateBytes(password.toCharArray(), hash);

            Base64.Encoder encoder = Base64.getEncoder().withoutPadding();
            String saltBase64 = encoder.encodeToString(salt);
            String hashBase64 = encoder.encodeToString(hash);

            return String.format("$argon2id$v=19$m=%d,t=%d,p=%d$%s$%s",
                    MEMORY_COST, ITERATIONS, PARALLELISM, saltBase64, hashBase64);
        }

        /**
         * Verifies a password against an encoded hash.
         *
         * @param password Plain text password
         * @param encodedHash Previously encoded hash
         * @return true if password matches
         */
        public static boolean verify(String password, String encodedHash) {
            try {
                // Parse the encoded hash
                String[] parts = encodedHash.split("\\$");
                if (parts.length != 6 || !parts[1].equals("argon2id")) {
                    logger.warn("Invalid hash format");
                    return false;
                }

                // Parse parameters
                String[] paramParts = parts[3].split(",");
                int memory = Integer.parseInt(paramParts[0].substring(2)); // m=
                int iterations = Integer.parseInt(paramParts[1].substring(2)); // t=
                int parallelism = Integer.parseInt(paramParts[2].substring(2)); // p=

                Base64.Decoder decoder = Base64.getDecoder();
                byte[] salt = decoder.decode(parts[4]);
                byte[] expectedHash = decoder.decode(parts[5]);

                // Generate hash with same parameters
                Argon2Parameters params = new Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
                        .withSalt(salt)
                        .withMemoryAsKB(memory)
                        .withIterations(iterations)
                        .withParallelism(parallelism)
                        .build();

                Argon2BytesGenerator generator = new Argon2BytesGenerator();
                generator.init(params);

                byte[] actualHash = new byte[expectedHash.length];
                generator.generateBytes(password.toCharArray(), actualHash);

                // Constant-time comparison
                return constantTimeEquals(expectedHash, actualHash);
            } catch (Exception e) {
                logger.error("Error verifying password", e);
                return false;
            }
        }

        /**
         * Constant-time comparison to prevent timing attacks.
         */
        private static boolean constantTimeEquals(byte[] a, byte[] b) {
            if (a.length != b.length) {
                return false;
            }

            int result = 0;
            for (int i = 0; i < a.length; i++) {
                result |= a[i] ^ b[i];
            }
            return result == 0;
        }

        /**
         * Hashes a token (for storage - simpler than password hashing).
         */
        public static String hashToken(String token) {
            try {
                java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
                byte[] hash = digest.digest(token.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                return Base64.getEncoder().encodeToString(hash);
            } catch (java.security.NoSuchAlgorithmException e) {
                throw new RuntimeException("SHA-256 not available", e);
            }
        }

        /**
         * Verifies a token against its hash.
         */
        public static boolean verifyToken(String token, String hash) {
            String computedHash = hashToken(token);
            return constantTimeEquals(computedHash.getBytes(), hash.getBytes());
        }
    }
}
