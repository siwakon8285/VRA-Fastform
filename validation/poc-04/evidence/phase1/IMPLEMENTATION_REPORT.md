# POC-04 Phase-1 implementation evidence

PHASE 1 CLOSED: NO

READY FOR INDEPENDENT PHASE-1 REVIEW: NO

The roles/schema candidate is implemented. Required real PostgreSQL proof is
blocked, so this is an incomplete checkpoint. No PostgreSQL container or database
was started. No effective database property is inferred from SQL declarations or
unit results. Stop after Phase 1; independent Phase-1 review remains pending.

## 1. Starting branch, HEAD and authority

Starting branch: `poc/04-security-auth`.

Starting HEAD, `origin/main`, and `origin/poc/04-security-auth` each equalled
`01256d96d85823740a8d6c3a3f99346afbdbef56`. Initial worktree and index were clean.
All four identities remain unchanged. No Git mutation was executed.

Approved plan SHA-256:
`c169ad62d947841ec08987d2561f16b75935168d99ea46a34143004d3da3d7c5`.
Frozen spec SHA-256:
`c035e627c087b41cb4533b00f0bdaf8fa208a5b55881358cf1f85b6809b2ed2d`.
Accepted ADR-004/005/006 are byte-identical to HEAD, with hashes respectively:

- `860d14431ed905e18aa67a60eb16fe44660a4fe28f056d47cd57eb13de642899`
- `dd5933ca1adc930a5b8617b6f5b49f748081f7f9770dea87b761b3e937319f49`
- `3068fa468b1483788a3a59cab3b628b6b8a2cc0beee101a93e0111b55e18f892`

The initial Phase-0 source binding matched all 254 recorded entries, including
size/mode; canonical content digest was
`609138eb48916e58261dfa73afe419960db6286998d4aec3f4701d69dc28e058`.
Phase-0 baseline, manifests, closure evidence, plan, spec and ADRs are unchanged.
Their historical evidence was not upgraded to describe Phase 1.

Current candidate canonical source-content SHA-256 is `e0248b71801aca92245605d110f65e31c48985f92e384c7a4cfde05943abf31d`
(261 source files; generated evidence excluded by the historical encoding).
This includes both new untracked backend files and all selected current source;
it is a dirty candidate binding, not a clean-source or executed-run claim.

## 2–3. Every changed path and SHA-256

There are 27 implementation/support files below, plus this newly created report.
This report's own SHA-256 is supplied in the accompanying final response because
a file cannot embed its own final hash. Final Git state is 20 modified tracked
files, 8 untracked files, and no staged files.

| Path | State | SHA-256 |
| --- | --- | --- |
| `backend/migration/src/main/resources/db/migration/V4__poc04_identity_security_foundation.sql` | created untracked | `45161b8c54f10778a5521cdd23dc0945fadb65364bd8094e3d19d8f79cfdd1e8` |
| `backend/migration/src/test/java/dev/vra/migration/MigrationSecurityIntegrationTest.java` | modified tracked | `feb7a8f223c4e90a70123f67aa001b8a9ac152a9532ec6b3009f37370a92119a` |
| `backend/migration/src/test/java/dev/vra/migration/OutboxMigrationSecurityIntegrationTest.java` | modified tracked | `664156057d8b71b9f8702ddead09344060c9cce43fd1021cf780dd430953253a` |
| `backend/migration/src/test/java/dev/vra/migration/Poc04FoundationMigrationIntegrationTest.java` | created untracked | `125e9cf33055428f4354fe7c0637ba60e2f3857eb3a266c7fa089db7f6c71005` |
| `backend/runtime/src/test/java/dev/vra/async/AsyncPermissionIntegrationTest.java` | modified tracked | `4ad16fe9dffbf2e2f6b3941bd8645f3094f108e5296807e55ee8ef0c63e380e4` |
| `backend/runtime/src/test/java/dev/vra/async/AsyncRoleBootstrap.java` | modified tracked | `80aaf4d4c7fb02878b55dfdb2ff51ed2c6ff36d631d512d1603d702dc0424a20` |
| `backend/runtime/src/test/java/dev/vra/async/AsyncVisibilityIntegrationTest.java` | modified tracked | `31e172e295b204e2070f984517bcba27b2f9a098174b6e64c3cfaf1c2d84c506` |
| `backend/runtime/src/test/java/dev/vra/async/DeliveryClaimIntegrationTest.java` | modified tracked | `cc11f8469f940c3d1a97bfeab78e6e4f27e36a959b4bf248dca42f24f0e91c99` |
| `backend/runtime/src/test/java/dev/vra/async/DeliveryStateIntegrationTest.java` | modified tracked | `07246761e5be4677125fbb09d1e11d86db546f1c64a37e5a5c2b7f992b211aa8` |
| `backend/runtime/src/test/java/dev/vra/async/ProducerOutboxIntegrationTest.java` | modified tracked | `88f6301a210bb00fdd21ea138f71e438ce08d5c34fab1c0143f3311450c0fcec` |
| `backend/runtime/src/test/java/dev/vra/async/ProjectionInboxIntegrationTest.java` | modified tracked | `d615be8fc0eadfc28b35e7061d42432f8fff6c1ee9b98533b9a8c19a73d2e727` |
| `backend/runtime/src/test/java/dev/vra/async/ProjectionRebuildIntegrationTest.java` | modified tracked | `21038aafd0ebd855e812e9333e1df760d318ff5c9d0bfab6676e685f55d007c0` |
| `backend/runtime/src/test/java/dev/vra/async/ReconciliationIntegrationTest.java` | modified tracked | `6c22d1094f1a976794c7a8b77dfffbfe17551d81eeb2de5b1531b5d0d24a8ea2` |
| `backend/runtime/src/test/java/dev/vra/async/RetryReplayIntegrationTest.java` | modified tracked | `f72f451ef737750bcc9e4777fd1ecb3c898d2acfd548289be90379c621a44682` |
| `backend/runtime/src/test/java/dev/vra/async/ValidationSimulatorIntegrationTest.java` | modified tracked | `b17af32f9b8ffb7cbc225fd57cf0cad65a6707e3794840cc51b66b0e6f7d265a` |
| `backend/runtime/src/test/java/dev/vra/async/WorkerProcessCrashIntegrationTest.java` | modified tracked | `d1f1f77ec890b26dcf39e7958dca274b5a378cf899b99164d8c9ff7449fc664f` |
| `backend/runtime/src/test/java/dev/vra/inventory/IdempotentReservationApplicationServiceIntegrationTest.java` | modified tracked | `52d0e29beb42a4c371bbe1c23aeaeda56f4e7a251c2585d20134ed51f887ee1c` |
| `backend/runtime/src/test/java/dev/vra/inventory/ReservationConcurrencyIntegrationTest.java` | modified tracked | `819a2a3d5537c40c434c1c175928bb83b0d6a05ca51aa306f4e0b05498304ed4` |
| `backend/runtime/src/test/java/dev/vra/inventory/ReservationHttpPostgresIntegrationTest.java` | modified tracked | `cd0b20f07c48162e76c388dd36bb7e429339aa0bb5b2de271e1696adb9d4f5e4` |
| `backend/runtime/src/test/java/dev/vra/inventory/ReservationIdempotencyRepositoryIntegrationTest.java` | modified tracked | `aeb577cc289476554252af3849263b09e246286bfeeea9cfd316745f7b07ef03` |
| `backend/runtime/src/test/java/dev/vra/inventory/ReservationTransactionIntegrationTest.java` | modified tracked | `5b0b4e0dfdb79875eb87edd1969602982c2d92b4558e18c8e43686260bb94b03` |
| `backend/runtime/src/test/java/dev/vra/platform/health/HealthProbePostgresIntegrationTest.java` | modified tracked | `283b9bb7655679ffa029c83837db025cd7b9425b5dbfdf82a37f731d78aeb45c` |
| `validation/poc-04/db/COLLECTOR_TEST.md` | created untracked | `09117a00f800c91748017234be2829a86828895ff26a119beb618327fddd50c1` |
| `validation/poc-04/db/bootstrap-security-roles.sql` | created untracked | `976cf704687e5bf005de8943c706fdd996d801172c112d640c993e0b193407b5` |
| `validation/poc-04/db/collector-test-entrypoint.sh` | created untracked | `90e6d263e9c121cb2b92dd80e45f49fef71452d5c6f52b1695752d1725fe73d0` |
| `validation/poc-04/db/collector-test.compose.yaml` | created untracked | `db3c7dec5fa8b1f6e04512e30a4d6dc9b58e11316c53d3301b82a0712db62c47` |
| `validation/poc-04/db/postgresql-collector-test.conf` | created untracked | `ba1f292521a67965fc3b8f03bf8d39cac460852cdb33f2d8e51d6809036440db` |
| `validation/poc-04/evidence/phase1/IMPLEMENTATION_REPORT.md` | created untracked | supplied with final response |

The 20 existing-test edits supply the prerequisite bootstrap and update only
latest-migration counts, role-graph expectations and descriptive version wording.
Existing business/async assertions remain. Production Java and build/workflow
files are unchanged.

## 4. V4 SHA-256

`45161b8c54f10778a5521cdd23dc0945fadb65364bd8094e3d19d8f79cfdd1e8`

## 5. Role bootstrap SHA-256

`976cf704687e5bf005de8943c706fdd996d801172c112d640c993e0b193407b5`

## 6. V1–V3 current hashes and Phase-0 comparison

| Migration | Current SHA-256 | Reviewed Phase-0 comparison |
| --- | --- | --- |
| V1 | `d804cbe5153b9a904ae0747af55c8295079d4a0c3edb4a38145ab5199b5b59c4` | exact match; HEAD bytes unchanged |
| V2 | `7bef750b59b7b77e8402f4f42c13c21c4e072092504b9f268bace9b72de8d2bb` | exact match; HEAD bytes unchanged |
| V3 | `9edbd15ffe08ee4edafa3f549856ee38a782485c54db9c73e69d4263e5ca26b4` | exact match; HEAD bytes unchanged |

## 7–11. Migration, validation, rerun and retained data

| Required proof | Result | Candidate assertion implemented |
| --- | --- | --- |
| 7. Fresh V1→V4 | NOT EXECUTED | 4 migrations, history 1/2/3/4, V4 once |
| 8. Populated V3→V4 | NOT EXECUTED | migrate to 3, seed prior state, apply V4 once |
| 9. Flyway validate | NOT EXECUTED | validation after fresh, upgrade and rerun |
| 10. Same-DB migration rerun | NOT EXECUTED | 0 migrations; rows/history/catalog unchanged |
| 11. Retained data/history | NOT EXECUTED | full prior rows and V1–V3 Flyway history/checksum comparison |

The populated fixture seeds inventory/reservation, V2 transient/success/rejection
idempotency rows, and POC-03 outbox event/delivery/history, inbox/projection and
reconciliation case/history. Sorted full-row JSON is hashed by PostgreSQL before
and after; complete prior Flyway rows and prior table/column ACLs are compared.
These assertions have compiled but have not executed. No Flyway repair, prior
migration edit, shared database cleanup or data drop was performed.

## 12. Six new roles

These are source declarations; actual catalog attributes remain NOT VERIFIED.

| Role | LOGIN | INHERIT | SUPERUSER | CREATEDB | CREATEROLE | REPLICATION | BYPASSRLS |
| --- | --- | --- | --- | --- | --- | --- | --- |
| vra_sync_executor | NO | NO | NO | NO | NO | NO | NO |
| vra_security_executor | NO | NO | NO | NO | NO | NO | NO |
| vra_telemetry_executor | NO | NO | NO | NO | NO | NO | NO |
| vra_factor_fixture | YES | NO | NO | NO | NO | NO | NO |
| vra_audit_evidence_reader | YES | NO | NO | NO | NO | NO | NO |
| vra_security_telemetry_observer | YES | NO | NO | NO | NO | NO | NO |

Bootstrap requires current administrator authority. Rerun applies exact restrictive
attributes and reports drift; unexpected memberships, ownership, object/database/
parameter ACLs and grant options fail transactionally without hidden revocation.
TEST authentication material is generated at execution time, never committed.

## 13. Direct role memberships

Expected exact VRA graph, with `SET TRUE, INHERIT FALSE, ADMIN FALSE` on every edge:

- `vra_migrator → vra_owner` (accepted existing administrative edge)
- `vra_owner → vra_async_executor` (accepted existing edge)
- `vra_owner → vra_sync_executor`
- `vra_owner → vra_security_executor`
- `vra_owner → vra_telemetry_executor`

No new ordinary membership is declared. Catalog and SQL execution: NOT VERIFIED.

## 14. Complete transitive SET graph

Expected non-self VRA paths: owner to all four executors; migrator to owner and all
four executors. Ordinary runtime, five async workloads and three TEST LOGIN roles
have no executor SET path. Bootstrap checks all nonsuperuser identities, including
unplanned helpers, rather than only a fixed username list. The test compares the
entire recursive SET graph and performs direct SQL denials. Actual result:
NOT EXECUTED. The accepted administrative migrator path is retained.

## 15. Final schema CREATE holders

Expected effective holders are the disposable administrator `postgres` and
`vra_owner`; executor and ordinary CREATE is denied. Executors receive schema
USAGE only. Actual catalog/DDL proof: NOT EXECUTED.

## 16. PUBLIC grant inspection

V4 revokes PUBLIC rights on its 17 tables, the evidence composite, and CREATE on
trusted `vra`. Prior table grants are unchanged. Tests inspect PUBLIC table,
sequence and schema ACLs. Actual catalog result: NOT EXECUTED.

## 17. New V4 object owners

All 17 tables and `audit_evidence_row` are created under the guarded `vra_owner`
migration identity. No new persisted function or trigger exists in V4. Actual
ownership/catalog proof: NOT EXECUTED.

## 18. Runtime final V4 grants

Source declarations are:

- SELECT on account, identity binding, organization/membership, inventory grant,
  both POC records, session/factor tables, staff roles, proposals/approvals and
  webhook receipt/target.
- UPDATE(label,version) on both POC records.
- No direct protected-audit SELECT or DML; no session/factor/role/proposal/approval
  DML.
- security_event INSERT columns: event_id, actor_type, actor_account_id,
  actor_workload, event_code, outcome_code, occurred_at, request_id, target_type,
  target_id, target_reference, risk_code. Safe SELECT uses these plus source_kind.
  No INSERT of source_kind or any observer provenance column, and no UPDATE,
  DELETE or TRUNCATE.
- INSERT webhook receipt; UPDATE receipt status and target state/version only.

Effective grants: NOT VERIFIED. V2 runtime direct INSERT/UPDATE is retained;
no V5 revocation or replacement was pulled into Phase 1.

## 19. Worker/reconciler new grants

Source review finds no new POC-04 table or function rights for worker, reconciler
or other async helpers, and no executor table capability privileges activated.
The three TEST logins have CONNECT/schema USAGE, and only the reader additionally
has evidence-type USAGE. Effective catalog/SQL proof: NOT EXECUTED.

## 20. Negative SQL allow/deny results

Every database result below is NOT EXECUTED. Assertions are present for:

| SQL category | Required outcome |
| --- | --- |
| runtime/workers/TEST logins SET ROLE executor | DENY 42501 |
| ordinary or executor CREATE in vra | DENY 42501 |
| ordinary ALTER ROLE / role membership GRANT | DENY 42501 |
| runtime migration and forbidden table/column DML | DENY |
| invalid state, negative generation/version, wrong digest length | DENY 23514 |
| duplicate identity/membership/grant/session authority | DENY 23505 |
| FK violation / required field NULL / overlong field | DENY 23503 / 23502 / 22001 |
| invalid APP/POSTGRES_ACL provenance shape | DENY 23514 |
| ordinary origin/provenance insertion | DENY 42501 |
| forged POSTGRES_ACL outcome via permitted APP columns | DENY 23514 |
| bootstrap unexpected privilege/membership drift | DENY P0001, retain reported drift |
| administrative migrator SET path | ALLOW |
| existing runtime V2 terminal INSERT/UPDATE residual | ALLOW within rolled-back proof |

Before/after authoritative-row snapshots are compared around denied SQL.
Phase-2 function-denial proof is not claimed; those functions do not exist.
The generic DEFAULT VALUES permission-denial loop excludes security_event, which
has approved column-level INSERT; explicit forbidden-provenance tests cover its
ACL boundary. This corrects the expected behavior from the
[PostgreSQL 17.11 executor source](https://raw.githubusercontent.com/postgres/postgres/REL_17_11/src/backend/executor/execMain.c).
The selected-transport guard uses the pinned
[Testcontainers 2.0.5 factory API](https://raw.githubusercontent.com/testcontainers/testcontainers-java/2.0.5/core/src/main/java/org/testcontainers/DockerClientFactory.java)
before client/resource-reaper initialization.

## 21. Collector profile/mount boundary

Preparation/source checks PASS; actual infrastructure boundary NOT VERIFIED.
Compose parses and defines only PostgreSQL under a TEST profile. Future observer
metadata reserves UID/GID 9404 and a read-only collector-source mount; no observer
process is created. Server owns the source directory (`postgres:9404`, mode 2750)
and configured files use 0640. No Docker socket, observer administrator credential,
PGDATA mount to observer, database server-file/monitor/log-parameter privileges,
or business/audit grants are supplied. Runtime/workers have no source mount.

Logging disables bind-parameter capture and statement/duration capture. Fresh
boot UUID plus timestamped segment naming avoids restart collision. Retention,
trusted metadata and forced rotation harness behavior remain Phase-2 work.
Actual IDs, mounts, ownership/modes, SHOW settings, read/write denial and leak
behavior have not executed. The profile was not started.

The Phase-1 integration fixture requires an explicitly declared isolated TEST
endpoint equal to DOCKER_HOST and checks the selected Testcontainers transport
before initializing the Docker client/resource reaper and at teardown. It uses one stable run
UUIDv4, per-instance UUIDv5 logical ordinal, complete canonical source binding,
fresh labelled named PGDATA/network/container, physical inspect before bootstrap,
and ownership/identity reinspection before limited teardown and absence checks.
These infrastructure paths compile; actual cleanup wrong-label/ID denial and
other-run sentinel preservation remain unexecuted required proof.

## 22. Exact Gradle commands executed

Working directory was `backend`, except the support compile command stated below.
`JDK` below expands exactly to
`/var/folders/6p/8cx8_9294s10q4v934jqdn9h0000gn/T/vra-poc04-jdk-x0q7h_vo/jdk-21.0.12.1+1/Contents/Home`.
It is the verified selected Temurin 21.0.12.1+1 archive, SHA-256
`3623232f33a9c3baadf304480b2535f9a3cba8a58d42ecbb438ba267315d9998`,
size 200073404 bytes. No unverified host JDK substitute is claimed.

```sh
env JAVA_HOME="$JDK" ./gradlew --no-daemon :migration:test --tests dev.vra.migration.Poc04FoundationMigrationIntegrationTest
env JAVA_HOME="$JDK" ./gradlew --no-daemon :migration:test
env JAVA_HOME="$JDK" ./gradlew --no-daemon :runtime:test
# From repository root:
env JAVA_HOME="$JDK" ./backend/gradlew -p backend --no-daemon :migration:compileTestJava :runtime:compileTestJava
# From backend; only pinned JDK detection enabled:
env JAVA_HOME="$JDK" ./gradlew --no-daemon -Dorg.gradle.java.installations.auto-detect=false -Dorg.gradle.java.installations.paths="$JDK" :migration:javaToolchains
env JAVA_HOME="$JDK" ./gradlew --no-daemon -Dorg.gradle.java.installations.auto-detect=false -Dorg.gradle.java.installations.paths="$JDK" assemble
env JAVA_HOME="$JDK" ./gradlew --no-daemon -Dorg.gradle.java.installations.auto-detect=false -Dorg.gradle.java.installations.paths="$JDK" --rerun-tasks :migration:test :runtime:test :migration:compileTestJava :runtime:compileTestJava
env JAVA_HOME="$JDK" ./gradlew --no-daemon -Dorg.gradle.java.installations.auto-detect=false -Dorg.gradle.java.installations.paths="$JDK" --rerun-tasks :migration:test
```

The first targeted test invocation intentionally failed before V4 was created:
1 test / 0 skipped / 1 failure / 0 errors, exit 1. Later targeted runs had 2 then
4 tests, no skips/failures/errors; final targeted run exit 0. Earlier full migration
unit run had 1/0/0/0 and runtime 56/0/0/0, exit 0. Support compile, toolchain,
assemble and final forced compile/unit commands exited 0. Toolchain inspection
reported only the pinned aarch64 Eclipse Temurin JDK; final run executed all nine
compile/resource/jar/unit tasks. Existing deprecation warnings remain. A final forced migration compile/unit rerun
after the two static-review fixes passed 4/0/0/0, exit 0; unchanged runtime XML
remains 56/0/0/0.

Required `:migration:integrationTest`, `:runtime:integrationTest` and normal
`:migration:check :runtime:check` were NOT EXECUTED: they require database/container
mutation, which is blocked by missing isolated TEST environment evidence. They
are not reported as PASS or as skipped tests.

## 23. Final test counts from independently parsed JUnit XML

| Suite | Tests | Skipped | Failures | Errors |
| --- | ---: | ---: | ---: | ---: |
| :migration:test | 4 | 0 | 0 | 0 |
| :runtime:test | 56 | 0 | 0 | 0 |
| Executed total | 60 | 0 | 0 | 0 |
| :migration:integrationTest | NOT EXECUTED | N/A | N/A | N/A |
| :runtime:integrationTest | NOT EXECUTED | N/A | N/A | N/A |

Unit tasks intentionally exclude postgres-tagged tests. Their absence is not
PostgreSQL PASS. Four migration unit cases cover old source/migration inventory,
collector preparation, canonical source binding and UUID identity derivation.

## 24. Whitespace and changed-configuration validation

`git diff --check`: PASS. Separate final-newline/trailing-whitespace/CR inspection:
PASS. `bash -n validation/poc-04/db/collector-test-entrypoint.sh`: PASS.
`docker compose -f validation/poc-04/db/collector-test.compose.yaml --profile
poc04-collector-test config --quiet`: PASS with explicit dummy UUID/source values
and `/dev/null` as the parse-only password-file value. No daemon provisioning or
secret was involved. No JSON/build/workflow file changed. SQL source review and
Java compilation PASS; actual PostgreSQL SQL execution remains blocked.

## 25. Secret/sensitive-material scan

Pinned Gitleaks 8.30.1 scanned copies of all 27 implementation/support files:
exit 0, zero findings, redacted report. Its selected archive SHA-256 is
`b40ab0ae55c505963e365f271a8d3846efbc170aa17f2607f13df610a9aeb6a5`,
size 7897593 bytes. Final scan includes all 28 changed files including this report: exit 0, zero findings. Source review found no
introduced literal DB password, private key, browser bearer, factor/webhook secret,
signed download URL or raw collector log. New TEST credentials are generated at
execution; preexisting unrelated historical test fixture strings were not changed.
Raw scanner secrets are not printed or published. This is not an S13 gate claim.

## 26. Tracked modified paths

- `backend/migration/src/test/java/dev/vra/migration/MigrationSecurityIntegrationTest.java`
- `backend/migration/src/test/java/dev/vra/migration/OutboxMigrationSecurityIntegrationTest.java`
- `backend/runtime/src/test/java/dev/vra/async/AsyncPermissionIntegrationTest.java`
- `backend/runtime/src/test/java/dev/vra/async/AsyncRoleBootstrap.java`
- `backend/runtime/src/test/java/dev/vra/async/AsyncVisibilityIntegrationTest.java`
- `backend/runtime/src/test/java/dev/vra/async/DeliveryClaimIntegrationTest.java`
- `backend/runtime/src/test/java/dev/vra/async/DeliveryStateIntegrationTest.java`
- `backend/runtime/src/test/java/dev/vra/async/ProducerOutboxIntegrationTest.java`
- `backend/runtime/src/test/java/dev/vra/async/ProjectionInboxIntegrationTest.java`
- `backend/runtime/src/test/java/dev/vra/async/ProjectionRebuildIntegrationTest.java`
- `backend/runtime/src/test/java/dev/vra/async/ReconciliationIntegrationTest.java`
- `backend/runtime/src/test/java/dev/vra/async/RetryReplayIntegrationTest.java`
- `backend/runtime/src/test/java/dev/vra/async/ValidationSimulatorIntegrationTest.java`
- `backend/runtime/src/test/java/dev/vra/async/WorkerProcessCrashIntegrationTest.java`
- `backend/runtime/src/test/java/dev/vra/inventory/IdempotentReservationApplicationServiceIntegrationTest.java`
- `backend/runtime/src/test/java/dev/vra/inventory/ReservationConcurrencyIntegrationTest.java`
- `backend/runtime/src/test/java/dev/vra/inventory/ReservationHttpPostgresIntegrationTest.java`
- `backend/runtime/src/test/java/dev/vra/inventory/ReservationIdempotencyRepositoryIntegrationTest.java`
- `backend/runtime/src/test/java/dev/vra/inventory/ReservationTransactionIntegrationTest.java`
- `backend/runtime/src/test/java/dev/vra/platform/health/HealthProbePostgresIntegrationTest.java`

## 27. Untracked paths

- `backend/migration/src/main/resources/db/migration/V4__poc04_identity_security_foundation.sql`
- `backend/migration/src/test/java/dev/vra/migration/Poc04FoundationMigrationIntegrationTest.java`
- `validation/poc-04/db/COLLECTOR_TEST.md`
- `validation/poc-04/db/bootstrap-security-roles.sql`
- `validation/poc-04/db/collector-test-entrypoint.sh`
- `validation/poc-04/db/collector-test.compose.yaml`
- `validation/poc-04/db/postgresql-collector-test.conf`
- `validation/poc-04/evidence/phase1/IMPLEMENTATION_REPORT.md`

## 28. Staged paths

None. No staging, commit, push or other Git mutation.

## 29–32. Phase-2 absence

| Item | Result |
| --- | --- |
| 29. Newly introduced Phase-2 files/behavior | NO |
| 30. V5 present | NO |
| 31. V6 present | NO |
| 32. Phase 2 started | NO |

No observer/parser process, observer append capability, API/worker DB identity
validator, V2 terminal-write revocation, security capability function, OIDC,
browser-session application, CSRF/factor/maker-checker service, webhook processor,
Bruno, Playwright or CI/workflow implementation was introduced. The legacy POC-02
`JdbcReservationIdempotencyRepository.java` already exists and remains byte-identical
to HEAD (SHA-256 `7d93cd8c0825b3b4cf9b6979d173167248ddde80eef45a0e6a18f667b2c388cd`).
It was not created, modified or upgraded into a Phase-2 implementation.

## 33. POC-04 status

IN PROGRESS / NOT VERIFIED. Phase 0 CLOSED; Phase 1 AUTHORIZED / NOT CLOSED;
Phase 2 NOT AUTHORIZED. No gate-upgrade claim.

## 34. S0–S15 status

S0, S1, S2, S3, S4, S5, S6, S7, S8, S9, S10, S11, S12, S13, S14 and S15 each
remain NOT VERIFIED.

## 35. Unresolved ambiguity/blocker

No unresolved V4/V6 placement or authority conflict was found by source review.
Assurance and factor-operation literal spellings map only to described approved
operations; security scope is separate from organization/Inventory authority.

The load-bearing blocker is the missing available, attested dedicated disposable
TEST Docker endpoint. Current context is `desktop-linux`; `docker info` exits 1
because `unix:///Users/siwakornbundi/.docker/run/docker.sock` does not exist.
The Phase-0 baseline §7 requires dedicated TEST-daemon and physical-instance
attestation before database mutation. No shared/default environment fallback was
used. The endpoint question remains pending.

All real migration/regression/catalog/SQL-negative/collector/teardown proofs must
run on that environment before readiness can become YES. Source declarations and
compiled assertions cannot close that gap. Do not start Phase 2.


---

# PostgreSQL execution-completion attempt — stopped at preflight

Recorded at UTC: `2026-09-30T15:47:57.864829+00:00`.

PHASE 1 CLOSED: NO

READY FOR INDEPENDENT PHASE-1 REVIEW: NO

The current execution-completion request was attempted through read-only
infrastructure preflight only. The dedicated disposable TEST endpoint remains
unavailable, so the user-required STOP condition applies. No database/container
was started, no bootstrap/Flyway/SQL was executed, and no Gradle suite was rerun.
No implementation/support source was changed in this attempt.

The preceding report body is preserved verbatim. The exact previous report is
also saved at `IMPLEMENTATION_REPORT.previous-4a4116f1.md`, SHA-256
`4a4116f11c77fdd2d7c503405f24cb53fe69e84c1502fcb2fc049387046eacbb`.
The prior NOT EXECUTED results remain historical; this attempt does not upgrade
them to PostgreSQL execution. Command output, UTC timestamps, exit statuses,
sanitized environment inventory and null database identity are captured in
`POSTGRES_EXECUTION_PREFLIGHT.json`, SHA-256
`6eee43e49f81d5bbb1db5eea0f60d25539be7c05dc309bec516b6e2bec98357f`.
Both artifacts are TEST evidence only and contain no authentication material.

## Recorded infrastructure commands

| Command | Exit | Result |
| --- | ---: | --- |
| docker version | 1 | Client 29.8.1, API 1.56, darwin/arm64; server unreachable |
| docker info | 1 | Server unreachable; no Docker daemon identity obtained |
| docker context show | 0 | desktop-linux |
| sanitized Docker/Testcontainers environment inventory | 0 | No DOCKER_*, TESTCONTAINERS_* or VRA_POC04_* variables present |
| git branch --show-current | 0 | poc/04-security-auth |
| git rev-parse HEAD origin/main origin/poc/04-security-auth | 0 | All remain 01256d96d85823740a8d6c3a3f99346afbdbef56 |
| git status --porcelain=v1 -uall | 0 | Only existing Phase-1 candidate before these evidence additions |
| git diff --cached --name-only | 0 | Empty |

Exact Docker error:

```text
failed to connect to the docker API at unix:///Users/siwakornbundi/.docker/run/docker.sock; check if the path is correct and if the daemon is running: dial unix /Users/siwakornbundi/.docker/run/docker.sock: connect: no such file or directory
```

DOCKER_HOST: absent. VRA_POC04_DISPOSABLE_TEST_DOCKER_ENDPOINT: absent.
Testcontainers transport was not initialized because the required endpoint was
not available/declared. No resolved Testcontainers endpoint or daemon is claimed.
No H2, mock, shared PostgreSQL, production/staging database, persistent local DB
or automatic fallback was used. Database identity, system_identifier, executed
PostgreSQL version and container/volume/network IDs are all absent.

## Requested final report, items 1–37

Every database item marked NOT EXECUTED has no execution command, exit status,
test count or database identity; those fields are N/A, not a passing result.
Static declarations and prior unit counts are identified separately.

| Item | Current result |
| --- | --- |
| 1. Docker/Testcontainers endpoint | BLOCKED; missing desktop socket, no dedicated TEST endpoint; commands and exit statuses above |
| 2. PostgreSQL exact version/image | NOT EXECUTED; reviewed intended server is 17.11, version_num 170011; image docker.io/library/postgres@sha256:d74eeac9a635390a49bc21bd49fccd973de707e2a53a76ac49b552b8712ec46f |
| 3. Fresh V1→V4 | NOT EXECUTED |
| 4. Populated V3→V4 | NOT EXECUTED |
| 5. Flyway validate | NOT EXECUTED |
| 6. Same-DB rerun | NOT EXECUTED |
| 7. V1/V2/V3 hashes | Rehashed; exact Phase-0 recorded/HEAD matches; hashes below |
| 8. Retained data | NOT EXECUTED; no before/after DB snapshots exist |
| 9. V4 SHA-256 | 45161b8c54f10778a5521cdd23dc0945fadb65364bd8094e3d19d8f79cfdd1e8; unchanged from prior report |
| 10. Bootstrap SHA-256 | 976cf704687e5bf005de8943c706fdd996d801172c112d640c993e0b193407b5; unchanged from prior report |
| 11. Six role attributes | NOT EXECUTED; earlier six-row attribute table is expected source declaration only |
| 12. Direct memberships | NOT EXECUTED; earlier owner→executor graph is expected source declaration only |
| 13. Complete transitive SET graph | NOT EXECUTED; administrative path and ordinary denials remain unproved |
| 14. Schema CREATE holders | NOT EXECUTED |
| 15. PUBLIC grants | NOT EXECUTED |
| 16. V4 owners | NOT EXECUTED |
| 17. Runtime V4 grants | NOT EXECUTED; earlier section 18 records source declarations only |
| 18. Worker/reconciler new grants | NOT EXECUTED; no new source grant introduced |
| 19. SQL denials/SQLSTATEs | NOT EXECUTED; no observed SQLSTATE; expected values in earlier section 20 are not actual results |
| 20. Constraint negatives | NOT EXECUTED |
| 21. Collector profile/mount | NOT VERIFIED; source preparation unchanged; no actual OS/mount/credential boundary proof |
| 22. Regression commands | None in this attempt; required integration/check/build execution stopped at environment prerequisite |
| 23. Per-suite counts | Current attempt: all four suites NOT EXECUTED, counts N/A; historical counts below |
| 24. Total counts | Current attempt: 0 tests executed, skip/failure/error suite counts N/A; historical unit total 60/0/0/0 |
| 25. Phase-2 path absence | V5/V6/observer/parser/identity-validator paths absent; no new Phase-2 work. Legacy JdbcReservationIdempotencyRepository exists unchanged; literal absence cannot be claimed |
| 26. V5 present | NO |
| 27. V6 present | NO |
| 28. Phase 2 started | NO |
| 29. git diff --check | PASS, exit 0; separate whitespace/final-newline check PASS |
| 30. Secret scan | Final evidence/source scan PASS, zero findings; details below |
| 31. Tracked modified paths | Same 20 Phase-1 paths, listed below |
| 32. Untracked paths | 10 Phase-1 paths/evidence files, listed below |
| 33. Staged paths | None |
| 34. Updated report SHA-256 | Supplied in final response; excluded from its own bytes |
| 35. Blockers | Required dedicated TEST endpoint unavailable; PostgreSQL evidence not executed. Legacy repository path conflicts with literal absence request |
| 36. POC-04 status | IN PROGRESS / NOT VERIFIED; Phase 1 NOT CLOSED; source PREPARED / NOT ACCEPTED |
| 37. S0–S15 | Every gate remains NOT VERIFIED |

### Immutability and scope

| File | Current SHA-256 | Comparison |
| --- | --- | --- |
| V1 | d804cbe5153b9a904ae0747af55c8295079d4a0c3edb4a38145ab5199b5b59c4 | exact Phase-0 and HEAD bytes |
| V2 | 7bef750b59b7b77e8402f4f42c13c21c4e072092504b9f268bace9b72de8d2bb | exact Phase-0 and HEAD bytes |
| V3 | 9edbd15ffe08ee4edafa3f549856ee38a782485c54db9c73e69d4263e5ca26b4 | exact Phase-0 and HEAD bytes |

All 27 implementation/support hashes match the prior report table. V4/bootstrap
and the prepared test/collector source are unchanged. The only changes in this
attempt are this append and the two evidence artifacts named above.

The legacy POC-02 production file
`backend/runtime/src/main/java/dev/vra/inventory/adapter/out/persistence/JdbcReservationIdempotencyRepository.java`
already exists in starting HEAD. Its unchanged SHA-256 is
`7d93cd8c0825b3b4cf9b6979d173167248ddde80eef45a0e6a18f667b2c388cd`.
The current request's literal absence requirement cannot be affirmed against that
baseline. No removal or redesign was attempted. No Phase-2 replacement was added.

### Current and historical suite results

| Suite | Current attempt | Historical tests/skipped/failures/errors |
| --- | --- | --- |
| :migration:test | NOT EXECUTED | 4 / 0 / 0 / 0 |
| :migration:integrationTest | NOT EXECUTED | NOT EXECUTED |
| :runtime:test | NOT EXECUTED | 56 / 0 / 0 / 0 |
| :runtime:integrationTest | NOT EXECUTED | NOT EXECUTED |
| Normal backend check/build | NOT EXECUTED | assemble previously passed; database checks NOT EXECUTED |

The earlier exact Gradle commands remain in section 22. No prior unit or build
result is presented as a fresh result of this execution-completion request. No
skipped PostgreSQL test is labelled PASS. No database cleanup was needed or run.

### Final artifact checks

Read-only `git diff --check` exited 0. Separate CR, trailing-whitespace and
final-newline checks passed over all changed files. The new JSON evidence parses.
Pinned Gitleaks 8.30.1 scanned all 30 changed files, including the appended report
and the two new evidence files: exit 0, zero findings, with redacted output. A
separate private-key/signed-URL marker check found no sensitive marker. No DB
credential was generated, persisted, printed or injected in this stopped attempt.
These artifact checks do not substitute for database execution or gate proof.

### Tracked modified paths

- `backend/migration/src/test/java/dev/vra/migration/MigrationSecurityIntegrationTest.java`
- `backend/migration/src/test/java/dev/vra/migration/OutboxMigrationSecurityIntegrationTest.java`
- `backend/runtime/src/test/java/dev/vra/async/AsyncPermissionIntegrationTest.java`
- `backend/runtime/src/test/java/dev/vra/async/AsyncRoleBootstrap.java`
- `backend/runtime/src/test/java/dev/vra/async/AsyncVisibilityIntegrationTest.java`
- `backend/runtime/src/test/java/dev/vra/async/DeliveryClaimIntegrationTest.java`
- `backend/runtime/src/test/java/dev/vra/async/DeliveryStateIntegrationTest.java`
- `backend/runtime/src/test/java/dev/vra/async/ProducerOutboxIntegrationTest.java`
- `backend/runtime/src/test/java/dev/vra/async/ProjectionInboxIntegrationTest.java`
- `backend/runtime/src/test/java/dev/vra/async/ProjectionRebuildIntegrationTest.java`
- `backend/runtime/src/test/java/dev/vra/async/ReconciliationIntegrationTest.java`
- `backend/runtime/src/test/java/dev/vra/async/RetryReplayIntegrationTest.java`
- `backend/runtime/src/test/java/dev/vra/async/ValidationSimulatorIntegrationTest.java`
- `backend/runtime/src/test/java/dev/vra/async/WorkerProcessCrashIntegrationTest.java`
- `backend/runtime/src/test/java/dev/vra/inventory/IdempotentReservationApplicationServiceIntegrationTest.java`
- `backend/runtime/src/test/java/dev/vra/inventory/ReservationConcurrencyIntegrationTest.java`
- `backend/runtime/src/test/java/dev/vra/inventory/ReservationHttpPostgresIntegrationTest.java`
- `backend/runtime/src/test/java/dev/vra/inventory/ReservationIdempotencyRepositoryIntegrationTest.java`
- `backend/runtime/src/test/java/dev/vra/inventory/ReservationTransactionIntegrationTest.java`
- `backend/runtime/src/test/java/dev/vra/platform/health/HealthProbePostgresIntegrationTest.java`

### Untracked paths

- `backend/migration/src/main/resources/db/migration/V4__poc04_identity_security_foundation.sql`
- `backend/migration/src/test/java/dev/vra/migration/Poc04FoundationMigrationIntegrationTest.java`
- `validation/poc-04/db/COLLECTOR_TEST.md`
- `validation/poc-04/db/bootstrap-security-roles.sql`
- `validation/poc-04/db/collector-test-entrypoint.sh`
- `validation/poc-04/db/collector-test.compose.yaml`
- `validation/poc-04/db/postgresql-collector-test.conf`
- `validation/poc-04/evidence/phase1/IMPLEMENTATION_REPORT.md`
- `validation/poc-04/evidence/phase1/IMPLEMENTATION_REPORT.previous-4a4116f1.md`
- `validation/poc-04/evidence/phase1/POSTGRES_EXECUTION_PREFLIGHT.json`

### Staged paths

None. No Git mutation, staging, commit or push.

Execution remains stopped. A reachable, attested dedicated disposable TEST
Docker endpoint is required before this request can continue. Phase 2 remains
NOT AUTHORIZED. Independent Phase-1 review remains NOT READY.


---

# Resume after Docker Desktop startup — infrastructure preflight

Recorded UTC: `2026-09-30T16:06:43.671708+00:00`.
Previous full report SHA-256:
`f7edcd95bee9bdafc0e6e33f5985b94d632c2a393df6affd3022e63e49a6036f`.
The preceding bytes and the original failed preflight JSON remain unchanged.

Docker availability now PASS: `docker version` and `docker info` both exit 0.
Client/Engine version is 29.8.1; Docker Desktop 4.93.0; server linux/arm64.
Context `desktop-linux` resolves to
`unix:///Users/siwakornbundi/.docker/run/docker.sock`.
Daemon ID is `13f77a94-7a9b-48e3-af0b-8f1915e2183e`.
Sanitized command/daemon/container evidence is
`POSTGRES_EXECUTION_RESUME_PREFLIGHT.json`, SHA-256
`f5245404d9d291d5398ce38a9f295b15e3cbf576a9edfbb53ffc6ab269ce04ff`.

The daemon contains 5 existing containers, including the running persistent
root-Compose `vra-poc00-postgres-1` database and four containers from other projects.
No existing container, volume or network was changed. Neither DOCKER_HOST nor
VRA_POC04_DISPOSABLE_TEST_DOCKER_ENDPOINT is set. Testcontainers was not initialized
and no PostgreSQL container was started.

The remaining precondition is the accepted Phase-0 baseline §7 dedicated
disposable TEST daemon requirement. Ryuk's reviewed trust boundary explicitly
excludes unrelated workloads/secrets on its daemon. Availability of this shared
Desktop daemon does not establish that required isolation. A dedicated endpoint
question is pending. No new daemon/helper was selected or deployed.

All 27 implementation/support hashes still match the initial report; source is
unchanged. Fresh/populated migrations, Flyway validation/rerun, SQL/catalog/role
checks, collector execution and PostgreSQL regression suites remain NOT EXECUTED
in this attempt (0 tests executed, suite counts and SQLSTATEs N/A). Earlier unit
counts remain historical. No source defect has been exposed by PostgreSQL because
PostgreSQL execution has not begun.

PHASE 1 CLOSED: NO

READY FOR INDEPENDENT PHASE-1 REVIEW: NO

Phase 2 started: NO. V5/V6 remain absent. POC-04 and S0–S15 remain NOT VERIFIED.
No Git mutation was executed. Only this report append and the resume-preflight
JSON evidence were created/changed during this resume attempt.


---

# Isolated-daemon execution attempt — failed before PostgreSQL creation

Attempt ID: `304b3976-4ebe-4a61-8bbc-e67a9a40e669`.
Prior full report SHA-256:
`34bb2c8865d8f741955bbf83f949c41d9040035f923ec11c2c0ce775fa242d98`.
Every preceding report byte and both prior failed/precondition evidence files
remain unchanged. Source implementation/support files remain byte-identical.

PHASE 1 CLOSED: NO

READY FOR INDEPENDENT PHASE-1 REVIEW: NO

The dedicated TEST Docker preflight passed and the first selected fresh test was
executed. It failed during Testcontainers bootstrap before a PostgreSQL container,
PGDATA volume or run network was created. The requirement to stop after a failing
expectation was followed. No further tests, database mutation or source remediation
was performed. This is an infrastructure failure, not an executed schema defect.

## Infrastructure identity and selected environment

Endpoint: `unix:///Users/siwakornbundi/.colima/vra-poc04-test/docker.sock`.
Daemon ID: `14c8ff26-86d1-4c0b-88a6-560be91a65ea`.
Daemon name: `colima-vra-poc04-test`.
Server: Docker 29.5.2, linux/arm64. Client: 29.8.1.
Independent initial inventory: 0 containers, 0 volumes, 0 images; three built-in
networks. `docker version`, `docker info`, inventory commands all exited 0.

The following were injected only for the test command:

```text
DOCKER_HOST=unix:///Users/siwakornbundi/.colima/vra-poc04-test/docker.sock
VRA_POC04_DISPOSABLE_TEST_DOCKER_ENDPOINT=unix:///Users/siwakornbundi/.colima/vra-poc04-test/docker.sock
TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock
TESTCONTAINERS_HOST_OVERRIDE=192.168.106.3
TESTCONTAINERS_RYUK_CONTAINER_IMAGE=docker.io/testcontainers/ryuk@sha256:7c1a8a9a47c780ed0f983770a662f80deb115d95cce3e2daa3d12115b8cd28f0
TESTCONTAINERS_TINYIMAGE_CONTAINER_IMAGE=docker.io/library/alpine@sha256:8fc3dacfb6d69da8d44e42390de777e48577085db99aa4e4af35f483eb08b989
```

No Testcontainers health check or resource reaper was disabled. No command in
this attempt targeted Docker Desktop; context configuration was not switched.
The user-provided dedicated daemon is accepted execution infrastructure. Existing
reviewed PostgreSQL/Ryuk/tinyimage artifacts were pulled by exact digest on it;
all pulls exited 0 and inspect reported linux/arm64 and matching repository digests.
The intended PostgreSQL version remains 17.11, server_version_num 170011; it was
not actually started or queried. No PostgreSQL database/system identifier exists.

## Exact executed test and failure

Working directory: `/Users/siwakornbundi/Project/VRA-Fastform/backend`.
JAVA_HOME is the previously verified pinned Temurin JDK path:
`/var/folders/6p/8cx8_9294s10q4v934jqdn9h0000gn/T/vra-poc04-jdk-x0q7h_vo/jdk-21.0.12.1+1/Contents/Home`.
The full environment, command, timestamps and exit status are in fresh-command.json.

```sh
./gradlew --no-daemon   -Dorg.gradle.java.installations.auto-detect=false   -Dorg.gradle.java.installations.paths="$JAVA_HOME"   :migration:integrationTest   --tests dev.vra.migration.Poc04FoundationMigrationIntegrationTest.freshV1ThroughV4ValidatesRerunsAndEnforcesPhaseOneBoundary
```

Exit status: 1. JUnit: 1 test / 0 skipped / 1 failure / 0 errors.
Compile/resource tasks were up-to-date; the selected integration test executed.
The failure is at the fixture's fresh-name absence assertion, because the Docker
client initializer raised IllegalStateException instead of reaching its inspect
operation. Root cause preserved in the JUnit XML:

```text
Could not connect to Ryuk at 192.168.106.3:32768
```

A subsequent read-only TCP connect check to that address/port timed out. Ryuk had
already exited and been removed when the independent absence inspection ran;
the TCP result is additional infrastructure evidence, not a schema/test rerun.
The published-port connection is the load-bearing blocker. No endpoint override,
source change, constraint/grant weakening or cleanup bypass was attempted.

## Resource identity and exact cleanup

The only container created in this execution was the pinned Testcontainers admin
helper:

- ID: `6a1a4a02ccbf76e4eec136c7db3702910c3a92a892df526cbb7520878fd0135c`
- Name: `testcontainers-ryuk-c24130a6-4543-4eee-9456-8e8f8f3138c9`
- Image: the exact reviewed Ryuk digest above.
- Existing helper labels: org.testcontainers=true, lang=java, ryuk=true,
  version=2.0.5. The event manifest binds its exact ID to this attempt. It did not
  acquire the new fixture's POC-04 labels because failure preceded DB provisioning.
- Network: original bridge ID
  `d6169038310792000aceb4c7cc43936dc2580869b05ebdee378244932b90279c`.
- Its administrative socket mount was `/var/run/docker.sock` on this dedicated
  daemon only. No application or future-observer socket mount was created.

Sanitized Docker create/start/die/destroy events and safe inspect projections are
preserved. No PostgreSQL container, volume or new network was created. No POC-04
DB run_id/database_instance_id/resource label proof is claimed, because that
provisioning path was not reached. No credentials were generated in a database,
published or persisted; the fixture's transient random TEST values stayed in memory.

Testcontainers removed the helper after the failing test process ended. A fresh
inspection against the recorded daemon proved the exact ID absent (exit 1,
`no such object`); inventory proved 0 containers, 0 volumes and the exact same
three built-in network IDs. No administrator remove/prune command was necessary.
The three pinned pulled images remain cache entries. Sentinel/wrong-label teardown
proof remains NOT EXECUTED because execution stopped at the first failure.

## Required final results, 1–37

| Item | This attempt's result |
| --- | --- |
| 1. Docker/Testcontainers endpoint | Docker preflight PASS; Testcontainers FAILED connecting to Ryuk |
| 2. PostgreSQL version/image | Exact reviewed image pulled/inspected; PostgreSQL version NOT EXECUTED |
| 3. Fresh V1→V4 | FAILED before PostgreSQL creation; no migration applied |
| 4. Populated V3→V4 | NOT EXECUTED after first failure |
| 5. Flyway validate | NOT EXECUTED |
| 6. Same-DB rerun | NOT EXECUTED |
| 7. V1/V2/V3 hashes | Rechecked exact Phase-0/HEAD matches; unchanged hashes in preceding table |
| 8. Retained data | NOT EXECUTED; no database created |
| 9. V4 SHA-256 | 45161b8c54f10778a5521cdd23dc0945fadb65364bd8094e3d19d8f79cfdd1e8 |
| 10. Bootstrap SHA-256 | 976cf704687e5bf005de8943c706fdd996d801172c112d640c993e0b193407b5 |
| 11. Six role attributes | NOT EXECUTED; source declarations unchanged |
| 12. Direct memberships | NOT EXECUTED |
| 13. Complete transitive SET graph | NOT EXECUTED |
| 14. Schema CREATE holders | NOT EXECUTED |
| 15. PUBLIC grants | NOT EXECUTED |
| 16. V4 owners | NOT EXECUTED |
| 17. Runtime V4 grants | NOT EXECUTED |
| 18. Worker/reconciler new grants | NOT EXECUTED |
| 19. SQL denial/SQLSTATEs | NOT EXECUTED; no observed PostgreSQL SQLSTATE |
| 20. Constraint negatives | NOT EXECUTED |
| 21. Collector profile/mount | NOT EXECUTED / NOT VERIFIED |
| 22. Regression commands | Only selected fresh migration command above executed; remaining suites/check/build stopped |
| 23. Per-suite counts | :migration:integrationTest selected fresh: 1/0/1/0; other suites NOT EXECUTED |
| 24. Total new tests/skipped/failures/errors | 1 / 0 / 1 / 0; earlier unit total 60/0/0/0 remains historical |
| 25. Phase-2 paths | No newly introduced paths; legacy JdbcReservationIdempotencyRepository remains preexisting unchanged |
| 26. V5 present | NO |
| 27. V6 present | NO |
| 28. Phase 2 started | NO |
| 29. git diff --check | PASS, exit 0; source/config/report whitespace and final newlines PASS; preserved raw Gradle output has trailing whitespace on line 2 |
| 30. Secret scan | Final scan PASS, zero findings; no raw secrets in published execution artifacts |
| 31. Tracked modified paths | Same 20 existing Phase-1 test/support paths |
| 32. Untracked paths | Existing Phase-1 files/evidence plus this attempt's 9 artifacts; listed below |
| 33. Staged paths | None |
| 34. Updated report SHA-256 | Supplied in final response |
| 35. Blockers | Host→Ryuk published-port connectivity; all PostgreSQL proofs remain outstanding |
| 36. POC-04 | IN PROGRESS / NOT VERIFIED; Phase 1 source PREPARED / NOT ACCEPTED |
| 37. S0–S15 | All NOT VERIFIED |

No database-driven source defect was exposed, since no PostgreSQL statement ran.
The outside-repository proof-capture and regression adapters were prepared and
compiled but not used. They changed no repository source and started no resources.
No external helper code modified the executed test. The temporary resource recorder
only captured administrative Docker metadata; it is not a PostgreSQL security
observer/parser, and no Phase-2 behavior was implemented.

## Preserved attempt artifacts and SHA-256

| Path | SHA-256 |
| --- | --- |
| `validation/poc-04/evidence/phase1/execution-304b3976-4ebe-4a61-8bbc-e67a9a40e669/TEST-dev.vra.migration.Poc04FoundationMigrationIntegrationTest.xml` | `47e6d6c9f8940671ca786e8ce27d55525eda26e7f4b1826720e0eec1d51e5938` |
| `validation/poc-04/evidence/phase1/execution-304b3976-4ebe-4a61-8bbc-e67a9a40e669/cleanup-proof.json` | `a1674fe389b439073a5c029084474b7e651d4903315b0758719a2fc4f5bd993e` |
| `validation/poc-04/evidence/phase1/execution-304b3976-4ebe-4a61-8bbc-e67a9a40e669/docker-resource-events.jsonl` | `0ecfd984d4851afe378a5553abef221cd4d559f76695e16dc119f17c4d29227a` |
| `validation/poc-04/evidence/phase1/execution-304b3976-4ebe-4a61-8bbc-e67a9a40e669/execution-summary.json` | `d0cb9e38c4d90ce8c77301812022f61ecb7588d26cc0680675902ed39b8f6ee5` |
| `validation/poc-04/evidence/phase1/execution-304b3976-4ebe-4a61-8bbc-e67a9a40e669/fresh-command.json` | `218897f4d9e28548f07dad1f90b0a638e80de1defe16bc2fc209efa5104dfb14` |
| `validation/poc-04/evidence/phase1/execution-304b3976-4ebe-4a61-8bbc-e67a9a40e669/fresh-gradle-output.txt` | `3555950acfc946f19c448702dd0a50f459cfee0cc3c971eed986339a0ccea5f0` |
| `validation/poc-04/evidence/phase1/execution-304b3976-4ebe-4a61-8bbc-e67a9a40e669/preflight.json` | `cc2a295672f4587267d716100e7ff49c732f5a21e7e6838b9668ab21e750a0b8` |
| `validation/poc-04/evidence/phase1/execution-304b3976-4ebe-4a61-8bbc-e67a9a40e669/pulled-images.json` | `bd0aafe1c3cb793bd77c9e820d4d128f09a2fccfb46bbbae32d5124e90cfdc8f` |
| `validation/poc-04/evidence/phase1/execution-304b3976-4ebe-4a61-8bbc-e67a9a40e669/ryuk-failure-infrastructure.json` | `784bd8999240ef948305f4102a019359789b612f38bb7a06da9324060276e0b6` |

## Current tracked modified paths

- `backend/migration/src/test/java/dev/vra/migration/MigrationSecurityIntegrationTest.java`
- `backend/migration/src/test/java/dev/vra/migration/OutboxMigrationSecurityIntegrationTest.java`
- `backend/runtime/src/test/java/dev/vra/async/AsyncPermissionIntegrationTest.java`
- `backend/runtime/src/test/java/dev/vra/async/AsyncRoleBootstrap.java`
- `backend/runtime/src/test/java/dev/vra/async/AsyncVisibilityIntegrationTest.java`
- `backend/runtime/src/test/java/dev/vra/async/DeliveryClaimIntegrationTest.java`
- `backend/runtime/src/test/java/dev/vra/async/DeliveryStateIntegrationTest.java`
- `backend/runtime/src/test/java/dev/vra/async/ProducerOutboxIntegrationTest.java`
- `backend/runtime/src/test/java/dev/vra/async/ProjectionInboxIntegrationTest.java`
- `backend/runtime/src/test/java/dev/vra/async/ProjectionRebuildIntegrationTest.java`
- `backend/runtime/src/test/java/dev/vra/async/ReconciliationIntegrationTest.java`
- `backend/runtime/src/test/java/dev/vra/async/RetryReplayIntegrationTest.java`
- `backend/runtime/src/test/java/dev/vra/async/ValidationSimulatorIntegrationTest.java`
- `backend/runtime/src/test/java/dev/vra/async/WorkerProcessCrashIntegrationTest.java`
- `backend/runtime/src/test/java/dev/vra/inventory/IdempotentReservationApplicationServiceIntegrationTest.java`
- `backend/runtime/src/test/java/dev/vra/inventory/ReservationConcurrencyIntegrationTest.java`
- `backend/runtime/src/test/java/dev/vra/inventory/ReservationHttpPostgresIntegrationTest.java`
- `backend/runtime/src/test/java/dev/vra/inventory/ReservationIdempotencyRepositoryIntegrationTest.java`
- `backend/runtime/src/test/java/dev/vra/inventory/ReservationTransactionIntegrationTest.java`
- `backend/runtime/src/test/java/dev/vra/platform/health/HealthProbePostgresIntegrationTest.java`

## Current untracked paths

- `backend/migration/src/main/resources/db/migration/V4__poc04_identity_security_foundation.sql`
- `backend/migration/src/test/java/dev/vra/migration/Poc04FoundationMigrationIntegrationTest.java`
- `validation/poc-04/db/COLLECTOR_TEST.md`
- `validation/poc-04/db/bootstrap-security-roles.sql`
- `validation/poc-04/db/collector-test-entrypoint.sh`
- `validation/poc-04/db/collector-test.compose.yaml`
- `validation/poc-04/db/postgresql-collector-test.conf`
- `validation/poc-04/evidence/phase1/IMPLEMENTATION_REPORT.md`
- `validation/poc-04/evidence/phase1/IMPLEMENTATION_REPORT.previous-4a4116f1.md`
- `validation/poc-04/evidence/phase1/POSTGRES_EXECUTION_PREFLIGHT.json`
- `validation/poc-04/evidence/phase1/POSTGRES_EXECUTION_RESUME_PREFLIGHT.json`
- `validation/poc-04/evidence/phase1/execution-304b3976-4ebe-4a61-8bbc-e67a9a40e669/TEST-dev.vra.migration.Poc04FoundationMigrationIntegrationTest.xml`
- `validation/poc-04/evidence/phase1/execution-304b3976-4ebe-4a61-8bbc-e67a9a40e669/cleanup-proof.json`
- `validation/poc-04/evidence/phase1/execution-304b3976-4ebe-4a61-8bbc-e67a9a40e669/docker-resource-events.jsonl`
- `validation/poc-04/evidence/phase1/execution-304b3976-4ebe-4a61-8bbc-e67a9a40e669/execution-summary.json`
- `validation/poc-04/evidence/phase1/execution-304b3976-4ebe-4a61-8bbc-e67a9a40e669/fresh-command.json`
- `validation/poc-04/evidence/phase1/execution-304b3976-4ebe-4a61-8bbc-e67a9a40e669/fresh-gradle-output.txt`
- `validation/poc-04/evidence/phase1/execution-304b3976-4ebe-4a61-8bbc-e67a9a40e669/preflight.json`
- `validation/poc-04/evidence/phase1/execution-304b3976-4ebe-4a61-8bbc-e67a9a40e669/pulled-images.json`
- `validation/poc-04/evidence/phase1/execution-304b3976-4ebe-4a61-8bbc-e67a9a40e669/ryuk-failure-infrastructure.json`

## Staged paths

None. No Git staging, commit, push, merge, reset, clean, stash or switch occurred.

Stop after preserving this failure. The dedicated Docker API is available; the
remaining infrastructure issue is Testcontainers' reachable published-port path.
Phase 2 remains NOT AUTHORIZED and independent Phase-1 review remains NOT READY.


Final artifact validation: all 40 changed files were scanned by pinned Gitleaks
8.30.1, exit 0, zero findings. JSON/JSONL and preserved JUnit XML parse checks
passed. Separate whitespace inspection found only the verbatim Gradle-output
line 2 trailing space; that original failure output is preserved without
normalization. All other changed-file trailing-whitespace/final-newline/CR checks
passed. The secret scan is an artifact check, not an S13 PASS.


# Localhost connectivity proof and PostgreSQL fixture failure — 2026-09-30 UTC

PHASE 1 CLOSED: **NO**

READY FOR INDEPENDENT PHASE-1 REVIEW: **NO**

This appendix preserves the preceding report byte-for-byte. Its prior SHA-256
was `d8731f17ee3c5d55f35df90ae7780071239eca5fbede02300f9bd6a4ec8b667d`.
All twelve archived report/preflight/previous isolated-attempt artifacts retain
their recorded hashes. The previous `192.168.106.3` Ryuk timeout remains historical
failure evidence; it is not replaced with the localhost result.

The minimum Testcontainers/Ryuk proof through `127.0.0.1` PASSED, including exact
helper cleanup. Only after that proof passed was the selected fresh PostgreSQL
test executed. PostgreSQL 17.11 started, but the fixture failed before the admin
role bootstrap or Flyway. Work stopped under the user's instruction to preserve
and report an executed failed expectation before remediation. No Phase-1 source
was edited during this attempt. This does not establish a V4 schema defect; it
exposes a genuine defect in the Phase-1 fixture's image attestation assertion.

## Minimum connectivity proof

Probe run UUID: `57951433-ef13-4dde-9ef5-3d397b096656`.
Java compilation and execution both exited **0**. This is an infrastructure probe,
not a JUnit suite; JUnit counts are N/A. No PostgreSQL container was requested by
this probe.

The selected transport was independently checked against
`unix:///Users/siwakornbundi/.colima/vra-poc04-test/docker.sock`, with expected
daemon ID `14c8ff26-86d1-4c0b-88a6-560be91a65ea`. Only the newly created Ryuk
helper was visible during the Java inspection; Docker Desktop resources were not
visible through that isolated endpoint. The helper was running, used the reviewed
Ryuk digest, and had only `/var/run/docker.sock` mounted to `/var/run/docker.sock`
on the isolated daemon. A real TCP connection to `127.0.0.1:32771` received Ryuk's
`ACK` response. Ryuk and Testcontainers checks remained enabled.

Ryuk container ID:
`9b4568c67129ba2fbb4b6b8dc49d7cd7710c0c61fd65345c3a50411ee49b1f3b`.
Its inspected labels include `dev.vra.environment=TEST`, `dev.vra.poc=04`, the
probe run UUID, source HEAD/content digest, tool-manifest digest, and logical
instance UUID `8fb65c47-20cf-5c10-a507-dfa6a71e7893`.

Automatic Testcontainers lifecycle cleanup removed this exact helper. The
post-cleanup exact-ID inspect exited **1**, reporting no such object. Inventory
commands exited **0**, returning zero containers, zero volumes, and the original
three built-in networks. No explicit administrative removal or prune was needed.
The complete Java command/classpath/environment and exit statuses are preserved
in `connectivity-57951433-ef13-4dde-9ef5-3d397b096656/probe-command.json`; the
assertions, actual inspection, ACK, and absence checks are preserved in that
folder's Java source, `connectivity-proof.json`, and `cleanup-proof.json`.

## Fresh PostgreSQL execution and exact failure

Orchestration attempt UUID: `e0a3d5b9-ab05-453f-b133-39b50828e0b7`.
The fixture separately generated its run namespace UUID
`d6086e95-29f0-4d32-aec6-3705b5740a3a`; its PostgreSQL logical instance UUID was
`1762ab45-d926-514a-be1c-7c6c35b94ddb`. Both identities are recorded rather than
assuming that the external orchestration UUID replaced the fixture's namespace.

The fresh preflight independently reconfirmed daemon ID
`14c8ff26-86d1-4c0b-88a6-560be91a65ea`, name `colima-vra-poc04-test`, Docker
server `29.5.2`, Linux/aarch64, zero containers, zero volumes, and only the
original networks. Each recorded preflight command exited **0**. All Docker calls
were bound to the isolated endpoint; no context switch or Docker Desktop query
was used. The current allowed environment was:

```text
DOCKER_HOST=unix:///Users/siwakornbundi/.colima/vra-poc04-test/docker.sock
VRA_POC04_DISPOSABLE_TEST_DOCKER_ENDPOINT=unix:///Users/siwakornbundi/.colima/vra-poc04-test/docker.sock
TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock
TESTCONTAINERS_HOST_OVERRIDE=127.0.0.1
```

The exact pinned image requested was:
`docker.io/library/postgres@sha256:d74eeac9a635390a49bc21bd49fccd973de707e2a53a76ac49b552b8712ec46f`.
A read-only metadata query in that actual disposable container returned database
`vra_poc01`, PostgreSQL `server_version_num=170011` (**17.11**), and
`system_identifier=7691375076119769131`. Its successful stdout and literal command
are preserved in `docker-resource-events.jsonl`; its command exit status was not
independently persisted. That query was administrative identity evidence, not a
Phase-1 migration or workload-denial proof.

Executed from `backend` using the previously verified Temurin 21.0.12.1+1:

```sh
./gradlew --no-daemon   -Dorg.gradle.java.installations.auto-detect=false   -Dorg.gradle.java.installations.paths=/var/folders/6p/8cx8_9294s10q4v934jqdn9h0000gn/T/vra-poc04-jdk-x0q7h_vo/jdk-21.0.12.1+1/Contents/Home   -I /var/folders/6p/8cx8_9294s10q4v934jqdn9h0000gn/T/vra-poc04-external-tc-vsfk4w5a/runner.init.gradle   :migration:integrationTest   --tests dev.vra.migration.Poc04FoundationMigrationIntegrationTest.freshV1ThroughV4ValidatesRerunsAndEnforcesPhaseOneBoundary
```

The command above used the endpoint/environment just recorded, reviewed Ryuk and
Alpine image pins, and run/source/tool metadata. The complete argv and every
injected non-secret environment field are in `fresh-command.json`. Gradle exited
**1**. Independently parsed preserved JUnit XML reports **1 test / 0 skipped /
1 failure / 0 errors**. The failing test entered the fixture and failed at
`Poc04FoundationMigrationIntegrationTest.java:898`:

```text
selected-platform config digest from Phase-0 pin
expected: sha256:97432f980da100ebd3e419711efee84e1e97a966d62c035286a07f239ddb4d9c
actual:   sha256:d74eeac9a635390a49bc21bd49fccd973de707e2a53a76ac49b552b8712ec46f
```

The fixture compares `InspectImageResponse.getId()` with the reviewed platform
config digest. Read-only inspection on this daemon identifies that returned Id
as the OCI **index** digest; inspection with `--platform linux/arm64` returns the
reviewed OCI **manifest** digest
`sha256:86fa57b44a1d38f09970f64ac71eb492f7a9cf94d08b85e1c8f07bb0fc0a1761`.
Both inspection commands exited **0**. These actual observations are preserved
in `image-attestation-defect.json`. Moby's current image-inspection implementation
also assigns Id from the selected target descriptor digest; this supports the
diagnosis but is not asserted to bind the exact server binary.
[Primary source: Moby image inspection](https://raw.githubusercontent.com/moby/moby/master/daemon/containerd/image_inspect.go).

The reviewed config blob was not separately re-attested in this attempt. A narrow
future correction must verify the reviewed index, actual platform manifest and
config blob as distinct identities. Merely replacing the expected config with the
index, allowing either hash, skipping the assertion, or changing the reviewed pin
would not provide the intended proof. No correction was made in this STOP turn.

No bootstrap, V1–V4 migration, Flyway validation, same-DB rerun, seeded upgrade,
role/ACL/SET graph assertion, workload SQL denial, or V4 constraint negative ran.
All remaining PostgreSQL suites were stopped; none is reported as passed/skipped.

## Exact resource identities, evidence limitations, and cleanup

| Resource | Exact identifier |
| --- | --- |
| Fresh PostgreSQL container | `be304b90ffa74f464e1dbc70332b9849bdeae0a2d37d7d6902d37ea5c9e5be49` |
| Fresh named PGDATA volume (Docker volume identifier is its name) | `vra-poc04-d6086e95-29f0-4d32-aec6-3705b5740a3a-postgres-authority-0-pgdata` |
| Fresh network | `415f0c206b1da818cb04149fae2f9fd569f861ba7beaeb3a0008185c03f1184a` |
| Gradle Testcontainers Ryuk helper | `ed67a9d43d0ef958ae4b1db6e8ff4d1f38d2e58f82f97ca74bfca987b949aa73` |

The PostgreSQL container's physical named-volume source was
`/var/lib/docker/volumes/vra-poc04-d6086e95-29f0-4d32-aec6-3705b5740a3a-postgres-authority-0-pgdata/_data`,
mounted at `/var/lib/postgresql/data`. Its inspected POC-04 labels, creation
metadata, image, mount, and attached exact network are preserved in the event
artifact. The fixture's volume/network labels were checked before the failing
assertion, but complete independent volume/network inspect records were not
captured. The Gradle Ryuk helper retained only native Testcontainers labels,
whereas the earlier standalone probe helper had the required POC-04 run labels.
The external hook did not apply its custom labels inside the Gradle test JVM;
that label boundary must be completed before claiming all-resource run labeling.
The native session association is
`922d78e5-8bcf-40a2-86ad-289a12574ac7`; it links the observed PostgreSQL container
and Gradle helper but does not substitute for the missing helper POC label proof.

Existing fixture bounded cleanup and the Testcontainers helper lifecycle removed
all four exact resources. All four post-cleanup inspect commands exited **1** with
no-such-object/volume/network responses. Inventory and daemon-ID commands exited
**0**: zero containers, zero volumes, and no new networks. Original network IDs
remained:

- bridge: `d6169038310792000aceb4c7cc43936dc2580869b05ebdee378244932b90279c`
- host: `71919e79066c633ba4548aa04103de53d1e31552893e0cec59e0ba812b5f4d22`
- none: `0984d415001adca8ca8a82f1903d6ca27f318228edcf69e42a7bede8a4a05b6a`

The exact checks are preserved in `cleanup-proof.json`; no blanket prune or
explicit root cleanup mutation occurred. Cached pinned images were retained.
Docker Desktop and persistent/shared `vra-poc00` were not touched. The temporary
recorder captured administrative Docker resource metadata and one PostgreSQL
identity query; it did not implement a PostgreSQL audit observer/parser or
Phase-2 behavior. No collector logs, passwords or bearer material were persisted.

## Required 37-item execution report — current attempt

The database identity/version and exact command/result evidence above bind the
one executed fresh attempt. Entries marked NOT EXECUTED have no command exit
status, SQLSTATE or test count and establish no database property.

| Required item | Actual result |
| --- | --- |
| 1. Docker/Testcontainers endpoint | PASS minimum localhost Ryuk connectivity; expected isolated daemon, no Desktop resources visible; PostgreSQL reached startup |
| 2. PostgreSQL exact version/image | 17.11 / 170011, Linux arm64, reviewed index pin above; DB vra_poc01, system identifier 7691375076119769131 |
| 3. Fresh V1→V4 | EXECUTED test FAILED in fixture before bootstrap/Flyway; exit 1, 1/0/1/0; no migration proof |
| 4. Populated V3→V4 | NOT EXECUTED |
| 5. Flyway validate | NOT EXECUTED |
| 6. Same-DB rerun | NOT EXECUTED |
| 7. V1/V2/V3 hashes | Exact equality to Phase-0 source binding and HEAD; current hashes below |
| 8. Retained data/history | NOT EXECUTED; no accepted prior state was seeded |
| 9. V4 SHA-256 | 45161b8c54f10778a5521cdd23dc0945fadb65364bd8094e3d19d8f79cfdd1e8; unchanged |
| 10. Role bootstrap SHA-256 | 976cf704687e5bf005de8943c706fdd996d801172c112d640c993e0b193407b5; unchanged |
| 11. Six role attributes | NOT EXECUTED; bootstrap never ran; source expectations in earlier section 12 are not catalog evidence |
| 12. Direct memberships | NOT EXECUTED |
| 13. Complete transitive SET graph | NOT EXECUTED, including migrator administrative path and ordinary denied paths |
| 14. Schema CREATE holders | NOT EXECUTED |
| 15. PUBLIC grants | NOT EXECUTED |
| 16. V4 owners | NOT EXECUTED; V4 was not applied |
| 17. Runtime V4 grants | NOT EXECUTED |
| 18. Worker/reconciler new grants | NOT EXECUTED |
| 19. Actual SQL denials/SQLSTATEs | NOT EXECUTED; assertion failure has no PostgreSQL SQLSTATE |
| 20. Constraint negatives | NOT EXECUTED |
| 21. Collector profile/mount | NOT EXECUTED / NOT VERIFIED; no future observer was implemented or run |
| 22. Regression commands | Only selected fresh :migration:integrationTest above; remaining integration/check/build commands stopped |
| 23. Per-suite counts | Current selected migration integration: 1/0/1/0; other current suites NOT EXECUTED; historical units below |
| 24. Total counts | Current attempt 1/0/1/0; historical final unit result 60/0/0/0; no combined PostgreSQL PASS claim |
| 25. Phase-2 path absence | No V5/V6, PostgresAuditTamperObserver, observer.py or new Phase-2 identity-validator work; legacy JdbcReservationIdempotencyRepository preexists and is unchanged |
| 26. V5 present | NO |
| 27. V6 present | NO |
| 28. Phase 2 started | NO |
| 29. git diff --check | PASS, exit 0; separate source/config/report newline/whitespace checks pass; raw historical/current Gradle outputs retain line-2 trailing whitespace |
| 30. Secret scan | Unfiltered pinned Gitleaks 8.30.1 exit 1, one finding on known Git HEAD SHA in artifact-validation.json; reviewed false positive, no credential/secret found; no ignore rule added |
| 31. Tracked modified paths | Same 20 existing Phase-1 test/support paths; all source hashes unchanged |
| 32. Untracked paths | 36 paths, listed below; includes 6 minimum-probe and 10 current-attempt artifacts |
| 33. Staged paths | None |
| 34. Updated report SHA-256 | Supplied with final response, outside its own bytes |
| 35. Unresolved blockers | Fixture config/index identity confusion at line 898; missing Gradle helper POC labels/full independent resource records; all mandatory PostgreSQL proofs outstanding |
| 36. POC-04 | IN PROGRESS / NOT VERIFIED; Phase 1 PREPARED / NOT ACCEPTED, NOT CLOSED; Phase 2 NOT AUTHORIZED |
| 37. S0–S15 | All NOT VERIFIED |

## Rechecked old migration source binding

`validation/poc-04/tooling/source-manifest.json` remains historical Phase-0
source binding. Current file bytes and starting HEAD bytes were hashed and
compared to its recorded entries; all three are exactly equal.

| Migration path | Current/HEAD/Phase-0 recorded SHA-256 | Result |
| --- | --- | --- |
| `backend/migration/src/main/resources/db/migration/V1__inventory_reservation_foundation.sql` | `d804cbe5153b9a904ae0747af55c8295079d4a0c3edb4a38145ab5199b5b59c4` | EQUAL |
| `backend/migration/src/main/resources/db/migration/V2__inventory_reservation_idempotency.sql` | `7bef750b59b7b77e8402f4f42c13c21c4e072092504b9f268bace9b72de8d2bb` | EQUAL |
| `backend/migration/src/main/resources/db/migration/V3__outbox_recovery.sql` | `9edbd15ffe08ee4edafa3f549856ee38a782485c54db9c73e69d4263e5ca26b4` | EQUAL |

Existing Flyway history/checksum retention is still NOT EXECUTED. Source-byte
immutability must not be described as an executed Flyway history proof.

## Suite counts and artifact validation

| Suite/probe | Current attempt tests/skipped/failures/errors | Exit | Historical final result |
| --- | --- | --- | --- |
| Minimum Java Ryuk probe | N/A (not JUnit) | 0 | N/A |
| :migration:integrationTest selected fresh | 1 / 0 / 1 / 0 | 1 | Earlier VM-address attempt also 1/0/1/0, exit 1 |
| :migration:test | NOT EXECUTED in this resume | N/A | 4 / 0 / 0 / 0, exit 0 |
| :runtime:test | NOT EXECUTED in this resume | N/A | 56 / 0 / 0 / 0, exit 0 |
| :runtime:integrationTest | NOT EXECUTED | N/A | No Phase-1 PostgreSQL execution result |
| Full migration integration / populated upgrade | NOT EXECUTED | N/A | No Phase-1 PostgreSQL execution result |
| Normal backend check/build | NOT EXECUTED in this resume | N/A | Earlier assemble exit 0; normal check remains outstanding |

Artifact validation commands/results are recorded in `artifact-validation.json`.
The preliminary scan covered all 54 changed files present before that validation
artifact and report appendix were written, with Gitleaks exit 0 and zero findings.
It parsed 17 JSON, 2 JSONL and 2 JUnit XML files, and found only the two verbatim
Gradle-output line-2 trailing spaces. Final newline and CR checks passed elsewhere.
`git diff --check` exited 0; its tracked-diff scope is complemented by that
separate untracked-file inspection. The first pre-delivery scan covered 55 files and exited 1 with one
`generic-api-key` match on `artifact-validation.json:212`: the known Git HEAD SHA
under the security-branch remote-reference key. Read-only JSON/Git comparison
confirmed this is a commit identifier, not a credential. The finding and raw
scanner exit are preserved in `final-validation.json`; no allowlist or ignore
rule was added. A final repeat includes the resulting 56 files, including this
complete appendix and that evidence. No zero-finding claim is made for the
unfiltered final scan.

`bash -n validation/poc-04/db/collector-test-entrypoint.sh` exited 0.
Ruby/Psych initially rejected the valid `run_labels` YAML alias because the
validator was invoked with `aliases: false` (exit 1, preserved diagnostic in
`artifact-validation.json`). Corrected validation used the same safe parser with
`aliases: true`, exited 0 and printed `YAML parse PASS`; no config was edited.
This validator-option correction did not resume database execution or remediate
the failed fixture.

All 27 implementation/support source hashes match the original report table.
There are no production Java, V1–V3, CI/workflow, frozen authority or Phase-0
closure edits. The preexisting POC-02 repository class remains byte-identical to
HEAD with SHA-256
`7d93cd8c0825b3b4cf9b6979d173167248ddde80eef45a0e6a18f667b2c388cd`.

## New preserved artifacts and SHA-256

| Path | SHA-256 |
| --- | --- |
| `validation/poc-04/evidence/phase1/connectivity-57951433-ef13-4dde-9ef5-3d397b096656/RyukConnectivityProbe.java` | `c20c1c90a7dfbbfdaa6b19a8bba91966ad1c95362b67dc37bf803e78543e7e24` |
| `validation/poc-04/evidence/phase1/connectivity-57951433-ef13-4dde-9ef5-3d397b096656/cleanup-proof.json` | `e5240825141c5cc8af43c69b1f50fc1e315562cb5c5ed073ba9e598cdfa95b91` |
| `validation/poc-04/evidence/phase1/connectivity-57951433-ef13-4dde-9ef5-3d397b096656/connectivity-proof.json` | `0d19e54f918faaf6ee338d0e199beafcf1c47a9c6b635f0e66ae2a40346fecd2` |
| `validation/poc-04/evidence/phase1/connectivity-57951433-ef13-4dde-9ef5-3d397b096656/probe-command.json` | `64d31718431dd19b8590dd361335d15d830b400e03b563f022b55525e8e794bf` |
| `validation/poc-04/evidence/phase1/connectivity-57951433-ef13-4dde-9ef5-3d397b096656/probe-output.txt` | `c92f6fab0503fee8895b40124d993e6f906cbf13bb1a93f764b10e04fe369347` |
| `validation/poc-04/evidence/phase1/connectivity-57951433-ef13-4dde-9ef5-3d397b096656/probe-state.json` | `cc5b35d0c6278a7af1fe4a73dbb174775075ef8100d0fc0c692babbdfb2e5715` |
| `validation/poc-04/evidence/phase1/execution-e0a3d5b9-ab05-453f-b133-39b50828e0b7/TEST-dev.vra.migration.Poc04FoundationMigrationIntegrationTest.xml` | `30736a023d77c7b9ff11c78e16651b5300b2ede035fb80158eedcb0445d0e88d` |
| `validation/poc-04/evidence/phase1/execution-e0a3d5b9-ab05-453f-b133-39b50828e0b7/artifact-validation.json` | `a00eab5f9f8b74f06fcf556e1c1679c2eadf952e0ca7bca9486838d1d117e12b` |
| `validation/poc-04/evidence/phase1/execution-e0a3d5b9-ab05-453f-b133-39b50828e0b7/cleanup-proof.json` | `97ac9bd08884b41e4c3531d04e15096e7d1b0fb515e35bf42dcc43db4c235e69` |
| `validation/poc-04/evidence/phase1/execution-e0a3d5b9-ab05-453f-b133-39b50828e0b7/docker-resource-events.jsonl` | `e206f6bdb728f96562b1d0c67225a4557abeb08d0634eb6561a282f7895ec71c` |
| `validation/poc-04/evidence/phase1/execution-e0a3d5b9-ab05-453f-b133-39b50828e0b7/execution-summary.json` | `975368b8933da29cd140f17aceb54f54dd152e29683dc9faa4b4f535b6395829` |
| `validation/poc-04/evidence/phase1/execution-e0a3d5b9-ab05-453f-b133-39b50828e0b7/fresh-command.json` | `8eb2b409c3b7dea4c55d2f96e53b39c63c4ef1edf79196c15e819e42c349ce92` |
| `validation/poc-04/evidence/phase1/execution-e0a3d5b9-ab05-453f-b133-39b50828e0b7/fresh-gradle-output.txt` | `a3f08bd464b1ddc88516ea343b14498541e0e46a5d7b10fa646c061d9305be4e` |
| `validation/poc-04/evidence/phase1/execution-e0a3d5b9-ab05-453f-b133-39b50828e0b7/image-attestation-defect.json` | `c9b489165e3b09d16993e119fdb64890b86beb55e05e1d127d578f8ec7b64b8f` |
| `validation/poc-04/evidence/phase1/execution-e0a3d5b9-ab05-453f-b133-39b50828e0b7/preflight.json` | `dc863f6ace8ef3935f1436e17959cb59f5f13c9620b9f6b7a66ad2576c6f7b3e` |
| `validation/poc-04/evidence/phase1/execution-e0a3d5b9-ab05-453f-b133-39b50828e0b7/final-validation.json` | `d0abfc8311b68300b192308ccbfb626915c28f6e67f520eab0ed0e68b5ddba7d` |

## Current tracked modified paths (20)

- `backend/migration/src/test/java/dev/vra/migration/MigrationSecurityIntegrationTest.java`
- `backend/migration/src/test/java/dev/vra/migration/OutboxMigrationSecurityIntegrationTest.java`
- `backend/runtime/src/test/java/dev/vra/async/AsyncPermissionIntegrationTest.java`
- `backend/runtime/src/test/java/dev/vra/async/AsyncRoleBootstrap.java`
- `backend/runtime/src/test/java/dev/vra/async/AsyncVisibilityIntegrationTest.java`
- `backend/runtime/src/test/java/dev/vra/async/DeliveryClaimIntegrationTest.java`
- `backend/runtime/src/test/java/dev/vra/async/DeliveryStateIntegrationTest.java`
- `backend/runtime/src/test/java/dev/vra/async/ProducerOutboxIntegrationTest.java`
- `backend/runtime/src/test/java/dev/vra/async/ProjectionInboxIntegrationTest.java`
- `backend/runtime/src/test/java/dev/vra/async/ProjectionRebuildIntegrationTest.java`
- `backend/runtime/src/test/java/dev/vra/async/ReconciliationIntegrationTest.java`
- `backend/runtime/src/test/java/dev/vra/async/RetryReplayIntegrationTest.java`
- `backend/runtime/src/test/java/dev/vra/async/ValidationSimulatorIntegrationTest.java`
- `backend/runtime/src/test/java/dev/vra/async/WorkerProcessCrashIntegrationTest.java`
- `backend/runtime/src/test/java/dev/vra/inventory/IdempotentReservationApplicationServiceIntegrationTest.java`
- `backend/runtime/src/test/java/dev/vra/inventory/ReservationConcurrencyIntegrationTest.java`
- `backend/runtime/src/test/java/dev/vra/inventory/ReservationHttpPostgresIntegrationTest.java`
- `backend/runtime/src/test/java/dev/vra/inventory/ReservationIdempotencyRepositoryIntegrationTest.java`
- `backend/runtime/src/test/java/dev/vra/inventory/ReservationTransactionIntegrationTest.java`
- `backend/runtime/src/test/java/dev/vra/platform/health/HealthProbePostgresIntegrationTest.java`

## Current untracked paths (36)

- `backend/migration/src/main/resources/db/migration/V4__poc04_identity_security_foundation.sql`
- `backend/migration/src/test/java/dev/vra/migration/Poc04FoundationMigrationIntegrationTest.java`
- `validation/poc-04/db/COLLECTOR_TEST.md`
- `validation/poc-04/db/bootstrap-security-roles.sql`
- `validation/poc-04/db/collector-test-entrypoint.sh`
- `validation/poc-04/db/collector-test.compose.yaml`
- `validation/poc-04/db/postgresql-collector-test.conf`
- `validation/poc-04/evidence/phase1/IMPLEMENTATION_REPORT.md`
- `validation/poc-04/evidence/phase1/IMPLEMENTATION_REPORT.previous-4a4116f1.md`
- `validation/poc-04/evidence/phase1/POSTGRES_EXECUTION_PREFLIGHT.json`
- `validation/poc-04/evidence/phase1/POSTGRES_EXECUTION_RESUME_PREFLIGHT.json`
- `validation/poc-04/evidence/phase1/connectivity-57951433-ef13-4dde-9ef5-3d397b096656/RyukConnectivityProbe.java`
- `validation/poc-04/evidence/phase1/connectivity-57951433-ef13-4dde-9ef5-3d397b096656/cleanup-proof.json`
- `validation/poc-04/evidence/phase1/connectivity-57951433-ef13-4dde-9ef5-3d397b096656/connectivity-proof.json`
- `validation/poc-04/evidence/phase1/connectivity-57951433-ef13-4dde-9ef5-3d397b096656/probe-command.json`
- `validation/poc-04/evidence/phase1/connectivity-57951433-ef13-4dde-9ef5-3d397b096656/probe-output.txt`
- `validation/poc-04/evidence/phase1/connectivity-57951433-ef13-4dde-9ef5-3d397b096656/probe-state.json`
- `validation/poc-04/evidence/phase1/execution-304b3976-4ebe-4a61-8bbc-e67a9a40e669/TEST-dev.vra.migration.Poc04FoundationMigrationIntegrationTest.xml`
- `validation/poc-04/evidence/phase1/execution-304b3976-4ebe-4a61-8bbc-e67a9a40e669/cleanup-proof.json`
- `validation/poc-04/evidence/phase1/execution-304b3976-4ebe-4a61-8bbc-e67a9a40e669/docker-resource-events.jsonl`
- `validation/poc-04/evidence/phase1/execution-304b3976-4ebe-4a61-8bbc-e67a9a40e669/execution-summary.json`
- `validation/poc-04/evidence/phase1/execution-304b3976-4ebe-4a61-8bbc-e67a9a40e669/fresh-command.json`
- `validation/poc-04/evidence/phase1/execution-304b3976-4ebe-4a61-8bbc-e67a9a40e669/fresh-gradle-output.txt`
- `validation/poc-04/evidence/phase1/execution-304b3976-4ebe-4a61-8bbc-e67a9a40e669/preflight.json`
- `validation/poc-04/evidence/phase1/execution-304b3976-4ebe-4a61-8bbc-e67a9a40e669/pulled-images.json`
- `validation/poc-04/evidence/phase1/execution-304b3976-4ebe-4a61-8bbc-e67a9a40e669/ryuk-failure-infrastructure.json`
- `validation/poc-04/evidence/phase1/execution-e0a3d5b9-ab05-453f-b133-39b50828e0b7/TEST-dev.vra.migration.Poc04FoundationMigrationIntegrationTest.xml`
- `validation/poc-04/evidence/phase1/execution-e0a3d5b9-ab05-453f-b133-39b50828e0b7/artifact-validation.json`
- `validation/poc-04/evidence/phase1/execution-e0a3d5b9-ab05-453f-b133-39b50828e0b7/cleanup-proof.json`
- `validation/poc-04/evidence/phase1/execution-e0a3d5b9-ab05-453f-b133-39b50828e0b7/docker-resource-events.jsonl`
- `validation/poc-04/evidence/phase1/execution-e0a3d5b9-ab05-453f-b133-39b50828e0b7/execution-summary.json`
- `validation/poc-04/evidence/phase1/execution-e0a3d5b9-ab05-453f-b133-39b50828e0b7/fresh-command.json`
- `validation/poc-04/evidence/phase1/execution-e0a3d5b9-ab05-453f-b133-39b50828e0b7/fresh-gradle-output.txt`
- `validation/poc-04/evidence/phase1/execution-e0a3d5b9-ab05-453f-b133-39b50828e0b7/image-attestation-defect.json`
- `validation/poc-04/evidence/phase1/execution-e0a3d5b9-ab05-453f-b133-39b50828e0b7/preflight.json`
- `validation/poc-04/evidence/phase1/execution-e0a3d5b9-ab05-453f-b133-39b50828e0b7/final-validation.json`

## Staged paths and STOP disposition

None. Branch remains `poc/04-security-auth`; HEAD, origin/main, and
origin/poc/04-security-auth remain
`01256d96d85823740a8d6c3a3f99346afbdbef56`. Only evidence/report files were created
or appended during this resume. No Git mutation occurred.

The infrastructure localhost/Ryuk blocker is resolved for the minimum proof.
The executed fresh fixture defect and resource-label evidence limitations now
block completion. Stop after preserving this failure for review. No source fix,
second database, remaining suite, capability function, V5, V6 or Phase-2 work was
started. Independent Phase-1 review remains NOT READY.


Final delivery validation executed on all 56 changed files: 19 JSON, 2 JSONL and
2 JUnit XML files parsed; all 27 source hashes and all 25 preserved artifact hash
claims matched; the previous report prefix remained byte-identical. Git status
was 20 tracked modified / 36 untracked / 0 staged. `git diff --check` exited 0.
The unfiltered Gitleaks repeat exited 1 with the same single known Git commit
identifier false positive; no secret material was found and no ignore rule was
added. Only the two preserved raw Gradle outputs retain line-2 trailing spaces.
The command/result record for this repeat is
`/tmp/vra-poc04-delivery-validation.json`; the underlying finding/triage is
preserved in the repository's `final-validation.json` above. The report is
included in the final repeat scan before its delivery hash is returned.


# Narrow image-identity remediation request — diagnostic STOP

PHASE 1 CLOSED: **NO**

READY FOR INDEPENDENT PHASE-1 REVIEW: **NO**

The prior report SHA-256
`b772942af3dc367941cd7e2d553ca2f990542964114fdd3002a8c994941cdd65`
is preserved in full as `IMPLEMENTATION_REPORT.previous-b772942a.md` and remains
the exact unchanged prefix of this report. Its failed fresh test remains a failure
(1/0/1/0, exit 1); nothing in this appendix converts it to PASS.

Only the requested read-only Docker semantics diagnosis was executed. No code was
edited and no focused-test retry, bootstrap, Flyway or regression suite ran. This
STOP follows current user section 3:

> If actual Docker 29.5.2 behavior differs: STOP and report exact observations
> before inventing a workaround.

Interpretation: platform-qualified inspection returned the platform manifest
digest in **both** Id and Descriptor. It did not expose the required config digest
as the platform image/config Id. Because the requested inspection mapping does
not independently establish that config identity, report this observation before
implementing any alternate Engine/blob evidence path. The authoritative pins
are unchanged and both observed index/manifest descriptor digests match them.

## Exact isolated-daemon diagnosis

Diagnostic run UUID: `03baf784-dd20-4615-be07-d633535b9b04`.
Endpoint: `unix:///Users/siwakornbundi/.colima/vra-poc04-test/docker.sock`.
Socket override: `/var/run/docker.sock`; host override: `127.0.0.1`.

Preflight reconfirmed daemon ID `14c8ff26-86d1-4c0b-88a6-560be91a65ea`, name
`colima-vra-poc04-test`, Docker `29.5.2`, Linux/aarch64, zero containers and zero
volumes. All four preflight commands exited 0. The isolated endpoint retained the
original three built-in networks. Docker Desktop and persistent `vra-poc00` were
not queried or mutated.

Requested command 1, exit **0**:

```sh
docker image inspect 'docker.io/library/postgres@sha256:d74eeac9a635390a49bc21bd49fccd973de707e2a53a76ac49b552b8712ec46f' --format 'ID={{.Id}} DESCRIPTOR={{json .Descriptor}}'
```

Exact stdout:

```text
ID=sha256:d74eeac9a635390a49bc21bd49fccd973de707e2a53a76ac49b552b8712ec46f DESCRIPTOR={"digest":"sha256:d74eeac9a635390a49bc21bd49fccd973de707e2a53a76ac49b552b8712ec46f","mediaType":"application/vnd.oci.image.index.v1+json","size":10237}
```

Requested command 2, exit **0**:

```sh
docker image inspect --platform linux/arm64/v8 'docker.io/library/postgres@sha256:d74eeac9a635390a49bc21bd49fccd973de707e2a53a76ac49b552b8712ec46f' --format 'ID={{.Id}} DESCRIPTOR={{json .Descriptor}}'
```

Exact stdout:

```text
ID=sha256:86fa57b44a1d38f09970f64ac71eb492f7a9cf94d08b85e1c8f07bb0fc0a1761 DESCRIPTOR={"annotations":{"com.docker.official-images.bashbrew.arch":"arm64v8","org.opencontainers.image.base.digest":"sha256:da496358bd6934d2bd6a563a33176a2e50eff5490c54b4ac6fb051b69fef4071","org.opencontainers.image.base.name":"debian:trixie-slim","org.opencontainers.image.created":"2026-09-19T00:38:22Z","org.opencontainers.image.revision":"2603e26e245e558218728ee14e0a42dcb020dc7f","org.opencontainers.image.source":"https://github.com/docker-library/postgres.git#2603e26e245e558218728ee14e0a42dcb020dc7f:17/trixie","org.opencontainers.image.url":"https://hub.docker.com/_/postgres","org.opencontainers.image.version":"17.11"},"digest":"sha256:86fa57b44a1d38f09970f64ac71eb492f7a9cf94d08b85e1c8f07bb0fc0a1761","mediaType":"application/vnd.oci.image.manifest.v1+json","platform":{"architecture":"arm64","os":"linux","variant":"v8"},"size":3630}
```

Both stderr streams were empty. Full argv, environment, preflight, outputs and
exit statuses are preserved in `docker-image-semantics.json` below.

| Distinct identity/property | Diagnostic observation | Execution proof |
| --- | --- | --- |
| Immutable index reference | Requested exact index; unqualified Id/Descriptor both d74eeac9… | No new running PostgreSQL container |
| linux/arm64/v8 platform | Explicit request; platform descriptor exactly linux/arm64/v8 | No new running-container platform assertion |
| Platform manifest | Qualified Id/Descriptor both 86fa57b4…, exact reviewed manifest | Image-inspection evidence only |
| Config blob | Expected 97432f98… is unchanged; neither inspection exposes it as Id | NOT VERIFIED |
| PostgreSQL 17.11 runtime | No new PostgreSQL execution | NOT EXECUTED; earlier startup evidence remains historical |

The expected config remains
`sha256:97432f980da100ebd3e419711efee84e1e97a966d62c035286a07f239ddb4d9c`.
The qualified Id is
`sha256:86fa57b44a1d38f09970f64ac71eb492f7a9cf94d08b85e1c8f07bb0fc0a1761`.
These are different identities. No assertion was changed to accept one in place
of the other, no tag-only/version-only shortcut was introduced, and no Phase-0
digest was changed.

Read-only installed API inspection found that Docker Java 3.7.1 exposes no
`InspectImageCmd.withPlatform` method. Newer Descriptor/ImageManifestDescriptor
fields lack typed accessors but may be exposed through `DockerObject.getRawValues()`.
No raw Engine request, image export, config-blob retrieval or workaround was
implemented or executed in this STOP turn.

## Resources, status, and validation

No container, volume or network was created by this diagnosis, so new resource
IDs and resource labels are N/A. No cleanup mutation was necessary. Recorded
post-diagnosis daemon/inventory commands all exited 0: zero containers, zero
volumes, and original network IDs unchanged. The current new-resource absence
proof is `post-diagnosis-inventory.json`; previous exact-resource teardown proofs
remain unchanged in the earlier execution directories. No blanket prune or
persistent/shared-resource action occurred.

All mandatory fresh/populated migration, validate/rerun, retained-data/history,
role/catalog/ACL/SET graph, actual credential SQL-denial, constraint-negative,
collector/mount and PostgreSQL regression proofs remain outstanding. This turn
executed **0 JUnit tests**; skipped/failures/errors and suite exit statuses are
N/A because no JUnit/Gradle command was invoked. Earlier failed test counts and
historical unit counts are retained in their original sections.

All 27 implementation/support file hashes remain unchanged. In particular,
`Poc04FoundationMigrationIntegrationTest.java` remains
`125e9cf33055428f4354fe7c0637ba60e2f3857eb3a266c7fa089db7f6c71005`.
V1/V2/V3, V4, bootstrap, frozen plan/spec and Phase-0 image-pins bytes remain
unchanged. V5/V6 and new Phase-2 behavior remain absent.

`git diff --check` exited 0. Separate validation of all 60 changed files parsed
22 JSON, 2 JSONL and 2 preserved JUnit XML files. Final newlines and CR inspection
passed; only the two verbatim historical Gradle outputs retain line-2 trailing
spaces. Pinned Gitleaks 8.30.1 exited 1 with two generic-api-key findings: both are
the actual known Git HEAD SHA under the security-branch remote-reference key,
in the old `artifact-validation.json:212` and new `docker-image-semantics.json:15`.
Read-only comparison to actual Git HEAD confirms both are non-secret commit
identifiers. No credentials were found and no ignore rule was added. The command
and reviewed results are recorded in `/tmp/vra-poc04-image-diagnosis-scan.json`;
the appended report is included in the delivery repeat scan.

Branch remains `poc/04-security-auth`; HEAD and both origin refs remain
`01256d96d85823740a8d6c3a3f99346afbdbef56`. Git status is 20 tracked modified /
40 untracked / 0 staged. The same 20 source test/support paths remain modified;
this turn adds only the four evidence/archive paths below and appends this report.
No Git staging, commit, push, merge, reset, clean, stash or switch occurred.

| New evidence/archive path | SHA-256 |
| --- | --- |
| `validation/poc-04/evidence/phase1/IMPLEMENTATION_REPORT.previous-b772942a.md` | `b772942af3dc367941cd7e2d553ca2f990542964114fdd3002a8c994941cdd65` |
| `validation/poc-04/evidence/phase1/image-identity-diagnosis-03baf784-dd20-4615-be07-d633535b9b04/diagnosis-summary.json` | `0304ec0a747b40a0e7280f137832d57e19a48834dc7b7468cfd896910c210228` |
| `validation/poc-04/evidence/phase1/image-identity-diagnosis-03baf784-dd20-4615-be07-d633535b9b04/docker-image-semantics.json` | `9c3e68cc3b0a55a1f900ac095fd099938bad8a2803d500e72b49cd52f4bb222c` |
| `validation/poc-04/evidence/phase1/image-identity-diagnosis-03baf784-dd20-4615-be07-d633535b9b04/post-diagnosis-inventory.json` | `92f322398a9a59c5518e05fcaf6609efc14b9ac31849f01c02dcf48f2bf7f935` |

Narrow remediation: **NOT APPLIED**, stopped at requested semantics diagnosis.
Focused retry and continuation: **NOT EXECUTED**.
POC-04 and S0–S15: **NOT VERIFIED**.
Phase 2: **NOT AUTHORIZED / NOT STARTED**.

Unresolved point: the actual platform-qualified Id is a manifest identity, not
config identity. The separate authoritative config-blob proof path must be
resolved before the fixture can be corrected without weakening the requested
index/platform/manifest/config checks. This report records the diagnosis and
stops before inventing that path.


# Authorized image provenance correction and focused retry — STOP at PGDATA assertion

PHASE 1 CLOSED: **NO**

READY FOR INDEPENDENT PHASE-1 REVIEW: **NO**

This appendix preserves the previous report bytes as its exact prefix, SHA-256
`93baf961f3370311e1d669e3594ed9c0c2d925ef66d0df70c62f33e61d127a57`, also archived
at `IMPLEMENTATION_REPORT.previous-93baf961.md`. The earlier failed report with
SHA-256 `b772942af3dc367941cd7e2d553ca2f990542964114fdd3002a8c994941cdd65` and all
previous failures/diagnosis artifacts remain unchanged. They are not rewritten
as PASS.

The authorized image-identity correction is implemented in only
`backend/migration/src/test/java/dev/vra/migration/Poc04FoundationMigrationIntegrationTest.java`.
It now checks separate index, selected-platform, platform-manifest and config
linkage identities. The focused retry passed those provenance assertions and
started PostgreSQL, then failed at the existing PGDATA mount-type assertion.
Work stopped immediately under the user's retry rule. No additional source fix,
bootstrap, Flyway, populated upgrade or regression suite followed that failure.

## Narrow source change and immutable evidence

The source change:

- binds expected records to the exact unchanged Phase-0 image-pins file SHA-256;
- selects only recorded linux/arm64/v8 or linux/amd64 records from actual daemon
  OS/architecture, failing on unknown platforms;
- explicitly selects the execution platform at container creation;
- reads the exact immutable index and selected manifest through
  `docker buildx imagetools inspect <digest-reference> --raw`, checks original
  byte length and SHA-256 without normalization, and validates unique exact
  index→platform-manifest→config linkage, schema/media types and recorded sizes;
- verifies the actual running container's platform manifest descriptor through
  Docker Java raw Engine fields using API 1.49;
- retains the exact `server_version_num=170011` assertion before bootstrap and
  emits a safe PASS marker only if that runtime assertion is reached.

It does not compare Docker `.Id` with the config digest. It reads `config.digest`
from the exact immutable platform manifest as expressly authorized in the current
request; no separate config-blob byte download is claimed. No tag/version-only or
either/or identity acceptance was introduced. Frozen pin data, V1–V4, bootstrap,
grants and all other 26 implementation/support source files remain unchanged.

Fixture SHA-256 after correction:
`eedaf64c01778dcc9f1b502a4127bd1363c08a310e3cc131a91a48e6891b1ee8`.
The complete old fixture is preserved in this attempt's
`Poc04FoundationMigrationIntegrationTest.before-image-remediation.java`, SHA-256
`125e9cf33055428f4354fe7c0637ba60e2f3857eb3a266c7fa089db7f6c71005`.
`source-change.patch` records the exact narrow before/after diff. Read-only review
confirmed that every existing mount assertion remained unchanged. The new
canonical candidate source binding is
`7cef9abea5c67e879fb3bf3e0f05ba68bbe83121c02e7363d9b7a0143b5bffe2`, 261 source files.
Phase-0's historical source-manifest bytes were not rewritten.

The raw index evidence has exactly 10237 bytes and hashes to
`sha256:d74eeac9a635390a49bc21bd49fccd973de707e2a53a76ac49b552b8712ec46f`.
The raw arm64 manifest has exactly 3630 bytes and hashes to
`sha256:86fa57b44a1d38f09970f64ac71eb492f7a9cf94d08b85e1c8f07bb0fc0a1761`.
Its config media type is `application/vnd.oci.image.config.v1+json`, config size
is 10161, and config digest is
`sha256:97432f980da100ebd3e419711efee84e1e97a966d62c035286a07f239ddb4d9c`.
Both preflight raw-manifest commands exited 0. Their actual bytes, commands,
lengths, hashes and outputs are preserved in `postgres-index.json`,
`postgres-arm64-manifest.json`, and `manifest-preflight.json`.

Strictly necessary temporary runner support sets API 1.49 at test JVM launch and
appends the existing external SPI jar after project evaluation. The earlier
integration-task classpath assignment had overwritten the early append. No
repository Gradle/build/CI file was changed. Actual current Ryuk helper labels
now contain the required POC/run/source/tool fields. The SPI's returned exec
proxy is not honored by Testcontainers, so exact container IDs and inspections
come from the independently preserved administrative Docker metadata recorder,
not from an unexecuted proxy interception. The recorder discards exec argv before
persistence because bootstrap arguments can contain ephemeral credentials. It
implements no security/audit observer, native-denial parser or append capability.

## Actual focused command, step results, and new failure

Orchestration run UUID: `5e8a333f-5b82-4b7d-b513-0fea93933bab`.
Fixture run UUID: `e384b63e-3450-40ca-aced-3acd4b9421e0`.
Logical PostgreSQL instance UUID: `abf716f6-ea63-5edd-b252-7df0af381b12`.
The configured database was `vra_poc01`; actual cluster system identifier and
runtime version were not captured/asserted before the new fixture failure.
They must not be inferred from image annotations or earlier instances.

Actual daemon preflight: ID `14c8ff26-86d1-4c0b-88a6-560be91a65ea`, name
`colima-vra-poc04-test`, Docker 29.5.2, Linux/aarch64; zero containers/volumes before
execution. Commands exited 0. Endpoint and injected test environment remained:

```text
DOCKER_HOST=unix:///Users/siwakornbundi/.colima/vra-poc04-test/docker.sock
VRA_POC04_DISPOSABLE_TEST_DOCKER_ENDPOINT=unix:///Users/siwakornbundi/.colima/vra-poc04-test/docker.sock
TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock
TESTCONTAINERS_HOST_OVERRIDE=127.0.0.1
```

From `backend`, with exact pinned Temurin 21.0.12.1+1 and the temporary runner:

```text
./gradlew --no-daemon -Dorg.gradle.java.installations.auto-detect=false -Dorg.gradle.java.installations.paths=/var/folders/6p/8cx8_9294s10q4v934jqdn9h0000gn/T/vra-poc04-jdk-x0q7h_vo/jdk-21.0.12.1+1/Contents/Home -I /var/folders/6p/8cx8_9294s10q4v934jqdn9h0000gn/T/vra-poc04-remediation-support-_iyghlz0/runner.init.gradle :migration:integrationTest --tests dev.vra.migration.Poc04FoundationMigrationIntegrationTest.freshV1ThroughV4ValidatesRerunsAndEnforcesPhaseOneBoundary
```

Complete argv, environment, timestamps and exit status are in `fresh-command.json`.
Gradle exited **1**. `:migration:compileTestJava` succeeded; the selected integration
test reported **1 test / 0 skipped / 1 failure / 0 errors** in independently parsed
preserved JUnit XML. No other JUnit/Gradle suite was executed in this resume.

| Sequential proof | Executed result |
| --- | --- |
| Isolated daemon attestation | PASS exact declared transport and independently attested actual daemon ID |
| Ryuk | PASS current Testcontainers initialization; helper observed running with isolated socket, 127.0.0.1 override, mapped port 32774, reviewed pin and POC labels; no new standalone protocol ACK probe |
| Exact index identity | PASS exact immutable execution ref, original raw bytes/SHA-256 and unique index linkage |
| Explicit selected platform | PASS linux/arm64/v8; unknown platforms fail closed |
| Exact platform manifest | PASS raw manifest SHA-256 and actual running-container descriptor digest/media type/size/platform |
| Exact config linkage | PASS immutable manifest config.digest/mediaType/size, distinct from Docker Id |
| PostgreSQL container start | PASS Testcontainers start and actual running daemon state |
| PGDATA mount representation | FAILED existing raw-value Type lookup, before runtime SQL assertions |
| Exact PostgreSQL runtime 17.11 | NOT REACHED |
| Bootstrap | NOT EXECUTED |
| Fresh Flyway V1→V4 | NOT EXECUTED |

The preserved JUnit failure is at `Poc04FoundationMigrationIntegrationTest.java:922`:

```text
actual PGDATA mount type ==> expected: <volume> but was: <null>
```

That existing assertion reads `pgdata.getRawValues().get("Type")`. Independently
captured actual daemon JSON reports the same run-owned mount as `Type=volume`,
with its exact named volume, physical source, target and RW flag. Installed
Docker Java 3.7.1 `InspectContainerResponse.Mount` has no typed Type field/accessor;
the library raw map did not expose it in this executed fixture. This is a new
harness representation discrepancy. It is not evidence that the actual PGDATA
mount was a bind or that image/config provenance failed. Read-only model inspection
and actual mount evidence are in `mount-model-diagnosis.json`; no mount assertion
was changed or weakened after failure.

JUnit stdout independently contains the two successful provenance markers before
failure. It contains no runtime-version PASS marker. Source ordering confirms
that `SHOW server_version_num`, the cluster identity query, all bootstrap scripts
and Flyway follow the failed mount assertion. Their results remain unexecuted.
The failure is a Java assertion, with PostgreSQL SQLSTATE **N/A**.

## Actual resources, labels, and exact cleanup

| Resource | Exact identifier |
| --- | --- |
| PostgreSQL container | `2944e1c95d47c56ddf6fd847b9a86dea7c1c44ef970ee2da82352be58e6dd63d` |
| Ryuk container | `52c4f25d0184b7fa60d13770dd4d8a261b25d308091f06daa58349d13000715e` |
| PGDATA volume | `vra-poc04-e384b63e-3450-40ca-aced-3acd4b9421e0-postgres-authority-0-pgdata` |
| Dedicated network | `a76988190a926e7be5d624d0525feaec5867a49b3405c13e1a15d9ce05fc4bc1` |

Actual full POC-04 labels and creation metadata for all four resources are
preserved in `docker-resource-events.jsonl` and `execution-summary.json`. The
PostgreSQL container/volume/network use fixture namespace e384b63e… and logical
instance abf716f6…; Ryuk uses orchestration namespace 5e8a333f… and helper logical
instance `eac68643-3514-5a3f-999c-d82666612c7b`. All bind the same source HEAD,
new canonical source hash and unchanged tool-manifest hash. Native Testcontainers
session association is `ed3a7e31-1be0-4c1f-988f-88d325c64ed0`.

Actual volume source:
`/var/lib/docker/volumes/vra-poc04-e384b63e-3450-40ca-aced-3acd4b9421e0-postgres-authority-0-pgdata/_data`,
mounted RW at `/var/lib/postgresql/data`. Actual running-container descriptor
exactly matches the reviewed linux/arm64/v8 manifest. Ryuk's sole mount was
`/var/run/docker.sock` to `/var/run/docker.sock` on the isolated daemon. Docker
Desktop and persistent `vra-poc00` were not touched. No collector/audit source was
mounted or read.

Existing bounded fixture cleanup and Testcontainers lifecycle removed all four
resources. Actual exact-ID/name inspections exited **1** with absence responses.
Daemon and inventory commands exited **0**: zero containers, zero volumes and
unchanged original bridge/host/none network IDs. No explicit administrative removal
or blanket prune occurred. `cleanup-proof.json` preserves those commands/results.

## Required 37-item current execution report

| Required item | Actual result |
| --- | --- |
| 1. Docker/Testcontainers endpoint | PASS current isolated daemon/client/Ryuk startup; exact endpoint/ID above |
| 2. PostgreSQL version/image | Image index/arm64-v8 manifest/config linkage PASS; configured DB vra_poc01; runtime 17.11 and actual system identifier NOT REACHED/CAPTURED |
| 3. Fresh V1→V4 | Focused test FAILED before bootstrap/Flyway at existing mount assertion; no migration applied |
| 4. Populated V3→V4 | NOT EXECUTED |
| 5. Flyway validate | NOT EXECUTED |
| 6. Same-DB rerun | NOT EXECUTED |
| 7. V1/V2/V3 hashes | Exact equality to reviewed Phase-0 source binding and HEAD; table below |
| 8. Retained data/history | NOT EXECUTED; no prior state seeded |
| 9. V4 SHA-256 | 45161b8c54f10778a5521cdd23dc0945fadb65364bd8094e3d19d8f79cfdd1e8; unchanged |
| 10. Role bootstrap SHA-256 | 976cf704687e5bf005de8943c706fdd996d801172c112d640c993e0b193407b5; unchanged |
| 11. Six role attributes | NOT EXECUTED; bootstrap never ran; earlier source expectations are not catalog proof |
| 12. Direct memberships | NOT EXECUTED |
| 13. Complete transitive SET graph | NOT EXECUTED |
| 14. Schema CREATE holders | NOT EXECUTED |
| 15. PUBLIC grants | NOT EXECUTED |
| 16. V4 owners | NOT EXECUTED; V4 not applied |
| 17. Runtime V4 grants | NOT EXECUTED |
| 18. Worker/reconciler new grants | NOT EXECUTED; no source grant change |
| 19. SQL denial results/SQLSTATEs | NOT EXECUTED; current Java assertion has SQLSTATE N/A |
| 20. Constraint negatives | NOT EXECUTED |
| 21. Collector profile/mount proof | NOT EXECUTED / NOT VERIFIED; no future observer implemented or run |
| 22. Regression commands | Only focused migration integration command above; remaining suites/check/build stopped |
| 23. Per-suite counts | Selected :migration:integrationTest 1/0/1/0, exit 1; other current suites NOT EXECUTED |
| 24. Total test counts | Current 1/0/1/0; previous final units 60/0/0/0 remain historical |
| 25. Phase-2 paths | No V5/V6, observer.py, PostgresAuditTamperObserver or new Phase-2 identity-validator work; preexisting legacy JdbcReservationIdempotencyRepository unchanged |
| 26. V5 present | NO |
| 27. V6 present | NO |
| 28. Phase 2 started | NO |
| 29. git diff --check | PASS exit 0 before focused retry and final validation; separate source/config whitespace PASS with preserved raw-artifact exceptions below |
| 30. Secret scan | Unfiltered Gitleaks exit 1, two known Git HEAD false positives; reviewed no secrets, no ignore rule added |
| 31. Tracked modified paths | Same 20 Phase-1 test/support paths, unchanged in this resume |
| 32. Untracked paths | 57 total after this report/artifact update; 16 current-attempt artifacts plus archived prior report added; fixture remains untracked and changed narrowly |
| 33. Staged paths | None |
| 34. Updated report SHA-256 | Supplied in final response outside its own bytes |
| 35. Unresolved blocker | Existing PGDATA raw-value Type lookup returns null despite daemon volume evidence; runtime/bootstrap/Flyway and remaining mandatory proofs outstanding |
| 36. POC-04 | IN PROGRESS / NOT VERIFIED; Phase 1 NOT CLOSED / source NOT ACCEPTED; Phase 2 NOT AUTHORIZED |
| 37. S0–S15 | All NOT VERIFIED |

## Rechecked source/hash and artifact validation

| Old migration | Current/HEAD/Phase-0 recorded SHA-256 | Result |
| --- | --- | --- |
| `backend/migration/src/main/resources/db/migration/V1__inventory_reservation_foundation.sql` | `d804cbe5153b9a904ae0747af55c8295079d4a0c3edb4a38145ab5199b5b59c4` | EQUAL |
| `backend/migration/src/main/resources/db/migration/V2__inventory_reservation_idempotency.sql` | `7bef750b59b7b77e8402f4f42c13c21c4e072092504b9f268bace9b72de8d2bb` | EQUAL |
| `backend/migration/src/main/resources/db/migration/V3__outbox_recovery.sql` | `9edbd15ffe08ee4edafa3f549856ee38a782485c54db9c73e69d4263e5ca26b4` | EQUAL |

Source-byte equality does not prove Flyway history/checksum retention; that remains
NOT EXECUTED. Image-pins SHA-256 remains
`340b51d2910f023eef2f7b8a0a4c4065c4baa8d95b2a95c5aaa6c76b888d123a`, and frozen plan/spec
hashes remain accepted. No V1–V4, role bootstrap, production Java or workflow edit
occurred.

The initial post-failure artifact validation covered 76 changed files before its
own artifact was added: 30 JSON, 3 JSONL and 3 preserved JUnit XML parse checks
passed. The complete report/delivery repeat covers 77 files including that
validation artifact. All 26 other implementation/support source hashes matched.
`git diff --check` exited 0, and index inspection found no staged paths. The
validation command and exact raw Gitleaks argv/results are in
`artifact-validation.json` and `/tmp/vra-poc04-image-remediation-validation.json`.

Separate whitespace inspection found only preserved artifacts: three original
Gradle-output line-2 trailing spaces, unified-diff blank context-marker spaces
at patch lines 9/20/157, and absent final newlines in the two exact raw immutable
manifest files. Their bytes are retained because normalization would change the
immutable digests or unified patch syntax. All implementation/support source,
config and report newline/trailing-whitespace/CR checks passed.

The unfiltered pinned Gitleaks scan exited 1 with exactly two known false positives:
Git HEAD SHA under the security-branch remote-ref key in old
`artifact-validation.json:212` and `docker-image-semantics.json:15`. Both were
compared read-only to actual Git HEAD. No passwords, credentials, tokens, signed
URLs, private keys or sensitive collector logs were found. No ignore rule was
added. Exec argv was discarded before resource-event persistence.

## Current-attempt artifacts and SHA-256

| Path | SHA-256 |
| --- | --- |
| `validation/poc-04/evidence/phase1/IMPLEMENTATION_REPORT.previous-93baf961.md` | `93baf961f3370311e1d669e3594ed9c0c2d925ef66d0df70c62f33e61d127a57` |
| `validation/poc-04/evidence/phase1/image-remediation-5e8a333f-5b82-4b7d-b513-0fea93933bab/Poc04FoundationMigrationIntegrationTest.before-image-remediation.java` | `125e9cf33055428f4354fe7c0637ba60e2f3857eb3a266c7fa089db7f6c71005` |
| `validation/poc-04/evidence/phase1/image-remediation-5e8a333f-5b82-4b7d-b513-0fea93933bab/TEST-dev.vra.migration.Poc04FoundationMigrationIntegrationTest.xml` | `045add229586d974e16fa2b55f06c1472d71493f03b749c0ecfb5a033c673a77` |
| `validation/poc-04/evidence/phase1/image-remediation-5e8a333f-5b82-4b7d-b513-0fea93933bab/artifact-validation.json` | `75350dfe2de8ca71eec89a1fbd7444819e1ee565e742b8169d9a43a61102f9ea` |
| `validation/poc-04/evidence/phase1/image-remediation-5e8a333f-5b82-4b7d-b513-0fea93933bab/cleanup-proof.json` | `50f21c05209c91be8028ffc6a49be4937a48eaccddfd1f99517dd95bff3ff232` |
| `validation/poc-04/evidence/phase1/image-remediation-5e8a333f-5b82-4b7d-b513-0fea93933bab/docker-resource-events.jsonl` | `0e743fb5f1dcb6e54e7bd97f379ecb60f67f9c382c476687f9ec0cc04a7b086c` |
| `validation/poc-04/evidence/phase1/image-remediation-5e8a333f-5b82-4b7d-b513-0fea93933bab/execution-summary.json` | `db05f146b809121ee5a17fbf5c4b157d39ec35183d243514e0e931ea1da4be30` |
| `validation/poc-04/evidence/phase1/image-remediation-5e8a333f-5b82-4b7d-b513-0fea93933bab/fresh-command.json` | `142bc74a0cdb137a04efe37172b72614d8484832abc90d2e7bbc8ad5d17e6e74` |
| `validation/poc-04/evidence/phase1/image-remediation-5e8a333f-5b82-4b7d-b513-0fea93933bab/fresh-gradle-output.txt` | `53b0e9fb16288c023a6b7cdeaffc4fd0d72328548d2fc967558a3fe6a815a9cc` |
| `validation/poc-04/evidence/phase1/image-remediation-5e8a333f-5b82-4b7d-b513-0fea93933bab/manifest-preflight.json` | `b1ab06561d193b66f99650ae593805a70e29fb93aa820c4dce80d2d1af1bcf0d` |
| `validation/poc-04/evidence/phase1/image-remediation-5e8a333f-5b82-4b7d-b513-0fea93933bab/mount-model-diagnosis.json` | `7f2143751dea96ceedb4638fd039efb916c9cf85fd4bcb48e0bd9c3765fd4b17` |
| `validation/poc-04/evidence/phase1/image-remediation-5e8a333f-5b82-4b7d-b513-0fea93933bab/postgres-arm64-manifest.json` | `86fa57b44a1d38f09970f64ac71eb492f7a9cf94d08b85e1c8f07bb0fc0a1761` |
| `validation/poc-04/evidence/phase1/image-remediation-5e8a333f-5b82-4b7d-b513-0fea93933bab/postgres-index.json` | `d74eeac9a635390a49bc21bd49fccd973de707e2a53a76ac49b552b8712ec46f` |
| `validation/poc-04/evidence/phase1/image-remediation-5e8a333f-5b82-4b7d-b513-0fea93933bab/resource-recorder.py` | `f68ba9d804b59f0e4c955c290e8e23b9dd30eec2871d63c08f7dbbed3cc74af8` |
| `validation/poc-04/evidence/phase1/image-remediation-5e8a333f-5b82-4b7d-b513-0fea93933bab/runner.init.gradle` | `1255ee9276b75e8f33819c911dc63aad7bedfcead64adb14f36a91a4f5657b34` |
| `validation/poc-04/evidence/phase1/image-remediation-5e8a333f-5b82-4b7d-b513-0fea93933bab/source-change.patch` | `ecb4a676c89a583c96e36546a9292300f55589e77aba3c1231c4e2f78a3ed7f1` |
| `validation/poc-04/evidence/phase1/image-remediation-5e8a333f-5b82-4b7d-b513-0fea93933bab/source-review.json` | `e9a2e2d70c50305fc1d9283bfc937698e6f283487c4ef4dc05dbfdd1ac072fc5` |

## Current tracked modified paths (20)

- `backend/migration/src/test/java/dev/vra/migration/MigrationSecurityIntegrationTest.java`
- `backend/migration/src/test/java/dev/vra/migration/OutboxMigrationSecurityIntegrationTest.java`
- `backend/runtime/src/test/java/dev/vra/async/AsyncPermissionIntegrationTest.java`
- `backend/runtime/src/test/java/dev/vra/async/AsyncRoleBootstrap.java`
- `backend/runtime/src/test/java/dev/vra/async/AsyncVisibilityIntegrationTest.java`
- `backend/runtime/src/test/java/dev/vra/async/DeliveryClaimIntegrationTest.java`
- `backend/runtime/src/test/java/dev/vra/async/DeliveryStateIntegrationTest.java`
- `backend/runtime/src/test/java/dev/vra/async/ProducerOutboxIntegrationTest.java`
- `backend/runtime/src/test/java/dev/vra/async/ProjectionInboxIntegrationTest.java`
- `backend/runtime/src/test/java/dev/vra/async/ProjectionRebuildIntegrationTest.java`
- `backend/runtime/src/test/java/dev/vra/async/ReconciliationIntegrationTest.java`
- `backend/runtime/src/test/java/dev/vra/async/RetryReplayIntegrationTest.java`
- `backend/runtime/src/test/java/dev/vra/async/ValidationSimulatorIntegrationTest.java`
- `backend/runtime/src/test/java/dev/vra/async/WorkerProcessCrashIntegrationTest.java`
- `backend/runtime/src/test/java/dev/vra/inventory/IdempotentReservationApplicationServiceIntegrationTest.java`
- `backend/runtime/src/test/java/dev/vra/inventory/ReservationConcurrencyIntegrationTest.java`
- `backend/runtime/src/test/java/dev/vra/inventory/ReservationHttpPostgresIntegrationTest.java`
- `backend/runtime/src/test/java/dev/vra/inventory/ReservationIdempotencyRepositoryIntegrationTest.java`
- `backend/runtime/src/test/java/dev/vra/inventory/ReservationTransactionIntegrationTest.java`
- `backend/runtime/src/test/java/dev/vra/platform/health/HealthProbePostgresIntegrationTest.java`

## Current untracked paths (57)

- `backend/migration/src/main/resources/db/migration/V4__poc04_identity_security_foundation.sql`
- `backend/migration/src/test/java/dev/vra/migration/Poc04FoundationMigrationIntegrationTest.java`
- `validation/poc-04/db/COLLECTOR_TEST.md`
- `validation/poc-04/db/bootstrap-security-roles.sql`
- `validation/poc-04/db/collector-test-entrypoint.sh`
- `validation/poc-04/db/collector-test.compose.yaml`
- `validation/poc-04/db/postgresql-collector-test.conf`
- `validation/poc-04/evidence/phase1/IMPLEMENTATION_REPORT.md`
- `validation/poc-04/evidence/phase1/IMPLEMENTATION_REPORT.previous-4a4116f1.md`
- `validation/poc-04/evidence/phase1/IMPLEMENTATION_REPORT.previous-93baf961.md`
- `validation/poc-04/evidence/phase1/IMPLEMENTATION_REPORT.previous-b772942a.md`
- `validation/poc-04/evidence/phase1/POSTGRES_EXECUTION_PREFLIGHT.json`
- `validation/poc-04/evidence/phase1/POSTGRES_EXECUTION_RESUME_PREFLIGHT.json`
- `validation/poc-04/evidence/phase1/connectivity-57951433-ef13-4dde-9ef5-3d397b096656/RyukConnectivityProbe.java`
- `validation/poc-04/evidence/phase1/connectivity-57951433-ef13-4dde-9ef5-3d397b096656/cleanup-proof.json`
- `validation/poc-04/evidence/phase1/connectivity-57951433-ef13-4dde-9ef5-3d397b096656/connectivity-proof.json`
- `validation/poc-04/evidence/phase1/connectivity-57951433-ef13-4dde-9ef5-3d397b096656/probe-command.json`
- `validation/poc-04/evidence/phase1/connectivity-57951433-ef13-4dde-9ef5-3d397b096656/probe-output.txt`
- `validation/poc-04/evidence/phase1/connectivity-57951433-ef13-4dde-9ef5-3d397b096656/probe-state.json`
- `validation/poc-04/evidence/phase1/execution-304b3976-4ebe-4a61-8bbc-e67a9a40e669/TEST-dev.vra.migration.Poc04FoundationMigrationIntegrationTest.xml`
- `validation/poc-04/evidence/phase1/execution-304b3976-4ebe-4a61-8bbc-e67a9a40e669/cleanup-proof.json`
- `validation/poc-04/evidence/phase1/execution-304b3976-4ebe-4a61-8bbc-e67a9a40e669/docker-resource-events.jsonl`
- `validation/poc-04/evidence/phase1/execution-304b3976-4ebe-4a61-8bbc-e67a9a40e669/execution-summary.json`
- `validation/poc-04/evidence/phase1/execution-304b3976-4ebe-4a61-8bbc-e67a9a40e669/fresh-command.json`
- `validation/poc-04/evidence/phase1/execution-304b3976-4ebe-4a61-8bbc-e67a9a40e669/fresh-gradle-output.txt`
- `validation/poc-04/evidence/phase1/execution-304b3976-4ebe-4a61-8bbc-e67a9a40e669/preflight.json`
- `validation/poc-04/evidence/phase1/execution-304b3976-4ebe-4a61-8bbc-e67a9a40e669/pulled-images.json`
- `validation/poc-04/evidence/phase1/execution-304b3976-4ebe-4a61-8bbc-e67a9a40e669/ryuk-failure-infrastructure.json`
- `validation/poc-04/evidence/phase1/execution-e0a3d5b9-ab05-453f-b133-39b50828e0b7/TEST-dev.vra.migration.Poc04FoundationMigrationIntegrationTest.xml`
- `validation/poc-04/evidence/phase1/execution-e0a3d5b9-ab05-453f-b133-39b50828e0b7/artifact-validation.json`
- `validation/poc-04/evidence/phase1/execution-e0a3d5b9-ab05-453f-b133-39b50828e0b7/cleanup-proof.json`
- `validation/poc-04/evidence/phase1/execution-e0a3d5b9-ab05-453f-b133-39b50828e0b7/docker-resource-events.jsonl`
- `validation/poc-04/evidence/phase1/execution-e0a3d5b9-ab05-453f-b133-39b50828e0b7/execution-summary.json`
- `validation/poc-04/evidence/phase1/execution-e0a3d5b9-ab05-453f-b133-39b50828e0b7/final-validation.json`
- `validation/poc-04/evidence/phase1/execution-e0a3d5b9-ab05-453f-b133-39b50828e0b7/fresh-command.json`
- `validation/poc-04/evidence/phase1/execution-e0a3d5b9-ab05-453f-b133-39b50828e0b7/fresh-gradle-output.txt`
- `validation/poc-04/evidence/phase1/execution-e0a3d5b9-ab05-453f-b133-39b50828e0b7/image-attestation-defect.json`
- `validation/poc-04/evidence/phase1/execution-e0a3d5b9-ab05-453f-b133-39b50828e0b7/preflight.json`
- `validation/poc-04/evidence/phase1/image-identity-diagnosis-03baf784-dd20-4615-be07-d633535b9b04/diagnosis-summary.json`
- `validation/poc-04/evidence/phase1/image-identity-diagnosis-03baf784-dd20-4615-be07-d633535b9b04/docker-image-semantics.json`
- `validation/poc-04/evidence/phase1/image-identity-diagnosis-03baf784-dd20-4615-be07-d633535b9b04/post-diagnosis-inventory.json`
- `validation/poc-04/evidence/phase1/image-remediation-5e8a333f-5b82-4b7d-b513-0fea93933bab/Poc04FoundationMigrationIntegrationTest.before-image-remediation.java`
- `validation/poc-04/evidence/phase1/image-remediation-5e8a333f-5b82-4b7d-b513-0fea93933bab/TEST-dev.vra.migration.Poc04FoundationMigrationIntegrationTest.xml`
- `validation/poc-04/evidence/phase1/image-remediation-5e8a333f-5b82-4b7d-b513-0fea93933bab/artifact-validation.json`
- `validation/poc-04/evidence/phase1/image-remediation-5e8a333f-5b82-4b7d-b513-0fea93933bab/cleanup-proof.json`
- `validation/poc-04/evidence/phase1/image-remediation-5e8a333f-5b82-4b7d-b513-0fea93933bab/docker-resource-events.jsonl`
- `validation/poc-04/evidence/phase1/image-remediation-5e8a333f-5b82-4b7d-b513-0fea93933bab/execution-summary.json`
- `validation/poc-04/evidence/phase1/image-remediation-5e8a333f-5b82-4b7d-b513-0fea93933bab/fresh-command.json`
- `validation/poc-04/evidence/phase1/image-remediation-5e8a333f-5b82-4b7d-b513-0fea93933bab/fresh-gradle-output.txt`
- `validation/poc-04/evidence/phase1/image-remediation-5e8a333f-5b82-4b7d-b513-0fea93933bab/manifest-preflight.json`
- `validation/poc-04/evidence/phase1/image-remediation-5e8a333f-5b82-4b7d-b513-0fea93933bab/mount-model-diagnosis.json`
- `validation/poc-04/evidence/phase1/image-remediation-5e8a333f-5b82-4b7d-b513-0fea93933bab/postgres-arm64-manifest.json`
- `validation/poc-04/evidence/phase1/image-remediation-5e8a333f-5b82-4b7d-b513-0fea93933bab/postgres-index.json`
- `validation/poc-04/evidence/phase1/image-remediation-5e8a333f-5b82-4b7d-b513-0fea93933bab/resource-recorder.py`
- `validation/poc-04/evidence/phase1/image-remediation-5e8a333f-5b82-4b7d-b513-0fea93933bab/runner.init.gradle`
- `validation/poc-04/evidence/phase1/image-remediation-5e8a333f-5b82-4b7d-b513-0fea93933bab/source-change.patch`
- `validation/poc-04/evidence/phase1/image-remediation-5e8a333f-5b82-4b7d-b513-0fea93933bab/source-review.json`

## Staged paths and STOP disposition

None. Branch remains `poc/04-security-auth`; HEAD, origin/main and
origin/poc/04-security-auth remain
`01256d96d85823740a8d6c3a3f99346afbdbef56`.
No Git mutation occurred.

The authorized image-identity correction is prepared and its A–D checks passed
in real execution. Full runtime/migration acceptance remains blocked by the newly
executed preexisting mount-representation assertion. No further repair was made
under the user's STOP rule. The failed evidence is preserved for review; Phase 2
remains NOT AUTHORIZED and all verification statuses remain unchanged.


## PGDATA mount remediation and PostgreSQL execution — 89fcfa4d-21ba-4401-8ab9-8e0518da17d8
PHASE 1 CLOSED: **NO**

READY FOR INDEPENDENT PHASE-1 REVIEW: **NO**

POC-04: **NOT VERIFIED**. S0–S15: **NOT VERIFIED**. Phase 2: **NOT AUTHORIZED / NOT STARTED**.
The narrow mount evidence correction passed. Fresh and populated migration, validation/rerun, retained-data, catalog/role/grant and negative SQL proofs passed. Execution stopped at the required runtime PostgreSQL regression suite: Docker exhausted the isolated daemon’s predefined network address pools. No schema/grant/source remediation or test retry followed that failure. Normal check/build completion and collector OS/mount execution remain unverified.
### Historical evidence and exact remediation
The entire prior report prefix remains byte-identical and is archived as `IMPLEMENTATION_REPORT.previous-e5df43af.md`, SHA-256 `e5df43afd289c8fc9a87cfe0bc25fdf182d6195ea8f8f6bfedf160df4caf0e3e`. The earlier PGDATA raw-value lookup failure remains **FAIL**, and its runtime-version/bootstrap/Flyway steps remain **NOT EXECUTED in that earlier attempt**. The new PASS applies only to this distinct execution. All older Docker/Ryuk/image failures and diagnosis evidence remain preserved.
The exact pre-edit assertion was `assertEquals("volume", pgdata.getRawValues().get("Type"), "actual PGDATA mount type");`. docker-java 3.7.1 does not model nested Mount.Type. Only `backend/migration/src/test/java/dev/vra/migration/Poc04FoundationMigrationIntegrationTest.java` was edited by this remediation: the unsupported lookup was replaced by consumption of the existing administrative safe daemon recorder’s atomically published running-container projection. The typed exact single-mount Name/Destination/Source/RW checks, separately inspected fresh volume labels/creation metadata/mountpoint, run ownership and exact teardown checks remain. WatchService uses a bounded deadline without a sleep correctness test. No Docker implementation/library upgrade, reflection change to the fixture, pin change, migration/grant/bootstrap change, or Phase-2 implementation was made.
An out-of-scope workspace difference was discovered and preserved: `AsyncVisibilityIntegrationTest.java` has exactly 24 leading spaces before `package`, current SHA-256 `089d7f92a2b956195a20b89f393e9a4cea856284abff16558575b8b2df23883d`; removing only those bytes reproduces the prior `31e172e295b204e2070f984517bcba27b2f9a098174b6e64c3cfaf1c2d84c506`. This agent did not edit that file. The current full source binding includes the preserved bytes. The other 25 Phase-1 implementation/support files match their previously recorded hashes.
### Required execution results
| # | Required item / new result and exact evidence |
|---|---|
| 1 | Docker/Testcontainers endpoint PASS: `unix:///Users/siwakornbundi/.colima/vra-poc04-test/docker.sock`; daemon `14c8ff26-86d1-4c0b-88a6-560be91a65ea`, `colima-vra-poc04-test`, server 29.5.2 linux/aarch64. Initial 0 containers/0 volumes; original 3 networks. Ryuk enabled and successful through host override `127.0.0.1`, socket override `/var/run/docker.sock`. `preflight.json`, safe projections and JUnit outputs record the actual endpoint/IDs/socket mount; Docker Desktop and persistent vra-poc00 were not accessed. |
| 2 | PostgreSQL 17.11 / server_version_num 170011, linux/arm64/v8. Exact immutable index `sha256:d74eeac9a635390a49bc21bd49fccd973de707e2a53a76ac49b552b8712ec46f`; selected manifest `sha256:86fa57b44a1d38f09970f64ac71eb492f7a9cf94d08b85e1c8f07bb0fc0a1761`; raw immutable manifest config linkage `sha256:97432f980da100ebd3e419711efee84e1e97a966d62c035286a07f239ddb4d9c`. Index, platform, manifest, config linkage and runtime version are separate assertions. `fresh-command.json`, focused JUnit XML, `daemon-inspect/` and `database-proof.json`. |
| 3 | Fresh V1→V4 PASS; focused command exit 0, 1/0/0/0. Independent fresh capture also PASS: migrationsExecuted=4; V4 exactly once; all 17 foundation tables/catalog assertions passed. `TEST-dev.vra.migration.Poc04FoundationMigrationIntegrationTest.xml`, `database-proof.json`. |
| 4 | Populated V3→V4 PASS; focused command exit 0, 1/0/0/0. Independent capture: V1–V3 migrationsExecuted=3; representative seed; V4 migrationsExecuted=1. `TEST-populated-Poc04FoundationMigrationIntegrationTest.xml`, `database-proof.json`. |
| 5 | Flyway validate PASS on fresh, targeted V3 and upgraded V4; same-DB post-rerun validate PASS. Command/evidence identities recorded in `database-proof-command.json` / `database-proof.json`. |
| 6 | Same-database rerun PASS on fresh and populated: migrationsExecuted=0; all table row digests, history and catalog/ACL unchanged. |
| 7 | V1/V2/V3 source SHA-256 exactly equals reviewed Phase-0 source-manifest and HEAD bytes. Executed retained Flyway checksums V1=-290113401, V2=-1049090241, V3=-70525330. Complete prior history including installed_on/execution_time retained on upgrade; no repair. |
| 8 | Retained data PASS: Inventory balance/reservation each 1 row; V2 idempotency 3 rows (transient, success, rejection); outbox event/delivery/history, inbox, projection, reconciliation case/history each 1 row. Exact BEFORE/AFTER/final SHA-256 row digests equal; prior table/column ACL/owner catalog equal. `database-proof.json` contains both snapshots and complete history. |
| 9 | V4 SHA-256 `45161b8c54f10778a5521cdd23dc0945fadb65364bd8094e3d19d8f79cfdd1e8`; unchanged by remediation. |
| 10 | Role bootstrap SHA-256 `976cf704687e5bf005de8943c706fdd996d801172c112d640c993e0b193407b5`; unchanged. Actual bootstrap convergence and unexpected drift rejection focused test PASS 1/0/0/0. |
| 11 | All six exact role attributes PASS. Executors NOLOGIN; three TEST fixtures LOGIN. All six NOINHERIT NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS. Actual role catalog rows below and in `database-proof.json`. |
| 12 | Direct membership graph PASS: migrator→owner and owner→async/sync/security/telemetry executors only; each membership SET=true INHERIT=false ADMIN=false. No ordinary/helper executor memberships. |
| 13 | Complete transitive SET graph PASS: migrator→owner and all four executors; owner→all four executors. No ordinary runtime/worker/reconciler/operator/rebuilder/observer/TEST helper SET paths. Actual LOGIN credential SET ROLE denials are also exported. |
| 14 | Final schema CREATE holders exactly postgres and vra_owner. Executors and ordinary workloads denied CREATE; schema USAGE only where granted. Actual catalog and SQL denial evidence retained. |
| 15 | PUBLIC table grants=[]; sequence grants=[]; trusted vra schema grants=[] in both captured databases. Accepted V1–V3 ACLs retained. |
| 16 | All 17 authoritative V4 tables owned by vra_owner; no ordinary ownership. Full column types/ACLs, PK/FK/CHECK/UNIQUE and index definitions retained in `full_v1_v4_catalog`; fixture validates expected definitions. |
| 17 | Runtime V4 grants PASS: SELECT-only foundation authority/session/factor/staff/proposal/approval tables; poc account/org SELECT plus UPDATE(label,version); protected audit no rights; security_event limited safe column SELECT and approved APP-column INSERT excluding source_kind/provenance INSERT; webhook receipt SELECT/INSERT plus UPDATE(status), target SELECT plus UPDATE(state,version). Full table/column ACLs below/in capture. V2 residual direct runtime INSERT/UPDATE unchanged. |
| 18 | Worker/reconciler/helper new V4 business/audit table grants NONE. No executor table capabilities activated; no Phase-2 function grants. |
| 19 | SQL denials PASS: runtime/workers/reconciler/all TEST LOGIN helpers cannot SET any executor, DDL or mutate roles; executors cannot CREATE. Ordinary credentials used directly; NOLOGIN owner/executors use accepted administrative migrator SET path. Exported deny SQLSTATE 42501, transaction rollback and unchanged row digests; ordinary bootstrap/drift rejections P0001 in focused bootstrap test. 71 exported denial cases per captured DB; 142 total. |
| 20 | Constraint negatives PASS: CHECK 23514 (invalid state/operation, negative version/generation, wrong verifier/digest lengths, actor/provenance shape); UNIQUE 23505 (issuer+subject, membership/grant authority tuples); FK 23503. Forged POSTGRES_ACL source column INSERT denied 42501; APP-column forged ACL-shaped event rejected 23514. Representative exported cases explicitly rollback and prove unchanged authoritative digests; complete fixture negative assertions also passed. |
| 21 | Collector/profile preparation static test PASS; YAML parse PASS; role catalogs show no pg_read_server_files/pg_monitor membership or log_statement SET privilege. Actual collector volume/OS/read-only mount execution NOT VERIFIED / NOT EXECUTED after regression failure. No observer/parser service was implemented or started; no raw collector logs exported. |
| 22 | Exact regression command in `regression-command.json`: Gradle migration:test, migration:integrationTest, runtime:test, runtime:integrationTest, check, build with pinned JDK and external TEST support. Exit 1 at runtime integrationTest; check/build NOT COMPLETED. Three focused commands and independent capture command retained separately. |
| 23 | Per-suite counts below. Migration unit 4/0/0/0; migration integration 18/0/0/0; runtime unit 56/0/0/0; runtime integration 102/0/38/0. Every failed XML preserved. No skipped suite described as PASS. |
| 24 | Regression totals 180 tests / 0 skipped / 38 failures / 0 errors; including the three focused reruns, 183 test executions / 0 skipped / 38 failures / 0 errors. Independent capture has no JUnit test count; its 142 exported SQL denials are separate evidence. |
| 25 | New Phase-2 paths/behavior absent. PostgresAuditTamperObserver and observer.py absent; no new identity validator work. Existing POC-02 JdbcReservationIdempotencyRepository is present unchanged at HEAD (not a new Phase-2 implementation); SHA-256 `7d93cd8c0825b3b4cf9b6979d173167248ddde80eef45a0e6a18f667b2c388cd`. |
| 26 | V5 present NO. |
| 27 | V6 present NO. |
| 28 | Phase 2 started NO. |
| 29 | git diff --check exit 0. Separate source/config/report trailing-whitespace/final-newline checks PASS. Exact historical raw manifest bytes, raw Gradle/JUnit output and valid unified-diff context whitespace preserved with explicit classifications; evidence bytes were not normalized to hide history. |
| 30 | Changed-file Gitleaks 8.30.1 scan: unfiltered exit 1, exactly 3 reviewed false positives matching the known Git HEAD SHA-1; zero actual secrets detected, zero ignore rules. `artifact-validation.json` preserves rule/path/line and scanner command. No password/secret/raw collector log exported. |
| 31 | 20 tracked modified paths; exact list in `final-git-inventory.json` and file table below. Only the authorized fixture source was edited by this remediation. |
| 32 | 169 untracked paths (current Phase-1 source/evidence only); exact list in `final-git-inventory.json` and file table below. |
| 33 | Staged paths NONE. No Git mutation performed. |
| 34 | Updated report SHA-256 is returned in the final response after the append and final checks; the report does not embed a circular self-hash. Every other modified/created file hash is listed below. |
| 35 | Unresolved blockers: runtime PostgreSQL regressions FAILED from isolated Docker network/address-pool exhaustion (7 root initialization errors + 31 cascades); temporary external regression support left exact per-fixture networks/volumes live until final cleanup. No executed SQL/schema defect demonstrated by these failures. Collector OS/mount proof and normal backend check/build completion remain NOT VERIFIED. No remediation/retry after failure. Cleanup PASS: all 42 recorded container IDs, 36 volume names, 36 network IDs absent; final 0 containers/0 volumes and original 3 networks. Exact IDs/labels/inspection/remove commands/exit statuses in `cleanup-proof.json`. Two failed metadata cleanup lookups stopped before mutations and are separately preserved; successful cleanup rechecked exact captured/current labels before each run-owned resource removal. |
| 36 | POC-04 NOT VERIFIED. |
| 37 | S0–S15 all NOT VERIFIED; no gate-upgrade claim. |

### Commands and counts
All Gradle commands ran in `backend` with `JAVA_HOME` set to the reviewed Temurin 21.0.12.1+1 and the isolated environment recorded in their command JSON. No password is present in command artifacts.

**fresh**, exit 0: `./gradlew --no-daemon -Dorg.gradle.java.installations.auto-detect=false -Dorg.gradle.java.installations.paths=/var/folders/6p/8cx8_9294s10q4v934jqdn9h0000gn/T/vra-poc04-jdk-x0q7h_vo/jdk-21.0.12.1+1/Contents/Home -I /var/folders/6p/8cx8_9294s10q4v934jqdn9h0000gn/T/vra-poc04-mount-support-_1rpppjy/runner.init.gradle :migration:integrationTest --tests dev.vra.migration.Poc04FoundationMigrationIntegrationTest.freshV1ThroughV4ValidatesRerunsAndEnforcesPhaseOneBoundary`. Evidence: `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/fresh-command.json`.

**populated**, exit 0: `./gradlew --no-daemon -Dorg.gradle.java.installations.auto-detect=false -Dorg.gradle.java.installations.paths=/var/folders/6p/8cx8_9294s10q4v934jqdn9h0000gn/T/vra-poc04-jdk-x0q7h_vo/jdk-21.0.12.1+1/Contents/Home -I /var/folders/6p/8cx8_9294s10q4v934jqdn9h0000gn/T/vra-poc04-mount-support-_1rpppjy/runner.init.gradle :migration:integrationTest --tests dev.vra.migration.Poc04FoundationMigrationIntegrationTest.populatedV3UpgradeRetainsEveryPriorTableAndFlywayHistory`. Evidence: `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/populated-command.json`.

**bootstrap**, exit 0: `./gradlew --no-daemon -Dorg.gradle.java.installations.auto-detect=false -Dorg.gradle.java.installations.paths=/var/folders/6p/8cx8_9294s10q4v934jqdn9h0000gn/T/vra-poc04-jdk-x0q7h_vo/jdk-21.0.12.1+1/Contents/Home -I /var/folders/6p/8cx8_9294s10q4v934jqdn9h0000gn/T/vra-poc04-mount-support-_1rpppjy/runner.init.gradle :migration:integrationTest --tests dev.vra.migration.Poc04FoundationMigrationIntegrationTest.administratorBootstrapConvergesAndRejectsUnexpectedMembershipDrift`. Evidence: `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/bootstrap-command.json`.

**database-proof**, exit 0: `/var/folders/6p/8cx8_9294s10q4v934jqdn9h0000gn/T/vra-poc04-jdk-x0q7h_vo/jdk-21.0.12.1+1/Contents/Home/bin/java -Dapi.version=1.49 -Djava.net.preferIPv4Stack=true --class-path /Users/siwakornbundi/Project/VRA-Fastform/backend/migration/build/classes/java/test:/Users/siwakornbundi/Project/VRA-Fastform/backend/migration/build/resources/test:/Users/siwakornbundi/Project/VRA-Fastform/backend/migration/build/classes/java/main:/Users/siwakornbundi/Project/VRA-Fastform/backend/migration/build/resources/main:/Users/siwakornbundi/.gradle/caches/modules-2/files-2.1/org.flywaydb/flyway-database-postgresql/12.4.0/90fc8dc92bef70c143c4112ad6658f29eea437fd/flyway-database-postgresql-12.4.0.jar:/Users/siwakornbundi/.gradle/caches/modules-2/files-2.1/org.flywaydb/flyway-core/12.4.0/8dfd90404bb9b6fd4727c67ccbe9a7dfa20c0e53/flyway-core-12.4.0.jar:/Users/siwakornbundi/.gradle/caches/modules-2/files-2.1/org.postgresql/postgresql/42.7.13/a6e1bd21b412d6ffb3df23cd13d507bc2cc9e37d/postgresql-42.7.13.jar:/Users/siwakornbundi/.gradle/caches/modules-2/files-2.1/org.junit.platform/junit-platform-launcher/6.0.3/4a721cdd640ebdaa4c6850f5eee0b0377cf698b0/junit-platform-launcher-6.0.3.jar:/Users/siwakornbundi/.gradle/caches/modules-2/files-2.1/org.junit.jupiter/junit-jupiter-engine/6.0.3/e26f7e17d06cfc85ba7643b5ad00c87d5f2084dd/junit-jupiter-engine-6.0.3.jar:/Users/siwakornbundi/.gradle/caches/modules-2/files-2.1/org.junit.jupiter/junit-jupiter-params/6.0.3/6cd3efadd171a3ddd70413868a9c3af988a45907/junit-jupiter-params-6.0.3.jar:/Users/siwakornbundi/.gradle/caches/modules-2/files-2.1/org.junit.jupiter/junit-jupiter-api/6.0.3/2e6cfb62db85350179f0408ed5270f7cdf8cefda/junit-jupiter-api-6.0.3.jar:/Users/siwakornbundi/.gradle/caches/modules-2/files-2.1/org.junit.platform/junit-platform-engine/6.0.3/101305612e7daa4ce978d3ba73fd02dd4a5f22d9/junit-platform-engine-6.0.3.jar:/Users/siwakornbundi/.gradle/caches/modules-2/files-2.1/org.junit.platform/junit-platform-commons/6.0.3/1093a7faa2ba47c28aa83f378d9a2083463bb20c/junit-platform-commons-6.0.3.jar:/Users/siwakornbundi/.gradle/caches/modules-2/files-2.1/org.junit.jupiter/junit-jupiter/6.0.3/da72f4bc0feccbca639b901ace26e3a62512ebec/junit-jupiter-6.0.3.jar:/Users/siwakornbundi/.gradle/caches/modules-2/files-2.1/org.testcontainers/testcontainers-postgresql/2.0.5/a7a9f748bb0e043098d3fc36d4929538ee0f6de8/testcontainers-postgresql-2.0.5.jar:/Users/siwakornbundi/.gradle/caches/modules-2/files-2.1/org.testcontainers/testcontainers-junit-jupiter/2.0.5/89bd1ef45ec60b17b323c747da1e6f2af6187543/testcontainers-junit-jupiter-2.0.5.jar:/Users/siwakornbundi/.gradle/caches/modules-2/files-2.1/tools.jackson.core/jackson-core/3.1.5/ccd7eb3269b8d33bc097b36770f367cdfe2f1150/jackson-core-3.1.5.jar:/Users/siwakornbundi/.gradle/caches/modules-2/files-2.1/tools.jackson.core/jackson-databind/3.1.5/782178f0b1fe088803c03b85eb9a406f60519044/jackson-databind-3.1.5.jar:/Users/siwakornbundi/.gradle/caches/modules-2/files-2.1/org.testcontainers/testcontainers-jdbc/2.0.5/8b93b3febba94df8a3bd67c9fde8f63c8ddf2177/testcontainers-jdbc-2.0.5.jar:/Users/siwakornbundi/.gradle/caches/modules-2/files-2.1/org.testcontainers/testcontainers-database-commons/2.0.5/bc1eeea337c461c108507f596122b3e6fdb62122/testcontainers-database-commons-2.0.5.jar:/Users/siwakornbundi/.gradle/caches/modules-2/files-2.1/org.testcontainers/testcontainers/2.0.5/7e5013c78fd678ba8958db23c6883274019f2f2f/testcontainers-2.0.5.jar:/Users/siwakornbundi/.gradle/caches/modules-2/files-2.1/com.github.docker-java/docker-java-api/3.7.1/2df91233a782749e85139991cc94d4ef40b59038/docker-java-api-3.7.1.jar:/Users/siwakornbundi/.gradle/caches/modules-2/files-2.1/com.fasterxml.jackson.core/jackson-annotations/2.21/b1bc1868bf02dc0bd6c7836257a036a331005309/jackson-annotations-2.21.jar:/Users/siwakornbundi/.gradle/caches/modules-2/files-2.1/org.apache.commons/commons-compress/1.28.0/e482f2c7a88dac3c497e96aa420b6a769f59c8d7/commons-compress-1.28.0.jar:/Users/siwakornbundi/.gradle/caches/modules-2/files-2.1/commons-codec/commons-codec/1.21.0/d95f998db5f89900fe895daf6cd2cddcb2f1d64b/commons-codec-1.21.0.jar:/Users/siwakornbundi/.gradle/caches/modules-2/files-2.1/org.apache.commons/commons-lang3/3.20.0/65897b3e5731220962e659e001904af3c3cbeba9/commons-lang3-3.20.0.jar:/Users/siwakornbundi/.gradle/caches/modules-2/files-2.1/com.github.docker-java/docker-java-transport-zerodep/3.7.1/6b1af9996b2004703a41e0fbbf1031cabbbe4e6b/docker-java-transport-zerodep-3.7.1.jar:/Users/siwakornbundi/.gradle/caches/modules-2/files-2.1/org.slf4j/slf4j-api/2.0.18/78a9e7a37cd6360e0b818e86341b24123d28d4df/slf4j-api-2.0.18.jar:/Users/siwakornbundi/.gradle/caches/modules-2/files-2.1/org.checkerframework/checker-qual/3.55.1/3d40699936a66b2d4c1f759f2f1a0446c8cf64fc/checker-qual-3.55.1.jar:/Users/siwakornbundi/.gradle/caches/modules-2/files-2.1/org.opentest4j/opentest4j/1.3.0/152ea56b3a72f655d4fd677fc0ef2596c3dd5e6e/opentest4j-1.3.0.jar:/Users/siwakornbundi/.gradle/caches/modules-2/files-2.1/org.rnorth.duct-tape/duct-tape/1.0.8/92edc22a9ab2f3e17c9bf700aaee377d50e8b530/duct-tape-1.0.8.jar:/Users/siwakornbundi/.gradle/caches/modules-2/files-2.1/commons-io/commons-io/2.20.0/36f3474daec2849c149e877614e7f979b2082cd2/commons-io-2.20.0.jar:/Users/siwakornbundi/.gradle/caches/modules-2/files-2.1/org.jetbrains/annotations/17.0.0/8ceead41f4e71821919dbdb7a9847608f1a938cb/annotations-17.0.0.jar:/Users/siwakornbundi/.gradle/caches/modules-2/files-2.1/com.github.docker-java/docker-java-transport/3.7.1/7b8f1b7486e83f2871d9f98a7e12c529dfddc9c2/docker-java-transport-3.7.1.jar:/Users/siwakornbundi/.gradle/caches/modules-2/files-2.1/net.java.dev.jna/jna/5.18.1/b27ba04287cc4abe769642fe8318d39fc89bf937/jna-5.18.1.jar:/var/folders/6p/8cx8_9294s10q4v934jqdn9h0000gn/T/vra-poc04-external-tc-vsfk4w5a/external-tc.jar /tmp/vra-poc04-proof-capture.MWebzs/PhaseOneEvidenceCapture.java /Users/siwakornbundi/Project/VRA-Fastform/validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/database-proof.json both`. Evidence: `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/database-proof-command.json`.

**regression**, exit 1: `./gradlew --no-daemon -Dorg.gradle.java.installations.auto-detect=false -Dorg.gradle.java.installations.paths=/var/folders/6p/8cx8_9294s10q4v934jqdn9h0000gn/T/vra-poc04-jdk-x0q7h_vo/jdk-21.0.12.1+1/Contents/Home -I /var/folders/6p/8cx8_9294s10q4v934jqdn9h0000gn/T/vra-poc04-mount-support-_1rpppjy/runner.init.gradle :migration:test :migration:integrationTest :runtime:test :runtime:integrationTest check build`. Evidence: `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/regression-command.json`.

| Suite/run | Tests | Skipped | Failures | Errors | Exit |
|---|---:|---:|---:|---:|---:|
| focused fresh | 1 | 0 | 0 | 0 | 0 |
| focused populated | 1 | 0 | 0 | 0 | 0 |
| focused bootstrap | 1 | 0 | 0 | 0 | 0 |
| :migration:test | 4 | 0 | 0 | 0 | 0 |
| :migration:integrationTest | 18 | 0 | 0 | 0 | 0 |
| :runtime:test | 56 | 0 | 0 | 0 | 0 |
| :runtime:integrationTest | 102 | 0 | 38 | 0 | 1 |

Suite exit values for passed tasks refer to their successful task completion in the combined Gradle invocation; the combined command exit is 1. Check/build completion has no PASS count.

### Database identities and actual catalog proof

fresh: container `34ffe2e8cb5f9a584e6857e2390ed69cf9cb0c16e8c033a069143fd62c2d8cfa`, volume `vra-poc04-91a3b9eb-1a68-446e-a50c-9c69f753f49e-postgres-authority-0-pgdata`, network `74853d4b0a0d2fd4c600610a0a9786df812baa2752fa514dd7bb2b0b73761aed`, run_id `91a3b9eb-1a68-446e-a50c-9c69f753f49e`, database_instance_id `1598232b-2ad6-580a-b24a-63fc3910f7ea`; actual database/version/system identity `[["vra_poc01", "postgres", "PostgreSQL 17.11 (Debian 17.11-1.pgdg13+2) on aarch64-unknown-linux-gnu, compiled by gcc (Debian 14.2.0-19) 14.2.0, 64-bit", "170011", "7691593394851569708"]]`.

populated: container `46b25f5c7ae46947bc4dc187cac14c5106c746d40b93563e8e64eeb1fce9db4f`, volume `vra-poc04-91a3b9eb-1a68-446e-a50c-9c69f753f49e-postgres-authority-1-pgdata`, network `05f3540fcbbfc4bff65c24baf5dd14da0da3879c6946be25c0301310d00a227a`, run_id `91a3b9eb-1a68-446e-a50c-9c69f753f49e`, database_instance_id `0fe97c56-5008-509d-9792-b6fde6f4555d`; actual database/version/system identity `[["vra_poc01", "postgres", "PostgreSQL 17.11 (Debian 17.11-1.pgdg13+2) on aarch64-unknown-linux-gnu, compiled by gcc (Debian 14.2.0-19) 14.2.0, 64-bit", "170011", "7691593475206656044"]]`.

Role catalog order: role, LOGIN, INHERIT, SUPERUSER, CREATEDB, CREATEROLE, REPLICATION, BYPASSRLS.

```text
vra_async_executor f f f f f f f
vra_async_observer t f f f f f f
vra_async_operator t f f f f f f
vra_audit_evidence_reader t f f f f f f
vra_factor_fixture t f f f f f f
vra_migrator t f f f f f f
vra_outbox_worker t f f f f f f
vra_owner f t f f f f f
vra_projection_rebuilder t f f f f f f
vra_reconciliation_worker t f f f f f f
vra_runtime t f f f f f f
vra_security_executor f f f f f f f
vra_security_telemetry_observer t f f f f f f
vra_sync_executor f f f f f f f
vra_telemetry_executor f f f f f f f
vra_migrator vra_owner t f f
vra_owner vra_async_executor t f f
vra_owner vra_security_executor t f f
vra_owner vra_sync_executor t f f
vra_owner vra_telemetry_executor t f f
```

Complete transitive SET graph:

```text
vra_migrator -> vra_async_executor
vra_migrator -> vra_owner
vra_migrator -> vra_security_executor
vra_migrator -> vra_sync_executor
vra_migrator -> vra_telemetry_executor
vra_owner -> vra_async_executor
vra_owner -> vra_security_executor
vra_owner -> vra_sync_executor
vra_owner -> vra_telemetry_executor
```

Runtime column ACLs (a=INSERT, r=SELECT, w=UPDATE; grantor vra_owner):

```text
poc_account_record.version {vra_runtime=w/vra_owner}
poc_account_record.label {vra_runtime=w/vra_owner}
poc_organization_record.version {vra_runtime=w/vra_owner}
poc_organization_record.label {vra_runtime=w/vra_owner}
security_event.event_id {vra_runtime=ar/vra_owner}
security_event.actor_type {vra_runtime=ar/vra_owner}
security_event.actor_account_id {vra_runtime=ar/vra_owner}
security_event.actor_workload {vra_runtime=ar/vra_owner}
security_event.event_code {vra_runtime=ar/vra_owner}
security_event.outcome_code {vra_runtime=ar/vra_owner}
security_event.occurred_at {vra_runtime=ar/vra_owner}
security_event.request_id {vra_runtime=ar/vra_owner}
security_event.target_type {vra_runtime=ar/vra_owner}
security_event.target_id {vra_runtime=ar/vra_owner}
security_event.target_reference {vra_runtime=ar/vra_owner}
security_event.risk_code {vra_runtime=ar/vra_owner}
security_event.source_kind {vra_runtime=r/vra_owner}
webhook_fixture_receipt.status {vra_runtime=w/vra_owner}
webhook_fixture_target.state {vra_runtime=w/vra_owner}
webhook_fixture_target.version {vra_runtime=w/vra_owner}
```

All authoritative V4 table/constraint/index/type/column ACL definitions and the exact retained BEFORE/AFTER history and row digests are in `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/database-proof.json`; no raw secret-bearing rows are exported. The actual schema CREATE holders are postgres and vra_owner. PUBLIC relation/sequence/schema grants are empty. Existing function count remains 22 and V4 declares no functions; no Phase-2 capability/function-denial claim is made.

### Current source binding and V1–V3 immutability
Branch `poc/04-security-auth`; HEAD and both recorded remote refs `01256d96d85823740a8d6c3a3f99346afbdbef56`. Current canonical full-source SHA-256 `810e22b6a49302dfc213b25d039182481ce2234da9ba92ad930a51ce303d85cc` over 261 files, including the preserved whitespace difference. Approved plan/spec/image-pin bytes remain unchanged.

`backend/migration/src/main/resources/db/migration/V1__inventory_reservation_foundation.sql`: `d804cbe5153b9a904ae0747af55c8295079d4a0c3edb4a38145ab5199b5b59c4` — exact Phase-0/HEAD equality.

`backend/migration/src/main/resources/db/migration/V2__inventory_reservation_idempotency.sql`: `7bef750b59b7b77e8402f4f42c13c21c4e072092504b9f268bace9b72de8d2bb` — exact Phase-0/HEAD equality.

`backend/migration/src/main/resources/db/migration/V3__outbox_recovery.sql`: `9edbd15ffe08ee4edafa3f549856ee38a782485c54db9c73e69d4263e5ca26b4` — exact Phase-0/HEAD equality.

### Every current modified/created path and SHA-256
This inventory contains 20 tracked modified and 169 untracked paths. The current report is untracked; its final self-hash is returned separately after verification. Earlier report inventories remain historical snapshots.

| Path | SHA-256 |
|---|---|
| `backend/migration/src/test/java/dev/vra/migration/MigrationSecurityIntegrationTest.java` | `feb7a8f223c4e90a70123f67aa001b8a9ac152a9532ec6b3009f37370a92119a` |
| `backend/migration/src/test/java/dev/vra/migration/OutboxMigrationSecurityIntegrationTest.java` | `664156057d8b71b9f8702ddead09344060c9cce43fd1021cf780dd430953253a` |
| `backend/runtime/src/test/java/dev/vra/async/AsyncPermissionIntegrationTest.java` | `4ad16fe9dffbf2e2f6b3941bd8645f3094f108e5296807e55ee8ef0c63e380e4` |
| `backend/runtime/src/test/java/dev/vra/async/AsyncRoleBootstrap.java` | `80aaf4d4c7fb02878b55dfdb2ff51ed2c6ff36d631d512d1603d702dc0424a20` |
| `backend/runtime/src/test/java/dev/vra/async/AsyncVisibilityIntegrationTest.java` | `089d7f92a2b956195a20b89f393e9a4cea856284abff16558575b8b2df23883d` |
| `backend/runtime/src/test/java/dev/vra/async/DeliveryClaimIntegrationTest.java` | `cc11f8469f940c3d1a97bfeab78e6e4f27e36a959b4bf248dca42f24f0e91c99` |
| `backend/runtime/src/test/java/dev/vra/async/DeliveryStateIntegrationTest.java` | `07246761e5be4677125fbb09d1e11d86db546f1c64a37e5a5c2b7f992b211aa8` |
| `backend/runtime/src/test/java/dev/vra/async/ProducerOutboxIntegrationTest.java` | `88f6301a210bb00fdd21ea138f71e438ce08d5c34fab1c0143f3311450c0fcec` |
| `backend/runtime/src/test/java/dev/vra/async/ProjectionInboxIntegrationTest.java` | `d615be8fc0eadfc28b35e7061d42432f8fff6c1ee9b98533b9a8c19a73d2e727` |
| `backend/runtime/src/test/java/dev/vra/async/ProjectionRebuildIntegrationTest.java` | `21038aafd0ebd855e812e9333e1df760d318ff5c9d0bfab6676e685f55d007c0` |
| `backend/runtime/src/test/java/dev/vra/async/ReconciliationIntegrationTest.java` | `6c22d1094f1a976794c7a8b77dfffbfe17551d81eeb2de5b1531b5d0d24a8ea2` |
| `backend/runtime/src/test/java/dev/vra/async/RetryReplayIntegrationTest.java` | `f72f451ef737750bcc9e4777fd1ecb3c898d2acfd548289be90379c621a44682` |
| `backend/runtime/src/test/java/dev/vra/async/ValidationSimulatorIntegrationTest.java` | `b17af32f9b8ffb7cbc225fd57cf0cad65a6707e3794840cc51b66b0e6f7d265a` |
| `backend/runtime/src/test/java/dev/vra/async/WorkerProcessCrashIntegrationTest.java` | `d1f1f77ec890b26dcf39e7958dca274b5a378cf899b99164d8c9ff7449fc664f` |
| `backend/runtime/src/test/java/dev/vra/inventory/IdempotentReservationApplicationServiceIntegrationTest.java` | `52d0e29beb42a4c371bbe1c23aeaeda56f4e7a251c2585d20134ed51f887ee1c` |
| `backend/runtime/src/test/java/dev/vra/inventory/ReservationConcurrencyIntegrationTest.java` | `819a2a3d5537c40c434c1c175928bb83b0d6a05ca51aa306f4e0b05498304ed4` |
| `backend/runtime/src/test/java/dev/vra/inventory/ReservationHttpPostgresIntegrationTest.java` | `cd0b20f07c48162e76c388dd36bb7e429339aa0bb5b2de271e1696adb9d4f5e4` |
| `backend/runtime/src/test/java/dev/vra/inventory/ReservationIdempotencyRepositoryIntegrationTest.java` | `aeb577cc289476554252af3849263b09e246286bfeeea9cfd316745f7b07ef03` |
| `backend/runtime/src/test/java/dev/vra/inventory/ReservationTransactionIntegrationTest.java` | `5b0b4e0dfdb79875eb87edd1969602982c2d92b4558e18c8e43686260bb94b03` |
| `backend/runtime/src/test/java/dev/vra/platform/health/HealthProbePostgresIntegrationTest.java` | `283b9bb7655679ffa029c83837db025cd7b9425b5dbfdf82a37f731d78aeb45c` |
| `backend/migration/src/main/resources/db/migration/V4__poc04_identity_security_foundation.sql` | `45161b8c54f10778a5521cdd23dc0945fadb65364bd8094e3d19d8f79cfdd1e8` |
| `backend/migration/src/test/java/dev/vra/migration/Poc04FoundationMigrationIntegrationTest.java` | `50e601ce5fb4bd009aa1d48d1459dd76cfb44171e7d470644ba43f373e2fd5ee` |
| `validation/poc-04/db/COLLECTOR_TEST.md` | `09117a00f800c91748017234be2829a86828895ff26a119beb618327fddd50c1` |
| `validation/poc-04/db/bootstrap-security-roles.sql` | `976cf704687e5bf005de8943c706fdd996d801172c112d640c993e0b193407b5` |
| `validation/poc-04/db/collector-test-entrypoint.sh` | `90e6d263e9c121cb2b92dd80e45f49fef71452d5c6f52b1695752d1725fe73d0` |
| `validation/poc-04/db/collector-test.compose.yaml` | `db3c7dec5fa8b1f6e04512e30a4d6dc9b58e11316c53d3301b82a0712db62c47` |
| `validation/poc-04/db/postgresql-collector-test.conf` | `ba1f292521a67965fc3b8f03bf8d39cac460852cdb33f2d8e51d6809036440db` |
| `validation/poc-04/evidence/phase1/IMPLEMENTATION_REPORT.previous-4a4116f1.md` | `4a4116f11c77fdd2d7c503405f24cb53fe69e84c1502fcb2fc049387046eacbb` |
| `validation/poc-04/evidence/phase1/IMPLEMENTATION_REPORT.previous-93baf961.md` | `93baf961f3370311e1d669e3594ed9c0c2d925ef66d0df70c62f33e61d127a57` |
| `validation/poc-04/evidence/phase1/IMPLEMENTATION_REPORT.previous-b772942a.md` | `b772942af3dc367941cd7e2d553ca2f990542964114fdd3002a8c994941cdd65` |
| `validation/poc-04/evidence/phase1/IMPLEMENTATION_REPORT.previous-e5df43af.md` | `e5df43afd289c8fc9a87cfe0bc25fdf182d6195ea8f8f6bfedf160df4caf0e3e` |
| `validation/poc-04/evidence/phase1/POSTGRES_EXECUTION_PREFLIGHT.json` | `6eee43e49f81d5bbb1db5eea0f60d25539be7c05dc309bec516b6e2bec98357f` |
| `validation/poc-04/evidence/phase1/POSTGRES_EXECUTION_RESUME_PREFLIGHT.json` | `f5245404d9d291d5398ce38a9f295b15e3cbf576a9edfbb53ffc6ab269ce04ff` |
| `validation/poc-04/evidence/phase1/connectivity-57951433-ef13-4dde-9ef5-3d397b096656/RyukConnectivityProbe.java` | `c20c1c90a7dfbbfdaa6b19a8bba91966ad1c95362b67dc37bf803e78543e7e24` |
| `validation/poc-04/evidence/phase1/connectivity-57951433-ef13-4dde-9ef5-3d397b096656/cleanup-proof.json` | `e5240825141c5cc8af43c69b1f50fc1e315562cb5c5ed073ba9e598cdfa95b91` |
| `validation/poc-04/evidence/phase1/connectivity-57951433-ef13-4dde-9ef5-3d397b096656/connectivity-proof.json` | `0d19e54f918faaf6ee338d0e199beafcf1c47a9c6b635f0e66ae2a40346fecd2` |
| `validation/poc-04/evidence/phase1/connectivity-57951433-ef13-4dde-9ef5-3d397b096656/probe-command.json` | `64d31718431dd19b8590dd361335d15d830b400e03b563f022b55525e8e794bf` |
| `validation/poc-04/evidence/phase1/connectivity-57951433-ef13-4dde-9ef5-3d397b096656/probe-output.txt` | `c92f6fab0503fee8895b40124d993e6f906cbf13bb1a93f764b10e04fe369347` |
| `validation/poc-04/evidence/phase1/connectivity-57951433-ef13-4dde-9ef5-3d397b096656/probe-state.json` | `cc5b35d0c6278a7af1fe4a73dbb174775075ef8100d0fc0c692babbdfb2e5715` |
| `validation/poc-04/evidence/phase1/execution-304b3976-4ebe-4a61-8bbc-e67a9a40e669/TEST-dev.vra.migration.Poc04FoundationMigrationIntegrationTest.xml` | `47e6d6c9f8940671ca786e8ce27d55525eda26e7f4b1826720e0eec1d51e5938` |
| `validation/poc-04/evidence/phase1/execution-304b3976-4ebe-4a61-8bbc-e67a9a40e669/cleanup-proof.json` | `a1674fe389b439073a5c029084474b7e651d4903315b0758719a2fc4f5bd993e` |
| `validation/poc-04/evidence/phase1/execution-304b3976-4ebe-4a61-8bbc-e67a9a40e669/docker-resource-events.jsonl` | `0ecfd984d4851afe378a5553abef221cd4d559f76695e16dc119f17c4d29227a` |
| `validation/poc-04/evidence/phase1/execution-304b3976-4ebe-4a61-8bbc-e67a9a40e669/execution-summary.json` | `d0cb9e38c4d90ce8c77301812022f61ecb7588d26cc0680675902ed39b8f6ee5` |
| `validation/poc-04/evidence/phase1/execution-304b3976-4ebe-4a61-8bbc-e67a9a40e669/fresh-command.json` | `218897f4d9e28548f07dad1f90b0a638e80de1defe16bc2fc209efa5104dfb14` |
| `validation/poc-04/evidence/phase1/execution-304b3976-4ebe-4a61-8bbc-e67a9a40e669/fresh-gradle-output.txt` | `3555950acfc946f19c448702dd0a50f459cfee0cc3c971eed986339a0ccea5f0` |
| `validation/poc-04/evidence/phase1/execution-304b3976-4ebe-4a61-8bbc-e67a9a40e669/preflight.json` | `cc2a295672f4587267d716100e7ff49c732f5a21e7e6838b9668ab21e750a0b8` |
| `validation/poc-04/evidence/phase1/execution-304b3976-4ebe-4a61-8bbc-e67a9a40e669/pulled-images.json` | `bd0aafe1c3cb793bd77c9e820d4d128f09a2fccfb46bbbae32d5124e90cfdc8f` |
| `validation/poc-04/evidence/phase1/execution-304b3976-4ebe-4a61-8bbc-e67a9a40e669/ryuk-failure-infrastructure.json` | `784bd8999240ef948305f4102a019359789b612f38bb7a06da9324060276e0b6` |
| `validation/poc-04/evidence/phase1/execution-e0a3d5b9-ab05-453f-b133-39b50828e0b7/TEST-dev.vra.migration.Poc04FoundationMigrationIntegrationTest.xml` | `30736a023d77c7b9ff11c78e16651b5300b2ede035fb80158eedcb0445d0e88d` |
| `validation/poc-04/evidence/phase1/execution-e0a3d5b9-ab05-453f-b133-39b50828e0b7/artifact-validation.json` | `a00eab5f9f8b74f06fcf556e1c1679c2eadf952e0ca7bca9486838d1d117e12b` |
| `validation/poc-04/evidence/phase1/execution-e0a3d5b9-ab05-453f-b133-39b50828e0b7/cleanup-proof.json` | `97ac9bd08884b41e4c3531d04e15096e7d1b0fb515e35bf42dcc43db4c235e69` |
| `validation/poc-04/evidence/phase1/execution-e0a3d5b9-ab05-453f-b133-39b50828e0b7/docker-resource-events.jsonl` | `e206f6bdb728f96562b1d0c67225a4557abeb08d0634eb6561a282f7895ec71c` |
| `validation/poc-04/evidence/phase1/execution-e0a3d5b9-ab05-453f-b133-39b50828e0b7/execution-summary.json` | `975368b8933da29cd140f17aceb54f54dd152e29683dc9faa4b4f535b6395829` |
| `validation/poc-04/evidence/phase1/execution-e0a3d5b9-ab05-453f-b133-39b50828e0b7/final-validation.json` | `d0abfc8311b68300b192308ccbfb626915c28f6e67f520eab0ed0e68b5ddba7d` |
| `validation/poc-04/evidence/phase1/execution-e0a3d5b9-ab05-453f-b133-39b50828e0b7/fresh-command.json` | `8eb2b409c3b7dea4c55d2f96e53b39c63c4ef1edf79196c15e819e42c349ce92` |
| `validation/poc-04/evidence/phase1/execution-e0a3d5b9-ab05-453f-b133-39b50828e0b7/fresh-gradle-output.txt` | `a3f08bd464b1ddc88516ea343b14498541e0e46a5d7b10fa646c061d9305be4e` |
| `validation/poc-04/evidence/phase1/execution-e0a3d5b9-ab05-453f-b133-39b50828e0b7/image-attestation-defect.json` | `c9b489165e3b09d16993e119fdb64890b86beb55e05e1d127d578f8ec7b64b8f` |
| `validation/poc-04/evidence/phase1/execution-e0a3d5b9-ab05-453f-b133-39b50828e0b7/preflight.json` | `dc863f6ace8ef3935f1436e17959cb59f5f13c9620b9f6b7a66ad2576c6f7b3e` |
| `validation/poc-04/evidence/phase1/image-identity-diagnosis-03baf784-dd20-4615-be07-d633535b9b04/diagnosis-summary.json` | `0304ec0a747b40a0e7280f137832d57e19a48834dc7b7468cfd896910c210228` |
| `validation/poc-04/evidence/phase1/image-identity-diagnosis-03baf784-dd20-4615-be07-d633535b9b04/docker-image-semantics.json` | `9c3e68cc3b0a55a1f900ac095fd099938bad8a2803d500e72b49cd52f4bb222c` |
| `validation/poc-04/evidence/phase1/image-identity-diagnosis-03baf784-dd20-4615-be07-d633535b9b04/post-diagnosis-inventory.json` | `92f322398a9a59c5518e05fcaf6609efc14b9ac31849f01c02dcf48f2bf7f935` |
| `validation/poc-04/evidence/phase1/image-remediation-5e8a333f-5b82-4b7d-b513-0fea93933bab/Poc04FoundationMigrationIntegrationTest.before-image-remediation.java` | `125e9cf33055428f4354fe7c0637ba60e2f3857eb3a266c7fa089db7f6c71005` |
| `validation/poc-04/evidence/phase1/image-remediation-5e8a333f-5b82-4b7d-b513-0fea93933bab/TEST-dev.vra.migration.Poc04FoundationMigrationIntegrationTest.xml` | `045add229586d974e16fa2b55f06c1472d71493f03b749c0ecfb5a033c673a77` |
| `validation/poc-04/evidence/phase1/image-remediation-5e8a333f-5b82-4b7d-b513-0fea93933bab/artifact-validation.json` | `75350dfe2de8ca71eec89a1fbd7444819e1ee565e742b8169d9a43a61102f9ea` |
| `validation/poc-04/evidence/phase1/image-remediation-5e8a333f-5b82-4b7d-b513-0fea93933bab/cleanup-proof.json` | `50f21c05209c91be8028ffc6a49be4937a48eaccddfd1f99517dd95bff3ff232` |
| `validation/poc-04/evidence/phase1/image-remediation-5e8a333f-5b82-4b7d-b513-0fea93933bab/docker-resource-events.jsonl` | `0e743fb5f1dcb6e54e7bd97f379ecb60f67f9c382c476687f9ec0cc04a7b086c` |
| `validation/poc-04/evidence/phase1/image-remediation-5e8a333f-5b82-4b7d-b513-0fea93933bab/execution-summary.json` | `db05f146b809121ee5a17fbf5c4b157d39ec35183d243514e0e931ea1da4be30` |
| `validation/poc-04/evidence/phase1/image-remediation-5e8a333f-5b82-4b7d-b513-0fea93933bab/fresh-command.json` | `142bc74a0cdb137a04efe37172b72614d8484832abc90d2e7bbc8ad5d17e6e74` |
| `validation/poc-04/evidence/phase1/image-remediation-5e8a333f-5b82-4b7d-b513-0fea93933bab/fresh-gradle-output.txt` | `53b0e9fb16288c023a6b7cdeaffc4fd0d72328548d2fc967558a3fe6a815a9cc` |
| `validation/poc-04/evidence/phase1/image-remediation-5e8a333f-5b82-4b7d-b513-0fea93933bab/manifest-preflight.json` | `b1ab06561d193b66f99650ae593805a70e29fb93aa820c4dce80d2d1af1bcf0d` |
| `validation/poc-04/evidence/phase1/image-remediation-5e8a333f-5b82-4b7d-b513-0fea93933bab/mount-model-diagnosis.json` | `7f2143751dea96ceedb4638fd039efb916c9cf85fd4bcb48e0bd9c3765fd4b17` |
| `validation/poc-04/evidence/phase1/image-remediation-5e8a333f-5b82-4b7d-b513-0fea93933bab/postgres-arm64-manifest.json` | `86fa57b44a1d38f09970f64ac71eb492f7a9cf94d08b85e1c8f07bb0fc0a1761` |
| `validation/poc-04/evidence/phase1/image-remediation-5e8a333f-5b82-4b7d-b513-0fea93933bab/postgres-index.json` | `d74eeac9a635390a49bc21bd49fccd973de707e2a53a76ac49b552b8712ec46f` |
| `validation/poc-04/evidence/phase1/image-remediation-5e8a333f-5b82-4b7d-b513-0fea93933bab/resource-recorder.py` | `f68ba9d804b59f0e4c955c290e8e23b9dd30eec2871d63c08f7dbbed3cc74af8` |
| `validation/poc-04/evidence/phase1/image-remediation-5e8a333f-5b82-4b7d-b513-0fea93933bab/runner.init.gradle` | `1255ee9276b75e8f33819c911dc63aad7bedfcead64adb14f36a91a4f5657b34` |
| `validation/poc-04/evidence/phase1/image-remediation-5e8a333f-5b82-4b7d-b513-0fea93933bab/source-change.patch` | `ecb4a676c89a583c96e36546a9292300f55589e77aba3c1231c4e2f78a3ed7f1` |
| `validation/poc-04/evidence/phase1/image-remediation-5e8a333f-5b82-4b7d-b513-0fea93933bab/source-review.json` | `e9a2e2d70c50305fc1d9283bfc937698e6f283487c4ef4dc05dbfdd1ac072fc5` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/PhaseOneEvidenceCapture.java` | `6ec9759c3e09d78cabed1cd5ef712dc6b4718f15ad4b03f0bda4aecce63c2200` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/Poc04FoundationMigrationIntegrationTest.before-mount-remediation.java` | `eedaf64c01778dcc9f1b502a4127bd1363c08a310e3cc131a91a48e6891b1ee8` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/TEST-bootstrap-Poc04FoundationMigrationIntegrationTest.xml` | `2fed9df4af68195466dd14a05cadbf74d72049ba15497d2e1373041fa2388bc0` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/TEST-dev.vra.migration.Poc04FoundationMigrationIntegrationTest.xml` | `2337acb89c1363904b04541a8d56987ae27def6c4235b5ee3b845baa18d7708e` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/TEST-populated-Poc04FoundationMigrationIntegrationTest.xml` | `4b37c16d40dd1df5113d354c6f3708c440f22f72c8c9afda340e18780c38fffd` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/artifact-validation.json` | `2c4c4b33e434ba95079a19d960d40db23a5983d567854f3a975a2ed7dc312941` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/bootstrap-command.json` | `7cd6a567d8251331ea0769cb2bfdf83ce674c2cbf66061c6b1a81e0621dae5a6` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/bootstrap-gradle-output.txt` | `40754acd033458292ba69a199ec87108dfa193d38356ced78cd1645c37b5ada4` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/cleanup-attempt-1.json` | `13c56ea85ebf03f9a3c025c579134e8a2da761b054206c2c1ce7fe78ece74342` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/cleanup-attempt-2.json` | `49e6f2e8b5ef248b0245719e9528a67deb1d141b95789139a0874be758023d22` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/cleanup-proof.json` | `45c6c7893c8c2de4bb67245f34f19620dd012ca8dbf073a1cae223ca96fa0264` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/daemon-inspect/09cb4e2c80d32d69ceb8386c12a4fd7945dada17ebdffaa86e615f8dd38dd417.safe-inspect.json` | `0ae2351de88a508326d886c4b65deb4c5d3a571673ed3e620c2fdbcd6cd401c6` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/daemon-inspect/0d8d4fb6856793dedd3f622ef34da69d6d343836df0fddacf814db9232579698.safe-inspect.json` | `be73e02510a26e0f855992af9f45e3d51501e2529c15d153820dabc8592be390` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/daemon-inspect/16873bc2c9d6d1570493b3be6d6d5a90d07934c52a1bea2c7d12b1adc304107e.safe-inspect.json` | `b95e0078082ddeac0f74c630931c02bd4044747767351eef25d312e014952abf` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/daemon-inspect/18b5f615e856ecbe06b34a77d1396f48cbbba6f474ad0adc5a542c5d7d767801.safe-inspect.json` | `faa561b00c647a06432d8d29f5fd4d8c7cad1c41c2c4dc20ed7fdeaca45f852c` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/daemon-inspect/18ec4bbe6f233181a33bf9e605747e25ea10e8dc0a4caa8200c0524c60e726b7.safe-inspect.json` | `cc0dc10620f07af28476e8c5368047fb7a6d45eee617b74e7866ef26e3bdcd65` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/daemon-inspect/2b69937c92dd72a4aa03736caf63e3672970e9b43eec332a6efa0eb4842b4fed.safe-inspect.json` | `6553e9f452be781e891c635999877798328ce12a9f29adac9c8e8e7eb27581e1` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/daemon-inspect/2f92afceb47252d8ab6bac87ade3c800b8b7b08aa7a5af02513a92d37f552026.safe-inspect.json` | `db5626d411ec877ae9ec322dd7e8385ca67a44c0e55085fe9f4c09cf22408d6d` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/daemon-inspect/310942bf8c0830b53c981560f7dbbf06a4d40fc88c4ef0cbfa54ac35b466fd87.safe-inspect.json` | `eaaf294e26dbae7750223e98790ea6805ec34b1d90cee06619d07b9538eb46a0` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/daemon-inspect/34ffe2e8cb5f9a584e6857e2390ed69cf9cb0c16e8c033a069143fd62c2d8cfa.safe-inspect.json` | `a99f64a1ad32b92ac8605260095f1dfdfcf10bba35e3d1d430272414ef2f3c58` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/daemon-inspect/3ffc576a19cb8a429325bcc8026e4919ed68fa53d55f6341afbe29724a5a186e.safe-inspect.json` | `5e3fc2a4a5f758f938940b6bf16e4ecb6b6fb8d23f4f64958fa0d619eacf6f8e` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/daemon-inspect/403145f010e6445eca74972c1e1140854ce5613188fbd4cb69a35ceda1972030.safe-inspect.json` | `1c02edd899a7f48f7717a997ea66d49248bd8162e8b11c82ed9fe1529a98a4ae` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/daemon-inspect/46b25f5c7ae46947bc4dc187cac14c5106c746d40b93563e8e64eeb1fce9db4f.safe-inspect.json` | `0012e21c303dc68ce52fefbaf4c5a5f4454ff01cc3a12c0dd8585e5eccdf6730` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/daemon-inspect/4d0674aa26e9acdad74d5a142201f5862ec88730ef8665b2d7771cebc272a68f.safe-inspect.json` | `49e0357c8c76fb690b97c49c60fd798b6cac10c3e6e00221e18eda09d75085f8` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/daemon-inspect/4f63fc6e4432a0714eb18360ebf1c7e9e7c2e88c0317df4267d9317df5a105f8.safe-inspect.json` | `29c27552505e5aa7b1baeef0afd38ecc69cfd9d1b4ee8017e89aae48155df056` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/daemon-inspect/5091ace65378b2972ddc227e4cf74157467fafb3ed33d389985e37f201348fbb.safe-inspect.json` | `b51a1fe37292384135246d27c515c62e33a13aae51d7ab5b474db259d4683c2e` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/daemon-inspect/5aae23b9d1b6855fe9c6a7f8e6e6f4b85ee88abec9e0b7922e0dbbdef78798d6.safe-inspect.json` | `d8032a4f75048a0d86fbcc346d7c7762801897a46bb959134166e427e8a038c3` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/daemon-inspect/5dff536198c025288050d48613cc02091ee27ec6453fa344b1529b3c341dd78a.safe-inspect.json` | `9f13f23901988c8d2f91074bd44782133a70a6ec5f023e55fdf57627b76c5be8` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/daemon-inspect/675094e8ace0b849f4ecb93332432f5f7cc0db9a1e05e0d97c52ecd2fe39accc.safe-inspect.json` | `a22661dcd609e3e443f196196fc02e0e00b939979b55d2fb91240193da119e37` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/daemon-inspect/745eb3ed039a5511a960eff65b9f868605f8e445ae3806fe0e5c94eb359696ec.safe-inspect.json` | `67959011c390fe8973b738fcb814e22f5c13181a947c85128c8a82adf7f7dd0f` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/daemon-inspect/86150e6f75bd020abae56eb740cd72ced891244a8df0188e0b2309770beb5179.safe-inspect.json` | `0f54a8c5373d51f3eb33865e796c80217d47c75d03df8686d6433cccc21289dc` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/daemon-inspect/871616502b17e86e230327f63524a57968271385fd168588dfcb317c164395f4.safe-inspect.json` | `55df1d62f3ebda890b2455ea961f97ec8df5341b656e1ac17bacde4619313854` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/daemon-inspect/8ca546c45ab73ffa9b62e906d9191a21aa59e6e9cfc6a5c29abc2449bb1d1bc6.safe-inspect.json` | `ddeddda2ed3a38a92721214bfdff530de630af30717727d66072da0e76e4e86d` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/daemon-inspect/8d4cc83c800dc37998f6544238455f447f19c52797c2c3e70fe219b0c7fba286.safe-inspect.json` | `45f05b4bab6609f9844282f2898cbdbd23a8608cd3e707e14fd2b24ff20e1ef1` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/daemon-inspect/933d4138439d542bcc16fdbd208a2dea7f8256078e649b208af4ac090f134cd2.safe-inspect.json` | `804117ed396591494968f8af50841b23ec0ab9762b5a8342729117b3976736e9` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/daemon-inspect/9a503b18c15606944671feb81b0fe3a30c7fc9652f7f6b18cb469ab218867dbe.safe-inspect.json` | `7c1fc4f9ed144bf65c23792522fd3168ba913dea4a858cd93a6831dd17cf22b4` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/daemon-inspect/a00ee6f2368f62434650ebc3decdbf05069b34ef4ca6fbaef38ed0bc8914baf0.safe-inspect.json` | `bea8ee38049cbb5915b5ee158fb07c81f7ace0735ba0c3753f92cee779227e2e` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/daemon-inspect/a3a8fec0f7ec2ad2cf9fc9258bda96f18868d5a6e85472fdeebbdb194e1f4154.safe-inspect.json` | `2e2707e7714a39d660d5340fa79f4f6b2f8fbb366a798db4da2a9026a40fa1b0` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/daemon-inspect/b04de203abc4be78070522994572cdfc98eb3c4997d2b83a98cabd2b2c511c55.safe-inspect.json` | `3d0b69f8dae6e5b474ab42fadf776f2927b83e729dad3069f2769daf0ed653d6` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/daemon-inspect/b202450e1dc411966925081c160ab6285c9ed14ec69accb13498443591273f0a.safe-inspect.json` | `325781d8770665fa57ffa208ef1ba9f96123bffd8e1062ff18738b86ff248137` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/daemon-inspect/b914d844bac27820107b4c1e2d39e6de3dcfa62c2988487bd2854ba9bb5867ac.safe-inspect.json` | `fbbf03c012c044145b61c587e0faaebfc35eceab2e61525c782538751f8d294b` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/daemon-inspect/bf42ad53c6e525b184817444655b210d8d221bf93106384245295c150e2288f2.safe-inspect.json` | `e97c4691d11bcbd6be518e3ebbca42f0b8aef0452b397c972e20cc6064f077df` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/daemon-inspect/bfd236995a86d7527869906e148e8878f2079e13d527da0fc8b750cc39476eab.safe-inspect.json` | `7aad7749110b9aa5b41382d3ef869ee4e3b4896c45b503aca45e1c258ee19af9` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/daemon-inspect/c7b2aeb49faac1beb34692fdc985ef00fd44946c7bd29b31ac75e630b13ce98d.safe-inspect.json` | `496144a19d9401e3ac2ec338482d8c2767dbcb036cecc8c0d49cfc2b14c128f5` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/daemon-inspect/cef9fbc976ae784fd71ec83b64274a58aa21506be41335b54ead16e88cc5dad8.safe-inspect.json` | `538e03ae3f0e9beb6d56fb6ecc97eb738505294a5e444b49cafcb27e179270c7` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/daemon-inspect/d0d53b4995a4869a0239886b6c6f09c767ddb5de61795062cab57c34c9422bb8.safe-inspect.json` | `6e03f20faf22ad72330d1619b7cef8d76347f42a72272d68d6ed6230ee7b0302` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/daemon-inspect/dca78ea9774f135755cc306019593f805a7dbcf2000c599ef9204e7aac3b832a.safe-inspect.json` | `a8b3a71088d0556f3d9f7a2697f8bb416f5e3d28e0b243ecbf6436d25ef85213` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/daemon-inspect/dccbf4b9cdb12970c50d1ea8405b365736f83659077622f2d3f07fe4f7833291.safe-inspect.json` | `0ff6f49826b064fbde0e46a455f3df96e3c2a5bbe48b96c7093f2134ae644749` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/daemon-inspect/e60c2198b56d064403de716b51b57efc6cb83ad89ff025365d75570697b7f1c5.safe-inspect.json` | `9ce9e0020d941628ff98c54a3f257e88f699adba4b564040372e094497267ce1` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/daemon-inspect/ead57834968d8aaba672def31a33f6e75fa4c22828a31b1fc6d6ebef549cea93.safe-inspect.json` | `fa53f70156676dd4ba04bf80c3628df82456ba10cfc236defeb1bb4cb48b374b` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/daemon-inspect/ed62d360804cffbde6b2e8bf7e20f231b678ab769c5655d7a10d9ce738ee02c6.safe-inspect.json` | `9b7ef00d7a0f8d526bb43e5b8c9cef6775fb62535cd2952beda8d8287dea8f44` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/daemon-inspect/f456a45d3026e908f79fc3012e827e1c965e669c098dcd0ebb3b0bfcedf5976b.safe-inspect.json` | `78a412c001c1e7a77ef9cb7d92fcc5b965140901f3f4fe3dd469dc2eecfb75be` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/daemon-inspect/fbe0a5c79add3b53641f4061b5f93caf1786089170e20894392a085adc5dea65.safe-inspect.json` | `d9646ee7fbcd074f4d5410eccf52867f89c06dba13d91fdd08dc69920db544ee` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/database-proof-command.json` | `cd3890b7c7b77bd5eaebbcc81abeca7b87f43b51e1a6c9a655ea93a2985af4ec` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/database-proof-output.txt` | `2b0b7b8b03cbdda58fb7333ce36b1ab5dcf6299f8226120b91b07a4f140da7d3` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/database-proof.json` | `6c5145d7516192d6ef7bbad7201a0215de9ffeb288b735a2f59ab3bd7e2e2c66` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/docker-resource-events.jsonl` | `2da72fec952eb0c40327ab0e1c742a31eb15a11504d0960a622dbedab0b6afd7` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/exact-resource-cleanup.py` | `d0c24d7da45b2d56ae21643707f10024f2b16986035db6c7a2fc9f60a4dc8773` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/execution-summary.json` | `d2b680dd7f366085a19c6ef64a229be3fdfc7f0797f445ed78a303d4bdb00133` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/external-support-provenance.json` | `9bce851394d45ed9b694871a2ab5f0bb00d4758255761f18413284923012126f` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/final-git-inventory.json` | `716fc90c8b9435306f6b8c3e2a6505909ba8340d925ba8869620a64a44df209b` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/final-validator.py` | `63b3ce4cfc8d635ecfb6ad2d6b6e317e7969be314880e7917b5a15f00ffa8dfd` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/fresh-command.json` | `a38ac8b3dfa49c397c062920ed9937b926b061c0d703c7cce943001413b898ef` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/fresh-gradle-output.txt` | `5f889cebaa8cb4b468abdd486e40e3c8c0872fe3574dcc66dbd2fb560a018d7e` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/populated-command.json` | `173daac18d1a700bf68f38f54321e10d9ee1fa0d46b591f3450b2cd39850ab2a` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/populated-gradle-output.txt` | `af3a2911a7adfc76b9551d9bf4662ffca7a4247b915c51609c4fa803a0e5aee7` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/preflight.json` | `b85b02fee0719d2c87b982354e82539b7fce1302ee5a22eb957433acca180253` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/preserved-workspace-drift.patch` | `4f0e4ba7604ecb96688e8a446838862ec48b23026279102bb205856d7873b0f9` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/regression-blocker.json` | `54d31b45ff57fc97c5013c70507eb5bd1cb34ac99a4c817bf8d1d812bbd5f4b6` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/regression-command.json` | `3deb25aad0c40170ca87e0b0511f7ccdb91a422b5e28b3eb9271aac4c696ab71` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/regression-counts.json` | `60056d38e8df1ec8129eb6113d3ec874eb996522a46077a68386648e21b8fc99` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/regression-failures.json` | `d1516eb23ad35a0f69cc0244f2018145316a0bb0dea26419b6f2734879367f76` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/regression-gradle-output.txt` | `561f20ad40fb5513291f4f4c9f94dc7944f3992d2dabb2354d2de06f5a932249` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/regression-results/migration/integrationTest/TEST-dev.vra.migration.MigrationSecurityIntegrationTest.xml` | `7a2ac593bf72eab4dadcb2b09eca21830a8e31a7bcb8620c11cecf7f7ce3a280` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/regression-results/migration/integrationTest/TEST-dev.vra.migration.OutboxMigrationSecurityIntegrationTest.xml` | `9d2d5f1c70ba576f2e280d0fc5e11e0df70e5b8ed6d53a64a3d78edf4566b775` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/regression-results/migration/integrationTest/TEST-dev.vra.migration.Poc04FoundationMigrationIntegrationTest.xml` | `662cc25536dd8dcf2648cc704d05ce78ecb5d62ef2dcd14bc568c5c6ac3113e1` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/regression-results/migration/test/TEST-dev.vra.migration.Poc04FoundationMigrationIntegrationTest.xml` | `e981c053e4eb066cd330dd0420e378df80897f38214d2ce9c3101e666cc45e19` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/regression-results/runtime/integrationTest/TEST-dev.vra.async.AsyncPermissionIntegrationTest.xml` | `639ffdfb90b0aa0a52775a50fe539f8e206e5c1672ae06589cba313aeacee5c9` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/regression-results/runtime/integrationTest/TEST-dev.vra.async.AsyncVisibilityIntegrationTest.xml` | `4d3a1a5ad7a4f2015233a5927b99c7efd66fad053520864af909bd6e88fc4c5c` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/regression-results/runtime/integrationTest/TEST-dev.vra.async.DeliveryClaimIntegrationTest.xml` | `69fa0829b7973434f8f19d090ac0c0b1a67e6b398b7272be425e1424a5511c6d` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/regression-results/runtime/integrationTest/TEST-dev.vra.async.DeliveryStateIntegrationTest.xml` | `15feac3bb20858fcca2c65793d3ce98be0bb9b731081c7f1d4ed17cb45e11082` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/regression-results/runtime/integrationTest/TEST-dev.vra.async.ProducerOutboxIntegrationTest.xml` | `035d5e5a77b359e6199de30ef97b391663d05e4ef168e9a3fbf47c5c62c490cd` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/regression-results/runtime/integrationTest/TEST-dev.vra.async.ProjectionInboxIntegrationTest.xml` | `9c05d5e08583956099d04ab39a573236c1ddc86d05c0143422e395f2130ac1d2` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/regression-results/runtime/integrationTest/TEST-dev.vra.async.ProjectionRebuildIntegrationTest.xml` | `500229555e991ebcd502b5a90b74c304f49474699a0a1ea90e51d93de216ae6b` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/regression-results/runtime/integrationTest/TEST-dev.vra.async.ReconciliationIntegrationTest.xml` | `259f46d61fa0235b6b2815295f481a89290072db44145bdbebd4b48aeb53ed82` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/regression-results/runtime/integrationTest/TEST-dev.vra.async.RetryReplayIntegrationTest.xml` | `fbe78c011f486a1a038fb31c93631602674c524d1c8d35802754f2a541cf3d9c` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/regression-results/runtime/integrationTest/TEST-dev.vra.async.ValidationSimulatorIntegrationTest.xml` | `f3a21dc48db21ddd819e31830c0ae50dd65be5aaee3ffe7dec33381c4f952adc` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/regression-results/runtime/integrationTest/TEST-dev.vra.async.WorkerProcessCrashIntegrationTest.xml` | `c1a217abda1881bc4b7622d7a3bf9dec4078b416ba3b64a4a15da02ecac24989` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/regression-results/runtime/integrationTest/TEST-dev.vra.inventory.IdempotentReservationApplicationServiceIntegrationTest.xml` | `0d23ef50f048ee784ec8d23a5a280c7298584a798a37ea78737e0ddb5d16535b` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/regression-results/runtime/integrationTest/TEST-dev.vra.inventory.ReservationConcurrencyIntegrationTest.xml` | `5645ac8e425dff33c9613e23459f4336c6678f26fe34620c5a3f4e4cea093add` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/regression-results/runtime/integrationTest/TEST-dev.vra.inventory.ReservationHttpPostgresIntegrationTest.xml` | `ed5e523bd5b92e1fbf02883d2e8b04b15c12ae198b6f598a4aea9aeb13e4c778` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/regression-results/runtime/integrationTest/TEST-dev.vra.inventory.ReservationIdempotencyRepositoryIntegrationTest.xml` | `97b64edaa579aaf65c5cdb62ba816e868ec99fa01d05a2179b22c6c22e2481d5` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/regression-results/runtime/integrationTest/TEST-dev.vra.inventory.ReservationTransactionIntegrationTest.xml` | `22cd8f3676afad4f6ab2fa389ff5bbe12b906b929489988ca77e7bec0fcc204e` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/regression-results/runtime/integrationTest/TEST-dev.vra.platform.health.HealthProbePostgresIntegrationTest.xml` | `42bcf9c55cc425346b0e12524004397b89041c17300dd0edf82859e83316a0f7` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/regression-results/runtime/test/TEST-dev.vra.async.AsyncPropertiesTest.xml` | `4ee42e4c4a9105c3fbc1badc2bf107ee5adfb0ad3c22f8124bba1e82e6f667d0` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/regression-results/runtime/test/TEST-dev.vra.async.DeliveryRetryPolicyTest.xml` | `13884ec673bc90aa80e8bfaf05ef1952ef67f8265c9abede0be6d8da29bb6ca7` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/regression-results/runtime/test/TEST-dev.vra.async.ReconciliationPolicyTest.xml` | `cbd55c93098564b4a195fc68dc0ffebd1c618720a835884fb2494b95b58dd091` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/regression-results/runtime/test/TEST-dev.vra.async.ReservationCreatedEventCodecTest.xml` | `ce4cd9c8c3c867bcdbca54b712fc50a684ab75e32290b9efcf818b97b9ca18d1` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/regression-results/runtime/test/TEST-dev.vra.async.architecture.AsyncArchitectureTest.xml` | `bb854deacb13e47f744fded0806631bd80d37914c3293ee353116da9b19a9e6b` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/regression-results/runtime/test/TEST-dev.vra.inventory.adapter.in.web.InventoryReservationControllerTest.xml` | `a877c392f2324f484bed5f3551cf66c175a925dc92de415683e347b481de9bf5` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/regression-results/runtime/test/TEST-dev.vra.inventory.application.ReservationApplicationServiceTest.xml` | `51083fb937199ea28aae545d3482577f243441e7d100d5fced03f1aca67a7fed` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/regression-results/runtime/test/TEST-dev.vra.inventory.application.ReservationRequestFingerprintTest.xml` | `4cbaec19f6f15e814d717913c862c873349c69e9e25108af8c919211e0966a76` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/regression-results/runtime/test/TEST-dev.vra.inventory.architecture.InventoryArchitectureTest.xml` | `7a8af3d868785567ac3f510fc9d7f232f6da84d158cf7d6136d1ba7682c39e34` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/regression-results/runtime/test/TEST-dev.vra.inventory.domain.InventoryReservationTest.xml` | `19c987d93bd2b89f87849e0d8c4a1112550efb630c58296ddc15ab63cddc2c59` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/regression-results/runtime/test/TEST-dev.vra.platform.configuration.RuntimeDatabasePropertiesTest.xml` | `99e4a3d0a8b8f78a7bdaddc5ac3332cb532e185d9d9ca3bf51d99a031ab64d72` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/resource-identities.jsonl` | `bb91358581d6fab22c0fbbf1e9259191cf469e7b19d88625038456f3a6e999c6` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/resource-recorder.py` | `a62abf850e9b7445c852804c50e61084a47824bd5e5ece3f0ab0e8d2066c2245` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/runner.init.gradle` | `1255ee9276b75e8f33819c911dc63aad7bedfcead64adb14f36a91a4f5657b34` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/source-change.patch` | `869616b5b47f31fdbb65e22767fa9a81880fb1702849e957e027a4b718ee3d08` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/source-review.json` | `8895a04652b2cbc976472f21f9831de6095635f39f1266bcf05dc8bb4d85530e` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/yaml-validation.json` | `a1d2d1e9c7b247be0da4222581e702642544598ab391f640ad03f80f207b0ae8` |

No staging, commit, push, merge, reset, stash, clean, switch or blanket Docker prune was performed. Execution stops here pending review of the preserved regression infrastructure failure; Phase 1 remains open.

---

## Latest execution update — isolated Docker IPAM remediation, 2026-10-01

PHASE 1 CLOSED: **NO**

READY FOR INDEPENDENT PHASE-1 REVIEW: **YES**

All successful implementation/database/security results are **REPORTED PASS pending independent Phase-1 review**. This execution checkpoint does not accept the source implementation or close Phase 1. POC-04 and every S0–S15 gate remain **NOT VERIFIED**. Phase 2 remains **NOT AUTHORIZED / NOT STARTED**.

This section is appended after the exact historical report bytes. The previous report SHA-256 `7e6d75823db3d1ff81f0f2974316ebe6e38ca0ff5039e937e6f913360c4dbbf9` is preserved byte-for-byte in `IMPLEMENTATION_REPORT.previous-7e6d7582.md` and as this report’s first 185250 bytes. Earlier infrastructure, image-model, mount-model and address-pool failures remain FAIL/NOT EXECUTED evidence. The earlier 38 runtime integration failures remain historical infrastructure FAIL evidence; this retry does not relabel them PASS.

Run UUID: `e47077d5-f7d8-463a-be2d-7acd2c5c0bcf`. Every Docker call used `DOCKER_HOST=unix:///Users/siwakornbundi/.colima/vra-poc04-test/docker.sock`; Testcontainers used `/var/run/docker.sock` and host `127.0.0.1`. Ryuk remained enabled. The same daemon ID `14c8ff26-86d1-4c0b-88a6-560be91a65ea`, name `colima-vra-poc04-test`, Docker server 29.5.2 linux/arm64 was independently attested. Docker Desktop and persistent vra-poc00 resources were neither enumerated nor operated on. The saved global Docker context remained `desktop-linux`; the explicit isolated socket controlled every Docker command.

### IPAM diagnosis and supported profile change

Read-only commands and exact exit statuses/output are preserved in [read-only-diagnosis.json](validation/poc-04/evidence/phase1/ipam-remediation-e47077d5-f7d8-463a-be2d-7acd2c5c0bcf/read-only-diagnosis.json): `docker info --format '{{json .DefaultAddressPools}}'`, `docker network ls --no-trunc`, safe network ID/name/driver/IPAM inspection, `netstat -rn -f inet`, `colima status --profile vra-poc04-test`, `colima list -j`, supported saved-profile inspection, and target VM route/address inspection. All 42 prior container IDs, 36 prior volume names and 36 prior network IDs were independently absent before configuration mutation. The endpoint contained no containers or volumes and only Docker built-in bridge/host/none networks.

The observed failure was automatic subnet allocation exhaustion while registered run-owned regression networks were still live. No post-run leaked network, unexpected second daemon, final cleanup failure, or current host/VPN route exclusion was found. Initial `DefaultAddressPools` was JSON `null`. Host LAN `192.168.5.0/24`, Colima VM `192.168.64.0/24` (address `192.168.64.2`) and old Docker bridge `172.17.0.0/16` were checked. No specific observed route/subnet overlaps `10.241.0.0/16`. The routing default is excluded from allocation-overlap comparison. Raw routes remain preserved; the capacity parser explicitly normalizes abbreviated macOS IPv4 CIDRs, including multicast `224.0.0.0/4` (which does not overlap the selected RFC1918 pool).

Supported Colima 0.10.3 saved configuration path was derived from its exact official source, recorded in [pool-selection.json](validation/poc-04/evidence/phase1/ipam-remediation-e47077d5-f7d8-463a-be2d-7acd2c5c0bcf/pool-selection.json). Only `/Users/siwakornbundi/.colima/vra-poc04-test/colima.yaml` changed outside the repository; unrelated parsed settings remained equal. No ad-hoc `/etc/docker/daemon.json` edit was performed.

```yaml
docker:
  default-address-pools:
    - base: 10.241.0.0/16
      size: 24
```

Profile SHA-256 before: `5ac8386a8b4ad2ff11517e08a1fd2a8df4a079c2fb99e2b463e854392cbd547f`; after: `53675defaacbcb1a36f59a3760a61142b16199d4e8a513e6ac63e44fd9505d68`. This provides 256 /24 child allocations; the daemon’s recreated default bridge uses one.

Only `colima stop --profile vra-poc04-test` (exit 0) and `colima start --profile vra-poc04-test --activate=false --save-config=false` (exit 0) ran. The profile/data was retained. Both Client and Server responded afterward and the effective pool was exactly `[{"Base":"10.241.0.0/16","Size":24}]`. See [profile-stop.json](validation/poc-04/evidence/phase1/ipam-remediation-e47077d5-f7d8-463a-be2d-7acd2c5c0bcf/profile-stop.json), [profile-start.json](validation/poc-04/evidence/phase1/ipam-remediation-e47077d5-f7d8-463a-be2d-7acd2c5c0bcf/profile-start.json) and [post-restart-attestation.json](validation/poc-04/evidence/phase1/ipam-remediation-e47077d5-f7d8-463a-be2d-7acd2c5c0bcf/post-restart-attestation.json). Docker normally recreated the built-in bridge on daemon restart; its new exact ID/subnet is recorded, while host/none IDs and daemon ID remained unchanged. The overly strict first metadata bridge-ID assertion is preserved in `post-restart-attestation-attempt-1.json`; it was not a PostgreSQL/product failure. The initial capacity metadata parser error occurred before resource creation and remains in `capacity-probe-preflight-parser.json`.

### Capacity, collector and build execution

Capacity command `python3 /tmp/vra-poc04-ipam-capacity-probe.py` exited **0**. All **48** temporary user-defined bridge networks were created successfully with TEST/POC04/purpose `ipam-capacity-probe`/run UUID labels. Allocations `10.241.1.0/24` through `10.241.48.0/24` were pairwise disjoint and non-overlapping with all specific observed host/Colima/prohibited subnets. Exact IDs/subnets/labels and individual create/inspect/remove commands are in [capacity-probe.json](validation/poc-04/evidence/phase1/ipam-remediation-e47077d5-f7d8-463a-be2d-7acd2c5c0bcf/capacity-probe.json). Only those 48 exact IDs were removed, then individually proven absent. No prune ran.

The runtime integration retry and full normal backend check/build were separate forced executions from `backend/`; neither used cached test results. Exact commands follow; command JSON also records timestamps, source/daemon/run bindings and non-secret environment.

```text
./gradlew --no-daemon -Dorg.gradle.java.installations.auto-detect=false -Dorg.gradle.java.installations.paths=/var/folders/6p/8cx8_9294s10q4v934jqdn9h0000gn/T/vra-poc04-jdk-x0q7h_vo/jdk-21.0.12.1+1/Contents/Home -I /var/folders/6p/8cx8_9294s10q4v934jqdn9h0000gn/T/vra-poc04-ipam-support-cjjo53er/runner.init.gradle :runtime:integrationTest --rerun-tasks
./gradlew --no-daemon -Dorg.gradle.java.installations.auto-detect=false -Dorg.gradle.java.installations.paths=/var/folders/6p/8cx8_9294s10q4v934jqdn9h0000gn/T/vra-poc04-jdk-x0q7h_vo/jdk-21.0.12.1+1/Contents/Home -I /var/folders/6p/8cx8_9294s10q4v934jqdn9h0000gn/T/vra-poc04-ipam-support-cjjo53er/runner.init.gradle check build --rerun-tasks
```

Both commands exited **0**. Relevant task execution is preserved in [runtime-retry-gradle-output.txt](validation/poc-04/evidence/phase1/ipam-remediation-e47077d5-f7d8-463a-be2d-7acd2c5c0bcf/runtime-retry-gradle-output.txt) and [check-build-gradle-output.txt](validation/poc-04/evidence/phase1/ipam-remediation-e47077d5-f7d8-463a-be2d-7acd2c5c0bcf/check-build-gradle-output.txt); per-test XML is in `runtime-retry-results/` and `check-build-results/`. The full `check build` executed the required migration/runtime unit and PostgreSQL integration tasks, compilation, packaging and normal project checks. No Gradle parallelism, source, test dependency or existing external Testcontainers runner was changed.

| Execution / suite | Tests | Skipped | Failures | Errors | Exit |
|---|---:|---:|---:|---:|---:|
| Runtime integration retry | 113 | 0 | 0 | 0 | 0 |
| check/build :migration:test | 4 | 0 | 0 | 0 | 0 |
| check/build :migration:integrationTest | 18 | 0 | 0 | 0 | 0 |
| check/build :runtime:test | 56 | 0 | 0 | 0 | 0 |
| check/build :runtime:integrationTest | 113 | 0 | 0 | 0 | 0 |
| check/build total | 191 | 0 | 0 | 0 | 0 |
| Current attempt total executions | 304 | 0 | 0 | 0 | 0 |

The requested historical count 102 is reconciled explicitly: the earlier `WorkerProcessCrashIntegrationTest` class contributed one collapsed `initializationError`; this run executed its 12 existing test cases, increasing the count by 11 to **113**. Class source and all 27 Phase-1 implementation files are unchanged. Every existing case executed; none were disabled or skipped. See [runtime-count-reconciliation.json](validation/poc-04/evidence/phase1/ipam-remediation-e47077d5-f7d8-463a-be2d-7acd2c5c0bcf/runtime-count-reconciliation.json).

Collector infrastructure-only command `python3 -u /var/folders/6p/8cx8_9294s10q4v934jqdn9h0000gn/T/vra-poc04-ipam-support-cjjo53er/collector-boundary-proof.py` exited **0**, after a guard verified the successful runtime retry. It used the already prepared restricted TEST Compose profile with a temporary execution override, fresh labeled PGDATA/source volumes, exact immutable PostgreSQL pin and disposable isolated network. No observer service, append capability, classifier, parser or webhook application behavior was introduced. See [collector-command.json](validation/poc-04/evidence/phase1/ipam-remediation-e47077d5-f7d8-463a-be2d-7acd2c5c0bcf/collector-command.json), [collector-boundary/summary.json](validation/poc-04/evidence/phase1/ipam-remediation-e47077d5-f7d8-463a-be2d-7acd2c5c0bcf/collector-boundary/summary.json) and safe individual command/mount/resource evidence in `collector-boundary/`.

Effective PostgreSQL collector settings were asserted. The collector-only DB was `vra_poc01`, system identifier `7691606189022396461`, runtime `17.11 (Debian 17.11-1.pgdg13+2)` / numeric version `170011`. Directory ownership/mode was server UID 999, reserved reader GID 9404, mode 2750; JSON log file ownership/mode was 999:9404 / 0640. Only safe filesystem metadata was saved; no raw log contents were exported.

The reserved native OS reader probe (9404:9404) read through the exact `ro,nocopy` source mount and could not write. A synthetic nonmember probe (9405:9405) could neither read nor write. Both had no network, Docker socket, administrator credential, DB credential or PGDATA mount; root filesystem was read-only with dropped capabilities/no-new-privileges. **This is a prepared TEST boundary proof, not a claim that 9405 is an application/worker deployment UID.** The actual prepared profile gives runtime/worker no collector-source mount. Actual relevant runtime/worker/helper LOGIN credentials independently proved 27 `42501` denials (nine identities × server file read / log directory read / logging parameter SET); wrong-password authentication was separately rejected. Catalog rights for `pg_read_server_files`, `pg_monitor` and logging SET were false. The same-byte existing V4 actual-credential/catalog proof supplies business/protected-audit restrictions; no V4 remigration is claimed in this collector-only database. The temporary admin credential file was outside the repository, generated at execution time, never logged, and removed.

### Database/security proof retained and repeated

The complete independent raw catalog/history/row-digest/SQLSTATE evidence remains unchanged in [database-proof.json](validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/database-proof.json) (command exit 0 in [database-proof-command.json](validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/database-proof-command.json)). Its fresh and populated disposable databases both reported PASS. The current normal check/build independently repeated all three Phase-1 integration methods: populated upgrade, fresh boundary/validate/rerun, and administrator-bootstrap convergence/drift rejection, with **3 tests / 0 skipped / 0 failures / 0 errors** within the 18 migration integration tests. Current exact database/container/volume/network identities and safe daemon mounts are preserved in `resource-identities.jsonl`, `docker-resource-events.jsonl` and `daemon-inspect/`. The earlier detailed captures are source-bound to the exact unchanged implementation bytes; no earlier NOT EXECUTED evidence is claimed as execution.

| Detailed captured scenario | Logical DB | PostgreSQL system identifier | Exact container ID | Result |
|---|---|---|---|---|
| fresh | vra_poc01 | `7691593394851569708` | `34ffe2e8cb5f9a584e6857e2390ed69cf9cb0c16e8c033a069143fd62c2d8cfa` | REPORTED PASS |
| populated | vra_poc01 | `7691593475206656044` | `46b25f5c7ae46947bc4dc187cac14c5106c746d40b93563e8e64eeb1fce9db4f` | REPORTED PASS |

Both captured DBs ran PostgreSQL 17.11 / 170011 on aarch64. Flyway history V1/V2/V3 checksums are respectively **-290113401 / -1049090241 / -70525330**, unchanged before/after upgrade; V4 checksum **1313157959**, applied exactly once. Fresh applied four migrations, populated upgrade one, same-DB rerun zero; validate passed. Row digests/catalog/history were unchanged on rerun. Populated retained data: inventory balance/reservation one each; V2 transient/terminal-success/terminal-rejection idempotency three; outbox event/delivery/history, inbox, reservation projection and reconciliation case/history one each. Before/after/final row digests and complete prior Flyway history matched.

Role catalog: all three new executors (`vra_sync_executor`, `vra_security_executor`, `vra_telemetry_executor`) are NOLOGIN, NOINHERIT, NOSUPERUSER, NOCREATEDB, NOCREATEROLE, NOREPLICATION, NOBYPASSRLS. The three TEST helpers (`vra_factor_fixture`, `vra_audit_evidence_reader`, `vra_security_telemetry_observer`) have LOGIN and the same six negative attributes. Direct membership edges are exactly migrator→owner and owner→async/sync/security/telemetry executors, every edge SET TRUE / INHERIT FALSE / ADMIN FALSE. No other workload/helper membership edge exists. The complete transitive SET graph has nine pairs: migrator→owner plus each of four executors, and owner→each of four executors. Ordinary workload/helper SET paths to executors are empty; the migrator administrative path is accepted. Actual credential SET denials complement catalog evidence.

Final `vra` schema CREATE holders are only `postgres` (administrator) and `vra_owner`; executors and ordinary/helper workloads have USAGE without CREATE. PUBLIC table, sequence and trusted-schema unexpected grants are empty. All 17 authoritative V4 tables, their indexes and declared foundation composite types are owned by `vra_owner`; no ordinary owner, Phase-2 capability function or executor early DML authority exists. Full type/PK/FK/CHECK/UNIQUE/index/column ACL catalog is preserved in the detailed database proof and verified by current integration execution.

Runtime V4 rights are the approved boundary: SELECT for authority tables, sessions/factors/staff/proposal/approval; POC account/org records SELECT and UPDATE(label,version) only; protected audit no direct rights; security event approved APP column INSERT and limited safe SELECT (no observer provenance insertion); webhook receipt SELECT/approved INSERT/UPDATE(status), webhook target SELECT/UPDATE(state,version). Workers/reconciler/helpers gained no new V4 DML. Existing V1–V3 grants remain, including the accepted V2 runtime direct INSERT/UPDATE residual; no revocation/function replacement was pulled forward.

Each detailed scenario recorded 71 real SQL negative cases with transaction rollback and unchanged authoritative rows: 58 permission denials `42501`, nine CHECK denials `23514`, three uniqueness denials `23505`, one FK denial `23503` (142 across fresh/populated). These include runtime/all relevant workload/helper executor SET, runtime DDL/migration/role mutation, executor schema CREATE, audit/session/authority mutation, APP provenance/grant forgery and approved representative invalid state/version/generation/digest/tuple/FK/actor shapes. Administrator bootstrap drift rejection separately uses `P0001` in its dedicated passing test. Phase-2 function denial was not claimed.

### Cleanup, immutable source and final validation

All exact labels/resource IDs and teardown commands/exit statuses are preserved in [cleanup-proof.json](validation/poc-04/evidence/phase1/ipam-remediation-e47077d5-f7d8-463a-be2d-7acd2c5c0bcf/cleanup-proof.json), [runtime-cleanup-proof.json](validation/poc-04/evidence/phase1/ipam-remediation-e47077d5-f7d8-463a-be2d-7acd2c5c0bcf/runtime-cleanup-proof.json), [resource-incarnation-inventory.json](validation/poc-04/evidence/phase1/ipam-remediation-e47077d5-f7d8-463a-be2d-7acd2c5c0bcf/resource-incarnation-inventory.json) and the capacity/collector artifacts. Run-owned recorded identities: **64 unique containers, 40 unique volume names, 59 unique networks**, plus the separate **48 capacity-probe network IDs**. There were **60 volume creation incarnations**: twenty names were freshly recreated only after their first incarnation was individually proven absent in `runtime-cleanup-proof.json`; no earlier persistent data was reused. Every exact final ID/name and all 48 capacity IDs are absent. Final target daemon has **0 containers, 0 volumes, exactly three post-restart built-in networks**. No blanket prune, other daemon or shared/persistent resource operation occurred.

| Remaining built-in | Exact ID | Subnet |
|---|---|---|
| bridge | `c0297a5ce0209b66940193c63975087c4355c0e38d6517ac408d4fbf452c9adf` | 10.241.0.0/24 |
| host | `71919e79066c633ba4548aa04103de53d1e31552893e0cec59e0ba812b5f4d22` | none |
| none | `0984d415001adca8ca8a82f1903d6ca27f318228edcf69e42a7bede8a4a05b6a` | none |

All **27** Phase-1 source/config implementation files are byte unchanged this IPAM turn. The **261-file canonical source manifest SHA-256** remains `810e22b6a49302dfc213b25d039182481ce2234da9ba92ad930a51ce303d85cc`; authority/ADR/plan/spec/pin bytes and built JAR hashes are in [final-source-binding.json](validation/poc-04/evidence/phase1/ipam-remediation-e47077d5-f7d8-463a-be2d-7acd2c5c0bcf/final-source-binding.json). V1/V2/V3 match both Phase-0 recorded bytes and HEAD exactly:

| Migration | Current SHA-256 | Phase-0 / HEAD comparison |
|---|---|---|
| `backend/migration/src/main/resources/db/migration/V1__inventory_reservation_foundation.sql` | `d804cbe5153b9a904ae0747af55c8295079d4a0c3edb4a38145ab5199b5b59c4` | EXACT MATCH |
| `backend/migration/src/main/resources/db/migration/V2__inventory_reservation_idempotency.sql` | `7bef750b59b7b77e8402f4f42c13c21c4e072092504b9f268bace9b72de8d2bb` | EXACT MATCH |
| `backend/migration/src/main/resources/db/migration/V3__outbox_recovery.sql` | `9edbd15ffe08ee4edafa3f549856ee38a782485c54db9c73e69d4263e5ca26b4` | EXACT MATCH |

V4 SHA-256: `45161b8c54f10778a5521cdd23dc0945fadb65364bd8094e3d19d8f79cfdd1e8`. Bootstrap SHA-256: `976cf704687e5bf005de8943c706fdd996d801172c112d640c993e0b193407b5`. Phase-1 fixture SHA-256: `50e601ce5fb4bd009aa1d48d1459dd76cfb44171e7d470644ba43f373e2fd5ee`. No application/test/SQL/bootstrap/grant/Gradle/dependency source was modified. The user-owned AsyncVisibility test whitespace difference recorded in earlier evidence remains preserved.

Final static command `python3 /tmp/vra-poc04-ipam-final-validate.py` exited **0** before report append; it is repeated after append to verify every newly bound hash claim and current report bytes. The reproducible source is [final-validator.py](validation/poc-04/evidence/phase1/ipam-remediation-e47077d5-f7d8-463a-be2d-7acd2c5c0bcf/final-validator.py) and the pre-append validation snapshot is [artifact-validation.json](validation/poc-04/evidence/phase1/ipam-remediation-e47077d5-f7d8-463a-be2d-7acd2c5c0bcf/artifact-validation.json). `git diff --check` exited **0**. Separate whitespace/newline inspection passed source/config/report; exact raw immutable manifest no-final-newline bytes, raw Gradle/JUnit stdout trailing spaces and unified-diff blank context markers remain classified historical/raw-evidence exceptions. JSON/JSONL/XML parses and both changed/prepared YAML parses passed. Gitleaks 8.30.1 redacted scan covered every changed file; raw exit **1** represents exactly three known generic-api-key false positives for the recorded Git HEAD SHA-1 in old `origin/poc/04-security-auth` fields. Manual safe classification found **0 actual secrets**, with no ignore rules added. No raw secrets/logs were printed or persisted.

Phase-2 additions/behavior: **NO**. V5: **NO**. V6: **NO**. Observer/parser/capability functions/new API or worker identity-validator work: **NO**. The repository already contains the pre-existing POC-02 `JdbcReservationIdempotencyRepository.java`; its bytes exactly match HEAD and it was not implemented or modified for Phase 2. Do not read “Phase-2 absence” as removal/absence of that accepted legacy file.

Starting/current branch: `poc/04-security-auth`; HEAD: `01256d96d85823740a8d6c3a3f99346afbdbef56`. Both recorded remote refs match this HEAD exactly. Working tree contains existing Phase-1 changes plus preserved/current TEST evidence. Git inventory: **20 tracked modified paths / 320 untracked paths / 0 staged paths**. Exact categorized paths are in [final-git-inventory.json](validation/poc-04/evidence/phase1/ipam-remediation-e47077d5-f7d8-463a-be2d-7acd2c5c0bcf/final-git-inventory.json); the current path/hash inventory below enumerates every modified/created repository file other than this report’s self hash. The external profile modification is separately bound. This report SHA-256 is delivered in the final response to avoid a self-referential digest.

### Required execution report checklist

| Item | Result / evidence |
|---|---|
| 1 Docker/Testcontainers endpoint | Dedicated isolated daemon attested; same ID; Ryuk enabled/passing. |
| 2 PostgreSQL version/image | 17.11 / 170011; frozen immutable index d74eeac9…; local arm64/v8 manifest86fa57b4… and config97432f98… separately checked. |
| 3 Fresh V1→V4 | REPORTED PASS; current migration integration repeated. |
| 4 Populated V3→V4 | REPORTED PASS; current migration integration repeated. |
| 5 Flyway validate | REPORTED PASS, both scenarios. |
| 6 Same-DB rerun | Zero migrations / data, catalog and history unchanged. |
| 7 V1–V3 hashes | Exact Phase-0 / HEAD match. |
| 8 Retained data | Prior inventory, all three V2 states, applicable POC03 state and history retained. |
| 9 V4 SHA-256 | 45161b8c54f10778a5521cdd23dc0945fadb65364bd8094e3d19d8f79cfdd1e8 |
| 10 Bootstrap SHA-256 | 976cf704687e5bf005de8943c706fdd996d801172c112d640c993e0b193407b5 |
| 11 Six role attributes | Exact LOGIN/NOLOGIN plus negative attributes; catalog proof. |
| 12 Direct memberships | migrator→owner; owner→four accepted executors; SET true / INHERIT false / ADMIN false. |
| 13 Complete transitive SET | Nine accepted administrative pairs; ordinary paths none. |
| 14 Schema CREATE | postgres and vra_owner only. |
| 15 PUBLIC grants | Unexpected table/sequence/vra schema grants none. |
| 16 V4 owners | vra_owner; all 17 authoritative tables plus declared indexes/types. |
| 17 Runtime V4 grants | Exact approved table/column boundaries, detailed catalog retained. |
| 18 Worker/reconciler grants | No new V4 DML. |
| 19 SQL denial states | 42501 actual credentials/SET/DDL/migration/role/object privileges; rollback/unchanged rows. |
| 20 Constraints | 23514 / 23505 / 23503; rollback/unchanged rows. |
| 21 Collector boundary | Prepared TEST OS/mount + actual LOGIN SQL proof REPORTED PASS; no deployment UID claim or observer implementation. |
| 22 Regression commands | Runtime retry and full check/build exact arrays/env/status in command JSON. |
| 23 Per-suite counts | 4 / 18 / 56 / 113; all zero skips/failures/errors. |
| 24 Total counts | Latest check/build191; this remediation304 test executions, all zero skips/failures/errors. |
| 25 Phase-2 absence | No new Phase-2 path/behavior; unchanged legacy POC02 repository explicitly disclosed. |
| 26 V5 present | NO |
| 27 V6 present | NO |
| 28 Phase 2 started | NO |
| 29 git diff --check | PASS / exit0 |
| 30 Secret scan | No actual secrets; three safely triaged historical Git-hash false positives, raw exit1 disclosed. |
| 31 Tracked modified paths | 20; exact final-git-inventory. |
| 32 Untracked paths | 320; exact final-git-inventory. |
| 33 Staged paths | Empty. |
| 34 Updated report SHA | Final response; historical prefix/archive exact SHA preserved. |
| 35 Blockers | No mandatory Phase-1 execution blocker remains; independent review/acceptance pending. |
| 36 POC-04 | NOT VERIFIED |
| 37 S0–S15 | Every gate NOT VERIFIED |

### Current modified/created path SHA-256 inventory

This inventory binds current bytes; earlier report inventories remain historical and unchanged. Build products are bound separately in `final-source-binding.json`. The report itself is excluded from its own inventory to avoid circular hashing. No staging, commit, push, merge, reset, stash, clean, switch or Git mutation was performed.

| Path | SHA-256 |
|---|---|
| `backend/migration/src/main/resources/db/migration/V4__poc04_identity_security_foundation.sql` | `45161b8c54f10778a5521cdd23dc0945fadb65364bd8094e3d19d8f79cfdd1e8` |
| `backend/migration/src/test/java/dev/vra/migration/MigrationSecurityIntegrationTest.java` | `feb7a8f223c4e90a70123f67aa001b8a9ac152a9532ec6b3009f37370a92119a` |
| `backend/migration/src/test/java/dev/vra/migration/OutboxMigrationSecurityIntegrationTest.java` | `664156057d8b71b9f8702ddead09344060c9cce43fd1021cf780dd430953253a` |
| `backend/migration/src/test/java/dev/vra/migration/Poc04FoundationMigrationIntegrationTest.java` | `50e601ce5fb4bd009aa1d48d1459dd76cfb44171e7d470644ba43f373e2fd5ee` |
| `backend/runtime/src/test/java/dev/vra/async/AsyncPermissionIntegrationTest.java` | `4ad16fe9dffbf2e2f6b3941bd8645f3094f108e5296807e55ee8ef0c63e380e4` |
| `backend/runtime/src/test/java/dev/vra/async/AsyncRoleBootstrap.java` | `80aaf4d4c7fb02878b55dfdb2ff51ed2c6ff36d631d512d1603d702dc0424a20` |
| `backend/runtime/src/test/java/dev/vra/async/AsyncVisibilityIntegrationTest.java` | `089d7f92a2b956195a20b89f393e9a4cea856284abff16558575b8b2df23883d` |
| `backend/runtime/src/test/java/dev/vra/async/DeliveryClaimIntegrationTest.java` | `cc11f8469f940c3d1a97bfeab78e6e4f27e36a959b4bf248dca42f24f0e91c99` |
| `backend/runtime/src/test/java/dev/vra/async/DeliveryStateIntegrationTest.java` | `07246761e5be4677125fbb09d1e11d86db546f1c64a37e5a5c2b7f992b211aa8` |
| `backend/runtime/src/test/java/dev/vra/async/ProducerOutboxIntegrationTest.java` | `88f6301a210bb00fdd21ea138f71e438ce08d5c34fab1c0143f3311450c0fcec` |
| `backend/runtime/src/test/java/dev/vra/async/ProjectionInboxIntegrationTest.java` | `d615be8fc0eadfc28b35e7061d42432f8fff6c1ee9b98533b9a8c19a73d2e727` |
| `backend/runtime/src/test/java/dev/vra/async/ProjectionRebuildIntegrationTest.java` | `21038aafd0ebd855e812e9333e1df760d318ff5c9d0bfab6676e685f55d007c0` |
| `backend/runtime/src/test/java/dev/vra/async/ReconciliationIntegrationTest.java` | `6c22d1094f1a976794c7a8b77dfffbfe17551d81eeb2de5b1531b5d0d24a8ea2` |
| `backend/runtime/src/test/java/dev/vra/async/RetryReplayIntegrationTest.java` | `f72f451ef737750bcc9e4777fd1ecb3c898d2acfd548289be90379c621a44682` |
| `backend/runtime/src/test/java/dev/vra/async/ValidationSimulatorIntegrationTest.java` | `b17af32f9b8ffb7cbc225fd57cf0cad65a6707e3794840cc51b66b0e6f7d265a` |
| `backend/runtime/src/test/java/dev/vra/async/WorkerProcessCrashIntegrationTest.java` | `d1f1f77ec890b26dcf39e7958dca274b5a378cf899b99164d8c9ff7449fc664f` |
| `backend/runtime/src/test/java/dev/vra/inventory/IdempotentReservationApplicationServiceIntegrationTest.java` | `52d0e29beb42a4c371bbe1c23aeaeda56f4e7a251c2585d20134ed51f887ee1c` |
| `backend/runtime/src/test/java/dev/vra/inventory/ReservationConcurrencyIntegrationTest.java` | `819a2a3d5537c40c434c1c175928bb83b0d6a05ca51aa306f4e0b05498304ed4` |
| `backend/runtime/src/test/java/dev/vra/inventory/ReservationHttpPostgresIntegrationTest.java` | `cd0b20f07c48162e76c388dd36bb7e429339aa0bb5b2de271e1696adb9d4f5e4` |
| `backend/runtime/src/test/java/dev/vra/inventory/ReservationIdempotencyRepositoryIntegrationTest.java` | `aeb577cc289476554252af3849263b09e246286bfeeea9cfd316745f7b07ef03` |
| `backend/runtime/src/test/java/dev/vra/inventory/ReservationTransactionIntegrationTest.java` | `5b0b4e0dfdb79875eb87edd1969602982c2d92b4558e18c8e43686260bb94b03` |
| `backend/runtime/src/test/java/dev/vra/platform/health/HealthProbePostgresIntegrationTest.java` | `283b9bb7655679ffa029c83837db025cd7b9425b5dbfdf82a37f731d78aeb45c` |
| `validation/poc-04/db/COLLECTOR_TEST.md` | `09117a00f800c91748017234be2829a86828895ff26a119beb618327fddd50c1` |
| `validation/poc-04/db/bootstrap-security-roles.sql` | `976cf704687e5bf005de8943c706fdd996d801172c112d640c993e0b193407b5` |
| `validation/poc-04/db/collector-test-entrypoint.sh` | `90e6d263e9c121cb2b92dd80e45f49fef71452d5c6f52b1695752d1725fe73d0` |
| `validation/poc-04/db/collector-test.compose.yaml` | `db3c7dec5fa8b1f6e04512e30a4d6dc9b58e11316c53d3301b82a0712db62c47` |
| `validation/poc-04/db/postgresql-collector-test.conf` | `ba1f292521a67965fc3b8f03bf8d39cac460852cdb33f2d8e51d6809036440db` |
| `validation/poc-04/evidence/phase1/IMPLEMENTATION_REPORT.previous-4a4116f1.md` | `4a4116f11c77fdd2d7c503405f24cb53fe69e84c1502fcb2fc049387046eacbb` |
| `validation/poc-04/evidence/phase1/IMPLEMENTATION_REPORT.previous-7e6d7582.md` | `7e6d75823db3d1ff81f0f2974316ebe6e38ca0ff5039e937e6f913360c4dbbf9` |
| `validation/poc-04/evidence/phase1/IMPLEMENTATION_REPORT.previous-93baf961.md` | `93baf961f3370311e1d669e3594ed9c0c2d925ef66d0df70c62f33e61d127a57` |
| `validation/poc-04/evidence/phase1/IMPLEMENTATION_REPORT.previous-b772942a.md` | `b772942af3dc367941cd7e2d553ca2f990542964114fdd3002a8c994941cdd65` |
| `validation/poc-04/evidence/phase1/IMPLEMENTATION_REPORT.previous-e5df43af.md` | `e5df43afd289c8fc9a87cfe0bc25fdf182d6195ea8f8f6bfedf160df4caf0e3e` |
| `validation/poc-04/evidence/phase1/POSTGRES_EXECUTION_PREFLIGHT.json` | `6eee43e49f81d5bbb1db5eea0f60d25539be7c05dc309bec516b6e2bec98357f` |
| `validation/poc-04/evidence/phase1/POSTGRES_EXECUTION_RESUME_PREFLIGHT.json` | `f5245404d9d291d5398ce38a9f295b15e3cbf576a9edfbb53ffc6ab269ce04ff` |
| `validation/poc-04/evidence/phase1/connectivity-57951433-ef13-4dde-9ef5-3d397b096656/RyukConnectivityProbe.java` | `c20c1c90a7dfbbfdaa6b19a8bba91966ad1c95362b67dc37bf803e78543e7e24` |
| `validation/poc-04/evidence/phase1/connectivity-57951433-ef13-4dde-9ef5-3d397b096656/cleanup-proof.json` | `e5240825141c5cc8af43c69b1f50fc1e315562cb5c5ed073ba9e598cdfa95b91` |
| `validation/poc-04/evidence/phase1/connectivity-57951433-ef13-4dde-9ef5-3d397b096656/connectivity-proof.json` | `0d19e54f918faaf6ee338d0e199beafcf1c47a9c6b635f0e66ae2a40346fecd2` |
| `validation/poc-04/evidence/phase1/connectivity-57951433-ef13-4dde-9ef5-3d397b096656/probe-command.json` | `64d31718431dd19b8590dd361335d15d830b400e03b563f022b55525e8e794bf` |
| `validation/poc-04/evidence/phase1/connectivity-57951433-ef13-4dde-9ef5-3d397b096656/probe-output.txt` | `c92f6fab0503fee8895b40124d993e6f906cbf13bb1a93f764b10e04fe369347` |
| `validation/poc-04/evidence/phase1/connectivity-57951433-ef13-4dde-9ef5-3d397b096656/probe-state.json` | `cc5b35d0c6278a7af1fe4a73dbb174775075ef8100d0fc0c692babbdfb2e5715` |
| `validation/poc-04/evidence/phase1/execution-304b3976-4ebe-4a61-8bbc-e67a9a40e669/TEST-dev.vra.migration.Poc04FoundationMigrationIntegrationTest.xml` | `47e6d6c9f8940671ca786e8ce27d55525eda26e7f4b1826720e0eec1d51e5938` |
| `validation/poc-04/evidence/phase1/execution-304b3976-4ebe-4a61-8bbc-e67a9a40e669/cleanup-proof.json` | `a1674fe389b439073a5c029084474b7e651d4903315b0758719a2fc4f5bd993e` |
| `validation/poc-04/evidence/phase1/execution-304b3976-4ebe-4a61-8bbc-e67a9a40e669/docker-resource-events.jsonl` | `0ecfd984d4851afe378a5553abef221cd4d559f76695e16dc119f17c4d29227a` |
| `validation/poc-04/evidence/phase1/execution-304b3976-4ebe-4a61-8bbc-e67a9a40e669/execution-summary.json` | `d0cb9e38c4d90ce8c77301812022f61ecb7588d26cc0680675902ed39b8f6ee5` |
| `validation/poc-04/evidence/phase1/execution-304b3976-4ebe-4a61-8bbc-e67a9a40e669/fresh-command.json` | `218897f4d9e28548f07dad1f90b0a638e80de1defe16bc2fc209efa5104dfb14` |
| `validation/poc-04/evidence/phase1/execution-304b3976-4ebe-4a61-8bbc-e67a9a40e669/fresh-gradle-output.txt` | `3555950acfc946f19c448702dd0a50f459cfee0cc3c971eed986339a0ccea5f0` |
| `validation/poc-04/evidence/phase1/execution-304b3976-4ebe-4a61-8bbc-e67a9a40e669/preflight.json` | `cc2a295672f4587267d716100e7ff49c732f5a21e7e6838b9668ab21e750a0b8` |
| `validation/poc-04/evidence/phase1/execution-304b3976-4ebe-4a61-8bbc-e67a9a40e669/pulled-images.json` | `bd0aafe1c3cb793bd77c9e820d4d128f09a2fccfb46bbbae32d5124e90cfdc8f` |
| `validation/poc-04/evidence/phase1/execution-304b3976-4ebe-4a61-8bbc-e67a9a40e669/ryuk-failure-infrastructure.json` | `784bd8999240ef948305f4102a019359789b612f38bb7a06da9324060276e0b6` |
| `validation/poc-04/evidence/phase1/execution-e0a3d5b9-ab05-453f-b133-39b50828e0b7/TEST-dev.vra.migration.Poc04FoundationMigrationIntegrationTest.xml` | `30736a023d77c7b9ff11c78e16651b5300b2ede035fb80158eedcb0445d0e88d` |
| `validation/poc-04/evidence/phase1/execution-e0a3d5b9-ab05-453f-b133-39b50828e0b7/artifact-validation.json` | `a00eab5f9f8b74f06fcf556e1c1679c2eadf952e0ca7bca9486838d1d117e12b` |
| `validation/poc-04/evidence/phase1/execution-e0a3d5b9-ab05-453f-b133-39b50828e0b7/cleanup-proof.json` | `97ac9bd08884b41e4c3531d04e15096e7d1b0fb515e35bf42dcc43db4c235e69` |
| `validation/poc-04/evidence/phase1/execution-e0a3d5b9-ab05-453f-b133-39b50828e0b7/docker-resource-events.jsonl` | `e206f6bdb728f96562b1d0c67225a4557abeb08d0634eb6561a282f7895ec71c` |
| `validation/poc-04/evidence/phase1/execution-e0a3d5b9-ab05-453f-b133-39b50828e0b7/execution-summary.json` | `975368b8933da29cd140f17aceb54f54dd152e29683dc9faa4b4f535b6395829` |
| `validation/poc-04/evidence/phase1/execution-e0a3d5b9-ab05-453f-b133-39b50828e0b7/final-validation.json` | `d0abfc8311b68300b192308ccbfb626915c28f6e67f520eab0ed0e68b5ddba7d` |
| `validation/poc-04/evidence/phase1/execution-e0a3d5b9-ab05-453f-b133-39b50828e0b7/fresh-command.json` | `8eb2b409c3b7dea4c55d2f96e53b39c63c4ef1edf79196c15e819e42c349ce92` |
| `validation/poc-04/evidence/phase1/execution-e0a3d5b9-ab05-453f-b133-39b50828e0b7/fresh-gradle-output.txt` | `a3f08bd464b1ddc88516ea343b14498541e0e46a5d7b10fa646c061d9305be4e` |
| `validation/poc-04/evidence/phase1/execution-e0a3d5b9-ab05-453f-b133-39b50828e0b7/image-attestation-defect.json` | `c9b489165e3b09d16993e119fdb64890b86beb55e05e1d127d578f8ec7b64b8f` |
| `validation/poc-04/evidence/phase1/execution-e0a3d5b9-ab05-453f-b133-39b50828e0b7/preflight.json` | `dc863f6ace8ef3935f1436e17959cb59f5f13c9620b9f6b7a66ad2576c6f7b3e` |
| `validation/poc-04/evidence/phase1/image-identity-diagnosis-03baf784-dd20-4615-be07-d633535b9b04/diagnosis-summary.json` | `0304ec0a747b40a0e7280f137832d57e19a48834dc7b7468cfd896910c210228` |
| `validation/poc-04/evidence/phase1/image-identity-diagnosis-03baf784-dd20-4615-be07-d633535b9b04/docker-image-semantics.json` | `9c3e68cc3b0a55a1f900ac095fd099938bad8a2803d500e72b49cd52f4bb222c` |
| `validation/poc-04/evidence/phase1/image-identity-diagnosis-03baf784-dd20-4615-be07-d633535b9b04/post-diagnosis-inventory.json` | `92f322398a9a59c5518e05fcaf6609efc14b9ac31849f01c02dcf48f2bf7f935` |
| `validation/poc-04/evidence/phase1/image-remediation-5e8a333f-5b82-4b7d-b513-0fea93933bab/Poc04FoundationMigrationIntegrationTest.before-image-remediation.java` | `125e9cf33055428f4354fe7c0637ba60e2f3857eb3a266c7fa089db7f6c71005` |
| `validation/poc-04/evidence/phase1/image-remediation-5e8a333f-5b82-4b7d-b513-0fea93933bab/TEST-dev.vra.migration.Poc04FoundationMigrationIntegrationTest.xml` | `045add229586d974e16fa2b55f06c1472d71493f03b749c0ecfb5a033c673a77` |
| `validation/poc-04/evidence/phase1/image-remediation-5e8a333f-5b82-4b7d-b513-0fea93933bab/artifact-validation.json` | `75350dfe2de8ca71eec89a1fbd7444819e1ee565e742b8169d9a43a61102f9ea` |
| `validation/poc-04/evidence/phase1/image-remediation-5e8a333f-5b82-4b7d-b513-0fea93933bab/cleanup-proof.json` | `50f21c05209c91be8028ffc6a49be4937a48eaccddfd1f99517dd95bff3ff232` |
| `validation/poc-04/evidence/phase1/image-remediation-5e8a333f-5b82-4b7d-b513-0fea93933bab/docker-resource-events.jsonl` | `0e743fb5f1dcb6e54e7bd97f379ecb60f67f9c382c476687f9ec0cc04a7b086c` |
| `validation/poc-04/evidence/phase1/image-remediation-5e8a333f-5b82-4b7d-b513-0fea93933bab/execution-summary.json` | `db05f146b809121ee5a17fbf5c4b157d39ec35183d243514e0e931ea1da4be30` |
| `validation/poc-04/evidence/phase1/image-remediation-5e8a333f-5b82-4b7d-b513-0fea93933bab/fresh-command.json` | `142bc74a0cdb137a04efe37172b72614d8484832abc90d2e7bbc8ad5d17e6e74` |
| `validation/poc-04/evidence/phase1/image-remediation-5e8a333f-5b82-4b7d-b513-0fea93933bab/fresh-gradle-output.txt` | `53b0e9fb16288c023a6b7cdeaffc4fd0d72328548d2fc967558a3fe6a815a9cc` |
| `validation/poc-04/evidence/phase1/image-remediation-5e8a333f-5b82-4b7d-b513-0fea93933bab/manifest-preflight.json` | `b1ab06561d193b66f99650ae593805a70e29fb93aa820c4dce80d2d1af1bcf0d` |
| `validation/poc-04/evidence/phase1/image-remediation-5e8a333f-5b82-4b7d-b513-0fea93933bab/mount-model-diagnosis.json` | `7f2143751dea96ceedb4638fd039efb916c9cf85fd4bcb48e0bd9c3765fd4b17` |
| `validation/poc-04/evidence/phase1/image-remediation-5e8a333f-5b82-4b7d-b513-0fea93933bab/postgres-arm64-manifest.json` | `86fa57b44a1d38f09970f64ac71eb492f7a9cf94d08b85e1c8f07bb0fc0a1761` |
| `validation/poc-04/evidence/phase1/image-remediation-5e8a333f-5b82-4b7d-b513-0fea93933bab/postgres-index.json` | `d74eeac9a635390a49bc21bd49fccd973de707e2a53a76ac49b552b8712ec46f` |
| `validation/poc-04/evidence/phase1/image-remediation-5e8a333f-5b82-4b7d-b513-0fea93933bab/resource-recorder.py` | `f68ba9d804b59f0e4c955c290e8e23b9dd30eec2871d63c08f7dbbed3cc74af8` |
| `validation/poc-04/evidence/phase1/image-remediation-5e8a333f-5b82-4b7d-b513-0fea93933bab/runner.init.gradle` | `1255ee9276b75e8f33819c911dc63aad7bedfcead64adb14f36a91a4f5657b34` |
| `validation/poc-04/evidence/phase1/image-remediation-5e8a333f-5b82-4b7d-b513-0fea93933bab/source-change.patch` | `ecb4a676c89a583c96e36546a9292300f55589e77aba3c1231c4e2f78a3ed7f1` |
| `validation/poc-04/evidence/phase1/image-remediation-5e8a333f-5b82-4b7d-b513-0fea93933bab/source-review.json` | `e9a2e2d70c50305fc1d9283bfc937698e6f283487c4ef4dc05dbfdd1ac072fc5` |
| `validation/poc-04/evidence/phase1/ipam-remediation-e47077d5-f7d8-463a-be2d-7acd2c5c0bcf/artifact-validation.json` | `b80b2c2ebd8d1b7a0518663ce9a4710dd8bcf014d416d36f7840580b02ec07f2` |
| `validation/poc-04/evidence/phase1/ipam-remediation-e47077d5-f7d8-463a-be2d-7acd2c5c0bcf/capacity-probe-preflight-parser.json` | `b454c1431913203e99206d75812da020c11b062b14dd009fe820209f859515ae` |
| `validation/poc-04/evidence/phase1/ipam-remediation-e47077d5-f7d8-463a-be2d-7acd2c5c0bcf/capacity-probe.json` | `1477e6bdef03ec5a310fead820fb14f43e01db823f1526a74728751b82b49801` |
| `validation/poc-04/evidence/phase1/ipam-remediation-e47077d5-f7d8-463a-be2d-7acd2c5c0bcf/check-build-command.json` | `7577c537d6e93adac5060b5c472241180d9d2ecb19402f505d01f6765c23a993` |
| `validation/poc-04/evidence/phase1/ipam-remediation-e47077d5-f7d8-463a-be2d-7acd2c5c0bcf/check-build-counts.json` | `a350f8fa1fe28c57ae1fd3d03ecb02582e987cd657ab16f819777894b0d8ec15` |
| `validation/poc-04/evidence/phase1/ipam-remediation-e47077d5-f7d8-463a-be2d-7acd2c5c0bcf/check-build-gradle-output.txt` | `5ff307276abd69196db9ec01c2967b70d8cc38f232f10a94f6af39414b913785` |
| `validation/poc-04/evidence/phase1/ipam-remediation-e47077d5-f7d8-463a-be2d-7acd2c5c0bcf/check-build-results/migration/integrationTest/TEST-dev.vra.migration.MigrationSecurityIntegrationTest.xml` | `2ba929ad884e4e28f9474f12e061df9c742a02770031e16ab37e058bc0cd49d2` |
| `validation/poc-04/evidence/phase1/ipam-remediation-e47077d5-f7d8-463a-be2d-7acd2c5c0bcf/check-build-results/migration/integrationTest/TEST-dev.vra.migration.OutboxMigrationSecurityIntegrationTest.xml` | `ff56ca1b3bb0d467bfba65a8e5075735fa33e3b211586e7a3b1d889a9ce26940` |
| `validation/poc-04/evidence/phase1/ipam-remediation-e47077d5-f7d8-463a-be2d-7acd2c5c0bcf/check-build-results/migration/integrationTest/TEST-dev.vra.migration.Poc04FoundationMigrationIntegrationTest.xml` | `cc2cf6f93e224b45ee48ccfa4f837a509538363a0e1ea9ff22f33b291e22ad16` |
| `validation/poc-04/evidence/phase1/ipam-remediation-e47077d5-f7d8-463a-be2d-7acd2c5c0bcf/check-build-results/migration/test/TEST-dev.vra.migration.Poc04FoundationMigrationIntegrationTest.xml` | `99a833ce0ce530b7c263c896fb79f4307a5e64bcb476b9c50619bd44c9969041` |
| `validation/poc-04/evidence/phase1/ipam-remediation-e47077d5-f7d8-463a-be2d-7acd2c5c0bcf/check-build-results/runtime/integrationTest/TEST-dev.vra.async.AsyncPermissionIntegrationTest.xml` | `9afeb963ac7d863f95fc58803067c6841f0b5f16d129e4716552f0f6b0289b63` |
| `validation/poc-04/evidence/phase1/ipam-remediation-e47077d5-f7d8-463a-be2d-7acd2c5c0bcf/check-build-results/runtime/integrationTest/TEST-dev.vra.async.AsyncVisibilityIntegrationTest.xml` | `fb14744abcd72ef05f4d2176c4a16079a2126256ffc2a323eefa2ef70d8be534` |
| `validation/poc-04/evidence/phase1/ipam-remediation-e47077d5-f7d8-463a-be2d-7acd2c5c0bcf/check-build-results/runtime/integrationTest/TEST-dev.vra.async.DeliveryClaimIntegrationTest.xml` | `5cd524e40aec8d743b181e1aeb46f6007528de312045be307c3f30d81b346717` |
| `validation/poc-04/evidence/phase1/ipam-remediation-e47077d5-f7d8-463a-be2d-7acd2c5c0bcf/check-build-results/runtime/integrationTest/TEST-dev.vra.async.DeliveryStateIntegrationTest.xml` | `5fc8266490677d1cbdd2ffeffeddb9d65690614f156b6b5e4a966451b79666ab` |
| `validation/poc-04/evidence/phase1/ipam-remediation-e47077d5-f7d8-463a-be2d-7acd2c5c0bcf/check-build-results/runtime/integrationTest/TEST-dev.vra.async.ProducerOutboxIntegrationTest.xml` | `6f10ddd6b8d6a410a26af76d279eb2cc9eeb19a4db9fcc6ef538b59c7801f8c0` |
| `validation/poc-04/evidence/phase1/ipam-remediation-e47077d5-f7d8-463a-be2d-7acd2c5c0bcf/check-build-results/runtime/integrationTest/TEST-dev.vra.async.ProjectionInboxIntegrationTest.xml` | `1009b88a61a16b1a221ae7284a9ab1cdc841b2707c0354378e528de4c53aafdd` |
| `validation/poc-04/evidence/phase1/ipam-remediation-e47077d5-f7d8-463a-be2d-7acd2c5c0bcf/check-build-results/runtime/integrationTest/TEST-dev.vra.async.ProjectionRebuildIntegrationTest.xml` | `c68ea3e83fffdb22f22c5161fd10012a17326e5c694037c39d141d00b9cde9c8` |
| `validation/poc-04/evidence/phase1/ipam-remediation-e47077d5-f7d8-463a-be2d-7acd2c5c0bcf/check-build-results/runtime/integrationTest/TEST-dev.vra.async.ReconciliationIntegrationTest.xml` | `78ed3af86f70dd8c53af45aacfc5343e8c0ed1f90b8071a551a9bffed0ad8921` |
| `validation/poc-04/evidence/phase1/ipam-remediation-e47077d5-f7d8-463a-be2d-7acd2c5c0bcf/check-build-results/runtime/integrationTest/TEST-dev.vra.async.RetryReplayIntegrationTest.xml` | `e1e7f621e77ab79ad8c92c22b082f4b1d534ae598a0031e48a5b98340c9c18fb` |
| `validation/poc-04/evidence/phase1/ipam-remediation-e47077d5-f7d8-463a-be2d-7acd2c5c0bcf/check-build-results/runtime/integrationTest/TEST-dev.vra.async.ValidationSimulatorIntegrationTest.xml` | `df98c9c88eb6ac3e4ca9be51b9fe6504c3ce167d3e3b104b2d1d024a1d2111cc` |
| `validation/poc-04/evidence/phase1/ipam-remediation-e47077d5-f7d8-463a-be2d-7acd2c5c0bcf/check-build-results/runtime/integrationTest/TEST-dev.vra.async.WorkerProcessCrashIntegrationTest.xml` | `15a61a48bb2cc8a6be77c13363feac7dc659e0d59dc89475b4500d75eada4f99` |
| `validation/poc-04/evidence/phase1/ipam-remediation-e47077d5-f7d8-463a-be2d-7acd2c5c0bcf/check-build-results/runtime/integrationTest/TEST-dev.vra.inventory.IdempotentReservationApplicationServiceIntegrationTest.xml` | `dc5a3c3e12ebe781e348bd3f73f4d41bd61c6d5aad4c44703a81c15d4167b93f` |
| `validation/poc-04/evidence/phase1/ipam-remediation-e47077d5-f7d8-463a-be2d-7acd2c5c0bcf/check-build-results/runtime/integrationTest/TEST-dev.vra.inventory.ReservationConcurrencyIntegrationTest.xml` | `c469a77edb6a67125f66625e1caa52c76fbfb71e06e455e4a201365f0b1f5321` |
| `validation/poc-04/evidence/phase1/ipam-remediation-e47077d5-f7d8-463a-be2d-7acd2c5c0bcf/check-build-results/runtime/integrationTest/TEST-dev.vra.inventory.ReservationHttpPostgresIntegrationTest.xml` | `ee60d88eb66a9518ed6fe405105e35b3e95deb4442f72747c42626c353272ef9` |
| `validation/poc-04/evidence/phase1/ipam-remediation-e47077d5-f7d8-463a-be2d-7acd2c5c0bcf/check-build-results/runtime/integrationTest/TEST-dev.vra.inventory.ReservationIdempotencyRepositoryIntegrationTest.xml` | `e05e428539675800aafd4434a8a2e598592782f73cf173620ae969bcecc3ec5d` |
| `validation/poc-04/evidence/phase1/ipam-remediation-e47077d5-f7d8-463a-be2d-7acd2c5c0bcf/check-build-results/runtime/integrationTest/TEST-dev.vra.inventory.ReservationTransactionIntegrationTest.xml` | `3b8c2af11691b1afca6ee4e129942980ab5c5fbb1569eacbfbd3b26d670a77b7` |
| `validation/poc-04/evidence/phase1/ipam-remediation-e47077d5-f7d8-463a-be2d-7acd2c5c0bcf/check-build-results/runtime/integrationTest/TEST-dev.vra.platform.health.HealthProbePostgresIntegrationTest.xml` | `0182cdf8a683c5c0cd3355cf0e0fe2db8ee417b53bafa9bc5078494d4f4ff704` |
| `validation/poc-04/evidence/phase1/ipam-remediation-e47077d5-f7d8-463a-be2d-7acd2c5c0bcf/check-build-results/runtime/test/TEST-dev.vra.async.AsyncPropertiesTest.xml` | `8c2d3a4477ca1e1ba33d25ca78b1dbfc1909e4399e5cd20a2edb6c07ce659590` |
| `validation/poc-04/evidence/phase1/ipam-remediation-e47077d5-f7d8-463a-be2d-7acd2c5c0bcf/check-build-results/runtime/test/TEST-dev.vra.async.DeliveryRetryPolicyTest.xml` | `a34dea01ec8ced8871d7808f9a1e3ca33fa23cc63046e9526bfeb7312d9554fc` |
| `validation/poc-04/evidence/phase1/ipam-remediation-e47077d5-f7d8-463a-be2d-7acd2c5c0bcf/check-build-results/runtime/test/TEST-dev.vra.async.ReconciliationPolicyTest.xml` | `c9842f215bedbf003da02d050022bc847270dc994f9b0c0d459bb28748408e9e` |
| `validation/poc-04/evidence/phase1/ipam-remediation-e47077d5-f7d8-463a-be2d-7acd2c5c0bcf/check-build-results/runtime/test/TEST-dev.vra.async.ReservationCreatedEventCodecTest.xml` | `4b9c4db4324d9f36b73aa67c95cf1ddc20eb9ff0e9dc7457efad4816addc307d` |
| `validation/poc-04/evidence/phase1/ipam-remediation-e47077d5-f7d8-463a-be2d-7acd2c5c0bcf/check-build-results/runtime/test/TEST-dev.vra.async.architecture.AsyncArchitectureTest.xml` | `e2990db894c4e6b12d8c5d7d7a8a9b24e0ebe28f901215c55d742be2398599eb` |
| `validation/poc-04/evidence/phase1/ipam-remediation-e47077d5-f7d8-463a-be2d-7acd2c5c0bcf/check-build-results/runtime/test/TEST-dev.vra.inventory.adapter.in.web.InventoryReservationControllerTest.xml` | `923b1f339e7d83759a2ff112853db303c5c7e4e9fbef46e288cf3b0ce4641bc5` |
| `validation/poc-04/evidence/phase1/ipam-remediation-e47077d5-f7d8-463a-be2d-7acd2c5c0bcf/check-build-results/runtime/test/TEST-dev.vra.inventory.application.ReservationApplicationServiceTest.xml` | `edbce23c9c7926e47c4ecfb86d3d3ac972bec5e1cec58afdccee95dcf8b2fc48` |
| `validation/poc-04/evidence/phase1/ipam-remediation-e47077d5-f7d8-463a-be2d-7acd2c5c0bcf/check-build-results/runtime/test/TEST-dev.vra.inventory.application.ReservationRequestFingerprintTest.xml` | `76c0b5d24c1f906c6e8fa159e91f9a91e6efa6aec33aeae43f1d6c9ce60c2c66` |
| `validation/poc-04/evidence/phase1/ipam-remediation-e47077d5-f7d8-463a-be2d-7acd2c5c0bcf/check-build-results/runtime/test/TEST-dev.vra.inventory.architecture.InventoryArchitectureTest.xml` | `e31a6e2403b31135be397e69c4e223fadb910e4074260ba859fbfc8f02fb627c` |
| `validation/poc-04/evidence/phase1/ipam-remediation-e47077d5-f7d8-463a-be2d-7acd2c5c0bcf/check-build-results/runtime/test/TEST-dev.vra.inventory.domain.InventoryReservationTest.xml` | `ce682fb736a4c01398ba9cae0b1bef521e855be06f748cc801a93fb0e4f94b17` |
| `validation/poc-04/evidence/phase1/ipam-remediation-e47077d5-f7d8-463a-be2d-7acd2c5c0bcf/check-build-results/runtime/test/TEST-dev.vra.platform.configuration.RuntimeDatabasePropertiesTest.xml` | `bc70f655b4daee518b196c6c515dda9bdf146922b60650ca0a859f87edd773bd` |
| `validation/poc-04/evidence/phase1/ipam-remediation-e47077d5-f7d8-463a-be2d-7acd2c5c0bcf/cleanup-proof.json` | `2ec789b61d4b8ec36a177c39d23dabbd5a8224b9987d270fe743b1663a4fa869` |
| `validation/poc-04/evidence/phase1/ipam-remediation-e47077d5-f7d8-463a-be2d-7acd2c5c0bcf/collector-boundary-proof.py` | `e6cea276e02d7d06fa7c1fdb5bb73dca265154b21d4bd810906de852baa2155b` |
| `validation/poc-04/evidence/phase1/ipam-remediation-e47077d5-f7d8-463a-be2d-7acd2c5c0bcf/collector-boundary/commands.json` | `9262d64a88f9253f68ebbe625b246fcd4c8f3f83504c5b55e9266c9528eee0b6` |
| `validation/poc-04/evidence/phase1/ipam-remediation-e47077d5-f7d8-463a-be2d-7acd2c5c0bcf/collector-boundary/resources.json` | `82c0120c2feb7bcd39cf925c7cfc73f5929d1c79bb0290a26ea881b33c8f7562` |
| `validation/poc-04/evidence/phase1/ipam-remediation-e47077d5-f7d8-463a-be2d-7acd2c5c0bcf/collector-boundary/summary-before-cleanup.json` | `209a014268bd3d05a51b72d162a05e757079f4fd1391d3289f47912814417e26` |
| `validation/poc-04/evidence/phase1/ipam-remediation-e47077d5-f7d8-463a-be2d-7acd2c5c0bcf/collector-boundary/summary.json` | `15e15278d42858430721618dc5bbacd76b9ecf504f9d42c3f6809020b743a7cd` |
| `validation/poc-04/evidence/phase1/ipam-remediation-e47077d5-f7d8-463a-be2d-7acd2c5c0bcf/collector-command.json` | `43af35f809bf12bded444b621e41e03051b0a89cbab581c535849be6a2dd7484` |
| `validation/poc-04/evidence/phase1/ipam-remediation-e47077d5-f7d8-463a-be2d-7acd2c5c0bcf/collector-proof-output.txt` | `6af2ca94aa6cc5dbc7d960ecbdb752d82a4f5d3cf83225be1ebbb838cb6d18e3` |
| `validation/poc-04/evidence/phase1/ipam-remediation-e47077d5-f7d8-463a-be2d-7acd2c5c0bcf/daemon-inspect/00f70e690d5ef019c74bf9b5c7d126f38bba951aad882ed6df0f9b6cfacbed60.safe-inspect.json` | `e55c46ff746e5b4fbf216e1d544a23bdadea1dfa407eb8d6581b79ada194a65d` |
| `validation/poc-04/evidence/phase1/ipam-remediation-e47077d5-f7d8-463a-be2d-7acd2c5c0bcf/daemon-inspect/055293f1d638b0306512ccc4de50190097ffeb49277bd7b1284c5f861ecd32b9.safe-inspect.json` | `04034d8bf835945092a2f83665e72da3ded9cf81a25476277aab30468bf58105` |
| `validation/poc-04/evidence/phase1/ipam-remediation-e47077d5-f7d8-463a-be2d-7acd2c5c0bcf/daemon-inspect/1280ed817bf9c4096e5fe072b9051377f2b0f571ffdb34876c58f39bdef41ee5.safe-inspect.json` | `c67c3f252898c34f275d84f871ed2bcf6197b0b1fd51cb358d31d12c00c7b082` |
| `validation/poc-04/evidence/phase1/ipam-remediation-e47077d5-f7d8-463a-be2d-7acd2c5c0bcf/daemon-inspect/13e0083b8faa90c7fab83af6b2913e5161e45c4bd2989a5b7cdfedd16a2b7a97.safe-inspect.json` | `044c5b705be25397f41fa296a81d00eb29abb2098537bfe395a7a7fba11c930f` |
| `validation/poc-04/evidence/phase1/ipam-remediation-e47077d5-f7d8-463a-be2d-7acd2c5c0bcf/daemon-inspect/170ecb2b8a0d9c4d117b9ba56cffa52b0e1be7ab539b04c261aa40214a0e9fa3.safe-inspect.json` | `d1a79f85fac340f1a0ad1123099c9a4d6b0266d4839b05073f365eca9653066b` |
| `validation/poc-04/evidence/phase1/ipam-remediation-e47077d5-f7d8-463a-be2d-7acd2c5c0bcf/daemon-inspect/1a590e7ec3a03e943d319f4bf6a62d168f8bd1b99951872cc110bfabbbb08b03.safe-inspect.json` | `7fb227a83f04210e1459965dc5fd1d0d6c671100cea6c034ac44360a0d4b5aee` |
| `validation/poc-04/evidence/phase1/ipam-remediation-e47077d5-f7d8-463a-be2d-7acd2c5c0bcf/daemon-inspect/1d032d8f9f087e45cc7244d10372f044b3e138f8824a80b9f6f28524212c0cb3.safe-inspect.json` | `cfb54e3fdc97d73973a66e80f3d30e23483330511e67afd578f16fa4b53bcc4d` |
| `validation/poc-04/evidence/phase1/ipam-remediation-e47077d5-f7d8-463a-be2d-7acd2c5c0bcf/daemon-inspect/29f62489a611604c7168f9e179758b24e1662b635bb0b588484b92cf8cb8c618.safe-inspect.json` | `c235618fb95c316dd61a3f52a3dc2ed3124e4b885959ccbdf84363d9ba925995` |
| `validation/poc-04/evidence/phase1/ipam-remediation-e47077d5-f7d8-463a-be2d-7acd2c5c0bcf/daemon-inspect/2e61b77d75b380c8ef3552743a5de94179f5baa74d09c934b4c6fa0d20ac1213.safe-inspect.json` | `8b7cf38291e8eda958e6cb023b6a67efb0ab9ff0f6f923ecb386fade6f15de1c` |
| `validation/poc-04/evidence/phase1/ipam-remediation-e47077d5-f7d8-463a-be2d-7acd2c5c0bcf/daemon-inspect/32ca2d3e1229eee53172dbd3759081bea067019f465cc713e304e87a570c3a70.safe-inspect.json` | `a5616bff85d09ca6e3de3cfd469f4965d0b4ecc595495878f62144218b530c98` |
| `validation/poc-04/evidence/phase1/ipam-remediation-e47077d5-f7d8-463a-be2d-7acd2c5c0bcf/daemon-inspect/3826b6ad2b01f6f18b4c31c581eaeb5ad78f04ed85e0a08c828ca1e8e9b53d51.safe-inspect.json` | `61588c053c8c635c6c80502935d3d1d8046c9d0a4e21bb65b54592dd70efb65b` |
| `validation/poc-04/evidence/phase1/ipam-remediation-e47077d5-f7d8-463a-be2d-7acd2c5c0bcf/daemon-inspect/38e48f6b91873bc5cceb58adf94c34dc3516181d0b2eb697cc5959b0983db858.safe-inspect.json` | `60de24d771f6620680e8b5c8a51892571212a0d8be6153f712d4388e10d189fc` |
| `validation/poc-04/evidence/phase1/ipam-remediation-e47077d5-f7d8-463a-be2d-7acd2c5c0bcf/daemon-inspect/409e62f6707c9c658299f2163518f67116d73e26c2c306c82cf4893e4d8f5ee8.safe-inspect.json` | `2888a37c831c669c3f2b5f2247542a27e1430020e879874dda4fee15ce3c1b38` |
| `validation/poc-04/evidence/phase1/ipam-remediation-e47077d5-f7d8-463a-be2d-7acd2c5c0bcf/daemon-inspect/47eb43cdc219c8da533ae9749c7aad747ac231777babdfad2cd43ec64622590d.safe-inspect.json` | `d4cf27970af49724ea806f6fdc494d410f84c26e9a228413ef2eb7f780120587` |
| `validation/poc-04/evidence/phase1/ipam-remediation-e47077d5-f7d8-463a-be2d-7acd2c5c0bcf/daemon-inspect/50888932cde72e4f161bcae6bf1f5fce7856023dec3e71f5da0c01fea9232ff8.safe-inspect.json` | `bb0256dc8879996c5c2d9b8c5268ae72789a9deb1898f34311e90a777974dcc4` |
| `validation/poc-04/evidence/phase1/ipam-remediation-e47077d5-f7d8-463a-be2d-7acd2c5c0bcf/daemon-inspect/53380657a5952ee5996a57fe8e000363431432f68f0789993fb2469aef3115f7.safe-inspect.json` | `954e2548c319fefe121e04bb7b5fa67fa951e356d921b24c498b248cb4de0854` |
| `validation/poc-04/evidence/phase1/ipam-remediation-e47077d5-f7d8-463a-be2d-7acd2c5c0bcf/daemon-inspect/53dd6b48815aad81babad41922dcd11a46fa002eb176736f9c97c4db309183a3.safe-inspect.json` | `d862b26ccc84eb0cab2873845947eaf77080ce9817275487c71e09eaf666517f` |
| `validation/poc-04/evidence/phase1/ipam-remediation-e47077d5-f7d8-463a-be2d-7acd2c5c0bcf/daemon-inspect/575d33b15f980363360e1d0a2bd512dfdf1e58a5727eb1310e63e708ff7176fd.safe-inspect.json` | `a9809c1a12343c45acad79ac9365d9aba0159536e8dd99591c78cd2cccefaeb2` |
| `validation/poc-04/evidence/phase1/ipam-remediation-e47077d5-f7d8-463a-be2d-7acd2c5c0bcf/daemon-inspect/582a50479cf55b88ce85483868994137e8b13a3a6eef045878438fa0f59b65ad.safe-inspect.json` | `de9a660bcbf75fd1feeb0d4aa426d1973c957e17bb2fa2fbfc89abd69252aff5` |
| `validation/poc-04/evidence/phase1/ipam-remediation-e47077d5-f7d8-463a-be2d-7acd2c5c0bcf/daemon-inspect/58935a459717372523c64805ccd0241efde0c77e005380863a9e4b0438968ba8.safe-inspect.json` | `a136535a079e5fede4d9e8cfc1ac6b70fb8e461338404e6adecc18369e22e0c4` |
| `validation/poc-04/evidence/phase1/ipam-remediation-e47077d5-f7d8-463a-be2d-7acd2c5c0bcf/daemon-inspect/5c1023969172f0b4caca89784682a23862f6b04568ffb8126e7d5331bf45c451.safe-inspect.json` | `7d10d0f0b993b4b2b7e414698aa5992f1cbfe08486ef556c3a707bf4e1d2266c` |
| `validation/poc-04/evidence/phase1/ipam-remediation-e47077d5-f7d8-463a-be2d-7acd2c5c0bcf/daemon-inspect/5f5a31a4a92b837b28b9116e5a1ac42ee05c88043a7e140878d4ec7082fb2640.safe-inspect.json` | `f6eeed4df56ab4a0a1935c0e4f413d2cd9dc05f988b9fa4e40cf73419967fb47` |
| `validation/poc-04/evidence/phase1/ipam-remediation-e47077d5-f7d8-463a-be2d-7acd2c5c0bcf/daemon-inspect/62dc3541ba4b4189abce3120cf4280ce910845fc049a110bea606cf626050447.safe-inspect.json` | `4cff604836e4334e122d8016ccc684962de35c9996180e754e129348253da400` |
| `validation/poc-04/evidence/phase1/ipam-remediation-e47077d5-f7d8-463a-be2d-7acd2c5c0bcf/daemon-inspect/64494aa94090a2f6e20acdf7d9be6490af24daab239a399f0bf76feb5186fae9.safe-inspect.json` | `cd7d721a71f65a20cb8f17e39eb52dfecbb646c5dedc4d9d10758777e826cb8a` |
| `validation/poc-04/evidence/phase1/ipam-remediation-e47077d5-f7d8-463a-be2d-7acd2c5c0bcf/daemon-inspect/67bebe26d72b5d19e4f6bb02b9d01575670d1733a5b9aa46eb35fe9348814f64.safe-inspect.json` | `e1bc0caafb97e90c248a33ef4d1d4a82ab36169aaa728c0854516a3b24b3b486` |
| `validation/poc-04/evidence/phase1/ipam-remediation-e47077d5-f7d8-463a-be2d-7acd2c5c0bcf/daemon-inspect/686e984734a56c5f43270b103c4e1afaf5f90e658e967bc440c92706a286205a.safe-inspect.json` | `f9adf24d4a7f73589d02a30c1a30dbd54e300ca29830a42ada070ee21816e88c` |
| `validation/poc-04/evidence/phase1/ipam-remediation-e47077d5-f7d8-463a-be2d-7acd2c5c0bcf/daemon-inspect/718873054fd1597ba9d3fe6d930c4e4ec87254186d13b227a30d1240e745c9f4.safe-inspect.json` | `358283433390641ef138848c68d7bf5dd4c4fb3e747742d1f97a831faf729448` |
| `validation/poc-04/evidence/phase1/ipam-remediation-e47077d5-f7d8-463a-be2d-7acd2c5c0bcf/daemon-inspect/79c173d67083c400167d7ea372502f0f772e852e81c3fd5b5d83b825cf2ff44c.safe-inspect.json` | `fee5dfd1dfc457e4eb198d72e7a639741f77d971d19b8ae8f27fed2eb1b7596d` |
| `validation/poc-04/evidence/phase1/ipam-remediation-e47077d5-f7d8-463a-be2d-7acd2c5c0bcf/daemon-inspect/7e02f042158cba0bb18a38fb2b31e9ec19965267bbb42e8a59b77e265664b5ee.safe-inspect.json` | `3d757de2ef8114196abdd42e3b7f4f2b94217a98299d5d504365a6b004057e18` |
| `validation/poc-04/evidence/phase1/ipam-remediation-e47077d5-f7d8-463a-be2d-7acd2c5c0bcf/daemon-inspect/8332b6144395c592ff67be204d10427befaae1a75302b422c71767defb68b693.safe-inspect.json` | `753b9890730e1462142ca557f42837b8423e94f6d184d71ad60a2d6e6bd7081f` |
| `validation/poc-04/evidence/phase1/ipam-remediation-e47077d5-f7d8-463a-be2d-7acd2c5c0bcf/daemon-inspect/89dfc2dd745d2070aed7fea142581058e228fbbb9ebd927e062c826f44208730.safe-inspect.json` | `f4614e47fda06e40214f82f07775bc14c1b3c52e8fd0093113bf0088717fbabb` |
| `validation/poc-04/evidence/phase1/ipam-remediation-e47077d5-f7d8-463a-be2d-7acd2c5c0bcf/daemon-inspect/8e47c13002bb500835b8c0e554e509c9a21fe7ee07208afa6b29de1ed3717bcb.safe-inspect.json` | `c0114252ccedf12be83c7e3185cf35a02e3bed121c177158a0f91c046d0caa1f` |
| `validation/poc-04/evidence/phase1/ipam-remediation-e47077d5-f7d8-463a-be2d-7acd2c5c0bcf/daemon-inspect/8e505138c6b469a69ee27dfe208b2db27c0200bba8171188aed3a4564efa4828.safe-inspect.json` | `3d17d6a03eab64b27c08bcb47933755069cead2b8250b86dc756da2431eb6fca` |
| `validation/poc-04/evidence/phase1/ipam-remediation-e47077d5-f7d8-463a-be2d-7acd2c5c0bcf/daemon-inspect/8ec8be383cf53dc9d591c9cd12c9448a1ec54d5036573e54efae86ee5565591e.safe-inspect.json` | `a21f2ec7a766607ac7edf26ce4576990af61119dcb1781a11d0a01de49fca321` |
| `validation/poc-04/evidence/phase1/ipam-remediation-e47077d5-f7d8-463a-be2d-7acd2c5c0bcf/daemon-inspect/95241701052c2ab5f4c4f9165e97e6efc4a1d7f4ca614f3a65bf9c2692588637.safe-inspect.json` | `4017cc11de9490453fa71ed4621ffe1ed12c7526596e5629738622199f0cacc4` |
| `validation/poc-04/evidence/phase1/ipam-remediation-e47077d5-f7d8-463a-be2d-7acd2c5c0bcf/daemon-inspect/96376e3edd0820a4fd0c6ccc3abed449a0ead5d615c66167e6809575573319f5.safe-inspect.json` | `19db78b36f830da643287e4abc61ad5122688c21d17e51bcec9d641671c7c1f3` |
| `validation/poc-04/evidence/phase1/ipam-remediation-e47077d5-f7d8-463a-be2d-7acd2c5c0bcf/daemon-inspect/9bb146fed853db77997ea879a0f5ca45d97a8043aed57f95677a72153905b445.safe-inspect.json` | `724ac621f6c549174ac59e057507a0a5b05bab86ccc06f5ce6a5232e51942207` |
| `validation/poc-04/evidence/phase1/ipam-remediation-e47077d5-f7d8-463a-be2d-7acd2c5c0bcf/daemon-inspect/9c5464c643ab19003fc3639167c391d36bd8f99c5d2c59e0217e762c032f1b53.safe-inspect.json` | `02a99648fd007cda1ed4d2d27d595d1617caf37b0d92d00941b081ccfd1c2121` |
| `validation/poc-04/evidence/phase1/ipam-remediation-e47077d5-f7d8-463a-be2d-7acd2c5c0bcf/daemon-inspect/a38a3110ca3868b9af7113ffd18226cc9fad24e9105f55a46c60f453c3be5705.safe-inspect.json` | `5e8d3ee441e745ec83f13ccdcce0244d664d756a30bce3409db6415c1d4388ef` |
| `validation/poc-04/evidence/phase1/ipam-remediation-e47077d5-f7d8-463a-be2d-7acd2c5c0bcf/daemon-inspect/a42b732f519611eafdf5fa64965fca417c817f88409b7422675ab536359750df.safe-inspect.json` | `3be84d6e186981ffe0d0e725f1e7296106b66a31363304b53002fc36067fb913` |
| `validation/poc-04/evidence/phase1/ipam-remediation-e47077d5-f7d8-463a-be2d-7acd2c5c0bcf/daemon-inspect/a5dd5d1d23674fc20b7ab198f440b06e34b68f0d49fe81f344519e458cf4d47d.safe-inspect.json` | `9e42d5d7f3e03b8ad490c944e0e7fa00d59fc8e0a1502fd76bf9e3888abc3600` |
| `validation/poc-04/evidence/phase1/ipam-remediation-e47077d5-f7d8-463a-be2d-7acd2c5c0bcf/daemon-inspect/b1c26efde81df7d7b585ea68609f1b8bd6611dd4901652d6da905afba4cff0e2.safe-inspect.json` | `175d1f82e10cba9981b74225d5b91a2457ee341ca3eed1aefaa109056cb78f8f` |
| `validation/poc-04/evidence/phase1/ipam-remediation-e47077d5-f7d8-463a-be2d-7acd2c5c0bcf/daemon-inspect/b3266cc75dc63955cb55c1fbd9fa1e59656b65a87d91abbc88c4d1f7df5a3664.safe-inspect.json` | `7cc84e6045e4db758f55d0248d0623ab8ef053df1904bcf52e342223ff025e17` |
| `validation/poc-04/evidence/phase1/ipam-remediation-e47077d5-f7d8-463a-be2d-7acd2c5c0bcf/daemon-inspect/b3ef868e5a88c17706d5bc2826685617f9f4a65da6b99d8b0b565ec8b7443ee1.safe-inspect.json` | `8138dee8c9997a2ddc2bbafd8b349afe8292077c50621159bf0b44b6f02909d9` |
| `validation/poc-04/evidence/phase1/ipam-remediation-e47077d5-f7d8-463a-be2d-7acd2c5c0bcf/daemon-inspect/b7317e56a0c8302230bd3decbbde49875d7870c4ce6f1747afe1dbfa8b09c321.safe-inspect.json` | `6645d1ef5a4af995e5c3cb54e0657a80d54c350111166b9327492ebe404afa07` |
| `validation/poc-04/evidence/phase1/ipam-remediation-e47077d5-f7d8-463a-be2d-7acd2c5c0bcf/daemon-inspect/befee258fc76b73dc53bfbde93080cf0ef7a39dfeefc72f3b15151b2f2998b53.safe-inspect.json` | `a80b4529589040bc76cd28b68c00618a7726b9e2b47c69a9c40810bea2eb7d4e` |
| `validation/poc-04/evidence/phase1/ipam-remediation-e47077d5-f7d8-463a-be2d-7acd2c5c0bcf/daemon-inspect/c40da09c5546199cde9926e4e1ce9a2d5e8061cfc3b45d8e60a772760fccca34.safe-inspect.json` | `1e3e327a8cfed3de933e3c67689c4bb87bdc63f5e55a795576842aacaccc6917` |
| `validation/poc-04/evidence/phase1/ipam-remediation-e47077d5-f7d8-463a-be2d-7acd2c5c0bcf/daemon-inspect/ca1b41d319bc6f130c86bfcd66bd93a885b5a3d4206a0828e567411508313fd5.safe-inspect.json` | `213b3a3bd8e269b3f690cbec3607dd87938ba8fdd5e5841d8a57290e15a4c7b9` |
| `validation/poc-04/evidence/phase1/ipam-remediation-e47077d5-f7d8-463a-be2d-7acd2c5c0bcf/daemon-inspect/cc2e4099fab80474ce06fe1cbe78ea0bedee43775697ecd07e46e6045b9a52a9.safe-inspect.json` | `f0d8a6514c2a2a7c039a1a65072d62ccb51f7929cdbbe25a71de886a19a37c72` |
| `validation/poc-04/evidence/phase1/ipam-remediation-e47077d5-f7d8-463a-be2d-7acd2c5c0bcf/daemon-inspect/d50b4c5def513acc2b3df876d63f28c958066dd9382746c2470a5ac7712e3891.safe-inspect.json` | `dc91891a994f20847cf89ea3dccbe71981a29069c70558c7d147246806656d42` |
| `validation/poc-04/evidence/phase1/ipam-remediation-e47077d5-f7d8-463a-be2d-7acd2c5c0bcf/daemon-inspect/d52bafc00520edb385cd4a3482ed450052bee542025147e8310ab69c676e5aab.safe-inspect.json` | `812cfe1f2634fa0c2026b98b83c50521651237718f5f20facd6d0fecc982b31c` |
| `validation/poc-04/evidence/phase1/ipam-remediation-e47077d5-f7d8-463a-be2d-7acd2c5c0bcf/daemon-inspect/de1998dabec09bff2ed980e23305ed1513fc007f7a003d8e917554e760827914.safe-inspect.json` | `4f9f83ff55c36ec2e3d2e856f47ae9722b207b8214662f21e65255ee5c170332` |
| `validation/poc-04/evidence/phase1/ipam-remediation-e47077d5-f7d8-463a-be2d-7acd2c5c0bcf/daemon-inspect/df698dcff71ce67c78b885b25c76c29fc22028fbaaf4eef0371c17e63ec8cc1b.safe-inspect.json` | `4d5696e32eb462b70d259bfca230ac35ee8765dd7b4bd67a31f5feb86c56ea40` |
| `validation/poc-04/evidence/phase1/ipam-remediation-e47077d5-f7d8-463a-be2d-7acd2c5c0bcf/daemon-inspect/e041af94ea825658274d83571a4b94d360d53cbad2fc2849c5202e38c43e7fd8.safe-inspect.json` | `efb5119f5e4f5cd8c8f82431dbdeb81e008628978a0d8f7d4864e43159a19c94` |
| `validation/poc-04/evidence/phase1/ipam-remediation-e47077d5-f7d8-463a-be2d-7acd2c5c0bcf/daemon-inspect/e2da10d83a5e4f8ad28337b5db8bd75f1111e6416aad4f7bb5d8f94cb67356db.safe-inspect.json` | `5a17070b06407cc409f756784fe36593af3254754ffbc9d2f168808a4a9b4044` |
| `validation/poc-04/evidence/phase1/ipam-remediation-e47077d5-f7d8-463a-be2d-7acd2c5c0bcf/daemon-inspect/e7f90234873fe46f27191c8154fa30be79afff9677020775c27a37459caf8630.safe-inspect.json` | `8a9e6b229a8578719c60e8ca7faef287410ebc24ceb1d7e03e4b1bbc466321a1` |
| `validation/poc-04/evidence/phase1/ipam-remediation-e47077d5-f7d8-463a-be2d-7acd2c5c0bcf/daemon-inspect/e93d9bfef96565bdc8c76e3b12e57dbaeb16758c5bf67a753df0bfb150432e79.safe-inspect.json` | `399adc55fabff049751a13e01b2362993721b24c5ecbf6ca5a0ddb20fc2c3855` |
| `validation/poc-04/evidence/phase1/ipam-remediation-e47077d5-f7d8-463a-be2d-7acd2c5c0bcf/daemon-inspect/ec0fbf7e237ca59e920a128995bb11748830b3a62901021b8022caba7afbdf7d.safe-inspect.json` | `8d8ebf139494bd2ddb7be8be7824762dacf4b54f02b8e434f2206da12c9eeff6` |
| `validation/poc-04/evidence/phase1/ipam-remediation-e47077d5-f7d8-463a-be2d-7acd2c5c0bcf/daemon-inspect/f017d43c8622628d6fad61e051c6bac7953d85c5c5b3415eb4e9b7deb12c2068.safe-inspect.json` | `76727ec7f2cb7d2745c2aec51110762802c5c8778cab140efe73c96c9046612a` |
| `validation/poc-04/evidence/phase1/ipam-remediation-e47077d5-f7d8-463a-be2d-7acd2c5c0bcf/daemon-inspect/f244c945ac95357835e911bb103b45f2899da6680b783da4c7f3e51d0dbec068.safe-inspect.json` | `fb7f03821934e93869a82a9b5fdbb917cf1aededca026cf14a171ca9d8d84210` |
| `validation/poc-04/evidence/phase1/ipam-remediation-e47077d5-f7d8-463a-be2d-7acd2c5c0bcf/daemon-inspect/f67ee5620388ba5ed9146a3d3c707f6850759ba81e23e525dfeabc930152d766.safe-inspect.json` | `7390bc16b9803b083debf25bfc9dd7feeea5fbd193ddf6a1daab790c9035ded6` |
| `validation/poc-04/evidence/phase1/ipam-remediation-e47077d5-f7d8-463a-be2d-7acd2c5c0bcf/daemon-inspect/ff8700d76d8c3dec5f2548b595a17df5d6f2f844163b37c54c0f3ea84ad569cb.safe-inspect.json` | `4fe08f8f491ef9837f3a61a77d49a097ed16f8db2078e5985dcbb4e99a132c98` |
| `validation/poc-04/evidence/phase1/ipam-remediation-e47077d5-f7d8-463a-be2d-7acd2c5c0bcf/docker-resource-events.jsonl` | `eb59c1fdd20863d2b6d42a6f717894ce7e3c28920db7f9671217067babccead6` |
| `validation/poc-04/evidence/phase1/ipam-remediation-e47077d5-f7d8-463a-be2d-7acd2c5c0bcf/execution-summary.json` | `5dd19e3bef1fc91fee5312b4b117956234a54aa7cd2753ad51ce8ca416543292` |
| `validation/poc-04/evidence/phase1/ipam-remediation-e47077d5-f7d8-463a-be2d-7acd2c5c0bcf/final-exact-cleanup.py` | `4807fc6d64cf3279028944cde6365c38e84d1a1c05c4d05388eb82ce1a34bdf2` |
| `validation/poc-04/evidence/phase1/ipam-remediation-e47077d5-f7d8-463a-be2d-7acd2c5c0bcf/final-git-inventory.json` | `0cadd024287cab6c49957a2e1e04645d56c563f3f663d3b5366b045beb21334f` |
| `validation/poc-04/evidence/phase1/ipam-remediation-e47077d5-f7d8-463a-be2d-7acd2c5c0bcf/final-source-binding.json` | `01f68d4495e68e5b789831238b60d04a56af94c01a9848261edbf0bd5e4ac1fa` |
| `validation/poc-04/evidence/phase1/ipam-remediation-e47077d5-f7d8-463a-be2d-7acd2c5c0bcf/final-validator.py` | `9953c3ff480b6c6adf606695151035ef6d178e323b5ba48e08e92e43ec1c9147` |
| `validation/poc-04/evidence/phase1/ipam-remediation-e47077d5-f7d8-463a-be2d-7acd2c5c0bcf/ipam-capacity-probe.py` | `077ae578821efd47dd3cbe0cfb639bb4e8c6598e66b01bc4f0c62b44aea3502a` |
| `validation/poc-04/evidence/phase1/ipam-remediation-e47077d5-f7d8-463a-be2d-7acd2c5c0bcf/pool-selection.json` | `bf42f001d0eaffee768a82ef57aca8c385db90a897b43a06a0ae0316dbec1488` |
| `validation/poc-04/evidence/phase1/ipam-remediation-e47077d5-f7d8-463a-be2d-7acd2c5c0bcf/post-restart-attestation-attempt-1.json` | `54281d672a470d50a4a4483837625cc70e7135b519a19619ed60b8a8e72b64a5` |
| `validation/poc-04/evidence/phase1/ipam-remediation-e47077d5-f7d8-463a-be2d-7acd2c5c0bcf/post-restart-attestation.json` | `a40b3745a849a35ebe1b74eeba37289b4f247df0e54aaf24b374a0c5db936b0b` |
| `validation/poc-04/evidence/phase1/ipam-remediation-e47077d5-f7d8-463a-be2d-7acd2c5c0bcf/profile-change.patch` | `af7b62d4c51a6b9cc8f25a3f3eab79cfc1ee0e01731fa395bd148d9557c0d39d` |
| `validation/poc-04/evidence/phase1/ipam-remediation-e47077d5-f7d8-463a-be2d-7acd2c5c0bcf/profile-start.json` | `d3a0220b91610c3e6dfe05b2ddcd0aa50098f7a00a92a2f5713a376d48f9c05c` |
| `validation/poc-04/evidence/phase1/ipam-remediation-e47077d5-f7d8-463a-be2d-7acd2c5c0bcf/profile-stop.json` | `d76d1d0e65cadb25663532ec11ed2a457735de483ce136fef235f46817cd4c3b` |
| `validation/poc-04/evidence/phase1/ipam-remediation-e47077d5-f7d8-463a-be2d-7acd2c5c0bcf/read-only-audit.json` | `9a5158e10346140a48f4e9409d63fe2a082989f9c0a44ccf5246576f5eca0872` |
| `validation/poc-04/evidence/phase1/ipam-remediation-e47077d5-f7d8-463a-be2d-7acd2c5c0bcf/read-only-diagnosis.json` | `c632c2b98582627d02cee43f33e49e6964577424c5bac7c0a387bce6c0f1a110` |
| `validation/poc-04/evidence/phase1/ipam-remediation-e47077d5-f7d8-463a-be2d-7acd2c5c0bcf/resource-identities.jsonl` | `e9b3278e777126b493273d242a6862ccfb9457d110f3e05721a693c3cefd11ab` |
| `validation/poc-04/evidence/phase1/ipam-remediation-e47077d5-f7d8-463a-be2d-7acd2c5c0bcf/resource-incarnation-inventory.json` | `93b3e71efc3d897d682690dfd036b140421654db99d1c07ce77f7c32c6dce9da` |
| `validation/poc-04/evidence/phase1/ipam-remediation-e47077d5-f7d8-463a-be2d-7acd2c5c0bcf/resource-recorder.py` | `7377b5ebe589330e4a97ef0114e1ed471e5de3234891023355efa73366b24ee6` |
| `validation/poc-04/evidence/phase1/ipam-remediation-e47077d5-f7d8-463a-be2d-7acd2c5c0bcf/runner.init.gradle` | `1255ee9276b75e8f33819c911dc63aad7bedfcead64adb14f36a91a4f5657b34` |
| `validation/poc-04/evidence/phase1/ipam-remediation-e47077d5-f7d8-463a-be2d-7acd2c5c0bcf/runtime-cleanup-proof.json` | `b4bfbd070dfaae0e16b77a8e3e007834acb958efd2466c82811c62b14c937455` |
| `validation/poc-04/evidence/phase1/ipam-remediation-e47077d5-f7d8-463a-be2d-7acd2c5c0bcf/runtime-count-reconciliation.json` | `b67ff11e743a37eaf96b5a2a5294048c20920029798cda11c7c023153e0729dc` |
| `validation/poc-04/evidence/phase1/ipam-remediation-e47077d5-f7d8-463a-be2d-7acd2c5c0bcf/runtime-exact-cleanup.py` | `39297adb27f53c1019bf7017201703748880d13d58d100a15a242697e887694b` |
| `validation/poc-04/evidence/phase1/ipam-remediation-e47077d5-f7d8-463a-be2d-7acd2c5c0bcf/runtime-retry-command.json` | `51dbfab1b62d07f325b88e18c30d402af9622f33746a663bcae61b36fc201b79` |
| `validation/poc-04/evidence/phase1/ipam-remediation-e47077d5-f7d8-463a-be2d-7acd2c5c0bcf/runtime-retry-counts.json` | `af41b7d2a1da974545d02fd4f44e8df61f9b7b46d385054ec8625cdb08584007` |
| `validation/poc-04/evidence/phase1/ipam-remediation-e47077d5-f7d8-463a-be2d-7acd2c5c0bcf/runtime-retry-gradle-output.txt` | `17479382f98d49cf576d16d98ed87ec20a65cfbafca35be6e9262b326decdc64` |
| `validation/poc-04/evidence/phase1/ipam-remediation-e47077d5-f7d8-463a-be2d-7acd2c5c0bcf/runtime-retry-results/TEST-dev.vra.async.AsyncPermissionIntegrationTest.xml` | `2c192a945516e1e13d2e77a4d88985a625dd7f2174f66c6ec9a0b4b2c78a2744` |
| `validation/poc-04/evidence/phase1/ipam-remediation-e47077d5-f7d8-463a-be2d-7acd2c5c0bcf/runtime-retry-results/TEST-dev.vra.async.AsyncVisibilityIntegrationTest.xml` | `83b14db1827a7ba2235b2df20b60a8a206137cf6cd0133ac18868e0b27896c55` |
| `validation/poc-04/evidence/phase1/ipam-remediation-e47077d5-f7d8-463a-be2d-7acd2c5c0bcf/runtime-retry-results/TEST-dev.vra.async.DeliveryClaimIntegrationTest.xml` | `21997c61d68a21ca55ea4b08a40e7c067e63398c4c57b5e1d2677c7c79e58b4e` |
| `validation/poc-04/evidence/phase1/ipam-remediation-e47077d5-f7d8-463a-be2d-7acd2c5c0bcf/runtime-retry-results/TEST-dev.vra.async.DeliveryStateIntegrationTest.xml` | `77ca0979a2ca2bd7d5499c626c4cb970ce8a98f9b02004b0a0ecd7a276e5cafa` |
| `validation/poc-04/evidence/phase1/ipam-remediation-e47077d5-f7d8-463a-be2d-7acd2c5c0bcf/runtime-retry-results/TEST-dev.vra.async.ProducerOutboxIntegrationTest.xml` | `0e94666e4a8f8cb093b063af4faf7be528e23c5f8584d6acdaf9679b3e5b2675` |
| `validation/poc-04/evidence/phase1/ipam-remediation-e47077d5-f7d8-463a-be2d-7acd2c5c0bcf/runtime-retry-results/TEST-dev.vra.async.ProjectionInboxIntegrationTest.xml` | `877ed4425937b95913fa1c7f43cbe8b2bb7f378089459c996570d0338c344970` |
| `validation/poc-04/evidence/phase1/ipam-remediation-e47077d5-f7d8-463a-be2d-7acd2c5c0bcf/runtime-retry-results/TEST-dev.vra.async.ProjectionRebuildIntegrationTest.xml` | `5d2d1336210d7d88b1d960640084c81ff8488f975b2deb10b21fbfaec1600add` |
| `validation/poc-04/evidence/phase1/ipam-remediation-e47077d5-f7d8-463a-be2d-7acd2c5c0bcf/runtime-retry-results/TEST-dev.vra.async.ReconciliationIntegrationTest.xml` | `eab5a630d4891df71cc3bcc3b2cdaa4e9f8afeeba3d2ca985441665f54f6939e` |
| `validation/poc-04/evidence/phase1/ipam-remediation-e47077d5-f7d8-463a-be2d-7acd2c5c0bcf/runtime-retry-results/TEST-dev.vra.async.RetryReplayIntegrationTest.xml` | `2b571446b697fce501f3fb24eff51c628538e6bf6686ac1f2af0c0a0aa51b431` |
| `validation/poc-04/evidence/phase1/ipam-remediation-e47077d5-f7d8-463a-be2d-7acd2c5c0bcf/runtime-retry-results/TEST-dev.vra.async.ValidationSimulatorIntegrationTest.xml` | `7d0d6f04e15db4d79af781aa3ad378b256bd52fbf891d767061ed587022d3d70` |
| `validation/poc-04/evidence/phase1/ipam-remediation-e47077d5-f7d8-463a-be2d-7acd2c5c0bcf/runtime-retry-results/TEST-dev.vra.async.WorkerProcessCrashIntegrationTest.xml` | `7dc0423f0a9079cc2cc5511130d200a1f95ce2071d69917ec940d96502ceb20d` |
| `validation/poc-04/evidence/phase1/ipam-remediation-e47077d5-f7d8-463a-be2d-7acd2c5c0bcf/runtime-retry-results/TEST-dev.vra.inventory.IdempotentReservationApplicationServiceIntegrationTest.xml` | `431d2a5539d608f14230cd5c27555f0d2140bb6cb0bc272143acc81092922db5` |
| `validation/poc-04/evidence/phase1/ipam-remediation-e47077d5-f7d8-463a-be2d-7acd2c5c0bcf/runtime-retry-results/TEST-dev.vra.inventory.ReservationConcurrencyIntegrationTest.xml` | `a09a0b9329609e2dea8295e150847f233e29ed2b438e9f2b610a58867a734b4c` |
| `validation/poc-04/evidence/phase1/ipam-remediation-e47077d5-f7d8-463a-be2d-7acd2c5c0bcf/runtime-retry-results/TEST-dev.vra.inventory.ReservationHttpPostgresIntegrationTest.xml` | `cdf8d7f8488e70f1e8431dc2513ca7a503cd975bfb541026c1766f7b33268ae2` |
| `validation/poc-04/evidence/phase1/ipam-remediation-e47077d5-f7d8-463a-be2d-7acd2c5c0bcf/runtime-retry-results/TEST-dev.vra.inventory.ReservationIdempotencyRepositoryIntegrationTest.xml` | `cf293f3be1aea3c59fba06a12292d3820d565e8b580ced60e783c9a1a326b755` |
| `validation/poc-04/evidence/phase1/ipam-remediation-e47077d5-f7d8-463a-be2d-7acd2c5c0bcf/runtime-retry-results/TEST-dev.vra.inventory.ReservationTransactionIntegrationTest.xml` | `f59aa9335ae1eaa9a1f97315cf86ebb96c2e41fa272309b6271a6ceb2e89460a` |
| `validation/poc-04/evidence/phase1/ipam-remediation-e47077d5-f7d8-463a-be2d-7acd2c5c0bcf/runtime-retry-results/TEST-dev.vra.platform.health.HealthProbePostgresIntegrationTest.xml` | `32ea0d4e89db60c8e0b542eb5cd66b146217adbba7437204f254cfa17575409c` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/PhaseOneEvidenceCapture.java` | `6ec9759c3e09d78cabed1cd5ef712dc6b4718f15ad4b03f0bda4aecce63c2200` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/Poc04FoundationMigrationIntegrationTest.before-mount-remediation.java` | `eedaf64c01778dcc9f1b502a4127bd1363c08a310e3cc131a91a48e6891b1ee8` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/TEST-bootstrap-Poc04FoundationMigrationIntegrationTest.xml` | `2fed9df4af68195466dd14a05cadbf74d72049ba15497d2e1373041fa2388bc0` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/TEST-dev.vra.migration.Poc04FoundationMigrationIntegrationTest.xml` | `2337acb89c1363904b04541a8d56987ae27def6c4235b5ee3b845baa18d7708e` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/TEST-populated-Poc04FoundationMigrationIntegrationTest.xml` | `4b37c16d40dd1df5113d354c6f3708c440f22f72c8c9afda340e18780c38fffd` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/artifact-validation.json` | `2c4c4b33e434ba95079a19d960d40db23a5983d567854f3a975a2ed7dc312941` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/bootstrap-command.json` | `7cd6a567d8251331ea0769cb2bfdf83ce674c2cbf66061c6b1a81e0621dae5a6` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/bootstrap-gradle-output.txt` | `40754acd033458292ba69a199ec87108dfa193d38356ced78cd1645c37b5ada4` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/cleanup-attempt-1.json` | `13c56ea85ebf03f9a3c025c579134e8a2da761b054206c2c1ce7fe78ece74342` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/cleanup-attempt-2.json` | `49e6f2e8b5ef248b0245719e9528a67deb1d141b95789139a0874be758023d22` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/cleanup-proof.json` | `45c6c7893c8c2de4bb67245f34f19620dd012ca8dbf073a1cae223ca96fa0264` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/daemon-inspect/09cb4e2c80d32d69ceb8386c12a4fd7945dada17ebdffaa86e615f8dd38dd417.safe-inspect.json` | `0ae2351de88a508326d886c4b65deb4c5d3a571673ed3e620c2fdbcd6cd401c6` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/daemon-inspect/0d8d4fb6856793dedd3f622ef34da69d6d343836df0fddacf814db9232579698.safe-inspect.json` | `be73e02510a26e0f855992af9f45e3d51501e2529c15d153820dabc8592be390` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/daemon-inspect/16873bc2c9d6d1570493b3be6d6d5a90d07934c52a1bea2c7d12b1adc304107e.safe-inspect.json` | `b95e0078082ddeac0f74c630931c02bd4044747767351eef25d312e014952abf` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/daemon-inspect/18b5f615e856ecbe06b34a77d1396f48cbbba6f474ad0adc5a542c5d7d767801.safe-inspect.json` | `faa561b00c647a06432d8d29f5fd4d8c7cad1c41c2c4dc20ed7fdeaca45f852c` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/daemon-inspect/18ec4bbe6f233181a33bf9e605747e25ea10e8dc0a4caa8200c0524c60e726b7.safe-inspect.json` | `cc0dc10620f07af28476e8c5368047fb7a6d45eee617b74e7866ef26e3bdcd65` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/daemon-inspect/2b69937c92dd72a4aa03736caf63e3672970e9b43eec332a6efa0eb4842b4fed.safe-inspect.json` | `6553e9f452be781e891c635999877798328ce12a9f29adac9c8e8e7eb27581e1` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/daemon-inspect/2f92afceb47252d8ab6bac87ade3c800b8b7b08aa7a5af02513a92d37f552026.safe-inspect.json` | `db5626d411ec877ae9ec322dd7e8385ca67a44c0e55085fe9f4c09cf22408d6d` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/daemon-inspect/310942bf8c0830b53c981560f7dbbf06a4d40fc88c4ef0cbfa54ac35b466fd87.safe-inspect.json` | `eaaf294e26dbae7750223e98790ea6805ec34b1d90cee06619d07b9538eb46a0` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/daemon-inspect/34ffe2e8cb5f9a584e6857e2390ed69cf9cb0c16e8c033a069143fd62c2d8cfa.safe-inspect.json` | `a99f64a1ad32b92ac8605260095f1dfdfcf10bba35e3d1d430272414ef2f3c58` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/daemon-inspect/3ffc576a19cb8a429325bcc8026e4919ed68fa53d55f6341afbe29724a5a186e.safe-inspect.json` | `5e3fc2a4a5f758f938940b6bf16e4ecb6b6fb8d23f4f64958fa0d619eacf6f8e` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/daemon-inspect/403145f010e6445eca74972c1e1140854ce5613188fbd4cb69a35ceda1972030.safe-inspect.json` | `1c02edd899a7f48f7717a997ea66d49248bd8162e8b11c82ed9fe1529a98a4ae` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/daemon-inspect/46b25f5c7ae46947bc4dc187cac14c5106c746d40b93563e8e64eeb1fce9db4f.safe-inspect.json` | `0012e21c303dc68ce52fefbaf4c5a5f4454ff01cc3a12c0dd8585e5eccdf6730` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/daemon-inspect/4d0674aa26e9acdad74d5a142201f5862ec88730ef8665b2d7771cebc272a68f.safe-inspect.json` | `49e0357c8c76fb690b97c49c60fd798b6cac10c3e6e00221e18eda09d75085f8` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/daemon-inspect/4f63fc6e4432a0714eb18360ebf1c7e9e7c2e88c0317df4267d9317df5a105f8.safe-inspect.json` | `29c27552505e5aa7b1baeef0afd38ecc69cfd9d1b4ee8017e89aae48155df056` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/daemon-inspect/5091ace65378b2972ddc227e4cf74157467fafb3ed33d389985e37f201348fbb.safe-inspect.json` | `b51a1fe37292384135246d27c515c62e33a13aae51d7ab5b474db259d4683c2e` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/daemon-inspect/5aae23b9d1b6855fe9c6a7f8e6e6f4b85ee88abec9e0b7922e0dbbdef78798d6.safe-inspect.json` | `d8032a4f75048a0d86fbcc346d7c7762801897a46bb959134166e427e8a038c3` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/daemon-inspect/5dff536198c025288050d48613cc02091ee27ec6453fa344b1529b3c341dd78a.safe-inspect.json` | `9f13f23901988c8d2f91074bd44782133a70a6ec5f023e55fdf57627b76c5be8` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/daemon-inspect/675094e8ace0b849f4ecb93332432f5f7cc0db9a1e05e0d97c52ecd2fe39accc.safe-inspect.json` | `a22661dcd609e3e443f196196fc02e0e00b939979b55d2fb91240193da119e37` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/daemon-inspect/745eb3ed039a5511a960eff65b9f868605f8e445ae3806fe0e5c94eb359696ec.safe-inspect.json` | `67959011c390fe8973b738fcb814e22f5c13181a947c85128c8a82adf7f7dd0f` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/daemon-inspect/86150e6f75bd020abae56eb740cd72ced891244a8df0188e0b2309770beb5179.safe-inspect.json` | `0f54a8c5373d51f3eb33865e796c80217d47c75d03df8686d6433cccc21289dc` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/daemon-inspect/871616502b17e86e230327f63524a57968271385fd168588dfcb317c164395f4.safe-inspect.json` | `55df1d62f3ebda890b2455ea961f97ec8df5341b656e1ac17bacde4619313854` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/daemon-inspect/8ca546c45ab73ffa9b62e906d9191a21aa59e6e9cfc6a5c29abc2449bb1d1bc6.safe-inspect.json` | `ddeddda2ed3a38a92721214bfdff530de630af30717727d66072da0e76e4e86d` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/daemon-inspect/8d4cc83c800dc37998f6544238455f447f19c52797c2c3e70fe219b0c7fba286.safe-inspect.json` | `45f05b4bab6609f9844282f2898cbdbd23a8608cd3e707e14fd2b24ff20e1ef1` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/daemon-inspect/933d4138439d542bcc16fdbd208a2dea7f8256078e649b208af4ac090f134cd2.safe-inspect.json` | `804117ed396591494968f8af50841b23ec0ab9762b5a8342729117b3976736e9` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/daemon-inspect/9a503b18c15606944671feb81b0fe3a30c7fc9652f7f6b18cb469ab218867dbe.safe-inspect.json` | `7c1fc4f9ed144bf65c23792522fd3168ba913dea4a858cd93a6831dd17cf22b4` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/daemon-inspect/a00ee6f2368f62434650ebc3decdbf05069b34ef4ca6fbaef38ed0bc8914baf0.safe-inspect.json` | `bea8ee38049cbb5915b5ee158fb07c81f7ace0735ba0c3753f92cee779227e2e` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/daemon-inspect/a3a8fec0f7ec2ad2cf9fc9258bda96f18868d5a6e85472fdeebbdb194e1f4154.safe-inspect.json` | `2e2707e7714a39d660d5340fa79f4f6b2f8fbb366a798db4da2a9026a40fa1b0` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/daemon-inspect/b04de203abc4be78070522994572cdfc98eb3c4997d2b83a98cabd2b2c511c55.safe-inspect.json` | `3d0b69f8dae6e5b474ab42fadf776f2927b83e729dad3069f2769daf0ed653d6` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/daemon-inspect/b202450e1dc411966925081c160ab6285c9ed14ec69accb13498443591273f0a.safe-inspect.json` | `325781d8770665fa57ffa208ef1ba9f96123bffd8e1062ff18738b86ff248137` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/daemon-inspect/b914d844bac27820107b4c1e2d39e6de3dcfa62c2988487bd2854ba9bb5867ac.safe-inspect.json` | `fbbf03c012c044145b61c587e0faaebfc35eceab2e61525c782538751f8d294b` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/daemon-inspect/bf42ad53c6e525b184817444655b210d8d221bf93106384245295c150e2288f2.safe-inspect.json` | `e97c4691d11bcbd6be518e3ebbca42f0b8aef0452b397c972e20cc6064f077df` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/daemon-inspect/bfd236995a86d7527869906e148e8878f2079e13d527da0fc8b750cc39476eab.safe-inspect.json` | `7aad7749110b9aa5b41382d3ef869ee4e3b4896c45b503aca45e1c258ee19af9` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/daemon-inspect/c7b2aeb49faac1beb34692fdc985ef00fd44946c7bd29b31ac75e630b13ce98d.safe-inspect.json` | `496144a19d9401e3ac2ec338482d8c2767dbcb036cecc8c0d49cfc2b14c128f5` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/daemon-inspect/cef9fbc976ae784fd71ec83b64274a58aa21506be41335b54ead16e88cc5dad8.safe-inspect.json` | `538e03ae3f0e9beb6d56fb6ecc97eb738505294a5e444b49cafcb27e179270c7` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/daemon-inspect/d0d53b4995a4869a0239886b6c6f09c767ddb5de61795062cab57c34c9422bb8.safe-inspect.json` | `6e03f20faf22ad72330d1619b7cef8d76347f42a72272d68d6ed6230ee7b0302` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/daemon-inspect/dca78ea9774f135755cc306019593f805a7dbcf2000c599ef9204e7aac3b832a.safe-inspect.json` | `a8b3a71088d0556f3d9f7a2697f8bb416f5e3d28e0b243ecbf6436d25ef85213` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/daemon-inspect/dccbf4b9cdb12970c50d1ea8405b365736f83659077622f2d3f07fe4f7833291.safe-inspect.json` | `0ff6f49826b064fbde0e46a455f3df96e3c2a5bbe48b96c7093f2134ae644749` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/daemon-inspect/e60c2198b56d064403de716b51b57efc6cb83ad89ff025365d75570697b7f1c5.safe-inspect.json` | `9ce9e0020d941628ff98c54a3f257e88f699adba4b564040372e094497267ce1` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/daemon-inspect/ead57834968d8aaba672def31a33f6e75fa4c22828a31b1fc6d6ebef549cea93.safe-inspect.json` | `fa53f70156676dd4ba04bf80c3628df82456ba10cfc236defeb1bb4cb48b374b` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/daemon-inspect/ed62d360804cffbde6b2e8bf7e20f231b678ab769c5655d7a10d9ce738ee02c6.safe-inspect.json` | `9b7ef00d7a0f8d526bb43e5b8c9cef6775fb62535cd2952beda8d8287dea8f44` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/daemon-inspect/f456a45d3026e908f79fc3012e827e1c965e669c098dcd0ebb3b0bfcedf5976b.safe-inspect.json` | `78a412c001c1e7a77ef9cb7d92fcc5b965140901f3f4fe3dd469dc2eecfb75be` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/daemon-inspect/fbe0a5c79add3b53641f4061b5f93caf1786089170e20894392a085adc5dea65.safe-inspect.json` | `d9646ee7fbcd074f4d5410eccf52867f89c06dba13d91fdd08dc69920db544ee` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/database-proof-command.json` | `cd3890b7c7b77bd5eaebbcc81abeca7b87f43b51e1a6c9a655ea93a2985af4ec` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/database-proof-output.txt` | `2b0b7b8b03cbdda58fb7333ce36b1ab5dcf6299f8226120b91b07a4f140da7d3` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/database-proof.json` | `6c5145d7516192d6ef7bbad7201a0215de9ffeb288b735a2f59ab3bd7e2e2c66` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/docker-resource-events.jsonl` | `2da72fec952eb0c40327ab0e1c742a31eb15a11504d0960a622dbedab0b6afd7` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/exact-resource-cleanup.py` | `d0c24d7da45b2d56ae21643707f10024f2b16986035db6c7a2fc9f60a4dc8773` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/execution-summary.json` | `d2b680dd7f366085a19c6ef64a229be3fdfc7f0797f445ed78a303d4bdb00133` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/external-support-provenance.json` | `9bce851394d45ed9b694871a2ab5f0bb00d4758255761f18413284923012126f` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/final-git-inventory.json` | `716fc90c8b9435306f6b8c3e2a6505909ba8340d925ba8869620a64a44df209b` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/final-validator.py` | `63b3ce4cfc8d635ecfb6ad2d6b6e317e7969be314880e7917b5a15f00ffa8dfd` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/fresh-command.json` | `a38ac8b3dfa49c397c062920ed9937b926b061c0d703c7cce943001413b898ef` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/fresh-gradle-output.txt` | `5f889cebaa8cb4b468abdd486e40e3c8c0872fe3574dcc66dbd2fb560a018d7e` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/populated-command.json` | `173daac18d1a700bf68f38f54321e10d9ee1fa0d46b591f3450b2cd39850ab2a` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/populated-gradle-output.txt` | `af3a2911a7adfc76b9551d9bf4662ffca7a4247b915c51609c4fa803a0e5aee7` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/preflight.json` | `b85b02fee0719d2c87b982354e82539b7fce1302ee5a22eb957433acca180253` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/preserved-workspace-drift.patch` | `4f0e4ba7604ecb96688e8a446838862ec48b23026279102bb205856d7873b0f9` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/regression-blocker.json` | `54d31b45ff57fc97c5013c70507eb5bd1cb34ac99a4c817bf8d1d812bbd5f4b6` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/regression-command.json` | `3deb25aad0c40170ca87e0b0511f7ccdb91a422b5e28b3eb9271aac4c696ab71` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/regression-counts.json` | `60056d38e8df1ec8129eb6113d3ec874eb996522a46077a68386648e21b8fc99` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/regression-failures.json` | `d1516eb23ad35a0f69cc0244f2018145316a0bb0dea26419b6f2734879367f76` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/regression-gradle-output.txt` | `561f20ad40fb5513291f4f4c9f94dc7944f3992d2dabb2354d2de06f5a932249` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/regression-results/migration/integrationTest/TEST-dev.vra.migration.MigrationSecurityIntegrationTest.xml` | `7a2ac593bf72eab4dadcb2b09eca21830a8e31a7bcb8620c11cecf7f7ce3a280` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/regression-results/migration/integrationTest/TEST-dev.vra.migration.OutboxMigrationSecurityIntegrationTest.xml` | `9d2d5f1c70ba576f2e280d0fc5e11e0df70e5b8ed6d53a64a3d78edf4566b775` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/regression-results/migration/integrationTest/TEST-dev.vra.migration.Poc04FoundationMigrationIntegrationTest.xml` | `662cc25536dd8dcf2648cc704d05ce78ecb5d62ef2dcd14bc568c5c6ac3113e1` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/regression-results/migration/test/TEST-dev.vra.migration.Poc04FoundationMigrationIntegrationTest.xml` | `e981c053e4eb066cd330dd0420e378df80897f38214d2ce9c3101e666cc45e19` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/regression-results/runtime/integrationTest/TEST-dev.vra.async.AsyncPermissionIntegrationTest.xml` | `639ffdfb90b0aa0a52775a50fe539f8e206e5c1672ae06589cba313aeacee5c9` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/regression-results/runtime/integrationTest/TEST-dev.vra.async.AsyncVisibilityIntegrationTest.xml` | `4d3a1a5ad7a4f2015233a5927b99c7efd66fad053520864af909bd6e88fc4c5c` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/regression-results/runtime/integrationTest/TEST-dev.vra.async.DeliveryClaimIntegrationTest.xml` | `69fa0829b7973434f8f19d090ac0c0b1a67e6b398b7272be425e1424a5511c6d` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/regression-results/runtime/integrationTest/TEST-dev.vra.async.DeliveryStateIntegrationTest.xml` | `15feac3bb20858fcca2c65793d3ce98be0bb9b731081c7f1d4ed17cb45e11082` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/regression-results/runtime/integrationTest/TEST-dev.vra.async.ProducerOutboxIntegrationTest.xml` | `035d5e5a77b359e6199de30ef97b391663d05e4ef168e9a3fbf47c5c62c490cd` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/regression-results/runtime/integrationTest/TEST-dev.vra.async.ProjectionInboxIntegrationTest.xml` | `9c05d5e08583956099d04ab39a573236c1ddc86d05c0143422e395f2130ac1d2` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/regression-results/runtime/integrationTest/TEST-dev.vra.async.ProjectionRebuildIntegrationTest.xml` | `500229555e991ebcd502b5a90b74c304f49474699a0a1ea90e51d93de216ae6b` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/regression-results/runtime/integrationTest/TEST-dev.vra.async.ReconciliationIntegrationTest.xml` | `259f46d61fa0235b6b2815295f481a89290072db44145bdbebd4b48aeb53ed82` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/regression-results/runtime/integrationTest/TEST-dev.vra.async.RetryReplayIntegrationTest.xml` | `fbe78c011f486a1a038fb31c93631602674c524d1c8d35802754f2a541cf3d9c` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/regression-results/runtime/integrationTest/TEST-dev.vra.async.ValidationSimulatorIntegrationTest.xml` | `f3a21dc48db21ddd819e31830c0ae50dd65be5aaee3ffe7dec33381c4f952adc` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/regression-results/runtime/integrationTest/TEST-dev.vra.async.WorkerProcessCrashIntegrationTest.xml` | `c1a217abda1881bc4b7622d7a3bf9dec4078b416ba3b64a4a15da02ecac24989` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/regression-results/runtime/integrationTest/TEST-dev.vra.inventory.IdempotentReservationApplicationServiceIntegrationTest.xml` | `0d23ef50f048ee784ec8d23a5a280c7298584a798a37ea78737e0ddb5d16535b` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/regression-results/runtime/integrationTest/TEST-dev.vra.inventory.ReservationConcurrencyIntegrationTest.xml` | `5645ac8e425dff33c9613e23459f4336c6678f26fe34620c5a3f4e4cea093add` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/regression-results/runtime/integrationTest/TEST-dev.vra.inventory.ReservationHttpPostgresIntegrationTest.xml` | `ed5e523bd5b92e1fbf02883d2e8b04b15c12ae198b6f598a4aea9aeb13e4c778` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/regression-results/runtime/integrationTest/TEST-dev.vra.inventory.ReservationIdempotencyRepositoryIntegrationTest.xml` | `97b64edaa579aaf65c5cdb62ba816e868ec99fa01d05a2179b22c6c22e2481d5` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/regression-results/runtime/integrationTest/TEST-dev.vra.inventory.ReservationTransactionIntegrationTest.xml` | `22cd8f3676afad4f6ab2fa389ff5bbe12b906b929489988ca77e7bec0fcc204e` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/regression-results/runtime/integrationTest/TEST-dev.vra.platform.health.HealthProbePostgresIntegrationTest.xml` | `42bcf9c55cc425346b0e12524004397b89041c17300dd0edf82859e83316a0f7` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/regression-results/runtime/test/TEST-dev.vra.async.AsyncPropertiesTest.xml` | `4ee42e4c4a9105c3fbc1badc2bf107ee5adfb0ad3c22f8124bba1e82e6f667d0` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/regression-results/runtime/test/TEST-dev.vra.async.DeliveryRetryPolicyTest.xml` | `13884ec673bc90aa80e8bfaf05ef1952ef67f8265c9abede0be6d8da29bb6ca7` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/regression-results/runtime/test/TEST-dev.vra.async.ReconciliationPolicyTest.xml` | `cbd55c93098564b4a195fc68dc0ffebd1c618720a835884fb2494b95b58dd091` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/regression-results/runtime/test/TEST-dev.vra.async.ReservationCreatedEventCodecTest.xml` | `ce4cd9c8c3c867bcdbca54b712fc50a684ab75e32290b9efcf818b97b9ca18d1` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/regression-results/runtime/test/TEST-dev.vra.async.architecture.AsyncArchitectureTest.xml` | `bb854deacb13e47f744fded0806631bd80d37914c3293ee353116da9b19a9e6b` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/regression-results/runtime/test/TEST-dev.vra.inventory.adapter.in.web.InventoryReservationControllerTest.xml` | `a877c392f2324f484bed5f3551cf66c175a925dc92de415683e347b481de9bf5` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/regression-results/runtime/test/TEST-dev.vra.inventory.application.ReservationApplicationServiceTest.xml` | `51083fb937199ea28aae545d3482577f243441e7d100d5fced03f1aca67a7fed` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/regression-results/runtime/test/TEST-dev.vra.inventory.application.ReservationRequestFingerprintTest.xml` | `4cbaec19f6f15e814d717913c862c873349c69e9e25108af8c919211e0966a76` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/regression-results/runtime/test/TEST-dev.vra.inventory.architecture.InventoryArchitectureTest.xml` | `7a8af3d868785567ac3f510fc9d7f232f6da84d158cf7d6136d1ba7682c39e34` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/regression-results/runtime/test/TEST-dev.vra.inventory.domain.InventoryReservationTest.xml` | `19c987d93bd2b89f87849e0d8c4a1112550efb630c58296ddc15ab63cddc2c59` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/regression-results/runtime/test/TEST-dev.vra.platform.configuration.RuntimeDatabasePropertiesTest.xml` | `99e4a3d0a8b8f78a7bdaddc5ac3332cb532e185d9d9ca3bf51d99a031ab64d72` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/resource-identities.jsonl` | `bb91358581d6fab22c0fbbf1e9259191cf469e7b19d88625038456f3a6e999c6` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/resource-recorder.py` | `a62abf850e9b7445c852804c50e61084a47824bd5e5ece3f0ab0e8d2066c2245` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/runner.init.gradle` | `1255ee9276b75e8f33819c911dc63aad7bedfcead64adb14f36a91a4f5657b34` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/source-change.patch` | `869616b5b47f31fdbb65e22767fa9a81880fb1702849e957e027a4b718ee3d08` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/source-review.json` | `8895a04652b2cbc976472f21f9831de6095635f39f1266bcf05dc8bb4d85530e` |
| `validation/poc-04/evidence/phase1/mount-remediation-89fcfa4d-21ba-4401-8ab9-8e0518da17d8/yaml-validation.json` | `a1d2d1e9c7b247be0da4222581e702642544598ab391f640ad03f80f207b0ae8` |

| External profile configuration | SHA-256 |
|---|---|
| `/Users/siwakornbundi/.colima/vra-poc04-test/colima.yaml` | `53675defaacbcb1a36f59a3760a61142b16199d4e8a513e6ac63e44fd9505d68` |

Execution stops after Phase-1 evidence collection. The source implementation and all reported successful results await independent Phase-1 review; Phase 1 is not closed. Phase 2 has not started.
