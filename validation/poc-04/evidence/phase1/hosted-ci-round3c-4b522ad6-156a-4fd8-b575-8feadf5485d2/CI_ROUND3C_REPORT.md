# POC-04 Phase-1 — hosted CI Round 3C controlled recurrence

HOSTED CI BOOTSTRAP REPRODUCIBILITY: PASS
HISTORICAL 08P01: NON-REPRODUCIBLE UNDER CONTROLLED RECURRENCE
08P01 ROOT CAUSE: NOT ESTABLISHED — class G
HISTORICAL UNEXPLAINED ANOMALY: OPEN RISK / REVIEW REQUIRED

READY FOR WORKFLOW-INTEGRATION STEP: NO — historical anomaly requires explicit narrow review/risk disposition first
READY FOR NARROW INDEPENDENT CI-DELTA REVIEW: NO
PHASE 1 CLOSED: NO
READY TO MERGE PR #3: NO
Phase 2: NOT AUTHORIZED / NOT STARTED
POC-04 / S0-S15: NOT VERIFIED

This round performed execution and evidence collection only. It applied no connection/SSL fix, test retry, timeout change, source change, workflow change, Docker configuration change, or Git mutation. Each authorized run executed once. No failed gate was rerun or replaced.

Candidate binding:

- Branch: `poc/04-security-auth`
- HEAD: `ae9bd1f53a7e3e8612f2f4cd9f778e27d7cdb0db`
- Tree: `988058edd53504936345bc51ebdeee2d7d55b0c7`
- Canonical candidate source-content SHA-256: `9157dc5aa931045e2f0482c2c2d4f51584510a8fb208194ad7f86143f6fa12e2`; 275 executable/configuration/authority source files. Historical/generated evidence is separately inventoried; it is not treated as executable source.
- runner.init.gradle SHA-256: `5b438ce0e7bec8b2dbbc321df680c6f57359c019d9eb241f65029efea0a3d5d8`
- phase1-ci.py SHA-256: `fa8e51b0e30e00406d801c5e939e59654ed5852c8f4b88009d73caa0705e1151`
- Support JAR SHA-256 on every run before tests: `852e65b312d71764fdb4d1047537611056ed2ab10211eb538d99142f16e4b9af`

Existing candidate source changes from earlier authorized rounds remain visible: tracked runner.init.gradle and RunOwnedPostgreSQLContainer.java; untracked phase1-ci.py, TransportDiagnosticDriver.java, and test_public_verification_classifier.py. Their bytes did not change in Round 3C. Nothing is staged. Source/SSL/transport diagnostic behavior remained the existing normal path; VRA_POC04_TRANSPORT_DIAGNOSTIC was not enabled.

The pre-run binding for each sample independently records branch/HEAD/tree, exact source file hashes, staged/unstaged/untracked inventories, Docker endpoint/physical daemon ID, and initial container/volume/user-network inventories. Recompiled support content is bound before tests. Every run started with zero containers, zero volumes, and no user-defined networks.

Disposable TEST endpoint: `unix:///Users/siwakornbundi/.colima/vra-poc04-test/docker.sock`
Daemon ID: `14c8ff26-86d1-4c0b-88a6-560be91a65ea`; name `colima-vra-poc04-test`; Linux/arm64 Docker 29.5.2. Testcontainers host override remained `127.0.0.1` and socket override `/var/run/docker.sock`. Effective pool remained `10.241.0.0/16`, child `/24`. No Docker Desktop or persistent vra-poc00 resource was accessed.

Executed samples (tests / skipped / failures / errors):

