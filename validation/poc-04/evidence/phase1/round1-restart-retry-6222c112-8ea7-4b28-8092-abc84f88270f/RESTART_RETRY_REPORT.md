# POC-04 Phase-1 review remediation Round 1 — restart correction PASS; full regression FAIL

PHASE 1 CLOSED: NO

READY FOR INDEPENDENT PHASE-1 RE-REVIEW: NO

Phase 2: NOT AUTHORIZED

POC-04 / S0-S15: NOT VERIFIED

This is new implementation-side evidence, not an independent-review acceptance. Execution stopped after the first failed full-regression expectation. No source change or test retry followed that failure. Collector boundary execution for these corrected bytes was not started.

## Historical FAIL preservation

The original independent Phase-1 review FAIL remains permanently recorded in review-remediation-round1-2d744f02-8f9b-43a4-8ab7-723867aec905/historical-review-FAIL.md. All 373 pre-existing evidence/report files in the retry snapshot remain byte-identical. No historical result was changed to PASS.

| Preserved report | SHA-256 |
| --- | --- |
| Previous implementation report | 82c87d88ff10985c5c18015768a7afc0442813608b7fbb616aa2ba405f249caf |
| First Round-1 locale-query failure | e7e372d24c567a41ea66c32d730f0713e70d060ac2f3c178b078d1e9a1b0b751 |
| Locale PASS / stale restart endpoint FAIL retry | 598f8e80f8eea33124671f0d251498173677d1f9562e496bd5c836661e0b5fac |

The earlier image, PGDATA, IPAM and initialization failures remain historical evidence. The previous 191-test green build and prior collector PASS are not substitutes for this corrected-byte run.

## Exact source and environment binding

Branch: poc/04-security-auth. HEAD and both origin refs: 01256d96d85823740a8d6c3a3f99346afbdbef56. Index empty. No Git mutation was executed. source-binding.json records the tree, all 272 source file hashes/modes/lengths, tool binding and excluded generated/evidence paths.

Current run UUIDv4: 6222c112-8ea7-4b28-8092-abc84f88270f. Canonical source-content SHA-256: bacf3084b406f644924aefadfb1fcafeec950f561d6e6a47357f8095712a6660. Source bytes were unchanged throughout execution. Only the following three source paths changed since the preserved locale retry:

| Path | Current SHA-256 |
| --- | --- |
| backend/migration/src/test/java/dev/vra/migration/Poc04FoundationMigrationIntegrationTest.java | f5654ca3253adc191767c5923935a7826bfbe9d2eb7400da16acc4e4fb0fbf84 |
| validation/poc-04/scripts/run-harness.py | f631088db8a63bb8748e02a6a7fbbaadfeade55672c2e4731f90f55348631dc6 |
| validation/poc-04/test-support/src/dev/vra/poc04/external/RunOwnedPostgreSQLContainer.java | e72e22bce87d94f71bcdada9a54ef757f0faa732a300b0675b29ca63dc71f70d |

RunOwnedPostgreSQLContainer.currentJdbcEndpoint() obtains live Docker inspection using the supported getCurrentContainerInfo API, checks a running container and current mapped 5432/tcp binding, and constructs a fresh JDBC URL preserving normal URL parameters. getJdbcUrl() returns that live endpoint. The focused fixture uses scoped connections; no JDBC connection or pool survives its native Docker restart. Both fresh A and B are initialized before restarting A. Before/after identity and endpoint records are emitted. The physical binding permits a different creator-authorized container incarnation while retaining all durable identity fields, and finish checks that each instance UUID has exactly one physical system identifier.

No fixed host port was introduced. This run actually tested a same-container native restart with a changed published port; replacement-container execution was not performed. V4, its reason expression, V1–V3, bootstrap/grants, dependencies, pins and Phase-0 authority were unchanged in this narrow retry.

Only DOCKER_HOST=unix:///Users/siwakornbundi/.colima/vra-poc04-test/docker.sock was used; socket override=/var/run/docker.sock; host override=127.0.0.1. Dedicated daemon ID=14c8ff26-86d1-4c0b-88a6-560be91a65ea, name=colima-vra-poc04-test, Client/Server29.5.2 linux/arm64. Effective 10.241.0.0/16 child /24 pool retained. Ryuk remained enabled. No profile change, Docker Desktop access, persistent vra-poc00 access or prune.

