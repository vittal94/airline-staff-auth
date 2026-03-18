package integrationtests.DAO.UserDao;

import com.airline.airlinebackend.config.DatasourceConfig;
import com.airline.airlinebackend.dao.UserDAO;
import com.airline.airlinebackend.model.User;
import com.airline.airlinebackend.model.emums.Role;
import com.airline.airlinebackend.model.emums.UserStatus;
import integrationtests.DAO.AbstractDAOIT;
import jakarta.activation.DataSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.sql.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.stream.IntStream;

import static integrationtests.DAO.UserDao.UserTestDataBuilder.*;
import static org.assertj.core.api.Assertions.*;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

@Tag("integration")
@DisplayName("UserDao integration tests")
public class UserDAOIT extends AbstractDAOIT {

    private UserDAO userDao;

    @BeforeEach
    void setUp() {
        userDao = new UserDAO();
    }

    @Nested
    @DisplayName("save()")
    class SaveTests {

        @Test
        @DisplayName("should persist user with all field and return it with generated id")
        void saveUserWithAllFields() {
           User user = aUser()
                   .withEmail("john@example.com")
                   .withName("John Doe")
                   .withPasswordHash("$2a$10$abc123")
                   .withRole(Role.FLIGHT_MANAGER)
                   .withStatus(UserStatus.ACTIVE)
                   .withPasswordChangeRequired(true)
                   .withFirstLoginCompleted(false)
                   .build();

           User saved = userDao.save(user);

           assertSoftly(softly -> {
               softly.assertThat(saved.getEmail()).isEqualTo("john@example.com");
               softly.assertThat(saved.getName()).isEqualTo("John Doe");
               softly.assertThat(saved.getPasswordHash()).isEqualTo("$2a$10$abc123");
               softly.assertThat(saved.getRole()).isEqualTo(Role.FLIGHT_MANAGER);
               softly.assertThat(saved.getStatus()).isEqualTo(UserStatus.ACTIVE);
               softly.assertThat(saved.isPasswordChangeRequired()).isTrue();
               softly.assertThat(saved.isFirstLoginCompleted()).isFalse();
               softly.assertThat(saved.getCreatedAt()).isNotNull();
           });
        }

        @Test
        @DisplayName("should generate UUID when id is null")
        void generateUUID() {
            User user = aUser().withId(null).build();

            User saved = userDao.save(user);

            assertThat(saved.getId()).isNotNull();
        }

        @Test
        @DisplayName("should preserve pre-set id")
        void preservePreSetId() {
            UUID id = UUID.randomUUID();
            User user = aUser().withId(id).build();

            User saved = userDao.save(user);
            assertThat(saved.getId()).isEqualTo(id);
        }

        @Test
        @DisplayName("should set createdAt when null")
        void setCreatedAt() {
            Instant before = Instant.now().minusSeconds(1);

            User user = aUser().withCreatedAt(null).build();
            User saved = userDao.save(user);

            assertThat(saved.getCreatedAt())
                    .isAfter(before)
                    .isBefore(Instant.now().plusSeconds(1));
        }

        @Test
        @DisplayName("should preserve created_at")
        void preserveCreatedAt() {
            Instant fixedTime = Instant.parse("2024-01-15T10:30:00Z");

            User user = aUser().withCreatedAt(fixedTime).build();

            User saved = userDao.save(user);

            assertThat(saved.getCreatedAt().truncatedTo(ChronoUnit.MILLIS))
                    .isEqualTo(fixedTime.truncatedTo(ChronoUnit.MILLIS));
        }

        @Test
        @DisplayName("should be retrievable after save")
        void shouldBeRetrievableAfterSave() {
            User user = aUser().build();
            User saved = userDao.save(user);

            Optional<User> found = userDao.findById(user.getId());

            assertThat(found)
                    .isPresent()
                    .get()
                    .satisfies( u -> {
                    assertThat(u.getId()).isEqualTo(saved.getId());
                        assertThat(u.getName()).isEqualTo(saved.getName());
                        assertThat(u.getEmail()).isEqualTo(saved.getEmail());
                    });
        }

        @Test
        @DisplayName("should throw on duplicate email")
        void shouldThrowOnDuplicateEmail() {
            User first = aUser().withEmail("duplicate@mail.com").build();
            User second = aUser().withEmail("duplicate@mail.com").build();

            userDao.save(first);

            assertThatThrownBy(() -> userDao.save(second))
                    .isInstanceOf(RuntimeException.class);
        }

        @Test
        @DisplayName("should throw on duplicate email case-insensitive")
        void shouldThrowOnDuplicateEmailCaseInsensitive() {
            User first = aUser().withEmail("Test@Example.COM").build();
            User second = aUser().withEmail("test@example.com").build();

            userDao.save(first);

            assertThatThrownBy(() -> userDao.save(second))
                    .isInstanceOf(RuntimeException.class);
        }

        @Test
        @DisplayName("should throw on duplicate id")
        void shouldThrowOnDuplicateId() {
            UUID sharedId = UUID.randomUUID();
            userDao.save(aUser().withId(sharedId).withEmail("first@mail.com").build());

            assertThatThrownBy(() ->
                    userDao.save(aUser().withId(sharedId).withEmail("test@example.com").build()))
            .isInstanceOf(RuntimeException.class);
        }
    }

    @Nested
    @DisplayName("findById()")
    class FindByIdTests {

