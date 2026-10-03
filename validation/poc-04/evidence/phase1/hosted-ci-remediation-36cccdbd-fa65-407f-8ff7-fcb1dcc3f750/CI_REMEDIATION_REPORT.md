# POC-04 Phase-1 hosted CI remediation candidate — focused compile FAIL

HOSTED CI REMEDIATION CANDIDATE: FAIL
READY FOR NARROW INDEPENDENT CI-DELTA REVIEW: NO
PHASE 1 CLOSED: NO
READY TO MERGE PR #3: NO
Phase 2: NOT AUTHORIZED / NOT STARTED
POC-04 / S0-S15: NOT VERIFIED

## Baseline and scope

Branch: poc/04-security-auth. HEAD: ae9bd1f53a7e3e8612f2f4cd9f778e27d7cdb0db. Tree: 988058edd53504936345bc51ebdeee2d7d55b0c7. The user-confirmed ordinary CI reproduction was not repeated. Existing committed Phase-1 evidence and historical failures were not changed. Nothing was staged, committed, pushed or merged.

Candidate direction A compiles the three checked-in external TEST support classes through resolved migration test dependencies and the Java 21 toolchain. Two checked-in SPI descriptors are packaged with them. The task output is added only to compileTestJava and Test classpaths. A checked-in administrative TEST entry point derives/binds the selected Docker endpoint, daemon identity and source content, creates a UUIDv4 run and restricted run state, and invokes the existing creator-ledger harness. This is a failed candidate, not accepted implementation or Phase-1 PASS.

## First focused gate and stop

Run ID: c39bef14-29d9-42d1-9fd9-4845f63b0cb2. PostgreSQL tests executed: 0.

The focused command was Gradle --no-daemon --warning-mode all --init-script validation/poc-04/test-support/runner.init.gradle :runtime:compileTestJava :migration:compileTestJava poc04TestClasspathProof --rerun-tasks, with backend as the Gradle project. Exact absolute arguments, exit statuses and elapsed times are in compile/commands.json. Exit status: 1. Full output: compile/focused-compile.txt, SHA-256 e6c1b67ec5b3eb499403428364f229533a18252347d17d8cd9bb60ddd34aec87.

Gradle task validation stopped at :compilePoc04TestSupport because sourceCompatibility and targetCompatibility were not configured. The candidate sets a Java 21 compiler and options.release=21, but this root project does not apply the Java plugin defaults. Neither requested test-compilation gate reached PASS. No source correction was made after failure; the user explicitly required STOP if compilation still fails.

| Obligation | Result |
|---|---|
| Focused runtime/migration compile | FAIL before test compilation |
| Support JAR/classpath content execution proof | NOT EXECUTED |
| Focused PostgreSQL resource-boundary proof | NOT EXECUTED |
| Full 193-test build | NOT EXECUTED |
| BootJar leakage check | NOT EXECUTED |
| Secret scanner gate | NOT EXECUTED |
| Exact cleanup | PASS; zero run-owned Docker resources created |
| Start/final source binding | PASS |
| Workflow change | NOT MADE; exact workflow diff is empty |

## Disposal and binding

Only unix:///Users/siwakornbundi/.colima/vra-poc04-test/docker.sock was used. Actual daemon ID: 14c8ff26-86d1-4c0b-88a6-560be91a65ea; name: colima-vra-poc04-test; architecture: aarch64; Docker Server: 29.5.2; API: 1.54. Java runtime was Temurin 21.0.12.1+1, captured in compile/java-version.txt. Initial and final containers: 0; volumes: 0; networks: the same three built-in exact IDs. Ryuk was not disabled; it was not started because no test execution began. Docker Desktop and persistent vra-poc00 were not accessed.

The authorized ledger had 0 resources and 0 physical database identities. run-harness.py finish returned 0 and PASS. No Docker deletion command was needed or invoked. Restricted temporary state was removed only after successful cleanup. Start/final canonical source digest: 88260e0d579b84ae9248b613717ac6885307ecb4cb0355ab66cce5fcb114a8c3. Actual checkout HEAD/tree are recorded independently. No hosted run or PR checkout was claimed.

## Source delta

| Path | SHA-256 |
|---|---|
| validation/poc-04/scripts/phase1-ci.py | 8ec7a6919c0322800a6d591edcb7dbb180bb093e49833ef1f295bcb0f318cf62 |
| validation/poc-04/test-support/runner.init.gradle | 59568bd1e4d1cf084f6ffe428471c43e92ec6d98172cab6d838043255c2f8d89 |

Exact source/bootstrap/init diff: source-delta.patch. Exact workflow diff: workflow-diff.patch (empty). Complete current changed-path hash inventory including all newly preserved evidence is in DELTA_BINDING.json; that file excludes its own hash. The original workflow and the three support Java files/SPI resources remain unchanged. V1-V4 and role/grant bytes remain unchanged. No V5/V6 or Phase-2 work was added.

The immediate blocker is the root TEST JavaCompile task's two required compatibility properties. Boundary/full-build/workflow gates remain pending. This report preserves the failed candidate and makes no claim that the missing-package CI defect is fixed.