Image execution reference: docker.io/library/postgres@sha256:d74eeac9a635390a49bc21bd49fccd973de707e2a53a76ac49b552b8712ec46f. Execution platform linux/arm64/v8; selected manifest sha256:86fa57b44a1d38f09970f64ac71eb492f7a9cf94d08b85e1c8f07bb0fc0a1761; exact immutable manifest config.digest sha256:97432f980da100ebd3e419711efee84e1e97a966d62c035286a07f239ddb4d9c. Runtime independently170011 / PostgreSQL17.11. Index, manifest and config are separate assertions. PGDATA evidence retains exact typed mount/volume identity plus authoritative daemon Type=volume; no unsupported docker-java raw Type lookup.

## Four independent-review blockers

| Original blocker | Remediation and new evidence | Status |
| --- | --- | --- |
| Daemon-wide teardown authorization | Trusted creation receipts populate a restricted current-run exact-kind/ID ledger; live run/source/instance labels and creation identity checked before deletion. Discovery is observation only. | Focused PASS; final ledger cleanup PASS |
| Missing teardown negatives | Other-run sentinel survives; wrong ID and missing source label denied before delete; independent exact-ID admin cleanup only after preservation. | EXECUTED PASS |
| Fresh database_instance_id reuse | File-locked run-scoped allocator shared across Gradle/Python processes; advancing separate role ordinals; exact UUIDv5; physical map and final one-system-per-UUID invariant. | Focused PASS; final45 physical bindings PASS |
| Whitespace-only reasons | Both required V4 reason CHECKs use reason ~ '[^[:space:]]'; actual PG17.11 locale/catalog and positive/negative execution. | EXECUTED PASS in current full migration suite |

## Focused identity/restart retry — EXECUTED PASS

Command, cwd backend:

    ./gradlew --no-daemon --init-script ../validation/poc-04/test-support/runner.init.gradle :migration:integrationTest --tests dev.vra.migration.Poc04FoundationMigrationIntegrationTest.twoFreshInstancesHaveDistinctIdsAndRestartRetainsPhysicalIdentity --rerun-tasks

Exit0; 1 test /0 skipped /0 failures /0 errors. Raw XML and log are under focused-identity-results and focused-identity.log; focused-identity-command.json preserves exact command/environment. focused-identity-proof.json independently parses the raw records. Fresh A/B have different volume, ordinal0/1, UUIDv5 and system_identifier. Native restart of A retained volume, UUID, logical name, physical system ID and its committed account sentinel. Published host port changed32856→32858; the current fresh JDBC endpoint worked, Flyway validate passed and rerun executed0 migrations. focused-identity-cleanup.json proves6 exact registered resources absent.

## Teardown and cross-process allocator negatives — EXECUTED PASS

Command:

    python3 -B validation/poc-04/scripts/teardown-negative-proof.py

Exit0. This is a deterministic Docker/allocator proof, not a JUnit suite. teardown-negatives.json preserves25 safe command/event records; focused-teardown-command.json binds exact source/JAR/environment. Sixteen independent Python processes (at most4 concurrent) allocated unique ordinals0–15 under teardown-probe; every UUIDv5 recomputes exactly under the current run namespace and each process exited0.

Current-run volume and network were immediately creator-registered and deleted by exact ledger identity. Other-run sentinel volume vra-poc04-negative-sentinel-ba3f2b51-1510-452d-abe6-82871f966495 retained the exact snapshot through current-run teardown. An explicit request for that unregistered wrong ID returned exit3 / RESOURCE_NOT_IN_AUTHORIZED_CURRENT_RUN_LEDGER, docker_delete_called=false, unchanged resource. A controlled replacement candidate at the registered volume name with its source-content label missing returned exit3 / LIVE_OWNERSHIP_LABEL_MISMATCH, docker_delete_called=false, unchanged replacement snapshot. Only separate admin exact-ID cleanup, with independent expected ownership snapshot checks, removed the negative candidate and the preserved sentinel afterward. All exact IDs, before/after snapshots and successful cleanup receipts remain in the JSON.

