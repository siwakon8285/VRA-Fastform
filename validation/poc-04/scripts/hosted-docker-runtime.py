"""Pinned disposable Linux TEST runtime; no system-daemon fallback or resource discovery authority."""
import argparse
import base64
import hashlib
import http.client
import json
import os
from pathlib import Path, PurePosixPath
import platform
import re
import secrets
import shlex
import shutil
import socket
import stat
import struct
import subprocess
import sys
import tarfile
import time
import tomllib
import urllib.parse
import urllib.request
import uuid

REPOSITORY = Path(__file__).resolve().parents[3]
MANIFEST = REPOSITORY / 'validation/poc-04/tooling/hosted-docker-runtime.json'
PHASE0_TOOL_SHA = '5814b0ee6eb022acfb458b21b8a009592ea627c3d371cfc7b0963595c05892d8'
ORIGINAL_SOCKET_STOP_POLICY = '[Socket]\nRemoveOnStop=yes\n'
ENVIRONMENT_REJECT = ('DOCKER_API_VERSION', 'DOCKER_MIN_API_VERSION', 'DOCKER_CONTEXT',
                      'DOCKER_TLS_VERIFY', 'DOCKER_CERT_PATH', 'DOCKER_DRIVER',
                      'DOCKER_CONTAINERD_ROOT', 'DOCKER_RAMDISK')
PROC_ROOT = Path('/proc')
CGROUP_ROOT = Path('/sys/fs/cgroup')
RUN_PARENT = Path('/run')
SHIM_NAME = 'containerd-shim-runc-v2'
UNIT_PROPERTIES = ('Id', 'LoadState', 'ActiveState', 'SubState', 'MainPID', 'ControlPID',
                   'ControlGroup', 'Description', 'Transient', 'Slice', 'KillMode', 'Restart', 'SendSIGKILL')


def require(condition, message):
    if not condition:
        raise RuntimeError(message)


def digest(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def write(path, value):
    path = Path(path)
    temporary = path.with_suffix(path.suffix + '.tmp')
    temporary.write_text(json.dumps(value, indent=2, sort_keys=True) + '\n')
    temporary.chmod(0o600)
    os.replace(temporary, path)


def load_manifest(path=MANIFEST):
    def unique(pairs):
        result = {}
        for key, value in pairs:
            require(key not in result, 'Duplicate manifest field')
            result[key] = value
        return result
    value = json.loads(Path(path).read_text(), object_pairs_hook=unique)
    require(value['schema_version'] == 1 and value['platform'] == {'os': 'linux', 'architecture': 'amd64'},
            'Unreviewed manifest schema/platform')
    docker = value['docker']
    require(docker['version'] == '29.8.2' and docker['server_api'] == '1.56' and
            docker['minimum_server_api'] == '1.40' and docker['required_api_floor'] == '1.49' and
            docker['client_api'] == '1.49', 'Unreviewed Docker version/API contract')
    require(docker['archive']['url'] ==
            'https://download.docker.com/linux/static/stable/x86_64/docker-29.8.2.tgz',
            'Unreviewed Docker distribution URL')
    require(value['components'] == {'containerd': '2.3.6', 'runc': '1.5.2'}, 'Unreviewed runtime components')
    require(value['image_store'] == {'driver': 'overlayfs', 'driver_type': 'io.containerd.snapshotter.v1',
                                    'containerd_snapshotter': True}, 'Unreviewed image-store contract')
    require(value['buildx']['version'] == '0.37.2' and value['buildx']['url'] ==
            'https://github.com/docker/buildx/releases/download/v0.37.2/buildx-v0.37.2.linux-amd64',
            'Unreviewed Buildx artifact')
    return value


def verify_download(path, expected):
    require(Path(path).is_file() and not Path(path).is_symlink(), 'Download is not a regular file')
    require(Path(path).stat().st_size == expected['size'], 'Artifact size mismatch')
    require(digest(path) == expected['sha256'], 'Artifact SHA-256 mismatch')


def verify_elf(data):
    require(len(data) >= 20 and data[:6] == b'\x7fELF\x02\x01' and
            struct.unpack_from('<H', data, 18)[0] == 62, 'Executable is not Linux ELF64 amd64')


def verify_archive(path, expected):
    """Read every member and verify the entire contract before any executable publication."""
    verify_download(path, expected)
    rows = expected['members']
    require(len({row['name'] for row in rows}) == len(rows), 'Duplicate approved member')
    approved = {row['name']: row for row in rows}
    seen, payload = set(), {}
    with tarfile.open(path, 'r:gz') as archive:
        for member in archive:
            name = member.name.rstrip('/')
            parsed = PurePosixPath(name)
            require(name and not parsed.is_absolute() and '..' not in parsed.parts and
                    str(parsed) == name and '\\' not in name, 'Unsafe archive member path')
            require(name not in seen, 'Duplicate archive member')
            seen.add(name)
            require(name in approved, 'Unexpected archive member')
            row = approved[name]
            require(member.mode == int(row['mode'], 8), 'Archive member mode mismatch')
            if row['type'] == 'directory':
                require(member.isdir(), 'Archive directory type mismatch')
                continue
            require(member.isfile() and not member.issym() and not member.islnk(), 'Unsafe archive member type')
            require(member.size == row['size'], 'Archive member size mismatch')
            data = archive.extractfile(member).read()
            require(hashlib.sha256(data).hexdigest() == row['sha256'], 'Archive member SHA-256 mismatch')
            verify_elf(data)
            payload[PurePosixPath(name).name] = data
    require(seen == set(approved), 'Missing approved archive member')
    return payload


def verify_buildx(path, expected):
    verify_download(path, expected)
    verify_elf(Path(path).read_bytes())


class OfficialRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, request, fp, code, message, headers, newurl):
        parsed = urllib.parse.urlsplit(newurl)
        require(parsed.scheme == 'https' and parsed.hostname == 'release-assets.githubusercontent.com'
                and not parsed.username, 'Unreviewed download redirect')
        return super().redirect_request(request, fp, code, message, headers, newurl)


def acquire(expected, destination, github=False):
    destination = Path(destination)
    require(not destination.exists(), 'Artifact destination already exists')
    parsed = urllib.parse.urlsplit(expected['url'])
    require(parsed.scheme == 'https' and not parsed.username and not parsed.query and
            parsed.hostname == ('github.com' if github else 'download.docker.com'), 'Unreviewed acquisition authority')
    class NoRedirect(urllib.request.HTTPRedirectHandler):
        def redirect_request(self, *args, **kwargs):
            raise RuntimeError('Docker archive redirect prohibited')
    opener = urllib.request.build_opener(OfficialRedirect() if github else NoRedirect())
    incomplete = destination.with_suffix(destination.suffix + '.incomplete')
    with opener.open(expected['url'], timeout=60) as response, incomplete.open('xb') as output:
        require(response.headers.get_content_type() in
                ('application/octet-stream', 'application/gzip', 'application/x-gzip',
                 'application/x-compressed-tar', 'binary/octet-stream'),
                'Unexpected artifact content type')
        size = 0
        while chunk := response.read(1024 * 1024):
            size += len(chunk)
            require(size <= expected['size'], 'Artifact exceeds pinned size')
            output.write(chunk)
    verify_download(incomplete, expected)
    os.replace(incomplete, destination)


def api_version(value):
    require(isinstance(value, str) and re.fullmatch(r'[0-9]+\.[0-9]+', value), 'Malformed Docker API version')
    return tuple(map(int, value.split('.')))


def process_incarnation(identity):
    require(type(identity.get('pid')) is int and identity['pid'] > 0 and
            isinstance(identity.get('start_time'), str) and re.fullmatch(r'[0-9]+', identity['start_time']) and
            isinstance(identity.get('boot_id'), str) and re.fullmatch(
                r'[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}', identity['boot_id']),
            'Process incarnation authority unavailable')
    return identity['pid'], identity['boot_id'], identity['start_time']


def validate_server(version, info, manifest, expected_id=None):
    require(api_version(version.get('ApiVersion')) >= (1, 49), 'Docker Engine API >=1.49 required')
    require(version.get('Version') == manifest['docker']['version'] and
            version.get('ApiVersion') == manifest['docker']['server_api'] and
            version.get('MinAPIVersion') == manifest['docker']['minimum_server_api'], 'Pinned server version/API differs')
    require(version.get('Os') == 'linux' and version.get('Arch') == 'amd64' and
            info.get('OSType') == 'linux' and info.get('Architecture') in ('amd64', 'x86_64'), 'Wrong daemon architecture')
    require(info.get('ID') and (expected_id is None or info['ID'] == expected_id), 'Wrong daemon identity')
    require(info.get('Driver') == manifest['image_store']['driver'] and
            ['driver-type', manifest['image_store']['driver_type']] in info.get('DriverStatus', []),
            'Required containerd image store is not effective')


def validate_empty(inventory):
    require(not inventory['containers'] and not inventory['volumes'], 'Original/TEST daemon is not empty')
    require({row['Name'] for row in inventory['networks']} == {'bridge', 'host', 'none'} and
            len(inventory['networks']) == 3, 'Unexpected daemon networks')


def validate_executable(path, expected_path, expected_hash):
    path, expected_path = Path(path), Path(expected_path)
    require(path == expected_path and path.resolve() == expected_path and path.is_file() and
            not path.is_symlink() and digest(path) == expected_hash, 'Executable identity mismatch')


def auxiliary_authority(run_id, token):
    require(str(uuid.UUID(run_id)) == run_id, 'Invalid owned run identity')
    require(isinstance(token, str) and re.fullmatch(r'[0-9a-f]{24}', token), 'Invalid auxiliary token')
    root = RUN_PARENT / ('vra-p04-' + token)
    socket_dir = root / 's'
    require(str(RUN_PARENT) == '/run' and len(os.fsencode(str(root))) == 37 and
            len(os.fsencode(str(socket_dir))) == 39 and len(os.fsencode(str(socket_dir))) <= 42,
            'Auxiliary socket path contract differs')
    return {'run_id': run_id, 'token': token, 'root': str(root), 'cwd': str(root / 'cwd'),
            'opt': str(root / 'opt'), 'opt_bin': str(root / 'opt/bin'),
            'opt_lib': str(root / 'opt/lib'), 'socket_dir': str(socket_dir),
            'shim': str(root / 'opt/bin' / SHIM_NAME)}


def validate_owned_path(value, path, kind, mode, expected_hash=None):
    require(value.get('exists') is True and value.get('kind') == kind and
            value.get('symlink') is False and value.get('realpath') == str(path) and
            value.get('uid') == 0 and value.get('gid') == 0 and value.get('mode') == mode,
            'Owned path identity differs: ' + str(path))
    if expected_hash is not None:
        require(value.get('sha256') == expected_hash, 'Owned executable digest differs: ' + str(path))


