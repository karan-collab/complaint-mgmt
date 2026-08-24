-- Management is told when a resident shares a suggestion, so a notification now
-- has to be able to point at something other than a complaint.
--
-- This widens t_notification from "notifications about complaints" to
-- "notifications about a subject". The subject CHECK keeps that honest: exactly
-- one of complaint_id / suggestion_id is set, never both and never neither, so
-- a row always has something to be about and the mapper never has to guess.
--
-- DROP NOT NULL is PostgreSQL's spelling; H2 2.x accepts it too (verified
-- against the 2.1.214 the dev profile runs), so one statement serves both
-- engines. H2's own spelling, SET NULL, is the one Postgres would reject.
ALTER TABLE t_notification ALTER COLUMN complaint_id DROP NOT NULL;

ALTER TABLE t_notification
    ADD COLUMN suggestion_id BIGINT REFERENCES t_suggestion(suggestion_id);

ALTER TABLE t_notification ADD CONSTRAINT chk_notification_subject CHECK (
    (complaint_id IS NOT NULL AND suggestion_id IS NULL) OR
    (complaint_id IS NULL     AND suggestion_id IS NOT NULL)
);

-- Supports the purge that runs when a resident is deleted: their suggestions
-- are about to go, so the notifications pointing at them must go first.
CREATE INDEX idx_notification_suggestion_id ON t_notification(suggestion_id);