        @Test
        @DisplayName("should return user when it exists")
        void returnsUserWhenItExists() {
            User saved = userDao.save(aUser().withName("Alon").build());
            Optional<User> found = userDao.findById(saved.getId());

            assertThat(found)
                    .isPresent()
                    .get()
                    .extracting(User::getName)
                    .isEqualTo("Alon");
        }

        @Test
        @DisplayName("should return empty when id does not exist")
        void returnsEmptyWhenIdDoesNotExist() {
            Optional<User> found = userDao.findById(UUID.randomUUID());
            assertThat(found).isEmpty();
        }

        @Test
        @DisplayName("should return all field correctly")
        void returnsAllFieldCorrectly() {
            User original = aUser()
                    .withName("Tom")
                    .withEmail("tom@mail.com")
                    .withRole(Role.FLIGHT_MANAGER)
                    .withStatus(UserStatus.ACTIVE)
                    .withPasswordHash("@hashedPassword123")
                    .withPasswordChangeRequired(true)
                    .withFirstLoginCompleted(true)
                    .build();

            User saved = userDao.save(original);

            User found = userDao.findById(saved.getId()).orElseThrow();

            assertSoftly(softly -> {
                softly.assertThat(found.getName()).isEqualTo(saved.getName());
                softly.assertThat(found.getEmail()).isEqualTo(saved.getEmail());
                softly.assertThat(found.getRole()).isEqualTo(Role.FLIGHT_MANAGER);
                softly.assertThat(found.getStatus()).isEqualTo(UserStatus.ACTIVE);
                softly.assertThat(found.isPasswordChangeRequired()).isTrue();
                softly.assertThat(found.isFirstLoginCompleted()).isTrue();
                softly.assertThat(found.getPasswordHash()).isEqualTo(saved.getPasswordHash());
                softly.assertThat(found.getCreatedAt()).isNotNull();
                softly.assertThat(found.getUpdatedAt()).isNull();
                softly.assertThat(found.getLastLoginAt()).isNull();
            });
        }
    }

    @Nested
    @DisplayName("findByEmail()")
    class FindByEmailTests {

        @Test
        @DisplayName("should return user by existing email")
        void returnsUserWithExistingEmail() {
            userDao.save(aUser().withEmail("alon@mail.com").build());

            Optional<User> found = userDao.findByEmail("alon@mail.com");

            assertThat(found)
                    .isPresent()
                    .get()
                    .extracting(User::getEmail)
                    .isEqualTo("alon@mail.com");
        }

        @Test
        @DisplayName("should return empty for non existing email")
        void returnsEmptyWhenEmailDoesNotExist() {
            Optional<User> found = userDao.findByEmail("alon@mail.com");
            assertThat(found).isEmpty();
        }

        @Test
        @DisplayName("should return empty for empty email")
        void returnsEmptyWhenEmailIsEmpty() {
            userDao.save(aUser().build());
            assertThat(userDao.findByEmail("")).isEmpty();
        }

        @Test
        @DisplayName("should return empty for null email")
        void returnsEmptyWhenEmailIsNull() {
            assertThat(userDao.findByEmail(null)).isEmpty();
        }

        @Test
        @DisplayName("should return user by case-insensitive email")
        void caseInsensitiveEmail() {
            userDao.save(aUser().withEmail("Alon@mail.com").build());

            assertThat(userDao.findByEmail("alon@mail.com"))
                    .isPresent();
        }
    }

    @Nested
    @DisplayName("findAll()")
    class FindAllTests {

        @Test
        @DisplayName("should return all saved users")
        void returnsAllUsers() {
            userDao.save(aUser().withEmail("alon@mail.com").build());
            userDao.save(aUser().withEmail("alon2@mail.com").build());
            userDao.save(aUser().withEmail("alon3@mail.com").build());

            List<User> users = userDao.findAll();

            assertThat(users).hasSize(3);
            assertThat(users)
                    .extracting(User::getEmail)
                    .containsExactlyInAnyOrder("alon2@mail.com", "alon3@mail.com", "alon@mail.com");
        }

        @Test
        @DisplayName("should return empty list for no users")
        void returnsEmptyListWhenNoUsers() {
            List<User> users = userDao.findAll();
            assertThat(users).isEmpty();
        }

        @Test
        @DisplayName("should return users by created_at descending order")
        void returnsUsersByCreatedAtDescendingOrder() {
            Instant first = Instant.parse("2024-01-01T00:00:00Z");
            Instant second = Instant.parse("2024-06-01T00:00:00Z");
            Instant third = Instant.parse("2024-10-01T00:00:00Z");

            userDao.save(aUser().withEmail("alon@mail.com").withCreatedAt(first).build());
            userDao.save(aUser().withEmail("alon2@mail.com").withCreatedAt(second).build());
            userDao.save(aUser().withEmail("alon3@mail.com").withCreatedAt(third).build());

            List<User> users = userDao.findAll();

            assertThat(users).hasSize(3);
            assertThat(users)
                    .extracting(User::getEmail)
                    .containsExactly("alon3@mail.com", "alon2@mail.com", "alon@mail.com");

        }

        @Test
        @DisplayName("should return single user when only one exists")
        void returnsSingleUserWhenOneExists() {
            userDao.save(aUser().withEmail("alon@mail.com").build());

            List<User> users = userDao.findAll();

            assertThat(users).hasSize(1);
            assertThat(users)
                    .first()
                    .extracting(User::getEmail)
                    .isEqualTo("alon@mail.com");
        }
    }

