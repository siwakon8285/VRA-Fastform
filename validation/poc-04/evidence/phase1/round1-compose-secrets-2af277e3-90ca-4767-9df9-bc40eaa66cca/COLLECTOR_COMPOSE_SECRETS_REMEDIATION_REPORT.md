# POC-04 Phase-1 normalized Compose secrets remediation

PHASE 1 CLOSED: NO

READY FOR INDEPENDENT PHASE-1 RE-REVIEW: YES

Phase 2: NOT AUTHORIZED / NOT STARTED

POC-04 / S0-S15: NOT VERIFIED

Run UUIDv4: `2af277e3-90ca-4767-9df9-bc40eaa66cca`. These are implementation-side executed results pending independent re-review. The original independent review remains FAIL; this report does not accept or close Phase 1.

## Historical preservation and scope

The previous Path-remediation report remains SHA-256 `669f2e1d4bd339c51e90fb04d08237b2ae954eb7da67f8de5072ae95cd8cc833` and its failed FINAL_BINDING remains `8b9f3c5c18add17d94730acd08c08f10e992b6f07c47f46deda9c53e7e04f43a`. The earlier implementation report remains `82c87d88ff10985c5c18015768a7afc0442813608b7fbb616aa2ba405f249caf`. All 713 pre-existing source/evidence paths other than the one explicitly authorized collector script match their frozen pre-execution hashes. Earlier image, PGDATA, Ryuk, IPAM, locale, restart, health, Path and normalized-secrets failures remain unchanged. No prior FAIL has been rewritten as PASS.

Only `validation/poc-04/scripts/collector-boundary-proof.py` changed in this remediation. Prior script SHA-256 `ca0139d63b19cc29a2392aeb99e6ca4c9e9998fb486f63c7f58d1f2a5b59f288`; final script `dfe90a39be85b0c38b3bfe8c8b41c07c5a7c5aaa85b8321dd87f80f8805f291e`. `collector-parser-only.patch` and `narrow-change-review.json` bind the exact correction. V4, role bootstrap, Compose/profile inputs, health test, production code and dependencies were unchanged in this remediation.

## Pre-edit diagnosis and corrected semantics

Pre-edit diagnosis is preserved in `../compose-secrets-diagnosis-2021b28f-c9cc-4fbe-b527-535a078cdae4/normalized-secrets-model.json` (SHA-256 `4dc51dbe9a9efb8802874922edcbc0f2ee42dae647fc43347a0410d11937a98a`). The same config command shape as the proof was executed using a non-authentication structural-only placeholder outside the repository; it created no Docker resources. Compose v5.5.1 emitted a list with one object, keys source/target, source `postgres_admin_password`, and absolute target `/run/secrets/postgres_admin_password`. The top-level sole secret had file/name keys and exactly matched the ephemeral file input. No credential contents or environment values were published.

