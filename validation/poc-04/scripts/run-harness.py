"""Administrative disposable TEST allocator/ledger. Daemon observation confers no deletion authority."""
import argparse
import contextlib
import datetime
import fcntl
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import uuid


def now():
    return datetime.datetime.now(datetime.timezone.utc).isoformat()


def require(condition, code):
    if not condition:
        raise RuntimeError(code)


def state_path():
    path = Path(os.environ['VRA_POC04_RUN_STATE'])
    require(path.is_absolute() and not path.is_symlink(), 'INVALID_RUN_STATE_PATH')
    require(path.parent.stat().st_mode & 0o077 == 0, 'RUN_STATE_DIRECTORY_NOT_RESTRICTED')
    return path


@contextlib.contextmanager
def transaction():
    path = state_path()
    fd = os.open(str(path) + '.lock', os.O_RDWR | os.O_CREAT, 0o600)
    with os.fdopen(fd, 'r+') as lock:
        fcntl.flock(lock, fcntl.LOCK_EX)
        require(path.stat().st_mode & 0o077 == 0, 'RUN_STATE_NOT_RESTRICTED')
        state = json.loads(path.read_text())
        require(state['run_id'] == os.environ['VRA_POC04_RUN_ID'], 'AUTHORIZED_RUN_MISMATCH')
        require(uuid.UUID(state['run_id']).version == 4, 'RUN_ID_NOT_UUIDV4')
        yield state
        # Atomic replace while the separate lock inode remains stable across processes.
        with tempfile.NamedTemporaryFile(mode='w', dir=path.parent, delete=False) as tmp:
            os.chmod(tmp.name, 0o600)
            json.dump(state, tmp, sort_keys=True, indent=2)
            tmp.write('\n')
            tmp.flush()
            os.fsync(tmp.fileno())
        os.replace(tmp.name, path)


def docker(state, argv):
    require(os.environ.get('DOCKER_HOST') == state['docker_host'], 'DOCKER_ENDPOINT_MISMATCH')
    env = os.environ.copy()
    env['DOCKER_HOST'] = state['docker_host']
    return subprocess.run(['docker'] + argv, env=env, capture_output=True, text=True, timeout=60)


def attest(state):
    r = docker(state, ['info', '--format', '{{.ID}}'])
    require(r.returncode == 0 and r.stdout.strip() == state['daemon_id'], 'DAEMON_ID_MISMATCH')


def labels(state, logical):
    return {'dev.vra.environment': 'TEST', 'dev.vra.poc': '04',
            'dev.vra.run_id': state['run_id'],
            'dev.vra.database_instance_id': str(uuid.uuid5(uuid.UUID(state['run_id']), logical)),
            'dev.vra.logical_name': logical, 'dev.vra.source_head': state['source_head'],
            'dev.vra.source_content_sha256': state['source_content_sha256'],
            'dev.vra.tool_manifest_sha256': state['tool_manifest_sha256']}


def inspect(state, kind, identity):
    argv = ['inspect', identity] if kind == 'container' else [kind, 'inspect', identity]
    r = docker(state, argv)
    if r.returncode != 0:
        require('no such' in r.stderr.lower() or 'not found' in r.stderr.lower(), 'INSPECTION_ERROR')
        return None
    value = json.loads(r.stdout)[0]
    actual_id = value['Name'] if kind == 'volume' else value['Id']
    require(actual_id == identity, 'EXACT_RESOURCE_ID_MISMATCH')
    actual_labels = value.get('Config', {}).get('Labels') if kind == 'container' else value.get('Labels')
    return {'kind': kind, 'id': actual_id, 'name': value.get('Name'),
            'created': value.get('Created', value.get('CreatedAt')),
            'labels': actual_labels or {}, 'mountpoint': value.get('Mountpoint')}


def matching(required, actual):
    return all(actual.get(k) == v for k, v in required.items())


def authorization(state, kind, identity):
    # Never derive authority from an observed resource, name or UUID format.
    key = kind + ':' + identity
    require(key in state['resources'], 'RESOURCE_NOT_IN_AUTHORIZED_CURRENT_RUN_LEDGER')
    entry = state['resources'][key]
    require(entry['authorized_run_id'] == state['run_id'], 'LEDGER_RUN_MISMATCH')
    allocation = state['allocations'].get(entry['database_instance_id'])
    require(allocation is not None, 'LEDGER_INSTANCE_NOT_ALLOCATED')
    require(entry['expected_labels'] == allocation['labels'], 'LEDGER_BINDING_MISMATCH')
    actual = inspect(state, kind, identity)
    if actual is not None:
        require(matching(entry['expected_labels'], actual['labels']), 'LIVE_OWNERSHIP_LABEL_MISMATCH')
        require(actual['created'] == entry['created'] and actual['name'] == entry['name'],
                'RESOURCE_CREATION_IDENTITY_MISMATCH')
        if kind == 'volume':
            require(actual['mountpoint'] == entry['mountpoint'], 'VOLUME_MOUNTPOINT_MISMATCH')
    return entry, actual


