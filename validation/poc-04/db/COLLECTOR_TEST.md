# Phase-1 TEST collector preparation

This profile prepares the approved PostgreSQL source boundary. It implements no
observer, SQL parser, telemetry queue, or database append capability. POC-04 and
S0–S15 remain NOT VERIFIED.

Use only the dedicated disposable TEST daemon and the physical-instance/source
attestation contract in `../PHASE0_BASELINE.md` §7. The Compose file supplies
run-bound names and labels; these declarations alone are not execution proof.
The administrator supplies run/instance/source metadata and an ephemeral password
file outside the repository. No port is published and no root Compose data volume
is reused. Do not start this profile against an unattested/shared daemon.

The server mounts a fresh run-owned collector source volume read/write, outside
PGDATA. The entrypoint sets its directory to server ownership, group 9404 and mode
2750. PostgreSQL creates source files with mode 0640; the setgid directory keeps
the reserved observer-read group. Only the server owner can write. The
`x-poc04-future-observer` extension reserves UID/GID 9404 and the same source as a
read-only, `nocopy` mount. It creates no process. Runtime and worker processes
receive no source mount. The future observer receives neither PGDATA nor the
administrator secret, Docker socket, server-file SQL privilege, `pg_monitor`,
logging-parameter SET privilege, nor business/audit table privileges.

The collector profile disables parameter capture and statement/duration logging
and preserves error-source segments. Each server start adds a fresh kernel UUID
to timestamped names; daily age rotation avoids size-driven reuse. A future admin
rotation harness must ensure forced
rotations select a distinct timestamped name. The future admin retention/metadata
harness must preserve every segment until verified acknowledgment and expose
trusted segment metadata without exposing PGDATA. Those behaviors belong to
Phase 2 and are not implemented by this preparation.

Before claiming this boundary, inspect actual container/volume/network IDs and
labels, resolved image/platform, PostgreSQL system identifier, mounts, directory
and file ownership/modes, and effective logging settings. Verify ordinary DB
identities cannot use server-file/monitor/log-parameter privileges. Keep raw
collector files restricted outside published evidence; inspect only safe metadata.
Remove only the recorded run-owned resource IDs after ownership reinspection.
