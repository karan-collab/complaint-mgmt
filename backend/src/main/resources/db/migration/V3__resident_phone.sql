-- Contact number for a resident. Optional: residents created before this
-- migration (and any created without one) simply have NULL.
ALTER TABLE t_resident ADD COLUMN phone VARCHAR(32);
