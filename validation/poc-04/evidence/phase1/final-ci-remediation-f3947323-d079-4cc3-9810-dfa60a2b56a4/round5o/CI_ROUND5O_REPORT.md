# Round 5O — hosted checkout Git-origin reproducibility

CI-DELTA BLOCKER REMEDIATION: PASS

This round changes only `.github/workflows/backend-ci.yml` and TEST-only `test_ci_support_boundary.py`. The workflow adds exactly `fetch-depth: 0` to the existing pinned checkout. Checkout pin `actions/checkout@v7.0.1`, `persist-credentials: false`, PR-head/push revision expression, read permissions and the single bootstrap command remain unchanged. No classifier, catalog, scanner configuration, runner init, Java, JDBC/SSL, migrations or historical evidence was changed. Canonical branch/HEAD/tree/index are unchanged; staged paths are zero.

The independently derived cumulative candidate has 13 implementation/workflow/TEST-support paths. `FINAL_SOURCE_INVENTORY.json` records every path/hash/size/mode. Exact Round-5O changes are in `backend-ci.yml.round5o.diff.patch` and `test_ci_support_boundary.py.round5o.diff.patch`; `WORKFLOW_STATIC_REVIEW.json` proves removing the single new input restores the pre-change workflow bytes.

## Fresh remote history proof

Two separate external repositories were fetched directly over HTTPS from `https://github.com/siwakon8285/VRA-Fastform.git`, with Git root/object overrides absent, global/system configuration disabled and only HTTPS transport permitted. No canonical `.git/objects`, alternates, hardlinks or Round-5N Git metadata was used. Both checkouts retain exact HEAD `ae9bd1f53a7e3e8612f2f4cd9f778e27d7cdb0db`, tree `988058edd53504936345bc51ebdeee2d7d55b0c7`. Full history changes object availability only.

The depth-one checkout is shallow and cannot resolve required commit `01256d96d85823740a8d6c3a3f99346afbdbef56`. All six reviewed finding replays stay unresolved with the unchanged classifier. This is expected negative regression proof, not a failed candidate scan. The separately cloned full-history repository is not shallow, contains that object as a commit, independently verifies its Git object identity, and proves it is an ancestor of checked-out HEAD. All six findings resolve their exact approved contexts and immutable commit origin, including the one exact single-period normalization. No textual-SHA fallback exists. `REMOTE_CHECKOUT_COMMANDS.json`, `REMOTE_CHECKOUT_PROOF.json` and `SIX_CLASSIFIER_ORIGIN_PROOFS.json` retain safe traces without raw matched tokens.

## Exact source capsule and focused gates

The pristine full-history remote checkout was bound against each committed Git blob before overlay. Only the exact current 13-file remediation delta was applied. The resulting 1103-file source capsule excludes `.local`, developer build/cache trees, OS metadata, all 276 historical bin paths and unselected untracked historical evidence. `CAPSULE_BASE.json`, `CANDIDATE_DELTA.json` and `SOURCE_CAPSULE_MANIFEST.json` bind the construction; no wholesale workspace copy was performed.

Focused tests ran once in this capsule: 255 / 0 skipped / 0 failures / 0 errors. Prior 250 remain, plus five checkout contract tests. The initial RED contract proof rejected the missing fetch-depth while the four prohibited mutation controls passed. The narrow standard-library helper inspects the existing checkout scalar-mapping layout; it is not a general YAML validator. No undeclared YAML library was added. Hermetic fixture tests require no unselected historical evidence. Python syntax/AST inspection and canonical/capsule `git diff --check` pass.

Two preplanned evaluated Gradle model queries agree exactly, cause candidate-tree mutation 0, and explicitly contain `--no-problems-report`. All eight actual configured shared-resource shapes resolve uniquely through evaluated project/source-set/resource roots and authoritative tracked origins, with exact byte equality. The eight generated destination copies are exercised only in the external observational model-proof workspace; the scanner candidate contains their already-tracked authoritative origins. No textual parser fallback or path/hash exception was added. Inventory unexpected paths: 0.

Candidate content binding: `7e0deae81a11148e5cbd3b76672ec226b7adb267fb22edd7cad7ba92ccd64780`.

Manifest binding: `a93db1df523df957aee0bda865ea07ba5a87bfeba9bf162825aa10815e265045`.

## Immutable security input and disposable build

Immutable scanner tree: `/private/var/folders/6p/8cx8_9294s10q4v934jqdn9h0000gn/T/vra-round5o-52nom05p/tmp/vra-poc04-ci-nrt0sj7t/domains/immutable-candidate`.

Disposable build tree: `/private/var/folders/6p/8cx8_9294s10q4v934jqdn9h0000gn/T/vra-round5o-52nom05p/tmp/vra-poc04-ci-nrt0sj7t/domains/build-workspace`.

Invocation checkout: `/private/var/folders/6p/8cx8_9294s10q4v934jqdn9h0000gn/T/vra-round5o-52nom05p/source-capsule`.

Canonical repository: `/Users/siwakornbundi/Project/VRA-Fastform`.

The scanner and build trees were independently materialized from the same frozen manifest before Gradle ran. Their per-path SHA/size/mode bindings match; roots and storage are distinct. Private Git metadata in those domains originates from the independently fetched full-history checkout. Every actual build command uses B cwd and explicit B wrapper/project/init/support paths. Canonical and immutable scanner roots receive no build commands.

