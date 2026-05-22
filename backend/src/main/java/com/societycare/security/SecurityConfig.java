package com.societycare.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.societycare.common.ApiError;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Stateless JWT-based security:
 *
 * <ul>
 *   <li>{@code POST /api/v1/auth/**} is open (login endpoints).</li>
 *   <li>Swagger, the H2 console and {@code /actuator/health} are open.</li>
 *   <li>Everything else under {@code /api/**} requires a valid JWT.</li>
 *   <li>{@code @PreAuthorize} is enabled for fine-grained role/ownership checks.</li>
 * </ul>
 */
@Configuration
@EnableGlobalMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final ObjectMapper objectMapper;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter, ObjectMapper objectMapper) {
        this.jwtAuthFilter = jwtAuthFilter;
        this.objectMapper = objectMapper;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf().disable()
                .cors().and()
                .sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS).and()
                .headers().frameOptions().sameOrigin().and()
                .authorizeRequests()
                    .antMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                    .antMatchers(HttpMethod.POST,
                            "/api/v1/auth/resident/login",
                            "/api/v1/auth/admin/login").permitAll()
                    .antMatchers(HttpMethod.GET, "/api/v1/meta/**").permitAll()
                    .antMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                    .antMatchers("/actuator/health", "/actuator/info").permitAll()
                    .antMatchers("/h2-console/**").permitAll()
                    .anyRequest().authenticated()
                .and()
                .exceptionHandling()
                    .authenticationEntryPoint(authEntryPoint())
                    .accessDeniedHandler(accessDeniedHandler())
                .and()
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    private AuthenticationEntryPoint authEntryPoint() {
        return (HttpServletRequest req, HttpServletResponse res, org.springframework.security.core.AuthenticationException ex) ->
                writeError(res, HttpStatus.UNAUTHORIZED, "Unauthenticated",
                        "Authentication is required to access this resource", req.getRequestURI());
    }

    private AccessDeniedHandler accessDeniedHandler() {
        return (HttpServletRequest req, HttpServletResponse res, AccessDeniedException ex) ->
                writeError(res, HttpStatus.FORBIDDEN, "Access denied",
                        "You do not have permission to perform this action", req.getRequestURI());
    }

    private void writeError(HttpServletResponse res, HttpStatus status,
                            String title, String detail, String instance) throws java.io.IOException {
        ApiError body = new ApiError(title, status.value(), detail, instance);
        res.setStatus(status.value());
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(res.getOutputStream(), body);
    }
}
