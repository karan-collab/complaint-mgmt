package com.societycare.security;

import java.io.Serializable;
import java.util.Objects;

/**
 * Principal stored in {@link org.springframework.security.core.Authentication#getPrincipal()}
 * after a successful JWT verification. Captures whichever side of the system the
 * caller belongs to (admin or resident) so controllers can apply ownership checks.
 *
 * Either {@code residentId} or {@code adminId} is set; never both.
 */
public class AuthenticatedUser implements Serializable {

    public enum Role { ADMIN, RESIDENT }

    private final Role role;
    private final Long userId;
    private final String displayName;
    private final String flatNo;

    private AuthenticatedUser(Role role, Long userId, String displayName, String flatNo) {
        this.role = Objects.requireNonNull(role, "role");
        this.userId = Objects.requireNonNull(userId, "userId");
        this.displayName = displayName;
        this.flatNo = flatNo;
    }

    public static AuthenticatedUser admin(Long adminId, String username) {
        return new AuthenticatedUser(Role.ADMIN, adminId, username, null);
    }

    public static AuthenticatedUser resident(Long residentId, String name, String flatNo) {
        Objects.requireNonNull(flatNo, "flatNo");
        return new AuthenticatedUser(Role.RESIDENT, residentId, name, flatNo);
    }

    public Role getRole() {
        return role;
    }

    public Long getUserId() {
        return userId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getFlatNo() {
        return flatNo;
    }

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }

    public boolean isResident() {
        return role == Role.RESIDENT;
    }

    /** Spring authority string ({@code ROLE_ADMIN} / {@code ROLE_RESIDENT}). */
    public String authority() {
        return "ROLE_" + role.name();
    }

    @Override
    public String toString() {
        return role + "(" + userId + (flatNo != null ? "," + flatNo : "") + ")";
    }
}