| Run | Execution | Raw XML result | Fresh UUIDv4 run_id |
| --- | --- | --- | --- |
| A | whole OutboxMigrationSecurityIntegrationTest | 13 / 0 / 0 / 0 | `3632644b-3d5c-44a5-85e9-d18a421a47b0` |
| B | complete migration context | 24 / 0 / 0 / 0 | `1da09778-b8a1-410c-9ac8-4543ececb9c7` |
| C | independent complete migration context | 24 / 0 / 0 / 0 | `b56713e2-0a8f-41c8-bea2-ad3c8efc593d` |
| D | full check/build | 193 / 0 / 0 / 0 | `ac137959-bdc4-4521-bf8b-f422c11a59d8` |
| E | independent full check/build | 193 / 0 / 0 / 0 | `5b069a48-de57-4d53-a462-4a24c3ae473a` |

A used the whole class, without manual method selection/reordering. B and C each ran migration:test (4) and migration:integrationTest (20). Migration-context independent executions: 2 PASS, 48 tests total, zero skipped/failures/errors. D and E each ran migration:test 4, migration:integrationTest 20, runtime:test 56, runtime:integrationTest 113: exactly 193 each. All five samples total 447 executions; this is not the count of a single full suite.

Raw commands and exit statuses are in EXACT_EXECUTION_COMMANDS.json and RUN_*/bootstrap/commands.json. A/B/C reuse the unchanged bootstrap Run methods because the existing CLI has no whole-class/migration-only selector. D/E invoke the unchanged phase1-ci.py CLI --disposable-test --gate full; their identical full Gradle command and separate bootstrap command records are preserved. Raw Gradle logs, raw JUnit XML, testcase inventories and observable console completion order are preserved per run. Console completion order is not asserted to be test-start order.

Capture and identity:

- Each fixture is registered by the existing creator-ledger path. The read-only capture recipe only inspects exact authorized ledger container IDs after validating creation identity and run/source labels. It does not confer deletion authority on discovered resources.
- All 137 initialized PostgreSQL physical databases have UUIDv5(run_id, logical_name) identities and distinct physical system_identifiers. Resource IDs, DB UUIDs and physical identifiers are disjoint across runs. Repeated observations/restarts retain one physical identifier and one durable volume per database_instance_id. Detailed maps, exact volume/network/container IDs, current sampled mapped ports and PostgreSQL 170011 identity observations are retained in the ledgers and INDEPENDENT_RAW_EVIDENCE_RECONCILIATION.json.
- PostgreSQL image index, platform manifest, config linkage, runtime version, exact PGDATA volume and source/run label checks remained the existing reviewed semantics. No image pin or mount/grant assertion was changed.
- The complete class and both migration contexts did not reproduce 08P01. Both full contexts also did not reproduce it. There was no new failure triggering independent exception/root-cause capture; no new cause is inferred from successful connections.
- Live endpoint evidence is sampled daemon state, not a hook around every JDBC connection. There was no unintentional restart established. The foundation identity test deliberately stops/starts the same durable database and permits a new mapped host port.
- B has a preserved log-capture limitation: its initial follower ended at the deliberate durable stop/start; resumed-incarnation logs were not followed. No new failure occurred in B. The evidence-only temporary capture recipe was adjusted after B to attach by exact container ID + StartedAt for C/D/E. CAPTURE_RECIPE_INCARNATION_NOTE.json and archived per-run recipes bind the adjustment. Repository source and test semantics did not change; A/B results were not edited.
- C/D/E captured resumed-incarnation startup/readiness logs through separate StartedAt-bound followers. All follower processes exited successfully. Sanitized logs contain no matched protocol/SSL/reset/crash markers under the documented pattern; absence of those markers does not establish the origin of the historical failed packet.

Full-run scanners and deployable artifacts:

| Run | Scanner executed | Matches | Public verification IDs | Digest metadata | Unresolved |
| --- | --- | --- | --- | --- | --- |
| D | YES — Gitleaks 8.30.1 | 28 | 2 | 26 | 0 |
| E | YES — Gitleaks 8.30.1 | 28 | 2 | 26 | 0 |

