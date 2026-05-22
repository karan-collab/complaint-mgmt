package com.societycare.support;

import com.societycare.security.AuthenticatedUser;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.Collection;
import java.util.Collections;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;

/**
 * Test-only helpers that wire an {@link AuthenticatedUser} principal into the
 * Spring Security context so {@code @PreAuthorize} and {@code @AuthenticationPrincipal}
 * behave the same way as in production.
 */
public final class TestAuth {

    private TestAuth() {
    }

    public static RequestPostProcessor asAdmin() {
        return asAdmin(1L, "admin");
    }

    public static RequestPostProcessor asAdmin(Long adminId, String username) {
        return authentication(toAuth(AuthenticatedUser.admin(adminId, username)));
    }

    public static RequestPostProcessor asResident(Long residentId, String name, String flatNo) {
        return authentication(toAuth(AuthenticatedUser.resident(residentId, name, flatNo)));
    }

    public static RequestPostProcessor asResident(String flatNo) {
        return asResident(1L, "Test Resident", flatNo);
    }

    private static Authentication toAuth(AuthenticatedUser user) {
        Collection<GrantedAuthority> authorities =
                Collections.singletonList(new SimpleGrantedAuthority(user.authority()));
        return new TestAuthenticationToken(user, authorities);
    }

    private static final class TestAuthenticationToken extends AbstractAuthenticationToken {

        private final AuthenticatedUser principal;

        TestAuthenticationToken(AuthenticatedUser principal,
                                Collection<? extends GrantedAuthority> authorities) {
            super(authorities);
            this.principal = principal;
            super.setAuthenticated(true);
        }

        @Override
        public Object getCredentials() {
            return "";
        }

        @Override
        public Object getPrincipal() {
            return principal;
        }
    }
}
