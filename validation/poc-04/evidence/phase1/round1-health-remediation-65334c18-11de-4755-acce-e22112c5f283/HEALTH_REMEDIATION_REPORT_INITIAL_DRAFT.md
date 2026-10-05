# POC-04 Phase-1 health remediation execution report

PHASE 1 CLOSED: NO

READY FOR INDEPENDENT PHASE-1 RE-REVIEW: NO

Phase 2: NOT AUTHORIZED / NOT STARTED

POC-04 / S0-S15: NOT VERIFIED

Run ID: `65334c18-11de-4755-acce-e22112c5f283`. All successful results below are implementation-side executed evidence pending independent review. This is a new report; no prior report or failure was overwritten.

## Remaining blocker and stop

The health regression and complete build passed. The required collector proof then FAILED (exit 1) before creating resources. `collector-boundary-proof.py:279` constructs the override path as `state_path.parent/'collector-local-platform-'+str(allocation['ordinal'])+'.override.json'`. AST inspection confirms the division produces a Path, followed by unsupported Path-plus-string addition. The preserved executed exception is TypeError; the source handler suppressed traceback details. The static diagnosis is supported by completed immutable image inspections and zero creation receipts. See `collector-failure-diagnosis.json`, `collector-proof-command.json`, `collector-boundary/commands.json` and both preserved collector summary files.

Collector OS/mount/SQL boundary: NOT VERIFIED. No collector container/network/volume was created, its ephemeral admin credential file is absent, and later boundary assertions were not reached. Its unused allocation `postgres-authority/41` / `ff4d86cd-4eb4-579c-9e78-ea210b6be9b8` has no physical database identifier. No source correction or retry was performed after this failure. Collector source remains SHA-256 `8e618e48b4d9d27fa05805a3e276c06cc8f7e99e4521e31f036343ced255b2b5`.

## Historical failure preservation

- Original implementation report remains `82c87d88ff10985c5c18015768a7afc0442813608b7fbb616aa2ba405f249caf` (expected82c87d88ff10985c5c18015768a7afc0442813608b7fbb616aa2ba405f249caf).
- Independent Phase-1 review FAIL remains authoritative historical evidence, including all four Round1 blockers recorded in the preserved remediation reports and user review. It has not been upgraded to PASS.
- Previous corrected-byte full build failed193tests/0skips/1failure/0errors, with collector NOT EXECUTED. Its unchanged restart report remains `786ec48925a56e67e9f9491c3d2922b876d83742758db4a2071de5c1eed197f4` (786ec48925a56e67e9f9491c3d2922b876d83742758db4a2071de5c1eed197f4).
- The new unchanged-source focused diagnosis also failed1test/0skips/1failure/0errors; report SHA-256 `ae95807d088dbb74559d478160d9d00a4c6147b5ae4a114da9d2ca6fb6cfea8d`. Its raw failed XML, timing evidence and DIAGNOSIS_BINDING remain unchanged.
- All554 pre-existing modified/untracked source/evidence paths other than the authorized health test retain their pre-remediation SHA-256. Earlier image, mount, Ryuk, IPAM, locale and restart failures remain preserved. The new collector failure remains FAIL.

## Proven health cause and narrow correction

Actual original datasource: com.zaxxer.hikari.HikariDataSource; acquisition30000ms, validation5000ms, maximum10, minimum10. Original JDBC connect/socket1s and HTTP connect2s/request5s/overallreadiness20s were unchanged. During the original readiness wait, actual threads showed Hikari ConcurrentBag.borrow/getConnection, no connections available and one acquiring thread. This snapshot preceded the extra diagnostic readiness request. Independent liveness returned200/UP in2.501ms while acquisition was waiting. The original test threw HttpTimeoutException. This establishes CASE A, pool acquisition exceeding the HTTP deadline; it does not establish a production-wide configuration defect. Original exact client start/end was not instrumented; the preserved diagnostic report distinguishes stack timing from the separately measured5-second diagnostic request.

