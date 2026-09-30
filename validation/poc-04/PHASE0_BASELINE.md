# POC-04 Phase 0 — baseline and pins

## 1. Scope and checkpoint status

**PHASE 0 CLOSED: YES — Independent Phase-0 Review Round 3 PASS; closure recording explicitly authorized.**

- Independent Phase-0 Review: **Round 3 PASS**.
- Review archive SHA-256: `ae9b52c9d9683067a3d59fa7cb96b60709244cf27aa0a2c04b1a1980776a5e17`.
- Closure findings: **B0-1 CLOSED; B0-2 CLOSED; G0-1 CLOSED; B0-3 CLOSED**.
- Blocking findings: **0**; critical unresolved pins: **0**.
- Pins: **25 VERIFIED / 0 UNRESOLVED / 0 PROPOSED**.
- Substantive drift: **0**; security regression: **0** in the accepted Round-3 review.

- Explicit current user authority: branch activation and Phase-0 implementation completed; recording Phase-0 closure is authorized after Independent Review Round 3 PASS.
- Phase 1: **NOT AUTHORIZED / NOT STARTED**; the next phase requires separate authorization.
- POC-04 and **all S0–S15 remain NOT VERIFIED**. Pin verification is not a security-gate PASS.
- No OIDC, session, CSRF, policy, audit/observer, webhook, factor, DB-role, migration or browser implementation was started.
- SHARED_SPEC/IMPLEMENTATION_PLAN/ADRs retain their reviewed bytes. Their historical authorization metadata does not itself authorize execution; the current user instruction authorizes this narrow phase.
- This closure-record update modifies only authorized Phase-0 closure/source-binding artifacts and the BRANCH_PLAN current checkpoint. The current BRANCH_PLAN closure-candidate SHA-256 is `24100225282f7ddae8b1d24cb097a9b38cde2842932334b32de6a7ab784df62b`; final closure byte review remains pending. No backend file, workflow, dependency configuration or Git setting was modified. No staging, commit, push, branch change, install or DB/container provisioning occurred.

## 2. Exact initial preflight and controlling bytes

Read-only preflight **before any write** returned:

| Assertion | Observed value | Disposition |
| --- | --- | --- |
| Branch | `poc/04-security-auth` | VERIFIED |
| HEAD / exact branch base | `8243d62f50aed2dc9a8605d7f12f0e2b7aa86854` | VERIFIED |
| `origin/main` | `8243d62f50aed2dc9a8605d7f12f0e2b7aa86854` | VERIFIED |
| `origin/poc/04-security-auth` | `8243d62f50aed2dc9a8605d7f12f0e2b7aa86854` | VERIFIED |
| HEAD tree | `33d347db596daa23247b1602964be6fb9ada54d6` | VERIFIED |
| `git status --porcelain=v1 -uall` | Empty | VERIFIED initial clean worktree |

The initial clean preflight above is historical. The current closure snapshot includes exactly one authorized unstaged tracked change, ` M docs/BRANCH_PLAN.md`, plus the existing 15 untracked Phase-0 files; nothing is staged. Pre-edit BRANCH_PLAN reconciliation passed independent document-byte review at SHA-256 `b0c231dcfe1b36897dd81e9cb6c0040c1e0253132115b0d33f91fb5af96d1ad2`. The authorized current-checkpoint closure update produces candidate SHA-256 `24100225282f7ddae8b1d24cb097a9b38cde2842932334b32de6a7ab784df62b`. Round-3 PASS applies to the preceding reviewed archive; it does not claim independent review of these newly recorded closure bytes or authorize Phase 1.

The plan's original drafting baseline `2d1b2936ee0b4eb159ff630db5a481dc06ad1976` is historical. The explicitly authorized execution base is the later approval commit above; no input was rewritten to conceal the distinction.

