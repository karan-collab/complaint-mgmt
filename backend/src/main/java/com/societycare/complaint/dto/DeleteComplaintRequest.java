package com.societycare.complaint.dto;

import com.societycare.complaint.DeletionReason;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

/**
 * Body for {@code POST /api/v1/complaints/{id}/delete}.
 *
 * The reason is mandatory so withdrawals can be counted by cause. Comments are
 * free text and are required only when the reason is {@code OTHER}, which on
 * its own says nothing.
 */
public class DeleteComplaintRequest {

    @NotNull(message = "reason is required")
    private DeletionReason reason;

    @Size(max = 500, message = "comments must be at most 500 characters")
    private String comments;

    public DeletionReason getReason() {
        return reason;
    }

    public void setReason(DeletionReason reason) {
        this.reason = reason;
    }

    public String getComments() {
        return comments;
    }

    public void setComments(String comments) {
        this.comments = comments;
    }
}
