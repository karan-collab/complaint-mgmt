-- In-app notifications: one row per event a recipient should be told about.
--
-- Recipients come in two kinds:
--   RESIDENT - addressed to one resident        (resident_id set)
--   ADMIN    - addressed to management as a whole (resident_id NULL)
--
-- Management is a single logical inbox rather than one row per admin account.
-- The app has one management login, the topbar shows it as "Management", and
-- the question a society actually asks is "has anyone seen this yet", not "has
-- this particular admin seen it". If per-admin inboxes are ever wanted, add an
-- admin_id column alongside and widen the CHECK below.
--
-- message is stored, not derived at read time. A notification is a record of
-- what was true when it fired: re-deriving "Suresh Patel has been assigned"
-- from the complaint would silently rewrite the history of an old notification
-- as soon as the ticket is reassigned to somebody else.
--
-- complaint_id has no ON DELETE CASCADE on purpose. Complaint deletion is a
-- soft delete (status 4), so the only hard delete in the system is resident
-- removal, and ResidentService clears these rows explicitly - the deletion
-- chain stays visible in the Java rather than hiding in the schema.
CREATE TABLE t_notification (
    notification_id BIGSERIAL    PRIMARY KEY,
    recipient_type  VARCHAR(16)  NOT NULL,
    resident_id     BIGINT       REFERENCES t_resident(resident_id),
    complaint_id    BIGINT       NOT NULL REFERENCES t_complaint(complaint_id),
    type            VARCHAR(32)  NOT NULL,
    message         VARCHAR(255) NOT NULL,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    read_at         TIMESTAMP WITH TIME ZONE,

    CONSTRAINT chk_notification_recipient CHECK (
        (recipient_type = 'RESIDENT' AND resident_id IS NOT NULL) OR
        (recipient_type = 'ADMIN'    AND resident_id IS NULL)
    )
);

-- Unread-count queries are the hot path: they run on every poll, for every
-- signed-in user, and are always filtered by recipient plus read_at IS NULL.
CREATE INDEX idx_notification_resident   ON t_notification(resident_id, read_at);
CREATE INDEX idx_notification_recipient  ON t_notification(recipient_type, read_at);

-- Supports the 45-day retention sweep.
CREATE INDEX idx_notification_created_at ON t_notification(created_at);
