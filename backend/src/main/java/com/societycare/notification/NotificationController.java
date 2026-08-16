package com.societycare.notification;

import com.societycare.notification.dto.NotificationDto;
import com.societycare.notification.dto.UnreadCountDto;
import com.societycare.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Parameter;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * The bell. Every endpoint is scoped to the caller by their principal - a
 * resident can only ever reach their own feed, and there is no id parameter
 * anywhere that could be tampered with to reach somebody else's.
 */
@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'RESIDENT')")
    public List<NotificationDto> list(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser principal) {
        return notificationService.findFor(principal);
    }

    /** Polled by the frontend; the only thing the red dot needs. */
    @GetMapping("/unread-count")
    @PreAuthorize("hasAnyRole('ADMIN', 'RESIDENT')")
    public UnreadCountDto unreadCount(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser principal) {
        return new UnreadCountDto(notificationService.unreadCountFor(principal));
    }

    @PostMapping("/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('ADMIN', 'RESIDENT')")
    public void markAllRead(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser principal) {
        notificationService.markAllRead(principal);
    }
}