    @Nested
    @DisplayName("findByStatus()")
    class FindByStatusTests {

        @Test
        @DisplayName("should return only users with matching status")
        void returnsOnlyUsersWithMatchingStatus() {
            userDao.save(aUser().withEmail("alon@mail.com").withStatus(UserStatus.ACTIVE).build());
            userDao.save(aUser().withEmail("Tome@mail.com").withStatus(UserStatus.ACTIVE).build());
            userDao.save(aUser().withEmail("John@mail.com").withStatus(UserStatus.BLOCKED).build());

            List<User> activeUsers = userDao.findByStatus(UserStatus.ACTIVE);

            assertThat(activeUsers)
                    .hasSize(2)
                    .extracting(User::getEmail)
                    .containsExactlyInAnyOrder("alon@mail.com", "Tome@mail.com");
        }

        @Test
        @DisplayName("should return empty list when status don't exists")
        void returnsEmptyListWhenStatusDontExists() {
            userDao.save(aUser().withEmail("alon@mail.com").withStatus(UserStatus.ACTIVE).build());

            List<User> blockedUsers = userDao.findByStatus(UserStatus.BLOCKED);

            assertThat(blockedUsers).isEmpty();
        }

        @ParameterizedTest
        @EnumSource(UserStatus.class)
        @DisplayName("should return status for every user status type")
        void returnsStatusForEveryUserStatus(UserStatus status) {
            userDao.save(aUser().withEmail("alon@mail.com").withStatus(status).build());

            List<User> users = userDao.findByStatus(status);

            assertThat(users)
                    .hasSize(1)
                    .extracting(User::getStatus)
                    .containsExactly(status);
        }
    }

    @Nested
    @DisplayName("update()")
    class UpdateTests {

        @Test
        @DisplayName("should update existing user")
        void updatesExistingUser() {
            User originalUser = aUser()
                    .withName("John Doe")
                    .withEmail("john@mail.com")
                    .withStatus(UserStatus.PENDING_EMAIL_CONFIRMATION)
                    .withRole(Role.FLIGHT_MANAGER)
                    .withPasswordChangeRequired(true)
                    .withFirstLoginCompleted(false)
                    .build();

            userDao.save(originalUser);

            User saved = userDao.findById(originalUser.getId()).orElseThrow();

            saved.setName("Jonny Doe");
            saved.setEmail("jonny@mail.com");
            saved.setStatus(UserStatus.ACTIVE);
            saved.setRole(Role.ADMIN);
            saved.setPasswordChangeRequired(false);
            saved.setFirstLoginCompleted(true);

            userDao.update(saved);
            User updatedUser = userDao.findById(originalUser.getId()).orElseThrow();

            assertSoftly(softly -> {
                softly.assertThat(updatedUser.getName()).isEqualTo("Jonny Doe");
                softly.assertThat(updatedUser.getEmail()).isEqualTo("jonny@mail.com");
                softly.assertThat(updatedUser.getStatus()).isEqualTo(UserStatus.ACTIVE);
                softly.assertThat(updatedUser.getRole()).isEqualTo(Role.ADMIN);
                softly.assertThat(updatedUser.isPasswordChangeRequired()).isFalse();
                softly.assertThat(updatedUser.isFirstLoginCompleted()).isTrue();
            });
        }

        @Test
        @DisplayName("should update updated_at field")
        void updatesUpdatedAtField() {
            Instant before = Instant.now().minusSeconds(1);
            User saved = userDao.save(aUser().withEmail("alon@mail.com").build());
            assertThat(saved.getUpdatedAt()).isNull();

            userDao.update(saved);

            User updatedUser = userDao.findById(saved.getId()).orElseThrow();
            assertThat(updatedUser.getUpdatedAt()).isNotNull();
            assertThat(updatedUser.getUpdatedAt())
                    .isAfter(before)
                    .isBefore(Instant.now().plusSeconds(1));
        }

        @Test
        @DisplayName("should not alter create_at")
        void shouldNotAlterCreateAt() {
            User user = userDao.save(aUser().build());
            Instant createdAt = userDao.findById(user.getId()).orElseThrow().getCreatedAt();

            user.setName("Mikel");
            userDao.update(user);
            User updatedUser = userDao.findById(user.getId()).orElseThrow();

            assertThat(updatedUser.getCreatedAt()).isEqualTo(createdAt);
        }

        @Test
        @DisplayName("should not alter the password hash")
        void shouldNotAlterPasswordHash() {
            User user = userDao.save(aUser().withPasswordHash("@234passwordHash").build());

            user.setName("Mikel");
            userDao.update(user);

            User updatedUser = userDao.findById(user.getId()).orElseThrow();
            assertThat(updatedUser.getPasswordHash()).isEqualTo("@234passwordHash");
        }

        @Test
        @DisplayName("should not affect other users")
        void shouldNotAffectOtherUsers() {
            User user1 = userDao.save(aUser().withEmail("alon@mail.com").build());
            User user2 = userDao.save(aUser().withEmail("john@mail.com").build());

            user1.setEmail("alonClinton@mail.com");
            userDao.update(user1);

            User foundUser2 = userDao.findById(user2.getId()).orElseThrow();
            assertThat(foundUser2.getEmail()).isEqualTo("john@mail.com");
        }

        @Test
        @DisplayName("should throw when update non-existing user")
        void throwsWhenUpdateNonExistingUser() {
            //todo
        }
    }

