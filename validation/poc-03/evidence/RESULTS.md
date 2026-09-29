# POC-03 bounded verification evidence

**POC-03 technical verification:** **VERIFIED — bounded G0–G12 independent closure review passed.** **Independent final review:** **PASS WITH NON-BLOCKING FINDINGS.** **POC-03 Git closure:** **CLOSED.** Stage-J verification commit: `073f63e378bb7b2ef1547b8bae9d700543c0eb40`; parent: `0ae2b394c8f336a7aace27d1632d8de056a15525`. Remote synchronization: **VERIFIED / CLOSED** through merged PR #1. Hosted CI: **PASS** on the PR head and merged `main`. This evidence does not certify deployment.

## Provenance and environment

- Execution: 2026-09-28–29 Asia/Bangkok; final narrow-remediation clean build executed 2026-09-29.
- Initial read-only preflight: branch `poc/03-outbox-recovery`, exact required HEAD, literally clean working tree, `git diff --check` clean.
- macOS 27.0 (26A428), aarch64; Homebrew OpenJDK 21.0.12.1; Gradle 9.7.1.
- PostgreSQL Testcontainers image `postgres:17.11`; Testcontainers 2.0.5; PostgreSQL JDBC 42.7.13; Spring Boot 4.1.1; Flyway 12.4.0; ArchUnit 1.4.1.
- Frozen spec SHA-256: `bc08efd024ea80a783ebd028f6ec8a0e7e0af64d391a3eb0684c251c43418fbb`; approved plan SHA-256: `39abaaa6394e54529f72084879c2fa89bedaadfcbc48150dec6f12e9fb6da8f7`. No repository-recorded expected hashes were found.
- No V1/V2/V3, role bootstrap, grants, frozen specification, approved plan, ADR, engineering guide, or CI workflow was edited.

## Post-commit Git closure

- **Stage-J verification commit:** `073f63e378bb7b2ef1547b8bae9d700543c0eb40`; **verified parent:** `0ae2b394c8f336a7aace27d1632d8de056a15525`.
- **Commit integrity:** **PASS**. The verified committed change set has exactly **8 paths**. Committed SHA-256 values match the reviewed, tested, and staged SHA-256 values for all 8 files. The commit diff check was clean.
- **Working tree after the commit-integrity check:** **CLEAN**. **POC-03 Git closure:** **CLOSED**. This later documentation reconciliation is separate from the verified Stage-J commit.
- **At the local post-commit checkpoint:** remote synchronization and hosted CI were **NOT EXECUTED**. The later merge and hosted runs are recorded below; no production readiness is claimed.

## Post-merge synchronization and hosted CI

