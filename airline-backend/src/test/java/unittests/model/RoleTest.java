package unittests.model;

import com.airline.airlinebackend.model.emums.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Role tests")
public class RoleTest {

    @Nested
    @DisplayName("HasPermission tests")
    public class HasPermissionTests {
        
        @Nested
        @DisplayName("ADMIN role permissions")
        public class AdminPermissionsTests {
            private final Role admin = Role.ADMIN;
            
            @Test
            @DisplayName("ADMIN has ADMIN_DASHBOARD permission")
            public void testAdminHasAdminDashboardPermission() {
                assertTrue(admin.hasPermission(Role.Permission.ADMIN_DASHBOARD));
            }
            
            @Test
            @DisplayName("ADMIN has FLIGHT_MANAGER_DASHBOARD permission")
            public void testAdminHasFlightManagerDashboardPermission() {
                assertTrue(admin.hasPermission(Role.Permission.FLIGHT_MANAGER_DASHBOARD));
            }
            
            @Test
            @DisplayName("ADMIN has CUSTOMER_MANAGER_DASHBOARD permission")
            public void testAdminHasCustomerManagerDashboardPermission() {
                assertTrue(admin.hasPermission(Role.Permission.CUSTOMER_MANAGER_DASHBOARD));
            }
            
            @Test
            @DisplayName("ADMIN has MANAGE_USERS permission")
            public void testAdminHasManageUsersPermission() {
                assertTrue(admin.hasPermission(Role.Permission.MANAGE_USERS));
            }
            
            @Test
            @DisplayName("ADMIN has VIEW_ALL_USERS permission")
            public void testAdminHasViewAllUsersPermission() {
                assertTrue(admin.hasPermission(Role.Permission.VIEW_ALL_USERS));
            }
        }
        
        @Nested
        @DisplayName("FLIGHT_MANAGER role permissions")
        public class FlightManagerPermissionsTests {
            private final Role flightManager = Role.FLIGHT_MANAGER;
            
            @Test
            @DisplayName("FLIGHT_MANAGER has FLIGHT_MANAGER_DASHBOARD permission")
            public void testFlightManagerHasFlightManagerDashboardPermission() {
                assertTrue(flightManager.hasPermission(Role.Permission.FLIGHT_MANAGER_DASHBOARD));
            }
            
            @Test
            @DisplayName("FLIGHT_MANAGER has CUSTOMER_MANAGER_DASHBOARD permission")
            public void testFlightManagerHasCustomerManagerDashboardPermission() {
                assertTrue(flightManager.hasPermission(Role.Permission.CUSTOMER_MANAGER_DASHBOARD));
            }
            
            @Test
            @DisplayName("FLIGHT_MANAGER does not have ADMIN_DASHBOARD permission")
            public void testFlightManagerDoesNotHaveAdminDashboardPermission() {
                assertFalse(flightManager.hasPermission(Role.Permission.ADMIN_DASHBOARD));
            }
            
            @Test
            @DisplayName("FLIGHT_MANAGER does not have MANAGE_USERS permission")
            public void testFlightManagerDoesNotHaveManageUsersPermission() {
                assertFalse(flightManager.hasPermission(Role.Permission.MANAGE_USERS));
            }
            
            @Test
            @DisplayName("FLIGHT_MANAGER does not have VIEW_ALL_USERS permission")
            public void testFlightManagerDoesNotHaveViewAllUsersPermission() {
                assertFalse(flightManager.hasPermission(Role.Permission.VIEW_ALL_USERS));
            }
        }
        
        @Nested
        @DisplayName("CUSTOMER_MANAGER role permissions")
        public class CustomerManagerPermissionsTests {
            private final Role customerManager = Role.CUSTOMER_MANAGER;
            
            @Test
            @DisplayName("CUSTOMER_MANAGER has CUSTOMER_MANAGER_DASHBOARD permission")
            public void testCustomerManagerHasCustomerManagerDashboardPermission() {
                assertTrue(customerManager.hasPermission(Role.Permission.CUSTOMER_MANAGER_DASHBOARD));
            }
            
            @Test
            @DisplayName("CUSTOMER_MANAGER does not have ADMIN_DASHBOARD permission")
            public void testCustomerManagerDoesNotHaveAdminDashboardPermission() {
                assertFalse(customerManager.hasPermission(Role.Permission.ADMIN_DASHBOARD));
            }
            
            @Test
            @DisplayName("CUSTOMER_MANAGER does not have FLIGHT_MANAGER_DASHBOARD permission")
            public void testCustomerManagerDoesNotHaveFlightManagerDashboardPermission() {
                assertFalse(customerManager.hasPermission(Role.Permission.FLIGHT_MANAGER_DASHBOARD));
            }
            
