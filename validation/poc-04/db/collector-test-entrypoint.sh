#!/usr/bin/env bash
set -euo pipefail

# Runs only in the admin-provisioned disposable TEST PostgreSQL container.
# Numeric group 9404 is reserved for the future poc04_pg_telemetry identity.
collector_dir=/var/log/vra-poc04-collector
if [[ -L "$collector_dir" ]]; then
    printf '%s\n' 'collector directory must not be a symbolic link' >&2
    exit 1
fi
install -d -o postgres -g 9404 -m 2750 "$collector_dir"
IFS= read -r collector_boot_id < /proc/sys/kernel/random/uuid
exec /usr/local/bin/docker-entrypoint.sh "$@" \
    -c "log_filename=postgresql-%Y-%m-%d_%H%M%S-${collector_boot_id}.log"
