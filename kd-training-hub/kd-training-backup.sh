#!/bin/bash
#
# Nightly backup: the whole database, the uploads volume, encrypted off-site.
#
# Runs from root's crontab at 03:00 — see the install note at the bottom of this file. It used to
# live only on the production box, which meant a rebuild of that box lost it: the one file in the
# backup system that had no backup. It ships from the repo now, the same way nginx.conf and
# setup-swap.sh do.
#
# Three rules this script exists to keep, each one learned the hard way somewhere:
#
#   1. NEVER let the off-site copy mirror deletions. `rclone sync` makes the destination identical
#      to the source, deletions included — so anything that wipes /backups here (a bad disk, a
#      stray rm, ransomware) reaches Google Drive on the next run, and a mistake nobody notices for
#      a week is unrecoverable because the copies from before it were pruned. `copy` only ever adds;
#      the remote is pruned separately and far more slowly.
#   2. NEVER publish a dump that did not finish. The shell creates the output file before pg_dump
#      writes a byte, so an interrupted dump leaves something that looks exactly like a backup — and
#      a truncated dump is usually still a VALID gzip, so testing the compression proves nothing.
#      Work goes to a .part file, is checked for pg_dump's own end marker, and only then takes the
#      real name.
#   3. NEVER rely on being told about failure. Cron mails root, root's mail goes nowhere on a cloud
#      box, and `set -e` exits in silence. A backup that stopped running in March is discovered in
#      August. The ping below inverts that: an external service expects to hear from us daily and
#      raises the alarm when it does not — which also covers the cases no failure mail could ever
#      report, like the machine being off or the cron entry being gone.

set -euo pipefail

DATE=$(date +%Y-%m-%d)
DB_DIR="/backups/db"
FILES_DIR="/backups/files"
DB_BACKUP="${DB_DIR}/${DATE}.sql.gz"
FILES_BACKUP="${FILES_DIR}/${DATE}.tar.gz"
COMPOSE_DIR="/opt/kd-training"
# Docker prefixes compose volumes with the project name, which defaults to the directory the compose
# file sits in. So this name is a guess about a path, and a wrong guess does not fail: `docker run -v
# <unknown-name>:/data` CREATES an empty volume and tars nothing. The archive is then a valid,
# readable, empty tar.gz — it passes the `tar tzf` check below, publishes, and copies off-site every
# night. The uploads would be gone and the backup log would say OK the whole time. Hence the
# existence check further down. Override in /etc/kd-training-backup.env if the project is renamed.
UPLOADS_VOLUME="${UPLOADS_VOLUME:-kd-training_kd_uploads_data_prod}"
LOG="/var/log/kd-training-backup.log"
REMOTE="kd-crypt:"

# How long copies live. Local is short because it is only a staging area and shares the disk with
# the database; off-site is longer because that is the copy you reach for when you discover a problem
# late, and "late" is the whole reason it exists. 40 days, not 90: inherited from the project this one
# was forked from, where one 15 GB Google Drive account was shared by several projects and 90 days of
# daily upload archives would have filled it (measured 2026-10-09). Revisit once this project's own
# Drive and sizes are known.
LOCAL_RETENTION_DAYS=7
REMOTE_RETENTION_DAYS=40

# The uploads archive is only made when the uploads changed. Every archive is a complete copy, so a
# nightly archive of the same files spends Drive space for nothing. Here the saving may be modest:
# training photos (uploads/trainingphotos, same volume) can arrive any day and expire after 30 days
# (TrainingPhotoRetentionScheduler), so every upload or expiry means a new archive — how many
# nights stay unchanged is not measured yet; the log says so on each run. Each archive is still
# FULL, never incremental: a restore is the newest dump plus the newest uploads archive dated on or
# before it — no newer archive means precisely that nothing changed.
#
# The state file holds a fingerprint of the volume (path, size and mtime of every file). Even with no
# change an archive is made every FILES_REFRESH_DAYS days, which must stay below
# REMOTE_RETENTION_DAYS, or the remote prune would delete the only archive there is. No state file
# (a new server) means an archive straight away.
FILES_REFRESH_DAYS=30
FILES_STATE="/var/lib/kd-training-backup/files-state"

# Optional, and kept OUT of this file on purpose: the ping URL is a shared secret, and anyone
# holding it can report a success we never had. Put HEALTHCHECK_URL=... in this file on the server,
# readable by root only.
ENV_FILE="/etc/kd-training-backup.env"
# shellcheck source=/dev/null
[ -f "$ENV_FILE" ] && . "$ENV_FILE"
HEALTHCHECK_URL="${HEALTHCHECK_URL:-}"

