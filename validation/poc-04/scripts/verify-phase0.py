#!/usr/bin/env python3
"""Read-only Phase-0 closure checks. Exit 0 records closure, never S-gate PASS."""

import argparse
import hashlib
import html
import json
from pathlib import Path
import re
import subprocess
import sys
from urllib.parse import parse_qsl, unquote, urlsplit

ROOT = Path(__file__).resolve().parents[3]
POC = "validation/poc-04/"
BASE = "8243d62f50aed2dc9a8605d7f12f0e2b7aa86854"
BRANCH = "poc/04-security-auth"
GOVERNANCE_PATH = "docs/BRANCH_PLAN.md"
GOVERNANCE_SHA256 = "24100225282f7ddae8b1d24cb097a9b38cde2842932334b32de6a7ab784df62b"
INDEPENDENT_REVIEW = {
    "round": 3, "result": "PASS",
    "archive_sha256": "ae9b52c9d9683067a3d59fa7cb96b60709244cf27aa0a2c04b1a1980776a5e17",
}
REQUIRED_PINS = {
    "gradle-distribution", "gradle-wrapper-jar", "actions-checkout", "actions-setup-java",
    "java21-temurin", "hosted-runner", "keycloak_test", "postgresql_test", "testcontainers_java",
    "testcontainers_ryuk", "testcontainers_tinyimage", "OSV-Scanner", "Gitleaks", "Syft",
    "@usebruno/cli", "@playwright/test", "playwright", "playwright-core", "Python", "Node.js",
    "Chromium browser bundle", "pglast", "OpenSSL TEST CA tool", "psycopg", "psycopg-binary",
}
EXPECTED = {
    POC + "SHARED_SPEC.md": "c035e627c087b41cb4533b00f0bdaf8fa208a5b55881358cf1f85b6809b2ed2d",
    POC + "IMPLEMENTATION_PLAN.md": "c169ad62d947841ec08987d2561f16b75935168d99ea46a34143004d3da3d7c5",
    "docs/adr/ADR-004-human-identity-and-browser-session-authority.md": "860d14431ed905e18aa67a60eb16fe44660a4fe28f056d47cd57eb13de642899",
    "docs/adr/ADR-005-privileged-security-operations-and-protected-audit.md": "dd5933ca1adc930a5b8617b6f5b49f748081f7f9770dea87b761b3e937319f49",
    "docs/adr/ADR-006-synchronous-guarded-postgresql-capability-boundary.md": "3068fa468b1483788a3a59cab3b628b6b8a2cc0beee101a93e0111b55e18f892",
    "backend/migration/src/main/resources/db/migration/V1__inventory_reservation_foundation.sql": "d804cbe5153b9a904ae0747af55c8295079d4a0c3edb4a38145ab5199b5b59c4",
    "backend/migration/src/main/resources/db/migration/V2__inventory_reservation_idempotency.sql": "7bef750b59b7b77e8402f4f42c13c21c4e072092504b9f268bace9b72de8d2bb",
    "backend/migration/src/main/resources/db/migration/V3__outbox_recovery.sql": "9edbd15ffe08ee4edafa3f549856ee38a782485c54db9c73e69d4263e5ca26b4",
}


def git(*args):
    return subprocess.check_output(["git", *args], cwd=ROOT).decode().rstrip("\n")


def sha(path):
    return hashlib.sha256((ROOT / path).read_bytes()).hexdigest()


