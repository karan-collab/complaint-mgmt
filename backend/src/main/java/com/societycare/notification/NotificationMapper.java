package com.societycare.notification;

import com.societycare.complaint.Complaint;
import com.societycare.notification.dto.NotificationDto;
import com.societycare.suggestion.Suggestion;
import org.springframework.stereotype.Component;

@Component
public class NotificationMapper {

    public NotificationDto toDto(Notification notification) {
        if (notification == null) return null;

        Complaint complaint = notification.getComplaint();
        NotificationDto dto = new NotificationDto();
        dto.setId(notification.getNotificationId());
        dto.setType(notification.getType());
        dto.setMessage(notification.getMessage());
        dto.setCreatedAt(notification.getCreatedAt());
        dto.setRead(notification.getReadAt() != null);
        if (complaint != null) {
            dto.setComplaintId(complaint.getComplaintId());
            dto.setFlatNo(complaint.getResident().getFlatNo());
            dto.setCategory(complaint.getCategory().getLabel());
        }
        // A suggestion has no category - it is not filed against a trade - so
        // the panel shows the flat alone for these rows.
        Suggestion suggestion = notification.getSuggestion();
        if (suggestion != null) {
            dto.setSuggestionId(suggestion.getSuggestionId());
            dto.setFlatNo(suggestion.getResident().getFlatNo());
        }
        return dto;
    }
}