The pinned archive SHA-256 is `b40ab0ae55c505963e365f271a8d3846efbc170aa17f2607f13df610a9aeb6a5`. Acquisition attempt records, archive validation, executable SHA/version, immutable public-key provenance and every classified observation remain in each full bootstrap evidence directory. Gitleaks exit 1 reflects retained classified matches; it is not represented as zero matches. A/B/C scanning was NOT EXECUTED because those were class/migration samples. D/E scanner scope includes source, evidence present at scan time, and deployable application entries; subsequently generated reconciliation/report/binding metadata is outside those earlier scan timestamps.

Both D/E bootJar proofs show no POC04 TEST support classes in application entries or nested libraries. Artifacts exist and have identical hashes in both builds:

- runtime-0.1.0-SNAPSHOT.jar: `35174e46636270ff31f7a92a700a230dfe52876cda2e92e7ae716111f08d13ec`
- migration-0.1.0-SNAPSHOT.jar: `bf49306fd51e4cd8cb6b1f8e1de74cbe36f299584ac017f2e4b65849e982d84e`

Cleanup and final integrity:

| Run | Containers | Networks | Volumes | Exact absences | Physical PostgreSQL instances |
| --- | --- | --- | --- | --- | --- |
| A | 13 | 13 | 13 | 39 | 13 |
| B | 21 | 21 | 21 | 63 | 21 |
| C | 21 | 21 | 21 | 63 | 21 |
| D | 41 | 42 | 43 | 126 | 41 |
| E | 41 | 42 | 43 | 126 | 41 |

All 417 registered resources have matching current-run exact-ID absence proof. Native Ryuk cleanup completed. D/E also execute the existing teardown-negative tests, preserve another-run sentinel during current-run cleanup, reject wrong-ID/wrong-label candidates, and perform explicit exact-ID admin cleanup of negative fixtures. The extra non-PostgreSQL negative-test resources are included in the full ledgers. No broad prune or discovery-derived deletion was used.

Every final run inventory has zero containers, zero volumes, and only the same bridge/host/none built-in networks. An independent final read-only Docker query confirmed that state and the expected daemon ID. All required catalog/constraint/ACL/SET-graph/migration proofs ran again in D/E through the unchanged foundation tests; no earlier green result substituted for those executions.

V1/V2/V3 remain byte-identical to the accepted Phase-0 source manifest:

- V1__inventory_reservation_foundation.sql: `d804cbe5153b9a904ae0747af55c8295079d4a0c3edb4a38145ab5199b5b59c4`
- V2__inventory_reservation_idempotency.sql: `7bef750b59b7b77e8402f4f42c13c21c4e072092504b9f268bace9b72de8d2bb`
- V3__outbox_recovery.sql: `9edbd15ffe08ee4edafa3f549856ee38a782485c54db9c73e69d4263e5ca26b4`
- V4 candidate SHA-256: `7e2a1ea77ba92a451763c7e06ab7dc4cee6e06f761bbbb4e6cb1c786c8a21ffc`

V5/V6 absent. git diff --check PASS. Nothing staged. Workflow remains frozen. Candidate source files are fully hashed in FINAL_SOURCE_BINDING.json; the final package binding separately hashes every new evidence file. Existing baseline JdbcReservationIdempotencyRepository remains unchanged; no Phase-2 implementation was added.

Historical failures remain visible as failures, including the original full-build 08P01 and the Round-3B root-cause G outcome. HISTORICAL_ARTIFACT_RECONCILIATION.json independently verifies 390 bound historical evidence files across the five earlier hosted-remediation packages. Round-3B report SHA-256 remains `9003e79d5bd5bb562731b1ed16642a754e62b66af9f11bb5b6e746d30bcedf72`.

Technical reproducibility: PASS under these controlled recurrence samples. Historical unexplained anomaly: OPEN RISK / REVIEW REQUIRED. The evidence does not establish a fix, root cause, or permission to treat the historical anomaly as non-blocking. Stop here for explicit narrow review/risk disposition; workflow integration and Git closure remain unauthorized in this round.