## Full Phase-1 fresh and populated database proofs — EXECUTED PASS

Source adapter: validation/poc-04/test-support/src/PhaseOneEvidenceCapture.java; reuses the actual compiled foundation fixture and assertions. database-proof-command.json records the exact Java source-launch command/classpath, exit0, source binding and environment. database-proof.json records each stage, database identity, raw catalog projections and SQLSTATE cases. Adapter executions are not counted as JUnit tests.

Fresh: V1→V4 executed4 migrations; V4 exactly once; validate PASS; same-DB rerun0; validate PASS; all27 authoritative-table digest maps equal before/after rerun. Populated: target3 executed3 migrations and validated; seeded10 existing tables including inventory, V2 transient/success/rejection and POC-03 outbox/inbox/reconciliation/history; V4 executed1 migration exactly once and validated; all prior row hashes/counts,3 Flyway rows/checksums and103 prior catalog rows unchanged. Rerun0 and subsequent negative proofs retained all prior digests.

Both database projections match: all17 foundation tables owned vra_owner;129 named V4 constraints (79 CHECK,25 FK,17 PK,8 UNIQUE),19 explicit indexes, intended exact ACLs and installed reason CHECK definitions. Complete raw table/column/constraint/index/catalog results are bound in database-proof.json; projection limitations: default expressions, pg_type.typacl and function ACLs are verified by fixture assertions where present rather than exhaustively exported in this projection.

All six new roles match restrictive attributes. Executors vra_sync_executor/vra_security_executor/vra_telemetry_executor: NOLOGIN/NOINHERIT/NOSUPERUSER/NOCREATEDB/NOCREATEROLE/NOREPLICATION/NOBYPASSRLS. Test helpers vra_factor_fixture/vra_audit_evidence_reader/vra_security_telemetry_observer: LOGIN with the same remaining non-admin attributes. Direct membership SET TRUE/INHERIT FALSE/ADMIN FALSE: migrator→owner; owner→existing async executor and all three new executors. The complete transitive SET graph has9 accepted edges including migrator→owner→executor; ordinary runtime/workers/reconciler/helpers have no executor SET path.

Trusted vra schema CREATE holders: postgres and vra_owner only. PUBLIC table/sequence/schema grant projections empty. Ordinary workloads and new executors have no CREATE; all new authoritative owners are vra_owner. Runtime V4 SELECTs, exact record UPDATE(label,version), webhook receipt status/target state+version updates, and security_event APP safe column INSERT/limited SELECT match approved boundaries. Protected audit has no direct runtime SELECT/DML. No worker/reconciler new V4 DML. Existing V2 runtime INSERT/UPDATE residual was retained. Full table/column ACL rows are in runtime_and_workload_v4_acl and catalog projections.

Per database,71 explicit denials:58 SQLSTATE42501 privilege denials,9x23514 CHECK,3x23505 uniqueness,1x23503 FK. Actual workload credentials were used for all9 ordinary roles against all3 executor SET paths, runtime DDL/role mutation and protected object boundaries. Executor CREATE is tested through the administrative SET path, not a fictitious executor LOGIN. Every exported denial records transaction rollback and authoritative rows unchanged. Full fixture tests additionally execute the broader approved shape/constraint cases; no Phase-2 function-denial claim.

## Corrected reason behavior — EXECUTED PASS

The current full migration suite reran reasonWhitespaceContractOnPostgres1711 with no skips/failures/errors. Actual pg_database locale metadata: datlocprovider=c (libc), datcollate=en_US.utf8, datctype=en_US.utf8, datlocale=NULL, encoding=UTF8. For both security_role_proposal and protected_security_audit, empty, ASCII spaces, tab, newline, carriage return, CRLF and mixed spaces/tabs/newlines failed the intended CHECK/SQLSTATE23514:14 denials. Ordinary text and text surrounded by whitespace succeeded:4 positives. Each case rolls back and all27 authoritative snapshot hashes remain equal. Metadata provides context; executed behavior proves nonblank for this TEST database. Raw POC04_REASON_CONTRACT records are in the final foundation JUnit XML; reason-behavior-current.json extracts them.

