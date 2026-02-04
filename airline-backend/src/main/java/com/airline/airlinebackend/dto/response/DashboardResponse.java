package com.airline.airlinebackend.dto.response;

import java.util.List;

public class DashboardResponse {
    private String userName;
    private String userRole;
    private String dashboardType;
    private List<UserResponse> users;
    private DashboardStats stats;

    public DashboardResponse() {}

    public DashboardResponse(String userName, String userRole, String dashboardType) {
        this.userName = userName;
        this.userRole = userRole;
        this.dashboardType = dashboardType;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getUserRole() {
        return userRole;
    }

    public void setUserRole(String userRole) {
        this.userRole = userRole;
    }

    public String getDashboardType() {
        return dashboardType;
    }

    public void setDashboardType(String dashboardType) {
        this.dashboardType = dashboardType;
    }

    public List<UserResponse> getUsers() {
        return users;
    }

    public void setUsers(List<UserResponse> users) {
        this.users = users;
    }

    public DashboardStats getStats() {
        return stats;
    }

    public void setStats(DashboardStats stats) {
        this.stats = stats;
    }

    public static class DashboardStats {
        private int totalUsers;
        private int pendingApproval;
        private int activeUsers;
        private int blockedUsers;

        public int getTotalUsers() {
            return totalUsers;
        }

        public void setTotalUsers(int totalUsers) {
            this.totalUsers = totalUsers;
        }

        public int getPendingApproval() {
            return pendingApproval;
        }

        public void setPendingApproval(int pendingApproval) {
            this.pendingApproval = pendingApproval;
        }

        public int getActiveUsers() {
            return activeUsers;
        }

        public void setActiveUsers(int activeUsers) {
            this.activeUsers = activeUsers;
        }

        public int getBlockedUsers() {
            return blockedUsers;
        }

        public void setBlockedUsers(int blockedUsers) {
            this.blockedUsers = blockedUsers;
        }
    }
}
