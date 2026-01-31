package modelTest;

import com.airline.airlinebackend.model.emums.Role;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class RoleTest {
    @Test
    public void test() {
        assertTrue(Role.ADMIN.hasPermission(Role.Permission.ADMIN_DASHBOARD));
        assertFalse(Role.CUSTOMER_MANAGER.hasPermission(Role.Permission.ADMIN_DASHBOARD));

        assertThrows(IllegalArgumentException.class, () -> Role.fromValue("main customer"));
        assertDoesNotThrow(() -> Role.fromValue("ADMIN"));
    }
}
