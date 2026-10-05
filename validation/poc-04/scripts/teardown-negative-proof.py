"""Executed TEST-only denial fixtures; all deletion uses exact recorded IDs."""
from concurrent.futures import ThreadPoolExecutor
import json
import os
from pathlib import Path
import subprocess
import uuid

import importlib.util
spec = importlib.util.spec_from_file_location('run_harness', Path(__file__).with_name('run-harness.py'))
harness = importlib.util.module_from_spec(spec)
spec.loader.exec_module(harness)

out = Path(os.environ['VRA_POC04_EVIDENCE_DIRECTORY'])
events = []


def record(value):
    events.append(value)
    (out / 'teardown-negatives.json').write_text(json.dumps(events, sort_keys=True, indent=2) + '\n')


def run(argv, expected=0):
    result = subprocess.run(argv, capture_output=True, text=True, timeout=60)
    record({'command': argv, 'exit_status': result.returncode, 'stdout': result.stdout, 'stderr': result.stderr})
    assert result.returncode == expected, 'Unexpected proof command status; preserved in teardown-negatives.json'
    return result.stdout.strip()


def cli(*args, expected=0):
    return json.loads(run(['python3', '-B', os.environ['VRA_POC04_RUN_HARNESS'], *args], expected))


def create(kind, name, labels):
    argv = ['docker', kind, 'create']
    for key, value in sorted(labels.items()):
        argv.extend(['--label', key + '=' + value])
    argv.append(name)
    with harness.transaction() as state:
        harness.attest(state)
        result = harness.docker(state, argv[1:])
        record({'command': argv, 'exit_status': result.returncode, 'stdout': result.stdout, 'stderr': result.stderr})
        assert result.returncode == 0, 'Negative fixture creation failed; evidence preserved'
        identity = result.stdout.strip()
        snapshot = harness.inspect(state, kind, identity)
    assert snapshot is not None and snapshot['labels'] == labels
    record({'operation': 'trusted_negative_fixture_created', 'exact_id': identity, 'snapshot': snapshot})
    return identity, snapshot


def present(kind, identity):
    with harness.transaction() as state:
        harness.attest(state)
        return harness.inspect(state, kind, identity)


def explicit_admin_cleanup(kind, identity, expected_snapshot, purpose):
    # Separate negative-fixture administrative authorization; never current-run ordinary teardown.
    with harness.transaction() as state:
        harness.attest(state)
        before = harness.inspect(state, kind, identity)
        assert before == expected_snapshot, 'Independent exact negative fixture ownership check failed'
        result = harness.docker(state, [kind, 'rm', identity])
        assert result.returncode == 0
        assert harness.inspect(state, kind, identity) is None
    record({'operation': 'explicit_admin_exact_negative_fixture_cleanup', 'purpose': purpose,
            'command': ['docker', kind, 'rm', identity], 'exit_status': result.returncode,
            'exact_id': identity, 'independent_before': before, 'absent_after': True})


def main():
    # Independent processes share the one locked state: no per-JVM/per-process counter.
    with ThreadPoolExecutor(max_workers=4) as pool:
        futures = [pool.submit(subprocess.run, ['python3', '-B', os.environ['VRA_POC04_RUN_HARNESS'],
                                                'allocate', 'teardown-probe'], capture_output=True, text=True) for _ in range(16)]
        results = [future.result() for future in futures]
    assert all(result.returncode == 0 for result in results)
    allocations = [json.loads(result.stdout) for result in results]
    assert len({a['ordinal'] for a in allocations}) == 16
    assert len({a['database_instance_id'] for a in allocations}) == 16
    namespace = uuid.UUID(os.environ['VRA_POC04_RUN_ID'])
    assert all(str(uuid.uuid5(namespace, a['logical_name'])) == a['database_instance_id'] for a in allocations)
    record({'operation': 'independent_process_allocator_proof', 'processes': 16, 'exit_statuses': [r.returncode for r in results],
            'allocations': allocations, 'unique_ordinals': True, 'exact_uuidv5': True, 'status': 'PASS'})
    allocation = allocations[0]
    labels = allocation['labels']
    other_labels = dict(labels)
    other_labels['dev.vra.run_id'] = str(uuid.uuid4())
    other_labels['dev.vra.logical_name'] = 'teardown-probe/other-run-sentinel'
    other_labels['dev.vra.database_instance_id'] = str(uuid.uuid5(uuid.UUID(other_labels['dev.vra.run_id']), other_labels['dev.vra.logical_name']))
    sentinel, sentinel_before = create('volume', 'vra-poc04-negative-sentinel-' + other_labels['dev.vra.run_id'], other_labels)
    current = []
    for kind in ('volume', 'network'):
        identity, _ = create(kind, 'vra-poc04-negative-current-' + str(namespace) + '-' + kind, labels)
        cli('register', '--kind', kind, '--id', identity, '--instance', allocation['database_instance_id'])
        current.append((kind, identity))
    for kind, identity in current:
        result = cli('teardown', '--kind', kind, '--id', identity)
        assert result['absent'] and present(kind, identity) is None
    assert present('volume', sentinel) == sentinel_before
    record({'operation': 'current_run_teardown_sentinel_preserved', 'authorized_exact_resources': current,
            'sentinel_exact_id': sentinel, 'before': sentinel_before, 'after': present('volume', sentinel), 'status': 'PASS'})
    denial = cli('teardown', '--kind', 'volume', '--id', sentinel, expected=3)
    assert denial['denied'] and not denial['docker_delete_called']
    assert denial['reason'] == 'RESOURCE_NOT_IN_AUTHORIZED_CURRENT_RUN_LEDGER'
    assert present('volume', sentinel) == sentinel_before
    record({'operation': 'wrong_id_denied_before_delete', 'denial': denial, 'resource_unchanged': True, 'status': 'PASS'})
    candidate, original = create('volume', 'vra-poc04-negative-wrong-label-' + str(namespace), labels)
    cli('register', '--kind', 'volume', '--id', candidate, '--instance', allocation['database_instance_id'])
    # Controlled substitution proves live ownership is checked even for a previously authorized exact volume name.
    explicit_admin_cleanup('volume', candidate, original, 'prepare controlled wrong-label substitution')
    wrong_labels = dict(labels)
    del wrong_labels['dev.vra.source_content_sha256']
    replacement, replacement_before = create('volume', candidate, wrong_labels)
    assert replacement == candidate
    denial = cli('teardown', '--kind', 'volume', '--id', candidate, expected=3)
    assert denial['denied'] and not denial['docker_delete_called']
    assert denial['reason'] == 'LIVE_OWNERSHIP_LABEL_MISMATCH'
    assert present('volume', candidate) == replacement_before
    record({'operation': 'wrong_missing_label_denied_before_delete', 'denial': denial,
            'before': replacement_before, 'after': present('volume', candidate), 'status': 'PASS'})
    explicit_admin_cleanup('volume', candidate, replacement_before, 'remove preserved wrong-label negative candidate')
    assert present('volume', sentinel) == sentinel_before
    explicit_admin_cleanup('volume', sentinel, sentinel_before, 'remove preserved other-run negative sentinel')
    record({'operation': 'focused_teardown_negative_proof', 'status': 'PASS',
            'all_current_exact_ids_absent': all(present(k, i) is None for k, i in current),
            'sentinel_absent_after_separate_admin_cleanup': present('volume', sentinel) is None,
            'wrong_label_candidate_absent_after_separate_admin_cleanup': present('volume', candidate) is None})
    print('Focused current-run ledger/negative teardown proof PASS')


if __name__ == '__main__':
    main()