    @Nested
    @DisplayName("updatePassword()")
    class UpdatePasswordTests {

        @Test
        @DisplayName("should update passwordHash")
        void shouldUpdatePasswordHash() {
            User user = userDao.save(aUser().withPasswordHash("@234passwordHash").build());

            user.setPasswordHash("@234newPasswordHash");
            userDao.update_password(user.getId(),user.getPasswordHash(),true);

            User updatedUser = userDao.findById(user.getId()).orElseThrow();
            assertThat(updatedUser.getPasswordHash()).isEqualTo("@234newPasswordHash");
        }

        @Test
        @DisplayName("should update passwordChangeRequired")
        void shouldUpdatePasswordChangeRequired() {
            User user = userDao.save(aUser().withPasswordChangeRequired(true).build());

            userDao.update_password(user.getId(),"newPasswordHash",false);
            User updatedUser = userDao.findById(user.getId()).orElseThrow();

            assertThat(updatedUser.isPasswordChangeRequired()).isFalse();
        }

        @Test
        @DisplayName("should set updated_at timestamp")
        void shouldSetUpdatedAtTimestamp() {
            Instant before = Instant.now().minusSeconds(1);
            User user = userDao.save(aUser().build());

            userDao.update_password(user.getId(),"newPasswordHash",true);
            User updatedUser = userDao.findById(user.getId()).orElseThrow();

            assertThat(updatedUser.getUpdatedAt()).isNotNull();
            assertThat(updatedUser.getUpdatedAt())
                    .isAfter(before)
                    .isBefore(Instant.now().plusSeconds(1));
        }

        @Test
        @DisplayName("should not modify other fields")
        void shouldNotModifyOtherFields() {
            User user = userDao.save(aUser()
                    .withEmail("Jonny@mail.com")
                    .withName("Jonny")
                    .withStatus(UserStatus.ACTIVE)
                    .withRole(Role.FLIGHT_MANAGER)
                    .build()
            );

            userDao.update_password(user.getId(),"newPasswordHash",false);
            User updatedUser = userDao.findById(user.getId()).orElseThrow();

            assertSoftly(softly -> {
                softly.assertThat(updatedUser.getName()).isEqualTo("Jonny");
                softly.assertThat(updatedUser.getEmail()).isEqualTo("Jonny@mail.com");
                softly.assertThat(updatedUser.getStatus()).isEqualTo(UserStatus.ACTIVE);
                softly.assertThat(updatedUser.getRole()).isEqualTo(Role.FLIGHT_MANAGER);
                softly.assertThat(updatedUser.isPasswordChangeRequired()).isFalse();
                softly.assertThat(updatedUser.getUpdatedAt()).isNotNull();
            });
        }
    }

    @Nested
    @DisplayName("updateStatus()")
    class UpdateStatusTests {

        @Test
        @DisplayName("should update status of existing user")
        void updateStatusOfExistingUser() {
            User user = userDao.save(aUser().withStatus(UserStatus.PENDING_EMAIL_CONFIRMATION).build());

            userDao.update_status(user.getId(),UserStatus.ACTIVE);

            User updatedUser = userDao.findById(user.getId()).orElseThrow();
            assertThat(updatedUser.getStatus()).isEqualTo(UserStatus.ACTIVE);
        }

        @ParameterizedTest
        @EnumSource(UserStatus.class)
        @DisplayName("should support transition to any status")
        void shouldSupportTransitionToAnyStatus(UserStatus status) {
            User user = userDao.save(aUser().withStatus(UserStatus.ACTIVE).build());

            userDao.update_status(user.getId(),status);

            User updatedUser = userDao.findById(user.getId()).orElseThrow();
            assertThat(updatedUser.getStatus()).isEqualTo(status);
        }

        @Test
        @DisplayName("should set updated_at")
        void shouldSetUpdatedAt() {
            Instant before = Instant.now().minusSeconds(1);
            User user = userDao.save(aUser().withStatus(UserStatus.ACTIVE).build());

            userDao.update_status(user.getId(),UserStatus.BLOCKED);
            User updatedUser = userDao.findById(user.getId()).orElseThrow();

            assertThat(updatedUser.getUpdatedAt())
                    .isAfter(before)
                    .isBefore(Instant.now().plusSeconds(1));
        }

        @Test
        @DisplayName("should not affect other fields")
        void shouldNotAffectOtherFields() {
            User user = userDao.save(aUser().withStatus(UserStatus.ACTIVE)
                            .withName("Mikle")
                            .withRole(Role.FLIGHT_MANAGER)
                            .withEmail("mikle@mail.com")
                            .withFirstLoginCompleted(true)
                            .build());

            userDao.update_status(user.getId(),UserStatus.BLOCKED);
            User updatedUser = userDao.findById(user.getId()).orElseThrow();

            assertSoftly(softly -> {
                softly.assertThat(updatedUser.getStatus()).isEqualTo(UserStatus.BLOCKED);
                softly.assertThat(updatedUser.getRole()).isEqualTo(Role.FLIGHT_MANAGER);
                softly.assertThat(updatedUser.getEmail()).isEqualTo("mikle@mail.com");
                softly.assertThat(updatedUser.getName()).isEqualTo("Mikle");
                softly.assertThat(updatedUser.isFirstLoginCompleted()).isTrue();
            });
        }
    }