| Controlling path | SHA-256 — VERIFIED and unchanged |
| --- | --- |
| `SHARED_SPEC.md` | `c035e627c087b41cb4533b00f0bdaf8fa208a5b55881358cf1f85b6809b2ed2d` |
| `IMPLEMENTATION_PLAN.md` | `c169ad62d947841ec08987d2561f16b75935168d99ea46a34143004d3da3d7c5` |
| `docs/adr/ADR-004-human-identity-and-browser-session-authority.md` | `860d14431ed905e18aa67a60eb16fe44660a4fe28f056d47cd57eb13de642899` |
| `docs/adr/ADR-005-privileged-security-operations-and-protected-audit.md` | `dd5933ca1adc930a5b8617b6f5b49f748081f7f9770dea87b761b3e937319f49` |
| `docs/adr/ADR-006-synchronous-guarded-postgresql-capability-boundary.md` | `3068fa468b1483788a3a59cab3b628b6b8a2cc0beee101a93e0111b55e18f892` |
| `backend/migration/src/main/resources/db/migration/V1__inventory_reservation_foundation.sql` | `d804cbe5153b9a904ae0747af55c8295079d4a0c3edb4a38145ab5199b5b59c4` |
| `backend/migration/src/main/resources/db/migration/V2__inventory_reservation_idempotency.sql` | `7bef750b59b7b77e8402f4f42c13c21c4e072092504b9f268bace9b72de8d2bb` |
| `backend/migration/src/main/resources/db/migration/V3__outbox_recovery.sql` | `9edbd15ffe08ee4edafa3f549856ee38a782485c54db9c73e69d4263e5ca26b4` |

The repository origin still uses the old `siwakon8285/VRA-Fastform.git` alias. The primary GitHub API resolves that alias and `siwakon8285/VRA-Platform` to repository ID **1383749291**; current `main` matches the required base. This naming drift is recorded, not silently changed.

## 3. Repository audit before selection

| Inspected source | Finding / consequence |
| --- | --- |
| Root tree | `.editorconfig`, `.gitattributes`, `.github`, `.gitignore`, `AGENTS.md`, `README.md`, `backend`, `docker-compose.yml`, `docs`, `validation`; existing Java foundation retained. |
| `validation/poc-04/` | Initially only frozen SHARED_SPEC and approved IMPLEMENTATION_PLAN. No Phase-1 source or evidence tree. |
| POC-03 | Existing spec/plan/role bootstrap and `evidence/RESULTS.md`: exact-source, executed SQL/state and independent-review evidence patterns; no substitution for POC-04. |
| Gradle wrapper | 9.7.1 BIN distribution and configured SHA-256; wrapper JAR independently hashed and matched upstream wrapper checksum. Distribution checksum does not prove wrapper JAR. |
| Gradle settings/builds | Java 21, Boot 4.1.1, runtime/migration modules; Maven Central/Gradle Plugin Portal. Testcontainers 2.0.5 independently confirmed in the Boot BOM. No existing dependency lock/verification metadata. |
| `.github/workflows/backend-ci.yml` | Only current workflow; checkout `v7.0.1`, setup-java `v6.0.1`, Temurin `21`, `ubuntu-latest`. No full-commit action refs or exact JDK/runner in that workflow; checkout tag mutable, setup-java tag release-locked by GitHub immutable release. Phase-10 remediation required. |
| `.gitignore` | Ignores `.local/`, Gradle/build outputs, `.env`, `*.log`; does not generally ignore `.env.*`, Node/Python caches or TEST private material. Keep secrets outside repository; no ignore edit authorized. |
| Root Compose | Mutable `postgres:17.11`, fixed project/database names, persistent named `postgres_data`, secret file and loopback port. **Not an attested disposable POC-04 run environment; do not reuse it for this proof.** |
| Testcontainers sources | Existing PostgreSQL tests request `postgres:17.11`; no image-digest override yet. Core 2.0.5 also uses Ryuk 0.14.0 and Alpine 3.17 tinyimage: both pins recorded; inherited helpers are not silently omitted. |
| npm / Python manifests | None in the initial tracked tree. Phase-0 provenance-only locks are new under `tooling/`; no functional suite or observer was added. |
| `docs/BRANCH_PLAN.md` | Initial audit found stale `main` / PLANNED / not-started wording. **G0-1 CLOSED / reconciled** by separately authorized status update and independent document-byte review at historical pre-closure SHA-256 `b0c231dcfe1b36897dd81e9cb6c0040c1e0253132115b0d33f91fb5af96d1ad2`. Current closure-candidate SHA-256 `24100225282f7ddae8b1d24cb097a9b38cde2842932334b32de6a7ab784df62b` binds the authorized Phase-0 checkpoint update: Phase 0 CLOSED after Round 3 PASS; Phase 1 NOT AUTHORIZED / NOT STARTED; POC-04 / S0-S15 NOT VERIFIED. |

