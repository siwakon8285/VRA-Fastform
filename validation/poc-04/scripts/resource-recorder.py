"""Safe isolated-daemon evidence only. Observation never registers teardown authority."""
import datetime
import json
import os
from pathlib import Path
import subprocess
import threading
import time

out = Path(os.environ['VRA_POC04_EVIDENCE_DIRECTORY'])
support = Path(os.environ['VRA_POC04_SUPPORT_DIRECTORY'])
state_path = Path(os.environ['VRA_POC04_RUN_STATE'])
projections = out / 'daemon-inspect'
projections.mkdir(exist_ok=True)
stop = support / 'recorder.stop'
ready = support / 'recorder.ready'
lock = threading.Lock()
records = out / 'docker-resource-events.jsonl'
harness = os.environ['VRA_POC04_RUN_HARNESS']


def emit(value):
    with lock:
        with records.open('a') as f:
            f.write(json.dumps(value, sort_keys=True) + '\n')


def command(argv):
    return subprocess.run(argv, capture_output=True, text=True, timeout=60)


def inspect(kind, identity):
    argv = ['docker', 'inspect', identity] if kind == 'container' else ['docker', kind, 'inspect', identity]
    result = command(argv)
    if result.returncode:
        return None
    value = json.loads(result.stdout)[0]
    if kind == 'container':
        safe = {'kind': 'container_inspect', 'id': value['Id'], 'name': value['Name'],
                'created': value.get('Created'), 'state': {k: value.get('State', {}).get(k) for k in ('Status', 'Running', 'ExitCode')},
                'image_id': value.get('Image'), 'image': value.get('Config', {}).get('Image'),
                'manifest_descriptor': value.get('ImageManifestDescriptor'),
                'labels': value.get('Config', {}).get('Labels') or {},
                'mounts': [{k: m.get(k) for k in ('Type', 'Name', 'Source', 'Destination', 'RW')} for m in value.get('Mounts', [])],
                'ports': value.get('NetworkSettings', {}).get('Ports'),
                'networks': {k: {'network_id': v.get('NetworkID')} for k, v in (value.get('NetworkSettings', {}).get('Networks') or {}).items()}}
    elif kind == 'volume':
        safe = {'kind': 'volume_inspect', **{k: value.get(k) for k in ('Name', 'CreatedAt', 'Driver', 'Mountpoint', 'Labels', 'Scope')}}
    else:
        safe = {'kind': 'network_inspect', **{k: value.get(k) for k in ('Id', 'Name', 'Created', 'Driver', 'Labels', 'Scope', 'IPAM')}}
    safe['daemon_id'] = os.environ['VRA_POC04_EXPECTED_DAEMON_ID']
    emit(safe)
    if kind == 'container' and safe['state']['Running']:
        target = projections / (identity + '.safe-inspect.json')
        temporary = projections / (identity + '.' + str(threading.get_ident()) + '.tmp')
        temporary.write_text(json.dumps(safe, sort_keys=True, indent=2) + '\n')
        os.replace(temporary, target)
    return value


def physical_identity(identity):
    # Readiness polling captures identity; no timing interval is used as correctness proof.
    deadline = time.monotonic() + 60
    while time.monotonic() < deadline and not stop.exists():
        try:
            state = json.loads(state_path.read_text())
            entry = state['resources'].get('container:' + identity)
            if entry is None:
                time.sleep(.05)
                continue
            if not entry['logical_name'].startswith(('postgres-authority/', 'postgres-simulator/')):
                return
            result = command(['docker', 'inspect', identity])
            if result.returncode:
                return
            value = json.loads(result.stdout)[0]
            # Never retain Config.Env or any password. Only these two nonsecret values are selected.
            fields = dict(x.split('=', 1) for x in value.get('Config', {}).get('Env', []) if '=' in x)
            database = fields.get('POSTGRES_DB', 'postgres')
            user = fields.get('POSTGRES_USER', 'postgres')
            argv = ['python3', '-B', harness, 'bind-physical', '--instance', entry['database_instance_id'],
                    '--container', identity, '--database', database, '--user', user]
            result = command(argv)
            if result.returncode == 0:
                emit({'kind': 'postgres_identity', 'command': argv, 'exit_status': 0, **json.loads(result.stdout)})
                return
        except (OSError, ValueError, subprocess.TimeoutExpired):
            pass
        time.sleep(.05)
    emit({'kind': 'physical_capture_failed', 'container_id': identity, 'at': datetime.datetime.now(datetime.timezone.utc).isoformat()})


attestation = command(['docker', 'info', '--format', '{{.ID}}'])
if attestation.returncode or attestation.stdout.strip() != os.environ['VRA_POC04_EXPECTED_DAEMON_ID']:
    raise RuntimeError('Dedicated daemon attestation failed before recorder')
process = subprocess.Popen(['docker', 'events', '--format', '{{json .}}', '--filter', 'type=container',
                            '--filter', 'type=volume', '--filter', 'type=network'],
                           stdout=subprocess.PIPE, stderr=subprocess.DEVNULL, text=True, bufsize=1)


def consume():
    for line in process.stdout:
        try:
            value = json.loads(line)
        except ValueError:
            continue
        actor = value.get('Actor') or {}
        attributes = actor.get('Attributes') or {}
        kind = value.get('Type')
        action = (value.get('Action') or '').split(':', 1)[0]
        identity = actor.get('ID') or value.get('id')
        # Exec argv can contain ephemeral credentials: never persist it.
        emit({'kind': 'docker_event', 'type': kind, 'action': action, 'id': identity, 'time_nano': value.get('timeNano'),
              'attributes': {k: v for k, v in attributes.items() if k in ('name', 'image', 'container', 'driver', 'type')
                             or k.startswith(('dev.vra.', 'org.testcontainers.', 'com.docker.compose.'))}})
        if action == 'create' or (kind == 'container' and action == 'start'):
            threading.Thread(target=inspect, args=(kind, identity), daemon=True).start()
        if kind == 'container' and action == 'start' and 'postgres' in attributes.get('image', ''):
            threading.Thread(target=physical_identity, args=(identity,), daemon=True).start()


threading.Thread(target=consume, daemon=True).start()
ready.write_text('ready\n')
print('Safe recorder ready; observations confer no teardown authorization', flush=True)
while not stop.exists():
    time.sleep(.2)
process.terminate()
process.wait(timeout=5)
print('Safe recorder stopped', flush=True)
