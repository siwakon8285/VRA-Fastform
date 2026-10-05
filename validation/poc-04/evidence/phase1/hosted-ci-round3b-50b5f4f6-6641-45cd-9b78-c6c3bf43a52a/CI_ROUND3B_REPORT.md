HOSTED CI REMEDIATION CANDIDATE: FAIL
READY FOR WORKFLOW-INTEGRATION STEP: NO
READY FOR NARROW INDEPENDENT CI-DELTA REVIEW: NO

Root-cause classification: G — NOT ESTABLISHED.

The exact focused method passed once (1 test / 0 skipped / 0 failures / 0 errors).
The historical 08P01 failure did not recur. No connection remediation was applied.
No complete Outbox class, runtime suite or full 193-test gate was run after this
diagnosis. Success of this focused execution or its A/B probes is not a fix or
a Phase-1 acceptance result.

PHASE 1 CLOSED: NO
READY TO MERGE PR #3: NO
Phase 2: NOT AUTHORIZED / NOT STARTED
POC-04 / S0-S15: NOT VERIFIED

Starting branch: poc/04-security-auth
Starting/current HEAD: ae9bd1f53a7e3e8612f2f4cd9f778e27d7cdb0db
Tree: 988058edd53504936345bc51ebdeee2d7d55b0c7
Evidence package UUID: 50b5f4f6-6641-45cd-9b78-c6c3bf43a52a
Authorized Docker execution run_id: 25d5826f-3873-4999-84e6-362b37e8582d
Source-content SHA-256: 9157dc5aa931045e2f0482c2c2d4f51584510a8fb208194ad7f86143f6fa12e2
Source HEAD/tree/content/file hashes agree before/after; current bytes still match.
Nothing staged. No Git mutation or workflow edit was performed.

Historical failure preservation

Round3A CI_ROUND3A_REPORT.md remains:
24b6cc91a317be06edfcef231573b81e6feb37fc79cbd63eb22ccacba31aded7
Round3A DELTA_BINDING.json remains:
c82ef315f54bddc9356d544ee335371a8756fdb619cae1383c23ab089896d35f
All 279 recorded Round3A files retain their starting hashes.
Earlier reports were not edited. STARTING_BINDING.json binds the prior state.
HISTORICAL_EXCEPTION.json preserves the safe historical stack/projection.
Historical full build remains FAIL: 24 / 0 skipped / 1 failure / 0 errors;
runtime suites were not reached. Its cleanup of 63 resources is historical evidence.

Exception mechanism and limits

The historical top exception is FlywaySqlUnableToConnectToDbException, caused by
org.postgresql.util.PSQLException, SQLSTATE 08P01, message:
"An error occurred while setting up the SSL connection."
It occurred in Fixture(true) during MigrationRunner.migrate -> Flyway ->
OwnerRoleDataSource.open -> DriverManager, before the test body.
The actual resolved pgJDBC 42.7.13 bytecode maps enableSSL line717 to a response
byte other than E/N/S after the initial SSLRequest. This is before authentication
and TLS conversion. The PSQLException construction has no deeper Throwable cause;
no suppressed exception is printed in the historical XML. The original Throwable
object and unexpected response byte are unavailable. This establishes a client
rejection branch, not an underlying endpoint/server/SSL compatibility cause.
PGJDBC_ENABLE_SSL_BYTECODE.txt and PGJDBC_IDENTITY.json bind this analysis.
The first evidence-extraction substring mismatch is retained separately; it did
not execute or retry any database test.

Opt-in diagnostic delta

The four exact source changes below add observation only. No production source,
schema, grants, SSL mode, original JDBC timeout or connection retry changed.
TransportDiagnosticDriver is installed only by --gate transport. Its recorded
DriverManager registration replaces the exact original pgJDBC registration with
a delegate that forwards the same URL/Properties and returns the same Connection.
Other drivers remain registered. Native direct Testcontainers readiness is outside
this observer. Current original URL/properties are not reconstructed or changed.
The child diagnostic A reconstructs this fixture's ordinary String properties;
it is not a general inherited/non-String Properties implementation.

The init script compiles the one additional named TEST class, supplies a resolved
TEST-only classpath to the separate child probes, and leaves production classpaths
unchanged. Support artifact inventory is exactly four named classes, two service
descriptors and MANIFEST.MF; SHA-256:
852e65b312d71764fdb4d1047537611056ed2ab10211eb538d99142f16e4b9af
The bootstrap's transport gate invokes only the requested method and preserves
XML before cleanup. It reports diagnosis_only=true and scanner NOT EXECUTED;
its internal capture PASS is not a hosted-CI candidate PASS.
DIAGNOSTIC_DELTA.diff and DIAGNOSTIC_SOURCE_BINDING.json contain the exact delta.

