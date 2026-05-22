package com.societycare.security;

import io.jsonwebtoken.JwtException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;

/**
 * Reads {@code Authorization: Bearer <jwt>} from each request and, when valid,
 * populates the Spring {@link SecurityContextHolder} with an authentication
 * carrying our {@link AuthenticatedUser} principal and a {@code ROLE_*} authority.
 *
 * Invalid tokens are silently ignored: {@link SecurityConfig} converts the
 * resulting anonymous request into a 401 via the entry point.
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthFilter.class);
    private static final String HEADER = "Authorization";
    private static final String PREFIX = "Bearer ";

    private final JwtService jwtService;

    public JwtAuthFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader(HEADER);
        if (header != null && header.startsWith(PREFIX)
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            String token = header.substring(PREFIX.length()).trim();
            try {
                AuthenticatedUser user = jwtService.parse(token);
                GrantedAuthority authority = new SimpleGrantedAuthority(user.authority());
                AbstractAuthenticationToken auth =
                        new JwtAuthenticationToken(user, Collections.singletonList(authority));
                auth.setDetails(request.getRemoteAddr());
                SecurityContextHolder.getContext().setAuthentication(auth);
            } catch (JwtException | IllegalArgumentException ex) {
                log.debug("Rejecting JWT: {}", ex.getMessage());
            }
        }
        filterChain.doFilter(request, response);
    }

    /** Authenticated marker for our custom principal. */
    static final class JwtAuthenticationToken extends AbstractAuthenticationToken {

        private final AuthenticatedUser principal;

        JwtAuthenticationToken(AuthenticatedUser principal,
                               java.util.Collection<? extends GrantedAuthority> authorities) {
            super(authorities);
            this.principal = principal;
            super.setAuthenticated(true);
        }

        @Override
        public Object getCredentials() {
            return ""; // credentials are intentionally not retained after JWT verification
        }

        @Override
        public Object getPrincipal() {
            return principal;
        }
    }
}