    @Nested
    @DisplayName("updateLastLogin()")
    class UpdateLastLoginTests {

        @Test
        @DisplayName("should change last login value of the user")
        void shouldChangeLastLoginValueOfUser() {
            Instant before = Instant.now().minusSeconds(1);
            User user = userDao.save(aUser().withEmail("John@mail.com").build());
            assertThat(userDao.findById(user.getId()).orElseThrow().getLastLoginAt()).isNull();

            userDao.update_last_login(user.getId());
            User updatedUser = userDao.findById(user.getId()).orElseThrow();

            assertThat(updatedUser.getLastLoginAt())
                    .isAfter(before)
                    .isBefore(Instant.now().plusSeconds(1));
        }

        @Test
        @DisplayName("should set updated_at field")
        void shouldSetUpdatedAtField() {
            User user = userDao.save(aUser().build());
            Instant before = Instant.now().minusSeconds(1);

            userDao.update_last_login(user.getId());
            User updatedUser = userDao.findById(user.getId()).orElseThrow();

            assertThat(updatedUser.getUpdatedAt())
                    .isAfter(before)
                    .isBefore(Instant.now().plusSeconds(1));
        }

        @Test
        @DisplayName("should not affect other fields")
        void shouldNotAffectOtherFields() {
            User user = userDao.save(aUser()
                    .withEmail("John@mail.com")
                    .withName("Mikle")
                    .withStatus(UserStatus.PENDING_EMAIL_CONFIRMATION)
                    .withRole(Role.ADMIN)
                    .build()
            );
            userDao.update_last_login(user.getId());
            User updatedUser = userDao.findById(user.getId()).orElseThrow();

            assertSoftly(softly -> {
                softly.assertThat(updatedUser.getLastLoginAt()).isNotNull();
                softly.assertThat(updatedUser.getName()).isEqualTo("Mikle");
                softly.assertThat(updatedUser.getStatus()).isEqualTo(UserStatus.PENDING_EMAIL_CONFIRMATION);
                softly.assertThat(updatedUser.getRole()).isEqualTo(Role.ADMIN);
                softly.assertThat(updatedUser.getEmail()).isEqualTo("John@mail.com");
            });
        }

        @Test
        @DisplayName("should override previous last login")
        void shouldOverridePreviousLastLogin() {
            User user = userDao.save(aUser().build());
            userDao.update_last_login(user.getId());
            Instant firstLogin = userDao.findById(user.getId()).orElseThrow().getLastLoginAt();

            try { Thread.sleep(100); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

            userDao.update_last_login(user.getId());
            Instant secondLogin = userDao.findById(user.getId()).orElseThrow().getLastLoginAt();

            assertThat(firstLogin).isBefore(secondLogin);
        }
    }

    @Nested
    @DisplayName("delete()")
    class DeleteTests {

        @Test
        @DisplayName("should delete existing user")
        void shouldDeleteExistingUser() {
            User user = userDao.save(aUser().build());

            userDao.delete(user.getId());

            Optional<User> deletedUser = userDao.findById(user.getId());

            assertThat(deletedUser).isEmpty();
        }

        @Test
        @DisplayName("should not delete other user")
        void shouldNotDeleteOtherUser() {
            User user1 = userDao.save(aUser().build());
            User user2 = userDao.save(aUser().build());

            userDao.delete(user1.getId());

            assertThat(userDao.findById(user1.getId())).isEmpty();
            assertThat(userDao.findById(user2.getId()).isPresent()).isTrue();
        }

