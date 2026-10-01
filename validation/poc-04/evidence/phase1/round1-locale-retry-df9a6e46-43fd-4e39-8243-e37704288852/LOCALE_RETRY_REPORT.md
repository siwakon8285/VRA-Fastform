# POC-04 Phase-1 remediation Round1 — locale correction PASS; subsequent identity retry FAIL

PHASE 1 CLOSED: NO

READY FOR INDEPENDENT PHASE-1 RE-REVIEW: NO

Phase 2: NOT AUTHORIZED

POC-04 / S0-S15: NOT VERIFIED

This is new evidence. The earlier failed remediation report remains byte-identical at SHA-256 e7e372d24c567a41ea66c32d730f0713e70d060ac2f3c178b078d1e9a1b0b751. The earlier implementation report at82c87d88ff10985c5c18015768a7afc0442813608b7fbb616aa2ba405f249caf and all other historical evidence are preserved. No earlier FAIL was rewritten.

## Authorized source correction

Only the locale evidence block in backend/migration/src/test/java/dev/vra/migration/Poc04FoundationMigrationIntegrationTest.java changed. It queries pg_catalog.pg_database for the current database and records datlocprovider, datcollate, datctype, datlocale and pg_encoding_to_char(encoding). PostgreSQL JSON construction preserves SQL NULL. Provider interpretation records datcollate/datctype for libc and datlocale for ICU/builtin; no expected locale value is hard-coded. The [PostgreSQL17 catalog documentation](https://www.postgresql.org/docs/17/catalog-pg-database.html) supports this interpretation.

V4, its two reason constraints, all reason cases/helpers, grants, bootstrap, dependencies and the remaining harness source are unchanged. Static exact-byte review passed before execution. git diff --check passed before the focused retry. authority-and-source-equivalence.json compares all272 source paths: only the test changed; all other271 source/authority files are byte-identical to the preceding run. preflight.json records exact changed-path hashes, clean index and Git refs.

Test SHA-256: bb9557fdc435b63e36a158aba32034bd43fd02c2c37f40dabf8fd4197679a5c0.

V4 unchanged SHA-256: 7e2a1ea77ba92a451763c7e06ab7dc4cee6e06f761bbbb4e6cb1c786c8a21ffc.

## Focused reason behavior — EXECUTED PASS

Command, cwd backend:

    ./gradlew --no-daemon --init-script ../validation/poc-04/test-support/runner.init.gradle :migration:integrationTest --tests dev.vra.migration.Poc04FoundationMigrationIntegrationTest.reasonWhitespaceContractOnPostgres1711 --rerun-tasks

Exit0. JUnit1 test /0 skipped /0 failures /0 errors. PostgreSQL170011 /17.11 started; exact image and PGDATA ownership evidence passed; administrator bootstrap and fresh V1→V4 migration/validation executed before the cases.

Actual locale/provider metadata:

| Field | Actual value |
| --- | --- |
| datlocprovider | c (libc) |
| datcollate | en_US.utf8 |
| datctype | en_US.utf8 |
| datlocale | SQL NULL |
| encoding | UTF8 |
| effective_locale_fields | datcollate, datctype |

Both installed reason CHECK definitions use reason ~ '[^[:space:]]'. For each of security_role_proposal and protected_security_audit, empty string, ASCII spaces, tab, newline, carriage return, CRLF and mixed spaces/tabs/newlines failed the intended CHECK with SQLSTATE23514. Ordinary text and text surrounded by whitespace succeeded. Total14 denied and4 accepted cases. Each case rolled back; all27 authoritative-table snapshot hashes matched before/after. The behavior, rather than locale metadata alone, proves the required nonblank expression for this actual TEST database.

Raw command/environment/log and original XML: focused-reason-command.json, focused-reason.log, focused-reason-results/migration/integrationTest/TEST-dev.vra.migration.Poc04FoundationMigrationIntegrationTest.xml. Independently parsed18 case records, actual installed CHECKs, locale metadata and before/after snapshots: focused-reason-behavior.json. Exact cleanup/identity consistency: focused-reason-cleanup.json, exit0, three ledger resources absent.

## Next focused identity/restart proof — EXECUTED FAIL / STOP

After reason PASS, the same run-scoped cross-process allocator was retained for:

    ./gradlew --no-daemon --init-script ../validation/poc-04/test-support/runner.init.gradle :migration:integrationTest --tests dev.vra.migration.Poc04FoundationMigrationIntegrationTest.twoFreshInstancesHaveDistinctIdsAndRestartRetainsPhysicalIdentity --rerun-tasks

Exit1. JUnit1 test /0 skipped /1 failure /0 errors. Failure at test line162: JDBC connection to127.0.0.1:32853 was refused while reading the durable account after restart. PostgreSQL driver exception SQLSTATE was not serialized in XML and is NOT CAPTURED; no SQLSTATE is inferred.

Authoritative daemon observations for the same container show the published5432/tcp port changed from32853 before restart to32854 afterward. The test then attempted the old32853 endpoint. The volume, database_instance_id and physical system_identifier remained identical across the restart, as shown by repeated physical bindings and the Docker restart event. These partial facts do not turn the failed test into PASS. The durable-row/Flyway post-restart checks and the second fixture within this focused method were not reached.

Raw failure/log/XML: focused-identity-failure.json, focused-identity-command.json, focused-identity.log, focused-identity-results/migration/integrationTest/TEST-dev.vra.migration.Poc04FoundationMigrationIntegrationTest.xml. Exact port/state/identity observations: restart-port-and-identity-observations.json. No source correction was made after this failure; execution stopped as required.

## Run, source and physical identities

Run UUIDv4: df9a6e46-43fd-4e39-8243-e37704288852. Branch poc/04-security-auth; HEAD01256d96d85823740a8d6c3a3f99346afbdbef56; exact tree/source modes/hash inventory in source-binding.json. Canonical content SHA-2566cce541d4fc5aa99bc27607c3feb1c79136f74105b1ddf697b50856fd989d1ca,272 files.

Only unix:///Users/siwakornbundi/.colima/vra-poc04-test/docker.sock was used, with socket override/var/run/docker.sock and host override127.0.0.1. Dedicated daemon14c8ff26-86d1-4c0b-88a6-560be91a65ea, colima-vra-poc04-test, Docker29.5.2 linux/arm64. Existing10.241.0.0/16 /24 pool retained. Initial daemon had zero containers/volumes and only built-in networks. Ryuk remained enabled. No Docker Desktop, persistent vra-poc00, profile change, blanket prune or Git mutation.

Both fresh initializations used exact immutable PostgreSQL index sha256:d74eeac9a635390a49bc21bd49fccd973de707e2a53a76ac49b552b8712ec46f, linux/arm64/v8 manifest sha256:86fa57b44a1d38f09970f64ac71eb492f7a9cf94d08b85e1c8f07bb0fc0a1761 and linked config sha256:97432f980da100ebd3e419711efee84e1e97a966d62c035286a07f239ddb4d9c. Independent runtime was170011. Raw XML retains provenance markers; daemon-inspect retains exact typed/daemon PGDATA context.

| Logical name | UUIDv5 | Physical system_identifier | Context |
| --- | --- | --- | --- |
| postgres-authority/0 | a1cd0d71-141f-55e2-ba68-da742268659f | 7691696043923185702 | Successful reason test, first Gradle execution |
| postgres-authority/1 | ec63ab7c-9647-5d1f-a4da-f4a9852f5e15 | 7691696467869839398 | Identity test, separate Gradle execution; stable through restart |

These recorded instances have different ordinals/UUIDv5 values/system identifiers and one shared namespace. The allocator advanced across Gradle processes. The entire requested focused identity/restart proof remains FAIL because its post-restart JDBC read did not execute successfully. All container/volume/network IDs, labels and creation metadata are in final-run-ledger.json.

## Four review blockers and remaining work

| Original blocker | Current status |
| --- | --- |
| Current-run-only teardown authorization | Previous narrow ledger/support correction unchanged; actual cleanup of six exact registered resources passed. Full negative proof still pending. |
| Missing teardown negatives | Other-run sentinel, wrong-ID and wrong-label executions NOT EXECUTED after identity failure. |
| Fresh instance identity reuse | Allocator advanced across two executions with distinct recorded physical identities; focused full restart proof FAIL at stale mapped endpoint. Full-run identity consistency remains pending. |
| Whitespace-only reasons | Focused PostgreSQL17.11 behavioral proof PASS:14 denials,4 successes, intended CHECKs and unchanged state. |

NOT EXECUTED on the corrected test bytes: teardown negatives; full Phase1 fresh/populated/history/rerun/catalog/roles/ACL/SET/SQL proof; collector TEST OS/mount/SQL proof; full check build --rerun-tasks and complete migration/runtime suites. Earlier green results are not substitutes.

Current retry totals:2 JUnit test executions /0 skipped /1 failure /0 errors. One successful focused test is distinct from18 SQL behavior cases. No full-suite count is claimed.

## Cleanup, integrity and final binding

cleanup-consistency.json records finish exit0: six exact ledger-owned resources absent and physical identity map consistent. All four observed container IDs were independently inspected absent (all-observed-container-absence.json); observation never conferred deletion authority. final-daemon-state.json proves zero containers, zero volumes and only the original bridge/host/none network IDs. No sentinel was created because its stage was not reached. No shared/persistent resource was deleted.

The final immutable history comparison, unchanged V1–V3/authorities/bootstrap/profile/harness, text/JSON parse checks, secret scan, tracked/untracked/staged inventory, and individual path SHA-256 values are in FINAL_BINDING.json and secret-scan.json. The binding excludes itself from recursive self-hashing. Nothing staged. V5:NO. V6:NO. Phase2 started:NO. Baseline JdbcReservationIdempotencyRepository unchanged.

Open blocker: restart changed the published port, but the next JDBC read used the pre-restart endpoint. This is preserved as FAIL; no restart-harness correction was made. Phase1 remains open and is not ready for independent re-review.