# These fixed programs run through sudo because a 0700 root-owned namespace is
# deliberately inaccessible to the unprivileged runner. Unknown reads are errors,
# never absence. No repository program is imported into the privileged interpreter.
PATH_OBSERVATION = '''
import hashlib, json, os, socket, stat, struct, sys
from pathlib import Path
p = Path(sys.argv[1]); flags = set(sys.argv[2:])
try:
    s = p.lstat()
except FileNotFoundError:
    print(json.dumps({'exists': False}))
    sys.exit(0)
def kind(s):
    return ('symlink' if stat.S_ISLNK(s.st_mode) else 'directory' if stat.S_ISDIR(s.st_mode)
            else 'regular' if stat.S_ISREG(s.st_mode) else 'socket' if stat.S_ISSOCK(s.st_mode) else 'other')
v = {'exists': True, 'kind': kind(s), 'symlink': stat.S_ISLNK(s.st_mode),
     'realpath': str(p.resolve(strict=True)), 'uid': s.st_uid, 'gid': s.st_gid,
     'mode': format(stat.S_IMODE(s.st_mode), '04o'), 'device': s.st_dev, 'inode': s.st_ino}
if 'sha256' in flags:
    if kind(s) != 'regular': raise RuntimeError('Hash target is not a regular file')
    fd = os.open(p, os.O_RDONLY | os.O_NOFOLLOW)
    with os.fdopen(fd, 'rb') as f: v['sha256'] = hashlib.sha256(f.read()).hexdigest()
if 'text' in flags:
    if kind(s) != 'regular': raise RuntimeError('Text target is not a regular file')
    fd = os.open(p, os.O_RDONLY | os.O_NOFOLLOW)
    with os.fdopen(fd, 'rb') as f: data = f.read(65537)
    if len(data) > 65536: raise RuntimeError('Owned metadata exceeds bound')
    v['text'] = data.decode('utf-8')
if 'peer' in flags:
    if kind(s) != 'socket': raise RuntimeError('Peer target is not a socket')
    with socket.socket(socket.AF_UNIX, socket.SOCK_STREAM) as c:
        c.settimeout(1); c.connect(str(p))
        v['peer'] = list(struct.unpack('3i', c.getsockopt(socket.SOL_SOCKET, socket.SO_PEERCRED, 12)))
if 'entries' in flags:
    if kind(s) != 'directory': raise RuntimeError('Inventory target is not a directory')
    v['entries'] = sorted(os.listdir(p))
if 'recursive' in flags:
    if kind(s) != 'directory': raise RuntimeError('Endpoint scope is not a directory')
    v['nodes'] = []
    for base, dirs, files in os.walk(p, followlinks=False, onerror=lambda e: (_ for _ in ()).throw(e)):
        for name in sorted(dirs + files):
            q = Path(base) / name; t = q.lstat()
            if stat.S_ISLNK(t.st_mode): raise RuntimeError('Endpoint scope contains a symlink')
            v['nodes'].append({'path': str(q.relative_to(p)), 'kind': kind(t)})
print(json.dumps(v))
'''

SHIM_PUBLICATION = '''
import hashlib, os, re, stat, sys
from pathlib import Path
p = Path(sys.argv[1]); expected = sys.argv[2]; size = int(sys.argv[3])
token = sys.argv[4]
if not re.fullmatch('[0-9a-f]{24}', token) or p != Path('/run/vra-p04-' + token) / 'opt/bin/containerd-shim-runc-v2':
    raise RuntimeError('Shim publication path authority differs')
data = sys.stdin.buffer.read(size + 1)
if os.geteuid() != 0 or len(data) != size or hashlib.sha256(data).hexdigest() != expected:
    raise RuntimeError('Shim publication bytes/owner differ')
for q in (p.parent.parent.parent, p.parent.parent, p.parent):
    s = q.lstat()
    if not stat.S_ISDIR(s.st_mode) or s.st_uid != 0 or s.st_gid != 0 or stat.S_IMODE(s.st_mode) != 0o700:
        raise RuntimeError('Shim publication directory differs')
fd = os.open(p, os.O_WRONLY | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW, 0o755)
with os.fdopen(fd, 'wb') as f:
    f.write(data); f.flush(); os.fsync(f.fileno()); os.fchmod(f.fileno(), 0o755)
'''


def decode_runc_options(value):
    """Bounded decoder for the pinned containerd.runc.v1.Options wire contract."""
    require(isinstance(value, dict) and value.get('type_url') == 'containerd.runc.v1.Options' and
            isinstance(value.get('value'), str) and len(value['value']) <= 87384,
            'Typed runc options authority differs')
    data = base64.b64decode(value['value'], validate=True)
    require(len(data) <= 65536, 'Typed runc options exceed bound')
    offset, fields = 0, {}
    def number():
        nonlocal offset
        result = 0
        for shift in range(0, 70, 7):
            require(offset < len(data), 'Truncated runc options')
            byte = data[offset]; offset += 1
            require(shift != 63 or byte <= 1, 'Oversized runc option integer')
            result |= (byte & 127) << shift
            if byte < 128:
                return result
        raise RuntimeError('Malformed runc option integer')
    while offset < len(data):
        tag = number(); key, wire = tag >> 3, tag & 7
        require(key in (1, 2, 3, 4, 5, 6, 7, 9, 10, 11, 12, 13) and key not in fields,
                'Unknown/duplicate runc option field')
        if key in (1, 2, 4, 5, 9, 13):
            require(wire == 0, 'Runc integer wire type differs')
            fields[key] = number()
        else:
            require(wire == 2, 'Runc string wire type differs')
            size = number()
            require(offset + size <= len(data), 'Truncated runc option string')
            fields[key] = data[offset:offset + size].decode('utf-8'); offset += size
    require(isinstance(fields.get(6), str) and Path(fields[6]).is_absolute() and
            fields.get(3, '') == '', 'Absolute runc/owned shim cgroup options differ')
    return {'binary_name': fields[6], 'shim_cgroup': fields.get(3, '')}


def validate_default_runtime(info, path):
    selected = (info.get('Runtimes') or {}).get('vra-pinned-runc', {})
    require(info.get('DefaultRuntime') == 'vra-pinned-runc' and
            selected.get('path') == str(path) and not selected.get('runtimeArgs') and
            not selected.get('runtimeType') and not selected.get('options'),
            'Live Docker default Path runtime differs')


def validate_descriptor(container, pin):
    expected = {'mediaType': 'application/vnd.oci.image.manifest.v1+json', 'digest': pin['digest'],
                'size': pin['response']['response_bytes'], 'platform': pin['platform']}
    value = container.get('ImageManifestDescriptor')
    require(isinstance(value, dict) and all(value.get(key) == item for key, item in expected.items()),
            'API-1.49 ImageManifestDescriptor differs from immutable image pin')
    return expected


class UnixHTTP(http.client.HTTPConnection):
    def __init__(self, path):
        super().__init__('localhost', timeout=20)
        self.path = str(path)

    def connect(self):
        self.sock = socket.socket(socket.AF_UNIX, socket.SOCK_STREAM)
        self.sock.settimeout(self.timeout)
        self.sock.connect(self.path)


def request(socket_path, method, path, body=None, api="1.49"):
    connection = UnixHTTP(socket_path)
    try:
        connection.request(method, '/v' + api + path, None if body is None else json.dumps(body),
                           {'Content-Type': 'application/json'})
        response = connection.getresponse()
        raw = response.read(8 * 1024 * 1024 + 1)
        require(len(raw) <= 8 * 1024 * 1024, 'Docker response exceeds bound')
        require(200 <= response.status < 300, 'Docker capability request failed: ' + method + ' ' + path.split('?')[0])
        return json.loads(raw) if raw else None
    finally:
        connection.close()


def inspect_inventory(socket_path, api="1.49"):
    # Arbitrary existing container commands, labels and environment are not safe evidence.
    containers = request(socket_path, 'GET', '/containers/json?all=1', api=api)
    volumes = request(socket_path, 'GET', '/volumes', api=api).get('Volumes') or []
    networks = request(socket_path, 'GET', '/networks', api=api)
    return {'containers': [{'Id': row['Id']} for row in containers],
            'volumes': [{'identity': 'UNEXPECTED EXISTING VOLUME; name withheld'} for _ in volumes],
            'networks': [{'Id': row['Id'], 'Name': row['Name'] if row['Name'] in ('bridge', 'host', 'none')
                         else 'UNEXPECTED NETWORK; name withheld'} for row in networks]}


def external_root(path):
    root = Path(path).absolute()
    base = Path(os.environ['RUNNER_TEMP']).resolve()
    require(root.parent.resolve() == base and root.name.startswith('vra-poc04-docker-') and
            root.resolve() == root and not root.is_symlink() and not root.is_relative_to(REPOSITORY),
            'Runtime root must be a fresh direct RUNNER_TEMP child outside repository')
    require(len(os.fsencode(str(root / 'run/containerd.sock.ttrpc'))) < 108,
            'Runtime Unix socket path exceeds Linux limit')
    return root


def verify_hosted_runtime_attestation(env, manifest_path=MANIFEST):
    """A hosted bootstrap consumes explicit external proof and independently checks executable bytes."""
    path = Path(env.get('VRA_POC04_HOSTED_RUNTIME_ATTESTATION', ''))
    require(path.is_absolute() and path.is_file() and not path.is_symlink(), 'Pinned runtime attestation absent')
    root = path.parent.parent
    require(root.parent == Path(env['RUNNER_TEMP']).resolve() and root.resolve() == root and
            root.name.startswith('vra-poc04-docker-') and path == root / 'evidence/runtime-attestation.json' and
            not root.is_relative_to(REPOSITORY), 'Runtime attestation must be external')
    value = json.loads(path.read_text())
    manifest = load_manifest(manifest_path)
    require(value['schema_version'] == 1 and value['status'] == 'PASS' and
            value['manifest_sha256'] == digest(manifest_path), 'Runtime manifest/attestation differs')
    expected_cli = root / 'bin/docker'
    expected_hash = next(row['sha256'] for row in manifest['docker']['archive']['members'] if row['name'] == 'docker/docker')
    require(value['cli_path'] == str(expected_cli) and value['cli_sha256'] == expected_hash, 'Attested CLI authority differs')
    validate_executable(shutil.which('docker', path=env.get('PATH', '')), expected_cli, expected_hash)
    validate_executable(root / 'docker-config/cli-plugins/docker-buildx',
                        root / 'docker-config/cli-plugins/docker-buildx', manifest['buildx']['sha256'])
    expected = {'DOCKER_HOST': 'unix://' + str(root / 'run/docker.sock'),
                'VRA_POC04_DISPOSABLE_TEST_DOCKER_ENDPOINT': 'unix://' + str(root / 'run/docker.sock'),
                'VRA_POC04_EXPECTED_DAEMON_ID': value['daemon_id'],
                'TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE': str(root / 'run/docker.sock'),
                'TESTCONTAINERS_HOST_OVERRIDE': '127.0.0.1', 'TESTCONTAINERS_RYUK_DISABLED': 'false',
                'DOCKER_CONFIG': str(root / 'docker-config'), 'BUILDX_CONFIG': str(root / 'buildx-state'),
                'VRA_POC04_HOSTED_RUNTIME_ATTESTATION': str(path)}
    require(value['environment'] == expected and all(env.get(key) == item for key, item in expected.items()),
            'Pinned runtime environment differs')
    require(all(name not in env for name in ENVIRONMENT_REJECT), 'Ambient Docker override prohibited')
    return value


