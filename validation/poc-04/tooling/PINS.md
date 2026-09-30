# POC-04 Phase-0 pin/provenance register

**PHASE 0 CLOSED; Independent Phase-0 Review Round 3 PASS; POC-04 / S0–S15 NOT VERIFIED; Phase 1 NOT AUTHORIZED / NOT STARTED.**

Machine register: `tool-manifest.json`. Detailed source records are SHA-256-bound there; all 25 selected records, including helper images and transport/transitive tools, are required by the verifier. This register selects TEST/build/review tooling only; no production IdP/MFA/KMS/webhook vendor. All production artifact inclusion values are NO: none of these binaries/fixtures is to be bundled in runtime/migration JARs. Java remains the accepted language/runtime family, while this JDK pin is the verification toolchain.

`VERIFIED PIN` means primary artifact identity was verified by the stated method. For `hosted-runner`, it means **VERIFIED BASELINE OBSERVATION** of the exact recorded run/image/release; it does not pin the mutable `ubuntu-latest` alias or a future execution. Status counts: **25 verified / 0 unresolved / 0 proposed**; BRANCH_PLAN governance reconciliation is CLOSED at historical pre-closure reviewed SHA-256 `b0c231dcfe1b36897dd81e9cb6c0040c1e0253132115b0d33f91fb5af96d1ad2`; current authorized closure-candidate SHA-256 is `24100225282f7ddae8b1d24cb097a9b38cde2842932334b32de6a7ab784df62b`. Independent Phase-0 Review **Round 3 PASS**, archive SHA-256 `ae9b52c9d9683067a3d59fa7cb96b60709244cf27aa0a2c04b1a1980776a5e17`; closure recording is authorized, with final closure byte review pending. It is not vulnerability clearance, installed/runtime proof, signature attestation, independently rebuilt source or gate PASS. Exact expected bytes must be rechecked before use; missing/mismatching provenance is STOP. Platform-child/config digests and all architecture hashes are in the bound records; never execute a readable tag as a substitute.

## Complete selection table

| Tool | Exact version | Artifact binding / immutable identifier or observed baseline | Pin disposition |
| --- | --- | --- | --- |
| gradle-distribution | 9.7.1 | `acd53f1edaf02f1a8ff99879f8a34b302661a057d9b063ae9e35b552f804d20a` | VERIFIED PIN |
| gradle-wrapper-jar | 9.7.1 | `7a9ce74cff467ca1bf60a4fcd9f05185acceda4d0f382434d393e17864262c5d` | VERIFIED PIN |
| actions-checkout | v7.0.1 | `3d3c42e5aac5ba805825da76410c181273ba90b1` | VERIFIED PIN |
| actions-setup-java | v6.0.1 | `de7274f081f381c8f8158605e0321c36c376e2e6` | VERIFIED PIN |
| java21-temurin | 21.0.12.1+1 | `ce79869e1307ed8ee1e2baa86a412b1eb5b75d10a01006d788a6f968bcfaee94 (first listed artifact; all platform hashes below/in JSON)` | VERIFIED PIN |
| hosted-runner | runner 2.337.0; ubuntu-24.04 / 20260920.314.1 | Run `36684984462`, attempt `1`, job `109788657016`; release `ubuntu24/20260920.314`; exact source HEAD `8243d62f50aed2dc9a8605d7f12f0e2b7aa86854` | VERIFIED PIN — BASELINE OBSERVATION ONLY |
| keycloak_test | 26.7.4 | `quay.io/keycloak/keycloak@sha256:82a77884f3af238beab1e7afd63b5f530e1b5c0590bd7aa60b40a40463e29b2c` | VERIFIED PIN |
| postgresql_test | 17.11 | `docker.io/library/postgres@sha256:d74eeac9a635390a49bc21bd49fccd973de707e2a53a76ac49b552b8712ec46f` | VERIFIED PIN |
| testcontainers_java | 2.0.5 | `bbba1376021fbf4e14663a61f10cd1aff14a5d74fb55f1a14be6fd6de83d7eef (first listed artifact; all platform hashes below/in JSON)` | VERIFIED PIN |
| testcontainers_ryuk | 0.14.0 | `docker.io/testcontainers/ryuk@sha256:7c1a8a9a47c780ed0f983770a662f80deb115d95cce3e2daa3d12115b8cd28f0` | VERIFIED PIN |
| testcontainers_tinyimage | 3.17 | `docker.io/library/alpine@sha256:8fc3dacfb6d69da8d44e42390de777e48577085db99aa4e4af35f483eb08b989` | VERIFIED PIN |
| OSV-Scanner | 2.6.0 | `ca69b3d3cd08f889a49dc0a383122f71cc528b83803671df5fd874d97485b108 (first listed artifact; all platform hashes below/in JSON)` | VERIFIED PIN |
| Gitleaks | 8.30.1 | `551f6fc83ea457d62a0d98237cbad105af8d557003051f41f3e7ca7b3f2470eb (first listed artifact; all platform hashes below/in JSON)` | VERIFIED PIN |
| Syft | 1.52.0 | `caeedb81fb0491615f1ebd1761e4145d41ee86dd2cc7bf80669f9f5ad9d6133d (first listed artifact; all platform hashes below/in JSON)` | VERIFIED PIN |
| @usebruno/cli | 4.2.0 | `fe013bedd2d08a44503ac1488ca4a8d459fdb1a9c58f9bea4fc03222ac63af31` | VERIFIED PIN |
| @playwright/test | 1.63.0 | `fc594f36b7b4aafece614ec6852ad728423215a76edd0a2a1821bb0c49539c4e` | VERIFIED PIN |
| playwright | 1.63.0 | `195a5ee9bfed7c6e9c03965950e32e5e4dbedaa02e740e5a530eb4b87dae050c` | VERIFIED PIN |
| playwright-core | 1.63.0 | `208593d4e1bcd8f8fe5f869cad1cc332dc7f1d70dc1d58c102dc3ac36e30f26c` | VERIFIED PIN |
| Python | 3.13.15 | `docker.io/library/python@sha256:2325bb286ec344af3e5898cc224b5844e2707ac6e26b1632516fd3edc84a5e26` | VERIFIED PIN |
| Node.js | 24.19.0 | `docker.io/library/node@sha256:a9f5f7c91a432850b2a8a7797adf5eadb6c733ceed61167806cee7ea7fbc29df` | VERIFIED PIN |
| Chromium browser bundle | 153.0.8010.12 / revision 1243 | `mcr.microsoft.com/playwright@sha256:eff16c30e6f3f4af0a03fa4b706120d5e9b0891c344a27d64559aff5900a4a27` | VERIFIED PIN |
| pglast | 7.18 | `a43295ccef3b7e9a75ab9b1f1c83d8128c3184c548840f26f12dd320d9c924df (first listed artifact; all platform hashes below/in JSON)` | VERIFIED PIN |
| OpenSSL TEST CA tool | OpenSSL 3.0.20; Debian package 3.0.20-1~deb12u2 | `docker.io/library/python@sha256:2325bb286ec344af3e5898cc224b5844e2707ac6e26b1632516fd3edc84a5e26` | VERIFIED PIN |
| psycopg | 3.3.6 | `a1db9f7148b06a28606767efaca51fa6f9398c5c0a3810519be69d7000bdb631 (first listed artifact; all platform hashes below/in JSON)` | VERIFIED PIN |
| psycopg-binary | 3.3.6 | `cec5ea900390897d0b46130f60bc2883bf19c314f9044235217c8be88b0ef269 (first listed artifact; all platform hashes below/in JSON)` | VERIFIED PIN |

