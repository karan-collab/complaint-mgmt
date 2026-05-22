package com.societycare.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

/**
 * Issues and verifies HS256 JWTs used for stateless authentication.
 *
 * Token shape:
 * <pre>
 * {
 *   "iss": "societycare",
 *   "sub": "<userId>",
 *   "role": "ADMIN" | "RESIDENT",
 *   "name": "<displayName>",
 *   "flat": "<flatNo>"   // only when role=RESIDENT
 * }
 * </pre>
 *
 * The signing secret is supplied as a base64-encoded key (>= 256 bits) via
 * {@code security.jwt.secret}. TTL is {@code security.jwt.ttl-minutes}.
 */
@Service
public class JwtService {

    static final String CLAIM_ROLE = "role";
    static final String CLAIM_NAME = "name";
    static final String CLAIM_FLAT = "flat";

    private final SecretKey signingKey;
    private final Duration ttl;
    private final String issuer;

    public JwtService(@Value("${security.jwt.secret}") String base64Secret,
                      @Value("${security.jwt.ttl-minutes:120}") long ttlMinutes,
                      @Value("${security.jwt.issuer:societycare}") String issuer) {
        byte[] bytes = Decoders.BASE64.decode(base64Secret);
        this.signingKey = Keys.hmacShaKeyFor(bytes);
        this.ttl = Duration.ofMinutes(ttlMinutes);
        this.issuer = issuer;
    }

    public String issueForResident(Long residentId, String name, String flatNo) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(issuer)
                .subject(String.valueOf(residentId))
                .claim(CLAIM_ROLE, AuthenticatedUser.Role.RESIDENT.name())
                .claim(CLAIM_NAME, name)
                .claim(CLAIM_FLAT, flatNo)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .signWith(signingKey)
                .compact();
    }

    public String issueForAdmin(Long adminId, String username) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(issuer)
                .subject(String.valueOf(adminId))
                .claim(CLAIM_ROLE, AuthenticatedUser.Role.ADMIN.name())
                .claim(CLAIM_NAME, username)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .signWith(signingKey)
                .compact();
    }

    /**
     * Parses and verifies the supplied compact JWT.
     *
     * @throws JwtException if the token is malformed, tampered with or expired.
     */
    public AuthenticatedUser parse(String token) {
        Claims claims = Jwts.parser()
                .requireIssuer(issuer)
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        Long userId = Long.parseLong(claims.getSubject());
        String roleName = claims.get(CLAIM_ROLE, String.class);
        if (roleName == null) {
            throw new JwtException("Missing role claim");
        }
        AuthenticatedUser.Role role = AuthenticatedUser.Role.valueOf(roleName);
        String name = claims.get(CLAIM_NAME, String.class);

        if (role == AuthenticatedUser.Role.ADMIN) {
            return AuthenticatedUser.admin(userId, name);
        }
        String flat = claims.get(CLAIM_FLAT, String.class);
        if (flat == null) {
            throw new JwtException("Missing flat claim for resident token");
        }
        return AuthenticatedUser.resident(userId, name, flat);
    }

    public long ttlSeconds() {
        return ttl.getSeconds();
    }
}
