#!/usr/bin/env python3
"""One administrative disposable TEST bootstrap for local and GitHub Phase-1 CI."""
import argparse
from datetime import datetime, timezone
import hashlib
import importlib.util
import io
import json
import os
from pathlib import Path
import platform
import re
import shutil
import stat
import subprocess
import sys
import tarfile
import tempfile
import time
import urllib.error
import urllib.request
import uuid
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parents[3]
SCRIPTS = ROOT / 'validation/poc-04/scripts'
INIT = ROOT / 'validation/poc-04/test-support/runner.init.gradle'
TOOL_SHA = '5814b0ee6eb022acfb458b21b8a009592ea627c3d371cfc7b0963595c05892d8'
EXPECTED_COUNTS = {'migration:test': 4, 'migration:integrationTest': 20,
                   'runtime:test': 56, 'runtime:integrationTest': 113}
# A public identifier is exempt only in the two exact, frozen image metadata occurrences.
# Whole-file binding also rejects a known identifier accompanied by newly inserted secret material.
PUBLIC_VERIFICATION_KEY_ALLOWLIST = {
    '7169605F62C751356D054A26A821E680E5FA6305': {
        'local_path': 'validation/poc-04/tooling/web-python-pins.json',
        'local_sha256': 'bab639a206eac0cbf991cf077b60762e2574f67836955b1352995fd4f5afb632',
        'local_lines': (228, 259),
        'provenance': {
            'repository': 'docker-library/python',
            'revision': '688a0b86bb44289df16a363e9f41d90514c1a5f9',
            'path': '3.13/slim-bookworm/Dockerfile',
            'sha256': '9167a49bf538f7df8fb2a7f04c0a4ff2ad157a317ac2ae54e22c761e718f9b20',
            'purpose': 'public Python source signature verification identifier',
        },
    },
}


def classify_public_verification_identifier(value, observation, provenance):
    approved = PUBLIC_VERIFICATION_KEY_ALLOWLIST.get(value)
    if not approved or not isinstance(observation, dict) or provenance != approved['provenance']:
        return False
    expected_context = '            "GPG_KEY=' + value + '",'
    return (observation.get('rule') == 'generic-api-key' and
            observation.get('path') == approved['local_path'] and
            observation.get('file_sha256') == approved['local_sha256'] and
            observation.get('line') in approved['local_lines'] and
            observation.get('context') == expected_context)


def strict_json(raw):
    def unique_object(pairs):
        result = {}
        for key, value in pairs:
            if key in result:
                raise ValueError('Ambiguous JSON metadata key')
            result[key] = value
        return result
    return json.loads(raw, object_pairs_hook=unique_object)


def resolve_scan_observation(item, contexts):
    """One exact text-file origin; binaries, multiline matches and ambiguity stay unresolved."""
    start, end = item.get('StartLine'), item.get('EndLine')
    if not isinstance(start, int) or end != start:
        return None
    origins = [entry for entry in contexts if entry['start'] <= start <= entry['end']]
    if len(origins) != 1:
        return None
    origin = origins[0]
    line = start - origin['start'] + 1
    context = origin['lines'][line - 1].decode('utf-8', errors='strict')
    return {'path': origin['path'], 'file_sha256': origin['file_sha256'], 'line': line,
            'context': context, 'rule': item['RuleID'], 'origin_kind': 'TEXT_FILE'}


def metadata_pointer(document, pointer):
    value = document
    for part in pointer:
        value = value[part]
    return value


def approved_digest_contexts(current_bindings=(), repository_root=None):
    """Frozen reviewed records, plus only the two current-run source-binding documents."""
    root = ROOT if repository_root is None else Path(repository_root)
    catalog = strict_json((root / 'validation/poc-04/test-support/digest-metadata-contexts.json').read_text())
    require(catalog['schema_version'] == 1, 'Unknown digest metadata catalog schema')
    records = list(catalog['contexts'])
    for path, expected in current_bindings:
        # Callers supply the independently collected run binding, never an arbitrary scanned document.
        document = strict_json(path.read_text())
        require(document == expected and document['schema_version'] == 1,
                'Current-run source metadata differs from independently collected binding')
        fields = [(['content_sha256'], 'SOURCE_CONTENT_SHA256', str(path.relative_to(root))),
                  (['head'], 'GIT_COMMIT', document['head']),
                  (['tree'], 'GIT_TREE', document['tree'])]
        fields.extend((['files', index, 'sha256'], 'FILE_SHA256', entry['path'])
                      for index, entry in enumerate(document['files']))
        lines = path.read_text().splitlines()
        for pointer, kind, target in fields:
            value = metadata_pointer(document, pointer)
            pair = json.dumps(pointer[-1]) + ': ' + json.dumps(value)
            candidates = [index + 1 for index, line in enumerate(lines)
                          if line.strip().rstrip(',') == pair]
            if len(candidates) != 1:
                continue  # Duplicate/multiline representation is not classification authority.
            line = candidates[0]
            records.append({'path': str(path.relative_to(root)), 'document_sha256': sha(path),
                            'line': line, 'context_sha256': hashlib.sha256(lines[line - 1].encode()).hexdigest(),
                            'rule': 'generic-api-key', 'format': 'JSON_FIELD', 'pointer': pointer,
                            'value': value, 'origin_kind': kind, 'origin_target': target})
    return records


def classify_digest_metadata(value, observation, records, repository_root=None):
    """Require exact value/path/field/context AND independently resolved immutable authority."""
    root = ROOT if repository_root is None else Path(repository_root)
    if not isinstance(observation, dict) or observation.get('origin_kind') != 'TEXT_FILE':
        return None
    candidates = [record for record in records if
                  record['path'] == observation.get('path') and
                  record['document_sha256'] == observation.get('file_sha256') and
                  record['line'] == observation.get('line') and record['rule'] == observation.get('rule') and
                  record['context_sha256'] == hashlib.sha256(observation.get('context', '').encode()).hexdigest()]
    if len(candidates) != 1:
        return None
    record = candidates[0]
    canonical = record['value']
    normalization = 'NONE'
    if record['origin_kind'] not in ('GIT_COMMIT', 'GIT_TREE') and value != canonical:
        return None
    try:
        path = root / record['path']
        if path.is_symlink() or not path.resolve().is_relative_to(root) or sha(path) != record['document_sha256']:
            return None
        lines = path.read_text().splitlines()
        if lines[record['line'] - 1] != observation['context']:
            return None
        if record['format'] == 'JSON_FIELD':
            document = strict_json(path.read_text())
            if metadata_pointer(document, record['pointer']) != canonical:
                return None
            # No command/free-form string can impersonate an approved structured scalar field.
            pair = json.dumps(record['pointer'][-1]) + ': ' + json.dumps(canonical)
            if observation['context'].strip().rstrip(',') != pair:
                return None
            field = '/' + '/'.join(str(part).replace('~', '~0').replace('/', '~1') for part in record['pointer'])
        elif record['format'] == 'FROZEN_REPORT_HEAD':
            # Only two whole-file/whole-line-bound historical HEAD clauses; no generic Markdown rule.
            if record['field'] != 'HEAD' or re.findall(r'(?:^|\. )HEAD: ([0-9a-f]{40})\.',
                                                      observation['context']) != [canonical]:
                return None
            field = 'HEAD'
        else:
            return None
        kind, target = record['origin_kind'], record['origin_target']
        if kind == 'FILE_SHA256':
            resolved = root / target
            if resolved.is_symlink() or not resolved.is_file() or not resolved.resolve().is_relative_to(root):
                return None
            identity = sha(resolved)
            if identity != value:
                return None
            if record['pointer'][0] == 'files':
                entry = document['files'][record['pointer'][1]]
                if (entry['path'] != target or entry['size_bytes'] != resolved.stat().st_size or
                        entry['mode'] != format(resolved.stat().st_mode & 0o777, '04o')):
                    return None
        elif kind in ('GIT_COMMIT', 'GIT_TREE'):
            if not re.fullmatch('[0-9a-f]{40}', canonical) or target != canonical:
                return None
            expected_type = 'commit' if kind == 'GIT_COMMIT' else 'tree'
            object_type = (git('cat-file', '-t', canonical) if repository_root is None else
                           git('cat-file', '-t', canonical, repository_root=root))
            if object_type != expected_type:
                return None
            raw = subprocess.check_output(['git', '--no-optional-locks', 'cat-file', expected_type, canonical],
                                          cwd=root, env=isolated_git_environment())
            identity = hashlib.sha1((expected_type + ' ' + str(len(raw)) + '\0').encode() + raw).hexdigest()
            if identity != canonical:
                return None
            if value != canonical:
                # Only the two frozen HEAD sentences have an approved terminal period delimiter.
                # Resolve authority first; the raw scanner token never supplies origin authority.
                if (kind != 'GIT_COMMIT' or record['format'] != 'FROZEN_REPORT_HEAD' or
                        value != canonical + '.' or len(value.encode('utf-8')) != len(canonical.encode('ascii')) + 1):
                    return None
                normalization = 'EXACT_SINGLE_TRAILING_PERIOD'
        elif kind == 'SOURCE_CONTENT_SHA256':
            if target != record['path'] or record['pointer'] != ['content_sha256']:
                return None
            files = document['files']
            if len({entry['path'] for entry in files}) != len(files) or files != sorted(files, key=lambda entry: entry['path']):
                return None
            content = [{key: entry[key] for key in ('path', 'sha256', 'size_bytes', 'mode')} for entry in files]
            identity = hashlib.sha256(json.dumps(content, sort_keys=True, separators=(',', ':')).encode()).hexdigest()
            if identity != value:
                return None
        else:
            return None
        proof = {'finding_path': record['path'], 'metadata_field': field,
                'scanner_origin_kind': 'TEXT_FILE', 'scanner_origin_identity': record['document_sha256'],
                'context_sha256': record['context_sha256'], 'resolved_origin_kind': kind,
                'resolved_origin_target': target, 'resolved_origin_identity': identity}
        if kind in ('GIT_COMMIT', 'GIT_TREE'):
            proof.update({'raw_token_length': len(value.encode('utf-8')),
                          'raw_token_sha256': hashlib.sha256(value.encode('utf-8')).hexdigest(),
                          'canonical_identity_length': len(canonical.encode('ascii')),
                          'canonical_identity_sha256': hashlib.sha256(canonical.encode('ascii')).hexdigest(),
                          'normalization_applied': normalization})
        return proof
    except (ValueError, KeyError, IndexError, TypeError, OSError, subprocess.CalledProcessError):
        return None


def verified_public_identifier_provenance():
    proofs = {}
    for value, approved in PUBLIC_VERIFICATION_KEY_ALLOWLIST.items():
        proof = approved['provenance']
        url = 'https://raw.githubusercontent.com/' + proof['repository'] + '/' + proof['revision'] + '/' + proof['path']
        with urllib.request.urlopen(url, timeout=60) as response:
            raw = response.read()
        require(hashlib.sha256(raw).hexdigest() == proof['sha256'], 'Immutable public identifier provenance differs')
        lines = raw.decode('utf-8').splitlines()
        require('ENV GPG_KEY ' + value in lines and
                any('--recv-keys "$GPG_KEY"' in line for line in lines) and
                any('--verify python.tar.xz.asc python.tar.xz' in line for line in lines),
                'Public verification purpose not established')
        proofs[value] = dict(proof)
    return proofs


def require(condition, message):
    if not condition:
        raise RuntimeError(message)