## Current CI action audit

| Workflow | Current action/ref | Ref mutability observation | Resolved full commit | Phase-10 requirement |
| --- | --- | --- | --- | --- |
| `.github/workflows/backend-ci.yml` | `actions/checkout@v7.0.1` | Mutable release tag; release API immutable=false | `3d3c42e5aac5ba805825da76410c181273ba90b1` | Same-version full SHA; workflow unchanged |
| `.github/workflows/backend-ci.yml` | `actions/setup-java@v6.0.1` | Tag locked by GitHub immutable release; immutable=true | `de7274f081f381c8f8158605e0321c36c376e2e6` | Explicit same-version full SHA; workflow unchanged |

JDK `temurin`/`21` is still floating in that workflow; selected exact Temurin 21.0.12.1+1 archives are independently hashed. `ubuntu-latest` remains mutable. The existing exact source-bound run is now recorded as a VERIFIED BASELINE OBSERVATION, with the future-execution residual below; no immutable alias/image-binary claim is made. No Action was upgraded, workflow changed, CI triggered or repository setting mutated.

## Per-tool provenance and STOP rules

### gradle-distribution

- Purpose: Existing build bootstrap provenance; distribution and wrapper are independently checked
- Artifact: `https://services.gradle.org/distributions/gradle-9.7.1-bin.zip`
- Execution location: Local/CI backend wrapper; never system Gradle
- Production artifact inclusion: **NO**
- License/source project: {'license': 'Apache-2.0', 'project': 'gradle/gradle'}
- Verification: Local wrapper JAR SHA-256 or configured distribution SHA compared separately with trusted Gradle release checksum endpoints and release-checksums page; distribution ZIP not downloaded
- Disposition: **VERIFIED PIN**
- STOP: Reject checksum mismatch before executing wrapper or distribution; never regenerate here
- Bound detailed record: `validation/poc-04/tooling/build-ci-pins.json` JSON pointer `/records/0`.
- Primary provenance: ['https://gradle.org/release-checksums/', 'https://docs.gradle.org/current/userguide/gradle_wrapper.html#sec:verification']

### gradle-wrapper-jar

- Purpose: Existing build bootstrap provenance; distribution and wrapper are independently checked
- Artifact: `https://services.gradle.org/distributions/gradle-9.7.1-wrapper.jar`
- Execution location: Local/CI backend wrapper; never system Gradle
- Production artifact inclusion: **NO**
- License/source project: {'license': 'Apache-2.0', 'project': 'gradle/gradle'}
- Verification: Local wrapper JAR SHA-256 or configured distribution SHA compared separately with trusted Gradle release checksum endpoints and release-checksums page; distribution ZIP not downloaded
- Disposition: **VERIFIED PIN**
- STOP: Reject checksum mismatch before executing wrapper or distribution; never regenerate here
- Bound detailed record: `validation/poc-04/tooling/build-ci-pins.json` JSON pointer `/records/1`.
- Primary provenance: ['https://gradle.org/release-checksums/', 'https://docs.gradle.org/current/userguide/gradle_wrapper.html#sec:verification']

### actions-checkout

- Purpose: Current hosted CI action code
- Artifact: `actions/checkout@3d3c42e5aac5ba805825da76410c181273ba90b1`
- Execution location: GitHub hosted CI only
- Production artifact inclusion: **NO**
- License/source project: {'license': 'MIT', 'project': 'actions/checkout'}
- Verification: GitHub primary tag API dereferenced to full commit; immutable action.yml fetched and hashed; release page cross-checked; no action executed; GitHub release API independently reports immutable=false. Full commit remains the selected machine pin.
- Disposition: **VERIFIED PIN**
- STOP: Reject missing/mismatched immutable commit before later use; current workflow not yet pinned
- Bound detailed record: `validation/poc-04/tooling/build-ci-pins.json` JSON pointer `/records/2`.
- Primary provenance: ['https://api.github.com/repos/actions/checkout/git/ref/tags/v7.0.1', 'https://github.com/actions/checkout/releases/tag/v7.0.1', 'https://raw.githubusercontent.com/actions/checkout/3d3c42e5aac5ba805825da76410c181273ba90b1/action.yml', 'https://api.github.com/repos/actions/checkout/releases/tags/v7.0.1']

### actions-setup-java

- Purpose: Current hosted CI action code
- Artifact: `actions/setup-java@de7274f081f381c8f8158605e0321c36c376e2e6`
- Execution location: GitHub hosted CI only
- Production artifact inclusion: **NO**
- License/source project: {'license': 'MIT', 'project': 'actions/setup-java'}
- Verification: GitHub primary tag API dereferenced to full commit; immutable action.yml fetched and hashed; release page cross-checked; no action executed; GitHub release API independently reports immutable=true. Full commit remains the selected machine pin.
- Disposition: **VERIFIED PIN**
- STOP: Reject missing/mismatched immutable commit before later use; current workflow not yet pinned
- Bound detailed record: `validation/poc-04/tooling/build-ci-pins.json` JSON pointer `/records/3`.
- Primary provenance: ['https://api.github.com/repos/actions/setup-java/git/ref/tags/v6.0.1', 'https://github.com/actions/setup-java/releases/tag/v6.0.1', 'https://raw.githubusercontent.com/actions/setup-java/de7274f081f381c8f8158605e0321c36c376e2e6/action.yml', 'https://api.github.com/repos/actions/setup-java/releases/tags/v6.0.1']

### java21-temurin

- Purpose: Exact Java21 JDK for future CI/local verification
- Artifact: `Eclipse Temurin HotSpot JDK jdk-21.0.12.1+1`
- Execution location: Future CI Linuxamd64/localAppleSilicon; existing Homebrew21.0.12.1 is only an observation and not this verified binary
- Production artifact inclusion: **NO**
- License/source project: {'license': 'GPL-2.0-with-classpath-exception', 'project': 'adoptium/temurin21-binaries; OpenJDK'}
- Verification: Primary Adoptium metadata plus same fixed-release published SHA256 and complete archive downloads hashed in memory for Linux x64 and macOS aarch64; nothing installed
- Disposition: **VERIFIED PIN**
- STOP: Do not run later build on an unverified different JDK; signatures/reproducible build not claimed
- Bound detailed record: `validation/poc-04/tooling/build-ci-pins.json` JSON pointer `/records/4`.
- Primary provenance: ['https://adoptium.net/', 'https://github.com/adoptium/temurin21-binaries/releases/tag/jdk-21.0.12.1%2B1']
- Exact artifact: `https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.12.1%2B1/OpenJDK21U-jdk_x64_linux_hotspot_21.0.12.1_1.tar.gz`; SHA-256 `ce79869e1307ed8ee1e2baa86a412b1eb5b75d10a01006d788a6f968bcfaee94`; primary download https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.12.1%2B1/OpenJDK21U-jdk_x64_linux_hotspot_21.0.12.1_1.tar.gz.
- Exact artifact: `https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.12.1%2B1/OpenJDK21U-jdk_aarch64_mac_hotspot_21.0.12.1_1.tar.gz`; SHA-256 `3623232f33a9c3baadf304480b2535f9a3cba8a58d42ecbb438ba267315d9998`; primary download https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.12.1%2B1/OpenJDK21U-jdk_aarch64_mac_hotspot_21.0.12.1_1.tar.gz.