Only `backend/runtime/src/test/java/dev/vra/platform/health/HealthProbePostgresIntegrationTest.java` changed in this correction, SHA-256 `3c9cdc89a6f297c6cdd655d94be2b3c10aca802a9570e1d04ab18319acdf9719`. TEST-only @Import and static BeanPostProcessor configure the actual named Hikari bean before pool initialization to acquisition2000ms/validation1000ms. The executed null-pool assertion and later actual initialized values prove lifecycle placement. Pool size and credentials are unchanged. Production `backend/runtime/src/main/java/dev/vra/platform/configuration/DatabaseConfiguration.java` remains `fcbafb216ebfd5218d079fc84a7fb9a0c5aeeb5a764aa4db967b797583983153`. The test records safe pool counters/current runtime identity and concurrent liveness; exceptions are logged by class then rethrown. It requires actual readiness503/DOWN and liveness200/UP. HTTP and JDBC budgets remain unchanged; no static ports, timeout-as-DOWN, skips or new correctness sleeps were introduced.

An initial live-setter candidate `c4415d925530b1c27602f93e8815a41592d5e03230bd18af9882b0900ebba2cb` was never executed. Static review of pinned Hikari bytecode showed cached acquisition timeouts updated by housekeeping; the before-initialization design replaced that candidate before focused execution. The historical diagnosis report describes that unexecuted earlier candidate; PLAN.md records the final design. No live timeout restoration is part of the executed correction.

## Focused and full-run HTTP evidence

The focused entire HealthProbePostgresIntegrationTest passed1/0/0/0. Actual SQL identity: vra_runtime / vra_health_test / PostgreSQL170011. Initialized pool:2000ms/1000ms/max10/min10. Raw copied XML matches all11 extracted timing records. No timeout exception was accepted.

| Probe | Focused HTTP/status | Focused latency ms | Full-run latency ms |
|---|---|---:|---:|
| Liveness before |200/UP|67.580333|10.870084|
| Readiness before |200/UP|5.293833|4.554916|
| Readiness after outage |503/DOWN|2027.294833|2022.880250|
| Liveness during actual acquisition wait |200/UP|2.613958|1.856959|
| Liveness after |200/UP|4.823708|6.696292|

In both executions, liveness start and end are inside the measured readiness request interval, with a real pool waiter. See focused-health-timing.json, focused-health-verification.json, full-check-build-verification.json and their raw JUnit XML.

## Commands and regression counts

All commands use backend as working directory and the safe environment captured in their command JSON. Exact wrapper flags and source/JAR hashes are recorded in each artifact.

| Command | Exit | Evidence |
|---|---:|---|
| `./gradlew --no-daemon --init-script ../validation/poc-04/test-support/runner.init.gradle :runtime:integrationTest --tests dev.vra.platform.health.HealthProbePostgresIntegrationTest --rerun-tasks` |0|focused-health-command.json / focused-health.log / focused-health-results|
| `./gradlew --no-daemon --init-script ../validation/poc-04/test-support/runner.init.gradle check build --rerun-tasks` |0|full-check-build-command.json / full-check-build.log / full-check-build-results|
| PinnedJDK Java source launcher PhaseOneEvidenceCapture.java, mode both |0|database-proof-command.json / database-proof.json / database-proof.log|
| `python3 -B validation/poc-04/scripts/teardown-negative-proof.py` |0|focused-teardown-command.json / teardown-negatives.json|
| `python3 -B validation/poc-04/scripts/collector-boundary-proof.py` |1|collector-proof-command.json / collector-proof.log / collector-boundary|
| Authorized current-run run-harness teardown per exact ID, then finish |0 each|final-ledger-cleanup.json / cleanup-consistency.json / final-run-ledger.json|

| Final complete suite |Tests|Skipped|Failures|Errors|
|---|---:|---:|---:|---:|
| migration:test |4|0|0|0|
| migration:integrationTest |20|0|0|0|
| runtime:test |56|0|0|0|
| runtime:integrationTest |113|0|0|0|
| Full build total |193|0|0|0|