def operate(args):
    with transaction() as state:
        if args.command == 'allocate':
            require(args.role in ('postgres-authority', 'postgres-simulator', 'testcontainers-helper',
                                  'teardown-probe'), 'UNSUPPORTED_LOGICAL_ROLE')
            ordinal = state['next_ordinal'].get(args.role, 0)
            state['next_ordinal'][args.role] = ordinal + 1
            logical = args.role + '/' + str(ordinal)
            bound = labels(state, logical)
            result = {'ordinal': ordinal, 'logical_name': logical,
                      'database_instance_id': bound['dev.vra.database_instance_id'], 'labels': bound}
            require(result['database_instance_id'] not in state['allocations'], 'ALLOCATOR_ID_REUSE')
            state['allocations'][result['database_instance_id']] = result
            state['events'].append({'at': now(), 'operation': 'allocate', **result})
            return result
        if args.command == 'allocation':
            require(args.instance in state['allocations'], 'UNALLOCATED_INSTANCE')
            return state['allocations'][args.instance]
        if args.command in ('register', 'authorize', 'teardown', 'bind-physical', 'finish'):
            attest(state)
        if args.command == 'register':
            # Called only by trusted creator immediately after Docker returns this exact identity.
            require(args.instance in state['allocations'], 'UNALLOCATED_INSTANCE')
            allocation = state['allocations'][args.instance]
            actual = inspect(state, args.kind, args.id)
            require(actual is not None, 'CREATED_RESOURCE_ABSENT')
            require(matching(allocation['labels'], actual['labels']), 'CREATION_LABEL_MISMATCH')
            key = args.kind + ':' + args.id
            entry = {**actual, 'authorized_run_id': state['run_id'],
                     'database_instance_id': args.instance, 'logical_name': allocation['logical_name'],
                     'expected_labels': allocation['labels']}
            if key in state['resources']:
                require(state['resources'][key] == entry, 'RESOURCE_RE_REGISTRATION_MISMATCH')
            else:
                state['resources'][key] = entry
                state['events'].append({'at': now(), 'operation': 'creator_registered', **entry})
            return entry
        if args.command in ('authorize', 'teardown'):
            try:
                entry, actual = authorization(state, args.kind, args.id)
            except RuntimeError as error:
                state['events'].append({'at': now(), 'operation': args.command + '_denied', 'kind': args.kind,
                                        'id': args.id, 'reason': str(error), 'docker_delete_called': False})
                # Commit the denial before returning the nonzero status.
                return {'denied': True, 'reason': str(error), 'docker_delete_called': False,
                        'kind': args.kind, 'id': args.id}
            if args.command == 'authorize':
                result = {'kind': args.kind, 'id': args.id, 'authorized': True,
                          'authorized_run_id': state['run_id'], 'already_absent': actual is None,
                          'database_instance_id': entry['database_instance_id'], 'logical_name': entry['logical_name'],
                          'docker_delete_called': False}
                state['events'].append({'at': now(), 'operation': 'native_lifecycle_teardown_authorized', **result})
                return result
            if actual is not None:
                argv = ['rm', '-f', args.id] if args.kind == 'container' else [args.kind, 'rm', args.id]
                r = docker(state, argv)
                require(r.returncode == 0, 'EXACT_AUTHORIZED_DELETION_FAILED')
            require(inspect(state, args.kind, args.id) is None, 'RESOURCE_REMAINS_AFTER_TEARDOWN')
            result = {'kind': args.kind, 'id': args.id, 'already_absent': actual is None,
                      'absent': True, 'authorized_run_id': state['run_id']}
            state['events'].append({'at': now(), 'operation': 'authorized_teardown', **result})
            return result
        if args.command == 'bind-physical':
            entry, actual = authorization(state, 'container', args.container)
            require(actual is not None and entry['database_instance_id'] == args.instance, 'PHYSICAL_CONTAINER_BINDING_MISMATCH')
            allocation = state['allocations'][args.instance]
            require(allocation['logical_name'].startswith(('postgres-authority/', 'postgres-simulator/')), 'NON_DATABASE_ALLOCATION')
            r = docker(state, ['exec', args.container, 'psql', '-U', args.user, '-d', args.database, '-Atq', '-c',
                              "SELECT system_identifier::text||'|'||current_setting('server_version_num') FROM pg_control_system()"])
            require(r.returncode == 0, 'POSTGRES_PHYSICAL_IDENTITY_UNAVAILABLE')
            system, version = r.stdout.strip().split('|')
            require(system.isdigit() and version == '170011', 'POSTGRES_VERSION_OR_IDENTITY_MISMATCH')
            raw = docker(state, ['inspect', args.container])
            require(raw.returncode == 0, 'PHYSICAL_MOUNT_INSPECTION_FAILED')
            mounts = [m for m in json.loads(raw.stdout)[0]['Mounts'] if m['Destination'] == '/var/lib/postgresql/data']
            require(len(mounts) == 1 and mounts[0]['Type'] == 'volume', 'PGDATA_NOT_EXACT_VOLUME')
            volume = mounts[0]['Name']
            ve, va = authorization(state, 'volume', volume)
            require(va is not None and ve['database_instance_id'] == args.instance, 'PGDATA_LEDGER_INSTANCE_MISMATCH')
            result = {'database_instance_id': args.instance, 'logical_name': allocation['logical_name'],
                      'container_id': args.container, 'database': args.database, 'system_identifier': system,
                      'server_version_num': version, 'volume_name': volume}
            old = state['physical_instances'].get(args.instance)
            if old is not None:
                # A creator-authorized incarnation may change; durable cluster identity may not.
                require(all(old[k] == v for k, v in result.items() if k != 'container_id'),
                        'INSTANCE_UUID_MAPPED_TO_DIFFERENT_PHYSICAL_DATABASE')
            else:
                require(all(x['system_identifier'] != system for x in state['physical_instances'].values()),
                        'PHYSICAL_DATABASE_HAS_MULTIPLE_INSTANCE_UUIDS')
                state['physical_instances'][args.instance] = result
            state['events'].append({'at': now(), 'operation': 'physical_identity', **result})
            return result
        if args.command == 'finish':
            for key, entry in state['resources'].items():
                require(inspect(state, entry['kind'], entry['id']) is None, 'RUN_RESOURCE_STILL_PRESENT')
            databases = {e['database_instance_id'] for e in state['resources'].values()
                         if e['kind'] == 'container' and e['logical_name'].startswith(('postgres-authority/', 'postgres-simulator/'))}
            require(databases == set(state['physical_instances']), 'DATABASE_PHYSICAL_CAPTURE_INCOMPLETE')
            require(len({x['system_identifier'] for x in state['physical_instances'].values()}) == len(databases),
                    'FINAL_PHYSICAL_IDENTITY_COLLISION')
            for instance in databases:
                systems = {e['system_identifier'] for e in state['events']
                           if e['operation'] == 'physical_identity' and e['database_instance_id'] == instance}
                require(systems == {state['physical_instances'][instance]['system_identifier']},
                        'FINAL_INSTANCE_UUID_HAS_MULTIPLE_PHYSICAL_SYSTEM_IDENTIFIERS')
            result = {'status': 'PASS', 'resources_absent': len(state['resources']),
                      'database_identity_map': list(state['physical_instances'].values()),
                      'all_database_instances_bound_once': True}
            state['events'].append({'at': now(), 'operation': 'final_consistency', 'status': 'PASS'})
            return result
        raise RuntimeError('UNKNOWN_OPERATION')


def main():
    parser = argparse.ArgumentParser()
    sub = parser.add_subparsers(dest='command', required=True)
    sub.add_parser('allocate').add_argument('role')
    sub.add_parser('allocation').add_argument('instance')
    for command in ('register', 'authorize', 'teardown'):
        child = sub.add_parser(command)
        child.add_argument('--kind', choices=('container', 'volume', 'network'), required=True)
        child.add_argument('--id', required=True)
        if command == 'register':
            child.add_argument('--instance', required=True)
    child = sub.add_parser('bind-physical')
    child.add_argument('--instance', required=True)
    child.add_argument('--container', required=True)
    child.add_argument('--database', required=True)
    child.add_argument('--user', default='postgres')
    sub.add_parser('finish')
    args = parser.parse_args()
    try:
        result = operate(args)
        print(json.dumps(result, sort_keys=True))
        return 3 if result.get('denied') else 0
    except Exception as error:
        print(json.dumps({'status': 'STOP', 'reason': str(error) if isinstance(error, RuntimeError) else type(error).__name__}), file=sys.stderr)
        return 1


if __name__ == '__main__':
    sys.exit(main())
