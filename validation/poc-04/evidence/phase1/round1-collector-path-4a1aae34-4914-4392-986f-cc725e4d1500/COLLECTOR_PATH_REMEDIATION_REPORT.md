# POC-04 Phase-1 collector path remediation — focused failure preserved

PHASE 1 CLOSED: NO

READY FOR INDEPENDENT PHASE-1 RE-REVIEW: NO

Phase 2: NOT AUTHORIZED / NOT STARTED

POC-04 / S0-S15: NOT VERIFIED

Run ID: `4a1aae34-4914-4392-986f-cc725e4d1500`. Branch `poc/04-security-auth`, HEAD `01256d96d85823740a8d6c3a3f99346afbdbef56`. This new report preserves the previous collector failure and does not upgrade historical or current results to PASS.

## Authorized correction

Only `validation/poc-04/scripts/collector-boundary-proof.py:279` changed. Parentheses now make the complete basename `collector-local-platform-<ordinal>.override.json` the right operand of pathlib division. It is one child in the private run-state parent directory. Path writing, Compose input and final unlink refer to the same Path object. No wholesale path string cast, extension replacement, permission/mount change, schema/role alteration, assertion weakening or unrelated refactoring was made.

Previous script SHA-256: `8e618e48b4d9d27fa05805a3e276c06cc8f7e99e4521e31f036343ced255b2b5`.

Corrected script SHA-256: `ca0139d63b19cc29a2392aeb99e6ca4c9e9998fb486f63c7f58d1f2a5b59f288`.

Exact patch: `collector-path-correction.patch`. Syntax via Python AST: PASS. Git diff --check: exit 0. The corrected path was actually written, and the Compose configuration command completed exit 0, before the later failure.

## Focused retry: FAIL / stopped

Command: `python3 -B /Users/siwakornbundi/Project/VRA-Fastform/validation/poc-04/scripts/collector-boundary-proof.py`; backend working directory; exact environment, timestamps and current source/JAR binding in `focused-collector-command.json`. Exit status: 1. This is a procedural proof, not a JUnit suite.

Failure at `validation/poc-04/scripts/collector-boundary-proof.py:336`:

```python
service['secrets']==[{'source':'postgres_admin_password','target':'postgres_admin_password'}]
```

Preserved exception: AssertionError, safe message `profile server credential target differs`. Both before-cleanup and final FAIL summaries remain unchanged. The proof was not retried and this assertion was not modified.

The actual normalized service.secrets value is NOT CAPTURED in these failure artifacts. Command records retain the command/exit status rather than stdout, and the safe profile projection omits secrets. Consequently the precise key/value difference is not established; this report does not infer an unsafe credential target. See `focused-failure-diagnosis.json` for the limitation.

Before the failure, the current source/config hashes, isolated daemon, immutable PostgreSQL/Alpine index/platform/manifest/config linkage, corrected override, resolved service/future-read-only-mount shape, absence of published ports, fresh resource declarations, server mount declarations, entrypoint, environment and external credential file binding checks passed. These are partial stages and do not constitute a collector boundary PASS.

A PostgreSQL container was not created or started. Runtime PostgreSQL version, bootstrap, SQL credential/privilege denials and OS/mount behavior checks were NOT EXECUTED. No physical PostgreSQL initialization occurred. Allocation `postgres-authority/0` / `e6d0c621-8e75-5808-9da6-abf15d3361cd` is an allocated UUIDv5 without a system_identifier; it is not reported as an initialized database.

The template field business_audit_grant_proof in the collector summary refers to this run's catalogs, but no current-run fresh/populated catalog execution occurred. That template text is not PASS evidence.

## Focused prerequisite and blocked final sequence

The current user required collector-only focus before a new full build. Before focus, the prior raw runtime XML was independently reconciled to 113 tests / 0 skipped / 0 failures / 0 errors, and every backend source byte was compared to that prior passing source manifest. Only the collector path expression differed. The unchanged script execution guard was supplied from this verified prerequisite and explicit focused ordering; the receipt `focused-prerequisite.json` states that the current run had NOT executed a runtime suite before focus. This is not a same-run full-regression claim or a final substitute.

The prior health-remediation full build remains historical PASS: 193 / 0 / 0 / 0. It was not reused as a final corrected-tree build. In accordance with the mandatory STOP rule, the new `check build --rerun-tasks` and final collector proof were NOT EXECUTED after focused failure. There are no current-run JUnit test executions or hidden skip claims. Phase-1 re-review readiness conditions remain unmet.

## Exact cleanup and isolated environment

