package com.societycare.status;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import org.hibernate.annotations.Immutable;

/**
 * Lookup table for the complaint statuses. Rows are owned by Flyway
 * (V2__seed_status.sql) and never modified at runtime, hence @Immutable.
 *
 * Stable IDs:
 *   1 = Assignment Pending
 *   2 = Pending Work
 *   3 = Complete
 *   4 = Deleted (withdrawn by the resident; kept for the record)
 */
@Entity
@Immutable
@Table(name = "t_status")
public class Status {

    public static final int ASSIGNMENT_PENDING = 1;
    public static final int PENDING_WORK = 2;
    public static final int COMPLETE = 3;
    public static final int DELETED = 4;

    @Id
    @Column(name = "status_id")
    private Integer statusId;

    @Column(name = "status_name", nullable = false, length = 32)
    private String statusName;

    public Status() {
    }

    public Integer getStatusId() {
        return statusId;
    }

    public String getStatusName() {
        return statusName;
    }
}