## Full backend check/build — EXECUTED FAIL / STOP

Command, cwd backend:

    ./gradlew --no-daemon --init-script ../validation/poc-04/test-support/runner.init.gradle check build --rerun-tasks

Exit1. BUILD FAILED in4m38s,16 actionable tasks executed. Raw command/environment, log and copied JUnit XML/binary artifacts: full-check-build-command.json, full-check-build.log, full-check-build-results/. Final counts are independently reconciled from all raw XML:

| Suite | Tests | Skipped | Failures | Errors |
| --- | ---: | ---: | ---: | ---: |
| migration:test | 4 | 0 | 0 | 0 |
| migration:integrationTest | 20 | 0 | 0 | 0 |
| runtime:test | 56 | 0 | 0 | 0 |
| runtime:integrationTest | 113 | 0 | 1 | 0 |
| Final full suite | 193 | 0 | 1 | 0 |

The two added remediation methods account for migration integration20 versus the prior18; runtime integration still113. This retry has194 JUnit executions including the focused1;193 is the single full-suite count. No skip is reported as PASS. The earlier304 total included focused/remediation executions and remains historical.

Sole failure: dev.vra.platform.health.HealthProbePostgresIntegrationTest.databaseOutageMakesReadinessDownButKeepsLivenessUp(). java.net.http.HttpTimeoutException: request timed out at get line169 → awaitReadinessDown line130 → test line113. The client did not complete the post-stop readiness503/DOWN assertion and the post-outage liveness request/assertion was not reached. The request timeout is5 seconds; the outer readiness deadline is20 seconds. Raw server output later records DataSourceHealthIndicator taking30014ms and readiness HTTP503 at14:40:08.370780Z; that late server response does not supply the missing completed client/body or liveness proof. No transient/product/harness root cause is inferred. full-check-build-failure.json preserves the exact exception and raw XML path. No source change, timeout widening or retry followed this failure.

## Collector and remaining obligations — NOT EXECUTED

Current-run collector TEST OS/mount/SQL proof was not executed because full runtime regression failed and the same-run PASS guard was not enabled. Prepared collector bytes/profile remain unchanged and parse; previous reported collector PASS is preserved, not used for corrected-byte acceptance. No Phase-2 observer/parser/telemetry capability was started or implemented. Full check/build remains FAIL; Phase-1 re-review readiness remains NO.

## Cleanup and run-wide physical identity

Initial post-build finish assertion failed with RUN_RESOURCE_STILL_PRESENT; cleanup-consistency.json preserves that failure. No source remediation followed it. Exactly five still-present current-run network IDs were intersected with the pre-existing creator ledger and removed via run-harness.py teardown; every operation rechecked exact run/source/instance labels and identity. post-failure-exact-ledger-cleanup.json preserves each exact-ID command/exit/receipt. No discovery-only resource was deleted.

cleanup-final-consistency.json: exit0, all138 creator-ledger resources absent; all45 physical instance bindings complete and unique. all-observed-container-absence.json independently proves49 observed exact container IDs absent; observations confer no deletion authority. final-daemon-state.json:0 containers,0 volumes,only the same original bridge/host/none IDs. Negative sentinel/candidate absence was separately proven through independent admin exact-ID cleanup. No shared/persistent resource was deleted. The safe recorder was stopped using its exact run stop file after cleanup.

For each UUID below, exactly one system_identifier exists across all captured incarnations. UUIDv5 is derived from run6222c112-8ea7-4b28-8092-abc84f88270f plus the listed role/ordinal. Separate authority/simulator ordinals advance across focused, adapter and Gradle processes. Exact CID/volume/network names/IDs/labels and physical_identity events are retained in final-run-ledger.json and Docker evidence.