Only endpoint `unix:///Users/siwakornbundi/.colima/vra-poc04-test/docker.sock` was used, with socket override `/var/run/docker.sock` and host override `127.0.0.1`. Daemon ID `14c8ff26-86d1-4c0b-88a6-560be91a65ea`, name `colima-vra-poc04-test`, server 29.5.2 linux/aarch64, pool 10.241.0.0/16 with /24 children. Recorded context is default; explicit DOCKER_HOST controls the actual endpoint. No Docker Desktop, persistent vra-poc00, blanket prune or profile/global Docker modification was performed.

Three successful creator receipts were registered into the current-run ledger with exact kind/identity, UUIDv5, run/source/tool labels and live ownership checks:

| Kind | Exact identity |
|---|---|
|network|`0211aa42436a42eb41a244df10f3b7712eb28badc34bc8eef54cd6cf55cf39a3`|
|volume|`vra-poc04-4a1aae34-4914-4392-986f-cc725e4d1500-collector-0_postgres_data`|
|volume|`vra-poc04-4a1aae34-4914-4392-986f-cc725e4d1500-collector-0_collector_source`|

All three exact resources were removed through the shared authorized-current-run ledger (each teardown exit 0, absent true, previously present). Independent `run-harness.py finish` exited 0 and confirms three absent resources with zero physical database mappings. The ephemeral admin credential file and exact override are removed; no credentials or raw collector content were published. The final daemon has zero containers, zero volumes, and the same three original bridge/host/none IDs. See creation-receipts.json, resources.json, summary cleanup, cleanup-consistency.json, final-run-ledger.json and final-daemon-state.json. The recorder stopped normally after evidence preservation.

## Immutability, source and artifact binding

Previous HEALTH_REMEDIATION_REPORT.md remains SHA-256 `f307560dcc352c89d8f5a436ad7b6872e0c4eb4883bf3d14bb3f66fbe0bcb2cc`.

Previous FINAL_BINDING.json remains SHA-256 `f966107b0e79f832954ea3933990a71ad686aacc6dd77df7926c1c3e7d7e5455`.

All 684 prior changed/source/evidence paths other than the authorized collector script match their previous hashes. Prior independent review FAIL and all original image, PGDATA, IPAM, locale, restart, health and collector failure evidence are preserved. The current source manifest has 272 files and canonical content SHA-256 `35f01843bc1a01d9266c8d40c4f43cb916e61ffad5adb70ca1eedbc7632b43c5`; only the collector script differs from the previous manifest. This is new current-tree failure evidence, not reuse of the old final binding as final corrected-tree proof.

| Migration/support | Current SHA-256 | Result |
|---|---|---|
| V1 | `d804cbe5153b9a904ae0747af55c8295079d4a0c3edb4a38145ab5199b5b59c4` | Exact accepted Phase-0 bytes |
| V2 | `7bef750b59b7b77e8402f4f42c13c21c4e072092504b9f268bace9b72de8d2bb` | Exact accepted Phase-0 bytes |
| V3 | `9edbd15ffe08ee4edafa3f549856ee38a782485c54db9c73e69d4263e5ca26b4` | Exact accepted Phase-0 bytes |
| V4 candidate | `7e2a1ea77ba92a451763c7e06ab7dc4cee6e06f761bbbb4e6cb1c786c8a21ffc` | Unchanged by this remediation |
| POC04 role bootstrap | `976cf704687e5bf005de8943c706fdd996d801172c112d640c993e0b193407b5` | Unchanged |
| Health integration test | `3c9cdc89a6f297c6cdd655d94be2b3c10aca802a9570e1d04ab18319acdf9719` | Unchanged from passing health remediation |

Authorities, pins, collector YAML/config/entrypoint, V1–V4, role grants, production datasource and baseline JdbcReservationIdempotencyRepository remain unchanged. V5/V6/PostgresAuditTamperObserver/observer.py/new Phase-2 API-worker behavior: absent. Phase 2 did not start.

Git diff --check passes. Separate authored-source/support trailing-whitespace/final-newline findings: zero. The existing 22 raw evidence whitespace findings are preserved without normalization. JSON/JSONL/YAML/Python parse checks pass. Complete modified/untracked text scan with Gitleaks 8.30.1: 16 independently verified Git/file/source-SHA metadata false positives, zero unresolved candidates. Raw candidate values are not stored in reports. The final delivery scan includes this report and is recorded separately.

Twenty tracked modified test paths are the existing Phase-1 work; nothing staged. The complete current tracked/untracked inventory and SHA-256 for every changed source/evidence path are in the new FINAL_BINDING.json, with its self-hash reported separately. The new binding records focused FAIL and blocked final executions; it is not evidence that final Phase-1 tests passed on these corrected bytes.

The unresolved blocker is the failed exact service.secrets assertion and missing normalized value needed to diagnose it. No further correction or proof execution was performed. Phase 1 remains open and not ready for independent re-review.