def sha(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def write(path, value):
    Path(path).write_text(json.dumps(value, sort_keys=True, indent=2) + '\n')
    os.chmod(path, 0o600)


def acquire_pinned_scanner(artifact, version, support, evidence):
    """Retry transport only; publish no executable until all pinned integrity checks pass."""
    scanner = support / 'gitleaks'
    require(not scanner.exists(), 'Scanner location must be fresh; no pre-existing binary is trusted')
    audit = {'status': 'RUNNING', 'scanner_execution': 'NOT EXECUTED', 'version': version,
             'asset': artifact, 'timeout_seconds': 60, 'maximum_attempts': 3,
             'backoff_seconds': [1, 2], 'checksum_scope': 'release archive', 'attempts': []}
    partial = None
    staged = None
    try:
        for attempt in range(1, 4):
            row = {'attempt': attempt, 'timestamp_utc': datetime.now(timezone.utc).isoformat(),
                   'url_identity': artifact['url'], 'byte_count': 0, 'result_class': 'STARTED'}
            audit['attempts'].append(row)
            write(evidence, audit)
            # A fresh file for every attempt; nothing from a failed response is reused.
            with tempfile.NamedTemporaryFile(prefix='.gitleaks-download-', suffix='.tar.gz',
                                             dir=support, delete=False) as download:
                partial = Path(download.name)
                try:
                    with urllib.request.urlopen(artifact['url'], timeout=60) as response:
                        row['http_status'] = response.status
                        require(response.status == 200, 'Unexpected scanner artifact HTTP status')
                        final_url = response.geturl()
                        require(final_url.startswith('https://'), 'Non-HTTPS scanner artifact redirect')
                        # Redirect query strings can contain signed URLs; never emit them.
                        row['redirect_target_sha256'] = hashlib.sha256(final_url.encode()).hexdigest()
                        content_type = response.headers.get('Content-Type', '').split(';')[0].strip().lower()
                        row['content_type'] = content_type
                        require(not content_type.startswith('text/') and
                                content_type not in ('application/json', 'application/xml', 'application/xhtml+xml'),
                                'Invalid scanner artifact content type')
                        while True:
                            block = response.read(65536)
                            if not block:
                                break
                            download.write(block)
                            row['byte_count'] += len(block)
                        download.flush()
                        os.fsync(download.fileno())
                    row['result_class'] = 'TRANSPORT_SUCCESS'
                except Exception as failure:
                    # HTTPError is also a URLError; HTTP classification must take precedence.
                    if isinstance(failure, urllib.error.HTTPError):
                        row['http_status'] = failure.code
                        transient = failure.code in (500, 502, 503, 504)
                        row['result_class'] = 'TRANSIENT_HTTP_ERROR' if transient else 'NONRETRYABLE_HTTP_ERROR'
                        failure.close()
                    else:
                        cause = failure.reason if isinstance(failure, urllib.error.URLError) else failure
                        transient = isinstance(cause, (TimeoutError, ConnectionResetError))
                        row['result_class'] = ('TRANSIENT_TIMEOUT' if isinstance(cause, TimeoutError) else
                                               'TRANSIENT_CONNECTION_RESET' if isinstance(cause, ConnectionResetError) else
                                               'INTEGRITY_FAILURE' if isinstance(failure, RuntimeError) else
                                               'NONRETRYABLE_TRANSPORT_FAILURE')
                    row['retry_allowed'] = transient
                    write(evidence, audit)
                    partial.unlink()
                    partial = None
                    if not transient or attempt == 3:
                        raise RuntimeError('Pinned scanner acquisition failed: ' + row['result_class']) from None
                    time.sleep(attempt)
                    continue
            # Integrity failures occur outside the retry block and stop immediately.
            write(evidence, audit)
            observed = sha(partial)
            require(observed == artifact['expected_sha256'], 'Pinned Gitleaks archive differs')
            audit['archive_sha256'] = observed
            with tarfile.open(partial, mode='r:gz') as archive:
                members = archive.getmembers()
                require(len({member.name for member in members}) == len(members), 'Duplicate scanner archive member')
                require(all(not Path(member.name).is_absolute() and '..' not in Path(member.name).parts and
                            (member.isfile() or member.isdir()) for member in members), 'Unsafe scanner archive structure')
                executables = [member for member in members if member.name == 'gitleaks']
                require(len(executables) == 1 and executables[0].isfile() and executables[0].size > 0,
                        'Gitleaks archive executable differs')
                with tempfile.NamedTemporaryFile(prefix='.gitleaks-executable-', dir=support, delete=False) as binary:
                    staged = Path(binary.name)
                    with archive.extractfile(executables[0]) as source:
                        shutil.copyfileobj(source, binary)
                    binary.flush()
                    os.fsync(binary.fileno())
            staged.chmod(0o700)
            binary_sha256 = sha(staged)
            checked = subprocess.run([str(staged), 'version'], capture_output=True, text=True, timeout=10)
            require(checked.returncode == 0 and checked.stdout.strip() == version, 'Pinned Gitleaks executable version differs')
            os.replace(staged, scanner)
            staged = None
            audit.update({'status': 'PASS', 'archive_validation': 'PASS', 'executable_version': version,
                          'executable_sha256': binary_sha256,
                          'executable_identity_basis': 'exact member of checksum-verified pinned archive',
                          'atomic_publication': 'PASS'})
            write(evidence, audit)
            return scanner
        raise RuntimeError('Pinned scanner attempts exhausted')
    except Exception as failure:
        audit['status'] = 'FAIL'
        audit['failure_class'] = type(failure).__name__
        write(evidence, audit)
        raise RuntimeError(str(failure) if isinstance(failure, RuntimeError) else
                           'Pinned scanner validation failed: ' + type(failure).__name__) from None
    finally:
        for path in (partial, staged):
            if path is not None:
                path.unlink(missing_ok=True)
        if audit['status'] == 'FAIL':
            scanner.unlink(missing_ok=True)


def isolated_git_environment():
    environment = dict(os.environ)
    for name in ('GIT_DIR', 'GIT_WORK_TREE', 'GIT_INDEX_FILE', 'GIT_COMMON_DIR',
                 'GIT_OBJECT_DIRECTORY', 'GIT_ALTERNATE_OBJECT_DIRECTORIES'):
        environment.pop(name, None)
    environment['GIT_OPTIONAL_LOCKS'] = '0'
    return environment


def git(*args, repository_root=None):
    root = ROOT if repository_root is None else Path(repository_root)
    return subprocess.check_output(['git', '--no-optional-locks', *args], cwd=root,
                                   env=isolated_git_environment()).decode().strip()


def binding(repository_root=None):
    root = ROOT if repository_root is None else Path(repository_root)
    spec = importlib.util.spec_from_file_location('source_manifest', root / 'validation/poc-04/scripts/source-manifest.py')
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    module.git = lambda *args: subprocess.check_output(['git', '--no-optional-locks', *args],
                                                      cwd=root, env=isolated_git_environment())
    result = module.manifest()
    files = {entry['path']: entry for entry in result['files']}
    # Same canonical scope as the reviewed migration fixture, including candidate backend source.
    for name in module.git('ls-files', '--others', '--exclude-standard', '-z').decode().split('\0'):
        if not name.startswith('backend/') or module.EXCLUDED_PARTS.intersection(Path(name).parts):
            continue
        path = root / name
        require(not path.is_symlink() and path.is_file() and path.resolve().is_relative_to(root),
                'Invalid candidate backend source')
        require(path.suffix.lower() not in module.SECRET_SUFFIXES and not path.name.startswith('.env'),
                'Secret-material path in backend source scope')
        files[name] = {'path': name, 'sha256': sha(path), 'size_bytes': path.stat().st_size,
                       'mode': format(path.stat().st_mode & 0o777, '04o'), 'tracked': False}
    result['files'] = [files[name] for name in sorted(files)]
    content = [{key: entry[key] for key in ('path', 'sha256', 'size_bytes', 'mode')}
               for entry in result['files']]
    result['content_sha256'] = hashlib.sha256(json.dumps(
        content, sort_keys=True, separators=(',', ':')).encode()).hexdigest()
    for entry in result['files']:
        path = root / entry['path']
        require(sha(path) == entry['sha256'] and format(path.stat().st_mode & 0o777, '04o') == entry['mode'],
                'Source changed during binding')
    require(git('rev-parse', 'HEAD', repository_root=root) == result['head'], 'HEAD changed during binding')
    return result


# Historical authority is an exact prior binding, not a directory exemption.
HISTORICAL_AUTHORITY = {
    'binding': {
        'path': 'validation/poc-04/evidence/phase1/hosted-ci-round5e-4b837db3-57bc-4b2d-bf6e-7cca3d864f78/FINAL_BINDING.json',
        'sha256': 'a2eacdb20e89ff9651890131ccc72debeb2e56709dddf14942c6d6b057aecd4a',
        'size': 112185, 'mode': '0644',
    },
    'linked_manifest_path': 'validation/poc-04/evidence/phase1/hosted-ci-round5e-4b837db3-57bc-4b2d-bf6e-7cca3d864f78/FROZEN_SCAN_MANIFEST.json',
}


CANDIDATE_SUPPORT_PATHS = (
    'validation/poc-04/scripts/phase1-ci.py',
    'validation/poc-04/test-support/digest-metadata-contexts.json',
    *('validation/poc-04/test-support/' + name for name in (
        'test_ci_support_boundary.py', 'test_digest_metadata_classifier.py',
        'test_git_period_normalization.py', 'test_public_verification_classifier.py',
        'test_scan_candidate_boundary.py', 'test_round5f_inventory_authority.py',
        'test_scanner_config_binding.py', 'test_gradle_resource_model.py',
        'test_postbuild_trust_domains.py')),
)
GENERATED_ROOTS = ('backend/.gradle', 'backend/build', 'backend/migration/build',
                   'backend/runtime/build', 'validation/poc-00/.gradle', 'validation/poc-00/build',
                   'validation/poc-00/java-candidate/build', 'validation/poc-00/kotlin-candidate/build')


EXPECTED_LOCAL_EPHEMERAL = (
    '.local/poc-01/gate6b-incompatible-schema-runtime.log',
    '.local/poc-01/gate6b-missing-schema-runtime.log',
    '.local/poc-01/liveness.json',
    '.local/poc-01/migrator_password',
    '.local/poc-01/postgres_admin_password',
    '.local/poc-01/readiness.json',
    '.local/poc-01/runtime-smoke-insufficient-response.json',
    '.local/poc-01/runtime-smoke-insufficient.json',
    '.local/poc-01/runtime-smoke-request.json',
    '.local/poc-01/runtime-smoke-success.headers',
    '.local/poc-01/runtime-smoke-success.json',
    '.local/poc-01/runtime-smoke.log',
    '.local/poc-01/runtime_password',
    '.local/secrets/db_password',
    'validation/poc-04/evidence/phase1/review-remediation-round1-2d744f02-8f9b-43a4-8ab7-723867aec905/focused-reason.log',
    'validation/poc-04/evidence/phase1/round1-collector-path-4a1aae34-4914-4392-986f-cc725e4d1500/focused-collector.log',
    'validation/poc-04/evidence/phase1/round1-compose-secrets-2af277e3-90ca-4767-9df9-bc40eaa66cca/database-proof.log',
    'validation/poc-04/evidence/phase1/round1-compose-secrets-2af277e3-90ca-4767-9df9-bc40eaa66cca/final-collector.log',
    'validation/poc-04/evidence/phase1/round1-compose-secrets-2af277e3-90ca-4767-9df9-bc40eaa66cca/final-teardown-negative.log',
    'validation/poc-04/evidence/phase1/round1-compose-secrets-2af277e3-90ca-4767-9df9-bc40eaa66cca/focused-collector.log',
    'validation/poc-04/evidence/phase1/round1-compose-secrets-2af277e3-90ca-4767-9df9-bc40eaa66cca/full-check-build.log',
    'validation/poc-04/evidence/phase1/round1-health-diagnosis-43eb52ce-5ef3-4393-9c95-36d858025ce2/focused-health-diagnosis.log',
    'validation/poc-04/evidence/phase1/round1-health-remediation-65334c18-11de-4755-acce-e22112c5f283/collector-proof.log',
    'validation/poc-04/evidence/phase1/round1-health-remediation-65334c18-11de-4755-acce-e22112c5f283/database-proof.log',
    'validation/poc-04/evidence/phase1/round1-health-remediation-65334c18-11de-4755-acce-e22112c5f283/focused-health.log',
    'validation/poc-04/evidence/phase1/round1-health-remediation-65334c18-11de-4755-acce-e22112c5f283/focused-teardown.log',
    'validation/poc-04/evidence/phase1/round1-health-remediation-65334c18-11de-4755-acce-e22112c5f283/full-check-build.log',
    'validation/poc-04/evidence/phase1/round1-locale-retry-df9a6e46-43fd-4e39-8243-e37704288852/focused-identity.log',
    'validation/poc-04/evidence/phase1/round1-locale-retry-df9a6e46-43fd-4e39-8243-e37704288852/focused-reason.log',
    'validation/poc-04/evidence/phase1/round1-restart-retry-6222c112-8ea7-4b28-8092-abc84f88270f/database-proof.log',
    'validation/poc-04/evidence/phase1/round1-restart-retry-6222c112-8ea7-4b28-8092-abc84f88270f/focused-identity.log',
    'validation/poc-04/evidence/phase1/round1-restart-retry-6222c112-8ea7-4b28-8092-abc84f88270f/focused-teardown.log',
    'validation/poc-04/evidence/phase1/round1-restart-retry-6222c112-8ea7-4b28-8092-abc84f88270f/full-check-build.log',
)


def canonical_sha(value):
    return hashlib.sha256(json.dumps(value, sort_keys=True, separators=(',', ':')).encode()).hexdigest()


def candidate_path(root, name):
    require(isinstance(name, str) and name and not name.startswith('/') and '\\' not in name and
            all(part not in ('', '.', '..') for part in name.split('/')), 'Unsafe scanner path')
    path = root
    for part in name.split('/'):
        path = path / part
        require(not path.is_symlink(), 'Scanner path contains a symlink: ' + name)
    require(path.is_relative_to(root), 'Scanner path escapes repository')
    return path


def scan_file_record(root, name):
    path = candidate_path(root, name)
    before = path.stat()
    require(stat.S_ISREG(before.st_mode), 'Scanner input is not a regular file: ' + name)
    data = path.read_bytes()
    after = path.stat()
    require((before.st_dev, before.st_ino, before.st_size, before.st_mtime_ns, before.st_mode) ==
            (after.st_dev, after.st_ino, after.st_size, after.st_mtime_ns, after.st_mode),
            'Scanner input changed during binding: ' + name)
    return {'path': name, 'sha256': hashlib.sha256(data).hexdigest(), 'size': len(data),
            'mode': format(after.st_mode & 0o777, '04o')}


def seal_scan_manifest(manifest):
    manifest['content_sha256'] = canonical_sha(manifest['files'])
    manifest['manifest_sha256'] = canonical_sha({key: value for key, value in manifest.items()
                                                if key != 'manifest_sha256'})
    return manifest


def freeze_scan_candidate(root, tracked, untracked, candidate_paths, preserved_files,
                          selected_evidence, current_evidence):
    """Account for every repository path before creating any current-run output."""
    root = Path(root).absolute()
    candidate_path(root, current_evidence)
    groups = [list(tracked), list(untracked), list(candidate_paths)]
    require(all(len(group) == len(set(group)) for group in groups), 'Duplicate repository classification')
    tracked, untracked, candidates = map(set, groups)
    require(not tracked & untracked and candidates <= untracked, 'Conflicting candidate classification')
    preserved_files, selected_evidence = list(preserved_files), list(selected_evidence)
    declarations = preserved_files + selected_evidence
    declared_names = [entry.get('path') for entry in declarations]
    require(len(declared_names) == len(set(declared_names)), 'Duplicate evidence classification')
    require(not set(declared_names) & (tracked | candidates), 'Evidence overrides source classification')
    require(set(declared_names) <= untracked, 'Evidence not present in repository inventory')
    for entry in declarations:
        require(set(entry) == {'path', 'sha256', 'size', 'mode'} and
                entry['path'].startswith('validation/poc-04/evidence/phase1/'),
                'Unsupported evidence selection record')
        require(scan_file_record(root, entry['path']) == entry, 'Evidence inventory binding differs')
    preserved = {entry['path']: entry for entry in preserved_files}
    selected = {entry['path']: entry for entry in selected_evidence}
    require(untracked == candidates | set(preserved) | set(selected), 'Unaccounted untracked scanner path')
    rows = []
    for name in sorted(tracked | candidates | set(selected)):
        require(name != current_evidence and not name.startswith(current_evidence + '/'),
                'Current evidence cannot enter frozen scanner input')
        row = scan_file_record(root, name)
        row['classification'] = ('TRACKED_CANDIDATE' if name in tracked else
                                 'CANDIDATE_SOURCE_SUPPORT' if name in candidates else 'SELECTED_EVIDENCE')
        rows.append(row)
    result = {'schema': 'poc04-scan-candidate-v1', 'current_evidence': current_evidence,
              'files': rows, 'preserved_evidence': sorted(preserved_files, key=lambda entry: entry['path']),
              'classifications': {'tracked_candidate': len(tracked), 'untracked_source_support': len(candidates),
                                  'selected_evidence': len(selected), 'preserved_evidence': len(preserved),
                                  'unexpected': 0}}
    return seal_scan_manifest(result)


def verify_scan_candidate(root, manifest):
    require(manifest['content_sha256'] == canonical_sha(manifest['files']) and
            manifest['manifest_sha256'] == canonical_sha({key: value for key, value in manifest.items()
                                                         if key != 'manifest_sha256'}),
            'Frozen scanner manifest hash differs')
    rows = manifest['files'] + manifest['preserved_evidence']
    names = [row['path'] for row in rows]
    require(len(names) == len(set(names)), 'Conflicting frozen scanner paths')
    for row in rows:
        require(scan_file_record(root, row['path']) ==
                {key: row[key] for key in ('path', 'sha256', 'size', 'mode')},
                'Candidate changed after manifest freeze: ' + row['path'])
    for row in manifest['files']:
        require(not row['path'].startswith(manifest['current_evidence'] + '/') and
                row['path'] != manifest['current_evidence'], 'Current output entered scanner manifest')


def materialize_scan_candidate(root, manifest, destination):
    verify_scan_candidate(root, manifest)
    require(not destination.exists() and not destination.is_symlink(), 'Scanner projection must be fresh')
    destination.mkdir(mode=0o700, parents=True)
    for row in manifest['files']:
        target = destination / row['path']
        target.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
        target.write_bytes(candidate_path(root, row['path']).read_bytes())
        target.chmod(int(row['mode'], 8))
        require(scan_file_record(destination, row['path']) ==
                {key: row[key] for key in ('path', 'sha256', 'size', 'mode')}, 'Scanner projection binding differs')
    verify_scan_candidate(root, manifest)


def domain_file_inventory(root):
    """Exact immutable-domain identity; this function is never used to authorize build output."""
    rows = []
    for directory, folders, files in os.walk(root, followlinks=False):
        for name in folders:
            require(not (Path(directory) / name).is_symlink(), 'Execution domain directory is a symlink')
        for name in files:
            relative = str((Path(directory) / name).relative_to(root))
            rows.append(scan_file_record(root, relative))
    return sorted(rows, key=lambda row: row['path'])


def execution_domain_root(root):
    path = Path(root).absolute()
    require(all(not node.is_symlink() for node in (path, *path.parents)),
            'Execution domain root contains a symlink')
    return path.resolve()


def private_git_identity(root):
    root = execution_domain_root(root)
    metadata = root / '.git'
    require(metadata.is_dir() and not metadata.is_symlink(), 'Isolated Git directory is required')
    require(not (metadata / 'objects/info/alternates').exists() and
            not (metadata / 'commondir').exists(), 'Git metadata references an external object/worktree authority')
    configured = subprocess.run(['git', '--no-optional-locks', 'config', '--get', 'core.worktree'],
                                cwd=root, env=isolated_git_environment(), capture_output=True)
    require(configured.returncode == 1, 'Git core.worktree escape is prohibited')
    top = Path(git('rev-parse', '--show-toplevel', repository_root=root)).resolve()
    directory = Path(git('rev-parse', '--absolute-git-dir', repository_root=root)).resolve()
    common = Path(git('rev-parse', '--git-common-dir', repository_root=root))
    common = (common if common.is_absolute() else root / common).resolve()
    require(top == root and directory == metadata and common == metadata,
            'Effective Git metadata/worktree authority escapes execution domain')
    return {'head': git('rev-parse', 'HEAD', repository_root=root),
            'head_tree': git('rev-parse', 'HEAD^{tree}', repository_root=root),
            'index_entries_sha256': hashlib.sha256(
                git('ls-files', '--stage', '-z', repository_root=root).encode()).hexdigest()}


def compare_execution_domain_candidates(scan_root, build_root, manifest):
    """Compare declared source rows only; generated build paths confer no source authority."""
    scan_root, build_root = execution_domain_root(scan_root), execution_domain_root(build_root)
    require(scan_root != build_root and not scan_root.is_relative_to(build_root) and
            not build_root.is_relative_to(scan_root), 'Execution domains overlap')
    require(manifest['content_sha256'] == canonical_sha(manifest['files']), 'Candidate content binding differs')
    for row in manifest['files']:
        expected = {key: row[key] for key in ('path', 'sha256', 'size', 'mode')}
        for root in (scan_root, build_root):
            require(scan_file_record(root, row['path']) == expected,
                    'Execution source differs from frozen candidate: ' + row['path'])
        left, right = (candidate_path(root, row['path']).stat() for root in (scan_root, build_root))
        require((left.st_dev, left.st_ino) != (right.st_dev, right.st_ino),
                'Immutable/build source files share writable storage')
    return {'immutable_snapshot_binding': manifest['content_sha256'],
            'build_workspace_prebuild_binding': manifest['content_sha256']}


def create_execution_domains(source_root, manifest, destination):
    """Materialize independent A/B trees from one frozen source inventory, never a workspace copy."""
    source_root = execution_domain_root(source_root)
    destination = execution_domain_root(destination)
    require(not destination.is_symlink() and not destination.exists(), 'Execution domain destination must be fresh')
    require(destination != source_root and not destination.is_relative_to(source_root) and
            not source_root.is_relative_to(destination), 'Execution domains must be outside source authority')
    verify_scan_candidate(source_root, manifest)
    git_identity = private_git_identity(source_root)
    require(all(git_identity[key] == manifest[key] for key in git_identity),
            'Source Git identity differs from frozen manifest')
    # Validate metadata before copying: no symlink, alternates, worktree pointer or shared inode.
    metadata = [row for row in domain_file_inventory(source_root / '.git')]
    destination.mkdir(mode=0o700, parents=True)
    scan_root, build_root = destination / 'immutable-candidate', destination / 'build-workspace'
    for root in (scan_root, build_root):
        materialize_scan_candidate(source_root, manifest, root)
        shutil.copytree(source_root / '.git', root / '.git', copy_function=shutil.copyfile)
        for row in metadata:
            copied = root / '.git' / row['path']
            copied.chmod(int(row['mode'], 8))
            require(scan_file_record(root / '.git', row['path']) == row,
                    'Copied isolated Git metadata differs')
            original = source_root / '.git' / row['path']
            require((original.stat().st_dev, original.stat().st_ino) !=
                    (copied.stat().st_dev, copied.stat().st_ino), 'Git metadata aliases source authority')
        require(private_git_identity(root) == git_identity, 'Copied Git identity differs')
    verify_scan_candidate(source_root, manifest)
    require(private_git_identity(source_root) == git_identity, 'Source Git state changed during materialization')
    equality = compare_execution_domain_candidates(scan_root, build_root, manifest)
    identity = {'root': str(scan_root), 'manifest_sha256': manifest['manifest_sha256'],
                'content_sha256': manifest['content_sha256'], 'git': git_identity,
                'files': domain_file_inventory(scan_root)}
    identity['identity_sha256'] = canonical_sha(identity)
    verify_frozen_scan_domain(scan_root, manifest, identity)
    return {'scan_root': scan_root, 'build_root': build_root, 'scan_identity': identity, **equality}


def verify_frozen_scan_domain(root, manifest, identity):
    """Original-input control checks ONLY immutable A, never mutable B or its ignored paths."""
    root = execution_domain_root(root)
    require(str(root) == identity['root'], 'Original scanner domain identity differs')
    require(identity['identity_sha256'] == canonical_sha({key: value for key, value in identity.items()
                                                         if key != 'identity_sha256'}),
            'Immutable scanner identity binding differs')
    require(manifest['manifest_sha256'] == identity['manifest_sha256'] and
            manifest['content_sha256'] == identity['content_sha256'] and
            manifest['content_sha256'] == canonical_sha(manifest['files']) and
            manifest['manifest_sha256'] == canonical_sha({key: value for key, value in manifest.items()
                                                         if key != 'manifest_sha256'}),
            'Immutable scanner manifest differs')
    require(domain_file_inventory(root) == identity['files'], 'Immutable scanner files changed or appeared')
    require(private_git_identity(root) == identity['git'], 'Immutable scanner Git identity changed')
    for row in manifest['files']:
        require(scan_file_record(root, row['path']) == {key: row[key] for key in ('path', 'sha256', 'size', 'mode')},
                'Immutable scanner candidate differs: ' + row['path'])
    return identity


BOOTJAR_PATHS = tuple('backend/' + project + '/build/libs/' + project + '-0.1.0-SNAPSHOT.jar'
                      for project in ('runtime', 'migration'))


def select_build_artifacts(build_root, manifest, run_id, producer=None):
    """Only the two existing workflow bootJar outputs may enter the expanded security input."""
    build_root = execution_domain_root(build_root)
    require(isinstance(producer, dict) and producer.get('name') == 'full-check-build' and
            producer.get('cwd') == str(build_root) and producer.get('exit_status') == 0 and
            isinstance(producer.get('output_sha256'), str) and
            re.fullmatch('[0-9a-f]{64}', producer['output_sha256']),
            'Selected artifacts require the exact successful bound build producer')
    rows = [{**scan_file_record(build_root, name), 'classification': 'GENERATED_BOOTJAR'}
            for name in BOOTJAR_PATHS]
    selection = {'schema': 'poc04-selected-bootjars-v1', 'run_id': run_id,
                 'build_root': str(build_root), 'source_manifest_sha256': manifest['manifest_sha256'],
                 'source_content_sha256': manifest['content_sha256'], 'producer': dict(producer), 'files': rows}
    selection['selection_sha256'] = canonical_sha(selection)
    verify_selected_build_artifacts(build_root, manifest, selection)
    return selection


def verify_selected_build_artifacts(build_root, manifest, selection):
    build_root = execution_domain_root(build_root)
    require(selection.get('schema') == 'poc04-selected-bootjars-v1' and
            selection.get('build_root') == str(build_root) and
            selection.get('source_manifest_sha256') == manifest['manifest_sha256'] and
            selection.get('source_content_sha256') == manifest['content_sha256'] and
            selection.get('selection_sha256') == canonical_sha({key: value for key, value in selection.items()
                                                               if key != 'selection_sha256'}),
            'Generated artifact selection binding differs')
    rows = selection['files']
    require(len(rows) == 2 and {row.get('path') for row in rows} == set(BOOTJAR_PATHS),
            'Expanded input must contain exactly the two contracted bootJars')
    producer = selection['producer']
    require(producer.get('name') == 'full-check-build' and producer.get('cwd') == str(build_root) and
            producer.get('exit_status') == 0 and isinstance(producer.get('output_sha256'), str) and
            re.fullmatch('[0-9a-f]{64}', producer['output_sha256']), 'Generated artifact producer differs')
    for row in rows:
        require(set(row) == {'path', 'sha256', 'size', 'mode', 'classification'} and
                row['classification'] == 'GENERATED_BOOTJAR' and
                scan_file_record(build_root, row['path']) == {key: row[key] for key in ('path', 'sha256', 'size', 'mode')},
                'Selected generated artifact identity differs')
    return selection


def historical_preservation_inventory(root, authority=None):
    """Resolve prior inventory authority before looking at observed historical files."""
    authority = HISTORICAL_AUTHORITY if authority is None else authority
    require(set(authority) == {'binding', 'linked_manifest_path'}, 'Unsupported historical authority')
    binding_record = authority['binding']
    binding_path = candidate_path(root, binding_record['path'])
    if not binding_path.exists():
        return {'files': [], 'origins': []}  # Clean hosted checkout has no local prior evidence.
    require(scan_file_record(root, binding_record['path']) == binding_record,
            'Historical bootstrap binding identity differs')
    document = strict_json(binding_path.read_text())
    require(document['schema'] == 'POC04_ROUND5E_FINAL_EVIDENCE_BINDING_V1' and
            document['binding_self_reference_excluded'] == binding_record['path'],
            'Unsupported historical binding schema')
    evidence = document['evidence_files']
    require(document['evidence_file_count'] == len(evidence) and
            canonical_sha(evidence) == document['evidence_binding_content_sha256'],
            'Historical evidence content binding differs')
    links = [row for row in evidence if row['path'] == authority['linked_manifest_path']]
    require(len(links) == 1, 'Missing or ambiguous historical inventory origin')
    link = links[0]
    require(scan_file_record(root, link['path']) == link, 'Historical linked inventory differs')
    frozen = strict_json(candidate_path(root, link['path']).read_text())
    require(frozen['schema'] == 'poc04-scan-candidate-v1' and
            frozen['content_sha256'] == canonical_sha(frozen['files']) and
            frozen['manifest_sha256'] == canonical_sha({k: v for k, v in frozen.items()
                                                       if k != 'manifest_sha256'}),
            'Historical inventory seal differs')
    records = evidence + frozen['preserved_evidence'] + [binding_record]
    names = []
    for row in records:
        require(set(row) == {'path', 'sha256', 'size', 'mode'} and
                type(row['size']) is int and row['size'] >= 0 and
                re.fullmatch('[0-9a-f]{64}', row['sha256']) and
                re.fullmatch('0[0-7]{3}', row['mode']) and
                row['path'].startswith('validation/poc-04/evidence/phase1/'),
                'Unsupported historical file inventory record')
        require(scan_file_record(root, row['path']) == row,
                'Historical file identity differs: ' + row['path'])
        names.append(row['path'])
    require(len(names) == len(set(names)), 'Ambiguous historical file authority')
    return {'files': sorted(records, key=lambda row: row['path']),
            'origins': [binding_record, link]}


def expected_generated_path(name):
    """Only exact default resource-copy relationships; output location is not authority."""
    match = re.fullmatch(r'(backend/(?:runtime|migration)|validation/poc-00/(?:java-candidate|kotlin-candidate))'
                         r'/build/resources/(main|test)/(.+)', name)
    if not match:
        return False
    source = match[1] + '/src/' + match[2] + '/resources/' + match[3]
    tracked = set(filter(None, git('ls-files', '-z').split('\0')))
    return source in tracked and scan_file_record(ROOT, name)['sha256'] == scan_file_record(ROOT, source)['sha256']


# This reviewed TEST-only script emits the evaluated model, never Gradle console prose.
# Only the root observational task may execute; it has no dependency on application,
# test, migration, resource-processing, or Docker tasks.
GRADLE_RESOURCE_QUERY_SOURCE = """import groovy.json.JsonOutput
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.language.jvm.tasks.ProcessResources

def modelPath = System.getProperty('vra.poc04.resourceModelOutput')
if (!modelPath) throw new GradleException('Missing restricted resource model output')
def describePath = { File file ->
    [path: file.absolutePath, canonical_path: file.canonicalPath]
}
gradle.projectsEvaluated {
    def root = gradle.rootProject
    def queryName = 'poc04EffectiveResourceModel'
    if (root.tasks.findByName(queryName) != null)
        throw new GradleException('Resource model query task identity collision')
    root.tasks.register(queryName) {
        doLast {
            def projects = root.allprojects.sort { it.path }.collect { project ->
                def sourceSets = project.extensions.findByType(SourceSetContainer)
                def sets = sourceSets == null ? [] : sourceSets.toList().sort { it.name }.collect { sourceSet ->
                    def processing = project.tasks.findByName(sourceSet.processResourcesTaskName)
                    if (!(processing instanceof ProcessResources))
                        throw new GradleException('Unsupported resource processing task')
                    [name: sourceSet.name,
                     resource_roots: sourceSet.resources.srcDirs.toList().sort { it.absolutePath }.collect(describePath),
                     resource_files: sourceSet.resources.asFileTree.files.toList().sort { it.absolutePath }.collect(describePath),
                     process_resource_files: processing.source.files.toList().sort { it.absolutePath }.collect(describePath),
                     output_directory: sourceSet.output.resourcesDir.absolutePath,
                     process_output_directory: processing.destinationDir.absolutePath,
                     includes: sourceSet.resources.includes.toList().sort(),
                     excludes: sourceSet.resources.excludes.toList().sort()]
                }
                [project_path: project.path, project_directory: project.projectDir.absolutePath,
                 build_file: project.buildFile.isFile() ? project.buildFile.absolutePath : null,
                 source_sets: sets]
            }
            def model = [schema_version: 1, gradle_version: gradle.gradleVersion,
                         build_root: root.projectDir.absolutePath, projects: projects]
            new File(modelPath).setText(JsonOutput.toJson(model), 'UTF-8')
        }
    }
    gradle.taskGraph.whenReady { graph ->
        if (graph.allTasks.size() != 1 || graph.allTasks[0].path != ':' + queryName)
            throw new GradleException('Resource model query may execute only its observational task')
    }
}
"""
GRADLE_RESOURCE_QUERY_SHA256 = hashlib.sha256(GRADLE_RESOURCE_QUERY_SOURCE.encode()).hexdigest()


def gradle_model_path(repository_root, value, label, build_root=None):
    require(isinstance(value, str) and value and '\\' not in value and
            Path(value).is_absolute() and all(part not in ('.', '..') for part in value.split('/')),
            'Malformed Gradle model path: ' + label)
    path = Path(value)
    require(str(path) == value, 'Noncanonical Gradle model path spelling: ' + label)
    repository_root = Path(repository_root).absolute()
    require(path.is_relative_to(repository_root), 'Gradle model path outside repository: ' + label)
    candidate_path(repository_root, str(path.relative_to(repository_root)))
    require(path.resolve() == path, 'Aliased Gradle model path: ' + label)
    if build_root is not None:
        require(path.is_relative_to(build_root), 'Gradle project path outside build: ' + label)
    return path


def validate_gradle_resource_model(raw, repository_root, build_root):
    """Validate the complete evaluated model. No DSL parsing or textual fallback."""
    try:
        model = strict_json(raw) if isinstance(raw, (str, bytes)) else raw
    except (ValueError, TypeError) as failure:
        raise RuntimeError('Malformed evaluated Gradle model JSON') from failure
    repository_root = Path(repository_root).absolute()
    build_root = gradle_model_path(repository_root, str(Path(build_root).absolute()), 'build root')
    require(isinstance(model, dict) and
            set(model) in ({'schema_version', 'gradle_version', 'build_root', 'projects'},
                           {'schema_version', 'gradle_version', 'build_root', 'projects', 'query_script_sha256'}),
            'Unsupported evaluated Gradle model fields')
    require(type(model['schema_version']) is int and model['schema_version'] == 1,
            'Unknown evaluated Gradle model schema')
    require(isinstance(model['gradle_version'], str) and
            re.fullmatch(r'[0-9]+\.[0-9]+(?:\.[0-9]+)?', model['gradle_version']),
            'Malformed evaluated Gradle version')
    require(model['build_root'] == str(build_root), 'Evaluated Gradle build identity differs')
    require(model.get('query_script_sha256', GRADLE_RESOURCE_QUERY_SHA256) == GRADLE_RESOURCE_QUERY_SHA256,
            'Evaluated Gradle query identity differs')
    require(isinstance(model['projects'], list) and model['projects'], 'Missing Gradle projects')
    normalized = []
    project_names, project_directories = set(), set()
    for project in model['projects']:
        require(isinstance(project, dict) and set(project) ==
                {'project_path', 'project_directory', 'build_file', 'source_sets'},
                'Unsupported evaluated Gradle project')
        identity = project['project_path']
        require(isinstance(identity, str) and re.fullmatch(r':(?:[A-Za-z0-9_-]+(?::[A-Za-z0-9_-]+)*)?', identity)
                and identity not in project_names, 'Missing or ambiguous Gradle project identity')
        directory = gradle_model_path(repository_root, project['project_directory'], 'project directory', build_root)
        require(directory.is_dir() and str(directory) not in project_directories,
                'Missing or ambiguous Gradle project directory')
        require(identity != ':' or directory == build_root, 'Gradle root project identity differs')
        project_names.add(identity)
        project_directories.add(str(directory))
        build_file = project['build_file']
        if build_file is not None:
            configuration = gradle_model_path(repository_root, build_file, 'build file', build_root)
            require(configuration.is_file() and configuration.parent == directory,
                    'Missing or unexpected Gradle build file')
        require(isinstance(project['source_sets'], list), 'Missing Gradle source sets')
        sets, names = [], set()
        for source_set in project['source_sets']:
            require(isinstance(source_set, dict) and set(source_set) ==
                    {'name', 'resource_roots', 'resource_files', 'process_resource_files', 'output_directory',
                     'process_output_directory', 'includes', 'excludes'}, 'Unsupported evaluated Gradle source set')
            name = source_set['name']
            require(isinstance(name, str) and re.fullmatch('[A-Za-z0-9_-]+', name) and name not in names,
                    'Missing or ambiguous Gradle source-set identity')
            names.add(name)
            roots = []
            for row in source_set['resource_roots'] if isinstance(source_set['resource_roots'], list) else [None]:
                require(isinstance(row, dict) and set(row) == {'path', 'canonical_path'},
                        'Malformed Gradle resource root')
                path = gradle_model_path(repository_root, row['path'], 'resource root')
                require(row['canonical_path'] == str(path) and
                        (not path.exists() or path.is_dir()), 'Gradle resource root identity differs')
                require(str(path) not in roots, 'Ambiguous canonicalized Gradle resource roots')
                roots.append(str(path))
            file_groups = {}
            for field in ('resource_files', 'process_resource_files'):
                require(isinstance(source_set[field], list), 'Missing effective Gradle resource membership')
                files = []
                for row in source_set[field]:
                    require(isinstance(row, dict) and set(row) == {'path', 'canonical_path'},
                            'Malformed effective Gradle resource member')
                    path = gradle_model_path(repository_root, row['path'], 'resource member')
                    require(row['canonical_path'] == str(path) and path.is_file() and
                            any(path.is_relative_to(Path(root)) for root in roots),
                            'Effective Gradle resource member lacks authoritative root')
                    require(str(path) not in files, 'Ambiguous effective Gradle resource member')
                    files.append(str(path))
                file_groups[field] = sorted(files)
            require(file_groups['resource_files'] == file_groups['process_resource_files'],
                    'Resource processing sources differ from evaluated source-set authority')
            output = gradle_model_path(repository_root, source_set['output_directory'], 'resource output', build_root)
            processing_output = gradle_model_path(repository_root, source_set['process_output_directory'],
                                                  'resource processing output', build_root)
            require(output == processing_output, 'Resource processing output differs from source-set output')
            filters = {}
            for field in ('includes', 'excludes'):
                patterns = source_set[field]
                require(isinstance(patterns, list) and all(isinstance(value, str) and value for value in patterns)
                        and len(patterns) == len(set(patterns)), 'Malformed Gradle resource filters')
                filters[field] = sorted(patterns)
            sets.append({'name': name,
                         'resource_roots': [{'path': root, 'canonical_path': root} for root in sorted(roots)],
                         **{field: [{'path': path, 'canonical_path': path} for path in files]
                            for field, files in file_groups.items()},
                         'output_directory': str(output), 'process_output_directory': str(processing_output),
                         **filters})
        normalized.append({'project_path': identity, 'project_directory': str(directory),
                           'build_file': build_file, 'source_sets': sorted(sets, key=lambda row: row['name'])})
    require(':' in project_names, 'Missing Gradle root project')
    return {'schema_version': 1, 'gradle_version': model['gradle_version'], 'build_root': str(build_root),
            'projects': sorted(normalized, key=lambda row: row['project_path']),
            'query_script_sha256': GRADLE_RESOURCE_QUERY_SHA256}


def query_gradle_resource_model(build_root, repository_root=None, evidence_directory=None):
    """Configure Gradle and run only the isolated observational task; failures stop."""
    repository_root = ROOT if repository_root is None else Path(repository_root).absolute()
    build_root = gradle_model_path(repository_root, str(Path(build_root).absolute()), 'query build root')
    wrapper = candidate_path(repository_root, str((build_root / 'gradlew').relative_to(repository_root)))
    require(wrapper.is_file(), 'Missing authoritative Gradle wrapper')
    temporary = Path(tempfile.mkdtemp(prefix='vra-poc04-resource-model-'))
    temporary.chmod(0o700)
    script, output, log = (temporary / name for name in ('resource-model.init.gradle', 'resource-model.json',
                                                        'resource-model-query.log'))
    success = False
    command = []
    try:
        script.write_text(GRADLE_RESOURCE_QUERY_SOURCE, encoding='utf-8')
        script.chmod(0o600)
        require(sha(script) == GRADLE_RESOURCE_QUERY_SHA256, 'Generated Gradle query script differs')
        command = [str(wrapper), '--no-daemon', '--console=plain', '--no-configuration-cache', '--no-problems-report',
                   '--project-cache-dir', str(temporary / 'project-cache'), '--init-script', str(script),
                   '-Dvra.poc04.resourceModelOutput=' + str(output), ':poc04EffectiveResourceModel']
        try:
            with log.open('w') as stream:
                result = subprocess.run(command, cwd=build_root, stdout=stream, stderr=subprocess.STDOUT,
                                        timeout=300)
            require(result.returncode == 0, 'Evaluated Gradle resource model query process failed; retained log: ' + str(log))
            require(output.is_file() and not output.is_symlink(), 'Missing evaluated Gradle resource model output')
            model = validate_gradle_resource_model(output.read_text(), repository_root, build_root)
        except subprocess.TimeoutExpired as failure:
            raise RuntimeError('Evaluated Gradle resource model query timed out; retained log: ' + str(log)) from failure
        success = True
        return model
    finally:
        for path in (output, log):
            if path.is_file():
                path.chmod(0o600)
        if evidence_directory is not None:
            evidence = Path(evidence_directory).absolute()
            require(not evidence.is_relative_to(repository_root) and not evidence.is_symlink(),
                    'Gradle query evidence must remain outside repository')
            evidence.mkdir(parents=True, exist_ok=True, mode=0o700)
            for source in (script, output, log):
                if source.is_file():
                    target = evidence / source.name
                    require(not target.exists(), 'Gradle query evidence must be fresh')
                    shutil.copyfile(source, target)
                    target.chmod(0o600)
            write(evidence / 'resource-model-query-identity.json', {
                'schema_version': 1, 'script_sha256': GRADLE_RESOURCE_QUERY_SHA256,
                'wrapper': str(wrapper.relative_to(repository_root)), 'wrapper_sha256': sha(wrapper),
                'command': command, 'working_directory': str(build_root),
                'configuration_model_only': True, 'query_status': 'PASS' if success else 'FAIL'})
        if success:
            shutil.rmtree(temporary)


def resolve_shared_resource(name, tracked=None, resource_model=None):
    """Resolve the evaluated resource model before comparing any observed bytes."""
    match = re.fullmatch(r'validation/poc-00/([A-Za-z0-9_-]+)/build/resources/(main|test)/(.+)', name)
    if not match:
        return None
    project_name, source_set_name, relative = match.groups()
    candidate_path(ROOT, relative)  # Validate spelling before joining a declared root.
    tracked = set(filter(None, git('ls-files', '-z').split('\0'))) if tracked is None else set(tracked)
    build_root = ROOT / 'validation/poc-00'
    resource_model = (query_gradle_resource_model(build_root) if resource_model is None else
                      validate_gradle_resource_model(resource_model, ROOT, build_root))
    projects = [project for project in resource_model['projects'] if project['project_path'] == ':' + project_name]
    require(len(projects) == 1, 'Missing or ambiguous evaluated shared-resource project')
    project = projects[0]
    source_sets = [entry for entry in project['source_sets'] if entry['name'] == source_set_name]
    require(len(source_sets) == 1, 'Missing or ambiguous evaluated shared-resource source set')
    source_set = source_sets[0]
    output = Path(source_set['output_directory'])
    destination_path = candidate_path(ROOT, name)
    require(destination_path == output / relative, 'Destination differs from evaluated Gradle resource output')
    settings = [str(path.relative_to(ROOT)) for path in (build_root / 'settings.gradle', build_root / 'settings.gradle.kts')
                if path.is_file()]
    configuration = project['build_file']
    require(len(settings) == 1 and configuration is not None, 'Missing or ambiguous authoritative Gradle configuration')
    configuration = str(Path(configuration).relative_to(ROOT))
    require(settings[0] in tracked and configuration in tracked, 'Untracked evaluated shared-resource configuration')
    configuration_files = [scan_file_record(ROOT, path) for path in (settings[0], configuration)]
    eligible = {row['canonical_path'] for row in source_set['resource_files']}
    matches = []
    for root in source_set['resource_roots']:
        origin_path = candidate_path(ROOT, str((Path(root['canonical_path']) / relative).relative_to(ROOT)))
        if origin_path.is_file() and str(origin_path) in eligible:
            matches.append((root['canonical_path'], origin_path))
    require(len(matches) == 1, 'Missing or ambiguous evaluated shared-resource origin')
    effective_root, origin_path = matches[0]
    origin = str(origin_path.relative_to(ROOT))
    require(origin in tracked, 'Untracked evaluated shared-resource origin')
    destination, authoritative = scan_file_record(ROOT, name), scan_file_record(ROOT, origin)
    require(destination['sha256'] == authoritative['sha256'] and destination['size'] == authoritative['size'],
            'Configured shared-resource bytes differ: ' + name)
    default_root = Path(project['project_directory']) / 'src' / source_set_name / 'resources'
    if Path(effective_root) == default_root:
        return None  # Exact evaluated default local copy; the local-copy adapter validates it too.
    return {'path': name, 'origin_kind': 'shared', 'configured_origin': origin,
            'project': project['project_path'], 'source_set': source_set_name,
            'effective_resource_root': str(Path(effective_root).relative_to(ROOT)), 'unique_match_count': len(matches),
            'destination_sha256': destination['sha256'], 'origin_sha256': authoritative['sha256'],
            'destination_size': destination['size'], 'origin_size': authoritative['size'],
            'destination_mode': destination['mode'], 'origin_mode': authoritative['mode'],
            'equality': True, 'classification': 'candidate/shared-resource',
            'configuration_files': configuration_files, 'gradle_version': resource_model['gradle_version'],
            'query_script_sha256': resource_model['query_script_sha256']}


def repository_scan_candidate(current_evidence):
    tracked = git('ls-files', '-z').split('\0')
    untracked = git('ls-files', '--others', '--exclude-standard', '-z').split('\0')
    ignored = git('ls-files', '--others', '--ignored', '--exclude-standard', '-z').split('\0')
    tracked, untracked, ignored = ([name for name in group if name] for group in (tracked, untracked, ignored))
    selection = {'preserved_evidence': [], 'selected_evidence': [], 'expected_generated': []}
    selected_path = os.environ.get('VRA_POC04_SCAN_SELECTION')
    selected_sha = os.environ.get('VRA_POC04_SCAN_SELECTION_SHA256')
    require(bool(selected_path) == bool(selected_sha), 'Incomplete explicit scan selection binding')
    if selected_path:
        path = Path(selected_path)
        require(path.is_absolute() and not path.is_symlink() and path.is_file() and
                path.stat().st_mode & 0o077 == 0 and sha(path) == selected_sha, 'Scan selection is not restricted/bound')
        selection = strict_json(path.read_text())
        require(set(selection) == {'preserved_evidence', 'selected_evidence', 'expected_generated'} and
                all(isinstance(value, list) for value in selection.values()), 'Unsupported scan selection schema')
    historical = historical_preservation_inventory(ROOT)
    authorized_history = {row['path']: row for row in historical['files']}
    for row in selection['preserved_evidence']:
        require(row == authorized_history.get(row.get('path')), 'Preservation lacks prior immutable authority')
    generated = selection['expected_generated']
    require(len(generated) == len(set(generated)) and set(generated) <= set(ignored),
            'Invalid generated path inventory')
    # Explicit ignored artifacts cannot turn source/config or evidence into an exemption.
    for name in generated:
        require(name in EXPECTED_LOCAL_EPHEMERAL, 'Unsupported explicit ephemeral path')
        require(not name.endswith(('.py', '.java', '.kt', '.sql', '.yml', '.yaml', '.gradle', '.env')),
                'Executable/config path excluded as ephemeral')
        candidate_path(ROOT, name)
    explicit_evidence = set(authorized_history) | {entry['path'] for key in ('preserved_evidence', 'selected_evidence')
                         for entry in selection[key]}
    ephemeral = []
    shared_resources = []
    resource_model = None
    for name in ignored:
        if name in explicit_evidence:
            untracked.append(name)
            continue
        if re.fullmatch(r'validation/poc-00/[A-Za-z0-9_-]+/build/resources/(?:main|test)/.+', name) and resource_model is None:
            resource_model = query_gradle_resource_model(ROOT / 'validation/poc-00')
        shared = resolve_shared_resource(name, tracked, resource_model)
        if shared is not None:
            shared_resources.append(shared)
            untracked.append(name)
        elif name in generated or expected_generated_path(name):
            candidate_path(ROOT, name)
            ephemeral.append({'path': name, 'classification': 'EXPECTED_GENERATED_EPHEMERAL'})
        else:
            raise RuntimeError('Unexpected ignored path: ' + name)
    selected_names = {entry['path'] for entry in selection['selected_evidence']}
    require(not selected_names.intersection(row['path'] for row in selection['preserved_evidence']),
            'Conflicting historical selection')
    observed_history = set(untracked).intersection(authorized_history) - selected_names
    preserved = [authorized_history[name] for name in sorted(observed_history)]
    result = freeze_scan_candidate(ROOT, tracked, untracked,
        [name for name in CANDIDATE_SUPPORT_PATHS if name in untracked] +
        [resource['path'] for resource in shared_resources],
        preserved, selection['selected_evidence'], current_evidence)
    shared_names = {resource['path'] for resource in shared_resources}
    for entry in result['files']:
        if entry['path'] in shared_names:
            entry['classification'] = 'candidate/shared-resource'
    result['historical_authority'] = {'origins': historical['origins'],
        'inventory_content_sha256': canonical_sha(historical['files']),
        'authorized_file_count': len(historical['files'])}
    result['configured_shared_resources'] = shared_resources
    result['effective_gradle_resource_model'] = resource_model
    result['classifications']['configured_shared_resources'] = len(shared_resources)
    result['classifications']['untracked_source_support'] -= len(shared_resources)
    result.update({'head': git('rev-parse', 'HEAD'), 'head_tree': git('rev-parse', 'HEAD^{tree}'),
                   'index_entries_sha256': hashlib.sha256(git('ls-files', '--stage', '-z').encode()).hexdigest(),
                   'modified_tracked_paths': git('diff', '--name-only', '-z').split('\0'),
                   'expected_generated': ephemeral, 'selection_sha256': selected_sha})
    result['classifications']['modified_tracked_candidate'] = len([name for name in result['modified_tracked_paths'] if name])
    result['classifications']['expected_generated_ephemeral'] = len(ephemeral)
    return seal_scan_manifest(result)


def bound_scanner_environment(snapshot, parent_env, binary, version):
    """MODEL B: no ambient or source config; defaults bound to the verified pinned binary."""
    overrides = ('GITLEAKS_CONFIG', 'GITLEAKS_CONFIG_TOML')
    for name in overrides:
        require(name not in parent_env, 'Ambient scanner override prohibited: ' + name)
    for directory in (snapshot, Path.cwd()):
        config = directory / '.gitleaks.toml'
        require(not config.exists() and not config.is_symlink(), 'Ambient/source scanner configuration prohibited')
    require(version == '8.30.1' and binary.is_file() and not binary.is_symlink(),
            'Unreviewed scanner identity')
    identity = {'mode': 'PINNED_BINARY_DEFAULT', 'version': version, 'binary_sha256': sha(binary),
                'override_presence': {name: False for name in overrides},
                'source_config_present': False}
    return dict(parent_env), identity


class Run:
    def __init__(self, args):
        self.args = args
        self.source_root = ROOT.resolve()
        self.out = Path(args.evidence_directory).absolute()
        require(not self.out.exists(), 'Evidence directory must be new; historical evidence is immutable')
        current_evidence = str(self.out.relative_to(ROOT))
        require(current_evidence.startswith('validation/poc-04/evidence/phase1/'),
                'Current output is outside the Phase-1 evidence boundary')
        self.scan_manifest = repository_scan_candidate(current_evidence)
        self.execution_directory = Path(tempfile.mkdtemp(prefix='vra-poc04-ci-')).resolve()
        try:
            domains = create_execution_domains(self.source_root, self.scan_manifest, self.execution_directory / 'domains')
            self.scan_root, self.build_root = domains['scan_root'], domains['build_root']
            self.scan_identity = domains['scan_identity']
            self.build_scripts = self.build_root / 'validation/poc-04/scripts'
            self.build_init = self.build_root / 'validation/poc-04/test-support/runner.init.gradle'
            self.support = self.build_root / '.poc04-execution'
            require(not self.support.exists(), 'Mutable execution support destination already exists')
            self.support.mkdir(mode=0o700)
        except Exception:
            # A failed construction is retained externally; source authority is never repaired here.
            raise
        # Output is created only after the primary input set and projection are frozen.
        self.out.mkdir(parents=True, mode=0o700)
        write(self.out / 'scan-candidate-manifest.json', self.scan_manifest)
        write(self.out / 'execution-domains.json', {**domains, 'scan_root': str(self.scan_root),
              'build_root': str(self.build_root), 'source_root': str(self.source_root)})
        self.env = isolated_git_environment()
        self.run_id = str(uuid.uuid4())
        self.recorder = None
        self.commands = []
        self.initial = None
        self.result = {'status': 'RUNNING', 'gate': args.gate, 'run_id': self.run_id,
                       'phase1_closed': False, 'phase2_started': False,
                       'retained_execution_directory': str(self.execution_directory)}

    def command(self, name, argv, cwd=None, timeout=900):
        cwd = self.build_root if cwd is None else Path(cwd)
        output = self.out / (name + '.txt')
        started = time.time()
        with output.open('w') as log:
            result = subprocess.run(argv, cwd=cwd, env=self.env, stdout=log,
                                    stderr=subprocess.STDOUT, timeout=timeout)
        os.chmod(output, 0o600)
        self.commands.append({'name': name, 'command': list(map(str, argv)), 'cwd': str(cwd),
                              'exit_status': result.returncode, 'duration_seconds': time.time() - started,
                              'output': output.name, 'output_sha256': sha(output)})
        write(self.out / 'commands.json', self.commands)
        require(result.returncode == 0, 'Command failed: ' + name + '; preserved output: ' + output.name)
        return output.read_text()

    def docker(self, *args):
        return subprocess.check_output(['docker', *args], cwd=self.build_root, env=self.env, text=True).strip()

    def inventory(self):
        return {'containers': self.docker('ps', '-aq', '--no-trunc').splitlines(),
                'volumes': self.docker('volume', 'ls', '-q').splitlines(),
                'networks': json.loads(self.docker('network', 'inspect', *self.docker('network', 'ls', '-q').splitlines()))}

    def preflight(self):
        hosted = self.env.get('GITHUB_ACTIONS') == 'true'
        require(not hosted or self.env.get('RUNNER_ENVIRONMENT') == 'github-hosted',
                'Hosted execution requires a disposable GitHub-hosted runner')
        self.hosted = hosted
        endpoint = self.env.get('DOCKER_HOST')
        if not endpoint:
            context = subprocess.check_output(['docker', 'context', 'show'], cwd=self.build_root, text=True).strip()
            context_data = json.loads(subprocess.check_output(['docker', 'context', 'inspect', context],
                                                             cwd=self.build_root, text=True))[0]
            endpoint = context_data['Endpoints']['docker']['Host']
        declared = self.env.get('VRA_POC04_DISPOSABLE_TEST_DOCKER_ENDPOINT')
        require(hosted or declared == endpoint, 'Local endpoint must match explicit disposable TEST declaration')
        require(endpoint.startswith('unix:///') or endpoint.startswith('tcp://'), 'Unsupported Docker endpoint')
        self.env['DOCKER_HOST'] = endpoint
        version = json.loads(self.docker('version', '--format', '{{json .Server}}'))
        info = json.loads(self.docker('info', '--format', '{{json .}}'))
        require(tuple(map(int, version['ApiVersion'].split('.'))) >= (1, 49), 'Docker Engine API >=1.49 required')
        require(info['OSType'] == 'linux' and info['Architecture'] in ('aarch64', 'arm64', 'x86_64', 'amd64'),
                'Unreviewed Docker platform')
        require('desktop' not in info['Name'].lower() and 'vra-poc00' not in info['Name'].lower(),
                'Shared/persistent Docker daemon is prohibited')
        expected = self.env.get('VRA_POC04_EXPECTED_DAEMON_ID')
        require(not expected or expected == info['ID'], 'Expected Docker daemon ID differs')
        require(self.env.get('TESTCONTAINERS_RYUK_DISABLED', 'false').lower() == 'false', 'Ryuk must remain enabled')
        socket = self.env.get('TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE')
        if not socket and platform.system() == 'Linux' and endpoint.startswith('unix:///'):
            socket = endpoint[len('unix://'):]
        require(socket and socket.startswith('/'), 'Explicit daemon-side Testcontainers socket required')
        host = self.env.get('TESTCONTAINERS_HOST_OVERRIDE')
        if not host and platform.system() == 'Linux' and endpoint.startswith('unix:///'):
            host = '127.0.0.1'
        require(host, 'Published-port host must be independently selected for this endpoint')
        java_home = self.env.get('JAVA_HOME')
        require(java_home and (Path(java_home) / 'bin/java').is_file(), 'Java 21 JAVA_HOME is required')
        java_version = self.command('java-version', [str(Path(java_home) / 'bin/java'), '-version'])
        require('version "21.' in java_version, 'Java 21 is required')
        self.command('buildx-version', ['docker', 'buildx', 'version'])
        self.initial = self.inventory()
        require(not self.initial['containers'] and not self.initial['volumes'], 'Disposable daemon is not empty')
        require({network['Name'] for network in self.initial['networks']} == {'bridge', 'host', 'none'},
                'Disposable daemon has unexpected networks')
        verify_frozen_scan_domain(self.scan_root, self.scan_manifest, self.scan_identity)
        compare_execution_domain_candidates(self.scan_root, self.build_root, self.scan_manifest)
        source = binding(self.scan_root)
        event = {}
        if hosted:
            event = json.loads(Path(self.env['GITHUB_EVENT_PATH']).read_text())
        pr_head = event.get('pull_request', {}).get('head', {}).get('sha')
        require(not pr_head or source['head'] == pr_head, 'PR-head checkout required; merge SHA is not PR-head identity')
        require(sha(self.scan_root / 'validation/poc-04/tooling/tool-manifest.json') == TOOL_SHA,
                'Frozen tool authority differs')
        write(self.out / 'source-binding-start.json', source)
        self.source = source
        state = {'allocations': {}, 'daemon_id': info['ID'], 'docker_host': endpoint, 'events': [],
                 'next_ordinal': {}, 'physical_instances': {}, 'resources': {}, 'run_id': self.run_id,
                 'source_content_sha256': source['content_sha256'], 'source_head': source['head'],
                 'tool_manifest_sha256': TOOL_SHA}
        self.state_path = self.support / 'run-state.json'
        write(self.state_path, state)
        self.env.update({'VRA_POC04_DISPOSABLE_TEST_DOCKER_ENDPOINT': endpoint,
                         'VRA_POC04_EXPECTED_DAEMON_ID': info['ID'], 'VRA_POC04_RUN_ID': self.run_id,
                         'VRA_POC04_SOURCE_HEAD': source['head'],
                         'VRA_POC04_SOURCE_CONTENT_SHA256': source['content_sha256'],
                         'VRA_POC04_TOOL_MANIFEST_SHA256': TOOL_SHA,
                         'VRA_POC04_RUN_STATE': str(self.state_path),
                         'VRA_POC04_RUN_HARNESS': str(self.build_scripts / 'run-harness.py'),
                         'VRA_POC04_RESOURCE_EVIDENCE': str(self.out / 'resource-identities.jsonl'),
                         'VRA_POC04_SUPPORT_DIRECTORY': str(self.support),
                         'VRA_POC04_EXTERNAL_TC_JAR': str(self.support / 'external-tc.jar'),
                         'VRA_POC04_EVIDENCE_DIRECTORY': str(self.out),
                         'VRA_POC04_DAEMON_INSPECT_DIRECTORY': str(self.out / 'daemon-inspect'),
                         'TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE': socket, 'TESTCONTAINERS_HOST_OVERRIDE': host,
                         'TESTCONTAINERS_RYUK_DISABLED': 'false'})
        write(self.out / 'preflight.json', {
            'environment_type': 'TEST', 'hosted': hosted, 'run_id': self.run_id,
            'daemon': {key: info[key] for key in ('ID', 'Name', 'OSType', 'Architecture', 'DefaultAddressPools')},
            'docker_server': {key: version[key] for key in ('Version', 'ApiVersion', 'Os', 'Arch')},
            'initial_resources': self.initial, 'pr_head_sha': pr_head,
            'github_sha_context': self.env.get('GITHUB_SHA'), 'checked_out_commit_sha': source['head'],
            'checked_out_tree': source['tree'], 'source_content_sha256': source['content_sha256'],
            'environment': {key: self.env[key] for key in (
                'VRA_POC04_DISPOSABLE_TEST_DOCKER_ENDPOINT', 'VRA_POC04_EXPECTED_DAEMON_ID',
                'VRA_POC04_RUN_ID', 'VRA_POC04_SOURCE_HEAD', 'VRA_POC04_SOURCE_CONTENT_SHA256',
                'VRA_POC04_TOOL_MANIFEST_SHA256', 'VRA_POC04_RUN_STATE', 'VRA_POC04_RUN_HARNESS',
                'VRA_POC04_RESOURCE_EVIDENCE', 'VRA_POC04_SUPPORT_DIRECTORY', 'VRA_POC04_EXTERNAL_TC_JAR',
                'VRA_POC04_EVIDENCE_DIRECTORY', 'VRA_POC04_DAEMON_INSPECT_DIRECTORY', 'DOCKER_HOST',
                'TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE', 'TESTCONTAINERS_HOST_OVERRIDE',
                'TESTCONTAINERS_RYUK_DISABLED')}})

    def gradle(self, name, *tasks):
        verify_frozen_scan_domain(self.scan_root, self.scan_manifest, self.scan_identity)
        compare_execution_domain_candidates(self.scan_root, self.build_root, self.scan_manifest)
        self.command(name, [str(self.build_root / 'backend/gradlew'), '-p', str(self.build_root / 'backend'),
                            '--no-daemon', '--warning-mode', 'all', '--init-script', str(self.build_init), *tasks])

    def support_proof(self, repository_root=None):
        root = ROOT if repository_root is None else Path(repository_root)
        artifact = Path(self.env['VRA_POC04_EXTERNAL_TC_JAR'])
        required = {'dev/vra/poc04/external/' + name + '.class'
                    for name in ('PinnedImages', 'RunOwnedPostgreSQLContainer', 'RunResources')}
        services = {'META-INF/services/' + name for name in (
            'org.testcontainers.core.CreateContainerCmdModifier', 'org.testcontainers.utility.ImageNameSubstitutor')}
        with zipfile.ZipFile(artifact) as jar:
            entries = {name for name in jar.namelist() if not name.endswith('/')}
            require(entries == required | services | {'META-INF/MANIFEST.MF'},
                    'Unexpected or diagnostic TEST support artifact content')
            for name in services:
                require(jar.read(name) == (root / 'validation/poc-04/test-support/resources' / name).read_bytes(),
                        'Service descriptor differs from checked-in source')
        write(self.out / 'support-artifact.json', {'status': 'PASS', 'sha256': sha(artifact),
                                                  'entries': sorted(entries), 'compile_test_only': True,
                                                  'diagnostic_classes_absent': True})

    def start_recorder(self):
        self.recorder_log = (self.out / 'recorder.txt').open('w')
        self.recorder = subprocess.Popen([sys.executable, '-B', str(self.build_scripts / 'resource-recorder.py')],
                                         cwd=self.build_root, env=self.env, stdout=self.recorder_log,
                                         stderr=subprocess.STDOUT)
        deadline = time.monotonic() + 20
        while not (self.support / 'recorder.ready').exists():
            require(self.recorder.poll() is None and time.monotonic() < deadline, 'Safe recorder failed to start')
            time.sleep(.05)

    def counts(self):
        rows = {}
        for suite, expected in EXPECTED_COUNTS.items():
            project, task = suite.split(':')
            source = self.build_root / 'backend' / project / 'build/test-results' / task
            target = self.out / 'junit' / project / task
            target.mkdir(parents=True)
            files = sorted(source.glob('TEST-*.xml'))
            require(files, 'Missing JUnit XML: ' + suite)
            counts = dict.fromkeys(('tests', 'skipped', 'failures', 'errors'), 0)
            for file in files:
                parsed = ET.parse(file).getroot()
                for key in counts:
                    counts[key] += int(parsed.attrib.get(key, '0'))
                require(len(parsed.findall('testcase')) == int(parsed.attrib['tests']), 'JUnit testcase count mismatch')
                shutil.copyfile(file, target / file.name)
            require(counts == {'tests': expected, 'skipped': 0, 'failures': 0, 'errors': 0},
                    'Required suite counts differ: ' + suite)
            rows[suite] = counts
        write(self.out / 'suite-counts.json', rows)
        self.result['suites'] = rows

    def bootjars(self):
        rows = []
        for project in ('runtime', 'migration'):
            path = self.build_root / 'backend' / project / 'build/libs' / (project + '-0.1.0-SNAPSHOT.jar')
            require(path.is_file() and path.stat().st_size > 0, 'Deployable bootJar absent: ' + project)
            with zipfile.ZipFile(path) as jar:
                entries = jar.namelist()
                require(not any('dev/vra/poc04/external' in name or 'external-tc' in name
                                or name.endswith('/PhaseOneEvidenceCapture.class') for name in entries),
                        'TEST support leaked into deployable bootJar')
                for name in entries:
                    if name.startswith('BOOT-INF/lib/') and name.endswith('.jar'):
                        with zipfile.ZipFile(io.BytesIO(jar.read(name))) as library:
                            require(not any('dev/vra/poc04/external' in item for item in library.namelist()),
                                    'TEST support leaked into nested deployable dependency')
            rows.append({'path': str(path.relative_to(self.build_root)), 'sha256': sha(path),
                         'test_support_present': False, 'nested_libraries_inspected': True})
        write(self.out / 'bootjar-proof.json', rows)

    def bind_build_artifacts(self):
        producers = [row for row in self.commands if row['name'] == 'full-check-build']
        require(len(producers) == 1, 'Expected exactly one full-build producer')
        producer = producers[0]
        require(sha(self.out / producer['output']) == producer['output_sha256'], 'Build producer log binding differs')
        self.selected_artifacts = select_build_artifacts(self.build_root, self.scan_manifest, self.run_id,
                                                        producer=producer)
        write(self.out / 'selected-build-artifacts.json', self.selected_artifacts)

    def deliver_hosted_artifacts(self):
        """Preserve the existing hosted workflow contract without building in its checkout."""
        require(self.hosted and self.env.get('GITHUB_ACTIONS') == 'true' and
                self.env.get('RUNNER_ENVIRONMENT') == 'github-hosted',
                'Artifact delivery requires the validated disposable hosted preflight')
        verify_selected_build_artifacts(self.build_root, self.scan_manifest, self.selected_artifacts)
        delivery = []
        candidate_names = {row['path'] for row in self.scan_manifest['files']}
        for row in self.selected_artifacts['files']:
            require(row['path'] not in candidate_names, 'Generated artifact cannot overwrite candidate source')
            target = candidate_path(self.source_root, row['path'])
            require(not target.exists(), 'Hosted artifact delivery destination already exists')
            target.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
            raw = candidate_path(self.build_root, row['path']).read_bytes()
            require(hashlib.sha256(raw).hexdigest() == row['sha256'], 'Selected artifact changed before delivery')
            descriptor = os.open(target, os.O_WRONLY | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW, int(row['mode'], 8))
            with os.fdopen(descriptor, 'wb') as output:
                output.write(raw)
            target.chmod(int(row['mode'], 8))
            require(scan_file_record(self.source_root, row['path']) ==
                    {key: row[key] for key in ('path', 'sha256', 'size', 'mode')}, 'Hosted delivery identity differs')
            delivery.append({**row, 'destination_root': str(self.source_root)})
        write(self.out / 'hosted-artifact-delivery.json', {'status': 'PASS',
              'selection_sha256': self.selected_artifacts['selection_sha256'], 'files': delivery,
              'authority': 'existing hosted workflow exact two-bootJar output contract'})

    def resource_boundary(self):
        ledger = json.loads(self.state_path.read_text())
        identities = [json.loads(line) for line in
                      (self.out / 'resource-identities.jsonl').read_text().splitlines()]
        created = [event for event in identities if event.get('event') == 'container_created_attested_before_start']
        physical = [event for event in identities if event.get('event') == 'postgres_physical_identity_after_readiness']
        require(created and physical, 'RunOwnedPostgreSQLContainer lifecycle evidence absent')
        require(all(event['labels']['dev.vra.run_id'] == self.run_id and
                    'container:' + event['id'] in ledger['resources'] for event in created),
                'Creator container labels/ledger differ')
        require(all(event['identity']['database_instance_id'] in ledger['physical_instances'] for event in physical),
                'Physical PostgreSQL identity not bound')
        events = [json.loads(line) for line in (self.out / 'docker-resource-events.jsonl').read_text().splitlines()]
        ryuk = [event for event in events if event.get('kind') == 'container_inspect'
                and 'testcontainers/ryuk@sha256:' in (event.get('image') or '') and event['state']['Running']]
        require(ryuk, 'Running pinned Ryuk evidence absent')
        socket = self.env['TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE']
        require(all(any(mount['Source'] == socket and mount['Destination'] == '/var/run/docker.sock'
                        for mount in event['mounts']) for event in ryuk), 'Ryuk isolated socket binding differs')
        require(any(event['operation'] == 'teardown_denied' and not event['docker_delete_called']
                    for event in ledger['events']), 'Unauthorized teardown denial not executed')
        write(self.out / 'resource-boundary-proof.json', {'status': 'PASS',
              'run_owned_container_ids': [event['id'] for event in created],
              'physical_instances': list(ledger['physical_instances'].values()),
              'ryuk_ids': sorted({event['id'] for event in ryuk}), 'ryuk_socket': socket,
              'creator_ledger_verified': True, 'unauthorized_teardown_denied': True})

    def cleanup(self):
        if not hasattr(self, 'state_path'):
            return
        state = json.loads(self.state_path.read_text())
        write(self.out / 'ledger-before-cleanup.json', state)
        for kind in ('container', 'network', 'volume'):
            for entry in state['resources'].values():
                if entry['kind'] == kind:
                    self.command('cleanup-' + kind + '-' + entry['id'],
                                 [sys.executable, '-B', self.env['VRA_POC04_RUN_HARNESS'], 'teardown',
                                  '--kind', kind, '--id', entry['id']])
        self.command('ledger-finish', [sys.executable, '-B', self.env['VRA_POC04_RUN_HARNESS'], 'finish'])
        deadline = time.monotonic() + 30
        while self.docker('ps', '-aq') and time.monotonic() < deadline:
            time.sleep(.1)  # Observe native Ryuk shutdown; absence, not elapsed time, is the assertion.
        final = self.inventory()
        write(self.out / 'final-daemon-state.json', final)
        require(not final['containers'] and not final['volumes'], 'Container or volume remains after exact cleanup')
        require({network['Id'] for network in final['networks']} ==
                {network['Id'] for network in self.initial['networks']}, 'Network IDs differ after cleanup')
        write(self.out / 'final-run-ledger.json', json.loads(self.state_path.read_text()))

    def secret_scan(self, phase="primary", expanded=False):
        self.result['scanner_execution'] = 'NOT EXECUTED'
        verify_frozen_scan_domain(self.scan_root, self.scan_manifest, self.scan_identity)
        manifest = json.loads((self.scan_root / 'validation/poc-04/tooling/tool-manifest.json').read_text())
        selected = next(tool for tool in manifest['tools'] if tool['id'] == 'Gitleaks')
        machine = {'aarch64': 'arm64', 'x86_64': 'amd64', 'arm64': 'arm64'}.get(platform.machine())
        system = {'Darwin': 'darwin', 'Linux': 'linux'}.get(platform.system())
        require(system is not None and machine is not None, 'Unreviewed scanner platform')
        host_platform = system + '/' + machine
        artifacts = [item for item in selected['immutable_identifiers']['artifacts'] if item['platform'] == host_platform]
        require(len(artifacts) == 1, 'Scanner platform artifact is not uniquely pinned')
        artifact = artifacts[0]
        require(artifact['url'].startswith('https://github.com/gitleaks/gitleaks/releases/download/v' + selected['version'] + '/') and
                artifact['url'].endswith('/' + artifact['identifier']) and
                len(artifact['expected_sha256']) == 64 and
                all(character in '0123456789abcdef' for character in artifact['expected_sha256']),
                'Malformed pinned scanner artifact identity')
        if not hasattr(self, 'scanner_binary'):
            self.scanner_binary = acquire_pinned_scanner(artifact, selected['version'], self.support, self.out / 'scanner-acquisition.json')
            self.scanner_binary_sha256 = sha(self.scanner_binary)
        scanner = self.scanner_binary
        require(sha(scanner) == self.scanner_binary_sha256, 'Pinned scanner bytes changed between controls')
        verify_frozen_scan_domain(self.scan_root, self.scan_manifest, self.scan_identity)
        blobs = []
        contexts = []
        binary_inputs = []
        start_line = 1
        for entry in self.scan_manifest['files']:
            path = self.scan_root / entry['path']
            require(scan_file_record(self.scan_root, entry['path']) ==
                    {key: entry[key] for key in ('path', 'sha256', 'size', 'mode')},
                    'Frozen scanner snapshot changed')
            data = path.read_bytes()
            if b'\0' in data:
                binary_inputs.append(entry['path'])
                continue
            contexts.append({'path': entry['path'], 'file_sha256': entry['sha256'],
                             'start': start_line, 'end': start_line + data.count(b'\n'),
                             'lines': data.split(b'\n')})
            blobs.append(data)
            start_line += data.count(b'\n') + 1
        if expanded:
            selection = self.selected_artifacts
            verify_selected_build_artifacts(self.build_root, self.scan_manifest, selection)
            expanded_root = self.support / 'expanded-candidate'
            require(not expanded_root.exists(), 'Expanded scanner projection must be fresh')
            expanded_root.mkdir(mode=0o700)
            for entry in self.scan_manifest['files']:
                target = expanded_root / entry['path']
                target.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
                target.write_bytes(candidate_path(self.scan_root, entry['path']).read_bytes())
                target.chmod(int(entry['mode'], 8))
                require(scan_file_record(expanded_root, entry['path']) ==
                        {key: entry[key] for key in ('path', 'sha256', 'size', 'mode')},
                        'Expanded original input differs')
            members = []
            for entry in selection['files']:
                path = expanded_root / entry['path']
                path.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
                path.write_bytes(candidate_path(self.build_root, entry['path']).read_bytes())
                path.chmod(int(entry['mode'], 8))
                require(scan_file_record(expanded_root, entry['path']) ==
                        {key: entry[key] for key in ('path', 'sha256', 'size', 'mode')},
                        'Selected generated artifact changed during projection')
                with zipfile.ZipFile(path) as jar:
                    names = jar.namelist()
                    require(len(names) == len(set(names)), 'Ambiguous duplicate deployable ZIP members')
                    for name in names:
                        require(not name.startswith('/') and '\\' not in name and
                                '..' not in Path(name).parts, 'Unsafe deployable ZIP member path')
                        if name.startswith('BOOT-INF/classes/') and not name.endswith('/'):
                            data = jar.read(name)
                            blobs.append(data)
                            members.append({'artifact_path': entry['path'], 'member': name,
                                            'sha256': hashlib.sha256(data).hexdigest(), 'size': len(data)})
            verify_selected_build_artifacts(self.build_root, self.scan_manifest, selection)
            expanded_manifest = {'schema': 'poc04-expanded-security-input-v1',
                'original_manifest_sha256': self.scan_manifest['manifest_sha256'],
                'original_content_sha256': self.scan_manifest['content_sha256'],
                'artifact_selection_sha256': selection['selection_sha256'],
                'files': self.scan_manifest['files'] + selection['files'], 'derived_members': members}
            expanded_manifest['manifest_sha256'] = canonical_sha(expanded_manifest)
            write(self.out / 'scanner-artifact-inputs.json', expanded_manifest)
        scanner_env, config_identity = bound_scanner_environment(self.scan_root, self.env, scanner, selected['version'])
        scanner_arguments = [str(scanner), 'stdin', '--report-format', 'json', '--report-path', '-', '--no-banner']
        write(self.out / ('scanner-effective-identity-' + phase + '.json'), {**config_identity, 'asset': artifact['identifier'],
              'arguments': scanner_arguments, 'scan_manifest_content_sha256': self.scan_manifest['content_sha256']})
        # A bound regular file supplies stable reads; append-only evidence never feeds this input.
        scanner_input = self.support / 'scanner-input'
        payload = b'\n'.join(blobs)
        if phase == 'primary':
            self.primary_scanner_payload = payload
        elif not expanded:
            require(payload == self.primary_scanner_payload, 'Original frozen scanner bytes changed')
        scanner_input.write_bytes(payload)
        scanner_input.chmod(0o600)
        with scanner_input.open('rb') as input_stream:
            scanned = subprocess.run(scanner_arguments, stdin=input_stream, capture_output=True,
                                     cwd=self.scan_root, env=scanner_env)
        self.result['scanner_execution'] = 'EXECUTED'
        require(scanned.returncode in (0, 1), 'Gitleaks execution failed')
        findings = json.loads(scanned.stdout)
        verify_frozen_scan_domain(self.scan_root, self.scan_manifest, self.scan_identity)
        proofs = verified_public_identifier_provenance()
        write(self.out / 'public-identifier-provenance.json', {
            'status': 'PASS', 'identifiers': list(proofs.values())})
        digest_contexts = approved_digest_contexts(repository_root=self.scan_root)
        triage = []
        for item in findings:
            row = {'rule': item['RuleID'], 'start_line': item['StartLine'], 'classification': 'UNRESOLVED'}
            observation = resolve_scan_observation(item, contexts)
            if observation:
                row.update({'path': observation['path'], 'local_line': observation['line'],
                            'origin_kind': observation['origin_kind']})
            if classify_public_verification_identifier(item['Secret'], observation, proofs.get(item['Secret'])):
                row.update({'classification': 'PUBLIC VERIFICATION IDENTIFIER',
                            'provenance': proofs[item['Secret']]})
            else:
                proof = classify_digest_metadata(item['Secret'], observation, digest_contexts,
                                                 repository_root=self.scan_root)
                if proof is not None:
                    # Keep immutable origin identity, lengths and normalization, never the
                    # raw observation or a new scanner-token-derived hash schema.
                    safe_proof = {key: value for key, value in proof.items() if key not in
                                  ('raw_token_sha256', 'canonical_identity_sha256', 'context_sha256')}
                    row.update({'classification': 'VERIFIED DIGEST METADATA', 'digest_proof': safe_proof})
            # Round-5F explicitly requires a safe matched-token hash for set identity, never raw content.
            row['finding_identity'] = {'rule': item['RuleID'], 'path': row.get('path'),
                'start_line': row.get('local_line', item['StartLine']), 'end_line': item['EndLine'] - item['StartLine'] + row.get('local_line', item['StartLine']),
                'start_column': item.get('StartColumn'), 'end_column': item.get('EndColumn'),
                'token_sha256': hashlib.sha256(item['Secret'].encode()).hexdigest()}
            row['finding_key'] = canonical_sha(row['finding_identity'])
            triage.append(row)
        public_count = sum(row['classification'] == 'PUBLIC VERIFICATION IDENTIFIER' for row in triage)
        digest_count = sum(row['classification'] == 'VERIFIED DIGEST METADATA' for row in triage)
        unresolved = sum(row['classification'] == 'UNRESOLVED' for row in triage)
        report = {'scanner_version': selected['version'],
              'archive_sha256': artifact['expected_sha256'], 'exit_status': scanned.returncode,
              'scope_files': len(self.scan_manifest['files']),
              'text_input_files': len(contexts), 'binary_inputs_bound_not_text_analyzed': binary_inputs,
              'candidate_content_sha256': self.scan_manifest['content_sha256'],
              'scan_manifest_sha256': self.scan_manifest['manifest_sha256'],
              'input': 'frozen restricted repository-relative projection',
              'bootjar_application_entries_included': expanded,
              'phase': phase, 'effective_config': config_identity, 'stdin_content_sha256': hashlib.sha256(payload).hexdigest(),
              'matches': triage, 'matches_total': len(triage),
              'classified_public_verification_identifiers': public_count,
              'classified_digest_metadata': digest_count, 'unresolved_secret_findings': unresolved,
              'unresolved': unresolved}
        write(self.out / ('secret-scan-' + phase + '.json'), report)
        if phase == 'primary':
            write(self.out / 'secret-scan.json', report)
        require(unresolved == 0, 'Secret scan has unresolved findings')
        return sorted(row['finding_key'] for row in triage)

    def execute(self):
        try:
            self.preflight()
            primary_findings = self.secret_scan('primary')
            self.result['secret_scan_status'] = 'PASS'
            if self.args.gate == 'full':
                controls = [self.secret_scan('prebuild-control-1'), self.secret_scan('prebuild-control-2')]
                write(self.out / 'prebuild-finding-sets.json', {'primary': primary_findings, 'controls': controls})
                require(all(findings == primary_findings for findings in controls),
                        'Scanner non-deterministic under identical frozen inputs')
            if self.args.gate != 'scanner':
                self.gradle('focused-compile', ':runtime:compileTestJava', ':migration:compileTestJava',
                            'poc04TestClasspathProof', '--rerun-tasks')
                self.support_proof(repository_root=self.scan_root)
            if self.args.gate in ('boundary', 'full'):
                self.start_recorder()
                if self.args.gate in ('boundary', 'full'):
                    self.gradle('focused-boundary', ':migration:integrationTest', '--tests',
                                'dev.vra.migration.Poc04FoundationMigrationIntegrationTest.freshV1ThroughV4ValidatesRerunsAndEnforcesPhaseOneBoundary',
                                '--rerun-tasks')
                    xml = self.build_root / 'backend/migration/build/test-results/integrationTest/TEST-dev.vra.migration.Poc04FoundationMigrationIntegrationTest.xml'
                    row = ET.parse(xml).getroot()
                    require(all(int(row.attrib[key]) == value for key, value in
                                {'tests': 1, 'skipped': 0, 'failures': 0, 'errors': 0}.items()), 'Focused boundary XML differs')
                    shutil.copyfile(xml, self.out / 'focused-boundary.xml')
                if self.args.gate == 'full':
                    self.gradle('full-check-build', ':runtime:clean', ':migration:clean', 'check', 'build',
                                'poc04TestClasspathProof', '--rerun-tasks')
                    self.counts()
                    self.bootjars()
                    self.bind_build_artifacts()
                self.command('teardown-negatives', [sys.executable, '-B', str(self.build_scripts / 'teardown-negative-proof.py')])
                self.resource_boundary()
            self.result['execution_status'] = 'PASS'
        except Exception as failure:
            self.result['execution_status'] = 'FAIL'
            self.result['failure'] = str(failure) if isinstance(failure, RuntimeError) else type(failure).__name__
        finally:
            try:
                self.cleanup()
                self.result['cleanup_status'] = 'PASS'
            except Exception as failure:
                self.result['cleanup_status'] = 'FAIL'
                self.result['cleanup_failure'] = str(failure) if isinstance(failure, RuntimeError) else type(failure).__name__
                if hasattr(self, 'state_path'):
                    write(self.out / 'cleanup-failure-ledger.json', json.loads(self.state_path.read_text()))
                self.result['restricted_recovery_directory'] = str(self.execution_directory)
            if self.recorder is not None:
                (self.support / 'recorder.stop').write_text('stop\n')
                try:
                    self.recorder.wait(timeout=10)
                    require(self.recorder.returncode == 0, 'Recorder exit differs')
                except Exception:
                    self.recorder.kill()
                    self.result['cleanup_status'] = 'FAIL'
                    self.result['recorder_failure'] = True
                self.recorder_log.close()
            try:
                verify_frozen_scan_domain(self.scan_root, self.scan_manifest, self.scan_identity)
                final = binding(self.scan_root)
                write(self.out / 'source-binding-final.json', final)
                self.final_source = final
                require(final['head'] == self.source['head'] and final['tree'] == self.source['tree'] and
                        final['content_sha256'] == self.source['content_sha256'], 'Source binding changed during execution')
                self.result['source_binding_status'] = 'PASS'
                if self.result['execution_status'] == 'PASS' and self.result['cleanup_status'] == 'PASS':
                    if self.args.gate == 'full':
                        postbuild = self.secret_scan('postbuild-original')
                        require(postbuild == primary_findings, 'Original scanner finding set changed after build')
                        expanded = self.secret_scan('expanded-artifacts', expanded=True)
                        require(set(primary_findings) <= set(expanded), 'Expanded scanner input lost original findings')
                        write(self.out / 'scanner-finding-set-comparison.json', {'primary': primary_findings,
                              'postbuild_original': postbuild, 'expanded': expanded, 'status': 'PASS'})
                    # Invocation-source stability is separate from the A-only original-input scan.
                    self.result['source_binding_status'] = 'FAIL'
                    verify_scan_candidate(self.source_root, self.scan_manifest)
                    require(private_git_identity(self.source_root) == self.scan_identity['git'],
                            'Invocation source Git identity changed during execution')
                    self.result['source_binding_status'] = 'PASS'
                    if self.args.gate == 'full':
                        if self.hosted:
                            self.deliver_hosted_artifacts()
                    self.result['secret_scan_status'] = 'PASS'
            except Exception as failure:
                self.result['secret_scan_status'] = 'FAIL'
                self.result['source_or_scan_failure'] = str(failure) if isinstance(failure, RuntimeError) else type(failure).__name__
            gates = ('execution_status', 'cleanup_status', 'source_binding_status', 'secret_scan_status')
            self.result['status'] = 'PASS' if all(self.result.get(key) == 'PASS' for key in gates) else 'FAIL'
            if self.result['status'] == 'FAIL':
                self.result['restricted_recovery_directory'] = str(self.execution_directory)
            write(self.out / 'result.json', self.result)
            entries = [{'path': str(path.relative_to(self.out)), 'sha256': sha(path)}
                       for path in sorted(self.out.rglob('*')) if path.is_file()]
            write(self.out / 'EVIDENCE_BINDING.json', {'run_id': self.run_id, 'status': self.result['status'],
                  'source_content_sha256': getattr(self, 'source', {}).get('content_sha256'), 'files': entries})
            # Preserve bounded external A/B state for independent inspection on success or failure.
        print(json.dumps({'status': self.result['status'], 'gate': self.args.gate,
                          'run_id': self.run_id, 'evidence_directory': str(self.out)}))
        return 0 if self.result['status'] == 'PASS' else 1


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--disposable-test', action='store_true', required=True)
    parser.add_argument('--gate', choices=('scanner', 'compile', 'boundary', 'full'), default='full')
    parser.add_argument('--evidence-directory', required=True)
    return Run(parser.parse_args()).execute()


if __name__ == '__main__':
    sys.exit(main())