| Logical name | UUIDv5 database_instance_id | PostgreSQL system_identifier |
| --- | --- | --- |
| postgres-authority/0 | 3bf104bf-968f-5930-8e19-6344bc163457 | 7691705906600251430 |
| postgres-authority/1 | a3a66ed8-2ba1-553b-b330-54d178f70c77 | 7691705933981405222 |
| postgres-authority/2 | ca6652b7-6a7c-537c-9d62-791218646ca0 | 7691707769863807014 |
| postgres-authority/3 | 9a0bb07b-19c0-555c-b189-23b95267b01b | 7691707869373976614 |
| postgres-authority/4 | 944811c1-e84f-56e5-858f-4f6b4701a2c6 | 7691708071195930662 |
| postgres-authority/5 | 49f1c013-e612-52ff-9d02-a77d876b74f5 | 7691708084657446950 |
| postgres-authority/6 | 0eca62ac-1737-532e-968d-934fecc810cd | 7691708096690937894 |
| postgres-authority/7 | 0e774062-361d-51cd-89c9-63238e05da82 | 7691708113229279270 |
| postgres-authority/8 | 407579be-f2a2-5d7b-a686-7183e05604e1 | 7691708127112560677 |
| postgres-authority/9 | 39d952a5-2c6c-5739-bfda-710d2175c9ff | 7691708139559669797 |
| postgres-authority/10 | e924b6ea-cebc-55cd-9b88-66c904f5adbd | 7691708152956555302 |
| postgres-authority/11 | ed9a20de-662b-5366-aa8d-eeef9d83b2cb | 7691708165630857254 |
| postgres-authority/12 | 3f6607c1-0301-5f76-a2a8-808c9104aea1 | 7691708178282893350 |
| postgres-authority/13 | 00d9e14a-e893-5654-8b07-cb2d8dc4d80c | 7691708231571361830 |
| postgres-authority/14 | ee0d62f5-3990-56e8-b043-ee3a6d78b061 | 7691708243955798054 |
| postgres-authority/15 | f06eee32-c769-5d94-8daa-918e5e47f2ac | 7691708257320210469 |
| postgres-authority/16 | 73607d7f-ad0a-5346-9e59-00a5060a6249 | 7691708269799219238 |
| postgres-authority/17 | f7536876-2269-559c-8953-92603c3e848a | 7691708282118926375 |
| postgres-authority/18 | 4206d762-fc08-563a-896a-35862d0a2dd0 | 7691708294042009637 |
| postgres-authority/19 | e490c57c-e5bb-567d-bd4d-81ea771ca035 | 7691708329270394918 |
| postgres-authority/20 | 8457fd9d-4572-5b17-99bd-d04bdb79fda2 | 7691708424444538918 |
| postgres-authority/21 | 21e29283-899d-5898-b7d3-2f1a1869fd28 | 7691708457002106918 |
| postgres-authority/22 | a6488208-087e-5bd9-91b4-779ea6ce0b03 | 7691708547626569766 |
| postgres-authority/23 | 3fbb8b56-9b6d-52e5-9196-f6923b671d45 | 7691708578504937510 |
| postgres-authority/24 | 8e311d2b-e75e-534f-bd87-d98ad4fc75f5 | 7691708608909869094 |
| postgres-authority/25 | b0359670-e204-56a8-ac71-b3f111721e88 | 7691708647804678182 |
| postgres-authority/26 | bc84ba06-bc6d-5224-afb1-6b6704332e1d | 7691708662603755558 |
| postgres-authority/27 | 0e5ea56a-e530-5181-aa6a-33c8eca94af3 | 7691708680196833318 |
| postgres-authority/28 | b84a5414-8900-57f2-aee1-094bde7b9c52 | 7691708696784338982 |
| postgres-authority/29 | 291e507c-8a1a-59dc-8e8d-5e0bf9df2c93 | 7691708710758907942 |
| postgres-authority/30 | 8c642b1e-050d-53f8-b654-241efc739291 | 7691708732639862822 |
| postgres-authority/31 | 23ece2c8-52b3-567b-8034-a248ecccd60f | 7691708749090029605 |
| postgres-authority/32 | 2a26ff7f-8151-51cf-a3d5-745d9e146766 | 7691708764367749158 |
| postgres-authority/33 | f73b7fdf-cc2d-504b-b9c2-86cfac83b7ca | 7691708799898251302 |
| postgres-authority/34 | 7ef710f6-7d42-57aa-b1ac-94be38bf1dff | 7691708816700952614 |
| postgres-authority/35 | b619fa95-13be-5be5-b9b2-128e72695925 | 7691708844003663910 |
| postgres-authority/36 | b5c4ff8f-a19b-598a-bc63-f7723f3086af | 7691709030185725990 |
| postgres-authority/37 | cc209018-a32f-55e9-a399-4163c079dd6d | 7691709039989239846 |
| postgres-authority/38 | 5b3c8b9b-56d6-5d7b-98f2-3fa86b78b78c | 7691709050835415078 |
| postgres-authority/39 | d721fb04-673f-51d8-bb5c-412c9105e1f4 | 7691709061868015653 |
| postgres-authority/40 | 4f3ef7e4-25a3-50fd-a5bc-45eca291871d | 7691709071371644966 |
| postgres-authority/41 | 640d3769-9d42-59c6-a59f-31009262cf28 | 7691709081735630886 |
| postgres-simulator/0 | 00c879e4-b135-5f58-afd1-615b3b124c3c | 7691708772079796262 |
| postgres-simulator/1 | abc8993c-0af3-50fe-a6e8-67df5be82804 | 7691708824278745126 |
| postgres-simulator/2 | 7ad9f510-3930-5abc-bbe2-08b7101d9550 | 7691708851628257318 |