log() { echo "$(date '+%Y-%m-%d %H:%M:%S') $*" >> "$LOG"; }

ping_healthcheck() {
    [ -n "$HEALTHCHECK_URL" ] || return 0
    # Never let the alarm bell take the backup down with it: this is best-effort by design.
    curl -fsS -m 10 --retry 3 "${HEALTHCHECK_URL}$1" >/dev/null 2>&1 || \
        log "WARN: could not reach the health check endpoint"
}

# Fires on any failed command, because of set -e. Tells the monitor immediately rather than leaving
# it to notice the silence hours later.
on_failure() {
    local line=$1
    log "FAILED at line ${line}"
    ping_healthcheck "/fail"
}
trap 'on_failure $LINENO' ERR

mkdir -p "$DB_DIR" "$FILES_DIR" "$(dirname "$FILES_STATE")"
log "=== Backup start ==="

if [ "$FILES_REFRESH_DAYS" -ge "$REMOTE_RETENTION_DAYS" ]; then
    log "FAILED: FILES_REFRESH_DAYS (${FILES_REFRESH_DAYS}) must be below REMOTE_RETENTION_DAYS (${REMOTE_RETENTION_DAYS}) — Drive would be left without an uploads archive"
    ping_healthcheck "/fail"
    exit 1
fi

# --- database -----------------------------------------------------------------------------------

log "DB backup..."
docker compose -f "${COMPOSE_DIR}/docker-compose.prod.yml" exec -T postgres \
    pg_dump -U kdtraining kdtraining | gzip > "${DB_BACKUP}.part"

# pg_dump signs off with its own end marker. Its presence is the only cheap proof that the database
# reached the end of the dump instead of dying halfway through a table.
#
# The window is 20 lines, not 5, because the marker is NOT the last thing in the file and what
# follows it grows with the server version. Postgres 17 began appending a `\unrestrict <token>`
# line after it, which put the marker at exactly line 5 of 5 — a passing check with zero margin,
# measured on 2026-09-06 during the move to Postgres 18. One more trailing line from any future
# release and this test starts calling every good dump corrupt, deleting it and publishing none.
# The failure would at least be loud (it pings /fail), but it would stop backups completely, on a
# routine database upgrade, for no reason. Twenty lines costs nothing and does not weaken the
# check: a dump truncated mid-table has no marker anywhere near its end.
#
# The tail goes into a variable before grep looks at it. `grep -q` at the end of a pipeline exits on
# its first match; if `tail` is still writing it gets SIGPIPE, and under pipefail the whole test
# fails — a good dump reported as truncated. (Plain grep writing to /dev/null stops early too.)
DUMP_TAIL=$(gunzip -c "${DB_BACKUP}.part" | tail -20)
if ! grep -q "PostgreSQL database dump complete" <<<"$DUMP_TAIL"; then
    log "FAILED: dump has no completion marker — refusing to publish it"
    rm -f "${DB_BACKUP}.part"
    ping_healthcheck "/fail"
    exit 1
fi

mv "${DB_BACKUP}.part" "$DB_BACKUP"
log "DB OK: $(du -sh "$DB_BACKUP" | cut -f1)"

# --- uploaded files -----------------------------------------------------------------------------

log "Files backup..."

# Assert the volume exists before reading it. Without this the only symptom of a wrong name is an
# empty archive that looks entirely healthy — see the note by UPLOADS_VOLUME.
if ! docker volume inspect "$UPLOADS_VOLUME" >/dev/null 2>&1; then
    log "FAILED: uploads volume '${UPLOADS_VOLUME}' does not exist — refusing to back up nothing"
    log "        available: $(docker volume ls --format '{{.Name}}' | grep -i uploads | tr '\n' ' ')"
    ping_healthcheck "/fail"
    exit 1
fi

# Fingerprint of the volume: path|size|mtime of every file, sorted and hashed. Adding, removing or
# replacing an upload changes it. Taken BEFORE the archive: a file added in between lands in the
# archive and changes tomorrow's fingerprint — at worst one archive too many, never one too few.
FILES_FP=$(docker run --rm -v "${UPLOADS_VOLUME}:/data:ro" alpine \
    sh -c 'cd /data && find . -type f -exec stat -c "%n|%s|%Y" {} + | sort' \
    | sha256sum | cut -d' ' -f1)

PREV_FP=""
PREV_AT=0
if [ -r "$FILES_STATE" ]; then
    read -r PREV_FP PREV_AT < "$FILES_STATE" || true
