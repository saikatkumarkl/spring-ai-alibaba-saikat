#!/bin/sh
# Entrypoint wrapper: fix volume ownership, then start the app as 'spring'
# Docker volumes are mounted as root — this ensures the spring user can write.

set -e

STORAGE_DIR="/home/spring/saa/storage"
LOG_DIR="/home/spring/logs"

# Fix ownership of mounted volumes (runs as root at this point)
chown -R spring:spring "$STORAGE_DIR" "$LOG_DIR" /home/spring/saa 2>/dev/null || true

# Drop to spring user and exec the JVM
exec gosu spring java $JAVA_OPTS -jar /app/app.jar "$@"