def check_reviewed_governance():
    """Allow only the exact authorized closure-candidate governance bytes."""
    if sha(GOVERNANCE_PATH) != GOVERNANCE_SHA256:
        raise ValueError("reviewed BRANCH_PLAN hash mismatch")
    text = (ROOT / GOVERNANCE_PATH).read_text(encoding="utf-8")
    if "**Current branch:** `" + BRANCH + "`" not in text.split("\n---", 1)[0]:
        raise ValueError("BRANCH_PLAN current branch marker mismatch")
    rows = [line for line in text.splitlines() if line.startswith("| 04 |")]
    if len(rows) != 1 or rows[0].split("|")[2].strip() != "`" + BRANCH + "`" or rows[0].split("|")[3].strip() != "**IN PROGRESS**":
        raise ValueError("BRANCH_PLAN row 04 status mismatch")
    heading = "## 17. Current Execution Pointer\n"
    if heading not in text:
        raise ValueError("BRANCH_PLAN execution pointer missing")
    section = text.split(heading, 1)[1].split("\n## 18.", 1)[0]
    markers = (
        "Current active:",
        "04 — " + BRANCH + "\nStatus: IN PROGRESS\nDepends on: POC-03 CLOSED",
        "Base: main@" + BASE,
        "Current implementation checkpoint: Phase 0 — CLOSED; Phase 1 authorization pending",
        "Phase 0 is CLOSED",
        "Independent Phase-0 Review Round 3 PASS",
        "Phase 1 is NOT AUTHORIZED /\nNOT STARTED",
        "POC-04 / S0-S15 remain NOT VERIFIED",
    )
    if any(marker not in section for marker in markers) or "POC-04 has not started" in text:
        raise ValueError("BRANCH_PLAN reconciled governance markers mismatch")
    return {"path": GOVERNANCE_PATH, "sha256": GOVERNANCE_SHA256,
            "status": "CLOSED / RECONCILED", "G0-1": "CLOSED",
            "required_markers_verified": True,
            "scope": "Exact authorized closure-candidate unstaged governance exception only; final byte review pending; no arbitrary tracked change or staged path permitted"}


def has_temporary_signed_url(value):
    """Reject temporary authorization queries without returning their values."""
    text = html.unescape(value.replace("\\/", "/"))
    for _ in range(3):
        for match in re.finditer(r"https?://[^\s<>\"'`]+", text, re.IGNORECASE):
            parsed = urlsplit(match.group())
            if not parsed.query:
                continue
            keys = [key.lower() for key, _ in parse_qsl(parsed.query, keep_blank_values=True)]
            if parsed.hostname == "release-assets.githubusercontent.com" or any(
                key in {"sig", "signature", "jwt", "token", "expires", "key-pair-id", "policy", "authorization"}
                or key.startswith(("x-amz-", "x-goog-"))
                or any(part in key for part in ("token", "signature", "jwt"))
                for key in keys
            ):
                return True
        decoded = unquote(text)
        if decoded == text:
            break
        text = decoded
    return False


def json_strings(value):
    if isinstance(value, dict):
        for key, item in value.items():
            yield key
            yield from json_strings(item)
    elif isinstance(value, list):
        for item in value:
            yield from json_strings(item)
    elif isinstance(value, str):
        yield value


def check_provenance_urls():
    """Inspect tracked and untracked Phase-0 artifacts; never echo a URL."""
    paths = [ROOT / POC / "PHASE0_BASELINE.md"]
    for directory in ("tooling", "scripts"):
        paths.extend(sorted((ROOT / POC / directory).rglob("*")))
    checked = 0
    for path in paths:
        if path.is_dir():
            continue
        if path.is_symlink() or not path.is_file():
            raise ValueError("nonregular Phase-0 artifact path")
        text = path.read_text(encoding="utf-8")
        values = [text]
        if path.suffix == ".json":
            values.extend(json_strings(json.loads(text)))
        if any(has_temporary_signed_url(value) for value in values):
            raise ValueError("temporary signed URL query material in Phase-0 artifact: " + str(path.relative_to(ROOT)))
        checked += 1
    return {"status": "VERIFIED", "artifacts_checked": checked,
            "scope": "Existing Phase-0 baseline/tooling/scripts, tracked and untracked; raw text plus decoded JSON strings",
            "failure_behavior": "PHASE0_STOP; offending path only, never URL/query values",
            "nonclaim": "URL-query policy check, not a general secret scan or S13 PASS"}