            @Test
            @DisplayName("CUSTOMER_MANAGER does not have MANAGE_USERS permission")
            public void testCustomerManagerDoesNotHaveManageUsersPermission() {
                assertFalse(customerManager.hasPermission(Role.Permission.MANAGE_USERS));
            }
            
            @Test
            @DisplayName("CUSTOMER_MANAGER does not have VIEW_ALL_USERS permission")
            public void testCustomerManagerDoesNotHaveViewAllUsersPermission() {
                assertFalse(customerManager.hasPermission(Role.Permission.VIEW_ALL_USERS));
            }
        }
    }

    @Nested
    @DisplayName("GetValue tests")
    public class GetValueTests {
        
        @Test
        @DisplayName("ADMIN role getValue returns correct value")
        public void testAdminGetValue() {
            assertEquals("ADMIN", Role.ADMIN.getValue());
        }
        
        @Test
        @DisplayName("FLIGHT_MANAGER role getValue returns correct value")
        public void testFlightManagerGetValue() {
            assertEquals("FLIGHT_MANAGER", Role.FLIGHT_MANAGER.getValue());
        }
        
        @Test
        @DisplayName("CUSTOMER_MANAGER role getValue returns correct value")
        public void testCustomerManagerGetValue() {
            assertEquals("CUSTOMER_MANAGER", Role.CUSTOMER_MANAGER.getValue());
        }
    }

    @Nested
    @DisplayName("GetPermissions tests")
    public class GetPermissionsTests {
        
        @Test
        @DisplayName("ADMIN role getPermissions returns all permissions")
        public void testAdminGetPermissions() {
            Set<Role.Permission> permissions = Role.ADMIN.getPermissions();
            assertEquals(5, permissions.size());
            assertTrue(permissions.contains(Role.Permission.ADMIN_DASHBOARD));
            assertTrue(permissions.contains(Role.Permission.FLIGHT_MANAGER_DASHBOARD));
            assertTrue(permissions.contains(Role.Permission.CUSTOMER_MANAGER_DASHBOARD));
            assertTrue(permissions.contains(Role.Permission.MANAGE_USERS));
            assertTrue(permissions.contains(Role.Permission.VIEW_ALL_USERS));
        }
        
        @Test
        @DisplayName("FLIGHT_MANAGER role getPermissions returns correct permissions")
        public void testFlightManagerGetPermissions() {
            Set<Role.Permission> permissions = Role.FLIGHT_MANAGER.getPermissions();
            assertEquals(2, permissions.size());
            assertTrue(permissions.contains(Role.Permission.FLIGHT_MANAGER_DASHBOARD));
            assertTrue(permissions.contains(Role.Permission.CUSTOMER_MANAGER_DASHBOARD));
        }
        
        @Test
        @DisplayName("CUSTOMER_MANAGER role getPermissions returns correct permissions")
        public void testCustomerManagerGetPermissions() {
            Set<Role.Permission> permissions = Role.CUSTOMER_MANAGER.getPermissions();
            assertEquals(1, permissions.size());
            assertTrue(permissions.contains(Role.Permission.CUSTOMER_MANAGER_DASHBOARD));
        }
        
        @ParameterizedTest
        @EnumSource(Role.class)
        @DisplayName("All roles return non-null permission sets")
        public void testAllRolesReturnNonNullPermissions(Role role) {
            assertNotNull(role.getPermissions());
            assertFalse(role.getPermissions().isEmpty());
        }
    }

    @Nested
    @DisplayName("fromValue tests")
    public class FromValueTests {
        
        @Test
        @DisplayName("fromValue with 'ADMIN' returns ADMIN role")
        public void testFromValueWithAdminRole() {
            Role role = Role.fromValue("ADMIN");
            assertSame(Role.ADMIN, role);
            assertEquals(Role.ADMIN, role);
        }
        
        @Test
        @DisplayName("fromValue with 'admin' (lowercase) returns ADMIN role")
        public void testFromValueWithLowercaseAdmin() {
            Role role = Role.fromValue("admin");
            assertSame(Role.ADMIN, role);
        }
        
        @Test
        @DisplayName("fromValue with 'AdMiN' (mixed case) returns ADMIN role")
        public void testFromValueWithMixedCaseAdmin() {
            Role role = Role.fromValue("AdMiN");
            assertSame(Role.ADMIN, role);
        }
        
        @Test
        @DisplayName("fromValue with 'FLIGHT_MANAGER' returns FLIGHT_MANAGER role")
        public void testFromValueWithFlightManagerRole() {
            Role role = Role.fromValue("FLIGHT_MANAGER");
            assertSame(Role.FLIGHT_MANAGER, role);
            assertEquals(Role.FLIGHT_MANAGER, role);
        }
        
        @Test
        @DisplayName("fromValue with 'flight_manager' (lowercase) returns FLIGHT_MANAGER role")
        public void testFromValueWithLowercaseFlightManager() {
            Role role = Role.fromValue("flight_manager");
            assertSame(Role.FLIGHT_MANAGER, role);
        }
        