Scanner A/B/C/D use the same immutable bytes, path set, pinned binary/default configuration, environment contract and arguments. Complete safe finding sets agree exactly, including all six historical commit-origin findings. Each has 18 observations: 2 public verification identifiers, 16 bounded digest/Git metadata classifications, 0 unresolved. Binary artifact integrity and effective configuration are bound; ambient scanner overrides cannot silently reduce coverage. Raw matched tokens are never retained. `INDEPENDENT_SCANNER_FINDING_SET_RECONCILIATION.json` independently compares full sets, payload hashes and execution identities.

The full workflow-equivalent bootstrap command ran once, exit 0. It used unchanged workflow command semantics. Compile gates, explicit Java21 support bytecode, TEST-only classpaths and representative PostgreSQL/resource proof pass. Raw JUnit XML independently reconciles:

| Suite | Tests | Skipped | Failures | Errors |
|---|---:|---:|---:|---:|
| migration:test | 4 | 0 | 0 | 0 |
| migration:integrationTest | 20 | 0 | 0 | 0 |
| runtime:test | 56 | 0 | 0 | 0 |
| runtime:integrationTest | 113 | 0 | 0 | 0 |
| TOTAL | 193 | 0 | 0 | 0 |

No replacement rerun, retry-to-green, JDBC/SSL masking or diagnostic transport machinery participated. The same pre-existing A snapshot was scanned after build for D; mutated B was not original-candidate authority. `checksums.lock` exists only as B build state and is absent from original/expanded input, without any `.gradle` ignore exception.

The expanded manifest contains frozen candidate plus exactly two explicitly producer-bound bootJars. No Git history objects, caches, lock files, reports or arbitrary build tree enters it. Expanded scan: 18 observations, 0 unresolved, original finding coverage retained. Runtime/migration bootJars were independently inspected including nested libraries: no TEST support/service providers, scanner helpers or diagnostic code.

Fresh UUID run `c816fc29-dff4-41a7-9681-b07606cb5409` is bound to the actual isolated TEST daemon and exact creator ledger. PostgreSQL physical identity and source/content/tool labels are verified; unauthorized teardown is denied, exact authorized teardown succeeds. All 129 registered resources are independently confirmed absent: {"container": 42, "network": 43, "volume": 44}. No prune, wildcard cleanup or discovery-derived deletion authority was used.

## Canonical stability and retained historical dispositions

All 7,659 canonical file paths were bound before and after execution. Only the two authorized pre-execution workflow/test edits differ from the start; execution caused zero additional path/hash/size/mode changes. HEAD/tree/index remain unchanged, staged paths zero. All 288 historical drift paths and unresolved `.local`/cache files remain untouched. Source boundaries verify committed V1–V4, JdbcReservationIdempotencyRepository and RunOwnedPostgreSQLContainer bytes; V5/V6 absent and no Phase2 delta.

Round-5N's 356 evidence files and report hash are independently rechecked unchanged. Prior failures remain preserved as failures. This round does not rewrite historical results or automatically recommend all historical evidence for commit.

CANONICAL 288-PATH HISTORICAL DRIFT: A — ACCEPTED NON-BLOCKING HISTORICAL WORKSPACE DRIFT. ROOT CAUSE / WRITER ATTRIBUTION: NOT ESTABLISHED.

HISTORICAL 18/17: A — ACCEPTED NON-BLOCKING HISTORICAL SCANNER ANOMALY. ROOT CAUSE: NOT ESTABLISHED. RESIDUAL RISK: RETAINED. FUTURE RECURRENCE: HARD FAILURE.

08P01: ACCEPTED NON-BLOCKING HISTORICAL ANOMALY. ROOT CAUSE: NOT ESTABLISHED. RESIDUAL RISK: RETAINED. FUTURE RECURRENCE: HARD FAILURE.

These accepted historical dispositions are retained independently; current reproducibility and determinism do not establish their old causes.

## Final status

CI-DELTA BLOCKER REMEDIATION: PASS

HOSTED CHECKOUT GIT-ORIGIN PREREQUISITE: PASS

FRESH FULL-HISTORY CHECKOUT: PASS

HISTORICAL COMMIT OBJECT: AVAILABLE / VERIFIED

SIX CLASSIFIER ORIGINS: PASS

FOCUSED TESTS: PASS — 255 / 0 / 0 / 0

ORIGINAL SNAPSHOT SCANNER: PASS — A/B/C/D exact finding-set equality / 0 unresolved

LOCAL WORKFLOW-EQUIVALENT: PASS — 193 / 0 / 0 / 0

EXPANDED ARTIFACT SCAN: PASS — exactly two bootJars / 0 unresolved

BOOTJAR ISOLATION: PASS

DOCKER CLEANUP: PASS

ROUND-5O CANONICAL NON-MUTATION: PASS

READY FOR INDEPENDENT RE-REVIEW: YES

READY FOR FINAL PRE-STAGING COMMIT-SET SCAN: NO — independent re-review required

READY FOR STAGING: NO

READY FOR HOSTED CI: NO

PHASE 1 CLOSED: NO

READY TO MERGE PR #3: NO

Phase 2: NOT AUTHORIZED

POC-04 / S0-S15: NOT VERIFIED

This is local hosted-checkout reproducibility proof; GitHub-hosted CI did not run. No staging, commit, push, merge or canonical cleanup occurred. Evidence and retained execution roots are external. The next independent review must pass before constructing and security-scanning the exact intended commit set; this report is not staging authorization.
