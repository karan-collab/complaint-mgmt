#!/usr/bin/env bash
# Print a BCrypt hash for the given password (Spring Security compatible).
# Usage: ./hash-password.sh 'my-secret-password'
set -euo pipefail
PASSWORD="${1:?Usage: $0 <password>}"
python3 -c "import bcrypt; print(bcrypt.hashpw('${PASSWORD}'.encode(), bcrypt.gensalt(rounds=10)).decode())"