## Immutable migrations, safety and final binding

| File | Current SHA-256 / accepted Phase-0 comparison |
| --- | --- |
| backend/migration/src/main/resources/db/migration/V1__inventory_reservation_foundation.sql | d804cbe5153b9a904ae0747af55c8295079d4a0c3edb4a38145ab5199b5b59c4 — exact equality |
| backend/migration/src/main/resources/db/migration/V2__inventory_reservation_idempotency.sql | 7bef750b59b7b77e8402f4f42c13c21c4e072092504b9f268bace9b72de8d2bb — exact equality |
| backend/migration/src/main/resources/db/migration/V3__outbox_recovery.sql | 9edbd15ffe08ee4edafa3f549856ee38a782485c54db9c73e69d4263e5ca26b4 — exact equality |
| V4 corrected candidate | 7e2a1ea77ba92a451763c7e06ab7dc4cee6e06f761bbbb4e6cb1c786c8a21ffc |
| bootstrap-security-roles.sql | 976cf704687e5bf005de8943c706fdd996d801172c112d640c993e0b193407b5 |

V4 remains an uncommitted Phase-1 candidate in disposable TEST DBs only. No Flyway repair or shared history rewrite. Controlling spec/plan/accepted ADRs/Phase-0 pins are byte unchanged. Baseline JdbcReservationIdempotencyRepository already exists and remains byte-identical to HEAD; no Phase-2 behavior modification. V5:NO; V6:NO; new observer/parser/API-worker capability validators:NO; Phase2 started:NO.

git diff --check passed before focused execution and at final inspection. Authored source/support has no trailing whitespace/final-newline defect. Separate raw-output inspection found19 evidence files with whitespace/newline findings:18 preserved historical files and1 current raw JUnit XML; these are reported and retained without normalization. JSON/JSONL and Python AST parse checks pass; collector YAML parses with safe aliases enabled. The first generic Ruby safe-load attempt rejected its expected run_labels anchor because aliases were disabled; that tooling result is preserved in final-source-integrity.json and the appropriate alias-enabled parse exited0. No config bytes changed.

Gitleaks8.30.1 scans all modified/untracked bytes through runtime stdin; secret-scan.json retains only safe classifications. Eleven matches at the first final scan are exact verified Git/file-SHA metadata false positives;0 unresolved credentials. No credential, raw collector log, private key, session/factor/webhook secret was persisted. Final rescan and complete tracked-modified/untracked/staged inventory plus SHA-256 for every file are in FINAL_BINDING.json and secret-scan-final.json. FINAL_BINDING excludes itself from recursive self-hashing.

Unresolved blockers: full runtime health-outage integration failure; full check/build FAIL; collector boundary on current corrected bytes NOT EXECUTED. The four original remediation focused proofs passed, but the mandatory complete green rerun did not. Independent Phase-1 re-review has not passed. Nothing staged/committed/pushed. Phase1 remains open; Phase2 NOT AUTHORIZED; POC04/S0–S15 NOT VERIFIED.
