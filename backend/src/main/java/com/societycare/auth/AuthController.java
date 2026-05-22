package com.societycare.auth;

import com.societycare.auth.dto.AdminLoginRequest;
import com.societycare.auth.dto.ChangePasswordRequest;
import com.societycare.auth.dto.LoginResponse;
import com.societycare.auth.dto.ResidentLoginRequest;
import com.societycare.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Parameter;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/resident/login")
    public LoginResponse residentLogin(@Valid @RequestBody ResidentLoginRequest request) {
        return authService.loginResident(request);
    }

    @PostMapping("/admin/login")
    public LoginResponse adminLogin(@Valid @RequestBody AdminLoginRequest request) {
        return authService.loginAdmin(request);
    }

    /**
     * Lightweight echo endpoint that lets the SPA verify a stored token is still
     * valid and recover the user's profile after a page refresh.
     */
    @GetMapping("/me")
    public Map<String, Object> me(@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user) {
        Map<String, Object> body = new HashMap<>();
        body.put("role", user.getRole().name());
        body.put("userId", user.getUserId());
        body.put("displayName", user.getDisplayName());
        if (user.isResident()) {
            body.put("flatNo", user.getFlatNo());
        }
        return body;
    }

    /**
     * Self-service password change. Works for both residents and admins; the
     * principal's role determines which table is updated.
     */
    @PostMapping("/me/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('ADMIN', 'RESIDENT')")
    public void changeMyPassword(@Valid @RequestBody ChangePasswordRequest request,
                                 @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user) {
        authService.changeMyPassword(user, request);
    }
}