class Runtime:
    def __init__(self, root):
        self.root = external_root(root)
        self.evidence = self.root / 'evidence'
        self.manifest = load_manifest()
        self.state_path = self.root / 'state/runtime-state.json'
        self.state = json.loads(self.state_path.read_text()) if self.state_path.exists() else None
        self.env = {**os.environ, 'DOCKER_CONFIG': str(self.root / 'docker-config'),
                    'BUILDX_CONFIG': str(self.root / 'buildx-state')}
        for name in ENVIRONMENT_REJECT:
            self.env.pop(name, None)

    def save(self):
        write(self.state_path, self.state)

    def record(self, name, value):
        write(self.evidence / (name + '.json'), value)

    def run(self, argv, privileged=False, env=None, timeout=60, input_data=None):
        argv = list(map(str, argv))
        if privileged:
            argv = ['/usr/bin/sudo', '-n', '--', *argv]
        completed = subprocess.run(argv, capture_output=True, text=input_data is None,
                                   input=input_data, env=env or self.env, cwd=self.root, timeout=timeout)
        stdout = completed.stdout if isinstance(completed.stdout, str) else completed.stdout.decode()
        stderr = completed.stderr if isinstance(completed.stderr, str) else completed.stderr.decode()
        if completed.returncode:
            if self.evidence.is_dir():
                self.record('command-failure', {'command': argv, 'exit_status': completed.returncode,
                            'output': 'Restricted logs; not part of safe evidence'})
                log = self.root / 'logs/failed-command.log'
                log.write_text(stdout + stderr); log.chmod(0o600)
            raise RuntimeError('Runtime command failed: ' + Path(argv[3] if privileged else argv[0]).name)
        return stdout.strip()

    def member_hash(self, name):
        return next(row['sha256'] for row in self.manifest['docker']['archive']['members']
                    if row['name'] == 'docker/' + name)

    def auxiliary(self):
        value = self.state.get('auxiliary')
        require(isinstance(value, dict), 'Auxiliary runtime authority absent')
        expected = auxiliary_authority(self.state['run_id'], value.get('token'))
        require(all(value.get(key) == item for key, item in expected.items()) and
                type(value.get('created')) is bool and
                not set(value) - set(expected) - {'created', 'identity'}, 'Auxiliary runtime authority differs')
        if value['created']:
            identity = value.get('identity')
            require(isinstance(identity, dict) and set(identity) == {'device', 'inode'} and
                    all(type(item) is int and item >= 0 for item in identity.values()),
                    'Auxiliary creation identity absent')
        return value

    def inspect_path(self, path, entries=False, sha256=False, recursive=False, text=False, peer=False):
        flags = [name for name, selected in (('entries', entries), ('sha256', sha256),
                                             ('recursive', recursive), ('text', text), ('peer', peer)) if selected]
        return json.loads(self.run(['/usr/bin/python3', '-I', '-S', '-B', '-c',
                                   PATH_OBSERVATION, path, *flags], privileged=True,
                                   env={'PATH': '/usr/bin:/bin', 'LANG': 'C', 'LC_ALL': 'C'}))

    def validate_run_parent(self):
        validate_owned_path(self.inspect_path(RUN_PARENT), RUN_PARENT, 'directory', '0755')
        # Reject stacked/remapped /run mounts and any overmount of the exact owned root.
        raw = (PROC_ROOT / 'self/mountinfo').read_text().splitlines()
        rows = [line.split(' - ', 1)[0].split() for line in raw]
        run_mounts = [row for row in rows if len(row) >= 5 and row[4] == '/run']
        require(len(run_mounts) == 1 and run_mounts[0][3] == '/', 'Run mount authority differs')
        require([line.split(' - ', 1)[1].split()[0] for line in raw
                 if line.split(' - ', 1)[0].split()[4] == '/run'] == ['tmpfs'],
                'Run runtime filesystem differs')
        root = Path(self.auxiliary()['root'])
        require(not any(point == root or point.is_relative_to(root) for point, kind in self.mount_points()),
                'Auxiliary runtime scope is overmounted')

    def create_auxiliary(self, payload):
        authority = self.auxiliary()
        require(not authority['created'], 'Auxiliary namespace already established')
        self.validate_run_parent()
        root = Path(authority['root'])
        require(self.inspect_path(root).get('exists') is False, 'Auxiliary root collision')
        self.run(['/usr/bin/mkdir', '-m', '0700', '--', root], privileged=True)
        observation = self.inspect_path(root)
        validate_owned_path(observation, root, 'directory', '0700')
        authority['created'] = True
        authority['identity'] = {key: observation[key] for key in ('device', 'inode')}
        self.save()  # Exclusive creation authority precedes child publication.
        for key in ('cwd', 'opt', 'opt_bin', 'opt_lib', 'socket_dir'):
            self.run(['/usr/bin/mkdir', '-m', '0700', '--', authority[key]], privileged=True)
        data = payload[SHIM_NAME]
        row = next(item for item in self.manifest['docker']['archive']['members']
                   if item['name'] == 'docker/' + SHIM_NAME)
        require(len(data) == row['size'] and hashlib.sha256(data).hexdigest() == row['sha256'],
                'Verified shim publication payload differs')
        self.run(['/usr/bin/python3', '-I', '-S', '-B', '-c', SHIM_PUBLICATION,
                  authority['shim'], row['sha256'], str(row['size']), authority['token']], privileged=True, input_data=data,
                  env={'PATH': '/usr/bin:/bin', 'LANG': 'C', 'LC_ALL': 'C'})

    def shim_preconditions(self):
        authority = self.auxiliary()
        require(authority['created'], 'Auxiliary namespace not established')
        self.validate_run_parent()
        expected_entries = {'root': ['cwd', 'opt', 's'], 'cwd': [], 'opt': ['bin', 'lib'],
                            'opt_bin': [SHIM_NAME], 'opt_lib': [], 'socket_dir': []}
        observations = {}
        for key, expected in expected_entries.items():
            path = Path(authority[key])
            value = self.inspect_path(path, entries=True)
            validate_owned_path(value, path, 'directory', '0700')
            if key == 'root':
                require(all(value.get(name) == item for name, item in authority['identity'].items()),
                        'Auxiliary root was replaced')
            require(value.get('entries') == expected, 'Unexpected auxiliary contents: ' + key)
            observations[key] = value
        shim = self.inspect_path(authority['shim'], sha256=True)
        validate_owned_path(shim, authority['shim'], 'regular', '0755', self.member_hash(SHIM_NAME))
        require(self.inspect_path(self.root / 'bin' / SHIM_NAME).get('exists') is False,
                'Side-by-side shim is forbidden')
        for name in ('containerd', 'runc'):
            path = self.root / 'bin' / name
            validate_owned_path(self.inspect_path(path, sha256=True), path, 'regular', '0755', self.member_hash(name))
        return {'authority': authority, 'directories': observations, 'shim': shim,
                'cwd_candidate': 'ABSENT', 'side_by_side_candidate': 'ABSENT',
                'initial_path': authority['opt_bin'], 'initial_library_path': authority['opt_lib']}

    def validate_configuration(self, daemon, containerd):
        authority = self.auxiliary()
        expected = {'path': str(self.root / 'bin/runc')}
        require(Path(expected['path']).is_absolute() and daemon.get('default-runtime') == 'vra-pinned-runc' and
                daemon.get('runtimes') == {'vra-pinned-runc': expected}, 'Pinned Path runtime differs')
        config = tomllib.loads(containerd)
        plugins = config.get('plugins', {})
        require(config.get('imports') == [] and
                {'io.containerd.internal.v1.opt', 'io.containerd.shim.v1.manager'} <=
                set(config.get('required_plugins', [])) and
                not {'io.containerd.internal.v1.opt', 'io.containerd.shim.v1.manager'} &
                set(config.get('disabled_plugins', [])) and
                plugins.get('io.containerd.internal.v1.opt') == {'path': authority['opt']} and
                plugins.get('io.containerd.shim.v1.manager') == {'env': [], 'socket_dir': authority['socket_dir']} and
                Path(authority['socket_dir']).is_absolute() and
                len(os.fsencode(authority['socket_dir'])) <= 42, 'Pinned opt/shim configuration differs')
        return config

    def containerd_unit_contract(self):
        authority = self.auxiliary()
        unit = self.state['units']['containerd']['unit']
        raw = self.run(['/usr/bin/systemctl', 'show', unit, '--no-pager',
                        '--property=WorkingDirectory,Environment,UnsetEnvironment'])
        properties = {}
        for line in raw.splitlines():
            key, separator, value = line.partition('=')
            require(separator and key in ('WorkingDirectory', 'Environment', 'UnsetEnvironment') and
                    key not in properties, 'Malformed containerd environment properties')
            properties[key] = value
        require(set(properties) == {'WorkingDirectory', 'Environment', 'UnsetEnvironment'},
                'Incomplete containerd environment properties')
        variables = shlex.split(properties['Environment'])
        require(len(variables) == 2 and set(variables) ==
                {'PATH=' + authority['opt_bin'], 'LD_LIBRARY_PATH=' + authority['opt_lib']} and
                properties['WorkingDirectory'] == authority['cwd'] and
                set(shlex.split(properties['UnsetEnvironment'])) == {'LD_PRELOAD', 'LD_AUDIT'},
                'Containerd unit environment differs')
        self.record('containerd-environment', properties)
        return properties

    def process_identity(self, pid):
        process = PROC_ROOT / str(pid)
        exe = self.run(['/usr/bin/readlink', process / 'exe'], privileged=True)
        sha = self.run(['/usr/bin/sha256sum', process / 'exe'], privileged=True).split()[0]
        fields = (process / 'stat').read_text().rsplit(')', 1)[1].split()
        return {'pid': int(pid), 'exe': exe, 'sha256': sha, 'start_time': fields[19],
                'boot_id': (PROC_ROOT / 'sys/kernel/random/boot_id').read_text().strip()}

    def unit_authority(self, role):
        require(role in ('dockerd', 'containerd'), 'Unapproved runtime role')
        run_id = self.state['run_id']
        require(str(uuid.UUID(run_id)) == run_id, 'Invalid owned run identity')
        unit = 'vra-poc04-' + role + '-' + run_id + '.service'
        sockets = ['docker.sock'] if role == 'dockerd' else ['containerd.sock', 'containerd.sock.ttrpc']
        root_identity = hashlib.sha256(os.fsencode(str(self.root))).hexdigest()
        row = {'unit': unit, 'role': role, 'run_id': run_id, 'runtime_root': str(self.root),
                'expected_exe': str(self.root / 'bin' / role), 'expected_sha256': self.member_hash(role),
                'expected_control_group': '/system.slice/' + unit,
                'description': 'VRA POC04 ' + role + ' ' + run_id + ' ' + root_identity,
                'sockets': [str(self.root / 'run' / name) for name in sockets]}
        if role == 'containerd':
            authority = self.auxiliary()
            row.update(auxiliary_root=authority['root'], working_directory=authority['cwd'],
                       shim_executable=authority['shim'], shim_socket_dir=authority['socket_dir'])
        return row

    def unit_properties(self, unit):
        raw = self.run(['/usr/bin/systemctl', 'show', unit, '--all', '--no-pager',
                        '--property=' + ','.join(UNIT_PROPERTIES)])
        result = {}
        for line in raw.splitlines():
            key, separator, value = line.partition('=')
            require(separator and key in UNIT_PROPERTIES and key not in result,
                    'Malformed owned unit properties')
            result[key] = value
        require(set(result) == set(UNIT_PROPERTIES), 'Incomplete owned unit properties')
        require(result['Id'] == unit, 'Owned unit name differs')
        require(all(re.fullmatch(r'[0-9]+', result[key]) for key in ('MainPID', 'ControlPID')),
                'Malformed owned unit PID')
        return result

    def validate_unit(self, row, properties):
        require(properties['LoadState'] == 'loaded' and properties['Id'] == row['unit'] and
                properties['Transient'] == 'yes' and properties['Slice'] == 'system.slice' and
                properties['Description'] == row['description'] and
                properties['KillMode'] == 'control-group' and properties['Restart'] == 'no' and
                properties['SendSIGKILL'] == 'yes', 'Owned unit authority differs; stop denied')
        group = properties['ControlGroup']
        require(group == row['expected_control_group'] or
                (not group and properties['ActiveState'] in ('inactive', 'failed') and
                 properties['MainPID'] == '0' and properties['ControlPID'] == '0'),
                'Owned unit cgroup differs; stop denied')

    def process_cgroup(self, pid):
        lines = (PROC_ROOT / str(pid) / 'cgroup').read_text().splitlines()
        groups = [line[3:] for line in lines if line.startswith('0::')]
        require(len(groups) == 1 and groups[0].startswith('/'), 'Process cgroup authority unavailable')
        return groups[0]

    def mount_points(self):
        mounts = []
        for line in (PROC_ROOT / 'self/mountinfo').read_text().splitlines():
            left, separator, right = line.partition(' - ')
            fields = left.split()
            require(separator and len(fields) >= 6 and len(right.split()) >= 3, 'Malformed mount authority')
            # Mountinfo encodes whitespace/backslashes with octal escapes.
            point = re.sub(r'\\([0-7]{3})', lambda match: chr(int(match[1], 8)), fields[4])
            if Path(point) == CGROUP_ROOT and right.split()[0] == 'cgroup2':
                require(fields[3] == '/', 'Cgroup-v2 hierarchy is a remapped subtree')
            mounts.append((Path(point), right.split()[0]))
        return mounts

    def cgroup_proof(self, group):
        mounts = self.mount_points()
        require([kind for point, kind in mounts if point == CGROUP_ROOT] == ['cgroup2'] and
                CGROUP_ROOT.is_dir() and not CGROUP_ROOT.is_symlink() and
                (CGROUP_ROOT / 'cgroup.controllers').is_file(), 'Cgroup-v2 mount authority unavailable')
        (CGROUP_ROOT / 'cgroup.controllers').read_text()  # Read access is a prerequisite, even for absence.
        require(re.fullmatch(r'/system\.slice/vra-poc04-(dockerd|containerd)-[0-9a-f-]{36}\.service', group),
                'Unapproved service cgroup')
        target = CGROUP_ROOT / group.lstrip('/')
        require(not any(point != CGROUP_ROOT and point.is_relative_to(CGROUP_ROOT) and
                        (point == target or point.is_relative_to(target) or target.is_relative_to(point))
                        for point, kind in mounts), 'Owned cgroup hierarchy is overmounted')
        for part in (target.parent, target):
            require(not part.is_symlink(), 'Owned cgroup path is a symlink')
        try:
            target.stat()
        except FileNotFoundError:
            return {'control_group': group, 'exists': False, 'owned_processes_remaining': 0}
        require(target.is_dir(), 'Owned cgroup is not a directory')
        count = 0
        for base, directories, files in os.walk(target, followlinks=False,
                                               onerror=lambda error: (_ for _ in ()).throw(error)):
            directory = Path(base)
            require(not directory.is_symlink() and all(not (directory / child).is_symlink()
                    for child in directories), 'Owned cgroup subtree is a symlink')
            procs = directory / 'cgroup.procs'
            require(procs.is_file() and not procs.is_symlink(), 'Owned cgroup process authority unavailable')
            values = procs.read_text().splitlines()
            require(all(re.fullmatch(r'[1-9][0-9]*', value) for value in values), 'Malformed cgroup process identity')
            count += len(values)
        events = target / 'cgroup.events'
        require(events.is_file() and not events.is_symlink(), 'Owned cgroup population authority unavailable')
        population = [line.split() for line in events.read_text().splitlines() if line.startswith('populated ')]
        require(population in ([['populated', '0']], [['populated', '1']]), 'Malformed cgroup population')
        require(count == 0 and population == [['populated', '0']], 'Owned cgroup retains processes')
        return {'control_group': group, 'exists': True, 'owned_processes_remaining': count}

    def owned_cgroup_pids(self, group):
        require(group == self.unit_authority('containerd')['expected_control_group'],
                'Shim observation cgroup authority differs')
        mounts = self.mount_points()
        require([kind for point, kind in mounts if point == CGROUP_ROOT] == ['cgroup2'] and
                not CGROUP_ROOT.is_symlink(), 'Shim cgroup mount authority unavailable')
        target = CGROUP_ROOT / group.lstrip('/')
        require(not any(point != CGROUP_ROOT and point.is_relative_to(CGROUP_ROOT) and
                        (point == target or point.is_relative_to(target) or target.is_relative_to(point))
                        for point, kind in mounts), 'Shim cgroup scope is overmounted')
        require(target.is_dir() and not target.is_symlink() and not target.parent.is_symlink(),
                'Shim observation cgroup absent')
        pids = set()
        for base, directories, files in os.walk(target, followlinks=False,
                                               onerror=lambda error: (_ for _ in ()).throw(error)):
            directory = Path(base)
            require(not directory.is_symlink() and all(not (directory / child).is_symlink()
                    for child in directories), 'Shim cgroup subtree is a symlink')
            path = directory / 'cgroup.procs'
            require(path.is_file() and not path.is_symlink(), 'Shim cgroup PID authority unavailable')
            values = path.read_text().splitlines()
            require(all(re.fullmatch(r'[1-9][0-9]*', value) for value in values), 'Malformed shim PID authority')
            pids.update(map(int, values))
            require(len(pids) <= 4096, 'Shim cgroup observation exceeds bound')
        return sorted(pids)

    def start(self, name, argv, bundle_only=False):
        if name == 'containerd':
            self.record('shim-resolution-preconditions', self.shim_preconditions())
            sealed = {}
            for filename in ('daemon.json', 'containerd.toml'):
                path = self.root / filename
                value = self.inspect_path(path, text=True)
                validate_owned_path(value, path, 'regular', '0400')
                sealed[filename] = value['text']
            self.validate_configuration(json.loads(sealed['daemon.json']), sealed['containerd.toml'])
        row = self.unit_authority(name)
        unit = row['unit']
        self.state['units'][name] = row
        self.save()  # Intent precedes process creation; never discover deletion authority.
        require(self.unit_properties(unit)['LoadState'] == 'not-found', 'Owned unit already exists')
        command = ['/usr/bin/systemd-run', '--unit=' + unit, '--service-type=exec',
                   '--slice=system.slice', '--property=Description=' + row['description'],
                   '--property=Restart=no', '--property=KillMode=control-group', '--property=SendSIGKILL=yes',
                   '--property=StandardOutput=append:' + str(self.root / 'logs' / (name + '.log')),
                   '--property=StandardError=append:' + str(self.root / 'logs' / (name + '.log')),
                   ]
        if name == 'containerd':
            authority = self.auxiliary()
            command += ['--property=WorkingDirectory=' + authority['cwd'],
                        '--property=UnsetEnvironment=LD_PRELOAD LD_AUDIT',
                        '--setenv=PATH=' + authority['opt_bin'],
                        '--setenv=LD_LIBRARY_PATH=' + authority['opt_lib']]
        else:
            command += ['--setenv=PATH=' + (str(self.root / 'bin') if bundle_only else
                                           str(self.root / 'bin') + ':/usr/sbin:/usr/bin:/sbin:/bin')]
        command += list(argv)
        self.run(command, privileged=True, env={'PATH': '/usr/bin:/bin', 'LANG': 'C', 'LC_ALL': 'C'})
        properties = self.unit_properties(unit)
        self.validate_unit(row, properties)
        require(properties['ControlGroup'] == row['expected_control_group'], 'Owned startup cgroup absent')
        row['control_group'] = properties['ControlGroup']
        self.save()  # Preserve cgroup authority even if the main process disappears during PID binding.
        pid = int(properties['MainPID'])
        require(pid > 0, 'Owned runtime process absent')
        identity = self.process_identity(pid)
        require(identity['exe'] == str(self.root / 'bin' / name) and
                identity['sha256'] == self.member_hash(name), 'Owned process executable identity differs')
        require(self.process_cgroup(pid) == row['control_group'], 'Owned process cgroup differs')
        row['identity'] = identity
        self.save()
        if name == 'containerd':
            self.containerd_unit_contract()
        self.record(name + '-identity', identity)

    def cli_contract(self):
        cli = self.root / 'bin/docker'
        validate_executable(shutil.which('docker', path=self.env['PATH']), cli, self.member_hash('docker'))
        require(re.fullmatch(r'Docker version 29\.8\.2, build 7fc2dff', self.run([cli, '--version'])),
                'Pinned Docker CLI version differs')
        plugin = self.root / 'docker-config/cli-plugins/docker-buildx'
        validate_executable(plugin, plugin, self.manifest['buildx']['sha256'])
        require(re.fullmatch(r'github\.com/docker/buildx v0\.37\.2 [0-9a-f]+', self.run([cli, 'buildx', 'version'])),
                'Pinned Buildx version differs')
        plugins = json.loads(self.run([cli, 'info', '--format', '{{json .ClientInfo.Plugins}}']))
        selected = [row for row in plugins if row.get('Name') == 'buildx']
        require(len(selected) == 1 and selected[0].get('Path') == str(plugin), 'Runner Buildx fallback prohibited')
        self.record('cli-buildx-identity', {'docker_path': str(cli), 'docker_sha256': self.member_hash('docker'),
                    'buildx_path': str(plugin), 'buildx_sha256': self.manifest['buildx']['sha256'],
                    'buildx_version': self.manifest['buildx']['version']})

    def original(self):
        cli = shutil.which('docker')
        require(cli, 'Original Docker CLI absent')
        endpoint = 'unix:///var/run/docker.sock'
        socket_path = '/var/run/docker.sock'
        version = json.loads(self.run([cli, '--host', endpoint, 'version', '--format', '{{json .Server}}']))
        info = json.loads(self.run([cli, '--host', endpoint, 'info', '--format', '{{json .}}']))
        inventory = inspect_inventory(socket_path, api=version['ApiVersion'])
        require(version['Os'] == 'linux' and version['Arch'] == 'amd64', 'Original daemon platform differs')
        units = {name: self.run(['/usr/bin/systemctl', 'show', name, '--property=MainPID,ActiveState,UnitFileState'])
                 for name in ('docker.service', 'docker.socket', 'containerd.service')}
        pid = int(self.run(['/usr/bin/systemctl', 'show', 'docker.service', '--property=MainPID', '--value']))
        require(pid > 0, 'Original daemon is not controlled by the reviewed system unit')
        self.state['original'] = {'pid': pid, 'identity': self.process_identity(pid), 'socket': socket_path}
        self.save()
        self.record('original-daemon', {'cli_path': cli, 'cli_sha256': digest(cli), 'endpoint': endpoint,
                    'version': {k: version.get(k) for k in ('Version', 'ApiVersion', 'MinAPIVersion', 'Os', 'Arch')},
                    'daemon_id': info['ID'], 'inventory': inventory, 'units': units})
        validate_empty(inventory)
        # The original containerd is preserved, but unexpected workloads block replacement.
        namespaces = self.run(['/usr/bin/ctr', '--address', '/run/containerd/containerd.sock',
                               'namespaces', 'list', '--quiet'], privileged=True).splitlines()
        observations = []
        for namespace in namespaces:
            tasks = self.run(['/usr/bin/ctr', '--address', '/run/containerd/containerd.sock',
                              '--namespace', namespace, 'tasks', 'list', '--quiet'], privileged=True).splitlines()
            observations.append({'namespace': namespace, 'tasks': tasks})
        self.record('original-containerd', {'workloads': observations, 'service': 'PRESERVED'})
        require(all(not row['tasks'] for row in observations), 'Unexpected original containerd workloads')

    def stop_original(self):
        # fd:// listeners do not unlink the inherited socket; its owning unit must do so.
        listener = self.run(['/usr/bin/systemctl', 'show', 'docker.socket', '--property=Listen', '--value'])
        match = re.fullmatch(r'(/[^\n]+) \(Stream\)', listener)
        require(match and Path(match[1]).resolve() == Path(self.state['original']['socket']).resolve(),
                'Original socket unit does not own the observed Docker endpoint')
        policy = self.root / 'original-socket-stop.conf'
        policy_hash = hashlib.sha256(ORIGINAL_SOCKET_STOP_POLICY.encode()).hexdigest()
        require(policy.is_file() and not policy.is_symlink() and policy.stat().st_uid == 0 and
                stat.S_IMODE(policy.stat().st_mode) == 0o400, 'Unsealed original socket shutdown policy')
        require(self.run(['/usr/bin/sha256sum', policy], privileged=True).split()[0] == policy_hash,
                'Original socket shutdown policy differs')
        directory = Path('/run/systemd/system/docker.socket.d')
        require(directory.parent.is_dir() and not directory.parent.is_symlink() and
                directory.parent.stat().st_uid == 0 and not directory.parent.stat().st_mode & 0o022,
                'Untrusted systemd runtime directory')
        if directory.exists():
            require(directory.is_dir() and not directory.is_symlink() and directory.stat().st_uid == 0 and
                    not directory.stat().st_mode & 0o022, 'Untrusted original socket drop-in directory')
        else:
            self.run(['/usr/bin/mkdir', '-m', '0755', directory], privileged=True)
        override = directory / ('90-vra-poc04-' + self.state['run_id'] + '.conf')
        require(not override.exists() and not override.is_symlink(), 'Socket policy integration already exists')
        self.state['original_socket_policy'] = {'source': str(policy), 'sha256': policy_hash,
                                                'integration': str(override), 'listener': listener}
        self.save()
        self.run(['/usr/bin/ln', '-s', policy, override], privileged=True)
        require(self.run(['/usr/bin/readlink', override]) == str(policy) and
                self.run(['/usr/bin/stat', '--format=%u', override]) == '0', 'Socket policy integration identity differs')
        self.run(['/usr/bin/systemctl', 'daemon-reload'], privileged=True)
        require(self.run(['/usr/bin/systemctl', 'show', 'docker.socket', '--property=Listen', '--value']) == listener,
                'Original socket listener changed during policy reload')
        require(self.run(['/usr/bin/systemctl', 'show', 'docker.socket', '--property=RemoveOnStop', '--value']) == 'yes',
                'Original socket RemoveOnStop policy is not effective')
        self.record('original-socket-policy', {**self.state['original_socket_policy'],
                    'source_text': ORIGINAL_SOCKET_STOP_POLICY, 'effective_remove_on_stop': True,
                    'source_owner': 'root', 'source_mode': '0400',
                    'socket_removal_authority': 'systemd owning socket unit',
                    'restoration': 'NOT PERFORMED on disposable VM'})
        self.run(['/usr/bin/systemctl', 'stop', 'docker.socket', 'docker.service'], privileged=True)
        self.run(['/usr/bin/systemctl', 'mask', '--runtime', 'docker.socket', 'docker.service'], privileged=True)
        socket_absent = not Path(self.state['original']['socket']).exists()
        process_absent = not Path('/proc/' + str(self.state['original']['pid'])).exists()
        self.record('original-stopped', {'socket_absent': socket_absent, 'process_absent': process_absent,
                    'units': {name: self.run(['/usr/bin/systemctl', 'show', name, '--property=ActiveState,UnitFileState'])
                              for name in ('docker.service', 'docker.socket')},
                    'system_containerd': 'PRESERVED; explicit distinct owned endpoint, roots and listeners',
                    'original_restoration': 'INTENTIONALLY NOT PERFORMED'})
        require(socket_absent and process_absent, 'Original daemon process/socket path remains; replacement denied')

    def configuration(self):
        root = self.root
        self.record('shim-publication', self.shim_preconditions())
        socket_path = root / 'run/docker.sock'
        ctrd_socket = root / 'run/containerd.sock'
        daemon = {'hosts': ['unix://' + str(socket_path)], 'data-root': str(root / 'docker-data'),
                  'exec-root': str(root / 'docker-exec'), 'pidfile': str(root / 'run/dockerd.pid'),
                  'containerd': str(ctrd_socket), 'containerd-namespace': 'poc04-' + self.state['run_id'],
                  'containerd-plugins-namespace': 'poc04-plugins-' + self.state['run_id'],
                  'features': {'containerd-snapshotter': True, 'embedded-containerd': False},
                  'storage-driver': 'overlayfs', 'default-runtime': 'vra-pinned-runc',
                  'runtimes': {'vra-pinned-runc': {'path': str(root / 'bin/runc')}},
                  'init-path': str(root / 'bin/docker-init'), 'userland-proxy-path': str(root / 'bin/docker-proxy'),
                  'group': str(os.getgid()), 'firewall-backend': 'iptables', 'bridge': 'poc04br0', 'bip': '172.30.0.1/24',
                  'default-address-pools': [{'base': '172.31.0.0/16', 'size': 24}]}
        import ipaddress
        routes = json.loads(self.run(['/usr/sbin/ip', '-j', 'route', 'show', 'table', 'all']))
        for route in routes:
            destination = route.get('dst', 'default')
            if destination != 'default':
                current = ipaddress.ip_network(destination, strict=False)
                require(all(not current.overlaps(ipaddress.ip_network(value)) for value in
                            ('172.30.0.0/24', '172.31.0.0/16')), 'Reviewed Docker network ranges overlap host routes')
        require(not Path('/sys/class/net/poc04br0').exists(), 'Job bridge already exists')
        ctrd = ('version = 4\nroot = ' + json.dumps(str(root / 'containerd-root')) +
                '\nstate = ' + json.dumps(str(root / 'containerd-state')) + '\nimports = []\n' +
                'plugin_dir = ' + json.dumps(str(root / 'containerd-plugins')) + '\n' +
                'required_plugins = ["io.containerd.snapshotter.v1.overlayfs", ' +
                '"io.containerd.internal.v1.opt", "io.containerd.shim.v1.manager"]\n' +
                'disabled_plugins = ["io.containerd.grpc.v1.cri", "io.containerd.cri.v1.images", ' +
                '"io.containerd.cri.v1.runtime", "io.containerd.nri.v1.nri"]\n' +
                '[plugins."io.containerd.server.v1.grpc"]\naddress = ' + json.dumps(str(ctrd_socket)) + '\n' +
                '[plugins."io.containerd.server.v1.ttrpc"]\naddress = ' + json.dumps(str(ctrd_socket) + '.ttrpc') + '\n' +
                '[plugins."io.containerd.server.v1.grpc-tcp"]\naddress = ""\n' +
                '[plugins."io.containerd.server.v1.debug"]\naddress = ""\n' +
                '[plugins."io.containerd.server.v1.metrics"]\naddress = ""\n' +
                '[plugins."io.containerd.internal.v1.opt"]\npath = ' +
                json.dumps(self.auxiliary()['opt']) + '\n' +
                '[plugins."io.containerd.shim.v1.manager"]\nenv = []\nsocket_dir = ' +
                json.dumps(self.auxiliary()['socket_dir']) + '\n')
        self.validate_configuration(daemon, ctrd)
        write(root / 'daemon.json', daemon)
        (root / 'containerd.toml').write_text(ctrd)
        (root / 'original-socket-stop.conf').write_text(ORIGINAL_SOCKET_STOP_POLICY)
        self.state['socket'] = str(socket_path)
        self.save()
        self.record('configuration', {'daemon': daemon, 'daemon_sha256': digest(root / 'daemon.json'),
                    'containerd_config': ctrd, 'containerd_config_sha256': digest(root / 'containerd.toml')})

    def wait_ready(self, path, probe):
        deadline = time.monotonic() + 30
        while time.monotonic() < deadline:
            try:
                return probe()
            except (OSError, RuntimeError, http.client.HTTPException):
                time.sleep(.1)  # Readiness observation; no daemon/test execution is retried.
        raise RuntimeError('Owned daemon readiness deadline exceeded: ' + Path(path).name)

    def server_proof(self):
        sock = self.state['socket']
        version = request(sock, 'GET', '/version')
        info = request(sock, 'GET', '/info')
        validate_server(version, info, self.manifest)
        validate_default_runtime(info, self.root / 'bin/runc')
        connection = socket.socket(socket.AF_UNIX, socket.SOCK_STREAM)
        connection.connect(sock)
        peer = struct.unpack('3i', connection.getsockopt(socket.SOL_SOCKET, socket.SO_PEERCRED, 12))
        connection.close()
        require(peer[0] == self.state['units']['dockerd']['identity']['pid'], 'Wrong socket peer daemon PID')
        self.state['daemon_id'] = info['ID']
        self.save()
        self.record('server-capability', {'server': {k: version[k] for k in ('Version', 'ApiVersion', 'MinAPIVersion', 'Os', 'Arch')},
                    'daemon_id': info['ID'], 'driver': info['Driver'], 'driver_status': info['DriverStatus'],
                    'default_runtime': info['DefaultRuntime'], 'runc_path': str(self.root / 'bin/runc'),
                    'socket_peer': {'pid': peer[0], 'uid': peer[1], 'gid': peer[2]},
                    'socket': sock, 'status': 'PASS'})

    def container_runtime_proof(self, container_id, inspection):
        """Only the exact creator-owned proof ID, namespace, bundle and socket are examined."""
        require(re.fullmatch(r'[0-9a-f]{64}', container_id) and inspection.get('Id') == container_id and
                inspection.get('HostConfig', {}).get('Runtime') == 'vra-pinned-runc',
                'Proof container default runtime differs')
        namespace = 'poc04-' + self.state['run_id']
        authority = self.auxiliary()
        validate_owned_path(self.inspect_path(authority['shim'], sha256=True), authority['shim'],
                            'regular', '0755', self.member_hash(SHIM_NAME))
        validate_owned_path(self.inspect_path(self.root / 'bin/runc', sha256=True), self.root / 'bin/runc',
                            'regular', '0755', self.member_hash('runc'))
        metadata = json.loads(self.run([self.root / 'bin/ctr', '--address', self.root / 'run/containerd.sock',
                                        '--namespace', namespace, 'containers', 'info', container_id], privileged=True))
        selected = metadata.get('Runtime', {})
        require(metadata.get('ID') == container_id and selected.get('Name') == 'io.containerd.runc.v2',
                'Proof containerd runtime identity differs')
        options = decode_runc_options(selected.get('Options'))
        require(options['binary_name'] == str(self.root / 'bin/runc'), 'Proof containerd BinaryName differs')
        # Serving-bundle metadata is scoped by the recorded state root, namespace and exact ID.
        bundle = self.root / 'containerd-state/io.containerd.runtime.v2.task' / namespace / container_id
        require(self.inspect_path(bundle).get('kind') == 'directory' and
                self.inspect_path(bundle).get('realpath') == str(bundle), 'Proof shim bundle authority differs')
        values = {}
        for name in ('options.json', 'runtime', 'bootstrap.json'):
            value = self.inspect_path(bundle / name, text=True)
            require(value.get('kind') == 'regular' and value.get('symlink') is False and
                    value.get('uid') == 0 and value.get('realpath') == str(bundle / name),
                    'Proof shim metadata authority differs')
            values[name] = value['text']
        consumed = json.loads(values['options.json'])
        require(consumed.get('binary_name') == str(self.root / 'bin/runc') and
                not consumed.get('shim_cgroup') and values['runtime'] == str(self.root / 'bin/runc'),
                'Shim-consumed absolute runc options differ')
        bootstrap = json.loads(values['bootstrap.json'])
        address = urllib.parse.urlsplit(bootstrap.get('address', ''))
        socket_path = Path(address.path)
        require(bootstrap.get('protocol') == 'ttrpc' and address.scheme == 'unix' and
                not address.netloc and not address.query and not address.fragment and
                socket_path.parent == Path(authority['socket_dir']) and
                re.fullmatch(r'[0-9a-f]{64}', socket_path.name), 'Proof shim socket scope differs')
        endpoint = self.inspect_path(socket_path, peer=True)
        require(endpoint.get('kind') == 'socket' and endpoint.get('realpath') == str(socket_path) and
                endpoint.get('uid') == 0 and endpoint.get('mode') == '0600', 'Proof shim endpoint differs')
        expected_group = self.unit_authority('containerd')['expected_control_group']
        observed = []
        # Listener credentials can retain a short-lived launcher PID after fd transfer.
        # Serving-process authority instead comes from the exact unit cgroup and bundle.
        for pid in self.owned_cgroup_pids(expected_group):
            try:
                identity = self.process_identity(pid)
                if identity['exe'] != authority['shim']:
                    continue
                require(identity['sha256'] == self.member_hash(SHIM_NAME), 'Observed shim executable differs')
                cwd = self.run(['/usr/bin/readlink', PROC_ROOT / str(pid) / 'cwd'], privileged=True)
                if cwd != str(bundle):
                    continue  # A short-lived runtime-info invocation has no serving bundle.
                group = self.process_cgroup(pid)
                require(group == expected_group or group.startswith(expected_group + '/'),
                        'Proof shim lifecycle differs')
                process_incarnation(identity)
                require(self.process_identity(pid) == identity, 'Proof shim incarnation changed during observation')
                observed.append((identity, group))
            except (OSError, RuntimeError):
                if not (PROC_ROOT / str(pid)).exists():
                    continue
                raise
        require(len(observed) == 1, 'Unique owned serving shim could not be established')
        identity, group = observed[0]
        observation = {'identity': identity, 'cgroup': group, 'container_id': container_id,
                       'bundle': str(bundle), 'socket': str(socket_path), 'runc_path': options['binary_name']}
        self.state['shim_observations'].append(observation)
        self.save()
        # Never publish the full ctr Spec, Docker Config.Env or full inspection.
        self.record('shim-runtime-proof', {'status': 'PASS', 'observation': observation,
                    'runtime': 'io.containerd.runc.v2', 'default_runtime': 'vra-pinned-runc',
                    'typed_options': options, 'shim_metadata': 'CONSUMED ABSOLUTE RUNC CONFIRMED'})

    def managed_opt_proof(self):
        authority = self.auxiliary()
        self.shim_preconditions()
        raw = self.run([self.root / 'bin/ctr', '--address', self.root / 'run/containerd.sock',
                        'plugins', 'ls', '--detailed', 'type==io.containerd.internal.v1,id==opt'], privileged=True)
        properties = {}
        for line in raw.splitlines():
            key, separator, value = line.partition(':')
            if separator and key.strip() in ('Type', 'ID', 'Error'):
                require(key.strip() not in properties, 'Duplicate managed opt plugin evidence')
                properties[key.strip()] = value.strip()
        path_exports = [line.split() for line in raw.splitlines() if line.split()[:1] == ['path']]
        require(properties.get('Type') == 'io.containerd.internal.v1' and properties.get('ID') == 'opt' and
                'Error' not in properties and path_exports == [['path', authority['opt']]],
                'Effective managed opt path differs')
        self.record('managed-opt-effective', {'status': 'PASS', 'path': authority['opt'],
                    'library_path': authority['opt_lib'], 'library_contents': 'EMPTY',
                    'shim_path': authority['shim'], 'plugin': properties})

    def capability(self):
        """One ephemeral TEST Postgres instance, exact creator ledger; never retain Config.Env."""
        tool_path = REPOSITORY / 'validation/poc-04/tooling/tool-manifest.json'
        require(digest(tool_path) == PHASE0_TOOL_SHA, 'Frozen Phase-0 tool authority differs')
        tools = json.loads(tool_path.read_text())
        pin_path = REPOSITORY / 'validation/poc-04/tooling/image-pins.json'
        authority = [row for row in tools['provenance_files'] if row['path'] == str(pin_path.relative_to(REPOSITORY))]
        require(len(authority) == 1 and digest(pin_path) == authority[0]['sha256'], 'Immutable TEST image-pin authority differs')
        pins = json.loads(pin_path.read_text())['records']
        postgres = next(row for row in pins if row['id'] == 'postgresql_test')
        pin = next(row for row in postgres['platform_manifests'] if row['platform'] == {'os': 'linux', 'architecture': 'amd64'})
        self.run([self.root / 'bin/docker', 'pull', '--platform', 'linux/amd64', postgres['execution_identifier']], timeout=300)
        sock = self.state['socket']
        name = 'poc04-capability-' + self.state['run_id']
        labels = {'dev.vra.runtime_run_id': self.state['run_id'], 'dev.vra.purpose': 'hosted-runtime-capability'}
        resources = self.state['capability_resources']
        self.record('capability-intent', {'name': name, 'image': postgres['execution_identifier'], 'labels': labels})
        primary = None
        try:
            network = request(sock, 'POST', '/networks/create', {'Name': name, 'Driver': 'bridge', 'Labels': labels})['Id']
            resources.append({'kind': 'network', 'id': network}); self.save()
            volume = request(sock, 'POST', '/volumes/create', {'Name': name, 'Labels': labels})['Name']
            resources.append({'kind': 'volume', 'id': volume}); self.save()
            # Password exists only in request memory and TEST daemon; never command args or evidence.
            created = request(sock, 'POST', '/containers/create?name=' + name + '&platform=linux%2Famd64',
                {'Image': postgres['execution_identifier'], 'Labels': labels,
                 'Env': ['POSTGRES_PASSWORD=' + uuid.uuid4().hex, 'POSTGRES_DB=poc04_probe'],
                 'ExposedPorts': {'5432/tcp': {}},
                 'HostConfig': {'NetworkMode': name, 'Mounts': [{'Type': 'volume', 'Source': volume,
                    'Target': '/var/lib/postgresql/data'}],
                    'PortBindings': {'5432/tcp': [{'HostIp': '127.0.0.1', 'HostPort': ''}]}}})['Id']
            resources.append({'kind': 'container', 'id': created}); self.save()
            inspection = request(sock, 'GET', '/containers/' + created + '/json')
            descriptor = validate_descriptor(inspection, pin)
            request(sock, 'POST', '/containers/' + created + '/start')
            inspection = request(sock, 'GET', '/containers/' + created + '/json')
            require(inspection['State']['Running'], 'Capability TEST container did not start')
            self.container_runtime_proof(created, inspection)
            ports = inspection['NetworkSettings']['Ports']['5432/tcp']
            require(len(ports) == 1 and ports[0]['HostIp'] == '127.0.0.1', 'Published-port contract differs')
            port = int(ports[0]['HostPort'])
            def probe():
                with socket.create_connection(('127.0.0.1', port), timeout=1) as channel:
                    channel.sendall(struct.pack('!II', 8, 80877103))
                    require(channel.recv(1) in (b'S', b'N'), 'Published PostgreSQL protocol unreachable')
                return True
            self.wait_ready(sock, probe)
            self.record('image-port-capability', {'status': 'PASS', 'api': '1.49', 'container_id': created,
                        'image': postgres['execution_identifier'], 'descriptor': descriptor,
                        'published_port': port, 'host': '127.0.0.1', 'postgres_protocol_response': 'VERIFIED',
                        'production_or_migration_behavior': 'NOT EXECUTED'})
        except Exception as failure:
            primary = failure
            raise
        finally:
            try:
                self.cleanup_capability_resources()
            except Exception as failure:
                self.record('capability-cleanup-failure', {'status': 'FAIL', 'failure': str(failure)})
                if primary is None:
                    raise
        validate_empty(inspect_inventory(sock))

    def cleanup_capability_resources(self):
        resources = self.state['capability_resources']
        for kind in ('container', 'network', 'volume'):
            for row in resources:
                if row['kind'] != kind or row.get('absent'):
                    continue
                endpoint = {'container': '/containers/', 'network': '/networks/', 'volume': '/volumes/'}[kind]
                suffix = '/json' if kind == 'container' else ''
                inspection = request(self.state['socket'], 'GET', endpoint + row['id'] + suffix)
                labels = (inspection.get('Config', {}) if kind == 'container' else inspection).get('Labels') or {}
                require(labels.get('dev.vra.runtime_run_id') == self.state['run_id'] and
                        labels.get('dev.vra.purpose') == 'hosted-runtime-capability', 'Capability cleanup identity differs')
                request(self.state['socket'], 'DELETE', endpoint + row['id'] + ('?force=1' if kind == 'container' else ''))
                row['absent'] = True; self.save()
        self.record('capability-cleanup', {'resources': resources, 'status': 'PASS'})

    def publish(self, github_env, github_path):
        sock = self.state['socket']
        contract = {'DOCKER_HOST': 'unix://' + sock, 'VRA_POC04_DISPOSABLE_TEST_DOCKER_ENDPOINT': 'unix://' + sock,
                    'VRA_POC04_EXPECTED_DAEMON_ID': self.state['daemon_id'],
                    'TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE': sock, 'TESTCONTAINERS_HOST_OVERRIDE': '127.0.0.1',
                    'TESTCONTAINERS_RYUK_DISABLED': 'false', 'DOCKER_CONFIG': str(self.root / 'docker-config'),
                    'BUILDX_CONFIG': str(self.root / 'buildx-state'),
                    'VRA_POC04_HOSTED_RUNTIME_ATTESTATION': str(self.evidence / 'runtime-attestation.json')}
        attestation = {'schema_version': 1, 'status': 'PASS', 'manifest_sha256': digest(MANIFEST),
                       'daemon_id': self.state['daemon_id'], 'environment': contract,
                       'cli_path': str(self.root / 'bin/docker'), 'cli_sha256': self.member_hash('docker'),
                       'units': self.state['units']}
        self.record('runtime-attestation', attestation)
        if github_env:
            require(Path(github_env) == Path(os.environ['GITHUB_ENV']) and
                    Path(github_path) == Path(os.environ['GITHUB_PATH']), 'Unreviewed workflow environment destination')
            with Path(github_env).open('a') as target:
                for key, value in contract.items():
                    require('\n' not in value and '\r' not in value, 'Unsafe environment serialization')
                    target.write(key + '=' + value + '\n')
            with Path(github_path).open('a') as target:
                target.write(str(self.root / 'bin') + '\n')
        self.record('runtime-environment', contract)

    def provision(self, github_env=None, github_path=None):
        require(not self.root.exists(), 'Runtime directory must be fresh')
        require(all(name not in os.environ for name in ENVIRONMENT_REJECT), 'Ambient Docker override prohibited')
        self.root.mkdir(mode=0o750)
        for name in ('downloads', 'bin', 'docker-config', 'containerd-root', 'containerd-state',
                     'containerd-plugins', 'docker-data', 'docker-exec', 'run', 'logs', 'evidence', 'state', 'buildx-state'):
            (self.root / name).mkdir(mode=0o700)
        self.evidence = self.root / 'evidence'
        self.state = {'schema_version': 1, 'run_id': str(uuid.uuid4()), 'units': {},
                      'runtime_root': str(self.root), 'socket': str(self.root / 'run/docker.sock'),
                      'socket_paths': [str(self.root / 'run' / name) for name in
                                       ('docker.sock', 'containerd.sock', 'containerd.sock.ttrpc')],
                      'capability_resources': [], 'shim_observations': [], 'status': 'PROVISIONING'}
        self.state['auxiliary'] = {**auxiliary_authority(self.state['run_id'], secrets.token_hex(12)),
                                   'created': False}
        self.save()
        self.record('manifest-identity', {'sha256': digest(MANIFEST), 'manifest': self.manifest})
        self.original()
        archive = self.root / 'downloads/docker.tgz'
        acquire(self.manifest['docker']['archive'], archive)
        payload = verify_archive(archive, self.manifest['docker']['archive'])
        buildx = self.root / 'downloads/buildx'
        acquire(self.manifest['buildx'], buildx, github=True)
        verify_buildx(buildx, self.manifest['buildx'])
        for name, data in payload.items():
            if name == SHIM_NAME:
                continue  # The only shim publication is the protected opt/bin target.
            path = self.root / 'bin' / name
            path.write_bytes(data); path.chmod(0o755)
        plugins = self.root / 'docker-config/cli-plugins'; plugins.mkdir(mode=0o755)
        shutil.copyfile(buildx, plugins / 'docker-buildx'); (plugins / 'docker-buildx').chmod(0o755)
        self.create_auxiliary(payload)
        # Seal daemon write roots and log pathnames; mutable metadata has separate directories.
        for name in ('containerd-root', 'containerd-state', 'containerd-plugins', 'docker-data', 'docker-exec'):
            self.run(['/usr/bin/chown', 'root:root', self.root / name], privileged=True)
        for name in ('containerd.log', 'dockerd.log', 'failed-command.log'):
            path = self.root / 'logs' / name
            path.touch(); path.chmod(0o600)
        (self.root / 'logs').chmod(0o755)
        self.run(['/usr/bin/chown', 'root:root', self.root / 'logs'], privileged=True)
        # Only critical binary/config directories are immutable to unprivileged job code.
        for name in ('bin', 'docker-config'):
            (self.root / name).chmod(0o755)
            self.run(['/usr/bin/chown', '-R', 'root:root', self.root / name], privileged=True)
        self.record('artifact-verification', {'status': 'PASS', 'archive_sha256': digest(archive),
                    'members': {name: (self.inspect_path(self.auxiliary()['shim'], sha256=True)['sha256']
                                if name == SHIM_NAME else digest(self.root / 'bin' / name)) for name in payload},
                    'buildx_sha256': digest(buildx)})
        self.configuration()
        for name in ('daemon.json', 'containerd.toml', 'original-socket-stop.conf'):
            (self.root / name).chmod(0o400)
            self.run(['/usr/bin/chown', 'root:root', self.root / name], privileged=True)
        (self.root / 'run').chmod(0o755)
        self.run(['/usr/bin/chown', 'root:root', self.root / 'run'], privileged=True)
        self.run(['/usr/bin/chown', 'root:' + str(os.getgid()), self.root], privileged=True)
        self.stop_original()
        self.env['PATH'] = str(self.root / 'bin') + ':/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin'
        self.env['DOCKER_HOST'] = 'unix://' + str(self.root / 'run/docker.sock')
        self.run([self.root / 'bin/dockerd', '--validate', '--config-file', self.root / 'daemon.json'], privileged=True)
        require('v2.3.6' in self.run([self.root / 'bin/containerd', '--version']), 'Pinned containerd version differs')
        require('runc version 1.5.2' in self.run([self.root / 'bin/runc', '--version']), 'Pinned runc version differs')
        self.start('containerd', [self.root / 'bin/containerd', '--config', self.root / 'containerd.toml'], bundle_only=True)
        ctr = [self.root / 'bin/ctr', '--address', self.root / 'run/containerd.sock']
        self.wait_ready(self.root / 'run/containerd.sock', lambda: self.run([*ctr, 'version'], privileged=True))
        self.managed_opt_proof()
        plugins_state = self.run([*ctr, 'plugins', 'ls'], privileged=True)
        require(any('io.containerd.snapshotter.v1' in line and 'overlayfs' in line and line.split()[-1] == 'ok'
                    for line in plugins_state.splitlines()), 'Containerd overlayfs snapshotter is unhealthy')
        self.record('snapshotter', {'status': 'PASS', 'plugins': plugins_state})
        self.start('dockerd', [self.root / 'bin/dockerd', '--config-file', self.root / 'daemon.json'])
        self.wait_ready(self.state['socket'], lambda: request(self.state['socket'], 'GET', '/info'))
        self.run(['/usr/bin/chgrp', str(os.getgid()), self.root / 'run/docker.sock'], privileged=True)
        self.run(['/usr/bin/chmod', '0660', self.root / 'run/docker.sock'], privileged=True)
        self.server_proof()
        self.cli_contract()
        self.capability()
        self.state['status'] = 'PASS'; self.save()
        self.publish(github_env, github_path)

    def stop_owned_unit(self, role, row):
        expected = self.unit_authority(role)
        require(all(row.get(key) == value for key, value in expected.items()) and
                row.get('control_group', expected['expected_control_group']) == expected['expected_control_group'],
                'Recorded unit ownership differs; stop denied')
        properties = self.unit_properties(row['unit'])
        observed = 'NOT ESTABLISHED'
        identity = row.get('identity')
        diagnostics = []
        observation = {'status': 'NOT OBSERVED', 'identity': None, 'cgroup': None,
                       'error_class': None, 'ownership': 'NOT ESTABLISHED'}
        if identity:
            process_incarnation(identity)
            require(identity.get('exe') == expected['expected_exe'] and
                    identity.get('sha256') == expected['expected_sha256'], 'Malformed recorded process authority')
        if properties['LoadState'] == 'loaded':
            require(self.state['units'].get(role) == row, 'Loaded unit has no recorded start authority; stop denied')
            self.validate_unit(row, properties)
            pid = int(properties['MainPID'])
            if pid:
                try:
                    observation['identity'] = self.process_identity(pid)
                    observation['cgroup'] = self.process_cgroup(pid)
                    process_incarnation(observation['identity'])
                    owned = (observation['identity']['exe'] == expected['expected_exe'] and
                             observation['identity']['sha256'] == expected['expected_sha256'] and
                             observation['cgroup'] == expected['expected_control_group'])
                    observation.update(status='OBSERVED', ownership='OWNED' if owned else 'NOT OWNED')
                    if owned:
                        identity = identity or observation['identity']
                except Exception as failure:
                    # Only optional sampling is non-authoritative. Unit ownership
                    # above and mandatory post-stop absence below remain fail-closed.
                    observation.update(status='PARTIAL' if observation['identity'] is not None else 'UNAVAILABLE',
                                       error_class=type(failure).__name__)
            # Even a vanished/reused recorded PID does not remove unit/cgroup stop authority.
            self.run(['/usr/bin/systemctl', 'stop', row['unit']], privileged=True)
            # Preserve diagnostics after stop, so evidence writes cannot preempt it.
            self.record(role + '-pre-stop-observation', observation)
            properties = self.unit_properties(row['unit'])
        require(properties['LoadState'] in ('loaded', 'not-found'), 'Owned unit load state is unknown')
        if properties['LoadState'] == 'loaded':
            self.validate_unit(row, properties)
        require(properties['ActiveState'] in ('inactive', 'failed') and
                properties['SubState'] in ('dead', 'failed') and properties['MainPID'] == '0' and
                properties['ControlPID'] == '0' and properties['ControlGroup'] in ('', expected['expected_control_group']),
                'Owned unit is not stopped')
        proof = self.cgroup_proof(expected['expected_control_group'])
        if identity:
            try:
                (PROC_ROOT / str(identity['pid'])).stat()
            except FileNotFoundError:
                observed = 'ABSENT'
            else:
                try:
                    current = self.process_identity(identity['pid'])
                except Exception:
                    # A disappearance race is absence; any other unknown identity fails closed.
                    if not (PROC_ROOT / str(identity['pid'])).exists():
                        observed = 'ABSENT'
                    else:
                        observed = 'UNKNOWN'
                        diagnostics.append('Recorded main process identity observation unavailable')
                else:
                    require(process_incarnation(current) != process_incarnation(identity),
                            'Recorded owned main process remains after unit shutdown')
                    observed = 'ORIGINAL ABSENT / PID REUSED'
        return {'unit': row['unit'], 'stopped': not diagnostics,
                'owned_processes_remaining': None if diagnostics else 0,
                'unit_state': properties, 'cgroup': proof, 'recorded_main_process': observed,
                'identity_failures': diagnostics, 'pre_stop_observation': observation}

    def endpoint_unavailable(self):
        connection = socket.socket(socket.AF_UNIX, socket.SOCK_STREAM)
        try:
            connection.settimeout(1)
            try:
                connection.connect(self.state['socket'])
            except (FileNotFoundError, ConnectionRefusedError):
                return True
            return False
        finally:
            connection.close()

    def shim_endpoint_proof(self):
        authority = self.auxiliary()
        root = Path(authority['root'])
        root_info = self.inspect_path(root)
        if not authority['created']:
            require(root_info.get('exists') is False, 'Uncreated auxiliary root collision remains')
            return {'socket_dir': authority['socket_dir'], 'exists': False, 'remaining': []}
        validate_owned_path(root_info, root, 'directory', '0700')
        require(all(root_info.get(key) == value for key, value in authority['identity'].items()),
                'Auxiliary root was replaced')
        self.validate_run_parent()
        value = self.inspect_path(authority['socket_dir'], recursive=True)
        validate_owned_path(value, authority['socket_dir'], 'directory', '0700')
        nodes = value.get('nodes')
        if isinstance(nodes, list) and nodes:
            self.record('shim-endpoint-residual', {'socket_dir': authority['socket_dir'], 'remaining': nodes,
                        'status': 'FAIL / PRESERVED'})
        require(isinstance(nodes, list) and not nodes, 'Owned shim endpoints remain')
        return {'socket_dir': authority['socket_dir'], 'exists': True, 'remaining': nodes}

    def shim_process_absence(self):
        authority = self.auxiliary()
        observations = self.state.get('shim_observations')
        require(isinstance(observations, list), 'Recorded shim observation authority absent')
        group = self.unit_authority('containerd')['expected_control_group']
        results = []
        for row in observations:
            identity = row.get('identity', {})
            process_incarnation(identity)
            require(identity.get('exe') == authority['shim'] and
                    identity.get('sha256') == self.member_hash(SHIM_NAME) and
                    (row.get('cgroup') == group or row.get('cgroup', '').startswith(group + '/')),
                    'Recorded shim ownership differs')
            try:
                (PROC_ROOT / str(identity['pid'])).stat()
            except FileNotFoundError:
                state = 'ABSENT'
            else:
                current = self.process_identity(identity['pid'])
                require(process_incarnation(current) != process_incarnation(identity),
                        'Recorded owned shim process remains')
                state = 'ORIGINAL ABSENT / PID REUSED'
            results.append({'pid': identity['pid'], 'status': state})
        # Recursive unit cgroup proof covers unobserved/short-lived shim descendants.
        proof = self.cgroup_proof(group)
        return {'owned_processes_remaining': 0, 'recorded_shims': results, 'cgroup': proof}

    def cleanup_auxiliary(self):
        authority = self.auxiliary()
        root = Path(authority['root'])
        self.shim_endpoint_proof()  # Revalidate root incarnation and empty endpoints before removal.
        if not authority['created']:
            return {'status': 'PASS', 'removed': False, 'reason': 'NOT CREATED / ABSENCE PROVEN'}
        require(not any(point == root or point.is_relative_to(root) for point, kind in self.mount_points()),
                'Auxiliary cleanup scope contains a mount')
        self.run(['/usr/bin/rm', '-r', '--', root], privileged=True)
        require(self.inspect_path(root).get('exists') is False, 'Auxiliary cleanup root remains')
        authority['created'] = False
        authority.pop('identity', None)
        self.save()
        return {'status': 'PASS', 'removed': True, 'root': str(root)}

    def cleanup_runtime(self):
        # Exact declared mutable roots, never discovery-derived deletion authority.
        directories = ('downloads', 'bin', 'docker-config', 'containerd-root', 'containerd-state',
                       'containerd-plugins', 'docker-data', 'docker-exec', 'run', 'buildx-state')
        files = ('daemon.json', 'containerd.toml')
        selected = [self.root / name for name in directories + files]
        mounts = self.mount_points()
        require(not any(point == self.root or point == path or point.is_relative_to(path)
                        for point, kind in mounts for path in selected),
                'Owned runtime cleanup path contains a mount; cleanup denied')
        for path in selected:
            require(not path.is_symlink(), 'Owned runtime cleanup path is a symlink; cleanup denied')
            if path.exists():
                require(path.is_dir() if path.name in directories else path.is_file(),
                        'Owned runtime cleanup file type differs')
        removed = []
        for path in selected:
            if path.exists():
                self.run(['/usr/bin/rm', '-r', '--', path], privileged=True)
                require(not os.path.lexists(path), 'Owned runtime cleanup path remains')
                removed.append(path.name)
        return {'status': 'PASS', 'removed': removed,
                'preserved': ['evidence', 'logs', 'state', 'original-socket-stop.conf']}

    def teardown(self):
        require(self.state is not None, 'Runtime teardown has no recorded ownership authority')
        failures = []
        result = {'dockerd_unit': {'stopped': False, 'owned_processes_remaining': None},
                  'containerd_unit': {'stopped': False, 'owned_processes_remaining': None},
                  'owned_sockets_remaining': None, 'daemon_endpoint_available': None,
                  'shim_endpoints_remaining': None, 'owned_shims_remaining': None,
                  'auxiliary_cleanup': {'status': 'NOT EXECUTED / SHUTDOWN NOT PROVEN'},
                  'runtime_cleanup': {'status': 'NOT EXECUTED / SHUTDOWN NOT PROVEN'},
                  'original_restoration': 'NOT PERFORMED'}
        try:
            require(external_root(self.root) == self.root and self.state.get('runtime_root') == str(self.root),
                    'Recorded runtime root authority differs')
            self.unit_authority('dockerd')  # Validate the recorded run UUID before any unit operation.
            self.auxiliary()
            require(isinstance(self.state.get('shim_observations'), list), 'Recorded shim observation authority absent')
            sockets = [str(self.root / 'run' / name) for name in
                       ('docker.sock', 'containerd.sock', 'containerd.sock.ttrpc')]
            require(self.state.get('socket_paths') == sockets and self.state.get('socket') == sockets[0] and
                    isinstance(self.state.get('units'), dict) and
                    not set(self.state['units']) - {'dockerd', 'containerd'}, 'Recorded runtime endpoint authority differs')
            require(not (self.root / 'run').is_symlink(), 'Owned socket directory is a symlink')
            for role, row in self.state['units'].items():
                expected = self.unit_authority(role)
                require(all(row.get(key) == value for key, value in expected.items()), 'Recorded unit ownership differs')
        except Exception as failure:
            failures.append(str(failure))
            result.update(status='FAIL', failures=failures)
            self.record('teardown', result)
            raise RuntimeError('Owned runtime teardown failed') from failure
        if self.state.get('daemon_id'):
            try:
                info = request(self.state['socket'], 'GET', '/info')
                require(info['ID'] == self.state['daemon_id'], 'Daemon identity changed before teardown')
                self.cleanup_capability_resources()
                inventory = inspect_inventory(self.state['socket'])
                self.record('final-daemon-inventory', inventory)
                validate_empty(inventory)
            except Exception as failure:
                failures.append(str(failure))
        for role in ('dockerd', 'containerd'):
            try:
                if role == 'containerd':
                    require(result['dockerd_unit']['stopped'], 'Dockerd shutdown unproven; containerd stop deferred')
                # A durable pre-start intent plus independently verified absence handles start failures.
                row = self.state['units'].get(role, self.unit_authority(role))
                result[role + '_unit'] = self.stop_owned_unit(role, row)
                failures.extend(role + ': ' + failure for failure in result[role + '_unit']['identity_failures'])
            except Exception as failure:
                failures.append(role + ': ' + str(failure))
        try:
            process_proof = self.shim_process_absence()
            result['owned_shims_remaining'] = process_proof['owned_processes_remaining']
            result['shim_process_proof'] = process_proof
        except Exception as failure:
            failures.append(str(failure))
        try:
            proof = self.shim_endpoint_proof()
            result['shim_endpoints_remaining'] = proof['remaining']
            result['shim_endpoint_proof'] = proof
        except Exception as failure:
            failures.append(str(failure))
        try:
            remaining = []
            for path in sockets:
                try:
                    Path(path).lstat()
                except FileNotFoundError:
                    continue
                remaining.append(path)
            result['owned_sockets_remaining'] = remaining
            result['daemon_endpoint_available'] = not self.endpoint_unavailable()
            require(not remaining, 'Exact owned runtime sockets remain')
            require(result['daemon_endpoint_available'] is False, 'Job-owned daemon endpoint remains available')
        except Exception as failure:
            failures.append(str(failure))
        if not failures:
            try:
                result['auxiliary_cleanup'] = self.cleanup_auxiliary()
                result['runtime_cleanup'] = self.cleanup_runtime()
            except Exception as failure:
                failures.append(str(failure))
                result['runtime_cleanup'] = {'status': 'FAIL'}
        result.update(status='FAIL' if failures else 'PASS', failures=failures)
        self.record('teardown', result)
        require(not failures, 'Owned runtime teardown failed')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('operation', choices=('provision', 'teardown'))
    parser.add_argument('--root', required=True)
    parser.add_argument('--github-env')
    parser.add_argument('--github-path')
    parser.add_argument('--disposable-linux', action='store_true')
    args = parser.parse_args()
    require(platform.system() == 'Linux' and platform.machine() == 'x86_64' and
            not Path('/.dockerenv').exists(), 'Provisioning requires a disposable Linux/amd64 VM, not Docker-in-Docker')
    hosted = os.environ.get('GITHUB_ACTIONS') == 'true' and os.environ.get('RUNNER_ENVIRONMENT') == 'github-hosted'
    require(hosted or (args.disposable_linux and os.environ.get('VRA_POC04_DISPOSABLE_LINUX_PROOF') == 'true'),
            'Explicit disposable execution authority required')
    runtime = Runtime(args.root)
    try:
        if args.operation == 'provision':
            runtime.provision(args.github_env, args.github_path)
        else:
            runtime.teardown()
    except Exception as failure:
        if runtime.evidence.is_dir():
            runtime.record(args.operation + '-failure', {'status': 'FAIL', 'failure': str(failure), 'fallback': 'PROHIBITED'})
        raise
    return 0


if __name__ == '__main__':
    sys.exit(main())
