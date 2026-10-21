#!/usr/bin/env bash
# Runs every migration in order, then the seed data.
# Usage (from the project root):
#   export DB_PASS='yourpassword'
#   bash database/setup.sh
#   bash database/setup.sh --reset     (drops all tables first)

DB_USER="${DB_USER:-argdev}"

if [ -z "$DB_PASS" ]; then
  echo "Set DB_PASS first:  export DB_PASS='yourpassword'"
  exit 1
fi

run_sql() {
  echo "Running $1"
  MYSQL_PWD="$DB_PASS" mysql -h 127.0.0.1 -u "$DB_USER" < "$1" || { echo "FAILED: $1"; echo "If it says Duplicate entry, run:  bash database/Setup.sh --reset"; exit 1; }
}

if [ "$1" = "--reset" ]; then
  run_sql database/reset.sql
fi

for f in database/Migration/*.sql; do run_sql "$f"; done
for f in database/Seed/*.sql; do run_sql "$f"; done

echo "Done. Tables:"
MYSQL_PWD="$DB_PASS" mysql -h 127.0.0.1 -u "$DB_USER" -e "USE adaptive_reading; SHOW TABLES;"
