package com.societycare.suggestion;

import com.societycare.resident.Resident;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.PrePersist;
import javax.persistence.Table;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * A piece of free-text feedback from one resident to management.
 *
 * There is no status and no assignee: a suggestion is read, not worked on. If
 * it ever needs a reply or an "acknowledged" flag, that is a new column here
 * rather than a reshaping of the table.
 */
@Entity
@Table(name = "t_suggestion")
public class Suggestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "suggestion_id")
    private Long suggestionId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "resident_id", nullable = false)
    private Resident resident;

    @Column(name = "suggestion", nullable = false, columnDefinition = "TEXT")
    private String suggestion;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    public Suggestion() {
    }

    public Suggestion(Resident resident, String suggestion) {
        this.resident = resident;
        this.suggestion = suggestion;
    }

    // Set here rather than left to the column default so the entity that comes
    // back from save() already carries the timestamp the caller will serialise.
    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now(ZoneOffset.UTC);
        }
    }

    public Long getSuggestionId() {
        return suggestionId;
    }

    public Resident getResident() {
        return resident;
    }

    public String getSuggestion() {
        return suggestion;
    }

    public void setSuggestion(String suggestion) {
        this.suggestion = suggestion;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