def verify(extra_expected_head=None):
    branch, head = git("branch", "--show-current"), git("rev-parse", "HEAD")
    if branch != BRANCH or head != BASE or (extra_expected_head is not None and head != extra_expected_head):
        raise ValueError("wrong Phase-0 branch/base")
    for ref in ("origin/main", "origin/" + BRANCH):
        if git("rev-parse", ref) != BASE:
            raise ValueError("remote tracking ref differs: " + ref)
    authority = []
    for path, expected in EXPECTED.items():
        actual = sha(path)
        if actual != expected:
            raise ValueError("controlling hash mismatch: " + path)
        authority.append({"path": path, "sha256": actual, "status": "VERIFIED"})
    if git("diff", "--cached", "--name-only"):
        raise ValueError("staged paths are not authorized")
    tracked_changes = git("diff", "--name-only").splitlines()
    if any(path != GOVERNANCE_PATH for path in tracked_changes):
        raise ValueError("tracked changes outside exact reviewed BRANCH_PLAN allowlist")
    reviewed_governance = check_reviewed_governance()
    changes = git("status", "--porcelain=v1", "-uall").splitlines()
    for line in changes:
        if line == " M " + GOVERNANCE_PATH:
            continue
        name = line[3:]
        if line[:2] != "??" or not (name == POC + "PHASE0_BASELINE.md" or name.startswith(POC + "tooling/") or name.startswith(POC + "scripts/")):
            raise ValueError("unexpected changed path: " + name)
    migration_dir = ROOT / "backend/migration/src/main/resources/db/migration"
    if sorted(p.name for p in migration_dir.iterdir()) != sorted(Path(p).name for p in EXPECTED if "/db/migration/" in p):
        raise ValueError("Phase-1 migration or unexpected migration path present")
    for suffix in ("fixtures", "browser", "bruno", "blackbox", "db"):
        if (ROOT / POC / suffix).exists():
            raise ValueError("Phase-1/later fixture implementation path present: " + suffix)
    provenance_url_check = check_provenance_urls()
    register = json.loads((ROOT / POC / "tooling/tool-manifest.json").read_text())
    records = register["tools"]
    ids = [x["id"] for x in records]
    if len(ids) != len(set(ids)) or set(register["required_ids"]) != REQUIRED_PINS or set(ids) != REQUIRED_PINS:
        raise ValueError("required pin missing or duplicate ID")
    for source in register["provenance_files"]:
        if sha(source["path"]) != source["sha256"]:
            raise ValueError("provenance record changed: " + source["path"])
    blockers = []
    for item in records:
        if item["status"] not in ("VERIFIED PIN", "PROPOSED PIN", "UNRESOLVED"):
            raise ValueError("invalid pin disposition")
        if item["status"] != "VERIFIED PIN" and item.get("critical_for_phase0_pin_closure", True):
            blockers.append("Provenance not established: " + item["id"])
    if sum(item["status"] == "VERIFIED PIN" for item in records) != 25:
        blockers.append("Phase-0 closure requires 25 VERIFIED / 0 UNRESOLVED / 0 PROPOSED pins")
    manifest = json.loads(subprocess.check_output([sys.executable, str(ROOT / POC / "scripts/source-manifest.py")], cwd=ROOT))
    mapped = {f["path"]: f["sha256"] for f in manifest["files"]}
    if any(mapped.get(path) != value for path, value in EXPECTED.items()):
        raise ValueError("source manifest misses controlling bytes")
    if mapped.get(GOVERNANCE_PATH) != GOVERNANCE_SHA256:
        raise ValueError("source manifest misses reviewed governance bytes")
    return {
        "schema_version": 1,
        "branch": branch,
        "head": head,
        "tree": git("rev-parse", "HEAD^{tree}"),
        "authority_and_migrations": authority,
        "reviewed_governance": reviewed_governance,
        "source_content_sha256": manifest["content_sha256"],
        "source_manifest_execution": "VERIFIED",
        "temporary_signed_url_check": provenance_url_check,
        "pins": [{"id": x["id"], "classification": "VERIFIED" if x["status"] == "VERIFIED PIN" else "UNRESOLVED"} for x in records],
        "phase1_code_or_migration_changes_present": False,
        "worktree_porcelain_v1": changes,
        "phase0_closed": not blockers,
        "phase0_status": "CLOSED" if not blockers else "NOT CLOSED",
        "independent_phase0_review": INDEPENDENT_REVIEW,
        "closure_findings": {"B0-1": "CLOSED", "B0-2": "CLOSED", "G0-1": "CLOSED", "B0-3": "CLOSED"},
        "blockers": blockers,
        "POC04": "NOT VERIFIED",
        "S0_S15": "NOT VERIFIED",
        "phase1": "NOT AUTHORIZED",
        "phase1_authorized": False,
        "phase1_started": False,
        "limitations": "Offline validation of source-bound provenance records and recording of user-supplied Round-3 acceptance; not fresh network re-verification, execution proof or independent review of these closure-candidate bytes",
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--expect-head", help="Additional restrictive assertion; cannot relax the fixed authorized base")
    args = parser.parse_args()
    try:
        result = verify(args.expect_head)
        print(json.dumps(result, indent=2, sort_keys=True))
        return 0 if result["phase0_closed"] else 2
    except (ValueError, KeyError, OSError, subprocess.CalledProcessError) as error:
        print("PHASE0_STOP: " + str(error), file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
