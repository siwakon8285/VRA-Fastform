# POC-04 Phase-1 Round1 — health timing root-cause diagnosis

PHASE 1 CLOSED: NO
READY FOR INDEPENDENT PHASE-1 RE-REVIEW: NO
Phase2: NOT AUTHORIZED
POC04/S0–S15: NOT VERIFIED

Preserved previous report:786ec48925a56e67e9f9491c3d2922b876d83742758db4a2071de5c1eed197f4. No historical evidence or result changed. Diagnosis run 43eb52ce-5ef3-4393-9c95-36d858025ce2, unmodified repository source binding bacf3084b406f644924aefadfb1fcafeec950f561d6e6a47357f8095712a6660. Original health test source SHA-256 b73f709a8d913f94da57232253afafc8792b94d5cfff334633a0489e250747ea; production DatabaseConfiguration remains fcbafb216ebfd5218d079fc84a7fb9a0c5aeeb5a764aa4db967b797583983153.

Command (cwd backend):

    ./gradlew --no-daemon --init-script ../validation/poc-04/test-support/runner.init.gradle :runtime:integrationTest --tests dev.vra.platform.health.HealthProbePostgresIntegrationTest.databaseOutageMakesReadinessDownButKeepsLivenessUp --rerun-tasks

Exit1;1test/0skipped/1failure/0errors. Original HttpTimeoutException remains a FAIL, never readiness DOWN. External TEST-only Spring TestExecutionListener is source/JAR/hash bound in diagnostic-support-binding.json and source preserved in HealthDiagnosticListener.java.txt. No dependency upgrade, configuration setter, bean replacement or assertion change occurred in the diagnostic run. Actual datasource inspection precedes the original method. The initial unmanaged recorder launch ended before readiness; no Gradle test ran on that attempt. It was restarted as a managed session; recorder-launch-attempt.json preserves that tooling result.

## Actual runtime measurements

Actual implementation:com.zaxxer.hikari.HikariDataSource. connectionTimeout30000ms, validationTimeout5000ms, maximumPoolSize10, minimumIdle10. These are actual bean getters, not assumed documentation defaults. HTTPconnect2s/request5s/overallreadiness20s; JDBCconnect/socket1s unchanged.

Diagnostic liveness-before HTTP200/UP:57.534542ms. Diagnostic readiness-before HTTP200/UP:5.764333ms. At15:24:20.352078Z, the ORIGINAL readiness request servlet thread was TIMED_WAITING in ConcurrentBag.borrow→HikariPool.getConnection→DataSourceUtils→JdbcTemplate.execute→DataSourceHealthIndicator.getProduct. Pooltotal0/idle0/active0/awaiters1. This capture occurs before an extra separately timed diagnostic readiness request was issued. It directly establishes CASE A: datasource acquisition before the readiness DB query; no query/socket-wait or globally blocked server is substituted as cause.

Independent liveness during the wait returned HTTP200/UP in2.501ms. The HTTP server remained responsive while readiness acquisition was stalled. The original test exception was recorded at15:24:25.351720Z. The separately timed diagnostic readiness request began15:24:20.353336Z and ended15:24:25.355080Z after5001.809167ms with HttpTimeoutException. It is explicitly a second diagnostic request, not mislabeled as the original client request; original exact client start/end was not instrumented. Original pool-wait/end-exception timestamps and the exception itself remain raw evidence. The corrected focused test will capture exact original request times.

Root cause established:30000ms pool acquisition >5000ms client request. JDBCconnect/socket1000ms limits a different boundary. Hikari pool acquisition exhaustion kept the readiness servlet request waiting; concurrent liveness responsiveness excludes CASE C for this observation. Do not infer a production-wide pool requirement from this disposable test.

## Authorized follow-on correction

After raw diagnostic completion, only HealthProbePostgresIntegrationTest was changed for a separate corrected source-bound run: TEST-only actual Hikari bean acquisition2000ms, validation1000ms; readback asserts configured values; actual runtime role/currentDB/version check; precise safe per-request timestamps/outcome; concurrent degraded liveness probe; original settings restored in finally. Rationale: measured healthy readiness5.764ms and concurrent liveness2.501ms are well below existing JDBC/validation1000ms;2000ms leaves a bounded acquisition budget below the unchanged5000ms HTTP request and20000ms readiness deadline. No production DataSource/config, poolsize, role/grant, V1–V4, dependency or HTTPdeadline changes. Any focused correction failure must STOP; no timeout is treated as DOWN.

## Isolation and cleanup

Only unix:///Users/siwakornbundi/.colima/vra-poc04-test/docker.sock; host127.0.0.1; socket/var/run/docker.sock; daemon14c8ff26-86d1-4c0b-88a6-560be91a65ea/colima-vra-poc04-test/server29.5.2linux/arm64; current CLIclient29.8.1 recorded accurately. Ryuk enabled; immutable reviewed PG17.11 image/subplatform/config pins retained. No Docker Desktop/vra-poc00/prune/profile/Git access mutation. All3 creator-ledger database resources absent and one UUIDv5/physical system binding passed finish; final-daemon-state.json0containers/0volumes/only3builtins. Exact IDs/labels are in final-run-ledger.json and safe Docker evidence. Cleanup only uses ledger-authorized exact kind/ID; observed helpers do not confer coordinator delete authority.

Raw command/log/JUnit XML and safe diagnostics:focused-health-diagnosis-command.json, focused-health-diagnosis.log, focused-health-diagnosis-results/, health-timing-diagnosis.json. This diagnostic FAIL is permanently preserved. Full corrected build/collector have not run in this diagnosis phase.