Observed local inventory, not inferred installation: Darwin 27.0.0 arm64; `/usr/bin/python3` 3.9.6; Node 24.19.0/npm 11.17.0; Homebrew Java 21.0.12.1; OpenSSL 3.6.4. Docker CLI exists; `gh` was not found. No daemon/DB/issuer/browser environment is claimed created or proven from those observations. Future observer/TLS/JDK execution uses the source-bound selected artifacts, not an unverified host substitute. The standard-library source-binding support scripts ran with observed Python 3.9.6; future observer Python is separately pinned.

## 4. Pin/provenance register and trust limits

The single register is [tooling/PINS.md](tooling/PINS.md), with machine-readable [tooling/tool-manifest.json](tooling/tool-manifest.json). It binds the four detailed primary-source provenance records by file SHA-256. Every selected record contains purpose, exact artifact/version, expected immutable checksum/digest, provenance source, verification method, license/source project, execution location, production inclusion **NO**, disposition and STOP behavior.

`VERIFIED PIN` means the specified primary artifact identity/provenance was established by the recorded method. For hosted-runner it means **VERIFIED BASELINE OBSERVATION** of the exact existing source-bound execution/image/release, not an immutable alias or future runner. There are **25 verified records / 0 unresolved pins / 0 proposed pins**; governance closure remains separate. It does not mean installed, executed, vulnerability-cleared, signature-attested or reproducibly rebuilt. Mutable readable tags are discovery labels; use the recorded digest/full commit/integrity for machine execution. Before use rehash downloaded bytes or verify image/index/platform/config digests against this reviewed register; mismatch, missing artifact or unsupported platform is STOP. Do not substitute `latest`, another architecture, system package or a moved tag.

Separate results: Gradle distribution publisher checksum matched configured checksum; local wrapper JAR matched a **different independently fetched wrapper checksum** and the upstream release-checksum reference. Exact existing action tags were resolved to their same-version full commits; no upgrade or workflow edit. JDK archives and selected scanner artifacts were fully downloaded and hashed without installation. OCI manifests/configs were hashed; only the explicitly recorded Python/Node/OpenSSL inspection fetched selected layers. No complete-image runtime/source reproducibility claim is made.

The provenance-only npm lock fixes the Bruno/Playwright graph with exact registry URLs and integrity, generated with Node 24.19.0/npm 11.17.0 using `--package-lock-only --ignore-scripts --no-audit --no-fund`. No `node_modules`, lifecycle script, suite or collection. Later suite packaging must preserve this reviewed graph or obtain new provenance review. Python wheel hashes likewise constrain the TEST observer dependency environment; no observer implementation or package installation occurred.

Additional compatibility/security inputs: pglast 7.18 bundles a **PostgreSQL 17.7 parser**, not a 17.11 server; actual 17.11 native-denial/classifier tests remain mandatory. Alpine 3.17 is an inherited old tinyimage default needing S13 security assessment; provenance does not approve its vulnerabilities. Vendored Python-client SBOM/library findings remain S13 triage inputs. Ryuk's inherited privileged Docker socket is TEST host administration, never ordinary workload authority; isolate the daemon from shared/production workloads and secrets. Any newly selected helper/tool/driver must be pinned before execution.

## 5. GitHub governance and runner findings