This is CASE A: normalized source/target exactly matches the reviewed profile. Docker documents short-secret mounts under `/run/secrets/<source>`, long target as a filename or absolute path, and omitted target defaulting to source: [official services reference](https://docs.docker.com/reference/compose-file/services/#secrets). The parser requires exactly one object and exact source; only omitted target, the exact default filename, or the exact approved absolute destination are accepted. Null, empty, other paths, traversal, unknown keys and shapes fail closed. It returns the one full constant destination; no basename-only or arbitrary path normalization is used.

Top-level secrets must be exactly the selected file-backed secret with approved structural fields. The sole service remains postgres. Server environment, external file, actual read-only bind, mount direction, no socket, reader authority and source/config binding checks remain strict. The corrected proof captures validated structural metadata, never secret content. Three accepted parser forms and 24 negative cases passed. `executed-parser-cases.py.txt` and `executed-run-coordinator.py` preserve the actual executed inputs/code; `parser-focused-cases.json` records outcomes. These parser checks are procedural cases, not JUnit test counts.

A temporary coordinator initially reused the preceding attempt directory prefix. The recorder stopped before any test/resource creation; `recorder-coordinator-setup-failure.json` preserves that exit 1. Only external coordinator environment paths were corrected. No source was changed for this setup error. `recorder-resume.json` records resumption after the turn interruption; physical captures were already complete for the full build. Observations never authorize teardown.

## Exact execution sequence and counts

All command records preserve exact argv, exit status, safe environment, source digest and support-JAR hash. Working directory is backend unless the command itself selects the repository path.

| Execution | Exit | Raw evidence |
|---|---:|---|
| Focused `python3 -B validation/poc-04/scripts/collector-boundary-proof.py` |0|focused-collector-command.json, collector-boundary/*|
| `./gradlew --no-daemon --init-script ../validation/poc-04/test-support/runner.init.gradle check build --rerun-tasks` |0|full-check-build-command.json, full-check-build.log, full-check-build-results/*|
| Pinned JDK PhaseOneEvidenceCapture.java, both fresh/populated |0|database-proof-command.json, database-proof.json|
| `python3 -B validation/poc-04/scripts/teardown-negative-proof.py` |0|final-teardown-negative-command.json, teardown-negatives.json|
| Final `python3 -B validation/poc-04/scripts/collector-boundary-proof.py` |0|final-collector-command.json, final-collector/collector-boundary/*|
| Current-run ledger teardown by each exact kind/ID; finish |0 each|final-exact-ledger-cleanup.json, final-identity-consistency.json|

The focused collector ran before the new full build as explicitly ordered. Its focused-only prerequisite transparently cites the previous green runtime XML on identical backend bytes, not a same-current-run suite result. `focused-collector-prerequisite.json` records that distinction. Final collector ran after this run's full build, backed by `final-collector-prerequisite.json`; earlier results were never used as final execution substitutes.

| Authoritative single full build | Tests | Skipped | Failures | Errors |
|---|---:|---:|---:|---:|
| migration:test |4|0|0|0|
| migration:integrationTest |20|0|0|0|
| runtime:test |56|0|0|0|
| runtime:integrationTest |113|0|0|0|
| Total |193|0|0|0|

All 32 copied raw XML files reconcile to 193 actual testcase nodes with no skip/failure/error nodes. The 20 migration integration cases include the two Round1 additions. Procedural collector, parser, adapter and teardown executions are separate. Historical 191/304/102 counts remain historical; no count is substituted for this single build. The accepted health test returns actual 503/DOWN readiness and 200/UP liveness after outage; no timeout is accepted as DOWN.

## Corrected-byte database/security and four original review blockers

Fresh V1→V4 and populated V3→V4 passed again in the full build and raw adapter. Fresh applied four migrations; populated applied three then one. Both validate; same-DB reruns apply zero and retain catalog/history/all 27 table digests. V4 is applied once with checksum -1759282690. The ten populated prior tables, including V2 transient/success/rejection and POC03 state, are nonempty and their before/after/final row digests/counts, catalog and prior Flyway history remain identical. V1–V3 Flyway checksums remain -290113401 / -1049090241 / -70525330. Each database records 71 actual rollback/unchanged-state denials: {'42501': 58, '23514': 9, '23505': 3, '23503': 1}; the populated count is identical. See `database-proof.json` for every identity, catalog, statement, SQLSTATE and stage.

The six new roles have these actual catalog attributes; the five administrative columns mean SUPERUSER/CREATEDB/CREATEROLE/REPLICATION/BYPASSRLS:

| Role | LOGIN | INHERIT | Administrative columns |
|---|---|---|---|
|vra_sync_executor|false|false|all false|
|vra_security_executor|false|false|all false|
|vra_telemetry_executor|false|false|all false|
|vra_factor_fixture|true|false|all false|
|vra_audit_evidence_reader|true|false|all false|
|vra_security_telemetry_observer|true|false|all false|

Direct memberships are migrator→owner and owner→async/sync/security/telemetry executors, all SET=true / INHERIT=false / ADMIN=false. The complete transitive graph has nine pairs: migrator→owner plus all four executors; owner→all four executors. Ordinary workloads/helpers have none. Executor CREATE denials use the accepted administrative SET path, while ordinary denials use actual LOGIN credentials. Trusted-schema CREATE holders are only postgres and vra_owner. PUBLIC schema/table/sequence projections are empty. All 17 foundation tables are owned by vra_owner; constraint/index/grant verification passed against the approved Phase1 model.

Runtime V4 boundaries remain SELECT on permitted authority/session/factor/staff/proposal tables; UPDATE(label,version) on POC records; no protected-audit SELECT/DML; only approved APP-event insert/safe select columns; exact webhook receipt/target rights. Worker/reconciler new V4 grants are none. V2 runtime INSERT/UPDATE residual remains unchanged. No Phase2 capability or capability-function denial proof is claimed.

1. **Teardown authorization:** exact creator registration in the current locked ledger plus exact kind/ID/current-run/source/environment/database binding is required; daemon-wide observations confer no authority. Every final cleanup operation used that ledger and checked live identity where present.
2. **Teardown negatives:** current authorized resources removed while the other-run sentinel survived; wrong-ID and wrong/missing-label attempts denied before Docker deletion and snapshots remained unchanged. Negative fixtures/sentinel were later removed only by independently checked separate admin exact-ID cleanup. Sixteen independent allocator processes had unique ordinals and exact UUIDv5 derivations.
3. **Database identity reuse:** every fresh initialization advances the shared cross-process logical ordinal. Two fresh databases in the full test have distinct volumes, logical names, UUIDv5s and physical systems. Durable restart retains exact volume/UUID/system/sentinel while reacquiring current JDBC transport; changed ephemeral port is allowed. `database-fresh-restart-final.json` is extracted from raw XML. Final run consistency proves 45 initialized UUIDv5s map one-to-one to 45 systems; unused allocations are not reported as initialized databases.
4. **Nonblank reason:** corrected V4 regex is unchanged in this remediation. PostgreSQL 17.11 behavioral tests deny 14 whitespace cases with 23514 and accept four nonblank cases with 00000, with rollback/unchanged-state proof. Actual catalog locale is libc c / en_US.utf8 collate/ctype / null datlocale / UTF8. `reason-behavior-final.json` and raw XML preserve both exact CHECKs and all cases. Locale metadata is context, behavioral execution is the proof.

## Collector boundary, image and isolation

Both collector proofs passed every mandatory assertion, including exact normalized secret source/target, server-only admin file, actual daemon mounts, collector ownership/modes/settings, no published ports, and source/config/run labels. Reserved UID/GID 9404 reads only the exact ro/nocopy collector mount and cannot write. Synthetic nonmember 9405 cannot read/write; no application deployment UID is invented. The prepared profile gives runtime/workers no collector mount. The temporary probe receives no DB credentials, PGDATA or Docker socket and has network none, read-only root, ALL capabilities dropped and no-new-privileges. No observer implementation or Phase2 telemetry function is introduced.

Nine actual LOGIN identities authenticate and each deny server-file read, log-directory read and logging-setting mutation with 42501 (27 denials per collector proof). Catalog pg_read_server_files / pg_monitor / log_statement SET privileges are false. Wrong-password authentication independently fails. Business/protected-audit grant proof belongs to the same-run migrated databases in `database-proof.json`; collector-only databases are not misrepresented as migrated. All ephemeral secret inputs are outside the repository, mode0600 under0700 directories, supplied at runtime, absent after cleanup and never emitted into evidence.

Dedicated endpoint: `unix:///Users/siwakornbundi/.colima/vra-poc04-test/docker.sock`; daemon `14c8ff26-86d1-4c0b-88a6-560be91a65ea` / colima-vra-poc04-test / Docker29.5.2 linux/aarch64. Host override127.0.0.1 and daemon socket override/var/run/docker.sock remain; Ryuk is enabled. Saved profile hash `53675defaacbcb1a36f59a3760a61142b16199d4e8a513e6ac63e44fd9505d68`, pool10.241.0.0/16 with /24 children unchanged; no daemon configuration changes. Docker Desktop and persistent vra-poc00 were not accessed.

PostgreSQL exact runtime 17.11 / 170011 independently verified. Provenance distinguishes immutable OCI index d74eeac9a635390a49bc21bd49fccd973de707e2a53a76ac49b552b8712ec46f, arm64/v8 manifest86fa57b44a1d38f09970f64ac71eb492f7a9cf94d08b85e1c8f07bb0fc0a1761 and its config97432f980da100ebd3e419711efee84e1e97a966d62c035286a07f239ddb4d9c. Exact manifest bytes/config linkage, daemon selection, runtime version and run-owned PGDATA volume are separate proofs. Pins were not changed and unsupported docker-java raw Type was not reinstated.

## Cleanup and integrity

All 144 creator-ledger resources are absent: 49 containers / 49 volume names / 46 network IDs. All 52 observed exact container IDs are absent, including three native Ryuk helpers never treated as observed deletion authority. Final daemon has 0 containers, 0 volumes, only the original bridge/host/none IDs. No blanket prune or unrelated deletion occurred. Full UUID/system/volume map and exact creator IDs/labels are in the final run ledger and identity consistency artifacts. The recorder was stopped before final binding.

Git branch `poc/04-security-auth`; HEAD `01256d96d85823740a8d6c3a3f99346afbdbef56`; tree `9690fa4f994b0dda3aefbb0db42535e47d04a10a`. Nothing staged. All272 source files retain one content digest `9b4dd09c08ed544173d8d31e64caae2a6a8d3e58b17b948aedc6a1e2dedf0494` across focused collector, full build, adapter, final collector and final checks. `source-binding-final.json`, final integrity and FINAL_BINDING inventory every modified/untracked file and SHA-256; the binding excludes its own self hash to avoid circularity. No Git mutation was performed.

| Migration | SHA-256 | Baseline comparison |
|---|---|---|
|V1|d804cbe5153b9a904ae0747af55c8295079d4a0c3edb4a38145ab5199b5b59c4|exact Phase0 equality|
|V2|7bef750b59b7b77e8402f4f42c13c21c4e072092504b9f268bace9b72de8d2bb|exact Phase0 equality|
|V3|9edbd15ffe08ee4edafa3f549856ee38a782485c54db9c73e69d4263e5ca26b4|exact Phase0 equality|
|corrected V4|7e2a1ea77ba92a451763c7e06ab7dc4cee6e06f761bbbb4e6cb1c786c8a21ffc|unchanged by this narrow correction|
|role bootstrap|976cf704687e5bf005de8943c706fdd996d801172c112d640c993e0b193407b5|unchanged|
|Compose profile|db3c7dec5fa8b1f6e04512e30a4d6dc9b58e11316c53d3301b82a0712db62c47|unchanged|

Controlling spec/plan/ADR004/005/006 bytes remain exact. Legacy JdbcReservationIdempotencyRepository exists in the accepted baseline and remains byte-identical. V5/V6/PostgresAuditTamperObserver/observer.py absent; no Phase2 API/worker capability changes. No grant widening or new product scope.

`git diff --check`: exit0. JSON/JSONL/YAML/Python parsing passed. Authored source/support whitespace/final-newline issues:0; preserved raw evidence formatting findings are enumerated without altering raw artifacts. Gitleaks8.30.1 returns1 for16 matches, all verified Git/file/source SHA metadata false positives;0 actual secrets/unresolved candidates. The initial classification run's FAIL remains `secret-scan.json`; read-only resolution verifies its historical metadata value equals the exact frozen artifact SHA in `secret-scan-classification-resolution.json`. Subsequent scans found only verified SHA matches. Scanner rules were not suppressed; no source/evidence was changed to hide a candidate.

Unresolved Phase1 execution blockers: none. Independent re-review and user-controlled Git closure remain outstanding. No Phase1 closure, POC04 verification or S0–S15 gate upgrade is claimed.

## Initialized database instance map

Each row records logical name → run-scoped UUIDv5 → physical PostgreSQL system identifier → exact durable volume. Restart evidence may have more than one container incarnation for the same row; no row maps to multiple systems.

|Logical name|UUIDv5|Physical system identifier|Durable volume|
|---|---|---|---|
|postgres-authority/0|0cc21592-40d2-549d-b0ab-fe821e9ef5d7|7691742854606184487|vra-poc04-2af277e3-90ca-4767-9df9-bc40eaa66cca-collector-0_postgres_data|
|postgres-authority/1|5b354c51-1b61-5f8e-9d92-630c19485500|7691743000585506854|vra-poc04-2af277e3-90ca-4767-9df9-bc40eaa66cca-postgres-authority-1-pgdata|
|postgres-authority/2|345f4a24-94c0-5aa0-83f2-df0e678a3db0|7691743013849128998|vra-poc04-2af277e3-90ca-4767-9df9-bc40eaa66cca-postgres-authority-2-pgdata|
|postgres-authority/3|886d8026-f07f-59bf-a2db-bc7c9a9765b8|7691743025805549605|vra-poc04-2af277e3-90ca-4767-9df9-bc40eaa66cca-postgres-authority-3-pgdata|
|postgres-authority/4|32bffdac-b366-5100-a4f6-eaf3526a1c75|7691743042021064741|vra-poc04-2af277e3-90ca-4767-9df9-bc40eaa66cca-postgres-authority-4-pgdata|
|postgres-authority/5|427b3384-92a2-5904-8d8b-e6e9741e3a16|7691743055278084134|vra-poc04-2af277e3-90ca-4767-9df9-bc40eaa66cca-postgres-authority-5-pgdata|
|postgres-authority/6|09b42cd8-49e5-58b0-9917-de5879bd2e30|7691743067791814693|vra-poc04-2af277e3-90ca-4767-9df9-bc40eaa66cca-postgres-authority-6-pgdata|
|postgres-authority/7|b03a2ba9-7929-5fce-b2f8-25bc86e0a3b4|7691743080746954790|vra-poc04-2af277e3-90ca-4767-9df9-bc40eaa66cca-postgres-authority-7-pgdata|
|postgres-authority/8|d7e53c1d-8f39-54fa-94da-3dff9759a90d|7691743093316702246|vra-poc04-2af277e3-90ca-4767-9df9-bc40eaa66cca-postgres-authority-8-pgdata|
|postgres-authority/9|161574f2-d234-5ee4-b748-778594472026|7691743105677221926|vra-poc04-2af277e3-90ca-4767-9df9-bc40eaa66cca-postgres-authority-9-pgdata|
|postgres-authority/10|ebe8d73e-9254-56b6-a34c-45ae544e03e4|7691743158799089701|vra-poc04-2af277e3-90ca-4767-9df9-bc40eaa66cca-postgres-authority-10-pgdata|
|postgres-authority/11|20ab3d0d-342e-5384-91d6-d12a7e6f0e9e|7691743171106316325|vra-poc04-2af277e3-90ca-4767-9df9-bc40eaa66cca-postgres-authority-11-pgdata|
|postgres-authority/12|1195f486-c4f7-5e42-bfab-b061b08f411f|7691743184360542246|vra-poc04-2af277e3-90ca-4767-9df9-bc40eaa66cca-postgres-authority-12-pgdata|
|postgres-authority/13|0079a3fb-40b1-575e-9d4c-db64a7ea8301|7691743196798816294|vra-poc04-2af277e3-90ca-4767-9df9-bc40eaa66cca-postgres-authority-13-pgdata|
|postgres-authority/14|d84ef4d5-ab98-53f0-b0b0-eb8ef85f24ce|7691743209081421862|vra-poc04-2af277e3-90ca-4767-9df9-bc40eaa66cca-postgres-authority-14-pgdata|
|postgres-authority/15|81ad781c-77b9-57f9-8432-8d3725fc5949|7691743221326200870|vra-poc04-2af277e3-90ca-4767-9df9-bc40eaa66cca-postgres-authority-15-pgdata|
|postgres-authority/16|58e5ea3d-85e1-5373-8ef2-d6bcf549dfc7|7691743253446328358|vra-poc04-2af277e3-90ca-4767-9df9-bc40eaa66cca-postgres-authority-16-pgdata|
|postgres-authority/17|5f1ff5da-f97e-5e86-954b-78e129f9a41c|7691743343865966629|vra-poc04-2af277e3-90ca-4767-9df9-bc40eaa66cca-postgres-authority-17-pgdata|
|postgres-authority/18|9e0828e8-bdbe-5c1b-a832-71c8a1310143|7691743376825012261|vra-poc04-2af277e3-90ca-4767-9df9-bc40eaa66cca-postgres-authority-18-pgdata|
|postgres-authority/19|147402b0-a816-5058-9633-bb52ada35036|7691743464580792358|vra-poc04-2af277e3-90ca-4767-9df9-bc40eaa66cca-postgres-authority-19-pgdata|
|postgres-authority/20|d1db516d-ff0a-5a81-8d03-1c3bbc887401|7691743495228764198|vra-poc04-2af277e3-90ca-4767-9df9-bc40eaa66cca-postgres-authority-20-pgdata|
|postgres-authority/21|f5a770b5-c46b-5771-8794-74a9e5b05d66|7691743522383568934|vra-poc04-2af277e3-90ca-4767-9df9-bc40eaa66cca-postgres-authority-21-pgdata|
|postgres-authority/22|cfb848b7-eda8-5fc3-90af-479e49718455|7691743561078587429|vra-poc04-2af277e3-90ca-4767-9df9-bc40eaa66cca-postgres-authority-22-pgdata|
|postgres-authority/23|4f5cb96e-98bf-5cb5-bf7d-3e24e5009f7f|7691743575805349926|vra-poc04-2af277e3-90ca-4767-9df9-bc40eaa66cca-postgres-authority-23-pgdata|
|postgres-authority/24|ba75a75c-f829-5ca9-a627-adc6cdda7fe7|7691743593432629286|vra-poc04-2af277e3-90ca-4767-9df9-bc40eaa66cca-postgres-authority-24-pgdata|
|postgres-authority/25|4e78ad8a-ca84-5f50-a656-a34280f6dd7d|7691743610290589734|vra-poc04-2af277e3-90ca-4767-9df9-bc40eaa66cca-postgres-authority-25-pgdata|
|postgres-authority/26|32bd66c7-c24b-5a79-9b75-ae625eade701|7691743624138342438|vra-poc04-2af277e3-90ca-4767-9df9-bc40eaa66cca-postgres-authority-26-pgdata|
|postgres-authority/27|bc3b56d3-800e-5aa2-81ac-f1e3a5bea1d3|7691743645469020198|vra-poc04-2af277e3-90ca-4767-9df9-bc40eaa66cca-postgres-authority-27-pgdata|
|postgres-authority/28|78e80466-304f-5f68-ad1c-5c11d010af6e|7691743661812351013|vra-poc04-2af277e3-90ca-4767-9df9-bc40eaa66cca-postgres-authority-28-pgdata|
|postgres-authority/29|4a20e323-d831-563e-bfaa-46411d832a56|7691743676705296422|vra-poc04-2af277e3-90ca-4767-9df9-bc40eaa66cca-postgres-authority-29-pgdata|
|postgres-authority/30|531efb78-33ed-5f55-977d-216098b0ffaa|7691743711664914470|vra-poc04-2af277e3-90ca-4767-9df9-bc40eaa66cca-postgres-authority-30-pgdata|
|postgres-authority/31|4f57a9ee-b7d5-5331-a5f2-e557179ea527|7691743728339972134|vra-poc04-2af277e3-90ca-4767-9df9-bc40eaa66cca-postgres-authority-31-pgdata|
|postgres-authority/32|0960d78d-8ea6-5c72-be20-e9a895c1c8c4|7691743755659096102|vra-poc04-2af277e3-90ca-4767-9df9-bc40eaa66cca-postgres-authority-32-pgdata|
|postgres-authority/33|41a0ab97-f376-516c-9483-48fd1f629cd4|7691743940189007910|vra-poc04-2af277e3-90ca-4767-9df9-bc40eaa66cca-postgres-authority-33-pgdata|
|postgres-authority/34|5e73be6c-a9fa-5649-b78a-c30e64a63ccb|7691743950031978534|vra-poc04-2af277e3-90ca-4767-9df9-bc40eaa66cca-postgres-authority-34-pgdata|
|postgres-authority/35|9a9ed94c-86d9-5ec8-ba2a-517aee67c82b|7691743960711389222|vra-poc04-2af277e3-90ca-4767-9df9-bc40eaa66cca-postgres-authority-35-pgdata|
|postgres-authority/36|45cc5d6f-0e37-5350-9cca-9bec995de8c4|7691743971750477861|vra-poc04-2af277e3-90ca-4767-9df9-bc40eaa66cca-postgres-authority-36-pgdata|
|postgres-authority/37|4120218d-65b6-58d5-a464-78651e5e2ab4|7691743981522952230|vra-poc04-2af277e3-90ca-4767-9df9-bc40eaa66cca-postgres-authority-37-pgdata|
|postgres-authority/38|3f7e9bce-4590-587e-b4e6-a8bff4c9a241|7691743991437758502|vra-poc04-2af277e3-90ca-4767-9df9-bc40eaa66cca-postgres-authority-38-pgdata|
|postgres-authority/39|97749314-2296-5b8c-a464-7cc17260b390|7691747279499812902|vra-poc04-2af277e3-90ca-4767-9df9-bc40eaa66cca-postgres-authority-39-pgdata|
|postgres-authority/40|785b2a22-e210-5f79-840d-f123060f2a5e|7691747379845083174|vra-poc04-2af277e3-90ca-4767-9df9-bc40eaa66cca-postgres-authority-40-pgdata|
|postgres-authority/41|6cd5cc9f-1aff-5cd1-8c54-0a4b7444fb98|7691747681127866407|vra-poc04-2af277e3-90ca-4767-9df9-bc40eaa66cca-collector-41_postgres_data|
|postgres-simulator/0|38b591c3-b170-528c-894d-25a338ff929c|7691743684054171686|vra-poc04-2af277e3-90ca-4767-9df9-bc40eaa66cca-postgres-simulator-0-pgdata|
|postgres-simulator/1|76e9397c-56de-5365-b723-b912496fa23c|7691743735714664486|vra-poc04-2af277e3-90ca-4767-9df9-bc40eaa66cca-postgres-simulator-1-pgdata|
|postgres-simulator/2|13c77fd8-7200-5445-837e-db2ac816b8fb|7691743763193151525|vra-poc04-2af277e3-90ca-4767-9df9-bc40eaa66cca-postgres-simulator-2-pgdata|
