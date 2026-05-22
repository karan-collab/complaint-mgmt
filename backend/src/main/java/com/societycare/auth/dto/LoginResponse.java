package com.societycare.auth.dto;

/**
 * Returned from the login endpoints. {@code expiresInSeconds} mirrors the
 * configured TTL so the client can schedule a renewal without parsing the JWT.
 */
public class LoginResponse {

    private final String token;
    private final String tokenType;
    private final long expiresInSeconds;
    private final String role;
    private final Long userId;
    private final String displayName;
    private final String flatNo;

    private LoginResponse(String token, String tokenType, long expiresInSeconds,
                          String role, Long userId, String displayName, String flatNo) {
        this.token = token;
        this.tokenType = tokenType;
        this.expiresInSeconds = expiresInSeconds;
        this.role = role;
        this.userId = userId;
        this.displayName = displayName;
        this.flatNo = flatNo;
    }

    public static LoginResponse forResident(String token, long expiresInSeconds,
                                            Long residentId, String name, String flatNo) {
        return new LoginResponse(token, "Bearer", expiresInSeconds, "RESIDENT", residentId, name, flatNo);
    }

    public static LoginResponse forAdmin(String token, long expiresInSeconds,
                                         Long adminId, String username) {
        return new LoginResponse(token, "Bearer", expiresInSeconds, "ADMIN", adminId, username, null);
    }

    public String getToken() {
        return token;
    }

    public String getTokenType() {
        return tokenType;
    }

    public long getExpiresInSeconds() {
        return expiresInSeconds;
    }

    public String getRole() {
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
}