        @Test
        @DisplayName("fromValue with 'CUSTOMER_MANAGER' returns CUSTOMER_MANAGER role")
        public void testFromValueWithCustomerManagerRole() {
            Role role = Role.fromValue("CUSTOMER_MANAGER");
            assertSame(Role.CUSTOMER_MANAGER, role);
            assertEquals(Role.CUSTOMER_MANAGER, role);
        }
        
        @Test
        @DisplayName("fromValue with 'customer_manager' (lowercase) returns CUSTOMER_MANAGER role")
        public void testFromValueWithLowercaseCustomerManager() {
            Role role = Role.fromValue("customer_manager");
            assertSame(Role.CUSTOMER_MANAGER, role);
        }
        
        @ParameterizedTest
        @ValueSource(strings = {"INVALID", "USER", "SUPERADMIN", "MANAGER", "null", ""})
        @DisplayName("fromValue with invalid values throws IllegalArgumentException")
        public void testFromValueWithInvalidValues(String value) {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> Role.fromValue(value)
            );
            assertEquals("Unknown role: " + value, exception.getMessage());
        }
        
        @Test
        @DisplayName("fromValue with null throws IllegalArgumentException")
        public void testFromValueWithNull() {
            assertThrows(IllegalArgumentException.class, () -> Role.fromValue(null));
        }
        
        @Test
        @DisplayName("fromValue with whitespace throws IllegalArgumentException")
        public void testFromValueWithWhitespace() {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> Role.fromValue("  ")
            );
            assertEquals("Unknown role:   ", exception.getMessage());
        }
    }

    @Nested
    @DisplayName("Enum properties tests")
    public class EnumPropertiesTests {
        
        @Test
        @DisplayName("All enum values exist")
        public void testAllEnumValues() {
            Role[] values = Role.values();
            assertEquals(3, values.length);
            
            assertNotNull(Role.ADMIN);
            assertNotNull(Role.FLIGHT_MANAGER);
            assertNotNull(Role.CUSTOMER_MANAGER);
        }
        
        @ParameterizedTest
        @EnumSource(Role.class)
        @DisplayName("All enum values have non-null value strings")
        public void testAllEnumValuesWithNonNullValueStrings(Role role) {
            assertNotNull(role.getValue());
            assertFalse(role.getValue().isEmpty());
        }
        
        @Test
        @DisplayName("Enum values in correct order")
        public void testEnumValuesInCorrectOrder() {
            Role[] values = Role.values();
            assertEquals(Role.ADMIN, values[0]);
            assertEquals(Role.FLIGHT_MANAGER, values[1]);
            assertEquals(Role.CUSTOMER_MANAGER, values[2]);
        }
        
        @Test
        @DisplayName("All enum values have unique value strings")
        public void testAllEnumValuesHaveUniqueValueStrings() {
            Role[] values = Role.values();
            Set<String> valueStrings = Set.of(
                    values[0].getValue(),
                    values[1].getValue(),
                    values[2].getValue()
            );
            assertEquals(3, valueStrings.size(), "All role values should be unique");
        }
    }

    @Nested
    @DisplayName("Permission enum tests")
    public class PermissionEnumTests {
        
        @Test
        @DisplayName("All permission values exist")
        public void testAllPermissionValues() {
            Role.Permission[] permissions = Role.Permission.values();
            assertEquals(5, permissions.length);
            
            assertNotNull(Role.Permission.ADMIN_DASHBOARD);
            assertNotNull(Role.Permission.FLIGHT_MANAGER_DASHBOARD);
            assertNotNull(Role.Permission.CUSTOMER_MANAGER_DASHBOARD);
            assertNotNull(Role.Permission.MANAGE_USERS);
            assertNotNull(Role.Permission.VIEW_ALL_USERS);
        }
        
        @Test
        @DisplayName("Permission enum values in correct order")
        public void testPermissionEnumValuesInCorrectOrder() {
            Role.Permission[] permissions = Role.Permission.values();
            assertEquals(Role.Permission.ADMIN_DASHBOARD, permissions[0]);
            assertEquals(Role.Permission.FLIGHT_MANAGER_DASHBOARD, permissions[1]);
            assertEquals(Role.Permission.CUSTOMER_MANAGER_DASHBOARD, permissions[2]);
            assertEquals(Role.Permission.MANAGE_USERS, permissions[3]);
            assertEquals(Role.Permission.VIEW_ALL_USERS, permissions[4]);
        }
        
        @ParameterizedTest
        @EnumSource(Role.Permission.class)
        @DisplayName("All permission values are not null")
        public void testAllPermissionValuesAreNotNull(Role.Permission permission) {
            assertNotNull(permission);
        }
    }
}