Changed source paths / SHA-256

- validation/poc-04/scripts/phase1-ci.py: fa8e51b0e30e00406d801c5e939e59654ed5852c8f4b88009d73caa0705e1151
- validation/poc-04/test-support/runner.init.gradle: 5b438ce0e7bec8b2dbbc321df680c6f57359c019d9eb241f65029efea0a3d5d8
- validation/poc-04/test-support/src/dev/vra/poc04/external/RunOwnedPostgreSQLContainer.java: ebbf45b27a0376ac96e7fd3bc59bb3a0bceefe1d08e7444d15967445717d9008
- validation/poc-04/test-support/src/dev/vra/poc04/external/TransportDiagnosticDriver.java: f21690bc370e751bddb5e4e6e70eb12a98aaf450a0de66796bd5a35247631c54

Execution commands and results

Reusable entry point: python3 -B validation/poc-04/scripts/phase1-ci.py
--disposable-test --gate transport --evidence-directory <this package>/focused-execution
Exit status 0, diagnostic capture and exact cleanup completed.
No automatic original test/SQL retry occurred.

Command: /Users/siwakornbundi/Project/VRA-Fastform/backend/gradlew -p /Users/siwakornbundi/Project/VRA-Fastform/backend --no-daemon --warning-mode all --init-script /Users/siwakornbundi/Project/VRA-Fastform/validation/poc-04/test-support/runner.init.gradle :runtime:compileTestJava :migration:compileTestJava poc04TestClasspathProof --rerun-tasks
Exit status: 0; output: focused-execution/focused-compile.txt

Command: /Users/siwakornbundi/Project/VRA-Fastform/backend/gradlew -p /Users/siwakornbundi/Project/VRA-Fastform/backend --no-daemon --warning-mode all --init-script /Users/siwakornbundi/Project/VRA-Fastform/validation/poc-04/test-support/runner.init.gradle :migration:integrationTest --tests dev.vra.migration.OutboxMigrationSecurityIntegrationTest.intendedDeliveryProducerConsumerAndControlCapabilitiesCommit --rerun-tasks
Exit status: 0; output: focused-execution/focused-transport.txt

CompilePoc04TestSupport/runtime:compileTestJava/migration:compileTestJava passed.
focused-transport.xml contains only intendedDeliveryProducerConsumerAndControlCapabilitiesCommit().
Counts: 1 / 0 skipped / 0 failures / 0 errors.
No other JUnit method ran in this diagnostic execution.

Live endpoint and database identity

Isolated endpoint: unix:///Users/siwakornbundi/.colima/vra-poc04-test/docker.sock
Daemon: 14c8ff26-86d1-4c0b-88a6-560be91a65ea (colima-vra-poc04-test)
Server: Docker29.5.2 / API1.54 / linux arm64
TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock
TESTCONTAINERS_HOST_OVERRIDE=127.0.0.1
Effective IPAM: 10.241.0.0/16, /24 children. No configuration changed this round.
Fresh run labels bind the exact HEAD/content/tool digest/current-run UUID.
Logical name: postgres-authority/0
database_instance_id: 8bcca2b4-628b-583f-8f24-2a2d50def89f
PostgreSQL system_identifier: 7691967708548755494
Container: cb3df0b288e3ee8e57ce9fb2c3a2f91319a011594037aea44f40af03bb42a2f4
Created: 2026-10-02T07:23:11.962845269Z
PGDATA volume: vra-poc04-25d5826f-3873-4999-84e6-362b37e8582d-postgres-authority-0-pgdata
Network: 25dd82b6bf0bdc434b47a949d76634005d4370512824d79bd870da4ba045839a
Host/mapped port: 127.0.0.1:33077
Safe original URL: jdbc:postgresql://127.0.0.1:33077/vra_poc01?loggerLevel=OFF

All 42 observed DriverManager attempts connected. Each requested URL matched the
fresh live inspect mapping for the same running container. All 47 endpoint-bearing
observations agree on container/created/instance/volume/network/current port. Docker
NetworkSettings records both 0.0.0.0 and :: bindings to 33077;
HostConfig.PortBindings contains the original empty ephemeral allocation request.
No fixed host port was introduced. Three physical-identity ledger events agree.
Lifecycle/admin evidence and daemon safe-inspect snapshots complement DriverManager
observation; not every direct Testcontainers driver invocation is intercepted.