Primary read-only public API observations for [main](https://api.github.com/repos/siwakon8285/VRA-Platform/branches/main), [rulesets](https://api.github.com/repos/siwakon8285/VRA-Platform/rulesets) and [effective main rules](https://api.github.com/repos/siwakon8285/VRA-Platform/rules/branches/main) show `protected=false`, required-check enforcement `off`, empty contexts/checks, empty public rulesets and empty effective public rules. This is a public-view observation, not an attestation about unavailable private/org settings. No settings changed. **S13 remains NOT VERIFIED** and direct-push bypass is not waived.

The existing exact source-bound [run 36684984462, attempt 1 / job 109788657016](https://github.com/siwakon8285/VRA-Platform/actions/runs/36684984462/job/109788657016), on `main` at HEAD `8243d62f50aed2dc9a8605d7f12f0e2b7aa86854`, is now a **VERIFIED BASELINE OBSERVATION**. It records runner `2.337.0`, image `ubuntu-24.04`, version `20260920.314.1`, provisioner `20260828.587`, and release `ubuntu24/20260920.314`. Observed Java labels are setup-java `21.0.12+1`, Gradle launcher `21.0.12.1`, and Eclipse Adoptium `21.0.12.1+1-LTS`.

Run/job APIs and image release/tag/commit-addressed software metadata were independently re-fetched. Exact runner/provisioner and actual Java log observations are supplied by the current independent review; anonymous logs returned 403, so no direct re-fetch/full log re-verification is claimed. Release metadata reports `immutable=false`; source commit `e75633902841aa5479c759492b73409e6d317f12` binds its software-manifest source, not immutable image binaries. No new hosted run was triggered by this remediation.

**ubuntu-latest remains mutable. Every later/final hosted execution must independently capture the exact runner version, image/OS, image version, runner-images release, and actual Java runtime. This baseline observation does not satisfy S13 and does not bind a future runner execution.** Missing/mismatching or non-source-bound observations are STOP for later/final hosted claims. Current action tags/major-only JDK must still be replaced/verified at the authorized Phase-10 checkpoint; exact candidates and this observation do not claim current workflow enforcement.

The frozen S13 closure path remains: preferred enforced pre-integration review/required checks, or an independently accepted exact-change alternative naming reviewer/approver, successful checks **before integration**, PR/commit/audit trail, technical direct-push bypass and residual-risk acceptance. No after-the-fact observation substitutes for it.

## 6. Deterministic source binding

Run from any working directory with a Python >=3.9 standard-library interpreter:

```text
python3 -B validation/poc-04/scripts/source-manifest.py
python3 -B validation/poc-04/scripts/source-manifest.py --require-clean
python3 -B validation/poc-04/scripts/verify-phase0.py
```

The scripts perform read-only Git/filesystem checks. Source output includes branch/HEAD/tree, raw worktree cleanliness/status, path/size/mode/SHA-256 for every tracked source and nonignored POC-04 support/TEST source, including canonical docs, authorities, migrations, workflow, wrapper and build files. Future fixture/browser/Bruno/blackbox/DB directories are explicit existence/coverage inputs. Worktree dirtiness is reported, never converted to clean because changes are authorized. Before a later final evidence run, `--require-clean` must succeed on reviewed committed bytes.

Content digest is SHA-256 of a sorted-path canonical UTF-8 JSON array of path/hash/size/mode; no clock or run ID. Output uses sorted JSON keys. HEAD/tree/status and a second file-byte/mode pass detect changes during collection. Operators must still prevent concurrent source mutation; no atomic-filesystem snapshot claim. Generated `source-manifest.json`, `phase0-checks.json`, `run-manifest.json`, execution evidence and caches are excluded to avoid recursive self-hashing. All source pin documents and lock files are included. Generated manifests are compared to fresh output, not trusted as authority themselves.

`verify-phase0.py` checks the fixed authorized base and remote refs, eight controlling hashes, no staged paths and an exact unstaged tracked-change allowlist containing only `docs/BRANCH_PLAN.md` at closure-candidate SHA-256 `24100225282f7ddae8b1d24cb097a9b38cde2842932334b32de6a7ab784df62b`. It also requires the reconciled current-branch/row-04/Section-17/base/checkpoint markers, the absence of stale not-started wording, and explicit Phase-0 CLOSED / Round-3 PASS / Phase-1-NOT-AUTHORIZED-and-NOT-STARTED / NOT VERIFIED statements. Any other tracked modification, staged path or different governance bytes is STOP. The existing Phase-0 untracked scope, V1–V3-only migration inventory, absent Phase-1 fixture paths, 25 VERIFIED / 0 UNRESOLVED / 0 PROPOSED pins and bound provenance files remain checked. It also scans all existing Phase-0 baseline/tooling/scripts, including raw text and decoded JSON strings, for temporary signed URL query material; denial reports only the offending path, never URL/query values. This guard does not substitute for a general secret scan. Wrong base/hash/scope returns **1 / PHASE0_STOP**. Completed checks with unresolved checkpoint items return **2 / NOT CLOSED**; zero blockers returns **0 / Phase-0 closure recorded**. Neither result marks any S0–S15 gate PASS or authorizes Phase 1. Offline record validation is not fresh upstream verification.

The saved `tooling/source-manifest.json` and `tooling/phase0-checks.json` are Phase-0 inputs for later S0/S13/S15 review. Closure recording regenerates them from the actual worktree, including the exact BRANCH_PLAN closure-candidate hash and unstaged porcelain status; repeated source-generator output must be byte-identical. Generated hashes are never patched manually. `tooling/run-manifest.json` separately records this provenance-only activity and explicitly says **no database instance exists**. Actual future execution timestamps and physical-instance evidence go there, not into source-content hashes.

## 7. Disposable TEST identity contract — design, not provisioned environment

Design is explicit/source-bound; actual create/destroy proof is **NOT EXECUTED**. Before any later test mutation require:

1. A new UUIDv4 `run_id`; stable within that run only. Record `environment_type=TEST`, `poc=04`, UTC `started_at`, branch/HEAD/tree/source-content SHA-256, tool-manifest version and file SHA-256. This metadata is **not authentication authority**.
2. Derive distinct UUIDv5 `database_instance_id` values with namespace `run_id` and exact logical names `postgres-authority/0`, `postgres-simulator/0` where required. A restart of the same durable test instance retains its ID; a newly initialized instance gets a new logical ordinal/ID.
3. Admin harness uses a dedicated disposable TEST Docker daemon/context, unique names `vra-poc04-<run_id>-<logical_role>`, exact platform/image digest, fresh per-run volumes and network, new injected credentials and no production secrets/external shared DB. No root-Compose persistent volume reuse. Ryuk only has this isolated TEST daemon's socket.
4. At creation bind labels `dev.vra.environment=TEST`, `dev.vra.poc=04`, `dev.vra.run_id`, `dev.vra.database_instance_id`, `dev.vra.source_head`, `dev.vra.source_content_sha256`, `dev.vra.tool_manifest_sha256` on container/volume/network. Capture exact Docker container/volume/network IDs and creation metadata with safe inspect projections, image/platform digest, and admin-only PostgreSQL `system_identifier`/server version/database name. Match these to the manifest; hostname/localhost alone is insufficient.
5. Before mutation assert all labels/physical IDs/source/image/database identity agree, no unexpected mount/shared volume/production target, and run-scoped resource absence before creation. A mismatch or missing attestation is STOP, not “TEST by configuration.” Keep admin creation attestation separate from ordinary runtime identity assertions.
6. Teardown is limited to the exact recorded IDs after rechecking ownership labels and the expected dedicated daemon/context. Preserve sanitized failure/state evidence first; remove only this run's container/volumes/network, then prove those IDs are absent. Never name-match a shared resource or run blanket prune/volume deletion. Demonstrate a harmless sentinel in another isolated run is preserved and a wrong-ID/label attempt denies. New-run initialization must prove fresh physical state; restart/recovery tests preserve the intended instance instead of recreating it.

This phase does not create credentials, DBs, roles, schemas, labels or a provision/teardown implementation. If those later proofs cannot establish disposability, block execution/evidence closure.

## 8. TLS / TEST host contract

- Preserve approved topology: `https://vra.poc04.test:8443` and `https://issuer.poc04.test:8444/realms/poc04`; separate hostnames are mandatory for host-cookie isolation even when mapped to one loopback address.
- Generate TEST CA/leaf certificates later with the register's exact OpenSSL 3.0.20 binary inside the pinned Python image. Recheck image/platform and `/usr/bin/openssl` hash plus version before generation; host Homebrew OpenSSL 3.6.4 is not substituted.
- Per-run RSA-3072 CA/key; TEST CA validity 7 days, leaves 1 day, each generated fresh for the run. Separate server-auth leaves with SAN exactly `DNS:vra.poc04.test` or `DNS:issuer.poc04.test`, CA=false, digitalSignature/keyEncipherment; no production SAN/provider/PKI. These are fixture configuration only.
- Local operator-controlled host mapping: `127.0.0.1 vra.poc04.test issuer.poc04.test`; explicit IPv4 service binding. No `/etc/hosts` edit in this phase. In CI, use an isolated network with those aliases reachable by the genuine browser, issuer and VRA; record actual resolver/route/TLS checks, never assume alias connectivity from Compose.
- Trust the public TEST CA only in disposable browser/process trust stores. No global trust-store update, TLS bypass, `ignoreHTTPSErrors` or plaintext-cookie exception can satisfy F proof.
- All private material stays in a restricted run directory outside the repository, mounted read-only into the intended process, keys mode 0600/directory 0700, never package/evidence/log/command value. Public certificate fingerprints/SANs and successful validation are safe evidence; no private-key hash/value export. Destroy key/trust state with that run. Actual TLS/browser proof remains NOT VERIFIED.

## 9. Checks, blockers and review handoff

Final check output is in `tooling/phase0-checks.json`; current untracked scope is only this baseline, tooling metadata/locks and source-binding scripts. `git diff --check` and a separate untracked text whitespace check are required; the former alone does not inspect untracked files. Source manifest was exercised twice and compared, with restrictive wrong-HEAD and dirty-clean negative checks. No backend suite, S-gate scan, DB migration, browser or new hosted CI run was executed by this Phase-0 work. The existing source-bound hosted run above supplies baseline observations only.

Required item dispositions: source baseline, controlling bytes, V1–V3 immutability, distribution/wrapper provenance, required selected tools/images/actions, source-manifest mechanism, explicit TEST identity design and TLS design are **VERIFIED at the recorded boundary**. Actual environment/browser/security execution is **NOT VERIFIED**; optional unselected Testcontainers modules are **NOT APPLICABLE until selected**, when their tool pins become mandatory.

Review round 1 remediation: **B0-1 CLOSED** — both complete signed redirect fields removed, canonical upstream identities/checksums retained and a safe URL-query verifier guard added. **B0-2 CLOSED** — exact hosted baseline observation and future-execution residual recorded; **no unresolved pins remain**. **G0-1 CLOSED** — separately authorized BRANCH_PLAN reconciliation passed independent document-byte review at historical pre-closure SHA-256 `b0c231dcfe1b36897dd81e9cb6c0040c1e0253132115b0d33f91fb5af96d1ad2`. **B0-3 CLOSED** — the verifier admits only the exact authorized unstaged exception and regenerated source/check manifests bind current closure-candidate governance bytes and worktree. No general tracked-change relaxation is authorized. **Independent Phase-0 Review Round 3 PASS**, archive SHA-256 `ae9b52c9d9683067a3d59fa7cb96b60709244cf27aa0a2c04b1a1980776a5e17`; blocking findings **0**, critical unresolved pins **0**, pins **25 VERIFIED / 0 UNRESOLVED / 0 PROPOSED**. No pin, drift or S13 finding is silently waived. S13 enforcement/alternative, actual runtime tool/version attestation, new dependency triage and executed S0–S15 evidence remain later required checkpoints. **PHASE 0 CLOSED: YES. READY FOR FINAL PHASE-0 CLOSURE BYTE REVIEW: YES. Phase 1: NOT AUTHORIZED / NOT STARTED. POC-04: NOT VERIFIED. S0-S15: NOT VERIFIED.**