The20 migration integration tests include the2 Round1 reason/physical-identity additions. Raw32XML files reconcile to193 actual testcase nodes. Crash tests execute all12 cases. This single full-suite count is193, not the historical191 or cumulative304. This corrected run has194 successful JUnit executions including the focused health test; procedural evidence commands are not JUnit tests. Collector exit1 is a separate failed mandatory proof and is not concealed by green JUnit counts.

## Corrected-byte database/security rerun

FreshV1→V4 and populatedV3→V4: PASS in both the complete suite and new raw adapter execution. The adapter fresh run applies4 migrations; populated applies3 then1. Both validate and same-database rerun applies0. V4 applied exactly once; checksum-1759282690. All27 same-database rerun table digests equal. Populated10 prior tables are nonempty; V2 includes3 accepted transient/terminal rows. Before/after/final digests, counts,103 prior catalog rows, and all fields of the three prior Flyway history rows are unchanged. V1–V3 checksums remain-290113401/-1049090241/-70525330. See database-proof.json for actual row digest/history/catalog projections, database identities and individual stages.

Both raw V4 catalog projections contain17 vra_owner-owned foundation tables,129 named constraints(79CHECK/25FK/17PK/8UNIQUE),19 explicit indexes and152 table/column ACL rows. Schema CREATE holders are postgres and vra_owner; PUBLIC table/sequence/schema projections are empty. All six POC04 role attributes match the required login/nonlogin and nonadministrative flags; ordinary workers/helpers have no new V4 DML or CREATE. Executor privileges remain USAGE without CREATE or Phase2 capabilities.

| New role |LOGIN|INHERIT|SUPERUSER/CREATEDB/CREATEROLE/REPLICATION/BYPASSRLS|
|---|---|---|---|
|vra_sync_executor|false|false|allfalse|
|vra_security_executor|false|false|allfalse|
|vra_telemetry_executor|false|false|allfalse|
|vra_factor_fixture|true|false|allfalse|
|vra_audit_evidence_reader|true|false|allfalse|
|vra_security_telemetry_observer|true|false|allfalse|

Direct memberships: migrator→owner; owner→async_executor plus sync/security/telemetry executors. EveryedgeSETtrue/INHERITfalse/ADMINfalse. Catalog completeSETgraph has9 edges: migrator→owner and eachof4executors, owner→eachof4executors. No ordinary workload/helper path is present. Real ordinary credentials execute denied SET ROLE/DDL/role-mutation statements. The NOLOGIN executor CREATE denial uses the accepted migrator administrative SET path.

Runtime V4 direct rights remain SELECT on permitted foundation tables; UPDATE(label,version) on both POC records; no protected-audit SELECT/DML; approved safe APP INSERT/SELECT columns on security_event (no source/provenance INSERT columns); webhook receiptSELECT+INSERT+UPDATE(status), targetSELECT+UPDATE(state,version). Worker/reconciler new grants: none. Accepted V2 runtime direct INSERT/UPDATE residual remains unchanged. No Phase2 function-denial claim is made.

Each adapter database records71 actual denials:58x42501,9x23514,3x23505,1x23503, with rollback/unchanged-row proof. They cover actual-login escalation/DDL/role changes, runtime APP-provenance boundary, invalid states/negative generation/digest lengths/authority duplicates/FKs/actor shapes. No grant was widened.

## Original four review blockers and reexecuted remediation proof