Separate probes (not original-operation retries)

- A_exact_original: CONNECTED; process latency 237ms; backend_pid=158, database=vra_poc01, user=vra_migrator, version=170011, ssl=off, server=10.241.1.2/32:5432, system_identifier=7691967708548755494
- B_explicit_non_SSL: CONNECTED; process latency 236ms; backend_pid=159, database=vra_poc01, user=vra_migrator, version=170011, ssl=off, server=10.241.1.2/32:5432, system_identifier=7691967708548755494
- known_good_admin_identity: CONNECTED; process latency 234ms; backend_pid=160, database=vra_poc01, user=postgres, version=170011, ssl=off, server=10.241.1.2/32:5432, system_identifier=7691967708548755494


A used the exact original migration URL and String connection properties.
B differed only by explicit sslmode=disable on the same container/mapped port.
The independent admin identity probe agreed with the previously bound physical
identity. Probe timings include child JVM startup; original observed durations
include inspection overhead. Only child diagnostic processes had a 12-second
external deadline; original JDBC/HTTP/pool timeouts were unchanged.
All probes ran while the same exact container was live and before teardown.

Server-side evidence

Logs were captured at 2026-10-02T07:23:16.386260Z before A/B/admin traffic, and at
2026-10-02T07:23:17.504679Z afterward. They are identical and contain no matching protocol,
SSL/TLS, connection-reset or backend-crash error. The temporary initialization
server shutdown and initial database-not-yet-created message precede the final
postmaster startup/readiness; no post-readiness restart is shown.
No 08P01 occurred in this run, so there is no new failed connection to correlate
with a server rejection. The historical run's packet/log correlation remains
unavailable. Log absence alone does not establish that the historical packet never
reached PostgreSQL. Raw sanitized snapshots are in transport-observations.json.

Property/environment comparison

Original sslmode/ssl/sslNegotiation/connectTimeout/socketTimeout were NOT EXPLICIT
in observed Properties and original URL. java.net.preferIPv4Stack=true was captured.
PGSSLMODE/JAVA_TOOL_OPTIONS/JDK_JAVA_OPTIONS were NOT SET. No other relevant
PostgreSQL/SSL system property was captured. Previous accepted final193 Outbox
XML has13/0/0/0; same Outbox/OwnerRoleDataSource bytes were bound before diagnostic
changes. Historical preflight/init captured the same reviewed daemon/host/JDK and
no added SSL property. It omitted the three environment variables above, so an
absence-of-delta claim cannot be made for them. ENVIRONMENT_COMPARISON.json records
the known values and missing historical observations.

Cleanup and source safety

All three creator-ledger resources were teardown-authorized by current run and
exact ID, then proven absent. Native Ryuk container
4bd4be2d6b088431a3ea03f78412e1243ac5bd0850cb783ece3e8e3d7af90e7a
also has destroy evidence. Final daemon state: zero containers, zero volumes,
the same bridge/host/none IDs. No prune or shared/persistent resource access.
No extra teardown-negative execution occurred: this gate ran only the requested
test. Previous negatives remain historical and are not claimed as newly executed.
Probe credentials used transient stdin, never process arguments or evidence files.
Focused execution artifacts contain no fixture-password literal. Exact unchanged
baseline source snapshots retain their existing public TEST fixture literal; it
is not reproduced in this report. A source-search output did include that baseline
literal; subsequent output/report avoids it. This is disclosed rather than claiming
that no credential-shaped source text was ever displayed.
Pinned scanner was NOT EXECUTED this round; previous Round3A scanner PASS is retained
as historical only. Manual safe-output inspection is not a new scanner PASS.

All controlling authority bytes match. V1/V2/V3 match Phase0 recorded hashes and
HEAD; V4 matches the reviewed committed candidate. See FINAL_CHECKS.json for exact
hashes. No V5/V6, no Phase2 source delta, no workflow modification, nothing staged.
git diff --check, Python AST, Java21 compilation, final-newline/trailing-whitespace
checks pass. Source binding stayed identical throughout execution.

Blocking conclusion

No evidence establishes stale endpoint, unexpected restart, pgJDBC incompatibility,
property contamination or a PostgreSQL process failure as the historical cause.
The focused success only establishes that the failure did not recur under this
observed one-fixture execution. Unknown original response-byte origin and missing
historical live log/endpoint correlation prevent an evidence-backed connection fix.
G — NOT ESTABLISHED is final for this round. Stopped without remediation, whole-class
retry, full193 build, collector proof, workflow edit, staging, commit, push or merge.
