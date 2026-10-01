# POC-04 Phase-1 remediation Round 1 — STOP / FAIL

PHASE 1 CLOSED: NO

READY FOR INDEPENDENT PHASE-1 RE-REVIEW: NO

Phase 2: NOT AUTHORIZED

POC-04 / S0-S15: NOT VERIFIED

This new report preserves the independent-review FAIL and all earlier execution results. The previous IMPLEMENTATION_REPORT.md remains byte-identical at SHA-256 82c87d88ff10985c5c18015768a7afc0442813608b7fbb616aa2ba405f249caf. Historical successful results cannot substitute for execution against these corrected candidate bytes. The permanent finding record is historical-review-FAIL.md.

## First executed failure and mandatory stop

The focused reason test failed at backend/migration/src/test/java/dev/vra/migration/Poc04FoundationMigrationIntegrationTest.java:259. SQL SHOW lc_ctype returned ERROR: unrecognized configuration parameter "lc_ctype". This locale-evidence query ran before any negative or positive reason case. No SQLSTATE was serialized by JUnit XML; actual SQLSTATE is NOT CAPTURED and is not inferred.

Command, cwd backend:

    ./gradlew --no-daemon --init-script ../validation/poc-04/test-support/runner.init.gradle :migration:integrationTest --tests dev.vra.migration.Poc04FoundationMigrationIntegrationTest.reasonWhitespaceContractOnPostgres1711 --rerun-tasks

Exit status: 1. JUnit: **1 test / 0 skipped / 1 failure / 0 errors**. Raw command/environment/counts: focused-reason-command.json. Raw log: focused-reason.log. Original JUnit XML: focused-reason-results/migration/integrationTest/TEST-dev.vra.migration.Poc04FoundationMigrationIntegrationTest.xml. Extracted safe failure/provenance: focused-failure-evidence.json.

The user-mandated STOP was honored. No source correction or later proof execution followed this failure. The error is in the new locale-evidence query; the V4 reason cases remain unverified.

## Four independent-review blockers

| Blocker | Narrow correction prepared | Execution state |
| --- | --- | --- |
| Daemon observation admitted other-run resources to teardown | Shared restricted, file-locked run state. Exact creator-returned ID, kind, current run, allocated instance, source/tool/environment labels, creation metadata and volume mountpoint gate deletion. Observation never registers authority. | Static review and compilation passed. Three registered resources removed through the ledger and exact absence verified. Other-run preservation not executed. |
| Teardown negatives missing | New TEST-only proof creates an other-run sentinel and current-run volume/network; wrong-ID and controlled wrong/missing-label substitution must deny before deletion. Negative fixtures use separately authorized exact-ID admin cleanup with independent ownership checks. | NOT EXECUTED after the focused failure; blocker remains open. |
| Fresh physical databases reused instance UUIDs | Persistent atomic allocator advances role/ordinal across all processes. UUIDv5 namespace is the current UUIDv4 run. Physical binding rejects reused UUIDs or multiple UUIDs per cluster. Added two-fresh and same-durable-volume restart test. | One physical cluster captured consistently. Two-fresh, restart and independent-process concurrency proofs NOT EXECUTED. |
| Whitespace-only reasons accepted | Both V4 reason CHECKs consistently use reason ~ '[^[:space:]]'. These are all two required V4 reason fields. Added seven required negatives and two text positives for each table with prepared values, SQLSTATE23514, rollback and complete authoritative-state comparison. | Candidate migration and validation succeeded. Locale query failed before all 14 negatives and 4 positives; reason semantics remain NOT VERIFIED. |

Static review also found that Testcontainers2.0.5 discards the command wrapper returned by its modifier. Before database execution, this harness path was corrected using supported containerIsCreated and containerIsStarted hooks in a TEST-only PostgreSQLContainer subclass. Only the creation callback registers its returned container ID; observed volume/network IDs must already belong to the matching creator ledger. Stop checks live ledger authorization before native lifecycle, then verifies/removes exact owned resources. Nineteen existing tests received constructor-only substitutions; including the foundation fixture, 24 constructions changed across 20 test files. No production code or dependency changed. Initial and final compile evidence are preserved separately.

## Environment, source and reached milestones

Run UUIDv4: 2d744f02-8f9b-43a4-8ab7-723867aec905. Branch: poc/04-security-auth. HEAD: 01256d96d85823740a8d6c3a3f99346afbdbef56. Exact tree, sorted file hashes/modes and worktree state: source-binding.json. Source content SHA-256: bdd3cfecbe03250bba398af7fbc3e7b5bd5c4bcd5fdfb82eaeae6906937a2210, across 272 files.

Only the dedicated endpoint unix:///Users/siwakornbundi/.colima/vra-poc04-test/docker.sock was used. Socket override: /var/run/docker.sock. Host override: 127.0.0.1. Daemon ID: 14c8ff26-86d1-4c0b-88a6-560be91a65ea; colima-vra-poc04-test; Docker29.5.2 linux/arm64. Existing IPAM10.241.0.0/16 with /24 children was retained. No Docker Desktop or persistent vra-poc00 access, profile change/restart, blanket prune or Git mutation.