- [PR #1](https://github.com/siwakon8285/VRA-Fastform/pull/1) was **merged** from POC-03 branch head `5a88d4d9a562652fc856a32d266ddc055e8e6c37` into `main` at merge commit `fb8d0034be9d57aebcf07c5bb8702d3654ee5733`. The branch head is reachable from `main`: **VERIFIED** by local Git ancestry. **Remote synchronization: VERIFIED / CLOSED.**
- [PR Backend CI run 36586572864](https://github.com/siwakon8285/VRA-Fastform/actions/runs/36586572864): `pull_request` at head `5a88d4d9a562652fc856a32d266ddc055e8e6c37`; `Backend CI`, Java 21 / PostgreSQL; **COMPLETED / SUCCESS (PASS)**.
- [Post-merge main Backend CI run 36588526956](https://github.com/siwakon8285/VRA-Fastform/actions/runs/36588526956): `push` at head `fb8d0034be9d57aebcf07c5bb8702d3654ee5733`; `Backend CI`, Java 21 / PostgreSQL; **COMPLETED / SUCCESS (PASS)**. The job steps `Run backend verification and build artifacts`, `Prove PostgreSQL integration suites executed`, and `Verify deployable JVM artifacts exist` all completed successfully.

## Configuration and authority

One runtime boot JAR has `API` (default), `OUTBOX_WORKER`, and `RECONCILER` modes. The root `VraApplication` remains `@SpringBootApplication`; API scans `dev.vra.inventory` and `dev.vra.platform` and explicitly imports narrow `ApiMode` producer composition. The worker and reconciler retain isolated non-web boot compositions. Actual SQL `current_user` in tests was `vra_runtime` for API publication, `vra_outbox_worker` for delivery, `vra_reconciliation_worker` for observation, `vra_async_operator` for controls, `vra_projection_rebuilder` for rebuild, `vra_async_observer` for visibility, and `vra_migrator` for migration. Wrong worker/reconciler identities and reconciler simulator use without explicit validation mode fail startup in `WorkerProcessCrashIntegrationTest`.

The V3 persisted current-cycle limits are **5** delivery execution-bearing claims and **4** reconciliation query-bearing claims. The only additional claim is one recovery-only, non-dispatched/non-querying fence claim. Row-shape ceiling is 16, not a configurable workload budget. PostgreSQL `statement_timestamp()` governs lease, eligibility, and visibility age. Default process tuning is `batchSize=8`, `maxInFlight=8`, 200 ms poll; process tests override valid settings to `batchSize=maxInFlight=1` for bounded proof. Capacity is reserved before durable claims; the worker uses a bounded executor/semaphore, and PostgreSQL is durable work storage.

The validation simulator runs in an independent JVM with a separate PostgreSQL database. Its test-only `sim_request_history` records EXECUTE/QUERY order without changing effect semantics. Stage-I process proof uses the production `dev.vra.VraApplication` entry point, `ProcessBuilder`, real child PIDs, `destroyForcibly()`, fresh PIDs, durable DB/simulator assertions, guarded drain and relinquishment. The Stage-J local clean-run XML captured, among others, Scenario-A claim PID `96662 → 96663` with distinct old/new claim tokens; Scenario-A post-effect PID `96678 → 96679`; blocked Scenario-B PID `96670 → 96671` with reconciler `96672`; lost-response Scenario-B PID `96647 → 96648` with reconciler `96652`. The process test asserts maximum claimed work **1** with `maxInFlight=1`, no later claim after drain admission closes, no second event EXECUTE after external drain, production RELINQUISH history, old-token denial, and normal reclaim. That local clean execution reran the 12-method Stage-I class. The simulator's durable effect count is exactly one in the lost-response recovery path.

## Commands and fresh execution

Earlier Stage-J focused invocations ran from `backend/`; the three remediation focused invocations ran from the repository root using `./backend/gradlew -p backend`. All used the existing task/tag routing:

```text
./gradlew :runtime:test --tests 'dev.vra.async.architecture.AsyncArchitectureTest' --console=plain
/usr/bin/time -p ./backend/gradlew -p backend --no-daemon :runtime:test --tests dev.vra.async.architecture.AsyncArchitectureTest --console=plain
/usr/bin/time -p ./backend/gradlew -p backend --no-daemon :runtime:test --tests dev.vra.async.ReservationCreatedEventCodecTest --console=plain
/usr/bin/time -p ./backend/gradlew -p backend --no-daemon :runtime:integrationTest --tests dev.vra.async.ProducerOutboxIntegrationTest --console=plain
/usr/bin/time -p ./backend/gradlew -p backend --no-daemon :runtime:integrationTest --tests dev.vra.async.AsyncVisibilityIntegrationTest --console=plain
./gradlew :runtime:integrationTest --tests 'dev.vra.async.AsyncVisibilityIntegrationTest' --console=plain
./gradlew :runtime:integrationTest --tests 'dev.vra.async.DeliveryStateIntegrationTest' --console=plain
./gradlew :runtime:integrationTest --tests 'dev.vra.async.ReconciliationIntegrationTest' --console=plain
./gradlew :runtime:integrationTest --tests 'dev.vra.async.AsyncPermissionIntegrationTest' --console=plain
./gradlew :migration:integrationTest --tests 'dev.vra.migration.OutboxMigrationSecurityIntegrationTest' --console=plain
./gradlew :runtime:integrationTest --tests 'dev.vra.async.WorkerProcessCrashIntegrationTest' --console=plain
./gradlew :runtime:integrationTest --tests 'dev.vra.async.ProducerOutboxIntegrationTest' --console=plain
```

The earlier Stage-J local candidate clean-build command, executed from the repository root after its source changes, was:

```text
/usr/bin/time -p ./backend/gradlew -p backend --no-daemon --warning-mode all :runtime:clean :migration:clean :runtime:check :migration:check :runtime:bootJar :migration:bootJar --console=plain
```

That Stage-J local clean build completed `BUILD SUCCESSFUL`, 17 executed tasks, **99.75 s** measured command wall time. The table uses the XML suite elapsed values from that run; these are sums of JUnit class elapsed time, not independently measured Gradle task wall times. All required focused classes also passed in their focused runs. Recorded focused command wall times are shown where captured; their XML values below are from that local clean run. The separate final bound execution and independent review are recorded below.

| Task / class | Tests | Skipped | Failures | Errors | Fresh XML elapsed | Focused command wall |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| `:runtime:test` total | 56 | 0 | 0 | 0 | 1.851 s | not separately captured |
| AsyncArchitectureTest | 9 | 0 | 0 | 0 | 0.662 s | 3.27 s (final focused run) |
| ReservationCreatedEventCodecTest | 3 | 0 | 0 | 0 | 0.070 s | 2.62 s (final focused run) |
| `:runtime:integrationTest` total | 113 | 0 | 0 | 0 | 65.039 s | not separately captured |
| AsyncVisibilityIntegrationTest | 3 | 0 | 0 | 0 | 1.640 s | 6.69 s (final focused run) |
| DeliveryStateIntegrationTest | 3 | 0 | 0 | 0 | 1.098 s | 3.12 s (earlier Stage-J focused run) |
| ReconciliationIntegrationTest | 16 | 0 | 0 | 0 | 3.677 s | 6.11 s (earlier Stage-J focused run) |
| AsyncPermissionIntegrationTest | 2 | 0 | 0 | 0 | 1.832 s | 2.68 s (earlier Stage-J focused run) |
| WorkerProcessCrashIntegrationTest | 12 | 0 | 0 | 0 | 39.313 s | 40.99 s (earlier Stage-J focused run) |
| ProducerOutboxIntegrationTest | 7 | 0 | 0 | 0 | 2.670 s | 6.48 s (final focused run) |
| `:migration:test` | 0 | 0 | 0 | 0 | no XML; task executed | not separately captured |
| `:migration:integrationTest` total | 15 | 0 | 0 | 0 | 24.007 s | not separately captured |
| OutboxMigrationSecurityIntegrationTest | 13 | 0 | 0 | 0 | 21.126 s | 22.01 s (earlier Stage-J focused run) |

The Stage-J local clean run retained POC-01/02/03 runtime unit, PostgreSQL integration, migration, and Inventory architecture tests. Earlier exploratory runs found an invalid ArchUnit rule/API use and an `ApiMode` direct import that activated a producer in `@WebMvcTest`; both were corrected within Stage-J test/bootstrap scope. The first attempted full build failed on that MVC slice, then a successful full build ran. The Stage-J narrow-remediation run above passed. An initial sandboxed focused invocation on 2026-09-29 stopped before tests because `~/.gradle` was not writable; the same command succeeded with approved access. These were explained remediation reruns, not unexplained flaky passes. No unexpected timeout, deadlock, process leak, or cleanup failure was observed; post-run process listing found no VraApplication/simulator child. At the time of this local run, hosted CI was **NOT EXECUTED**.

## G11 visibility and SQL authority

`JdbcAsyncVisibility` is a typed, read-only JDBC adapter constructed only with effective `vra_async_observer`; a wrong identity is rejected. Its exact read surfaces are:

1. `deliveryBacklog`: `target_code,state,count(*)` from `outbox_delivery` for READY, PROCESSING, RETRY_WAIT, FAILED, RECONCILIATION_REQUIRED.
2. `reconciliationBacklog`: `target_code,case state,count(*)` from `reconciliation_case` joined to delivery for PENDING, CHECKING, WAITING, OPERATOR_REQUIRED.
3. `oldestOutstandingAge`: per-target count and `GREATEST(0,FLOOR(EXTRACT(EPOCH FROM (statement_timestamp()-min(created_at)))*1000))`; includes the five non-SUCCEEDED/non-CLOSED delivery states above.
4. `deliveryActivity` and `reconciliationActivity`: per-target `action_code,count(*)` from the respective immutable history joined to durable current rows.
5. `deliveryHistory(eventId)` and `reconciliationHistory(caseId)`: ordered history ID, opaque event/case ID, from/to state, action, lifetime attempt, automatic cycle, count, persisted limit, stable class/observation/reason. They do not select claim tokens, event payload, reservation/sku contents, credentials, HTTP bodies, or raw exceptions.

`AsyncVisibilityIntegrationTest` compares every listed backlog state and success/retry/failure/exhaustion activity to independent authoritative SQL. It brackets age with PostgreSQL-derived before/after values, compares history cycle/count/limit/lifetime/action/reason to database history, distinguishes normal from recovery-only exhaustion in both state machines, checks that DTO components and rendered history exclude durable claim-token values, checks seeded sensitive IDs/passwords/raw-exception text are absent, and denies actual observer mutation, event SELECT, and SET ROLE with SQLSTATE `42501` while asserting current rows/history unchanged. `AsyncPermissionIntegrationTest` and migration ACL tests separately deny observer writes. No visibility query mutates durable state and no production HTTP visibility endpoint was added.

## Full G7 requirement-to-test matrix

The named tests ran in the final clean build. Assertions use PostgreSQL delivery/case/history, independently durable simulator operation/effect/request history, or actual process PIDs rather than logs alone. `W` = WorkerProcessCrashIntegrationTest; `R` = ReconciliationIntegrationTest; `S` = ValidationSimulatorIntegrationTest; `D` = DeliveryClaimIntegrationTest; `T` = RetryReplayIntegrationTest; `P` = ProducerOutboxIntegrationTest; `V` = AsyncVisibilityIntegrationTest.

| Frozen G7 obligation | Test method and authoritative assertion |
| --- | --- |
| C1 dies after PROCESSING; fresh C2 reclaims without execute first; ambiguous pre-send/send and crash before UNKNOWN | W.`killedExternalRequestReclaimsToUnknownBeforeAnyFurtherExecute`: actual force-killed PID, fresh PID/token, durable UNKNOWN/case, simulator request order. T.`reclaimedExternalClaimStillCannotBypassUnknownHandoff`: V3 handoff guard. |
| Query-only reconciliation before later execute eligibility, including relinquishment/reclaim | W.`killedExternalRequestReclaimsToUnknownBeforeAnyFurtherExecute` and W.`lostResponseAfterDurableEffectIsRecoveredByFreshWorkerAndReconcilerPids`: `sim_request_history` QUERY before any later EXECUTE. W.`productionDrainRelinquishesStoppedAttemptWithCurrentToken` and T.`reclaimedExternalClaimStillCannotBypassUnknownHandoff`: current-token/reclaim and reconciliation-first guards. |
| External durable commit/lost response, stable ID, exactly one effect, convergence | W.`lostResponseAfterDurableEffectIsRecoveredByFreshWorkerAndReconcilerPids`, R.`lostResponseUnknownHandoffThenCommittedObservationConfirmsExactlyOneEffect`, S.`lostResponseAndTimeoutRemainUnknownDespiteOneDurableEffect`: independent simulator effect count 1, UNKNOWN/case and final SUCCEEDED. |
| Confirmed no effect gives bounded same-event safe retry; exhaustion preserves UNKNOWN | R.`strongAbsenceResolvesCaseAndSchedulesSameEventSameCycleRetry`, R.`exhaustedDeliveryCycleResolvesNoEffectWithoutOpeningAnotherRetry`, R.`fourIndeterminateQueriesExhaustWithoutChangingPersistedLimit`, S.`acceptedPendingIsIndeterminateAndObservationNeverCreatesAnEffect`: event ID, RETRY_WAIT or OPERATOR_REQUIRED, no blind effect. |
| One event/one delivery/one validation external target; no composite fanout | S.`validationTargetBootstrapProducesOnlyOneScenarioBDelivery`, P.`ordinaryAndIdempotentFirstSuccessCommitExactProducerRows`: unique event/delivery/target SQL. |
| Independent simulator; same ID dedupe/conflicting payload rejected | S.`sameIdentityAndSemanticPayloadDeduplicateWhileConflictDoesNotMutate`, S.`observationCannotConfirmAbsenceWhileExecuteWaitsToRegister`: simulator operation/effect rows and conflict denial. |
| Reconciler fresh-process rediscovery, interrupted CHECKING, durable eligibility/current owner; no cursor authority | W.`reconcilerDrainStopsClaimsAndFreshPidRediscoversDurableCase`, R.`recreatedRepositoryRediscoversDueWaitingAndExpiredCheckingWithoutLimitExpansion`, R.`reclaimedTokenFencesEveryOldFinalizationAndCurrentTokenCanFinish`: fresh PID/repository, DB checkpoints, token fencing. No process-local scan cursor exists, so a cursor-reset operation is NOT APPLICABLE. |
| Persisted four-query cycle, one extra non-query recovery-only claim, lifetime history; visible operator backlog | R.`fourIndeterminateQueriesExhaustWithoutChangingPersistedLimit`, R.`expiredFourthCheckingGetsOneRecoveryOnlyClaimAndNoObservation`, D.`fiveDispatchableClaimsThenOneRecoveryOnlyForEachScenario`, V.`normalAndRecoveryOnlyExhaustionRemainDistinctInImmutableHistory`: persisted counts/limits, immutable actions, OPERATOR_REQUIRED backlog. |
| Stale token denial, active-case uniqueness, later uncertainty creates a new case | R.`reclaimedTokenFencesEveryOldFinalizationAndCurrentTokenCanFinish`, R.`activeCaseConflictAbortsSecondHandoffWithoutPartialMutation`, DeliveryStateIntegrationTest.`reconciliationEdgesUseCurrentCaseTokenAndPairedDeliveryTransitions`: old-token denial, uniqueness, distinct later case ID. |
| Controlled resume new case cycle/count 0/limit 4, lifetime preserved; unresolved close keeps UNKNOWN | R.`operatorResumeStartsNewCaseCycleWithoutErasingLifetimeOrDeliveryCycle`, R.`operatorClosePreservesUnknownAndTerminallyStopsCaseAndDelivery`, DeliveryStateIntegrationTest.`reconciliationEdgesUseCurrentCaseTokenAndPairedDeliveryTransitions`: paired DB state/history and stable knowledge. |
| Paired transition/history rollback, resume/close/replay races | R.`requiredHistoryFailuresRollBackAllSevenPairedTransitions`, R.`operatorResumeAndCloseRaceToOnePairedTransitionWhileReplayIsDenied`, OutboxMigrationSecurityIntegrationTest.`deferredPairsAndRecoveryEvidenceRejectAtCommitAndPermitCompoundShapes`: DB rollback/current pairs. |
| Idempotent observable processing and backlog | S.`sameIdentityAndSemanticPayloadDeduplicateWhileConflictDoesNotMutate`, W.`externalDrainDoesNotClaimOrExecuteTheNextDurableEvent`, V.`allCurrentStatesActivityAgeAndSafeHistoryMatchPostgresql`: effect count, durable READY, observer backlog/history. |

## Full G9 delivery/reconciliation transition and constraint matrix

Every frozen semantic edge is exercised by the named final-run tests. `DS` = DeliveryStateIntegrationTest; `DC` = DeliveryClaimIntegrationTest; `RR` = RetryReplayIntegrationTest; `R` = ReconciliationIntegrationTest; `M` = OutboxMigrationSecurityIntegrationTest; `W` = WorkerProcessCrashIntegrationTest.

| Delivery edge | Guard/evidence |
| --- | --- |
| creation→READY | ProducerOutboxIntegrationTest.`ordinaryAndIdempotentFirstSuccessCommitExactProducerRows`: business/event/delivery atomic rows; DS.`readyRetryFailReplaySuccessAndTerminalGuardsPreserveCycleHistory`: initial 0/5. |
| READY→PROCESSING; RETRY_WAIT→PROCESSING | DC.`readyClaimCommitsAndRolledBackClaimLeavesNoTrace`, DC.`retryWaitBecomesClaimableOnlyAtDatabaseEligibilityTime`, DS.`readyRetryFailReplaySuccessAndTerminalGuardsPreserveCycleHistory`: committed token, DB eligibility/count. |
| PROCESSING→PROCESSING reclaim | DC.`expiryDoesNotRevokeButCommittedReclaimFencesEveryOldOperation`, W.`committedScenarioAClaimSurvivesAbruptPidDeathAndFreshPidReclaims`: new token/attempt and stale denial. |
| PROCESSING→SUCCEEDED | DS.`readyRetryFailReplaySuccessAndTerminalGuardsPreserveCycleHistory`, ProjectionInboxIntegrationTest.`normalClaimConsumesAtomicallyThenFinalizes`: current token and durable effect. |
| PROCESSING→RETRY_WAIT; PROCESSING→FAILED | DS.`readyRetryFailReplaySuccessAndTerminalGuardsPreserveCycleHistory`, RR.`durableTransientCycleExhaustsThenReplaysSameEventIntoNewCycle`, RR.`explicitTerminalClassesUseOnlyStableV3Pairs`: safe class/budget/next eligibility or stable terminal reason. |
| PROCESSING→RECONCILIATION_REQUIRED | R.`lostResponseUnknownHandoffThenCommittedObservationConfirmsExactlyOneEffect`, M.`deferredPairsAndRecoveryEvidenceRejectAtCommitAndPermitCompoundShapes`: current token and atomic PENDING case. |
| RECONCILIATION_REQUIRED→SUCCEEDED/RETRY_WAIT/FAILED | R.`lostResponseUnknownHandoffThenCommittedObservationConfirmsExactlyOneEffect`, R.`strongAbsenceResolvesCaseAndSchedulesSameEventSameCycleRetry`, R.`fourIndeterminateQueriesExhaustWithoutChangingPersistedLimit`: guarded case resolution/remaining budget or operator outcome. |
| FAILED→READY/RECONCILIATION_REQUIRED/CLOSED | RR.`durableTransientCycleExhaustsThenReplaysSameEventIntoNewCycle`, R.`operatorResumeStartsNewCaseCycleWithoutErasingLifetimeOrDeliveryCycle`, R.`operatorClosePreservesUnknownAndTerminallyStopsCaseAndDelivery`: controlled operation, fixed new cycle, paired knowledge. |

| Reconciliation edge | Guard/evidence |
| --- | --- |
| creation→PENDING | R.`lostResponseUnknownHandoffThenCommittedObservationConfirmsExactlyOneEffect`, M.`deferredPairsAndRecoveryEvidenceRejectAtCommitAndPermitCompoundShapes`: atomic matching UNKNOWN delivery. |
| PENDING→CHECKING; WAITING→CHECKING | R.`pendingSimulatorObservationPersistsIndeterminateHistoryAndUnknownCaseReason`, R.`fourIndeterminateQueriesExhaustWithoutChangingPersistedLimit`: committed token, DB eligibility, limit. |
| CHECKING→CHECKING reclaim | R.`reclaimedTokenFencesEveryOldFinalizationAndCurrentTokenCanFinish`, R.`expiredFourthCheckingGetsOneRecoveryOnlyClaimAndNoObservation`: new token and one non-query recovery path. |
| CHECKING→WAITING/RESOLVED/OPERATOR_REQUIRED | R.`pendingSimulatorObservationPersistsIndeterminateHistoryAndUnknownCaseReason`, R.`strongAbsenceResolvesCaseAndSchedulesSameEventSameCycleRetry`, R.`lostResponseUnknownHandoffThenCommittedObservationConfirmsExactlyOneEffect`, R.`fourIndeterminateQueriesExhaustWithoutChangingPersistedLimit`: current token, observation mapping, paired delivery state. |
| OPERATOR_REQUIRED→PENDING/CLOSED | R.`operatorResumeStartsNewCaseCycleWithoutErasingLifetimeOrDeliveryCycle`, R.`operatorClosePreservesUnknownAndTerminallyStopsCaseAndDelivery`: controlled paired transition, UNKNOWN retained on close. |

| Constraint / forbidden shape | Authoritative denial |
| --- | --- |
| SUCCEEDED/CLOSED terminal; FAILED and UNKNOWN cannot ordinary replay; stale/relinquished token cannot finalize | DS.`readyRetryFailReplaySuccessAndTerminalGuardsPreserveCycleHistory`, RR.`replayRejectsSucceededClosedReconciliationRequiredUnknownAndActiveCase`, DC.`relinquishmentImmediatelyFencesAndRollbackKeepsOldLease`, R.`reclaimedTokenFencesEveryOldFinalizationAndCurrentTokenCanFinish`. |
| Safe stable reasons; immutable event/history; append-only history | M.`staticShapesIdentityForeignKeysAndUniquenessRejectMalformedRows`, M.`immutableEvidenceRejectsOwnerRewriteAndFailedHistoryAppendRollsBackWholeOperation`: actual owner/worker denied UPDATE/DELETE, SQLSTATE and rollback. |
| One active case, no RECONCILIATION_REQUIRED orphan, paired delivery/case atomicity | R.`activeCaseConflictAbortsSecondHandoffWithoutPartialMutation`, R.`requiredHistoryFailuresRollBackAllSevenPairedTransitions`, M.`deferredPairsAndRecoveryEvidenceRejectAtCommitAndPermitCompoundShapes`: unique/deferred checks at commit and unchanged state after failure. |
| Invalid CHECK/UNIQUE/FK/pair shapes; limit 1..16; count bound including one recovery-only +1 | M.`staticShapesIdentityForeignKeysAndUniquenessRejectMalformedRows`, M.`deferredPairsAndRecoveryEvidenceRejectAtCommitAndPermitCompoundShapes`, DS.`malformedShapeAndOrphanAreDeniedWithoutChangingAuthoritativeState`: SQLSTATE `23514`/`23505`/`23503`; M.`deliveryBudgetAllowsFiveClaimsThenExactlyOneNonDispatchingRecovery`, M.`reconciliationBudgetAllowsFourClaimsThenExactlyOneNonQueryableRecovery`. |
| No arbitrary state/budget setter; no dispatch/query after ordinary limit | DS.`malformedShapeAndOrphanAreDeniedWithoutChangingAuthoritativeState` catalog assertion; M.`normalRetryAndObservationExhaustionNeverScheduleBeyondDurableLimits`, R.`expiredFourthCheckingGetsOneRecoveryOnlyClaimAndNoObservation`, DC.`fiveDispatchableClaimsThenOneRecoveryOnlyForEachScenario`. |
| Replay/resume fixed 5/4 cycle, count 0, lifetime history retained; closure/success terminal | DS.`readyRetryFailReplaySuccessAndTerminalGuardsPreserveCycleHistory`, R.`operatorResumeStartsNewCaseCycleWithoutErasingLifetimeOrDeliveryCycle`, R.`operatorClosePreservesUnknownAndTerminallyStopsCaseAndDelivery`; immutable history queried. |

## Full G10 allow/deny matrix

`AsyncPermissionIntegrationTest` (`AP`) and `OutboxMigrationSecurityIntegrationTest` (`M`) execute real SQL under PostgreSQL 17.11 credentials. `AP.forbiddenSqlHasExpectedSqlstateAndLeavesAuthoritativeRowsUnchanged` snapshots delivery/history and verifies state unchanged after each denial. `M.exactDirectAclAndFunctionCallerMatrixExecuteRealDeniedSql` attempts denied SELECT/INSERT/UPDATE/DELETE/TRUNCATE/DDL/GRANT and wrong-caller function EXECUTE over the accepted ACL matrix. Ordinary denies use SQLSTATE `42501`; missing replacement-budget signature is `42883`; PostgreSQL ineffective GRANT is checked as warning `01007` or error `42501`. Constraint denials use `23514`, `23505`, `23503` as appropriate.

| Effective identity | Allowed executed SQL / state evidence | Denied executed SQL / authority evidence |
| --- | --- | --- |
| `vra_runtime` | AP.`eachWorkloadCredentialExecutesItsNarrowPositiveOperation`: initial READY publication through `async_publish_reservation`; ProducerOutboxIntegrationTest verifies business/event/delivery atomicity. | AP denies arbitrary SUCCEEDED insert, delivery claim, controlled replay; M denies later functions, async DML, DELETE/TRUNCATE/DDL/GRANT/SET ROLE. |
| `vra_outbox_worker` | AP completes/fails guarded delivery, handoff UNKNOWN; ProjectionInboxIntegrationTest commits narrow consumer/inbox effect. | AP denies Inventory/Reservation/idempotency UPDATE, event/history rewrite, direct count UPDATE, controlled replay, rebuild; M denies reconciler/operator functions and broad DML. |
| `vra_reconciliation_worker` | AP claims case and confirms success; R.`actualCredentialsEnforceReconcilerOperatorWorkerSeparation` checks current user and case functions. | AP denies normal delivery claim, case count UPDATE, reconciliation-history rewrite; M denies ordinary delivery/controls/business writes. |
| `vra_async_operator` | AP controlled replay; R tests controlled resume/close paired history. | AP denies direct limit UPDATE; M denies worker/reconciler calls, DML/DDL/GRANT/escalation. |
| `vra_projection_rebuilder` | AP executes dedicated rebuild; ProjectionRebuildIntegrationTest verifies authorized repair. | AP denies event SELECT; M denies unrelated table/function access, lifecycle DML/DDL/GRANT/escalation. |
| `vra_async_observer` | AP and AsyncVisibilityIntegrationTest execute only granted current/history SELECT, effective identity checked. | AsyncVisibilityIntegrationTest denies UPDATE/DELETE/TRUNCATE/INSERT/CREATE/claim/SET ROLE, SQLSTATE `42501`, unchanged state/history; AP denies event payload SELECT. |
| `vra_migrator` | AP executes actual `SET ROLE vra_owner` and reads `current_user`; M verifies V3 migrations. | M.`exactMembershipOwnershipAndExecutorSteadyStateDenyEscalation` denies CREATE ROLE/schema table directly; confirms non-superuser/NOCREATEROLE. |
| `vra_owner`, `vra_async_executor`, PUBLIC | M verifies exactly `migrator→owner SET=true` and `owner→executor SET=true, INHERIT=false, ADMIN=false`; owner applies constrained migration DDL; executor owns named guarded functions. | M proves executor NOLOGIN, no schema/table ownership or persistent schema CREATE, actual executor DDL denied, PUBLIC probe function EXECUTE denied. All ordinary roles have no direct/transitive SET path to owner/executor and actual SET ROLE fails `42501`. Administrative migrator→executor transitive SET is accepted. |

M also verifies event/history UPDATE and DELETE denied even through owner, sequence access limited, required history append failure rolls back the full paired operation, and narrow positive producer/consumer/control/reconciliation operations commit. Direct budget changes are denied, and no replacement-budget function/signature exists. There was no privilege change in Stage J.

## G12 architecture and artifact evidence

`AsyncArchitectureTest` (9 methods/rules) uses ArchUnit package rules and root annotation checks for async contract/Inventory domain independence; application absence of adapter/bootstrap/web/JDBC/JPA dependencies; reconciliation application separation from delivery adapter/bootstrap and Inventory; adapter inward direction; bootstrap exclusion of API controller/business packages; external `HttpClient` constrained to validation external adapter; package ownership of ports/policies/adapters/modes; and root API scan narrowed with explicit producer import. Its new direct `ReconciliationWorkerMode` dependency rule forbids ordinary delivery/projection application and adapters, producer adapter, Inventory/platform web, and OutboxWorkerMode/OutboxWorkerLoop/ApiMode while allowing accepted shared bootstrap, reconciliation, and external-observation classes. Existing `InventoryArchitectureTest` (5 ArchUnit rules) also passed. `ProducerOutboxIntegrationTest` passed with `@SpringBootTest` discovery and API producer composition; the 9 MVC slice tests passed after `ApiMode` used narrow component scan with `TypeExcludeFilter`. `WorkerProcessCrashIntegrationTest` passed after the final API composition correction. No internal Fastform localhost HTTP boundary was added.

Structural ZIP/manifest inspection after the Stage-J local clean build:

| Artifact | Entry point/expected production classes | Test leakage |
| --- | --- | --- |
| `runtime-0.1.0-SNAPSHOT.jar` (55,126,844 bytes) | Executable Spring Boot `JarLauncher`; `Start-Class: dev.vra.VraApplication`; VraApplication, OutboxWorkerMode, ReconciliationWorkerMode, JdbcAsyncVisibility present; Boot 4.1.1, JDK 21 | WorkerProcessHarness, WorkerProcessCrashIntegrationTest, SimulatorMain/Store/HttpServer, AsyncVisibilityIntegrationTest, AsyncArchitectureTest absent; no `BOOT-INF/classes` test class/path found. |
| `migration-0.1.0-SNAPSHOT.jar` (5,176,360 bytes) | Executable Spring Boot `JarLauncher`; `Start-Class: dev.vra.migration.MigrationMain`; MigrationMain and MigrationRunner present; Boot 4.1.1, JDK 21 | Same test probes absent; no `BOOT-INF/classes` test class/path found. |

This structural check does not certify production deployment. No new Gradle module, dependency, task, workflow, broker, Redis, or service boundary was added.

## Independent final architecture/security/evidence review

- **Verdict:** PASS WITH NON-BLOCKING FINDINGS. **Blocking findings:** 0. **Unresolved critical contradictions:** 0.
- **Final evidence archive:** `poc03-final-bound-evidence.tar.gz`; SHA-256 `a3069b9ca475bf8e52cd3d0f93ec6c6132f2d1f1d8d261fb158f8d39bcf8b3db`. Independent archive verification found safe paths/types, 72/72 internal SHA manifest entries verified, 0 missing, 0 mismatches, and 0 unmanifested evidence files.
- **Source binding:** All seven executable/test Stage-J files and the pre-update `RESULTS.md` (SHA-256 `8b44545f2a34de4a98a3762b5e45b43e9fc6adf03a68ec2310dbfb089c2e63dc`) matched their reviewed expected SHA-256 values before and after the clean build. Branch, HEAD, dirty set, and staged state were identical before and after execution. **Reviewed Stage-J bytes == pre-build bytes == post-build bytes == tested bytes.** The subsequent pre-commit documentation update changed the `RESULTS.md` SHA after that binding; the committed eight-file integrity check is recorded above.
- **Review basis:** exact Stage-I HEAD archive at `0ae2b394c8f336a7aace27d1632d8de056a15525`, exact Stage-J delta, V1/V2/V3, async-role bootstrap, Inventory/Async ArchUnit source, G0–G12 implementation and tests, fresh XML, real-process crash and independent simulator evidence, authoritative PostgreSQL state, migration/SQLSTATE and ACL evidence, visibility, bootJar contents, and final source-byte binding.

The final bound execution ran:

```text
./backend/gradlew -p backend --no-daemon --warning-mode all :runtime:clean :migration:clean :runtime:check :migration:check :runtime:bootJar :migration:bootJar --console=plain
```

It completed `BUILD SUCCESSFUL` in **100.11 s**, with **17 actionable tasks / 17 executed**. Independently parsed fresh XML reported:

| Suite | Tests | Failures | Errors | Skipped |
| --- | ---: | ---: | ---: | ---: |
| `runtime:test` | 56 | 0 | 0 | 0 |
| `runtime:integrationTest` | 113 | 0 | 0 | 0 |
| `migration:integrationTest` | 15 | 0 | 0 | 0 |
| **Total** | **184** | **0** | **0** | **0** |

The fresh runtime bootJar SHA-256 is `35174e46636270ff31f7a92a700a230dfe52876cda2e92e7ae716111f08d13ec` (`Start-Class: dev.vra.VraApplication`). The fresh migration bootJar SHA-256 is `d8dd04de3c8e9e65ae4dd4c9da63934daa693dc9caad590e04fca6e966c3805f` (`Start-Class: dev.vra.migration.MigrationMain`). The migration JAR contains V1, V2, and V3; the runtime JAR has no Flyway library. Test implementations and the independent simulator-process implementation did not leak into either bootJar. `ValidationSimulatorHttpAdapter` is intentional main-source POC validation adapter code, not the independent test simulator implementation.

Independent parsing of the Gradle problems report found **4 WARNING**, **0 ERROR** (`totalProblemCount=4`): compiler deprecation warnings, **NON-BLOCKING**. At the time of final-bound execution, hosted CI was **NOT EXECUTED**; branch-protection policy proof was not performed.

## Gate accounting and limits

The table records bounded POC-03 technical verification after the final bound execution and independent review. The separate Stage-J Git closure passed the post-commit integrity check recorded above.

| Gate | Final state | Basis |
| --- | --- | --- |
| G0 | PASS | `MigrationSecurityIntegrationTest` and `OutboxMigrationSecurityIntegrationTest` execute fresh PostgreSQL migration and actual V2→V3 upgrade; validate historical checksums/data, ownership/grants and migrator/runtime/worker boundaries. `AsyncPermissionIntegrationTest` verifies effective workload identities. |
| G1 | PASS | `ProducerOutboxIntegrationTest.ordinaryAndIdempotentFirstSuccessCommitExactProducerRows` verifies inventory/reservation/applicable idempotency success/event/READY delivery/CREATED history together. `eventInsertFailureRollsBackBothPaths` and `deliveryInsertFailureAfterEventInsertRollsBackBothPaths` independently inject failures in ordinary and idempotent paths and assert no orphan event/delivery or partial business state. |
| G2 | PASS | `ProducerOutboxIntegrationTest` verifies sequential/concurrent same-key and lost-result replay preserve one reservation/event/delivery and immutable event content; conflict, rejection and prevalidation create no new event; runtime producer identity stays separate. `ReservationCreatedEventCodecTest` verifies exact `inventory.reservation.created` type, schema version 1, six payload fields, occurredAt, strict round trip and invalid type/version rejection. `ProjectionInboxIntegrationTest.unsupportedContractFailsBeforeInboxRegistration` verifies visible unsupported-contract failure; `independentEventsCanProjectInReverseCreationOrder` denies a global-order assumption. Retained POC-02 idempotency, stock and concurrency suites passed in the fresh full build. |
| G3 | PASS | `DeliveryClaimIntegrationTest` executes PostgreSQL contention, committed/rolled-back claims, lease expiry, current/stale token fencing and fixed limits; `RetryReplayIntegrationTest.reclaimedExternalClaimStillCannotBypassUnknownHandoff` covers Scenario-B reclaim; `AsyncPropertiesTest` rejects invalid timing/concurrency configuration. |
| G4 | PASS | Fresh 12-method real child-process crash/drain/reclaim/relinquishment suite. |
| G5 | PASS | Fresh projection/inbox dedupe and rollback tests (`ProjectionInboxIntegrationTest`). |
| G6 | PASS | Fresh retry/backoff policy (`DeliveryRetryPolicyTest`), durable cycle/history, exhaustion and controlled same-event replay (`RetryReplayIntegrationTest`), with UNKNOWN and terminal replay denial. |
| G7 | PASS | Full requirement-to-test matrix above; fresh Stage-F/G/I execution and independent review. |
| G8 | PASS | Fresh rebuild/consumer serialization tests (`ProjectionRebuildIntegrationTest`). |
| G9 | PASS | Full state-machine/constraint matrix above; fresh V3 PostgreSQL tests and independent review. |
| G10 | PASS | Full actual-SQL allow/deny matrix above; fresh role and state evidence and independent review. |
| G11 | PASS | Fresh actual observer and authoritative PostgreSQL visibility tests and independent review. |
| G12 local execution | PASS | Final bound clean build, 184/184 fresh XML tests passing with zero skipped/failures/errors, bootJar inspection, and exact source binding. |
| G12 independent architecture/security review | PASS | Independent final verdict: PASS WITH NON-BLOCKING FINDINGS; zero blockers and unresolved critical contradictions. |
| G12 full bounded verification | PASS | G0–G11, G12 local execution, and independent final review passed. |
| Hosted CI | PASS | PR head run `36586572864` and post-merge `main` push run `36588526956` completed successfully. |

Decision 15 governance: ADR-003 was independently reviewed and **ACCEPTED before implementation**. Stage J follows its already-approved one-artifact/process-mode composition and narrow observer visibility; it introduces no new material architecture decision. **ADR decision: NO NEW / REVISED ADR REQUIRED.** No Stage-J business/state-machine semantic, privilege, migration, simulator effect, or external uncertainty behavior changed.

Non-blocking finding and nonclaims: the four compiler deprecation warnings do not block bounded verification. PR-head and post-merge `main` hosted CI both **PASS**; branch-protection policy proof was not performed. Remote synchronization is **VERIFIED / CLOSED** through PR #1's merge. POC-04 owns deferred runtime `UPDATE` hardening for the POC-02 idempotency table. This evidence does not certify production deployment, production credential management, HA, capacity/SLO, a provider, multi-region behavior, production retention, or production secrets/KMS selection; it does not add a public visibility endpoint. **Technical independent POC-03 verification: PASS. Stage-J verification commit integrity: PASS. POC-03 Git closure: CLOSED.**
