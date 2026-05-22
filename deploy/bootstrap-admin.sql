-- One-time bootstrap: create the first admin user (password: admin).
-- Generate a new hash for production: use deploy/scripts/hash-password.sh
-- BCrypt for "admin" (cost 10):
INSERT INTO t_admin (username, password_hash)
VALUES (
  'admin',
  '$2b$10$nUGt8iukBpVHSNg4JIeCuedjQiUccH0BSv7IRwKMLJ4QVERuoP6v2'
)
ON CONFLICT (username) DO NOTHING;
