package com.societycare.notification;

import com.societycare.complaint.Complaint;
import com.societycare.notification.dto.NotificationDto;
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
        return dto;
    }
}