1. Teardown authorization: only exact kind/ID registered by the current creator into the current locked ledger can authorize deletion; live run/environment/POC/source/database labels and stable resource identity are rechecked. Observations confer no authority. Current full-run cleanup and separate negative proof execute this implementation.
2. Teardown negatives: current exact volume/network removed while other-run sentinel remains; wrong-ID and controlled missing-label attempts both exit3 before deletion, with unchanged snapshots. Sentinel/candidate later removed only through separate admin exact-ID cleanup with independent snapshots. See25 records in teardown-negatives.json.
3. Fresh-instance UUID reuse: shared locked run-scoped ordinal allocator is used by every Gradle/process incarnation.16 separate allocator processes have unique ordinals/exact UUIDv5. Native restart A retains volume/UUID/system/data and refreshes endpoint32951→32954; new B has distinct ordinal/volume/UUID/system. Full44-instance consistency maps each initializedUUID to exactly one physical identifier; no unused allocation is reported as an initialized database.
4. Nonblank reasons: corrected V4 retains the same POSIX expression in proposal/audit. ActualPG17.11 database metadata: libc provider c, datcollate/datctype en_US.utf8, datlocale null, UTF8. Full-run reason behavior denies14 whitespace cases with23514 and accepts4 nonblank cases with00000; all cases rollback with27 unchanged table digests. Includes empty/spaces/tab/newline/CR/CRLF/mixed values on both constraints. See poc04_reason_contract-records.json and raw Foundation XML.

These successful executions do not replace the historical independent review FAIL or establish independent acceptance.

## Isolated environment, image identity and disposability

Only `unix:///Users/siwakornbundi/.colima/vra-poc04-test/docker.sock` was used; overrides /var/run/docker.sock and127.0.0.1; Ryuk enabled. Daemon14c8ff26-86d1-4c0b-88a6-560be91a65ea, colima-vra-poc04-test, server29.5.2linux/aarch64; currentCLI29.8.1 is accurately recorded. Saved isolated profile/pool10.241.0.0/16 size24 unchanged. Docker Desktop, persistent vra-poc00, profile restart/mutation and blanket prune were not used in this run. PinnedJDK/Gradle/Testcontainers/docker-java versions remain unchanged.

Execution image immutable indexd74eeac9a635390a49bc21bd49fccd973de707e2a53a76ac49b552b8712ec46f; platformlinux/arm64/v8; manifest86fa57b44a1d38f09970f64ac71eb492f7a9cf94d08b85e1c8f07bb0fc0a1761; manifest-linked config97432f980da100ebd3e419711efee84e1e97a966d62c035286a07f239ddb4d9c. Running database version independently170011. Existing source-bound provenance logic and daemon projections keep these distinct. Exact run-owned PGDATA raw daemonType=volume/Name/Source/RW and typed ownership checks remain enforced. Immutable pins did not change.

Final corrected ledger:135resources absent,44initialized physical databases. Final administrative cleanup removed10 residual current-run resources(5networks/5volumes) through exact ledger authorization; everycommandexit0. Negative sentinel/replacement are removed only through their recorded separate administrative path. All48 observed containerIDs including Ryuk helpers are absent. Finaldaemon0containers/0volumes, only the original bridge/host/none IDs. See final-daemon-state.json and cleanup-consistency.json. Collector's empty resource set and credential-file absence are independently preserved.

Two final read-only evidence-reader attempts failed on a metadataheader KeyError and an unavailable Ruby safe_load_file API. Both attempts are preserved; corrected readers filter explicit Docker events and use installed YAML.safe_load(File.read(...), aliases:true). Neither attempt changed resources, config or source. Final equivalent inspections pass.

## Source and evidence binding

Branchpoc/04-security-auth; HEAD01256d96d85823740a8d6c3a3f99346afbdbef56; remote refs/tree unchanged. Nothing staged. Source manifest272files, canonical contentSHA-256 `94c06b9b99095f30d39d846c82e04315b14c33fca101c23c5d244cfd91a6e540`. Only health test differs from the prior frozen restart/diagnosis source. Approved spec/plan/ADRs and canonical docs remain exact. Existing baseline JdbcReservationIdempotencyRepository.java remains byte-identical; its existence is not Phase2 work.

