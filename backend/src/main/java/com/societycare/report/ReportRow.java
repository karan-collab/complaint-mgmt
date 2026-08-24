package com.societycare.report;

import java.time.OffsetDateTime;

/**
 * One line of the management report.
 *
 * Complaints and suggestions are flattened into a single shape so the sheet is
 * one flat table: a row carries either a complaint or a suggestion, and the
 * columns belonging to the other kind are left null. That is what makes the
 * export pivot cleanly in Excel - the Type column is the discriminator, so
 * nobody has to test a cell for blankness to work out what they are looking at.
 */
public class ReportRow {

    public static final String TYPE_COMPLAINT = "Complaint";
    public static final String TYPE_SUGGESTION = "Suggestion";

    private final String type;
    private final Long id;
    private final String flatNo;
    private final String residentName;
    private final String category;
    private final String complaint;
    private final String suggestion;
    private final String status;
    private final String assignedTo;
    private final OffsetDateTime createdAt;
    private final OffsetDateTime assignedAt;
    private final OffsetDateTime completedAt;

    private ReportRow(String type, Long id, String flatNo, String residentName, String category,
                      String complaint, String suggestion, String status, String assignedTo,
                      OffsetDateTime createdAt, OffsetDateTime assignedAt, OffsetDateTime completedAt) {
        this.type = type;
        this.id = id;
        this.flatNo = flatNo;
        this.residentName = residentName;
        this.category = category;
        this.complaint = complaint;
        this.suggestion = suggestion;
        this.status = status;
        this.assignedTo = assignedTo;
        this.createdAt = createdAt;
        this.assignedAt = assignedAt;
        this.completedAt = completedAt;
    }

    public static ReportRow complaint(Long id, String flatNo, String residentName, String category,
                                      String description, String status, String assignedTo,
                                      OffsetDateTime createdAt, OffsetDateTime assignedAt,
                                      OffsetDateTime completedAt) {
        return new ReportRow(TYPE_COMPLAINT, id, flatNo, residentName, category, description,
                null, status, assignedTo, createdAt, assignedAt, completedAt);
    }

    /**
     * A suggestion has no category, no status and no worker: it is read, not
     * worked on. Those columns stay empty rather than carrying a placeholder,
     * so a COUNTA or a pivot over them counts complaints only.
     */
    public static ReportRow suggestion(Long id, String flatNo, String residentName,
                                       String text, OffsetDateTime createdAt) {
        return new ReportRow(TYPE_SUGGESTION, id, flatNo, residentName, null,
                null, text, null, null, createdAt, null, null);
    }

    public String getType() {
        return type;
    }

    /**
     * Not a column on the sheet - the report does not expose ticket numbers.
     * It is kept so the ordering can break ties on it: two rows can share a
     * timestamp to the millisecond, and without a tiebreaker the same data
     * would come out in a different order from one run to the next.
     */
    public Long getId() {
        return id;
    }

    public String getFlatNo() {
        return flatNo;
    }

    public String getResidentName() {
        return residentName;
    }

    public String getCategory() {
        return category;
    }

    public String getComplaint() {
        return complaint;
    }

    public String getSuggestion() {
        return suggestion;
    }

    public String getStatus() {
        return status;
    }

    public String getAssignedTo() {
        return assignedTo;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getAssignedAt() {
        return assignedAt;
    }

    public OffsetDateTime getCompletedAt() {
        return completedAt;
    }
}