fi
# A damaged state file (a write cut short by a full disk) must not break every night's backup:
# anything that is not a number counts as no state, so an archive is made and the write after it
# repairs the file.
case "$PREV_AT" in
    ''|*[!0-9]*) PREV_FP=""; PREV_AT=0 ;;
esac
FILES_AGE_DAYS=$(( ( $(date +%s) - PREV_AT ) / 86400 ))

if [ "$FILES_FP" != "$PREV_FP" ] || [ "$FILES_AGE_DAYS" -ge "$FILES_REFRESH_DAYS" ]; then
    docker run --rm \
        -v "${UPLOADS_VOLUME}:/data:ro" \
        -v "${FILES_DIR}:/backup" \
        alpine tar czf "/backup/${DATE}.tar.gz.part" -C /data .

    # Reading the archive back is what separates "tar exited 0" from "the archive can be opened".
    if ! tar tzf "${FILES_BACKUP}.part" >/dev/null 2>&1; then
        log "FAILED: files archive will not read back — refusing to publish it"
        rm -f "${FILES_BACKUP}.part"
        ping_healthcheck "/fail"
        exit 1
    fi

    mv "${FILES_BACKUP}.part" "$FILES_BACKUP"
    # Written only once the archive is published: a run that dies leaves the old fingerprint, so the
    # next run makes the archive again.
    printf '%s %s\n' "$FILES_FP" "$(date +%s)" > "$FILES_STATE"
    log "Files OK: $(du -sh "$FILES_BACKUP" | cut -f1)"
else
    log "Files unchanged since $(date -d "@${PREV_AT}" +%F 2>/dev/null || echo "${FILES_AGE_DAYS} days ago") — newest archive still current, not making another"
fi

# --- off-site -------------------------------------------------------------------------------

# `copy`, never `sync`. See rule 1 at the top. The remote keeps its own, much longer history, and
# the local prune below cannot reach it.
log "Copy to Google Drive (encrypted remote)..."
rclone copy /backups "$REMOTE" --log-file="$LOG" --log-level NOTICE

# Pruned separately and slowly. --min-age is a filter, so nothing newer can be caught by it.
# --drive-use-trash=false deletes for good instead of moving to the Drive trash. The trash counts
# against the account's 15 GB and Google empties it only after 30 days, so every copy held space for
# 70 days, not 40 — 13.2 GB of 15 in the worst case across the three projects sharing the account
# (measured 2026-10-12). An expired backup has nothing worth undeleting.
log "Pruning off-site copies older than ${REMOTE_RETENTION_DAYS} days..."
rclone delete "$REMOTE" --min-age "${REMOTE_RETENTION_DAYS}d" --drive-use-trash=false --log-file="$LOG" --log-level NOTICE

# --- local prune --------------------------------------------------------------------------------

find "$DB_DIR" -name "*.sql.gz" -mtime "+${LOCAL_RETENTION_DAYS}" -delete
# The newest uploads archive always stays, even past 7 days: while nothing changes it IS the current
# copy, and a restore from this box alone (no Drive) must still have one. File names are dates, so
# sorting by name is sorting by age.
NEWEST_FILES=$(find "$FILES_DIR" -maxdepth 1 -name "*.tar.gz" | sort | tail -1)
find "$FILES_DIR" -name "*.tar.gz" -mtime "+${LOCAL_RETENTION_DAYS}" ! -path "${NEWEST_FILES:-/none}" -delete
# Leftovers from a run that died mid-dump. Never uploaded — they never got their real name — but
# they do take up disk until something clears them.
find "$DB_DIR" "$FILES_DIR" -name "*.part" -mtime +1 -delete

log "=== Backup done ==="
ping_healthcheck ""

# --- installing this on the server ----------------------------------------------------------------
#
#   sudo install -m 0755 kd-training-backup.sh /usr/local/bin/kd-training-backup.sh
#   sudo crontab -l | grep -q kd-training-backup || \
#       (sudo crontab -l; echo "0 3 * * * /usr/local/bin/kd-training-backup.sh") | sudo crontab -
#
# For the failure alarm, create an account at https://healthchecks.io (free), add a check that
# expects a daily ping, and put its URL on the server — root-only, never in this repo:
#
#   printf 'HEALTHCHECK_URL=https://hc-ping.com/YOUR-UUID\n' | sudo tee /etc/kd-training-backup.env
#   sudo chmod 600 /etc/kd-training-backup.env
#
# Restoring is documented in RESTORE.md, next to this file. Read it before you need it.