|File|Current SHA-256|Comparison|
|---|---|---|
|V1 inventory reservation foundation|d804cbe5153b9a904ae0747af55c8295079d4a0c3edb4a38145ab5199b5b59c4|exactPhase0|
|V2 inventory reservation idempotency|7bef750b59b7b77e8402f4f42c13c21c4e072092504b9f268bace9b72de8d2bb|exactPhase0|
|V3 outbox recovery|9edbd15ffe08ee4edafa3f549856ee38a782485c54db9c73e69d4263e5ca26b4|exactPhase0|
|V4 candidate|7e2a1ea77ba92a451763c7e06ab7dc4cee6e06f761bbbb4e6cb1c786c8a21ffc|unchangedbyhealthfix|
|POC04 rolebootstrap|976cf704687e5bf005de8943c706fdd996d801172c112d640c993e0b193407b5|unchanged|
|Health integrationtest|3c9cdc89a6f297c6cdd655d94be2b3c10aca802a9570e1d04ab18319acdf9719|authorizedTESTfix|
|Production datasourceconfiguration|fcbafb216ebfd5218d079fc84a7fb9a0c5aeeb5a764aa4db967b797583983153|unchanged|

Gitdiff--check exit0. Separate authoredsource/support trailing-whitespace/final-newline inspection:0findings;22raw historical/current evidence findings are recorded and preserved without normalization. JSON/JSONL/YAML/Python parse checks pass. Initial complete changed/untracked-byte Gitleaks8.30.1 scan has16 exact verified Git/file/source-SHA metadata false positives,0unresolved; final scan including this report is recorded separately. No secret candidates are copied into reports. Credentials remain execution-only; collector ephemeral file removed.

V5present:NO. V6present:NO. PostgresAuditTamperObserver/observer.py/newPhase2API-worker validators:NO. Phase2started:NO. All20 tracked modified test paths belong to previously prepared Phase1 plus this health correction. Complete tracked/untracked/staged inventory and SHA-256 for every current changed/source/evidence file are provided by FINAL_BINDING.json and final integrity artifacts; FINAL_BINDING itself is hashed separately at delivery. No Git mutation occurred.

## Physical database identity map

Every row is derived from the current authorized run's allocator and realpg_control_system. A durable restart may have multiple observations/incarnations but only one physical identifier. Collectorordinal41 is excluded because no physical initialization occurred.

