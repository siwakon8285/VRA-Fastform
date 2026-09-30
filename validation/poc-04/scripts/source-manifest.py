#!/usr/bin/env python3
"""Read-only, deterministic source binding. No environment provisioning."""

import argparse
import hashlib
import json
from pathlib import Path
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[3]
POC = "validation/poc-04/"
OUTPUTS = {
    POC + "tooling/source-manifest.json",
    POC + "tooling/phase0-checks.json",
    POC + "tooling/run-manifest.json",
}
EXCLUDED_PARTS = {".local", ".venv", "__pycache__", "node_modules", "build", "evidence"}
SECRET_SUFFIXES = {".key", ".pem", ".p12", ".pfx"}
FUTURE_PATHS = [POC + x for x in ("fixtures", "browser", "bruno", "blackbox", "db")]


def git(*args):
    return subprocess.check_output(["git", *args], cwd=ROOT)


def digest(data):
    return hashlib.sha256(data).hexdigest()


def manifest(require_clean=False):
    identity_before = [git("branch", "--show-current"), git("rev-parse", "HEAD"), git("rev-parse", "HEAD^{tree}")]
    status = git("status", "--porcelain=v1", "-uall").decode("utf-8").splitlines()
    if require_clean and status:
        raise ValueError("worktree is not clean; no clean-source claim permitted")
    tracked = set(git("ls-files", "--cached", "-z").decode("utf-8").split("\0")) - {""}
    untracked = set(git("ls-files", "--others", "--exclude-standard", "-z").decode("utf-8").split("\0")) - {""}
    candidates = tracked | {p for p in untracked if p.startswith(POC)}
    files, excluded = [], []
    for name in sorted(candidates):
        relative = Path(name)
        if name in OUTPUTS or (name.startswith(POC) and EXCLUDED_PARTS.intersection(relative.parts)):
            excluded.append(name)
            continue
        if name.startswith(POC) and (relative.suffix.lower() in SECRET_SUFFIXES or relative.name.startswith(".env")):
            raise ValueError("secret-material path in source scope; keep it outside repository source")
        path = ROOT / relative
        if path.is_symlink() or not path.is_file() or not path.resolve().is_relative_to(ROOT):
            raise ValueError("missing, nonregular or escaping source path: " + name)
        data = path.read_bytes()
        files.append({"path": name, "sha256": digest(data), "size_bytes": len(data), "mode": format(path.stat().st_mode & 0o777, "04o"), "tracked": name in tracked})
    for item in files:
        path = ROOT / item["path"]
        if path.is_symlink() or digest(path.read_bytes()) != item["sha256"] or format(path.stat().st_mode & 0o777, "04o") != item["mode"]:
            raise ValueError("source changed during collection: " + item["path"])
    identity_after = [git("branch", "--show-current"), git("rev-parse", "HEAD"), git("rev-parse", "HEAD^{tree}")]
    if identity_before != identity_after or status != git("status", "--porcelain=v1", "-uall").decode("utf-8").splitlines():
        raise ValueError("Git source/status changed during collection")
    content = [{k: f[k] for k in ("path", "sha256", "size_bytes", "mode")} for f in files]
    content_bytes = json.dumps(content, sort_keys=True, separators=(",", ":")).encode("utf-8")
    return {
        "schema_version": 1,
        "branch": identity_before[0].decode().strip(),
        "head": identity_before[1].decode().strip(),
        "tree": identity_before[2].decode().strip(),
        "worktree": {"clean": not status, "porcelain_v1": status},
        "content_sha256": digest(content_bytes),
        "content_hash_encoding": "UTF-8 canonical JSON array; sorted keys, separators comma/colon; sorted paths; no timestamp",
        "files": files,
        "excluded_generated_or_execution_paths": excluded,
        "future_test_paths": [{"path": p, "exists": (ROOT / p).exists()} for p in FUTURE_PATHS],
        "execution_metadata": "Run ID, database-instance ID and timestamps belong in a separate run manifest, not content hashes",
        "scope": "All tracked source plus nonignored POC-04 untracked support/TEST source; explicit generated outputs excluded to prevent recursive self-hashing",
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--require-clean", action="store_true")
    args = parser.parse_args()
    try:
        print(json.dumps(manifest(args.require_clean), indent=2, sort_keys=True))
    except (ValueError, OSError, subprocess.CalledProcessError) as error:
        print("SOURCE_BINDING_STOP: " + str(error), file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
