-- Free-text feedback a resident sends to management.
--
-- Unlike a complaint this has no lifecycle: it is written, it is read, and that
-- is the whole of it. So there is no status column, no professional, and no
-- CHECK constraint - nothing here can be in an inconsistent state.
--
-- flat_no is deliberately NOT copied onto this table even though the admin
-- screen filters by flat. A flat number belongs to the resident and an admin can
-- correct it; complaints already read it live through the join (see
-- ComplaintMapper), and denormalising it here would let the two disagree about
-- who lives where.
--
-- resident_id carries no ON DELETE CASCADE, matching t_complaint and
-- t_notification. Resident removal is the only hard delete in the system and
-- ResidentService clears these rows explicitly, so the deletion chain stays
-- readable in one Java method instead of hiding in the schema.
CREATE TABLE t_suggestion (
    suggestion_id BIGSERIAL PRIMARY KEY,
    resident_id   BIGINT    NOT NULL REFERENCES t_resident(resident_id),
    suggestion    TEXT      NOT NULL,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- The admin list is "everything, newest first"; the resident index supports the
-- purge that runs when a resident is removed.
CREATE INDEX idx_suggestion_resident_id     ON t_suggestion(resident_id);
CREATE INDEX idx_suggestion_created_at_desc ON t_suggestion(created_at DESC);
