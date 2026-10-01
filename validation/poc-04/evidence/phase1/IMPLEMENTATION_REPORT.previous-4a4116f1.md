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
