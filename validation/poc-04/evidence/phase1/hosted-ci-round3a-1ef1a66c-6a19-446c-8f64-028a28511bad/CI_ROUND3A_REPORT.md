# POC-04 Phase-1 hosted CI remediation Round 3A

HOSTED CI REMEDIATION CANDIDATE: FAIL
READY FOR WORKFLOW-INTEGRATION STEP: NO
READY FOR NARROW INDEPENDENT CI-DELTA REVIEW: NO
PHASE 1 CLOSED: NO
READY TO MERGE PR #3: NO
Phase 2: NOT AUTHORIZED / NOT STARTED
POC-04 / S0-S15: NOT VERIFIED

## Baseline / immutable history

Branch `poc/04-security-auth`; HEAD `ae9bd1f53a7e3e8612f2f4cd9f778e27d7cdb0db`; tree `988058edd53504936345bc51ebdeee2d7d55b0c7`. Nothing staged; no Git mutation. Workflow unchanged. Only phase1-ci.py changed this round. Runner, classifier tests, production code, V1-V4, grants and dependencies were unchanged.
All 67 files in the earlier hosted-CI remediation/Round2/Round3 directories were independently rehashed unchanged. Round3 report SHA `44002f5256f897ecd2e9a4bafad37454698bc1105f4ad2de54236e32abe38676` and binding SHA `d1aa6093a31dd34a40ac6646fd47988ec725dc9830be9c3a33d2a27be67f723a` remain intact. Historical HTTP500 means scanner NOT EXECUTED / result NOT ESTABLISHED; that record remains FAIL.

## Exact acquisition contract / correction

Gitleaks version8.30.1; local asset `gitleaks_8.30.1_darwin_arm64.tar.gz`, platform `darwin/arm64`. Exact URL: `https://github.com/gitleaks/gitleaks/releases/download/v8.30.1/gitleaks_8.30.1_darwin_arm64.tar.gz`. Expected release archive SHA `b40ab0ae55c505963e365f271a8d3846efbc170aa17f2607f13df610a9aeb6a5` from frozen tool-manifest.json SHA `5814b0ee6eb022acfb458b21b8a009592ea627c3d371cfc7b0963595c05892d8`. The frozen checksum identifies the tar.gz archive, not its extracted executable. No pin bytes changed.
Before: timeout60 seconds, one attempt, no retry, urllib default redirects, archive in memory, extracted executable directly written into restricted run-support directory; no explicit version check and no PATH fallback. ACQUISITION_CONTRACT_BEFORE.json records every inspected property.
After: maximum3 attempts, same artifact identity, bounded1/2-second backoff. Only HTTP500/502/503/504, TimeoutError and ConnectionResetError (including URLError causes) retry. HTTPError is classified before URLError. Integrity failures bypass retry: checksum, clearly invalid content type, archive structure/duplicate/unsafe members, wrong version, unknown platform and malformed pin. Each attempt is audited before transport with UTC timestamp, fixed URL identity, result class/HTTP status and byte count. No response body or signed redirect URL/query is emitted; HTTPS redirect target is only hashed.
Every attempt gets a fresh0600 temporary archive in the restricted run directory. Download/fsync precedes SHA check and archive validation. Exactly one regular gitleaks member is staged, version checked, then atomically published; partial/staged files and failed publication are invalidated. No prior binary, PATH, cache, latest, alternate version, mirror, scanner skip or network-as-clean fallback is trusted.
Actual archive `b40ab0ae55c505963e365f271a8d3846efbc170aa17f2607f13df610a9aeb6a5`; extracted binary observed SHA `ba52fb1bfabbcde42f032afad3d6e0b19dff8ed105229a16e7caa338bbc0e84f` authenticated as the exact member of that verified archive; executable version `8.30.1`. Binary/archive digests are deliberately distinct. Acquisition audit's NOT EXECUTED records its acquisition stage; result.json separately records actual scanner execution.

## Focused proofs / corrected final bytes