|Logical name|UUIDv5 database_instance_id|Physical system_identifier|Database|
|---|---|---|---|
|postgres-authority/0|d6faa7ec-12bc-538c-a616-b6de9ed6e61d|7691726175028977702|vra_health_test|
|postgres-authority/1|30e234c3-8420-52a6-8f7d-06f0b61c51e1|7691726391186444326|vra_poc01|
|postgres-authority/2|5d90e9c9-19e2-5a3a-8832-2eb3a5fba4a8|7691726404284940326|vra_poc01|
|postgres-authority/3|e845c259-3719-5c07-86c0-721382c3a445|7691726416174354470|vra_poc01|
|postgres-authority/4|e1e55029-295b-587a-a73d-9c35dfd31d24|7691726432352817190|vra_poc01|
|postgres-authority/5|acfb715d-a37c-5539-8cff-5300e7a5ec52|7691726445507829798|vra_poc01|
|postgres-authority/6|53609d92-a4c9-5267-b9aa-78dbd5eac98b|7691726458051207206|vra_poc01|
|postgres-authority/7|cf7032e7-4c3f-515b-917b-2504815172d5|7691726471015505958|vra_poc01|
|postgres-authority/8|e4d0e0ea-ef4f-5c10-aff7-676f4263179a|7691726483167973414|vra_poc01|
|postgres-authority/9|fb0ef878-47a0-5e02-9286-6669cfc03467|7691726495588442150|vra_poc01|
|postgres-authority/10|89c89d14-c622-55be-b914-075782b33247|7691726548700438566|vra_poc01|
|postgres-authority/11|404beb83-abdc-5268-af5a-c64fd6047fee|7691726560563855398|vra_poc01|
|postgres-authority/12|d09fb45d-dc8f-5f03-88b7-ccb1e71e6ef4|7691726574002602022|vra_poc01|
|postgres-authority/13|2210cd47-1f70-56ca-95ca-fa2f390de912|7691726586218872870|vra_poc01|
|postgres-authority/14|0c5c74cd-211c-5bd4-aeca-c647a535cb5a|7691726598466371623|vra_poc01|
|postgres-authority/15|3c15e2c2-62e2-5971-a98f-9aa9fa54d540|7691726610672177190|vra_poc01|
|postgres-authority/16|5f537e81-0cb6-5484-9338-f294f9417e92|7691726642693513254|vra_poc01|
|postgres-authority/17|fcd23c06-00b8-540e-9722-8ae51ccc4277|7691726734172626982|vra_poc01|
|postgres-authority/18|58be17e2-1606-5c22-a24d-c487ab4acf1c|7691726767921696806|vra_poc01|
|postgres-authority/19|8fd0fb8a-5e50-5a6b-bc86-10cbb4e4d649|7691726860775039014|vra_poc01|
|postgres-authority/20|5446b132-3773-517b-8b8d-cd36e41c4bab|7691726891723845670|vra_poc01|
|postgres-authority/21|9c3bd391-750f-5774-80f2-cbe1b62b7f02|7691726919705808934|vra_poc01|
|postgres-authority/22|1e7d6064-b313-5e82-a27d-45f2c94fb721|7691726958593753126|vra_poc01|
|postgres-authority/23|0af21bcd-013a-5117-8595-53c196aa9368|7691726973449547813|vra_poc01|
|postgres-authority/24|046e06dd-d8ef-54be-96a7-29cf77c7e534|7691726990789558310|vra_poc01|
|postgres-authority/25|1506dc00-ef81-5957-96c4-46c6dc425e55|7691727006976671782|vra_poc01|
|postgres-authority/26|8c782b02-b28a-5642-9adb-04cfe7e0c3fa|7691727020985020453|vra_poc01|
|postgres-authority/27|0ee0f406-4800-5cbf-bb67-c96f23517341|7691727042035519526|vra_poc01|
|postgres-authority/28|75072960-5fd3-5187-acbd-5b4fd9c515e5|7691727058185277478|vra_poc01|
|postgres-authority/29|5d69db6c-1834-5c18-b3c5-255302bf4631|7691727073101762598|vra_poc01|
|postgres-authority/30|b9a834a5-a886-5b70-8cc6-45cef3102f7e|7691727108002398246|vra_poc01|
|postgres-authority/31|7836ad22-6d35-580c-82e4-5a8f3060f285|7691727124660776998|vra_poc01|
|postgres-authority/32|1997b4f7-87a1-5b6c-b60c-b8d26441594c|7691727151591710758|vra_poc01|
|postgres-authority/33|2650ed69-9aef-50d8-9648-0a056101118a|7691727335893127206|vra_idempotent_application_test|
|postgres-authority/34|195250d0-2809-5c8c-94cf-3d4904100a58|7691727345690812454|vra_concurrency_test|
|postgres-authority/35|0eeee171-54c5-57ff-9938-4d21e7f22088|7691727356419588134|vra_http_test|
|postgres-authority/36|ea0c44bc-d39a-5622-850a-b37bc024deb3|7691727367268081702|vra_idempotency_test|
|postgres-authority/37|5fdc26b4-1b79-5342-bbeb-a4a98a58e10f|7691727376471920678|vra_runtime_test|
|postgres-authority/38|f8b65f8e-2ba1-569e-a1df-05eecdbbd0f7|7691727386662166565|vra_health_test|
|postgres-authority/39|3b0a2a9e-624c-55b0-a3f7-5a82e0e125f6|7691727552069296165|vra_poc01|
|postgres-authority/40|aad715de-1b80-5118-840f-0884946e531e|7691727654498291750|vra_poc01|
|postgres-simulator/0|3b301561-4a7f-58ec-8fb9-aa1627f2787c|7691727080365383718|simulator_stage_g|
|postgres-simulator/1|9ea718a0-78f6-5add-82b9-d260a25fcb06|7691727131887824933|simulator_stage_f|
|postgres-simulator/2|39649976-1403-5194-a55d-4e2fda3581b1|7691727159176855590|simulator_stage_i|

The only unresolved execution blocker is the unremediated collector harness Path/string defect and therefore the unexecuted collector OS/mount/SQL proof. Stop for narrow reviewed remediation; Phase1 remains open and not ready for re-review.
