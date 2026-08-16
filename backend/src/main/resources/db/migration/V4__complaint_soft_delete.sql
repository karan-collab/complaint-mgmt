-- Soft delete for complaints.
--
-- A withdrawn complaint is kept so the society can see how many were deleted
-- and why, instead of the row vanishing. Status 4 marks it; the professional
-- and assigned_at are left as they were at the moment of deletion.

INSERT INTO t_status (status_id, status_name) VALUES (4, 'Deleted');

ALTER TABLE t_complaint ADD COLUMN deletion_reason   VARCHAR(32);
ALTER TABLE t_complaint ADD COLUMN deletion_comments TEXT;
ALTER TABLE t_complaint ADD COLUMN deleted_at        TIMESTAMP WITH TIME ZONE;

-- The V1 invariant only permitted statuses 1-3, so a deleted row would be
-- rejected. Recreate it with a fourth branch: a deleted complaint may or may
-- not have had a worker assigned, but it was never completed.
ALTER TABLE t_complaint DROP CONSTRAINT chk_complaint_status_invariant;

ALTER TABLE t_complaint ADD CONSTRAINT chk_complaint_status_invariant CHECK (
    (status_id = 1 AND professional_id IS NULL     AND assigned_at IS NULL     AND completed_at IS NULL) OR
    (status_id = 2 AND professional_id IS NOT NULL AND assigned_at IS NOT NULL AND completed_at IS NULL) OR
    (status_id = 3 AND professional_id IS NOT NULL AND assigned_at IS NOT NULL AND completed_at IS NOT NULL) OR
    (status_id = 4 AND completed_at IS NULL AND deleted_at IS NOT NULL AND deletion_reason IS NOT NULL)
);