Final classifier:12 tests/0 skipped/0 failures/0 errors. Final synthetic acquisition:12/0/0/0. Acquisition proofs exercise retry/exhaustion, HTTP403 denial, timeout/reset, partial invalidation, checksum/content/archive/duplicate/version failure and pre-existing binary rejection. Fabricated markers grant no authority; exact sleep timing is not asserted. Syntax/AST/compile and separate source whitespace/final-newline checks passed.
Pre-implementation missing-helper failures are preserved. Initial fabricated HTTPError lacked a response stream and close raised KeyError; the TEST evidence fixture was corrected, retaining original fixture/output. Implementation did not weaken retry/integrity semantics to accept it. An accidentally removed pre-existing io import was restored before full build; initial scanner/compile/boundary evidence is retained as intermediate bytes. All final gates below were rerun after restoration.
Additional reporting/read-only projection mistakes (ledger field, lowercase absence message, report text/path variable) are recorded in diagnostic JSON; they changed neither source/historical evidence nor resource state.
Final source-content SHA `ba82fbbd7d1d4da69f5ed6e70b861a8d35a964971a6979900f7abbcd3a4813bb` is identical across final scanner, compile, boundary and failed full run. Start/final source binding agrees and every EVIDENCE_BINDING artifact hash recomputed matches. Commands/exit statuses/runUUIDs/daemon/source identities are in GATE_RECONCILIATION.json and each gate's commands.json/preflight.json/result.json.

## Final gate results

- `final-scanner`: PASS; run `2df44faa-b70d-4941-a347-5fc0a2ee0f24`; cleanup/source binding PASS/PASS.
  Scanner actually EXECUTED, Gitleaks8.30.1, raw exit1, gate exit0. matches_total=21; classified_digest_metadata=19; classified_public_verification_identifiers=2; unresolved_secret_findings=0. Both exact public observations remain visible at frozen Python metadata lines228/259 with immutable upstream revision688a0b86bb44289df16a363e9f41d90514c1a5f9 linkage.
- `final-compile`: PASS; run `d50c695d-4f82-4fa4-b027-833e0ac93866`; cleanup/source binding PASS/PASS.
  Scanner actually EXECUTED, Gitleaks8.30.1, raw exit1, gate exit0. matches_total=21; classified_digest_metadata=19; classified_public_verification_identifiers=2; unresolved_secret_findings=0. Both exact public observations remain visible at frozen Python metadata lines228/259 with immutable upstream revision688a0b86bb44289df16a363e9f41d90514c1a5f9 linkage.
- `final-boundary`: PASS; run `fa3afe7c-cd2a-4807-b636-5246871f67ca`; cleanup/source binding PASS/PASS.
  Scanner actually EXECUTED, Gitleaks8.30.1, raw exit1, gate exit0. matches_total=21; classified_digest_metadata=19; classified_public_verification_identifiers=2; unresolved_secret_findings=0. Both exact public observations remain visible at frozen Python metadata lines228/259 with immutable upstream revision688a0b86bb44289df16a363e9f41d90514c1a5f9 linkage.
- `full`: FAIL; run `86ecb257-55ee-47bf-8fb2-6e53d9bca978`; cleanup/source binding PASS/PASS.

Compile: :compilePoc04TestSupport, :runtime:compileTestJava, :migration:compileTestJava PASS. Explicit Java toolchain/source/target/release21. Required support JAR SHA `4440ac9186685763f60f7f704b281cdc8097150c29ab9fb10b1aa969e93d9858`; exactly three classes, two META-INF/services entries and manifest. TEST compile/test classpath includes support once; production classpaths exclude it.
Boundary: fresh foundation integration1/0/0/0; actual RunOwnedPostgreSQLContainer, creator ledger and TEST/source/content/tool labels proven. UUIDv4 run, exact container/volume/network resources; PG17.11 UUIDv5 instance `fea87f5e-58c1-593e-9103-5b868186bbc4` maps to physical `7691794269973807142`. PGDATA authoritative Type=volume, run ownership/RW, image index/manifest/config linkage, running Ryuk isolated socket proven. Sentinel survives current-run cleanup; wrong-ID/wrong-label teardown denied before deletion; denied resources unchanged; separate exact-ID admin cleanup verified. All6 registered resources plus Ryuk absent after cleanup.