### hosted-runner

- Purpose: Exact source-bound hosted baseline observation, not a pin of the mutable runner alias.
- Artifact: Existing run `36684984462`, attempt `1`, job `109788657016`, workflow `.github/workflows/backend-ci.yml`, branch `main`, source HEAD `8243d62f50aed2dc9a8605d7f12f0e2b7aa86854`. This existing baseline is not a new POC-04 S-gate execution.
- Observed runner: version `2.337.0`; image `ubuntu-24.04`, version `20260920.314.1`, provisioner `20260828.587`; runner-images release `ubuntu24/20260920.314`.
- Release source: commit `e75633902841aa5479c759492b73409e6d317f12`; release API `immutable=false`. The commit binds the software-manifest source, not immutable runner-image binary content.
- Observed Java: setup-java resolved `21.0.12+1`; Gradle launcher JVM `21.0.12.1`; Eclipse Adoptium `21.0.12.1+1-LTS`. These exact observed labels do not substitute for the separate future JDK archive hash checks.
- Execution location: Existing hosted baseline observation only; no new hosted run triggered by this remediation.
- Production artifact inclusion: **NO**.
- License/source project: Repository-specific runner image components; `actions/runner-images`.
- Verification: Run/job primary APIs independently confirm IDs, association and exact HEAD. Release/tag APIs and commit-addressed image software README independently confirm image release/version/source commit and included Java. Exact runner/provisioner and actual Java log observations derive from the current user-supplied independent review. Anonymous job-log API returned 403; no raw logs were re-fetched or retained, and no full log re-verification is claimed.
- Disposition: **VERIFIED PIN — VERIFIED BASELINE OBSERVATION ONLY**. The verified object is this exact observed execution/image/release, not `ubuntu-latest` or a future execution.
- Retained residual: **ubuntu-latest remains mutable. Every later/final hosted execution must independently capture the exact runner version, image/OS, image version, runner-images release, and actual Java runtime. This baseline observation does not satisfy S13 and does not bind a future runner execution.**
- STOP: Missing, mismatching or non-source-bound required observations block any later/final hosted claim; no fallback to this baseline and no S13 PASS from the observation alone.
- Bound detailed record: `validation/poc-04/tooling/build-ci-pins.json` JSON pointer `/records/5`; exact safe response hashes are recorded there and in `tool-manifest.json`.
- Primary provenance: [run API](https://api.github.com/repos/siwakon8285/VRA-Platform/actions/runs/36684984462), [job API](https://api.github.com/repos/siwakon8285/VRA-Platform/actions/jobs/109788657016), [job UI](https://github.com/siwakon8285/VRA-Platform/actions/runs/36684984462/job/109788657016), [image release](https://github.com/actions/runner-images/releases/tag/ubuntu24/20260920.314), [tag commit API](https://api.github.com/repos/actions/runner-images/git/ref/tags/ubuntu24%2F20260920.314), [commit-addressed software README](https://raw.githubusercontent.com/actions/runner-images/e75633902841aa5479c759492b73409e6d317f12/images/ubuntu/Ubuntu2404-Readme.md).

### keycloak_test

- Purpose: Independent TEST-only OIDC issuer fixture; not a production IdP selection
- Artifact: `quay.io/keycloak/keycloak:26.7.4`
- Execution location: Isolated TEST containers on local Apple Silicon linux/arm64 and hosted CI linux/amd64; no image was pulled or started in Phase 0
- Production artifact inclusion: **NO**
- License/source project: {'license': 'Apache-2.0', 'license_response': {'response_bytes': 11358, 'response_sha256': 'cfc7749b96f63bd31c3c42b5c471bf756814053e847c10f3eb003417bc523d30', 'url': 'https://raw.githubusercontent.com/keycloak/keycloak/aa9fe3fba0c6cd5770f19a49378c55f4378cf544/LICENSE.txt'}, 'project': 'https://github.com/keycloak/keycloak'}
- Verification: Anonymous HTTPS to the project-advertised registry; compute SHA-256 of raw OCI index, each required platform manifest and config blob; compare to registry Docker-Content-Digest/index/config descriptors. Read-only metadata fetches only; independently re-fetch exact digests before future pull/run and assert the running version then.
- Disposition: **VERIFIED PIN**
- STOP: STOP before later execution if index/selected-platform manifest or config hash differs, required platform is absent, the exact digest is unavailable, or executed software version fails its runtime assertion. Do not fall back to a tag/latest/other version.
- Bound detailed record: `validation/poc-04/tooling/image-pins.json` JSON pointer `/records/0`.
- Primary provenance: ['https://github.com/keycloak/keycloak', 'https://www.keycloak.org/downloads', 'https://www.keycloak.org/2026/09/keycloak-2674-released', 'https://www.keycloak.org/getting-started/getting-started-docker']

### postgresql_test

- Purpose: Disposable PostgreSQL TEST database and native ACL/logging evidence
- Artifact: `docker.io/library/postgres:17.11`
- Execution location: Isolated TEST containers on local Apple Silicon linux/arm64 and hosted CI linux/amd64; no image was pulled or started in Phase 0
- Production artifact inclusion: **NO**
- License/source project: {'license': 'PostgreSQL server license; docker-library image scripts MIT; constituent licenses apply', 'license_response': {'response_bytes': 1084, 'response_sha256': '87ffd2c45e3f90cfa3407b5c40ef8333e87c3e875e4895f8b64df758198deafc', 'url': 'https://raw.githubusercontent.com/docker-library/postgres/2603e26e245e558218728ee14e0a42dcb020dc7f/LICENSE'}, 'project': 'https://github.com/docker-library/postgres'}
- Verification: Anonymous HTTPS to the project-advertised registry; compute SHA-256 of raw OCI index, each required platform manifest and config blob; compare to registry Docker-Content-Digest/index/config descriptors. Read-only metadata fetches only; independently re-fetch exact digests before future pull/run and assert the running version then.
- Disposition: **VERIFIED PIN**
- STOP: STOP before later execution if index/selected-platform manifest or config hash differs, required platform is absent, the exact digest is unavailable, or executed software version fails its runtime assertion. Do not fall back to a tag/latest/other version.
- Bound detailed record: `validation/poc-04/tooling/image-pins.json` JSON pointer `/records/1`.
- Primary provenance: ['https://github.com/docker-library/postgres', 'https://hub.docker.com/_/postgres', 'https://www.postgresql.org/docs/release/17.11/', 'https://raw.githubusercontent.com/docker-library/official-images/56d7550006f0cbcd5d7ef874c6f43c0ebc8aa43b/library/postgres', 'https://raw.githubusercontent.com/docker-library/postgres/2603e26e245e558218728ee14e0a42dcb020dc7f/17/trixie/Dockerfile']

### testcontainers_java

- Purpose: Existing real PostgreSQL/Testcontainers test harness; Phase-0 source/byte provenance only
- Artifact: `org.testcontainers:testcontainers-bom:2.0.5 and the existing core/postgresql/junit-jupiter modules`
- Execution location: Gradle TEST compile/runtime only, local and hosted CI
- Production artifact inclusion: **NO**
- License/source project: {'license': 'MIT', 'license_response': {'response_bytes': 1085, 'response_sha256': '2159220a6d068db4dc329a5f9f1f4670abdfbe225942a5e46c7330ffa98f89d4', 'url': 'https://raw.githubusercontent.com/testcontainers/testcontainers-java/5c448202ac69d073f746433d3e79f6a2bf0ec585/LICENSE'}, 'project': 'https://github.com/testcontainers/testcontainers-java'}
- Verification: Download the exact Maven Central BOM/JAR/POM bytes to memory, compute SHA-256, and match each publisher checksum sidecar; verify Spring Boot 4.1.1 BOM selects 2.0.5. Future Gradle resolution must reproduce these version/byte checks and source-bound transitive graph. No Gradle build/dependency installation was performed here.
- Disposition: **VERIFIED PIN**
- STOP: STOP before test execution if BOM/modules resolve to another version or artifact hashes differ; do not silently upgrade or use a mirror with different bytes. Transitive graph and container helper image verification remain later evidence obligations.
- Bound detailed record: `validation/poc-04/tooling/image-pins.json` JSON pointer `/records/2`.
- Primary provenance: ['https://java.testcontainers.org/', 'https://github.com/testcontainers/testcontainers-java/releases/tag/2.0.5', 'https://repo.maven.apache.org/maven2/org/testcontainers/']
- Exact artifact: `org.testcontainers:testcontainers-bom:2.0.5`; SHA-256 `bbba1376021fbf4e14663a61f10cd1aff14a5d74fb55f1a14be6fd6de83d7eef`; primary download https://repo.maven.apache.org/maven2/org/testcontainers/testcontainers-bom/2.0.5/testcontainers-bom-2.0.5.pom.
- Exact artifact: `org.testcontainers:testcontainers:2.0.5`; SHA-256 `0466f481343d5f350a91274cd7bf984308cbaf90d706247fd1cf4b1a8010c2e1`; primary download https://repo.maven.apache.org/maven2/org/testcontainers/testcontainers/2.0.5/testcontainers-2.0.5.jar.
- Exact artifact: `org.testcontainers:testcontainers-postgresql:2.0.5`; SHA-256 `8dbfb2fda977eb813ed1188ce02f098b8b634910eeab220568d54d6bd5b7ce9f`; primary download https://repo.maven.apache.org/maven2/org/testcontainers/testcontainers-postgresql/2.0.5/testcontainers-postgresql-2.0.5.jar.
- Exact artifact: `org.testcontainers:testcontainers-junit-jupiter:2.0.5`; SHA-256 `d66eb7f257a85833a8cc973e3814d740967d40a7db1a0de0040653c6ed236748`; primary download https://repo.maven.apache.org/maven2/org/testcontainers/testcontainers-junit-jupiter/2.0.5/testcontainers-junit-jupiter-2.0.5.jar.

### testcontainers_ryuk

- Purpose: Existing Testcontainers default resource cleanup helper
- Artifact: `docker.io/testcontainers/ryuk:0.14.0`
- Execution location: Dedicated disposable TEST Docker host only, local linux/arm64 and hosted linux/amd64; no container run/pull occurred
- Production artifact inclusion: **NO**
- License/source project: {'license': 'MIT', 'license_response': {'response_bytes': 1085, 'response_sha256': 'c6c6340d7962d2d1239a0bd8c43d48b2a7cfe377de2fad6ad72588903cd68a0d', 'url': 'https://raw.githubusercontent.com/testcontainers/moby-ryuk/b3726afd6cc2c36628abcc08e9cabac43f587384/LICENSE'}, 'project': 'https://github.com/testcontainers/moby-ryuk'}
- Verification: Read exact default from immutable Testcontainers 2.0.5 source commit; fetch named project/Official Image OCI index, required platform manifests and configs over HTTPS to memory; SHA-256 each response and compare descriptors/Docker-Content-Digest. Future external execution must apply digest substitution before pull and verify actual pulled content.
- Disposition: **VERIFIED PIN**
- STOP: STOP before any Testcontainers run if helper digest/platform cannot be verified or actual pull resolves another digest; do not disable required environment checks/resource cleanup merely to bypass the pin. Unselected optional helpers require their own reviewed digest before use.
- Bound detailed record: `validation/poc-04/tooling/image-pins.json` JSON pointer `/records/3`.
- Primary provenance: ['https://raw.githubusercontent.com/testcontainers/testcontainers-java/5c448202ac69d073f746433d3e79f6a2bf0ec585/core/src/main/java/org/testcontainers/utility/RyukContainer.java', 'https://java.testcontainers.org/features/configuration/', 'https://hub.docker.com/r/testcontainers/ryuk']

### testcontainers_tinyimage

- Purpose: Existing Testcontainers default Docker environment/startup check helper
- Artifact: `docker.io/library/alpine:3.17`
- Execution location: Dedicated disposable TEST Docker host only, local linux/arm64 and hosted linux/amd64; no container run/pull occurred
- Production artifact inclusion: **NO**
- License/source project: {'license': 'Docker build scripts MIT; Alpine constituent package licenses apply', 'license_ref_response': {'response_bytes': 361, 'response_sha256': 'b7c312dd9aacc644554ea783def9f66824a4a99b35f75c7649de5daa891f4c42', 'url': 'https://api.github.com/repos/alpinelinux/docker-alpine/git/ref/heads/master'}, 'license_response': {'response_bytes': 1070, 'response_sha256': 'cb0eb140cdf42808d7c948e5de07bcf435ee7eb314d9e757ca01bdca97109eb7', 'url': 'https://raw.githubusercontent.com/alpinelinux/docker-alpine/2c36a79aaaec3582d3b1961c9245b1a8a5a144dc/LICENSE'}, 'project': 'https://github.com/alpinelinux/docker-alpine'}
- Verification: Read exact default from immutable Testcontainers 2.0.5 source commit; fetch named project/Official Image OCI index, required platform manifests and configs over HTTPS to memory; SHA-256 each response and compare descriptors/Docker-Content-Digest. Future external execution must apply digest substitution before pull and verify actual pulled content.
- Disposition: **VERIFIED PIN**
- STOP: STOP before any Testcontainers run if helper digest/platform cannot be verified or actual pull resolves another digest; do not disable required environment checks/resource cleanup merely to bypass the pin. Unselected optional helpers require their own reviewed digest before use.
- Bound detailed record: `validation/poc-04/tooling/image-pins.json` JSON pointer `/records/4`.
- Primary provenance: ['https://raw.githubusercontent.com/testcontainers/testcontainers-java/5c448202ac69d073f746433d3e79f6a2bf0ec585/core/src/main/java/org/testcontainers/DockerClientFactory.java', 'https://java.testcontainers.org/features/configuration/', 'https://hub.docker.com/_/alpine']

### OSV-Scanner

- Purpose: Dependency vulnerability lookup of source-bound lockfiles/SBOM, later Phase 10/S13.
- Artifact: `OSV-Scanner`
- Execution location: Separate TEST scanner job; linux/amd64 hosted CI; linux/arm64 containers or darwin/arm64 Apple Silicon local review. No production runtime inclusion.
- Production artifact inclusion: **NO**
- License/source project: {'license': 'Apache-2.0', 'project': 'https://github.com/google/osv-scanner'}
- Verification: Full checksum-manifest bytes and all three selected platform artifacts downloaded, hashed with Python hashlib.sha256, cross-checked with upstream manifest and API asset digests. No artifact installed; one local Gitleaks stdin smoke only. Before every later execution, recompute artifact hash and compare to this source-bound pin; mismatch or unavailable provenance is STOP.
- Disposition: **VERIFIED PIN**
- STOP: STOP dependent tool execution and Phase 0 closure if expected immutable checksum cannot be independently verified, if platform differs, or if download digest differs. Do not fall back to a tag, latest or package-manager upgrade.
- Bound detailed record: `validation/poc-04/tooling/scanner-pins.json` JSON pointer `/tools/0`.
- Primary provenance: ['https://api.github.com/repos/google/osv-scanner/releases/tags/v2.6.0', 'https://github.com/google/osv-scanner/releases/tag/v2.6.0', 'https://github.com/google/osv-scanner/blob/e840a6e8adb14b7777c78e26cfbf6e2abc1d1fc6/LICENSE']
- Exact artifact: `osv-scanner_linux_amd64`; SHA-256 `ca69b3d3cd08f889a49dc0a383122f71cc528b83803671df5fd874d97485b108`; primary download https://github.com/google/osv-scanner/releases/download/v2.6.0/osv-scanner_linux_amd64.
- Exact artifact: `osv-scanner_linux_arm64`; SHA-256 `2c71403eb443d05891c4f268c3ad771cf4f16e5443463fd7851ef8f454d3c7e4`; primary download https://github.com/google/osv-scanner/releases/download/v2.6.0/osv-scanner_linux_arm64.
- Exact artifact: `osv-scanner_darwin_arm64`; SHA-256 `98c460dcd37de25819babd757d04542045b6243113e209edcd4d89fedb0256b4`; primary download https://github.com/google/osv-scanner/releases/download/v2.6.0/osv-scanner_darwin_arm64.

### Gitleaks

- Purpose: Redacted secret scanning of tracked source, generated artifacts and evidence, later Phase 10/S13.
- Artifact: `Gitleaks`
- Execution location: Separate TEST scanner job; linux/amd64 hosted CI; linux/arm64 containers or darwin/arm64 Apple Silicon local review. No production runtime inclusion.
- Production artifact inclusion: **NO**
- License/source project: {'license': 'MIT', 'project': 'https://github.com/gitleaks/gitleaks'}
- Verification: Full checksum-manifest bytes and all three selected platform artifacts downloaded, hashed with Python hashlib.sha256, cross-checked with upstream manifest and API asset digests. No artifact installed; one local Gitleaks stdin smoke only. Before every later execution, recompute artifact hash and compare to this source-bound pin; mismatch or unavailable provenance is STOP.
- Disposition: **VERIFIED PIN**
- STOP: STOP dependent tool execution and Phase 0 closure if expected immutable checksum cannot be independently verified, if platform differs, or if download digest differs. Do not fall back to a tag, latest or package-manager upgrade.
- Bound detailed record: `validation/poc-04/tooling/scanner-pins.json` JSON pointer `/tools/1`.
- Primary provenance: ['https://api.github.com/repos/gitleaks/gitleaks/releases/tags/v8.30.1', 'https://github.com/gitleaks/gitleaks/releases/tag/v8.30.1', 'https://github.com/gitleaks/gitleaks/blob/83d9cd684c87d95d656c1458ef04895a7f1cbd8e/LICENSE']
- Exact artifact: `gitleaks_8.30.1_linux_x64.tar.gz`; SHA-256 `551f6fc83ea457d62a0d98237cbad105af8d557003051f41f3e7ca7b3f2470eb`; primary download https://github.com/gitleaks/gitleaks/releases/download/v8.30.1/gitleaks_8.30.1_linux_x64.tar.gz.
- Exact artifact: `gitleaks_8.30.1_linux_arm64.tar.gz`; SHA-256 `e4a487ee7ccd7d3a7f7ec08657610aa3606637dab924210b3aee62570fb4b080`; primary download https://github.com/gitleaks/gitleaks/releases/download/v8.30.1/gitleaks_8.30.1_linux_arm64.tar.gz.
- Exact artifact: `gitleaks_8.30.1_darwin_arm64.tar.gz`; SHA-256 `b40ab0ae55c505963e365f271a8d3846efbc170aa17f2607f13df610a9aeb6a5`; primary download https://github.com/gitleaks/gitleaks/releases/download/v8.30.1/gitleaks_8.30.1_darwin_arm64.tar.gz.

### Syft

- Purpose: Source-bound dependency graph/SBOM inventory, later Phase 10/S13.
- Artifact: `Syft`
- Execution location: Separate TEST scanner job; linux/amd64 hosted CI; linux/arm64 containers or darwin/arm64 Apple Silicon local review. No production runtime inclusion.
- Production artifact inclusion: **NO**
- License/source project: {'license': 'Apache-2.0', 'project': 'https://github.com/anchore/syft'}
- Verification: Full checksum-manifest bytes and all three selected platform artifacts downloaded, hashed with Python hashlib.sha256, cross-checked with upstream manifest and API asset digests. No artifact installed; one local Gitleaks stdin smoke only. Before every later execution, recompute artifact hash and compare to this source-bound pin; mismatch or unavailable provenance is STOP.
- Disposition: **VERIFIED PIN**
- STOP: STOP dependent tool execution and Phase 0 closure if expected immutable checksum cannot be independently verified, if platform differs, or if download digest differs. Do not fall back to a tag, latest or package-manager upgrade.
- Bound detailed record: `validation/poc-04/tooling/scanner-pins.json` JSON pointer `/tools/2`.
- Primary provenance: ['https://api.github.com/repos/anchore/syft/releases/tags/v1.52.0', 'https://github.com/anchore/syft/releases/tag/v1.52.0', 'https://github.com/anchore/syft/blob/02ba369d13b4248395b20a504eca94b0cab564d8/LICENSE']
- Exact artifact: `syft_1.52.0_linux_amd64.tar.gz`; SHA-256 `caeedb81fb0491615f1ebd1761e4145d41ee86dd2cc7bf80669f9f5ad9d6133d`; primary download https://github.com/anchore/syft/releases/download/v1.52.0/syft_1.52.0_linux_amd64.tar.gz.
- Exact artifact: `syft_1.52.0_linux_arm64.tar.gz`; SHA-256 `c46d5e4c28e12aa4c5becfaa343ef1c7f89045b6b895f2c21d471c62db09c706`; primary download https://github.com/anchore/syft/releases/download/v1.52.0/syft_1.52.0_linux_arm64.tar.gz.
- Exact artifact: `syft_1.52.0_darwin_arm64.tar.gz`; SHA-256 `014d561b6d13059124155f74a6c5a9a99501f5e209313638dd884f39eb418ee6`; primary download https://github.com/anchore/syft/releases/download/v1.52.0/syft_1.52.0_darwin_arm64.tar.gz.

### @usebruno/cli

- Purpose: TEST-only external black-box HTTP runner; no collection exists yet.
- Artifact: `@usebruno/cli@4.2.0`
- Execution location: Disposable TEST Linux arm64/amd64 container; npm ci consumes tooling/node-pin-lock/package-lock.json, not an unconstrained npm install.
- Production artifact inclusion: **NO**
- License/source project: {'license': 'MIT', 'project': 'git+https://github.com/usebruno/bruno.git'}
- Verification: Exact-version npm registry metadata fetched over HTTPS; tarball downloaded into bounded memory; recomputed SHA-256, SHA-512 SRI and SHA-1 all match registry metadata. Registry metadata identity is trusted; package signatures and any SLSA/Sigstore attestations were not cryptographically validated in Phase 0.
- Disposition: **VERIFIED PIN**
- STOP: STOP before use if exact artifact bytes, version, digest/integrity, platform or source binding differs; never fall back to a floating tag or host-installed tool.
- Bound detailed record: `validation/poc-04/tooling/web-python-pins.json` JSON pointer `/records/0`.
- Primary provenance: ['https://registry.npmjs.org/@usebruno%2fcli/4.2.0', 'https://registry.npmjs.org/@usebruno/cli/-/cli-4.2.0.tgz', 'https://github.com/usebruno/bruno/releases/tag/v4.2.0']

### @playwright/test

- Purpose: TEST-only real-browser evidence runner; no E2E suite exists yet.
- Artifact: `@playwright/test@1.63.0`
- Execution location: Disposable TEST Linux arm64/amd64 container; npm ci consumes tooling/node-pin-lock/package-lock.json, not an unconstrained npm install.
- Production artifact inclusion: **NO**
- License/source project: {'license': 'Apache-2.0', 'project': 'git+https://github.com/microsoft/playwright.git'}
- Verification: Exact-version npm registry metadata fetched over HTTPS; tarball downloaded into bounded memory; recomputed SHA-256, SHA-512 SRI and SHA-1 all match registry metadata. Registry metadata identity is trusted; package signatures and any SLSA/Sigstore attestations were not cryptographically validated in Phase 0.
- Disposition: **VERIFIED PIN**
- STOP: STOP before use if exact artifact bytes, version, digest/integrity, platform or source binding differs; never fall back to a floating tag or host-installed tool.
- Bound detailed record: `validation/poc-04/tooling/web-python-pins.json` JSON pointer `/records/1`.
- Primary provenance: ['https://registry.npmjs.org/@playwright%2ftest/1.63.0', 'https://registry.npmjs.org/@playwright/test/-/test-1.63.0.tgz', 'https://github.com/microsoft/playwright/tree/1b025d7e20a026371cd5f98ba0cdce48892737c8']

### playwright

- Purpose: Exact Playwright Test runner dependency.
- Artifact: `playwright@1.63.0`
- Execution location: Disposable TEST Linux arm64/amd64 container; npm ci consumes tooling/node-pin-lock/package-lock.json, not an unconstrained npm install.
- Production artifact inclusion: **NO**
- License/source project: {'license': 'Apache-2.0', 'project': 'git+https://github.com/microsoft/playwright.git'}
- Verification: Exact-version npm registry metadata fetched over HTTPS; tarball downloaded into bounded memory; recomputed SHA-256, SHA-512 SRI and SHA-1 all match registry metadata. Registry metadata identity is trusted; package signatures and any SLSA/Sigstore attestations were not cryptographically validated in Phase 0.
- Disposition: **VERIFIED PIN**
- STOP: STOP before use if exact artifact bytes, version, digest/integrity, platform or source binding differs; never fall back to a floating tag or host-installed tool.
- Bound detailed record: `validation/poc-04/tooling/web-python-pins.json` JSON pointer `/records/2`.
- Primary provenance: ['https://registry.npmjs.org/playwright/1.63.0', 'https://registry.npmjs.org/playwright/-/playwright-1.63.0.tgz', 'https://github.com/microsoft/playwright/tree/1b025d7e20a026371cd5f98ba0cdce48892737c8']

### playwright-core

- Purpose: Browser installer/driver and authoritative release browser metadata.
- Artifact: `playwright-core@1.63.0`
- Execution location: Disposable TEST Linux arm64/amd64 container; npm ci consumes tooling/node-pin-lock/package-lock.json, not an unconstrained npm install.
- Production artifact inclusion: **NO**
- License/source project: {'license': 'Apache-2.0', 'project': 'git+https://github.com/microsoft/playwright.git'}
- Verification: Exact-version npm registry metadata fetched over HTTPS; tarball downloaded into bounded memory; recomputed SHA-256, SHA-512 SRI and SHA-1 all match registry metadata. Registry metadata identity is trusted; package signatures and any SLSA/Sigstore attestations were not cryptographically validated in Phase 0.
- Disposition: **VERIFIED PIN**
- STOP: STOP before use if exact artifact bytes, version, digest/integrity, platform or source binding differs; never fall back to a floating tag or host-installed tool.
- Bound detailed record: `validation/poc-04/tooling/web-python-pins.json` JSON pointer `/records/3`.
- Primary provenance: ['https://registry.npmjs.org/playwright-core/1.63.0', 'https://registry.npmjs.org/playwright-core/-/playwright-core-1.63.0.tgz', 'https://github.com/microsoft/playwright/tree/1b025d7e20a026371cd5f98ba0cdce48892737c8']

### Python

- Purpose: TEST-only audit observer interpreter and TEST CA tool container
- Artifact: `docker.io/library/python:3.13.15-slim-bookworm`
- Execution location: Disposable TEST Linux arm64 container on Apple Silicon; Linux amd64 container on hosted CI. Native macOS browser/interpreter is not selected.
- Production artifact inclusion: **NO**
- License/source project: {'license': 'PSF-2.0 and container distribution licenses', 'project': 'https://github.com/docker-library/python'}
- Verification: Fetched registry OCI index, each selected platform manifest and config over primary registry HTTPS and independently recomputed every returned content digest. Docker Hub tags are readable references only; execution uses immutable index plus explicitly selected platform. Image artifact identity is verified; no container was started in Phase 0.
- Disposition: **VERIFIED PIN**
- STOP: STOP before use if exact artifact bytes, version, digest/integrity, platform or source binding differs; never fall back to a floating tag or host-installed tool.
- Bound detailed record: `validation/poc-04/tooling/web-python-pins.json` JSON pointer `/records/4`.
- Primary provenance: ['https://hub.docker.com/_/python', 'https://registry-1.docker.io/v2/library/python/manifests/sha256:2325bb286ec344af3e5898cc224b5844e2707ac6e26b1632516fd3edc84a5e26']

### Node.js

- Purpose: TEST-only npm/Bruno provenance and locked package execution runtime
- Artifact: `docker.io/library/node:24.19.0-bookworm-slim`
- Execution location: Disposable TEST Linux arm64 container on Apple Silicon; Linux amd64 container on hosted CI. Native macOS browser/interpreter is not selected.
- Production artifact inclusion: **NO**
- License/source project: {'license': 'MIT and container distribution licenses', 'project': 'https://github.com/nodejs/docker-node'}
- Verification: Fetched registry OCI index, each selected platform manifest and config over primary registry HTTPS and independently recomputed every returned content digest. Docker Hub tags are readable references only; execution uses immutable index plus explicitly selected platform. Image artifact identity is verified; no container was started in Phase 0.
- Disposition: **VERIFIED PIN**
- STOP: STOP before use if exact artifact bytes, version, digest/integrity, platform or source binding differs; never fall back to a floating tag or host-installed tool.
- Bound detailed record: `validation/poc-04/tooling/web-python-pins.json` JSON pointer `/records/5`.
- Primary provenance: ['https://hub.docker.com/_/node', 'https://registry-1.docker.io/v2/library/node/manifests/sha256:a9f5f7c91a432850b2a8a7797adf5eadb6c733ceed61167806cee7ea7fbc29df']

### Chromium browser bundle

- Purpose: TEST-only real browser bundle, installed only in later authorized disposable TEST job
- Artifact: `mcr.microsoft.com/playwright:v1.63.0-noble`
- Execution location: Disposable TEST Linux arm64 container on Apple Silicon; Linux amd64 container on hosted CI. Native macOS browser/interpreter is not selected.
- Production artifact inclusion: **NO**
- License/source project: {'license': 'Chromium BSD-style / Playwright Apache-2.0 and container distribution licenses', 'project': 'https://github.com/microsoft/playwright'}
- Verification: Fetched registry OCI index, each selected platform manifest and config over primary registry HTTPS and independently recomputed every returned content digest. Docker Hub tags are readable references only; execution uses immutable index plus explicitly selected platform. Image artifact identity is verified; no container was started in Phase 0.
- Disposition: **VERIFIED PIN**
- STOP: STOP before use if exact artifact bytes, version, digest/integrity, platform or source binding differs; never fall back to a floating tag or host-installed tool.
- Bound detailed record: `validation/poc-04/tooling/web-python-pins.json` JSON pointer `/records/6`.
- Primary provenance: ['https://playwright.dev/docs/docker', 'https://github.com/microsoft/playwright/blob/1b025d7e20a026371cd5f98ba0cdce48892737c8/utils/docker/Dockerfile.noble', 'https://mcr.microsoft.com/v2/playwright/manifests/sha256:eff16c30e6f3f4af0a03fa4b706120d5e9b0891c344a27d64559aff5900a4a27']

### pglast

- Purpose: TEST-only independent PostgreSQL audit-tamper observer SQL AST parser; no observer or SQL log parser implemented.
- Artifact: `pglast==7.18`
- Execution location: Pinned Python 3.13.15 Linux glibc arm64/amd64 TEST observer container; CPython313 non-free-threaded manylinux wheels only.
- Production artifact inclusion: **NO**
- License/source project: {'license': 'GPL-3.0-or-later; libpg_query includes PostgreSQL license/source', 'project': 'https://github.com/lelit/pglast'}
- Verification: PyPI version JSON and exact sdist/wheels downloaded into bounded memory; hashes recomputed; inspected sdist CHANGES.rst and libpg_query/pg_query.h; GitHub annotated v7.18 tag resolved to immutable commit.
- Disposition: **VERIFIED PIN**
- STOP: STOP before use if exact artifact bytes, version, digest/integrity, platform or source binding differs; never fall back to a floating tag or host-installed tool.
- Bound detailed record: `validation/poc-04/tooling/web-python-pins.json` JSON pointer `/records/7`.
- Primary provenance: ['https://pypi.org/pypi/pglast/7.18/json', 'https://pypi.org/project/pglast/7.18/', 'https://github.com/lelit/pglast/blob/8a2ea399c3d87ae68ad680ea52af02c9257be0af/CHANGES.rst']
- Exact artifact: `pglast-7.18-cp313-cp313-manylinux2014_aarch64.manylinux_2_17_aarch64.manylinux_2_28_aarch64.whl`; SHA-256 `a43295ccef3b7e9a75ab9b1f1c83d8128c3184c548840f26f12dd320d9c924df`; primary download https://files.pythonhosted.org/packages/5c/85/2da98ebfa5f78a477caef64952e46572437c7d39c8b57bf99d9367125fd0/pglast-7.18-cp313-cp313-manylinux2014_aarch64.manylinux_2_17_aarch64.manylinux_2_28_aarch64.whl.
- Exact artifact: `pglast-7.18-cp313-cp313-manylinux2014_x86_64.manylinux_2_17_x86_64.manylinux_2_28_x86_64.whl`; SHA-256 `6c3c4ca0d88e2f5dc9d261a1a718be02426f14758c13b1665c431f756e6e5f1e`; primary download https://files.pythonhosted.org/packages/8d/70/713ac4030e8a63f32e4f5d43be6161cd7bb3ee1aa69018a330b5ae30a883/pglast-7.18-cp313-cp313-manylinux2014_x86_64.manylinux_2_17_x86_64.manylinux_2_28_x86_64.whl.
- Exact artifact: `pglast-7.18.tar.gz`; SHA-256 `18b52362cea47c87025d031015232da555c70e34762a351633ae040db1ec3c01`; primary download https://files.pythonhosted.org/packages/e6/67/fdd5a7f256704a457039423f16bba3bfc14d52672e607217816738230125/pglast-7.18.tar.gz.

### OpenSSL TEST CA tool

- Purpose: Generate disposable TEST CA and SAN host certificates; no keys/certificates generated in Phase 0.
- Artifact: `OpenSSL TEST CA tool`
- Execution location: Same exact Python TEST image, chosen platform, invoked only in disposable per-run TEST workspace. No host OpenSSL/Homebrew/native build is execution authority.
- Production artifact inclusion: **NO**
- License/source project: {'license': 'Apache-2.0 for OpenSSL 3; image distribution/component licenses remain separately inspectable.', 'project': 'OpenSSL Project; Debian OpenSSL package; Docker Official Python image'}
- Verification: Downloaded every Python platform layer in bounded memory and recomputed compressed layer SHA-256; inspected final dpkg status records for openssl/libssl3 3.0.20-1~deb12u2; hashed /usr/bin/openssl bytes independently for each platform. Neither image nor binary was executed. Container digest binds library/config dependencies too.
- Disposition: **VERIFIED PIN**
- STOP: STOP before use if exact artifact bytes, version, digest/integrity, platform or source binding differs; never fall back to a floating tag or host-installed tool.
- Bound detailed record: `validation/poc-04/tooling/web-python-pins.json` JSON pointer `/records/8`.
- Primary provenance: ['https://hub.docker.com/_/python', 'https://github.com/docker-library/python/tree/688a0b86bb44289df16a363e9f41d90514c1a5f9/3.13/slim-bookworm', 'https://www.openssl.org/']

### psycopg

- Purpose: TEST-only PostgreSQL transport for independent audit-tamper observer; confers no database privilege or authorization authority.
- Artifact: `psycopg==3.3.6`
- Execution location: Pinned Python3.13.15 Linux glibc arm64/amd64 TEST observer container; production inclusion NO.
- Production artifact inclusion: **NO**
- License/source project: {'license': 'LGPL-3.0-only for Psycopg; vendored libraries have their own licenses represented in wheel SBOM/license files.', 'project': 'https://github.com/psycopg/psycopg'}
- Verification: Exact-version primary PyPI metadata and package payloads independently hashed; inspected wheel METADATA, native library content and embedded auditwheel SBOM; annotated upstream 3.3.6 tag resolved to immutable commit. Package signatures/build attestations not cryptographically verified.
- Disposition: **VERIFIED PIN**
- STOP: STOP before use if exact wheel SHA-256, package version, selected CPython313 Linux platform or source binding differs; no source build, system libpq fallback or floating package version.
- Bound detailed record: `validation/poc-04/tooling/web-python-pins.json` JSON pointer `/records/9`.
- Primary provenance: ['https://pypi.org/pypi/psycopg/3.3.6/json', 'https://pypi.org/project/psycopg/3.3.6/', 'https://github.com/psycopg/psycopg/tree/a67654d1e7afbf9b3a619557838f62de1c790e7c', 'https://www.psycopg.org/psycopg3/docs/basic/install.html']
- Exact artifact: `psycopg-3.3.6-py3-none-any.whl`; SHA-256 `a1db9f7148b06a28606767efaca51fa6f9398c5c0a3810519be69d7000bdb631`; primary download https://files.pythonhosted.org/packages/4e/de/748bd7609c71cae5d737f0ba9192f19329f70180ecda8fff3cac02c5abe3/psycopg-3.3.6-py3-none-any.whl.
- Exact artifact: `psycopg-3.3.6.tar.gz`; SHA-256 `c081f2250df751a943036e42db6df4571c66cd0aabe8291a7a506512b12007d2`; primary download https://files.pythonhosted.org/packages/76/26/3ea4ca5eaea1c0debcdf7ee7c1613fbe721dc27a03c461c0817ffd8a0601/psycopg-3.3.6.tar.gz.

### psycopg-binary

- Purpose: TEST-only PostgreSQL transport for independent audit-tamper observer; confers no database privilege or authorization authority.
- Artifact: `psycopg-binary==3.3.6`
- Execution location: Pinned Python3.13.15 Linux glibc arm64/amd64 TEST observer container; production inclusion NO.
- Production artifact inclusion: **NO**
- License/source project: {'license': 'LGPL-3.0-only for Psycopg; vendored libraries have their own licenses represented in wheel SBOM/license files.', 'project': 'https://github.com/psycopg/psycopg'}
- Verification: Exact-version primary PyPI metadata and package payloads independently hashed; inspected wheel METADATA, native library content and embedded auditwheel SBOM; annotated upstream 3.3.6 tag resolved to immutable commit. Package signatures/build attestations not cryptographically verified.
- Disposition: **VERIFIED PIN**
- STOP: STOP before use if exact wheel SHA-256, package version, selected CPython313 Linux platform or source binding differs; no source build, system libpq fallback or floating package version.
- Bound detailed record: `validation/poc-04/tooling/web-python-pins.json` JSON pointer `/records/10`.
- Primary provenance: ['https://pypi.org/pypi/psycopg-binary/3.3.6/json', 'https://pypi.org/project/psycopg-binary/3.3.6/', 'https://github.com/psycopg/psycopg/tree/a67654d1e7afbf9b3a619557838f62de1c790e7c', 'https://www.psycopg.org/psycopg3/docs/basic/install.html']
- Exact artifact: `psycopg_binary-3.3.6-cp313-cp313-manylinux2014_x86_64.manylinux_2_17_x86_64.whl`; SHA-256 `cec5ea900390897d0b46130f60bc2883bf19c314f9044235217c8be88b0ef269`; primary download https://files.pythonhosted.org/packages/de/b0/c6f8a0585a5dacbea74e130bcfc66629390e8f5bbc79d2a8e806e8952150/psycopg_binary-3.3.6-cp313-cp313-manylinux2014_x86_64.manylinux_2_17_x86_64.whl.
- Exact artifact: `psycopg_binary-3.3.6-cp313-cp313-manylinux_2_27_aarch64.manylinux_2_28_aarch64.whl`; SHA-256 `98c02090d88f2ebc0ec1e8da538f77d225ce0fffecf372aa39262e62a1b054ef`; primary download https://files.pythonhosted.org/packages/e2/fc/c3a7a8bbef7e945ec584ac61d460a612363ea398511cd0e220242b1d69f1/psycopg_binary-3.3.6-cp313-cp313-manylinux_2_27_aarch64.manylinux_2_28_aarch64.whl.

## Required later corroboration

- OCI image/platform/config/layer identity and actual runtime version must be checked before use. Chromium image identity is pinned; actual revision 1243/executable hash and genuine HTTPS/browser behavior remain unexecuted. Do not install from an unpinned rolling CDN.
- npm lock has 510 exact registry/integrity records; top selected package tarballs were downloaded/hashed. Future npm ci validates the entire graph; cryptographic registry attestations and vulnerability clearance are not claimed. No node_modules or functional suite exists.
- pglast 7.18 bundles PG17.7 parser: actual PostgreSQL17.11 classifier proof remains Phase2/S11. Psycopg3.3.6 binary wheels bundle libpq18.6, which does not change server17.11. Bundled-library hashes/SBOM and inherited Alpine3.17 remain S13 triage inputs.
- TEST OpenSSL3.0.20 comes from the exact Python image; both binary hashes are recorded. No TEST CA/private material generated. Current host OpenSSL is a different observed tool.
- Optional unselected Testcontainers-module helpers are NOT APPLICABLE until selected; pin before use. Ryuk has isolated TEST-daemon administration, never runtime/human authority.
- Hosted runner baseline observation is verified; every later/final hosted execution must independently capture its actual runner/image/Java observations. BRANCH_PLAN reconciliation remains CLOSED; Phase 0 CLOSED is recorded after independent Round-3 PASS, while separate Phase-1 authorization remains required. Final closure byte review remains pending. No S0/S13/S15 PASS is claimed.

## Review round 1 provenance hygiene

B0-1: both complete temporary signed JDK checksum redirect fields were removed. Stable canonical release/download/checksum URLs, version identities, expected checksums and safe response/content hashes remain. No partially redacted signed query is retained. The verifier rejects temporary signed authorization URL queries throughout existing Phase-0 baseline/tooling/scripts, including decoded JSON strings, without printing URL/query values. This is a provenance URL policy check, not a general secret scan or S13 PASS.

B0-2: CLOSED; exact hosted baseline observation recorded above; 25 verified records and 0 unresolved pins. G0-1: CLOSED after separately authorized BRANCH_PLAN reconciliation and independent document-byte review at historical pre-closure SHA-256 `b0c231dcfe1b36897dd81e9cb6c0040c1e0253132115b0d33f91fb5af96d1ad2`. B0-3: CLOSED by integrating the exact unstaged exception and regenerating source/check manifests from the actual worktree, now binding closure-candidate SHA-256 `24100225282f7ddae8b1d24cb097a9b38cde2842932334b32de6a7ab784df62b`. PHASE 0 CLOSED is recorded after Independent Phase-0 Review Round 3 PASS, archive SHA-256 `ae9b52c9d9683067a3d59fa7cb96b60709244cf27aa0a2c04b1a1980776a5e17`; blocking findings 0; critical unresolved pins 0. Phase 1 remains NOT AUTHORIZED / NOT STARTED; POC-04 / S0-S15 remain NOT VERIFIED.