        @Test
        @DisplayName("should not throw when deleting non-existing user")
        void shouldNotThrowWhenDeletingNonExistingUser() {
            assertThatCode(() -> userDao.delete(UUID.randomUUID())).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("should make email available after deletion")
        void shouldMakeEmailAvailableAfterDeletion() {
            User original = userDao.save(aUser().withEmail("test@mail.com").build());
            userDao.delete(original.getId());

            User reusedUser = aUser().withEmail("test@mail.com").build();

            assertThatCode(() -> userDao.save(reusedUser)).doesNotThrowAnyException();
            assertThat(userDao.findByEmail("test@mail.com")).isPresent();
        }

        @Test
        @DisplayName("should take cascade effect when deleting user")
        void cascadeEffectWhenDeletingUser() {
            User user =  userDao.save( aUser().withEmail("test@mail.com").build());
            assertThat(insertTestBatch(user.getId())).hasSize(3);

            assertThat(existsByUserId("sessions",user.getId())).isEqualTo(1);
            assertThat(existsByUserId("remember_me_tokens",user.getId())).isEqualTo(1);
            assertThat(existsByUserId("email_confirmation_tokens", user.getId())).isEqualTo(1);

            userDao.delete(user.getId());

            assertThat(existsByUserId("sessions", user.getId())).isZero();
            assertThat(existsByUserId("remember_me_tokens", user.getId())).isZero();
            assertThat(existsByUserId("email_confirmation_tokens", user.getId())).isZero();
        }

        private int[] insertTestBatch(UUID userId) {
            String sessionId = "sess" + UUID.randomUUID();
            String series = "series" + UUID.randomUUID();
            String rememberTokenHash = "remember" + UUID.randomUUID();
            String emailHash = "email" + UUID.randomUUID();

            var sessionInsert = String.format(
                    "INSERT INTO sessions (id, user_id, expires_at) VALUES ('%s', '%s'::uuid, NOW() + INTERVAL '2 minutes')",
                    sessionId,
                    userId
            );

            var rememberTokenInsert =
                    String.format(
                            "INSERT INTO remember_me_tokens (series, user_id, token_hash, expires_at)" +
                                    " VALUES ('%s', '%s'::uuid, '%s', NOW() + INTERVAL '2 minutes')",
                            series,
                            userId,
                            rememberTokenHash
                    );

            var emailConfTokenInsert =
                    String.format(
                            "INSERT INTO email_confirmation_tokens (user_id, token_hash, expires_at)" +
                                    "VALUES ('%s'::uuid, '%s', NOW() + INTERVAL '2 minutes')",
                            userId,
                            emailHash
                    );

            try(Connection conn = DatasourceConfig.getConnection();
                Statement stat = conn.createStatement();
            ) {
                stat.addBatch(sessionInsert);
                stat.addBatch(rememberTokenInsert);
                stat.addBatch(emailConfTokenInsert);

                return stat.executeBatch();
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }

        private int existsByUserId(String table, UUID userId) {
            var sql = String.format(
                    "SELECT COUNT(*) FROM %s WHERE user_id = '%s'::uuid", table, userId
            );
            int result = 0;

            try(Connection conn = DatasourceConfig.getConnection();
                Statement stat = conn.createStatement()
            ) {
                try( ResultSet rs = stat.executeQuery(sql)) {
                    if (rs.next())
                       result = rs.getInt(1);
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
            return result;
        }

    }

    @Nested
    @DisplayName("existByEmail()")
    class ExistByEmailTests {

        @Test
        @DisplayName("should return true when email exists")
        void shouldReturnTrueWhenEmailExists() {
            userDao.save(aUser().withEmail("Max@mail.com").build());

            assertThat(userDao.existsByEmail("Max@mail.com")).isTrue();
        }

        @Test
        @DisplayName("should return false when the email non-exists")
        void shouldReturnFalseWhenEmailNonExists() {
            userDao.save(aUser().withEmail("Max@mail.com").build());

            assertThat(userDao.existsByEmail("NonExist@mail.com")).isFalse();
        }

        @Test
        @DisplayName("should return false when email is empty string")
        void returnFalseWhenEmailIsEmpty() {
            assertThat(userDao.existsByEmail("")).isFalse();
        }

        @Test
        @DisplayName("should return false when email null")
        void shouldReturnFalseWhenEmailNull() {
            assertThat(userDao.existsByEmail(null)).isFalse();
        }

        @Test
        @DisplayName("should return false after user deletion")
        void shouldReturnFalseAfterUserDeletion() {
            User user = userDao.save(aUser().withEmail("Max@mail.com").build());
            assertThat(userDao.existsByEmail("Max@mail.com")).isTrue();

            userDao.delete(user.getId());

            assertThat(userDao.existsByEmail("Max@mail.com")).isFalse();
        }

        @Test
        @DisplayName("should return true when case-insensitive")
        void caseInsensitiveEmail() {
            userDao.save(aUser().withEmail("Max@mail.Com").build());
            userDao.save(aUser().withEmail("test@mail.com").build());

            assertThat(userDao.existsByEmail("max@mail.com")).isTrue();
            assertThat(userDao.existsByEmail("Test@Mail.com")).isTrue();
        }
    }

    @Nested
    @DisplayName("countByStatus()")
    class CountByStatusTests {

        @Test
        @DisplayName("should return correct count by single status")
        void shouldReturnCorrectCountByStatus() {
            userDao.save(aUser().withEmail("test@mail.com").withStatus(UserStatus.ACTIVE).build());
            userDao.save(aUser().withEmail("test1@mail.com").withStatus(UserStatus.ACTIVE).build());
            userDao.save(aUser().withEmail("test2@mail.com").withStatus(UserStatus.ACTIVE).build());

            assertThat(userDao.countByStatus(UserStatus.ACTIVE)).isEqualTo(3);
        }

        @ParameterizedTest
        @EnumSource(UserStatus.class)
        @DisplayName("should return correct number by all statuses")
        void shouldReturnCorrectCountByStatus(UserStatus status) {
            userDao.save(aUser().withStatus(status).build());
            assertThat(userDao.countByStatus(status)).isEqualTo(1);
        }

        @Test
        @DisplayName("should return correct count of status when mixed statuses")
        void shouldReturnCorrectCountOfStatusWhenMixedStatuses() {
            userDao.save(aUser().withStatus(UserStatus.ACTIVE).build());
            userDao.save(aUser().withStatus(UserStatus.PENDING_APPROVAL).build());
            userDao.save(aUser().withStatus(UserStatus.PENDING_APPROVAL).build());
            userDao.save(aUser().withStatus(UserStatus.PENDING_EMAIL_CONFIRMATION).build());
            userDao.save(aUser().withStatus(UserStatus.BLOCKED).build());

            assertThat(userDao.countByStatus(UserStatus.PENDING_APPROVAL)).isEqualTo(2);
            assertThat(userDao.countByStatus(UserStatus.BLOCKED)).isEqualTo(1);
        }

        @Test
        @DisplayName("should return zero when there is no user with status")
        void shouldReturnZeroWhenNoUserWithStatus() {
            assertThat(userDao.countByStatus(UserStatus.ACTIVE)).isEqualTo(0);
        }

        @Test
        @DisplayName("should reflect the status has change")
        void shouldReflectTheStatusHasChange() {
            User user = userDao.save(aUser().withStatus(UserStatus.ACTIVE).build());
            assertThat(userDao.countByStatus(UserStatus.ACTIVE)).isEqualTo(1);

            userDao.update_status(user.getId(), UserStatus.PENDING_APPROVAL);

            assertThat(userDao.countByStatus(UserStatus.ACTIVE)).isZero();
        }
    }

    @Nested
    @DisplayName("countAdmins()")
    class CountAdminsTests {

        @Test
        @DisplayName("should count all admins")
        void countAdmins() {
            userDao.save(aUser().withEmail("test@mail.com").withRole(Role.ADMIN).build());
            userDao.save(aUser().withEmail("test3@mail.com").withRole(Role.ADMIN).build());
            userDao.save(aUser().withEmail("test4@mail.com").withRole(Role.ADMIN).build());

            assertThat(userDao.countAdmins()).isEqualTo(3);
        }

        @Test
        @DisplayName("should return zero when admins no exists")
        void shouldReturnZeroWhenAdminsNoExists() {
            userDao.save(aUser().withEmail("test@mail.com").withRole(Role.FLIGHT_MANAGER).build());
            userDao.save(aUser().withEmail("test3@mail.com").withRole(Role.FLIGHT_MANAGER).build());
            userDao.save(aUser().withEmail("test4@mail.com").withRole(Role.CUSTOMER_MANAGER).build());

            assertThat(userDao.countAdmins()).isZero();
        }

        @Test
        @DisplayName("should exclude blocked admins")
        void shouldExcludeBlockedAdmins() {
            userDao.save(aUser().withEmail("test@mail.com").withRole(Role.ADMIN).build());
            userDao.save(aUser().withStatus(UserStatus.BLOCKED).withRole(Role.ADMIN).build());

            assertThat(userDao.countAdmins()).isEqualTo(1);
        }

        @Test
        @DisplayName("should not count not admin users")
        void shouldNotCountNotAdminUsers() {
            userDao.save(aUser().withEmail("test@mail.com").withRole(Role.ADMIN).build());
            userDao.save(aUser().withEmail("test2@mail.com").withRole(Role.FLIGHT_MANAGER).build());
            userDao.save(aUser().withEmail("test3@mail.com").withRole(Role.CUSTOMER_MANAGER).build());

            assertThat(userDao.countAdmins()).isEqualTo(1);
        }

        @Test
        @DisplayName("should reflect admin role change")
        void shouldReflectAdminRoleChange() {
            User user = userDao.save(anAdmin().build());
            User saved = userDao.findById(user.getId()).orElseThrow();

            assertThat(userDao.countAdmins()).isEqualTo(1);

            saved.setRole(Role.FLIGHT_MANAGER);
            userDao.update(saved);

            assertThat(userDao.countAdmins()).isZero();
        }
    }

    @Nested
    @DisplayName("complex scenarios")
    class ComplexScenariosTests {

        @Test
        @DisplayName("full user lifecycle: create -> login -> update -> block -> delete")
        void fullUserLifecycle() {
            //Create
            User user = userDao.save(aUser()
                    .withEmail("test@mail.com")
                    .withName("Nick")
                    .withPasswordHash("@passwordOriginHash123")
                    .withStatus(UserStatus.PENDING_APPROVAL)
                    .withPasswordChangeRequired(true)
                    .build());
            assertThat(userDao.findById(user.getId())).isPresent();

            //First login
            userDao.update_last_login(user.getId());
            User afterLogin = userDao.findById(user.getId()).orElseThrow();
            assertThat(afterLogin.getLastLoginAt()).isNotNull();

            //Change password (first login password change)
            userDao.update_password(afterLogin.getId(),"@newPasswordHash123",false);
            User afterPwdChange = userDao.findById(afterLogin.getId()).orElseThrow();
            assertThat(afterPwdChange.getPasswordHash()).isEqualTo("@newPasswordHash123");
            assertThat(afterPwdChange.isPasswordChangeRequired()).isFalse();

            //Update user profile
            afterPwdChange.setName("UpdatedName");
            afterPwdChange.setFirstLoginCompleted(true);
            userDao.update(afterPwdChange);
            User afterUpdate = userDao.findById(afterPwdChange.getId()).orElseThrow();
            assertThat(afterUpdate.getName()).isEqualTo("UpdatedName");
            assertThat(afterUpdate.isFirstLoginCompleted()).isTrue();

            //Block
            userDao.update_status(afterUpdate.getId(), UserStatus.BLOCKED);
            User afterBlocked = userDao.findById(afterUpdate.getId()).orElseThrow();
            assertThat(afterBlocked.getStatus()).isEqualTo(UserStatus.BLOCKED);

            //Delete
            userDao.delete(afterBlocked.getId());
            assertThat(userDao.findById(afterBlocked.getId())).isNotPresent();
            assertThat(userDao.existsByEmail("test@mail.com")).isFalse();
        }

        @Test
        @DisplayName("bulk operation maintain data integrity")
        void bulkOperation() {
            int batchSize = 50;

            List<User> users = IntStream.range(0, batchSize)
                    .mapToObj(i -> userDao.save(aUser()
                            .withEmail("bulk" + i + "@mail.com")
                            .withStatus( i % 2 == 0 ? UserStatus.ACTIVE : UserStatus.BLOCKED)
                            .withRole( i < 5 ? Role.ADMIN : Role.FLIGHT_MANAGER)
                            .build()))
                    .toList();
            assertThat(userDao.findAll()).hasSize(batchSize);
            assertThat(userDao.countByStatus(UserStatus.ACTIVE)).isEqualTo(25);
            assertThat(userDao.countByStatus(UserStatus.BLOCKED)).isEqualTo(25);
            assertThat(userDao.countAdmins()).isEqualTo(3);

            users.stream()
                    .filter(user -> user.getStatus().equals(UserStatus.BLOCKED))
                    .forEach(user -> userDao.delete(user.getId()));

            assertThat(userDao.findAll().size()).isEqualTo(25);
            assertThat(userDao.countByStatus(UserStatus.BLOCKED)).isZero();
        }

        @Test
        @DisplayName("concurrent saves with different emails should succeed")
        void concurrentSaves() throws InterruptedException {
            int threadCount = 10;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(threadCount);
            List<Future<User>> futures = new ArrayList<>();

            for(int i = 0; i < threadCount; i++) {
                final int index = i;
                futures.add(executor.submit(() -> {
                    try {
                        latch.countDown();
                        latch.await(5, TimeUnit.SECONDS);
                        return userDao.save(aUser()
                                .withEmail("test" + index + "@mail.com")
                                .build());
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException(e);
                    }
                }));
            }
            executor.shutdown();
            assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

            long successCount = futures.stream()
                    .filter(f -> {
                        try {
                            f.get();
                            return true;
                        } catch (Exception e) {
                            return false;
                        }
                    }).count();

            assertThat(successCount).isEqualTo(threadCount);
            assertThat(userDao.findAll()).hasSize(threadCount);
        }

        @Test
        @DisplayName("concurrent save with same email should fail for all but one")
        void concurrentSaveTheSameEmail() throws InterruptedException {
            int threadCount = 5;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch startGate = new CountDownLatch(1);
            List<Future<User>> futures = new ArrayList<>();

            for(int i = 0; i < threadCount; i++) {
                futures.add(executor.submit(() -> {
                    startGate.await(5, TimeUnit.SECONDS);
                    return userDao.save(aUser()
                            .withEmail("test@mail.com")
                            .build());

                }));
            }
            startGate.countDown(); //releases all thread at once
            executor.shutdown();
            executor.awaitTermination(30, TimeUnit.SECONDS);

            long successCount = 0;
            long failCount = 0;

            for (Future<User> f : futures) {
                try {
                    f.get();
                    successCount++;
                } catch (Exception e) {
                    failCount++;
                }
            }

            assertThat(successCount).isEqualTo(1);
            assertThat(failCount).isEqualTo(threadCount - 1);
            assertThat(userDao.findAll()).hasSize( 1);
        }
    }

    @Nested
    @DisplayName("edge cases")
    class EdgeCasesTests {
        @Test
        @DisplayName("should handle users with maximum length fields")
        void maxLengthFields() {
            String longEmail = "a".repeat(243) + "@example.com"; // 255 chars
            String longName = "t".repeat(100); //  100 chars
            String longPwdHash = "@".repeat(248) + "!123456"; // 255 chars

            User saved = aUser()
                    .withName(longName)
                    .withEmail(longEmail)
                    .withPasswordHash(longPwdHash)
                    .build();

            assertThatCode(() -> userDao.save(saved)).doesNotThrowAnyException();

            User found = userDao.findById(saved.getId()).orElseThrow();

            assertThat(found.getName()).isEqualTo(longName);
            assertThat(found.getEmail()).isEqualTo(longEmail);
            assertThat(found.getPasswordHash()).isEqualTo(longPwdHash);
        }

        @Test
        @DisplayName("should handle special characters in name")
        void specialCharactersInName() {
            String specialName = "O'Brien-José María 日本語 Ñoño <script>alert('xss')</script>";

            User saved = userDao.save(aUser()
                    .withName(specialName)
                    .build());
            User found = userDao.findById(saved.getId()).orElseThrow();
            assertThat(found.getName()).isEqualTo(specialName);
        }

        @Test
        @DisplayName("should handle email with special valid characters")
        void specialCharactersInEmail() {
            String email = "user+tag@sub.example.co.uk";

            User saved = userDao.save(aUser()
                    .withEmail(email)
                    .build());

            assertThat(userDao.findByEmail(email))
                    .isPresent()
                    .get()
                    .extracting(User::getEmail)
                    .isEqualTo(email);
        }

        @Test
        @DisplayName("multiple sequential updates should all persist")
        void multipleSequentialUpdates() {
            User user = userDao.save(aUser().withName("version1").build());

            for(int i = 2; i <= 10; i++) {
                user.setName("version" + i);
                userDao.update(user);
            }
            assertThat(userDao.findById(user.getId()))
                    .isPresent()
                    .get()
                    .extracting(User::getName)
                    .isEqualTo("version10");
        }

        @Test
        @DisplayName("findAll() should not return deleted users")
        void findAllWithDeletedUsers() {
            User u1 = userDao.save(aUser().withEmail("test1@mail.com").build());
            User u2 = userDao.save(aUser().withEmail("test2@mail.com").build());
            User u3 = userDao.save(aUser().withEmail("test3@mail.com").build());

            userDao.delete(u2.getId());

            assertThat(userDao.findAll())
                    .hasSize(2)
                    .extracting(User::getEmail)
                    .containsExactlyInAnyOrder("test1@mail.com", "test3@mail.com");
        }
    }
}
