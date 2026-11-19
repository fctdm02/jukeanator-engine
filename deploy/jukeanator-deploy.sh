#!/bin/bash
# Server side of deploy/deploy-prod.ps1 (see "Updating the application" in MasterConfig.md).
# Installs /opt/jukeanator/jukeanator-engine.war.new as the running application:
#   1. checks the uploaded WAR against the SHA-256 the PC computed (catches a broken upload);
#   2. backs up the database (jukeanator-db-backup), since a new version may change the schema;
#   3. keeps the current WAR as jukeanator-engine.war.previous and switches to the new one;
#   4. restarts the service and waits for the application to answer;
#   5. if it does not answer in time, puts the previous WAR back and restarts that (exit 1).
# Usage (as root): jukeanator-deploy.sh <sha256-of-new-war>
set -euo pipefail

DIR=/opt/jukeanator
NEW=$DIR/jukeanator-engine.war.new
CUR=$DIR/jukeanator-engine.war
PREV=$DIR/jukeanator-engine.war.previous
HEALTH_URL=http://127.0.0.1:8080/
WAIT_SECONDS=240

EXPECTED=${1:-}
if [ -z "$EXPECTED" ]; then
  echo "Usage: $0 <sha256-of-new-war>" >&2
  exit 2
fi

# One deploy at a time.
exec 9>/run/jukeanator-deploy.lock
flock -n 9 || { echo "Another deploy is running" >&2; exit 1; }

[ -s "$NEW" ] || { echo "No uploaded WAR at $NEW" >&2; exit 1; }
ACTUAL=$(sha256sum "$NEW" | cut -d' ' -f1)
if [ "${ACTUAL,,}" != "${EXPECTED,,}" ]; then
  echo "The uploaded WAR is damaged (SHA-256 mismatch); nothing was changed" >&2
  rm -f "$NEW"
  exit 1
fi

wait_for_app() {
  local i code
  for i in $(seq 1 $((WAIT_SECONDS / 3))); do
    sleep 3
    code=$(curl -s -o /dev/null -w '%{http_code}' --max-time 3 "$HEALTH_URL" || true)
    [ "$code" = "200" ] && return 0
  done
  return 1
}

echo "Backing up the database..."
systemctl start jukeanator-db-backup.service
journalctl -u jukeanator-db-backup.service -n 20 -o cat --no-pager | grep 'Backup written' | tail -1 || true

echo "Installing the new version..."
cp -p "$CUR" "$PREV"
mv "$NEW" "$CUR"
chmod 644 "$CUR"
systemctl restart jukeanator

echo "Waiting for the application to start..."
if wait_for_app; then
  echo "Deployed: application is answering (service $(systemctl is-active jukeanator))"
  exit 0
fi

echo "The new version did not start within ${WAIT_SECONDS}s; restoring the previous version." >&2
journalctl -u jukeanator -n 40 --no-pager >&2 || true
cp -p "$PREV" "$CUR"
systemctl restart jukeanator
if wait_for_app; then
  echo "Rolled back: the previous version is running again." >&2
else
  echo "Rolled back, but the previous version is not answering either; check the log." >&2
fi
exit 1