## Full regression failure / STOP

Full bootstrap invoked `:runtime:clean :migration:clean check build poc04TestClasspathProof --rerun-tasks` using the SAME checked-in wrapper/init-script path. Gradle exit1, BUILD FAILED in2m11s, 12tasks executed. No subsequent gate or build retry followed.
First independent failure: `OutboxMigrationSecurityIntegrationTest.intendedDeliveryProducerConsumerAndControlCapabilitiesCommit()`. FlywaySqlUnableToConnectToDbException at fixture fresh526/constructor571/test239; nested PSQLException: `An error occurred while setting up the SSL connection.` SQLSTATE `08P01`, vendor error0. Raw stack identifies ConnectionFactoryImpl.enableSSL717; no deeper cause recorded. Root cause beyond this SSL setup failure is NOT ESTABLISHED. It was not dismissed as a cascade or classified as a product defect; no remediation attempted.
Exact failed XML SHA `a1ee8850985eadb2fe7308d568d4532647a373cde4244f3c8fb8f1f1a616a146`; raw XML copied into full-failure-preserved/ so a future retry cannot erase it. FIRST_FAILURE.json and SUITE_COUNTS.json retain the failure and raw reconciliation.

| Suite | Tests | Skipped | Failures | Errors | Status |
|---|---:|---:|---:|---:|---|
| migration:test | 4 | 0 | 0 | 0 | EXECUTED |
| migration:integrationTest | 20 | 0 | 1 | 0 | EXECUTED |
| runtime:test | 0 | 0 | 0 | 0 | NOT EXECUTED |
| runtime:integrationTest | 0 | 0 | 0 | 0 | NOT EXECUTED |
| Executed total |24|0|1|0|INCOMPLETE|

Expected full total remains193. Focused Python/foundation executions are separate; they are not mixed into the24 executed full-build tests. Runtime suites, deployable bootJar existence/leakage proof, full-artifact scanner and post-build teardown-negative proof were NOT EXECUTED after Gradle failure. Earlier focused scanner0unresolved applies only to its captured scope; no scan result is established for new failed full-build artifacts.

## Cleanup / binding / scope

Full failure still completed exact authorized cleanup and source binding. All21containers+21volumes+21networks=63 registered exact resources absent;21UUIDv5 instances map one-to-one to physical PostgreSQL identifiers. INDEPENDENT_CLEANUP_INSPECTION.json independently checks all63 exact IDs against the actual isolated daemon14c8ff26-86d1-4c0b-88a6-560be91a65ea and endpoint unix:///Users/siwakornbundi/.colima/vra-poc04-test/docker.sock. Final state0containers/0volumes/original bridge-host-none networks. No blanket prune, Docker Desktop or persistent vra-poc00 access. Restricted per-run support/archive/binary state removed.
git diff --check PASS/exit0; JSON parse PASS; nothing staged. V1-V3 remain byte-identical to HEAD, hashes in FINAL_INTEGRITY_CHECKS.json. V4 unchanged, SHA 7e2a1ea77ba92a451763c7e06ab7dc4cee6e06f761bbbb4e6cb1c786c8a21ffc. V5/V6 absent; Phase2 not started. No workflow change or Git mutation.
Complete current changed/untracked path hashes, source identities and new evidence hashes are bound in DELTA_BINDING.json; its own SHA is provided externally to avoid self-reference.

## Candidate source hashes

- `validation/poc-04/scripts/phase1-ci.py` — `f1d110c83173df2954e6a42bce6141d5ec40b22429b9199482444c72ecb16e2b` (Round3A changed)
- `validation/poc-04/test-support/runner.init.gradle` — `107850fd0f08c012f34404aa6ca641b9af2a9d1aa4426f6beb0adcbcfe5ac5a0` (unchanged this round)
- `validation/poc-04/test-support/test_public_verification_classifier.py` — `2b6a0e73f294156003136557c0a0d9243391f9cdbdd51f374802c61e780ed794` (unchanged this round)

Unresolved blocker: the executed POC03 Outbox integration SSL setup failure (08P01). Candidate not ready for workflow integration, CI-delta review, Phase1 closure or merge.
