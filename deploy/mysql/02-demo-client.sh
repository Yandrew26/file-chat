#!/bin/bash
# Creates one gateway API client from FILECHAT_DEMO_AUTH_ID / FILECHAT_DEMO_AUTH_SECRET, if both are set.
# Runs once, on first start of an empty MySQL volume. Nothing is created when they are unset.
set -euo pipefail

if [[ -z "${FILECHAT_DEMO_AUTH_ID:-}" || -z "${FILECHAT_DEMO_AUTH_SECRET:-}" ]]; then
  echo "02-demo-client: FILECHAT_DEMO_AUTH_ID/FILECHAT_DEMO_AUTH_SECRET not set, no API client created"
  exit 0
fi
if [[ ! "$FILECHAT_DEMO_AUTH_ID" =~ ^[A-Za-z0-9_-]{1,64}$ || ! "$FILECHAT_DEMO_AUTH_SECRET" =~ ^[A-Za-z0-9_-]{16,128}$ ]]; then
  echo "02-demo-client: auth id must be 1-64 chars and the secret 16-128 chars, using only letters, digits, _ and -" >&2
  exit 1
fi

# Values are validated above, so they are safe to embed in the statement.
MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot "$MYSQL_DATABASE" <<SQL
INSERT IGNORE INTO rag_auth_base (auth_name, auth_id, secret_key, enabled_status)
VALUES ('local-client', '${FILECHAT_DEMO_AUTH_ID}', '${FILECHAT_DEMO_AUTH_SECRET}', 1);
SQL
echo "02-demo-client: created API client '${FILECHAT_DEMO_AUTH_ID}'"