The real fixture reached exact index/platform/manifest/config linkage, PostgreSQL startup, typed PGDATA and authoritative daemon Type=volume checks, runtime170011, administrator role bootstrap, fresh V1→V4 applying four migrations, and Flyway validation of four migrations. Safe raw markers and resource identities are preserved in focused-failure-evidence.json and daemon-inspect. Ryuk was enabled; its running pinned helper, mapped port and isolated socket mount were recorded. Helper observation is not administrative deletion authority. No separate connectivity probe was executed after failure.

| PostgreSQL identity | Exact value |
| --- | --- |
| Immutable execution/index | sha256:d74eeac9a635390a49bc21bd49fccd973de707e2a53a76ac49b552b8712ec46f |
| Selected execution platform | linux/arm64/v8 |
| Selected manifest | sha256:86fa57b44a1d38f09970f64ac71eb492f7a9cf94d08b85e1c8f07bb0fc0a1761 |
| Linked config | sha256:97432f980da100ebd3e419711efee84e1e97a966d62c035286a07f239ddb4d9c |
| Independent runtime | PostgreSQL17.11 / server_version_num170011 |

## Database identity map and cleanup

| Logical name | UUIDv5 database_instance_id | Physical system_identifier |
| --- | --- | --- |
| postgres-authority/0 | 17e2dca0-882d-5be7-bea1-af803e6c032a | 7691690167227441190 |

Exact PostgreSQL container: 9dfb98a45c97928fd2b1174966ab0d0e47ba19e9178ce910fa9e89446a5b7266. PGDATA volume: vra-poc04-2d744f02-8f9b-43a4-8ab7-723867aec905-postgres-authority-0-pgdata. Every container/volume/network ID, label, creation identity and physical binding is in final-run-ledger.json.

All three ledger-owned resources were removed through authorized exact-ID teardown. Finish exit0 verifies their absence and one consistent physical map: focused-failure-cleanup-consistency.json. The observed Ryuk helper was removed by native lifecycle; it was never admitted to administrative deletion authority by discovery. Both observed container IDs were independently inspected as absent: all-observed-container-absence.json. Final isolated daemon: zero containers, zero volumes, original bridge/host/none exact network IDs only: daemon-after-focused-failure.json. No negative sentinel was created because that stage was not reached.

## Still required — NOT EXECUTED

No earlier green result fills these gaps on the corrected bytes:

- All reason negatives/positives and PostgreSQL locale semantics.
- Two fresh physical clusters, cross-process allocation proof and same-volume restart.
- Other-run preservation, wrong-ID and wrong-label teardown negatives.
- Populated V3→V4 retention/history and same-DB rerun.
- Complete V4 catalog/constraint/ownership/grant proof, role attributes/membership/SET graph and actual SQL denials.
- Collector TEST OS/mount/SQL boundary.
- Full check build --rerun-tasks, migration unit/full integration and runtime unit/integration suites.

Only one test case executed in this round: 1/0 skipped/1 failure/0 errors. Required final full-suite counts cannot be claimed. No sleep-based correctness test was introduced; readiness depends on observed PostgreSQL/Docker state.

## Immutable files and candidate hashes

V1: d804cbe5153b9a904ae0747af55c8295079d4a0c3edb4a38145ab5199b5b59c4

V2: 7bef750b59b7b77e8402f4f42c13c21c4e072092504b9f268bace9b72de8d2bb

V3: 9edbd15ffe08ee4edafa3f549856ee38a782485c54db9c73e69d4263e5ca26b4

All equal the recorded Phase-0 hashes. Corrected V4: 7e2a1ea77ba92a451763c7e06ab7dc4cee6e06f761bbbb4e6cb1c786c8a21ffc. Bootstrap unchanged: 976cf704687e5bf005de8943c706fdd996d801172c112d640c993e0b193407b5.

Controlling authorities and the prior report are unchanged. Exactly21 preexisting source files changed:19 constructor-only test files plus the foundation test and V4. The other319 preexisting changed/source/evidence paths remain byte-identical to their before-round hashes. See final-integrity.json for exact comparisons. All current changed/source/evidence path hashes, tracked/untracked/staged inventory and final report hash are recorded in FINAL_BINDING.json, which excludes itself from recursive self-hashing.

## Final checks and open blocker

git diff --check: exit0/PASS. New/changed text trailing-whitespace/final-newline inspection:PASS. JSON/JSONL and Python parse:PASS. Gradle init and TEST Java compilation executed successfully before the failed focused test. Final external support javac and JAR creation:exit0. Gitleaks8.30.1 scanned375 changed paths: exactly3 known exact Git HEAD SHA false positives; zero actual/unresolved secret findings. Raw candidate values were not published; sanitized evidence: secret-scan.json.

Nothing staged. V5:NO. V6:NO. Phase2 started:NO. The baseline JdbcReservationIdempotencyRepository remains unchanged. No production, schema-grant, collector-profile or Phase-0 pin change was made.

Open blocker: the new locale evidence query fails on the actual pinned server before the reason cases. No correction was made after the focused failure. All later mandatory evidence remains unexecuted. Wait for user direction; Phase1 is not closed and is not ready for independent re-review.
