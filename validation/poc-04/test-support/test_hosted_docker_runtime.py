"""TEST-only hermetic proof for pinned hosted runtime acquisition and capabilities."""
import copy
import base64
from email.message import Message
import hashlib
import importlib.util
import io
import json
import os
from pathlib import Path
import shutil
import socket
import struct
import tarfile
import tempfile
from types import SimpleNamespace
import unittest
from unittest import mock

ROOT = Path(__file__).resolve().parents[3]
HELPER = ROOT / 'validation/poc-04/scripts/hosted-docker-runtime.py'
spec = importlib.util.spec_from_file_location('hosted_docker_runtime_test', HELPER)
runtime = importlib.util.module_from_spec(spec)
spec.loader.exec_module(runtime)


def synthetic_elf(machine=62):
    data = bytearray(20)
    data[:6] = b'\x7fELF\x02\x01'
    struct.pack_into('<H', data, 18, machine)
    return bytes(data)


class HostedRuntimeArchiveTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory(prefix='vra-hosted-runtime-archive-')
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name).resolve()

    def archive(self, extra=(), missing=False):
        path = self.root / 'runtime.tgz'
        data = synthetic_elf()
        with tarfile.open(path, 'w:gz') as archive:
            directory = tarfile.TarInfo('docker')
            directory.type = tarfile.DIRTYPE
            directory.mode = 0o755
            archive.addfile(directory)
            if not missing:
                member = tarfile.TarInfo('docker/docker')
                member.mode = 0o755
                member.size = len(data)
                archive.addfile(member, io.BytesIO(data))
            for name, member_type in extra:
                member = tarfile.TarInfo(name)
                member.mode = 0o755
                member.type = member_type
                if member_type == tarfile.REGTYPE:
                    member.size = len(data)
                    archive.addfile(member, io.BytesIO(data))
                else:
                    member.linkname = 'docker'
                    archive.addfile(member)
        expected = {'sha256': runtime.digest(path), 'size': path.stat().st_size, 'members': [
            {'name': 'docker', 'type': 'directory', 'mode': '0755'},
            {'name': 'docker/docker', 'type': 'regular', 'mode': '0755',
             'sha256': hashlib.sha256(data).hexdigest(), 'size': len(data)}]}
        return path, expected

    def test_verified_archive_yields_only_approved_members_without_extraction(self):
        path, expected = self.archive()
        self.assertEqual(runtime.verify_archive(path, expected), {'docker': synthetic_elf()})
        self.assertEqual(list(self.root.iterdir()), [path])

    def test_wrong_archive_hash_rejects_before_opening_tar(self):
        path, expected = self.archive()
        expected['sha256'] = '0' * 64
        with mock.patch.object(runtime.tarfile, 'open') as open_archive:
            with self.assertRaisesRegex(RuntimeError, 'Artifact SHA-256 mismatch'):
                runtime.verify_archive(path, expected)
            open_archive.assert_not_called()

    def test_unexpected_member_is_rejected(self):
        path, expected = self.archive(extra=(('docker/unapproved-tool', tarfile.REGTYPE),))
        with self.assertRaisesRegex(RuntimeError, 'Unexpected archive member'):
            runtime.verify_archive(path, expected)

    def test_path_traversal_member_is_rejected(self):
        path, expected = self.archive(extra=(('docker/../escape', tarfile.REGTYPE),))
        with self.assertRaisesRegex(RuntimeError, 'Unsafe archive member path'):
            runtime.verify_archive(path, expected)

    def test_absolute_member_is_rejected(self):
        path, expected = self.archive(extra=(('/escape', tarfile.REGTYPE),))
        with self.assertRaisesRegex(RuntimeError, 'Unsafe archive member path'):
            runtime.verify_archive(path, expected)

    def test_duplicate_member_is_rejected(self):
        path, expected = self.archive(extra=(('docker/docker', tarfile.REGTYPE),))
        with self.assertRaisesRegex(RuntimeError, 'Duplicate archive member'):
            runtime.verify_archive(path, expected)

    def test_selected_member_hash_mismatch_is_rejected(self):
        path, expected = self.archive()
        expected['members'][1]['sha256'] = '0' * 64
        with self.assertRaisesRegex(RuntimeError, 'Archive member SHA-256 mismatch'):
            runtime.verify_archive(path, expected)

    def test_approved_symlink_cannot_be_published_as_executable(self):
        path, expected = self.archive(extra=(('docker/link', tarfile.SYMTYPE),))
        expected['members'].append({'name': 'docker/link', 'type': 'regular', 'mode': '0755',
                                    'sha256': '0' * 64, 'size': 20})
        with self.assertRaisesRegex(RuntimeError, 'Unsafe archive member type'):
            runtime.verify_archive(path, expected)

    def test_missing_approved_member_is_rejected(self):
        path, expected = self.archive(missing=True)
        with self.assertRaisesRegex(RuntimeError, 'Missing approved archive member'):
            runtime.verify_archive(path, expected)

    def test_non_amd64_executable_is_rejected(self):
        with self.assertRaisesRegex(RuntimeError, 'Linux ELF64 amd64'):
            runtime.verify_elf(synthetic_elf(machine=183))

    def test_wrong_buildx_digest_is_rejected(self):
        path = self.root / 'buildx'
        path.write_bytes(synthetic_elf())
        expected = {'sha256': '0' * 64, 'size': path.stat().st_size}
        with self.assertRaisesRegex(RuntimeError, 'Artifact SHA-256 mismatch'):
            runtime.verify_buildx(path, expected)

    def acquisition_response(self, content_type):
        data = b'TEST_ONLY_PINNED_ACQUISITION_BYTES'
        expected = {'url': 'https://download.docker.com/linux/static/stable/x86_64/docker-29.8.2.tgz',
                    'sha256': hashlib.sha256(data).hexdigest(), 'size': len(data)}
        response = io.BytesIO(data)
        response.headers = Message()
        response.headers['Content-Type'] = content_type
        return expected, response

    def test_official_compressed_tar_content_type_still_requires_pinned_bytes(self):
        expected, response = self.acquisition_response('application/x-compressed-tar')
        destination = self.root / 'verified-download.tgz'
        failure = None
        with mock.patch.object(runtime.urllib.request, 'build_opener',
                               return_value=SimpleNamespace(open=lambda *args, **kwargs: response)):
            try:
                runtime.acquire(expected, destination)
            except RuntimeError as error:
                failure = str(error)
        self.assertIsNone(failure, 'Official archive MIME type must not fail the pinned acquisition contract')
        self.assertEqual(runtime.digest(destination), expected['sha256'])
        self.assertEqual(destination.stat().st_size, expected['size'])

    def test_html_response_is_rejected_before_executable_publication(self):
        expected, response = self.acquisition_response('text/html')
        destination = self.root / 'verified-download.tgz'
        with mock.patch.object(runtime.urllib.request, 'build_opener',
                               return_value=SimpleNamespace(open=lambda *args, **kwargs: response)):
            with self.assertRaisesRegex(RuntimeError, 'Unexpected artifact content type'):
                runtime.acquire(expected, destination)
        self.assertFalse(destination.exists())


class HostedRuntimeCapabilityTest(unittest.TestCase):
    def setUp(self):
        self.manifest = runtime.load_manifest()
        self.version = {'Version': '29.8.2', 'ApiVersion': '1.56', 'MinAPIVersion': '1.40',
                        'Os': 'linux', 'Arch': 'amd64'}
        self.info = {'ID': 'TEST_ONLY_DAEMON_ID', 'OSType': 'linux', 'Architecture': 'x86_64',
                     'Driver': 'overlayfs', 'DriverStatus': [['driver-type', 'io.containerd.snapshotter.v1']]}
        self.inventory = {'containers': [], 'volumes': [],
                          'networks': [{'Name': name} for name in ('bridge', 'host', 'none')]}
        self.pin = {'digest': 'sha256:' + '0' * 64, 'response': {'response_bytes': 123},
                    'platform': {'os': 'linux', 'architecture': 'amd64'}}

    def test_selected_server_contract_and_identity_are_accepted(self):
        runtime.validate_server(self.version, self.info, self.manifest, 'TEST_ONLY_DAEMON_ID')

    def test_api_148_is_rejected(self):
        self.version['ApiVersion'] = '1.48'
        with self.assertRaisesRegex(RuntimeError, 'API >=1.49 required'):
            runtime.validate_server(self.version, self.info, self.manifest)

    def test_malformed_or_unknown_api_is_rejected(self):
        for value in (None, '', 'unknown', '1', '1.49.extra', 1.49):
            with self.subTest(value=value), self.assertRaisesRegex(RuntimeError, 'Malformed Docker API'):
                self.version['ApiVersion'] = value
                runtime.validate_server(self.version, self.info, self.manifest)

    def test_api_floor_pass_cannot_authorize_wrong_server_version(self):
        self.version['Version'] = '29.8.1'
        with self.assertRaisesRegex(RuntimeError, 'Pinned server version/API differs'):
            runtime.validate_server(self.version, self.info, self.manifest)

    def test_wrong_daemon_identity_is_rejected(self):
        with self.assertRaisesRegex(RuntimeError, 'Wrong daemon identity'):
            runtime.validate_server(self.version, self.info, self.manifest, 'OTHER_TEST_DAEMON')

    def test_wrong_daemon_architecture_is_rejected(self):
        self.version['Arch'] = 'arm64'
        with self.assertRaisesRegex(RuntimeError, 'Wrong daemon architecture'):
            runtime.validate_server(self.version, self.info, self.manifest)

    def test_wrong_storage_backend_is_rejected(self):
        self.info['DriverStatus'] = []
        with self.assertRaisesRegex(RuntimeError, 'containerd image store is not effective'):
            runtime.validate_server(self.version, self.info, self.manifest)

    def test_missing_image_manifest_descriptor_is_rejected(self):
        with self.assertRaisesRegex(RuntimeError, 'ImageManifestDescriptor differs'):
            runtime.validate_descriptor({}, self.pin)

    def test_exact_manifest_descriptor_is_accepted(self):
        descriptor = {'mediaType': 'application/vnd.oci.image.manifest.v1+json',
                      'digest': self.pin['digest'], 'size': 123, 'platform': self.pin['platform']}
        self.assertEqual(runtime.validate_descriptor({'ImageManifestDescriptor': descriptor}, self.pin),
                         descriptor)

    def test_wrong_manifest_descriptor_identity_is_rejected(self):
        descriptor = {'mediaType': 'application/vnd.oci.image.manifest.v1+json',
                      'digest': 'sha256:' + '1' * 64, 'size': 123, 'platform': self.pin['platform']}
        with self.assertRaisesRegex(RuntimeError, 'ImageManifestDescriptor differs'):
            runtime.validate_descriptor({'ImageManifestDescriptor': descriptor}, self.pin)

    def test_original_empty_daemon_with_exact_base_networks_is_accepted(self):
        runtime.validate_empty(self.inventory)

    def test_original_nonempty_daemon_is_rejected(self):
        for field in ('containers', 'volumes'):
            with self.subTest(field=field):
                inventory = copy.deepcopy(self.inventory)
                inventory[field] = [{'Id': 'TEST_ONLY_EXISTING_RESOURCE'}]
                with self.assertRaisesRegex(RuntimeError, 'daemon is not empty'):
                    runtime.validate_empty(inventory)

    def test_original_unexpected_network_is_rejected(self):
        self.inventory['networks'].append({'Name': 'unexpected'})
        with self.assertRaisesRegex(RuntimeError, 'Unexpected daemon networks'):
            runtime.validate_empty(self.inventory)


class HostedRuntimeExecutableAndFailureTest(unittest.TestCase):
    def setUp(self):
        # Keep the complete fixture socket path below Linux's Unix-socket limit,
        # including when macOS's default temporary directory has a long prefix.
        self.temporary = tempfile.TemporaryDirectory(prefix='vra-hr-', dir='/tmp')
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name).resolve()

    def test_wrong_cli_executable_path_is_rejected(self):
        correct = self.root / 'approved-docker'
        correct.write_bytes(synthetic_elf())
        arbitrary = self.root / 'other-docker'
        arbitrary.write_bytes(correct.read_bytes())
        with self.assertRaisesRegex(RuntimeError, 'Executable identity mismatch'):
            runtime.validate_executable(arbitrary, correct, runtime.digest(correct))

    def test_wrong_containerd_executable_hash_is_rejected(self):
        executable = self.root / 'containerd'
        executable.write_bytes(synthetic_elf())
        with self.assertRaisesRegex(RuntimeError, 'Executable identity mismatch'):
            runtime.validate_executable(executable, executable, '0' * 64)

    def test_symlink_cli_resolution_is_rejected(self):
        executable = self.root / 'docker-real'
        executable.write_bytes(synthetic_elf())
        link = self.root / 'docker'
        link.symlink_to(executable)
        with self.assertRaisesRegex(RuntimeError, 'Executable identity mismatch'):
            runtime.validate_executable(link, link, runtime.digest(executable))

    def test_acquisition_failure_does_not_replace_original_or_publish_fallback(self):
        root = self.root / 'vra-poc04-docker-test'
        with mock.patch.dict(os.environ, {'RUNNER_TEMP': str(self.root)}, clear=True):
            instance = runtime.Runtime(root)
            with mock.patch.object(instance, 'original') as original, \
                    mock.patch.object(runtime, 'acquire', side_effect=RuntimeError('TEST_ACQUISITION_FAILURE')), \
                    mock.patch.object(instance, 'stop_original') as stop_original, \
                    mock.patch.object(instance, 'start') as start, \
                    mock.patch.object(instance, 'publish') as publish:
                with self.assertRaisesRegex(RuntimeError, '^TEST_ACQUISITION_FAILURE$'):
                    instance.provision()
                original.assert_called_once()
                stop_original.assert_not_called()
                start.assert_not_called()
                publish.assert_not_called()
        self.assertFalse((root / 'bin/docker').exists())

    def test_nonempty_original_rejects_before_acquisition_or_replacement(self):
        root = self.root / 'vra-poc04-docker-test'
        inventory = {'containers': [{'Id': 'TEST_ONLY_EXISTING_CONTAINER'}], 'volumes': [],
                     'networks': [{'Name': name} for name in ('bridge', 'host', 'none')]}
        version = json.dumps({'Os': 'linux', 'Arch': 'amd64', 'ApiVersion': '1.48'})
        info = json.dumps({'ID': 'TEST_ORIGINAL_DAEMON_ID'})
        original_cli = self.root / 'original-docker'
        original_cli.write_bytes(synthetic_elf())
        with mock.patch.dict(os.environ, {'RUNNER_TEMP': str(self.root)}, clear=True):
            instance = runtime.Runtime(root)
            with mock.patch.object(runtime.shutil, 'which', return_value=str(original_cli)), \
                    mock.patch.object(instance, 'run', side_effect=(version, info, '', '', '', '1234')), \
                    mock.patch.object(instance, 'process_identity', return_value={'pid': 1234}), \
                    mock.patch.object(runtime, 'inspect_inventory', return_value=inventory) as inspect_inventory, \
                    mock.patch.object(runtime, 'acquire') as acquire, \
                    mock.patch.object(instance, 'stop_original') as stop_original:
                with self.assertRaisesRegex(RuntimeError, 'daemon is not empty'):
                    instance.provision()
                acquire.assert_not_called()
                stop_original.assert_not_called()
                inspect_inventory.assert_called_once_with('/var/run/docker.sock', api='1.48')


class OriginalSocketShutdownPolicyTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory(prefix='vra-original-socket-policy-')
        self.addCleanup(self.temporary.cleanup)
        self.instance = object.__new__(runtime.Runtime)
        self.instance.root = Path(self.temporary.name)
        self.instance.state = {'run_id': '00000000-0000-4000-8000-000000000001',
                               'original': {'socket': '/run/docker.sock', 'pid': 12345}}
        self.instance.save = mock.Mock()
        self.instance.record = mock.Mock()
        self.listener = '/run/docker.sock (Stream)'
        self.effective = 'yes'
        self.policy_sha = hashlib.sha256(runtime.ORIGINAL_SOCKET_STOP_POLICY.encode()).hexdigest()
        self.actual_sha = self.policy_sha
        self.commands = []
        def command(argv, **kwargs):
            values = list(map(str, argv)); self.commands.append(values)
            if '--property=Listen' in values:
                return self.listener
            if '--property=RemoveOnStop' in values:
                return self.effective
            if values[0] == '/usr/bin/sha256sum':
                return self.actual_sha + '  original-socket-stop.conf'
            if values[0] == '/usr/bin/readlink':
                return str(self.instance.root / 'original-socket-stop.conf')
            if values[0] == '/usr/bin/stat':
                return '0'
            return ''
        self.instance.run = mock.Mock(side_effect=command)

    def execute(self):
        def metadata(path):
            return SimpleNamespace(st_uid=0, st_mode=0o100400 if path.name == 'original-socket-stop.conf' else 0o40755)
        with mock.patch.object(runtime.Path, 'is_file', return_value=True), \
                mock.patch.object(runtime.Path, 'is_symlink', return_value=False), \
                mock.patch.object(runtime.Path, 'is_dir', return_value=True), \
                mock.patch.object(runtime.Path, 'stat', metadata), \
                mock.patch.object(runtime.Path, 'exists', side_effect=lambda: False):
            self.instance.stop_original()

    def test_effective_job_scoped_policy_precedes_stop_and_mask_without_socket_unlink(self):
        self.execute()
        override = self.instance.state['original_socket_policy']
        self.assertEqual(override['sha256'], self.policy_sha)
        self.assertEqual(runtime.ORIGINAL_SOCKET_STOP_POLICY, '[Socket]\nRemoveOnStop=yes\n')
        reload_command = ['/usr/bin/systemctl', 'daemon-reload']
        stop_command = ['/usr/bin/systemctl', 'stop', 'docker.socket', 'docker.service']
        self.assertLess(self.commands.index(reload_command), self.commands.index(stop_command))
        self.assertIn(['/usr/bin/systemctl', 'mask', '--runtime', 'docker.socket', 'docker.service'], self.commands)
        self.assertFalse(any(values[0] in ('rm', '/usr/bin/rm', 'unlink', '/usr/bin/unlink') for values in self.commands))

    def test_policy_override_failure_rejects_before_stopping_original(self):
        self.effective = 'no'
        with self.assertRaisesRegex(RuntimeError, 'RemoveOnStop policy is not effective'):
            self.execute()
        self.assertFalse(any('stop' in values or 'mask' in values for values in self.commands))

    def test_unrelated_socket_listener_rejects_before_host_integration(self):
        self.listener = '/run/unrelated.sock (Stream)'
        with self.assertRaisesRegex(RuntimeError, 'does not own the observed Docker endpoint'):
            self.execute()
        self.assertEqual(len(self.commands), 1)

    def test_changed_socket_policy_hash_rejects_before_host_integration(self):
        self.actual_sha = '0' * 64
        with self.assertRaisesRegex(RuntimeError, 'shutdown policy differs'):
            self.execute()
        self.assertFalse(any(values[0] == '/usr/bin/ln' for values in self.commands))


class HostedRuntimeOwnedTeardownTest(unittest.TestCase):
    def setUp(self):
        # These are real external evidence writes and socket pathnames; only the
        # Linux/systemd boundary is substituted, never start/teardown themselves.
        self.temporary = tempfile.TemporaryDirectory(prefix='vra-td-', dir='/tmp')
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name).resolve() / 'vra-poc04-docker-test'
        environment = mock.patch.dict(os.environ, {'RUNNER_TEMP': str(self.root.parent)})
        environment.start()
        self.addCleanup(environment.stop)
        for module, name in ((runtime.subprocess, 'run'), (runtime.subprocess, 'Popen'),
                             (runtime.os, 'kill')):
            guard = mock.patch.object(module, name, side_effect=AssertionError('Live process boundary bypass denied'))
            guard.start()
            self.addCleanup(guard.stop)
        for name in ('bin', 'run', 'logs', 'evidence', 'state', 'downloads', 'docker-config',
                     'containerd-root', 'containerd-state', 'containerd-plugins', 'docker-data',
                     'docker-exec', 'buildx-state'):
            (self.root / name).mkdir(parents=True)
        (self.root / 'daemon.json').write_text('{}\n')
        (self.root / 'containerd.toml').write_text('version = 4\n')
        (self.root / 'original-socket-stop.conf').write_text('[Socket]\nRemoveOnStop=yes\n')
        (self.root / 'logs/dockerd.log').write_text('TEST_ONLY_PRESERVED_LOG\n')
        self.instance = runtime.Runtime.__new__(runtime.Runtime)
        self.instance.root = self.root
        self.instance.evidence = self.root / 'evidence'
        self.instance.state_path = self.root / 'state/runtime-state.json'
        self.instance.manifest = runtime.load_manifest()
        self.run_id = '00000000-0000-4000-8000-000000000002'
        self.socket_paths = [str(self.root / 'run' / name) for name in
                             ('docker.sock', 'containerd.sock', 'containerd.sock.ttrpc')]
        self.instance.state = {'schema_version': 1, 'run_id': self.run_id,
                               'runtime_root': str(self.root), 'socket_paths': self.socket_paths,
                               'units': {}, 'capability_resources': [],
                               'socket': str(self.root / 'run/docker.sock')}
        self.instance.env = dict(os.environ)
        self.path_observations = []
        self.auxiliary_layout(created=False)
        self.instance.inspect_path = self.inspect_owned_path
        self.commands = []
        self.proc = self.root.parent / 'proc'
        self.cgroups = self.root.parent / 'cgroup2'
        (self.proc / 'self').mkdir(parents=True)
        (self.proc / 'sys/kernel/random').mkdir(parents=True)
        (self.proc / 'sys/kernel/random/boot_id').write_text('00000000-0000-4000-8000-000000000003\n')
        self.cgroups.mkdir()
        (self.cgroups / 'cgroup.controllers').write_text('cpu memory pids\n')
        (self.proc / 'self/mountinfo').write_text(
            '36 25 0:32 / ' + str(self.cgroups) +
            ' rw,nosuid,nodev,noexec,relatime - cgroup2 cgroup rw\n' +
            '37 25 0:33 / /run rw,nosuid,nodev - tmpfs tmpfs rw\n')
        for name, value in (('PROC_ROOT', self.proc), ('CGROUP_ROOT', self.cgroups)):
            patcher = mock.patch.object(runtime, name, value, create=True)
            patcher.start()
            self.addCleanup(patcher.stop)
        for role in ('dockerd', 'containerd', 'runc'):
            executable = self.root / 'bin' / role
            executable.write_bytes(synthetic_elf() + role.encode())
            executable.chmod(0o755)
            next(row for row in self.instance.manifest['docker']['archive']['members']
                 if row['name'] == 'docker/' + role)['sha256'] = runtime.digest(executable)
        self.shim_bytes = synthetic_elf() + b'TEST_ONLY_PINNED_SHIM'
        shim_pin = next(row for row in self.instance.manifest['docker']['archive']['members']
                        if row['name'] == 'docker/containerd-shim-runc-v2')
        shim_pin.update(sha256=hashlib.sha256(self.shim_bytes).hexdigest(), size=len(self.shim_bytes))

    def auxiliary_layout(self, created=True):
        # Derive the reviewed fixed layout independently of production helpers.
        token = 'a' * 24
        root = '/run/vra-p04-' + token
        self.instance.state['auxiliary'] = {
            'run_id': self.run_id, 'token': token, 'root': root, 'cwd': root + '/cwd',
            'opt': root + '/opt', 'opt_bin': root + '/opt/bin', 'opt_lib': root + '/opt/lib',
            'socket_dir': root + '/s', 'shim': root + '/opt/bin/containerd-shim-runc-v2',
            'created': created}
        if created:
            self.instance.state['auxiliary']['identity'] = {'device': 1, 'inode': 2000}
        self.instance.state['shim_observations'] = []
        self.auxiliary_paths = {'/run': {
            'exists': True, 'kind': 'directory', 'realpath': '/run', 'uid': 0, 'gid': 0,
            'mode': '0755', 'symlink': False}}
        if created:
            entries = {'': ['cwd', 'opt', 's'], '/cwd': [], '/opt': ['bin', 'lib'],
                       '/opt/bin': ['containerd-shim-runc-v2'], '/opt/lib': [], '/s': []}
            for suffix, names in entries.items():
                path = root + suffix
                self.auxiliary_paths[path] = {
                    'exists': True, 'kind': 'directory', 'realpath': path, 'uid': 0, 'gid': 0,
                    'mode': '0700', 'symlink': False, 'entries': names, 'nodes': [],
                    'device': 1, 'inode': 2000}
            shim = self.instance.state['auxiliary']['shim']
            self.auxiliary_paths[shim] = {
                'exists': True, 'kind': 'regular', 'realpath': shim, 'uid': 0, 'gid': 0,
                'mode': '0755', 'symlink': False,
                'sha256': self.instance.member_hash('containerd-shim-runc-v2')}

    def inspect_owned_path(self, path, entries=False, sha256=False, recursive=False, text=False, peer=False):
        # Substitute privileged filesystem metadata only. Security decisions and
        # lifecycle methods remain the production implementation.
        path = Path(path)
        self.path_observations.append((str(path), entries, sha256, recursive, text, peer))
        if str(path) in self.auxiliary_paths:
            return copy.deepcopy(self.auxiliary_paths[str(path)])
        auxiliary = Path(self.instance.state['auxiliary']['root'])
        if path == auxiliary or path.is_relative_to(auxiliary):
            return {'exists': False}
        try:
            observed = path.lstat()
        except FileNotFoundError:
            return {'exists': False}
        kind = ('symlink' if path.is_symlink() else 'directory' if path.is_dir()
                else 'regular' if path.is_file() else 'socket' if runtime.stat.S_ISSOCK(observed.st_mode)
                else 'other')
        value = {'exists': True, 'kind': kind, 'realpath': str(path.resolve()),
                 'uid': 0, 'gid': 0, 'mode': format(runtime.stat.S_IMODE(observed.st_mode), '04o'),
                 'symlink': path.is_symlink(), 'device': observed.st_dev, 'inode': observed.st_ino}
        if entries:
            value['entries'] = sorted(item.name for item in path.iterdir())
        if sha256:
            value['sha256'] = runtime.digest(path)
        if recursive:
            value['nodes'] = []
        if text:
            value['text'] = path.read_text()
        return value

    def unit(self, role):
        return 'vra-poc04-' + role + '-' + self.run_id + '.service'

    def identity(self, role, pid):
        return {'pid': pid, 'exe': str(self.root / 'bin' / role),
                'sha256': runtime.digest(self.root / 'bin' / role),
                'start_time': '12345', 'boot_id': '00000000-0000-4000-8000-000000000003'}

    def description(self, role):
        root_hash = hashlib.sha256(os.fsencode(str(self.root))).hexdigest()
        return 'VRA POC04 ' + role + ' ' + self.run_id + ' ' + root_hash

    def owned_row(self, role, identity=None):
        # Deliberately derive the reviewed contract here, without unit_authority.
        row = {'unit': self.unit(role), 'role': role, 'run_id': self.run_id,
               'runtime_root': str(self.root), 'expected_exe': str(self.root / 'bin' / role),
               'expected_sha256': hashlib.sha256((self.root / 'bin' / role).read_bytes()).hexdigest(),
               'expected_control_group': '/system.slice/' + self.unit(role),
               'control_group': '/system.slice/' + self.unit(role),
               'description': self.description(role),
               'sockets': [str(self.root / 'run' / value) for value in
                           (('docker.sock',) if role == 'dockerd'
                            else ('containerd.sock', 'containerd.sock.ttrpc'))]}
        if role == 'containerd':
            auxiliary = self.instance.state['auxiliary']
            row.update(auxiliary_root=auxiliary['root'], working_directory=auxiliary['cwd'],
                       shim_executable=auxiliary['shim'], shim_socket_dir=auxiliary['socket_dir'])
        if identity is not None:
            row['identity'] = identity
        return row

    def properties(self, role, loaded=True, active=True, pid=0):
        return {'Id': self.unit(role), 'LoadState': 'loaded' if loaded else 'not-found',
                'ActiveState': 'active' if active else 'inactive',
                'SubState': 'running' if active else 'dead', 'MainPID': str(pid), 'ControlPID': '0',
                'ControlGroup': '/system.slice/' + self.unit(role) if loaded else '',
                'Description': self.description(role) if loaded else self.unit(role),
                'Transient': 'yes' if loaded else 'no', 'Slice': 'system.slice',
                'KillMode': 'control-group', 'Restart': 'no', 'SendSIGKILL': 'yes'}

    def environment_properties(self):
        auxiliary = self.instance.state['auxiliary']
        return {'WorkingDirectory': auxiliary['cwd'],
                'Environment': 'PATH=' + auxiliary['opt_bin'] + ' LD_LIBRARY_PATH=' + auxiliary['opt_lib'],
                'UnsetEnvironment': 'LD_PRELOAD LD_AUDIT'}

    def group(self, role, pids=(), descendant=None):
        target = self.cgroups / 'system.slice' / self.unit(role)
        target.mkdir(parents=True, exist_ok=True)
        (target / 'cgroup.events').write_text('populated ' + ('1' if pids else '0') + '\nfrozen 0\n')
        (target / 'cgroup.procs').write_text(''.join(str(pid) + '\n' for pid in pids))
        if descendant is not None:
            child = target / 'children'
            child.mkdir()
            (child / 'cgroup.procs').write_text(str(descendant) + '\n')
            (child / 'cgroup.events').write_text('populated 1\nfrozen 0\n')
            (target / 'cgroup.events').write_text('populated 1\nfrozen 0\n')
        return target

    def process(self, role, pid, start_time='12345', cgroup=None):
        path = self.proc / str(pid)
        path.mkdir(exist_ok=True)
        (path / 'stat').write_text(str(pid) + ' (' + role + ') ' + ' '.join(['S'] + ['0'] * 18 + [start_time]))
        (path / 'cgroup').write_text('0::' + (cgroup or '/system.slice/' + self.unit(role)) + '\n')

    def boundary(self, properties, stop_failures=(), remain_active=(), identities=None, stop_effects=None):
        def execute(command, **kwargs):
            values = list(map(str, command))
            self.commands.append(values)
            if values[:2] == ['/usr/bin/systemctl', 'show']:
                role = next(name for name in ('dockerd', 'containerd') if values[2] == self.unit(name))
                if values[3:] == ['--property=MainPID', '--value']:
                    return properties[role]['MainPID']
                if values[3:] == ['--no-pager', '--property=WorkingDirectory,Environment,UnsetEnvironment']:
                    return '\n'.join(key + '=' + value for key, value in self.environment_properties().items())
                return '\n'.join(key + '=' + value for key, value in properties[role].items())
            if values[:2] == ['/usr/bin/systemctl', 'stop']:
                self.assertTrue(kwargs.get('privileged'), 'Exact owned unit stop needs the reviewed boundary')
                self.assertEqual(len(values), 3)
                role = next(name for name in ('dockerd', 'containerd') if values[2] == self.unit(name))
                if role in stop_failures:
                    raise RuntimeError('TEST_ONLY_STOP_FAILURE_' + role)
                if role in (stop_effects or {}):
                    stop_effects[role]()
                if role not in remain_active:
                    properties[role] = self.properties(role, active=False)
                return ''
            if values[0] in ('/usr/bin/readlink', '/usr/bin/sha256sum'):
                self.assertTrue(kwargs.get('privileged'))
                pid = int(Path(values[1]).parent.name)
                role = next(name for name in ('dockerd', 'containerd')
                            if properties[name]['MainPID'] == str(pid) or
                            self.instance.state['units'].get(name, {}).get('identity', {}).get('pid') == pid)
                identity = self.identity(role, pid)
                identity.update((identities or {}).get(pid, {}))
                return identity['exe'] if values[0] == '/usr/bin/readlink' else identity['sha256'] + '  ' + values[1]
            if values[:3] == ['/usr/bin/rm', '-r', '--']:
                self.assertTrue(kwargs.get('privileged'))
                self.assertEqual(len(values), 4)
                path = Path(values[3])
                if path == Path(self.instance.state['auxiliary']['root']):
                    self.assertTrue(self.instance.state['auxiliary']['created'])
                    for observed in list(self.auxiliary_paths):
                        if observed == str(path) or Path(observed).is_relative_to(path):
                            del self.auxiliary_paths[observed]
                    return ''
                self.assertEqual(path.parent, self.root)
                self.assertIn(path.name, ('downloads', 'bin', 'docker-config', 'containerd-root',
                                         'containerd-state', 'containerd-plugins', 'docker-data',
                                         'docker-exec', 'run', 'buildx-state', 'daemon.json', 'containerd.toml'))
                if path.is_dir():
                    shutil.rmtree(path)
                else:
                    path.unlink()
                return ''
            self.fail('Unexpected external command: ' + repr(values))
        self.instance.run = execute

    def bound_units(self, loaded=True):
        properties = {}
        for role in ('dockerd', 'containerd'):
            self.instance.state['units'][role] = self.owned_row(role)
            properties[role] = self.properties(role, loaded=loaded, active=loaded)
            if loaded:
                self.group(role)
        self.instance.save()
        return properties

    def teardown_report(self, fails=False):
        if fails:
            with self.assertRaises(RuntimeError):
                self.instance.teardown()
        else:
            self.instance.teardown()
        report = json.loads((self.instance.evidence / 'teardown.json').read_text())
        self.assertEqual(report['status'], 'FAIL' if fails else 'PASS')
        self.assertEqual(bool(report['failures']), fails)
        return report

    def assert_cleanup_preserved(self):
        for name in ('evidence', 'state', 'logs', 'docker-data', 'containerd-root'):
            self.assertTrue((self.root / name).is_dir(), name + ' must remain after failed cleanup')

    def restore_runtime_fixture_paths(self):
        # A RED table case may expose an erroneous successful deletion. Restore
        # only the external fixture so the next independent case can still run.
        for name in ('bin', 'run', 'logs', 'evidence', 'state', 'downloads', 'docker-config',
                     'containerd-root', 'containerd-state', 'containerd-plugins', 'docker-data',
                     'docker-exec', 'buildx-state'):
            (self.root / name).mkdir(parents=True, exist_ok=True)
        for role in ('dockerd', 'containerd'):
            (self.root / 'bin' / role).write_bytes(synthetic_elf() + role.encode())

    def assert_only_owned_unit_stops(self, expected):
        stops = [command for command in self.commands if command[:2] == ['/usr/bin/systemctl', 'stop']]
        self.assertEqual(stops, [['/usr/bin/systemctl', 'stop', self.unit(role)] for role in expected])
        self.assertFalse(any(Path(command[0]).name in ('kill', 'pkill', 'killall', 'unlink')
                             for command in self.commands))

    def faulty_pre_stop_sample_fixture(self, roles, identity_unavailable=False,
                                      descendant_role=None, unknown_role=None):
        # Corrupt only the synthetic /proc read boundary. Production identity,
        # stop_owned_unit, teardown, and recursive cgroup proof stay real.
        properties = self.bound_units()
        identities = {}
        effects = {}
        for role in roles:
            pid = 12345 if role == 'dockerd' else 12346
            identity = self.identity(role, pid)
            identities[role] = identity
            self.instance.state['units'][role]['identity'] = identity
            self.process(role, pid)
            properties[role]['MainPID'] = str(pid)
            target = self.group(role, (pid,), descendant=23456 if role == descendant_role else None)
            if identity_unavailable:
                (self.proc / str(pid) / 'stat').unlink()
            else:
                (self.proc / str(pid) / 'cgroup').write_text('TEST_ONLY_MALFORMED_CGROUP\n')

            def stopped(role=role, pid=pid, target=target):
                if role != unknown_role:
                    shutil.rmtree(self.proc / str(pid))
                (target / 'cgroup.procs').write_text('')
                population = '1' if role == descendant_role else '0'
                (target / 'cgroup.events').write_text('populated ' + population + '\nfrozen 0\n')
            effects[role] = stopped
        self.boundary(properties, stop_effects=effects)
        return identities

    def assert_faulty_pre_stop_sample_outcome(self, roles, identity_unavailable=False,
                                            descendant_role=None, unknown_role=None):
        identities = self.faulty_pre_stop_sample_fixture(
            roles, identity_unavailable=identity_unavailable,
            descendant_role=descendant_role, unknown_role=unknown_role)
        failure = None
        try:
            self.instance.teardown()
        except RuntimeError as error:
            failure = error
        report = json.loads((self.instance.evidence / 'teardown.json').read_text())
        failed_role = descendant_role or unknown_role
        expected_stops = ('dockerd',) if failed_role == 'dockerd' else ('dockerd', 'containerd')
        # This catches the original partial-read unbound-variable branch before
        # schema assertions: optional observations cannot preempt exact stop.
        self.assert_only_owned_unit_stops(expected_stops)
        self.assertEqual(report['status'], 'FAIL' if failed_role else 'PASS')
        self.assertEqual(bool(report['failures']), bool(failed_role))
        if failed_role:
            self.assertIsInstance(failure, RuntimeError)
        else:
            self.assertIsNone(failure)
        for role in roles:
            observation_path = self.instance.evidence / (role + '-pre-stop-observation.json')
            self.assertTrue(observation_path.is_file(), 'Pre-stop observation must survive later proof failure')
            observation = json.loads(observation_path.read_text())
            self.assertEqual(observation, {
                'status': 'UNAVAILABLE' if identity_unavailable else 'PARTIAL',
                'identity': None if identity_unavailable else identities[role],
                'cgroup': None,
                'error_class': 'FileNotFoundError' if identity_unavailable else 'RuntimeError',
                'ownership': 'NOT ESTABLISHED'})
            if role != failed_role:
                self.assertEqual(report[role + '_unit']['pre_stop_observation'], observation)
                self.assertTrue(report[role + '_unit']['stopped'])
                self.assertEqual(report[role + '_unit']['owned_processes_remaining'], 0)
                self.assertEqual(report[role + '_unit']['identity_failures'], [])
                self.assertEqual(report[role + '_unit']['recorded_main_process'], 'ABSENT')
        if failed_role:
            self.assert_cleanup_preserved()
            self.assertFalse(any(command[0] == '/usr/bin/rm' for command in self.commands))
            self.assertFalse(any('UnboundLocalError' in value or 'not associated with a value' in value
                                 for value in report['failures']))
            if descendant_role:
                self.assertTrue(any('Owned cgroup retains processes' in value for value in report['failures']),
                                'Real recursive post-stop cgroup proof must reject the surviving descendant')
                child = self.cgroups / 'system.slice' / self.unit(descendant_role) / 'children/cgroup.procs'
                self.assertEqual(child.read_text(), '23456\n')
            else:
                self.assertFalse(report[unknown_role + '_unit']['stopped'])
                self.assertIsNone(report[unknown_role + '_unit']['owned_processes_remaining'])
                self.assertEqual(report[unknown_role + '_unit']['recorded_main_process'], 'UNKNOWN')
                self.assertTrue(any('Recorded main process identity observation unavailable' in value
                                    for value in report['failures']))
        else:
            for role in ('dockerd', 'containerd'):
                self.assertTrue(report[role + '_unit']['stopped'])
                self.assertEqual(report[role + '_unit']['cgroup']['owned_processes_remaining'], 0)
                self.assertEqual(report[role + '_unit']['unit_state']['MainPID'], '0')
            self.assertEqual(report['owned_sockets_remaining'], [])
            self.assertFalse(report['daemon_endpoint_available'])
            self.assertEqual(report['runtime_cleanup']['status'], 'PASS')
            for name in ('bin', 'run', 'docker-data', 'containerd-root'):
                self.assertFalse((self.root / name).exists())

    def test_dockerd_identity_success_cgroup_failure_does_not_block_complete_shutdown(self):
        self.assert_faulty_pre_stop_sample_outcome(('dockerd',))

    def test_containerd_identity_success_cgroup_failure_does_not_block_complete_shutdown(self):
        self.assert_faulty_pre_stop_sample_outcome(('containerd',))

    def test_both_identity_success_cgroup_failures_do_not_block_complete_shutdown(self):
        self.assert_faulty_pre_stop_sample_outcome(('dockerd', 'containerd'))

    def test_dockerd_partial_sample_stops_unit_then_descendant_proof_denies_cleanup(self):
        self.assert_faulty_pre_stop_sample_outcome(('dockerd',), descendant_role='dockerd')

    def test_containerd_partial_sample_stops_unit_then_descendant_proof_denies_cleanup(self):
        self.assert_faulty_pre_stop_sample_outcome(('containerd',), descendant_role='containerd')

    def test_both_partial_samples_stop_units_then_descendant_proof_denies_cleanup(self):
        self.assert_faulty_pre_stop_sample_outcome(('dockerd', 'containerd'), descendant_role='containerd')

    def test_identity_read_failure_before_cgroup_does_not_block_complete_shutdown(self):
        self.assert_faulty_pre_stop_sample_outcome(('dockerd', 'containerd'), identity_unavailable=True)

    def test_identity_read_failure_before_cgroup_still_requires_known_post_stop_identity(self):
        self.assert_faulty_pre_stop_sample_outcome(('dockerd',), identity_unavailable=True,
                                                 unknown_role='dockerd')

    def test_absent_recorded_pids_with_owned_socket_paths_cannot_report_pass(self):
        for role, pid in (('dockerd', 2147480000), ('containerd', 2147480001)):
            self.assertFalse(Path('/proc/' + str(pid)).exists(), 'Fixture PID must be absent')
            self.instance.state['units'][role] = self.owned_row(role, self.identity(role, pid))
        for name in ('docker.sock', 'containerd.sock', 'containerd.sock.ttrpc'):
            channel = socket.socket(socket.AF_UNIX, socket.SOCK_STREAM)
            self.addCleanup(channel.close)
            channel.bind(str(self.root / 'run' / name))
        self.instance.save()
        self.boundary({role: self.properties(role, loaded=False, active=False)
                       for role in ('dockerd', 'containerd')})

        failure = None
        try:
            self.instance.teardown()
        except RuntimeError as error:
            failure = error
        report = json.loads((self.instance.evidence / 'teardown.json').read_text())
        self.assertEqual(report['status'], 'FAIL', 'Surviving owned sockets cannot be cleanup PASS')
        self.assertIsInstance(failure, RuntimeError, 'Failed cleanup must fail the runtime operation')
        self.assertTrue(report['failures'])
        self.assertTrue(all((self.root / 'run' / name).exists()
                            for name in ('docker.sock', 'containerd.sock', 'containerd.sock.ttrpc')))

    def test_start_emits_control_group_kill_mode_for_owned_service(self):
        role = 'containerd'
        self.auxiliary_layout(created=True)
        auxiliary = self.instance.state['auxiliary']
        self.instance.run = lambda *args, **kwargs: '[]'
        self.instance.configuration()
        for filename in ('daemon.json', 'containerd.toml'):
            (self.root / filename).chmod(0o400)
        unit = self.unit(role)
        argv = [self.root / 'bin/containerd', '--config', self.root / 'containerd.toml']
        self.process(role, 12345)
        properties = self.properties(role, loaded=False, active=False)
        def boundary(command, **kwargs):
            values = list(map(str, command))
            self.commands.append(values)
            if values[0] == '/usr/bin/systemd-run':
                self.assertTrue(kwargs.get('privileged'))
                properties.update(self.properties(role, pid=12345))
                return ''
            if values[:3] == ['/usr/bin/systemctl', 'show', unit]:
                if values[3:] == ['--no-pager', '--property=WorkingDirectory,Environment,UnsetEnvironment']:
                    return '\n'.join(key + '=' + value for key, value in self.environment_properties().items())
                return '\n'.join(key + '=' + value for key, value in properties.items())
            if values[0] == '/usr/bin/readlink':
                return str(self.root / 'bin' / role)
            if values[0] == '/usr/bin/sha256sum':
                return hashlib.sha256((self.root / 'bin' / role).read_bytes()).hexdigest() + '  ' + values[1]
            self.fail('Unexpected external command: ' + repr(values))
        self.instance.run = boundary

        self.instance.start(role, argv, bundle_only=True)

        expected = ['/usr/bin/systemd-run', '--unit=' + unit, '--service-type=exec',
                    '--slice=system.slice', '--property=Description=' + self.description(role),
                    '--property=Restart=no', '--property=KillMode=control-group', '--property=SendSIGKILL=yes',
                    '--property=StandardOutput=append:' + str(self.root / 'logs/containerd.log'),
                    '--property=StandardError=append:' + str(self.root / 'logs/containerd.log'),
                    '--property=WorkingDirectory=' + auxiliary['cwd'],
                    '--property=UnsetEnvironment=LD_PRELOAD LD_AUDIT',
                    '--setenv=PATH=' + auxiliary['opt_bin'],
                    '--setenv=LD_LIBRARY_PATH=' + auxiliary['opt_lib'],
                    str(self.root / 'bin/containerd'), '--config', str(self.root / 'containerd.toml')]
        self.assertEqual([command for command in self.commands if command[0] == '/usr/bin/systemd-run'], [expected])
        saved = json.loads(self.instance.state_path.read_text())['units'][role]
        expected_row = self.owned_row(role, self.identity(role, 12345))
        self.assertEqual(saved, expected_row)

    def test_loaded_owned_units_are_stopped_when_recorded_main_pids_are_absent(self):
        properties = self.bound_units()
        for role, pid in (('dockerd', 2147480000), ('containerd', 2147480001)):
            self.instance.state['units'][role]['identity'] = self.identity(role, pid)
        self.boundary(properties)

        report = self.teardown_report()

        self.assert_only_owned_unit_stops(('dockerd', 'containerd'))
        for role in ('dockerd', 'containerd'):
            self.assertTrue(report[role + '_unit']['stopped'])
            self.assertEqual(report[role + '_unit']['owned_processes_remaining'], 0)
        self.assertEqual(report['owned_sockets_remaining'], [])
        self.assertFalse(report['daemon_endpoint_available'])
        self.assertEqual(report['runtime_cleanup']['status'], 'PASS')
        for name in ('docker-data', 'containerd-root', 'run', 'bin'):
            self.assertFalse((self.root / name).exists(), name + ' should be cleaned after proven absence')
        for name in ('evidence', 'logs', 'state'):
            self.assertTrue((self.root / name).is_dir())
        self.assertEqual((self.root / 'original-socket-stop.conf').read_text(), '[Socket]\nRemoveOnStop=yes\n')
        self.assertEqual((self.root / 'logs/dockerd.log').read_text(), 'TEST_ONLY_PRESERVED_LOG\n')
        for name in ('daemon.json', 'containerd.toml'):
            self.assertFalse((self.root / name).exists())

    def test_matching_live_main_processes_stop_and_prove_complete_cleanup(self):
        properties = self.bound_units()
        effects = {}
        for role, pid in (('dockerd', 12345), ('containerd', 12346)):
            self.instance.state['units'][role]['identity'] = self.identity(role, pid)
            self.process(role, pid)
            properties[role]['MainPID'] = str(pid)
            target = self.cgroups / 'system.slice' / self.unit(role)
            (target / 'cgroup.procs').write_text(str(pid) + '\n')
            (target / 'cgroup.events').write_text('populated 1\nfrozen 0\n')
            def stopped(pid=pid, target=target):
                # Simulate the external unit's process exit, then let production
                # independently read proc/cgroup absence before it deletes roots.
                shutil.rmtree(self.proc / str(pid))
                (target / 'cgroup.procs').write_text('')
                (target / 'cgroup.events').write_text('populated 0\nfrozen 0\n')
            effects[role] = stopped
        self.boundary(properties, stop_effects=effects)

        report = self.teardown_report()

        self.assert_only_owned_unit_stops(('dockerd', 'containerd'))
        for role, pid in (('dockerd', 12345), ('containerd', 12346)):
            self.assertTrue(report[role + '_unit']['stopped'])
            self.assertEqual(report[role + '_unit']['owned_processes_remaining'], 0)
            self.assertFalse((self.proc / str(pid)).exists())
            self.assertFalse((self.root / ('docker-data' if role == 'dockerd' else 'containerd-root')).exists())
        self.assertEqual(report['runtime_cleanup']['status'], 'PASS')

    def test_wrong_current_main_executable_hash_or_cgroup_denies_unit_stop(self):
        # Retain this historical method ID and all six security cases. The
        # approved authority rule permits exact unit stop despite this sample;
        # the surviving recorded process incarnation still denies cleanup.
        for role, pid in (('dockerd', 12345), ('containerd', 12346)):
            for field, value in (('exe', '/usr/bin/unrelated-daemon'), ('sha256', '0' * 64),
                                 ('cgroup', '/unrelated.slice/other.service')):
                with self.subTest(role=role, field=field):
                    properties = self.bound_units()
                    properties[role]['MainPID'] = str(pid)
                    self.instance.state['units'][role]['identity'] = self.identity(role, pid)
                    self.process(role, pid, cgroup=value if field == 'cgroup' else None)
                    identities = {pid: {field: value}} if field != 'cgroup' else {}
                    self.commands.clear()
                    self.boundary(properties, identities=identities)

                    report = self.teardown_report(fails=True)

                    self.assert_only_owned_unit_stops(('dockerd',) if role == 'dockerd'
                                                      else ('dockerd', 'containerd'))
                    self.assertTrue(any('Recorded owned main process remains after unit shutdown' in value
                                        for value in report['failures']))
                    self.assert_cleanup_preserved()
                    self.assertTrue((self.proc / str(pid)).is_dir())
                    self.assertFalse(any(command[0] == '/usr/bin/rm' for command in self.commands))

    def test_readable_current_main_mismatch_cannot_block_stop_with_complete_post_stop_proofs(self):
        # A sampled mismatch cannot veto independently valid unit ownership.
        # Exact whole-unit stop must still yield PASS when every final proof is clean.
        for role, pid in (('dockerd', 12345), ('containerd', 12346)):
            for field, value in (('exe', '/usr/bin/unrelated-daemon'), ('sha256', '0' * 64),
                                 ('cgroup', '/unrelated.slice/other.service')):
                with self.subTest(role=role, field=field):
                    self.restore_runtime_fixture_paths()
                    properties = self.bound_units()
                    properties[role]['MainPID'] = str(pid)
                    identity = self.identity(role, pid)
                    self.instance.state['units'][role]['identity'] = identity
                    self.process(role, pid, cgroup=value if field == 'cgroup' else None)
                    target = self.group(role, (pid,))
                    identities = {pid: {field: value}} if field != 'cgroup' else {}
                    def stopped(pid=pid, target=target):
                        shutil.rmtree(self.proc / str(pid))
                        (target / 'cgroup.procs').write_text('')
                        (target / 'cgroup.events').write_text('populated 0\nfrozen 0\n')
                    self.commands.clear()
                    self.boundary(properties, identities=identities, stop_effects={role: stopped})
                    failure = None
                    try:
                        self.instance.teardown()
                    except RuntimeError as error:
                        failure = error
                    report = json.loads((self.instance.evidence / 'teardown.json').read_text())

                    self.assert_only_owned_unit_stops(('dockerd', 'containerd'))
                    self.assertIsNone(failure)
                    self.assertEqual(report['status'], 'PASS')
                    sampled_identity = dict(identity)
                    if field != 'cgroup':
                        sampled_identity[field] = value
                    self.assertEqual(report[role + '_unit']['pre_stop_observation'], {
                        'status': 'OBSERVED', 'identity': sampled_identity,
                        'cgroup': value if field == 'cgroup' else '/system.slice/' + self.unit(role),
                        'error_class': None, 'ownership': 'NOT OWNED'})
                    self.assertEqual(report[role + '_unit']['recorded_main_process'], 'ABSENT')
                    self.assertEqual(report[role + '_unit']['identity_failures'], [])
                    self.assertEqual(report['runtime_cleanup']['status'], 'PASS')
                    self.assertFalse((self.proc / str(pid)).exists())

    def test_machine_unit_properties_reject_duplicate_missing_unknown_or_malformed_values(self):
        values = self.properties('dockerd')
        valid = '\n'.join(key + '=' + value for key, value in values.items())
        expected = ['/usr/bin/systemctl', 'show', self.unit('dockerd'), '--all', '--no-pager',
                    '--property=Id,LoadState,ActiveState,SubState,MainPID,ControlPID,ControlGroup,Description,Transient,Slice,KillMode,Restart,SendSIGKILL']
        for label, response in (
                ('duplicate', valid + '\nMainPID=0'),
                ('missing', '\n'.join(line for line in valid.splitlines() if not line.startswith('Transient='))),
                ('unknown', valid + '\nUnreviewedAuthority=yes'),
                ('not-key-value', valid + '\nMALFORMED'),
                ('wrong-unit', valid.replace('Id=' + self.unit('dockerd'), 'Id=unrelated.service')),
                ('main-pid', valid.replace('MainPID=0', 'MainPID=unknown')),
                ('control-pid', valid.replace('ControlPID=0', 'ControlPID=-1'))):
            with self.subTest(property=label):
                def external(command, **kwargs):
                    self.assertEqual(list(map(str, command)), expected)
                    return response
                self.instance.run = external

                with self.assertRaises(RuntimeError):
                    self.instance.unit_properties(self.unit('dockerd'))

    def test_not_found_units_with_independently_empty_or_absent_cgroups_pass(self):
        properties = self.bound_units(loaded=False)
        self.group('containerd')
        self.boundary(properties)

        report = self.teardown_report()

        self.assert_only_owned_unit_stops(())
        self.assertFalse(report['dockerd_unit']['cgroup']['exists'])
        self.assertTrue(report['containerd_unit']['cgroup']['exists'])
        self.assertEqual(report['runtime_cleanup']['status'], 'PASS')
        # Repeat proof from preserved metadata and a physically absent runtime.
        repeated = self.teardown_report()
        self.assertEqual(repeated['owned_sockets_remaining'], [])

    def test_descendant_process_in_each_owned_role_blocks_cleanup(self):
        for role in ('dockerd', 'containerd'):
            with self.subTest(role=role):
                properties = self.bound_units()
                target = self.cgroups / 'system.slice' / self.unit(role)
                child = target / 'children'
                child.mkdir(exist_ok=True)
                (child / 'cgroup.procs').write_text('23456\n')
                (target / 'cgroup.events').write_text('populated 1\nfrozen 0\n')
                self.commands.clear()
                self.boundary(properties)

                self.teardown_report(fails=True)

                self.assert_only_owned_unit_stops(('dockerd',) if role == 'dockerd'
                                                  else ('dockerd', 'containerd'))
                self.assert_cleanup_preserved()
                self.assertTrue((child / 'cgroup.procs').is_file())
                (child / 'cgroup.procs').write_text('')
                (target / 'cgroup.events').write_text('populated 0\nfrozen 0\n')

    def test_each_owned_socket_path_remaining_blocks_cleanup(self):
        for name in ('docker.sock', 'containerd.sock', 'containerd.sock.ttrpc'):
            with self.subTest(socket=name):
                properties = self.bound_units()
                remaining = self.root / 'run' / name
                remaining.touch()
                self.commands.clear()
                self.boundary(properties)

                report = self.teardown_report(fails=True)

                self.assertEqual(report['owned_sockets_remaining'], [str(remaining)])
                self.assertTrue(remaining.exists(), 'Cleanup must preserve failed socket evidence')
                self.assert_cleanup_preserved()
                remaining.unlink()

    def test_reused_recorded_pid_does_not_authorize_signaling_the_pid(self):
        properties = self.bound_units()
        self.instance.state['units']['dockerd']['identity'] = self.identity('dockerd', 12345)
        self.process('dockerd', 12345, start_time='99999', cgroup='/unrelated.slice/reused.service')
        self.boundary(properties)

        report = self.teardown_report()

        self.assert_only_owned_unit_stops(('dockerd', 'containerd'))
        self.assertTrue((self.proc / '12345').is_dir(), 'An unrelated reused PID must remain untouched')
        self.assertEqual(report['runtime_cleanup']['status'], 'PASS')

    def test_not_found_unit_cannot_hide_descendants(self):
        properties = self.bound_units(loaded=False)
        self.group('containerd', descendant=23456)
        self.boundary(properties)

        self.teardown_report(fails=True)

        self.assert_only_owned_unit_stops(())
        self.assert_cleanup_preserved()

    def test_dockerd_stop_failure_preserves_roots_and_defers_containerd(self):
        self.boundary(self.bound_units(), stop_failures=('dockerd',))

        report = self.teardown_report(fails=True)

        self.assertTrue(any('TEST_ONLY_STOP_FAILURE_dockerd' in failure for failure in report['failures']))
        self.assert_only_owned_unit_stops(('dockerd',))
        self.assertTrue(any('containerd stop deferred' in failure for failure in report['failures']))
        self.assert_cleanup_preserved()

    def test_active_unit_after_successful_stop_blocks_cleanup(self):
        self.boundary(self.bound_units(), remain_active=('containerd',))

        self.teardown_report(fails=True)

        self.assert_only_owned_unit_stops(('dockerd', 'containerd'))
        self.assert_cleanup_preserved()

    def test_recorded_authority_mismatch_in_each_role_denies_that_stop(self):
        mutations = {'role': 'unrelated', 'run_id': '00000000-0000-4000-8000-000000000099',
                     'runtime_root': '/tmp/another-runtime', 'unit': 'unrelated.service',
                     'expected_exe': '/usr/bin/other', 'expected_sha256': '0' * 64,
                     'expected_control_group': '/unrelated.slice/other.service',
                     'control_group': '/unrelated.slice/other.service',
                     'sockets': ['/run/unrelated.sock'], 'description': 'unrelated ownership'}
        for role in ('dockerd', 'containerd'):
            for field, value in mutations.items():
                with self.subTest(role=role, field=field):
                    properties = self.bound_units()
                    self.instance.state['units'][role][field] = value
                    self.commands.clear()
                    self.boundary(properties)

                    self.teardown_report(fails=True)

                    self.assertNotIn(['/usr/bin/systemctl', 'stop', self.unit(role)], self.commands)
                    self.assert_cleanup_preserved()

    def test_observed_systemd_authority_mismatch_denies_exact_unit_stop(self):
        mutations = {'Description': 'unrelated service', 'ControlGroup': '/unrelated.slice/other.service',
                     'Transient': 'no', 'Slice': 'other.slice', 'KillMode': 'process',
                     'Restart': 'always', 'SendSIGKILL': 'no'}
        for field, value in mutations.items():
            with self.subTest(field=field):
                properties = self.bound_units()
                properties['dockerd'][field] = value
                self.commands.clear()
                self.boundary(properties)

                self.teardown_report(fails=True)

                self.assertNotIn(['/usr/bin/systemctl', 'stop', self.unit('dockerd')], self.commands)
                self.assert_cleanup_preserved()

    def test_partial_start_saves_cgroup_binding_before_pid_identity_failure(self):
        role = 'dockerd'
        properties = self.properties(role, loaded=False, active=False)
        def boundary(command, **kwargs):
            values = list(map(str, command))
            self.commands.append(values)
            if values[0] == '/usr/bin/systemd-run':
                properties.update(self.properties(role, pid=12345))
                return ''
            if values[:3] == ['/usr/bin/systemctl', 'show', self.unit(role)]:
                return '\n'.join(key + '=' + value for key, value in properties.items())
            self.fail('Unexpected external command: ' + repr(values))
        self.instance.run = boundary
        self.instance.process_identity = mock.Mock(side_effect=RuntimeError('TEST_ONLY_MAIN_PID_GONE'))

        with self.assertRaisesRegex(RuntimeError, 'TEST_ONLY_MAIN_PID_GONE'):
            self.instance.start(role, [self.root / 'bin/dockerd', '--config-file', self.root / 'daemon.json'])

        saved = json.loads(self.instance.state_path.read_text())['units'][role]
        self.assertEqual(saved, self.owned_row(role))
        self.assertNotIn('identity', saved)
        self.boundary({'dockerd': self.properties('dockerd'),
                       'containerd': self.properties('containerd', loaded=False, active=False)})
        self.group(role)
        report = self.teardown_report()
        self.assertTrue(report['dockerd_unit']['stopped'])

    def test_existing_unit_blocks_start_before_process_creation(self):
        self.boundary({'dockerd': self.properties('dockerd'),
                       'containerd': self.properties('containerd')})

        with self.assertRaises(RuntimeError):
            self.instance.start('dockerd', [self.root / 'bin/dockerd'])

        self.assertFalse(any(command[0] == '/usr/bin/systemd-run' for command in self.commands))

    def test_mount_below_cleanup_root_blocks_all_removal(self):
        self.boundary(self.bound_units())
        marker = self.root / 'docker-data/do-not-delete'
        marker.write_text('TEST_ONLY_MOUNT_CONTENT')
        with (self.proc / 'self/mountinfo').open('a') as mounts:
            mounts.write('37 25 0:33 / ' + str(self.root / 'docker-data') + ' rw - tmpfs tmpfs rw\n')

        self.teardown_report(fails=True)

        self.assert_cleanup_preserved()
        self.assertEqual(marker.read_text(), 'TEST_ONLY_MOUNT_CONTENT')

    def test_each_recorded_main_process_remaining_blocks_cleanup(self):
        for role, pid in (('dockerd', 12345), ('containerd', 12346)):
            with self.subTest(role=role):
                properties = self.bound_units()
                self.instance.state['units'][role]['identity'] = self.identity(role, pid)
                self.process(role, pid)
                target = self.cgroups / 'system.slice' / self.unit(role)
                (target / 'cgroup.procs').write_text(str(pid) + '\n')
                (target / 'cgroup.events').write_text('populated 1\nfrozen 0\n')
                self.commands.clear()
                self.boundary(properties)

                self.teardown_report(fails=True)

                self.assert_only_owned_unit_stops(('dockerd',) if role == 'dockerd'
                                                  else ('dockerd', 'containerd'))
                self.assert_cleanup_preserved()
                self.assertTrue((self.proc / str(pid)).is_dir())
                (target / 'cgroup.procs').write_text('')
                (target / 'cgroup.events').write_text('populated 0\nfrozen 0\n')
                self.instance.state['units'][role].pop('identity')

    def test_unchanged_recorded_process_outside_owned_cgroup_blocks_cleanup(self):
        properties = self.bound_units()
        self.instance.state['units']['dockerd']['identity'] = self.identity('dockerd', 12345)
        self.process('dockerd', 12345, cgroup='/unrelated.slice/escaped.service')
        self.boundary(properties)

        self.teardown_report(fails=True)

        self.assert_cleanup_preserved()
        self.assertTrue((self.proc / '12345').is_dir())
        self.assertFalse(any(Path(command[0]).name in ('kill', 'pkill', 'killall') for command in self.commands))

    def test_unreadable_recorded_pid_does_not_discard_exact_unit_stop_authority(self):
        properties = self.bound_units()
        self.instance.state['units']['dockerd']['identity'] = self.identity('dockerd', 12345)
        self.process('dockerd', 12345)
        self.boundary(properties)
        self.instance.process_identity = mock.Mock(side_effect=OSError('TEST_ONLY_PID_IDENTITY_UNREADABLE'))

        report = self.teardown_report(fails=True)

        self.assertIn(['/usr/bin/systemctl', 'stop', self.unit('dockerd')], self.commands)
        self.assert_only_owned_unit_stops(('dockerd',))
        self.assertFalse(report['dockerd_unit']['stopped'])
        self.assertIsNone(report['dockerd_unit']['owned_processes_remaining'])
        self.assertTrue(any('containerd stop deferred' in failure for failure in report['failures']))
        self.assert_cleanup_preserved()
        self.assertFalse(any(Path(command[0]).name in ('kill', 'pkill', 'killall') for command in self.commands))

    def test_same_process_incarnation_with_changed_executable_cannot_be_pid_reuse(self):
        for field, value in (('exe', '/usr/bin/escaped-replacement'), ('sha256', '0' * 64)):
            with self.subTest(changed=field):
                self.restore_runtime_fixture_paths()
                properties = self.bound_units()
                self.instance.state['units']['dockerd']['identity'] = self.identity('dockerd', 12345)
                self.process('dockerd', 12345, cgroup='/unrelated.slice/escaped.service')
                self.commands.clear()
                self.boundary(properties, identities={12345: {field: value}})

                self.teardown_report(fails=True)

                self.assert_cleanup_preserved()
                self.assertTrue((self.proc / '12345').is_dir())
                self.assertFalse(any(Path(command[0]).name in ('kill', 'pkill', 'killall')
                                     for command in self.commands))

    def test_missing_or_malformed_recorded_process_incarnation_denies_cleanup(self):
        for field, value in (('start_time', None), ('start_time', ''), ('start_time', '-1'),
                             ('start_time', 'not-a-clock'), ('start_time', 12345),
                             ('boot_id', None), ('boot_id', ''), ('boot_id', 'not-a-uuid'),
                             ('boot_id', 42)):
            with self.subTest(field=field, value=value):
                self.restore_runtime_fixture_paths()
                properties = self.bound_units()
                identity = self.identity('dockerd', 2147480000)
                if value is None:
                    del identity[field]
                else:
                    identity[field] = value
                self.instance.state['units']['dockerd']['identity'] = identity
                self.commands.clear()
                self.boundary(properties)

                self.teardown_report(fails=True)

                self.assert_cleanup_preserved()
                self.assertFalse(any(Path(command[0]).name in ('kill', 'pkill', 'killall')
                                     for command in self.commands))

    def test_mount_at_runtime_root_denies_cleanup_and_preserves_contents(self):
        self.boundary(self.bound_units())
        marker = self.root / 'docker-data/external-victim'
        marker.write_text('TEST_ONLY_EXTERNAL_MOUNT_CONTENT')
        with (self.proc / 'self/mountinfo').open('a') as mounts:
            mounts.write('37 25 0:33 / ' + str(self.root) + ' rw - tmpfs tmpfs rw\n')

        self.teardown_report(fails=True)

        self.assert_cleanup_preserved()
        self.assertEqual(marker.read_text(), 'TEST_ONLY_EXTERNAL_MOUNT_CONTENT')
        self.assertFalse(any(command[0] == '/usr/bin/rm' for command in self.commands))

    def test_endpoint_probe_observes_real_absent_refused_and_live_socket(self):
        self.assertTrue(self.instance.endpoint_unavailable())
        endpoint = socket.socket(socket.AF_UNIX, socket.SOCK_STREAM)
        self.addCleanup(endpoint.close)
        endpoint.bind(self.instance.state['socket'])
        self.assertTrue(self.instance.endpoint_unavailable())
        endpoint.listen(1)
        self.assertFalse(self.instance.endpoint_unavailable())

    def test_live_owned_endpoint_reports_available_and_blocks_cleanup(self):
        self.boundary(self.bound_units())
        endpoint = socket.socket(socket.AF_UNIX, socket.SOCK_STREAM)
        self.addCleanup(endpoint.close)
        endpoint.bind(self.instance.state['socket'])
        endpoint.listen(1)

        report = self.teardown_report(fails=True)

        self.assertTrue(report['daemon_endpoint_available'])
        self.assertEqual(report['owned_sockets_remaining'], [self.instance.state['socket']])
        self.assert_cleanup_preserved()

    def test_missing_or_mismatched_runtime_state_authority_denies_all_stop_and_cleanup(self):
        mutations = (('runtime_root', None), ('runtime_root', '/tmp/unrelated-runtime'),
                     ('socket', '/run/unrelated.sock'), ('socket_paths', ['/run/unrelated.sock']),
                     ('run_id', 'not-a-uuid'))
        for field, value in mutations:
            with self.subTest(field=field, value=value):
                properties = self.bound_units()
                original = self.instance.state[field]
                if value is None:
                    del self.instance.state[field]
                else:
                    self.instance.state[field] = value
                self.commands.clear()
                self.boundary(properties)

                self.teardown_report(fails=True)

                self.assert_only_owned_unit_stops(())
                self.assert_cleanup_preserved()
                self.instance.state[field] = original

    def test_missing_state_never_authorizes_success_even_when_runtime_directory_is_absent(self):
        self.instance.state = None
        self.instance.run = lambda *args, **kwargs: self.fail('Missing state grants no command authority')
        for root in (self.root, self.root.parent / 'vra-poc04-docker-missing'):
            with self.subTest(runtime_directory_exists=root.exists()):
                self.instance.root = root
                with self.assertRaises(RuntimeError):
                    self.instance.teardown()
        self.assert_cleanup_preserved()

    def test_loaded_unit_without_recorded_start_intent_grants_no_stop_authority(self):
        properties = self.bound_units()
        del self.instance.state['units']['dockerd']
        self.boundary(properties)

        self.teardown_report(fails=True)

        self.assertNotIn(['/usr/bin/systemctl', 'stop', self.unit('dockerd')], self.commands)
        self.assert_cleanup_preserved()

    def test_cgroup_proof_reads_descendants_and_population_from_real_files(self):
        target = self.group('dockerd')
        group = '/system.slice/' + self.unit('dockerd')
        self.assertEqual(self.instance.cgroup_proof(group),
                         {'control_group': group, 'exists': True, 'owned_processes_remaining': 0})
        child = target / 'delegated'
        child.mkdir()
        (child / 'cgroup.procs').write_text('23456\n')
        with self.assertRaises(RuntimeError):
            self.instance.cgroup_proof(group)
        (child / 'cgroup.procs').write_text('')
        (target / 'cgroup.events').write_text('populated 1\nfrozen 0\n')
        with self.assertRaises(RuntimeError):
            self.instance.cgroup_proof(group)

    def test_cgroup_proof_rejects_missing_mount_missing_file_and_symlink_authority(self):
        group = '/system.slice/' + self.unit('dockerd')
        target = self.group('dockerd')
        mountinfo = self.proc / 'self/mountinfo'
        mount = mountinfo.read_text()
        mountinfo.write_text('')
        with self.assertRaises(RuntimeError):
            self.instance.cgroup_proof(group)
        mountinfo.write_text(mount)
        (target / 'cgroup.procs').unlink()
        with self.assertRaises(RuntimeError):
            self.instance.cgroup_proof(group)
        outside = self.root.parent / 'unrelated-procs'
        outside.write_text('')
        (target / 'cgroup.procs').symlink_to(outside)
        with self.assertRaises(RuntimeError):
            self.instance.cgroup_proof(group)

    def test_remapped_stacked_or_overmounted_cgroup_mount_cannot_prove_absence(self):
        mountinfo = self.proc / 'self/mountinfo'
        valid_mount = mountinfo.read_text()
        owned = self.cgroups / 'system.slice' / self.unit('dockerd')
        cases = (
            ('remapped-root', valid_mount.replace('0:32 / ', '0:32 /unrelated.slice ')),
            ('stacked-root', valid_mount + '37 25 0:33 / ' + str(self.cgroups) + ' rw - tmpfs tmpfs rw\n'),
            ('overmounted-owned-group', valid_mount + '37 25 0:33 / ' + str(owned) + ' rw - tmpfs tmpfs rw\n'),
            ('overmounted-owned-descendant', valid_mount + '37 25 0:33 / ' + str(owned / 'hidden') +
             ' rw - tmpfs tmpfs rw\n'))
        for label, mounts in cases:
            with self.subTest(mount=label):
                self.restore_runtime_fixture_paths()
                self.boundary(self.bound_units(loaded=False))
                mountinfo.write_text(mounts)
                self.commands.clear()

                self.teardown_report(fails=True)

                self.assert_only_owned_unit_stops(())
                self.assert_cleanup_preserved()

    def test_cgroup_proof_rejects_malformed_process_or_population(self):
        group = '/system.slice/' + self.unit('containerd')
        target = self.group('containerd')
        for value in ('0\n', '-1\n', 'unknown\n'):
            with self.subTest(process=value):
                (target / 'cgroup.procs').write_text(value)
                with self.assertRaises(RuntimeError):
                    self.instance.cgroup_proof(group)
        (target / 'cgroup.procs').write_text('')
        for value in ('', 'populated unknown\n', 'populated 0\npopulated 1\n'):
            with self.subTest(population=value):
                (target / 'cgroup.events').write_text(value)
                with self.assertRaises(RuntimeError):
                    self.instance.cgroup_proof(group)


class HostedRuntimeShimIsolationTest(unittest.TestCase):
    def setUp(self):
        # Reuse Linux/systemd boundary fixtures without inheriting or duplicating
        # historical test methods. Production security/lifecycle methods stay real.
        self.fixture = HostedRuntimeOwnedTeardownTest(
            'test_matching_live_main_processes_stop_and_prove_complete_cleanup')
        self.addCleanup(self.fixture.doCleanups)
        self.fixture.setUp()
        self.fixture.auxiliary_layout(created=True)
        self.instance = self.fixture.instance
        self.root = self.fixture.root
        self.commands = self.fixture.commands
        self.auxiliary = copy.deepcopy(self.instance.state['auxiliary'])
        self.fixture.boundary({role: self.fixture.properties(role, loaded=False, active=False)
                               for role in ('dockerd', 'containerd')})

    def configure(self):
        boundary = self.instance.run
        def execute(command, **kwargs):
            values = list(map(str, command))
            if values == ['/usr/sbin/ip', '-j', 'route', 'show', 'table', 'all']:
                self.commands.append(values)
                return '[]'
            return boundary(command, **kwargs)
        self.instance.run = execute
        self.instance.configuration()
        return (json.loads((self.root / 'daemon.json').read_text()),
                (self.root / 'containerd.toml').read_text())

    def assert_no_mutation(self):
        self.assertFalse(any(command[0] in ('/usr/bin/systemd-run', '/usr/bin/mkdir', '/usr/bin/rm')
                             for command in self.commands))
        self.assertTrue((self.root / 'docker-data').is_dir())
        self.assertTrue((self.root / 'containerd-root').is_dir())

    def test_short_socket_directory_is_exact_39_bytes_under_run(self):
        authority = runtime.auxiliary_authority(self.fixture.run_id, 'a' * 24)
        expected = {key: value for key, value in self.auxiliary.items() if key not in ('created', 'identity')}
        self.assertEqual(authority, expected)
        self.assertEqual(len(os.fsencode(authority['root'])), 37)
        self.assertEqual(len(os.fsencode(authority['socket_dir'])), 39)
        self.assertLessEqual(len(os.fsencode(authority['socket_dir'])), 42)
        self.assertEqual(Path(authority['root']).parent, Path('/run'))
        self.assertNotEqual(authority['socket_dir'], '/run/containerd/s')

    def test_malformed_or_pathlike_token_grants_no_auxiliary_authority(self):
        for token in (None, '', 'a' * 23, 'a' * 25, 'A' * 24, '../' + 'a' * 21,
                      'a' * 23 + '/', 'g' * 24, 123):
            with self.subTest(token=token), self.assertRaises((RuntimeError, ValueError, TypeError)):
                runtime.auxiliary_authority(self.fixture.run_id, token)
        self.assert_no_mutation()

    def test_malformed_run_identity_grants_no_auxiliary_authority(self):
        for run_id in ('not-a-uuid', '00000000-0000-4000-8000-00000000000A', None):
            with self.subTest(run_id=run_id), self.assertRaises((RuntimeError, ValueError, TypeError, AttributeError)):
                runtime.auxiliary_authority(run_id, 'a' * 24)
        self.assert_no_mutation()

    def test_missing_or_mismatched_auxiliary_ledger_denies_before_unit_stop(self):
        mutations = [(None, None), ('root', '/run/containerd'), ('token', 'b' * 24),
                     ('run_id', '00000000-0000-4000-8000-000000000099'),
                     ('socket_dir', '/run/containerd/s'), ('cwd', str(self.root)), ('created', 1)]
        for key, value in mutations:
            with self.subTest(field=key):
                self.instance.state['auxiliary'] = copy.deepcopy(self.auxiliary)
                properties = self.fixture.bound_units()
                if key is None:
                    del self.instance.state['auxiliary']
                else:
                    self.instance.state['auxiliary'][key] = value
                self.commands.clear()
                self.fixture.boundary(properties)
                report = self.fixture.teardown_report(fails=True)
                self.assertEqual(report['status'], 'FAIL')
                self.fixture.assert_only_owned_unit_stops(())
                self.fixture.assert_cleanup_preserved()
                self.assert_no_mutation()
        self.instance.state['auxiliary'] = copy.deepcopy(self.auxiliary)

    def test_auxiliary_root_collision_denies_before_creation_or_publication(self):
        self.instance.state['auxiliary']['created'] = False
        with self.assertRaisesRegex(RuntimeError, 'collision'):
            self.instance.create_auxiliary({'containerd-shim-runc-v2': self.fixture.shim_bytes})
        self.assert_no_mutation()
        self.assertFalse(self.instance.state['auxiliary']['created'])

    def test_run_parent_owner_mode_symlink_and_type_are_fail_closed(self):
        original = copy.deepcopy(self.fixture.auxiliary_paths['/run'])
        for field, value in (('uid', 1000), ('gid', 1000), ('mode', '0777'),
                             ('symlink', True), ('kind', 'regular'), ('realpath', '/tmp/run')):
            with self.subTest(field=field):
                self.fixture.auxiliary_paths['/run'] = {**original, field: value}
                with self.assertRaisesRegex(RuntimeError, 'Owned path identity differs'):
                    self.instance.validate_run_parent()
                self.assert_no_mutation()
        self.fixture.auxiliary_paths['/run'] = original

    def test_stacked_remapped_or_auxiliary_mount_denies_creation(self):
        mountinfo = self.fixture.proc / 'self/mountinfo'
        original = mountinfo.read_text()
        run_row = '37 25 0:33 / /run rw,nosuid,nodev - tmpfs tmpfs rw\n'
        variants = (original.replace(run_row, ''), original + run_row.replace('37 ', '38 ', 1),
                    original.replace('0:33 / /run', '0:33 /other /run'),
                    original + '39 37 0:34 / ' + self.auxiliary['root'] +
                    ' rw - tmpfs tmpfs rw\n',
                    original + '39 37 0:34 / ' + self.auxiliary['socket_dir'] +
                    ' rw - tmpfs tmpfs rw\n')
        for index, value in enumerate(variants):
            with self.subTest(case=index):
                mountinfo.write_text(value)
                with self.assertRaises(RuntimeError):
                    self.instance.validate_run_parent()
                self.assert_no_mutation()
        mountinfo.write_text(original)

    def test_exclusive_creation_saves_ownership_before_verified_shim_publication(self):
        self.fixture.auxiliary_layout(created=False)
        authority = self.instance.state['auxiliary']
        def execute(command, **kwargs):
            values = list(map(str, command))
            self.commands.append(values)
            self.assertTrue(kwargs.get('privileged'))
            if values[:3] == ['/usr/bin/mkdir', '-m', '0700']:
                self.assertEqual(values[3], '--')
                path = values[4]
                self.assertFalse(self.fixture.auxiliary_paths.get(path, {}).get('exists', False))
                self.fixture.auxiliary_paths[path] = {
                    'exists': True, 'kind': 'directory', 'realpath': path, 'uid': 0, 'gid': 0,
                    'mode': '0700', 'symlink': False, 'entries': [], 'nodes': [],
                    'device': 1, 'inode': 2000}
                return ''
            self.assertEqual(values[:5], ['/usr/bin/python3', '-I', '-S', '-B', '-c'])
            self.assertEqual(values[5], runtime.SHIM_PUBLICATION)
            self.assertEqual(values[6], authority['shim'])
            self.assertEqual(values[7], self.instance.member_hash('containerd-shim-runc-v2'))
            self.assertEqual(values[8], str(len(self.fixture.shim_bytes)))
            self.assertEqual(kwargs.get('input_data'), self.fixture.shim_bytes)
            self.assertTrue(json.loads(self.instance.state_path.read_text())['auxiliary']['created'])
            return ''
        self.instance.run = execute
        self.instance.create_auxiliary({'containerd-shim-runc-v2': self.fixture.shim_bytes})
        self.assertTrue(authority['created'])
        self.assertEqual([command[4] for command in self.commands if command[0] == '/usr/bin/mkdir'],
                         [authority[key] for key in ('root', 'cwd', 'opt', 'opt_bin', 'opt_lib', 'socket_dir')])
        self.assertEqual(len([command for command in self.commands if command[0] == '/usr/bin/python3']), 1)
        self.assertFalse(any('/run/containerd' in item or '/opt/containerd' in item
                             for command in self.commands for item in command))

    def test_created_false_never_authorizes_existing_short_root_cleanup(self):
        self.instance.state['auxiliary']['created'] = False
        with self.assertRaises(RuntimeError):
            self.instance.cleanup_auxiliary()
        self.assert_no_mutation()
        self.assertTrue(self.fixture.auxiliary_paths[self.auxiliary['root']]['exists'])

    def test_exact_owned_layout_is_accepted_without_ambient_candidate_reads(self):
        with mock.patch.dict(os.environ, {'PATH': '/TEST_ONLY_HOSTILE/bin:/usr/bin',
                                        'LD_LIBRARY_PATH': '/TEST_ONLY_HOSTILE/lib',
                                        'LD_PRELOAD': '/TEST_ONLY_HOSTILE/inject.so'}):
            proof = self.instance.shim_preconditions()
        self.assertEqual(proof['initial_path'], self.auxiliary['opt_bin'])
        self.assertEqual(proof['initial_library_path'], self.auxiliary['opt_lib'])
        self.assertEqual(proof['cwd_candidate'], 'ABSENT')
        self.assertEqual(proof['side_by_side_candidate'], 'ABSENT')
        observed = {path for path, *_ in self.fixture.path_observations}
        self.assertNotIn('/opt/containerd/bin/containerd-shim-runc-v2', observed)
        self.assertFalse(any('/TEST_ONLY_HOSTILE' in path for path in observed))
        self.assert_no_mutation()

    def test_hostile_cwd_opt_library_or_extra_candidate_denies_before_start(self):
        for key, entry in (('cwd', 'containerd-shim-runc-v2'), ('opt_lib', 'inject.so'),
                           ('opt_bin', 'OTHER_SHIM'), ('root', 'unexpected'), ('socket_dir', 'socket')):
            with self.subTest(key=key):
                original = list(self.fixture.auxiliary_paths[self.auxiliary[key]]['entries'])
                self.fixture.auxiliary_paths[self.auxiliary[key]]['entries'] = sorted(original + [entry])
                with self.assertRaisesRegex(RuntimeError, 'Unexpected auxiliary contents'):
                    self.instance.shim_preconditions()
                self.assert_no_mutation()
                self.fixture.auxiliary_paths[self.auxiliary[key]]['entries'] = original

    def test_missing_or_mismatched_staged_shim_denies_without_fallback(self):
        original = copy.deepcopy(self.fixture.auxiliary_paths[self.auxiliary['shim']])
        for field, value in (('exists', False), ('sha256', '0' * 64), ('mode', '0700'),
                             ('uid', 1000), ('gid', 1000), ('symlink', True), ('kind', 'directory')):
            with self.subTest(field=field):
                self.fixture.auxiliary_paths[self.auxiliary['shim']] = {**original, field: value}
                with self.assertRaises(RuntimeError):
                    self.instance.shim_preconditions()
                self.assert_no_mutation()
        self.fixture.auxiliary_paths[self.auxiliary['shim']] = original

    def test_side_by_side_shim_denies_even_with_matching_pinned_bytes(self):
        path = str(self.root / 'bin/containerd-shim-runc-v2')
        self.fixture.auxiliary_paths[path] = {**self.fixture.auxiliary_paths[self.auxiliary['shim']],
                                            'realpath': path}
        with self.assertRaisesRegex(RuntimeError, 'Side-by-side shim'):
            self.instance.shim_preconditions()
        self.assert_no_mutation()

    def test_every_auxiliary_directory_requires_exact_root_ownership_and_0700(self):
        for key in ('root', 'cwd', 'opt', 'opt_bin', 'opt_lib', 'socket_dir'):
            original = copy.deepcopy(self.fixture.auxiliary_paths[self.auxiliary[key]])
            for field, value in (('uid', 1000), ('gid', 1000), ('mode', '0755'),
                                 ('symlink', True), ('kind', 'regular'), ('realpath', '/unrelated')):
                with self.subTest(key=key, field=field):
                    self.fixture.auxiliary_paths[self.auxiliary[key]] = {**original, field: value}
                    with self.assertRaisesRegex(RuntimeError, 'Owned path identity differs'):
                        self.instance.shim_preconditions()
                    self.assert_no_mutation()
            self.fixture.auxiliary_paths[self.auxiliary[key]] = original

    def test_runc_and_containerd_remain_pinned_absolute_root_bin_executables(self):
        for name in ('runc', 'containerd'):
            path = str(self.root / 'bin' / name)
            original = self.fixture.inspect_owned_path(path, sha256=True)
            for field, value in (('sha256', '0' * 64), ('mode', '0700'), ('uid', 1000),
                                 ('symlink', True), ('realpath', '/usr/bin/' + name)):
                with self.subTest(name=name, field=field):
                    self.fixture.auxiliary_paths[path] = {**original, field: value}
                    with self.assertRaises(RuntimeError):
                        self.instance.shim_preconditions()
                    self.assert_no_mutation()
            self.fixture.auxiliary_paths.pop(path, None)

    def test_configuration_uses_exact_path_runtime_opt_and_short_socket_dir(self):
        daemon, text = self.configure()
        config = self.instance.validate_configuration(daemon, text)
        self.assertEqual(daemon['default-runtime'], 'vra-pinned-runc')
        self.assertEqual(daemon['runtimes'], {'vra-pinned-runc': {'path': str(self.root / 'bin/runc')}})
        self.assertTrue(Path(daemon['runtimes']['vra-pinned-runc']['path']).is_absolute())
        self.assertEqual(config['imports'], [])
        self.assertEqual(config['plugins']['io.containerd.internal.v1.opt'], {'path': self.auxiliary['opt']})
        self.assertEqual(config['plugins']['io.containerd.shim.v1.manager'],
                         {'env': [], 'socket_dir': self.auxiliary['socket_dir']})
        self.assertEqual(len(os.fsencode(config['plugins']['io.containerd.shim.v1.manager']['socket_dir'])), 39)
        for name in ('io.containerd.internal.v1.opt', 'io.containerd.shim.v1.manager'):
            self.assertIn(name, config['required_plugins'])

    def test_runtime_type_wrapper_args_options_or_default_override_are_rejected(self):
        daemon, text = self.configure()
        variants = []
        for key, value in (('runtimeType', 'io.containerd.runc.v2'), ('runtimeArgs', ['--debug']),
                           ('options', {'BinaryName': str(self.root / 'bin/runc')}), ('path', 'runc')):
            candidate = copy.deepcopy(daemon)
            candidate['runtimes']['vra-pinned-runc'][key] = value
            variants.append(candidate)
        candidate = copy.deepcopy(daemon)
        candidate['default-runtime'] = 'runc'
        variants.append(candidate)
        for index, candidate in enumerate(variants):
            with self.subTest(case=index), self.assertRaisesRegex(RuntimeError, 'Pinned Path runtime differs'):
                self.instance.validate_configuration(candidate, text)

    def test_ambient_opt_imports_env_or_long_socket_configuration_is_rejected(self):
        daemon, text = self.configure()
        variants = (text.replace(json.dumps(self.auxiliary['opt']), '"/opt/containerd"'),
                    text.replace('imports = []', 'imports = ["/TEST_ONLY_HOSTILE/*.toml"]'),
                    text.replace('env = []', 'env = ["PATH=/usr/bin"]'),
                    text.replace(json.dumps(self.auxiliary['socket_dir']), '"/run/containerd/s"'),
                    text.replace(json.dumps(self.auxiliary['socket_dir']), json.dumps('/run/' + 'x' * 60)))
        for index, candidate in enumerate(variants):
            with self.subTest(case=index), self.assertRaisesRegex(RuntimeError, 'Pinned opt/shim configuration differs'):
                self.instance.validate_configuration(daemon, candidate)

    def test_observed_containerd_unit_contract_accepts_only_exact_environment(self):
        self.fixture.boundary(self.fixture.bound_units())
        observed = self.instance.containerd_unit_contract()
        self.assertEqual(observed, self.fixture.environment_properties())
        command = self.commands[-1]
        self.assertEqual(command, ['/usr/bin/systemctl', 'show', self.fixture.unit('containerd'),
                                   '--no-pager', '--property=WorkingDirectory,Environment,UnsetEnvironment'])

    def test_observed_containerd_unit_contract_rejects_ambient_or_malformed_properties(self):
        self.fixture.bound_units()
        expected = self.fixture.environment_properties()
        variants = []
        for key, value in (('WorkingDirectory', str(self.root)), ('Environment', 'PATH=/usr/bin'),
                           ('Environment', expected['Environment'] + ' LD_PRELOAD=/inject.so'),
                           ('Environment', expected['Environment'] + ' PATH=/other'),
                           ('UnsetEnvironment', 'LD_PRELOAD')):
            candidate = dict(expected)
            candidate[key] = value
            variants.append('\n'.join(name + '=' + item for name, item in candidate.items()))
        valid = '\n'.join(name + '=' + item for name, item in expected.items())
        variants += [valid + '\nWorkingDirectory=/duplicate', valid + '\nUnknown=value',
                     '\n'.join(valid.splitlines()[:-1]), 'not-properties']
        for index, output in enumerate(variants):
            with self.subTest(case=index):
                self.instance.run = lambda *args, output=output, **kwargs: output
                with self.assertRaises(RuntimeError):
                    self.instance.containerd_unit_contract()

    def test_privileged_path_observation_uses_fixed_isolated_python_and_unknown_is_not_absence(self):
        del self.instance.inspect_path
        captured = []
        def execute(command, **kwargs):
            captured.append((list(map(str, command)), kwargs))
            return '{"exists": false}'
        self.instance.run = execute
        self.assertEqual(self.instance.inspect_path(self.auxiliary['root'], entries=True,
                                                   sha256=True, recursive=True), {'exists': False})
        self.assertEqual(captured[0][0][:6], ['/usr/bin/python3', '-I', '-S', '-B', '-c', runtime.PATH_OBSERVATION])
        self.assertEqual(captured[0][0][6:], [self.auxiliary['root'], 'entries', 'sha256', 'recursive'])
        self.assertTrue(captured[0][1]['privileged'])
        self.instance.run = mock.Mock(side_effect=PermissionError('TEST_ONLY_UNKNOWN_ROOT'))
        with self.assertRaises(PermissionError):
            self.instance.inspect_path(self.auxiliary['root'])

    def test_residual_shim_endpoint_or_symlink_preserves_every_owned_root(self):
        for kind in ('socket', 'symlink', 'regular'):
            with self.subTest(kind=kind):
                self.fixture.auxiliary_paths[self.auxiliary['socket_dir']]['nodes'] = [
                    {'path': 'TEST_ONLY_RESIDUAL', 'kind': kind}]
                self.commands.clear()
                self.fixture.boundary(self.fixture.bound_units())
                report = self.fixture.teardown_report(fails=True)
                self.assertEqual(report['status'], 'FAIL')
                self.fixture.assert_cleanup_preserved()
                self.assertTrue(self.fixture.auxiliary_paths[self.auxiliary['root']]['exists'])
                self.assertFalse(any(command[0] == '/usr/bin/rm' for command in self.commands))
                self.fixture.assert_only_owned_unit_stops(('dockerd', 'containerd'))

    def test_unknown_protected_socket_scope_never_becomes_empty_proof(self):
        boundary = self.instance.inspect_path
        def observe(path, **kwargs):
            if str(path) == self.auxiliary['socket_dir']:
                raise PermissionError('TEST_ONLY_SOCKET_SCOPE_UNKNOWN')
            return boundary(path, **kwargs)
        self.instance.inspect_path = observe
        self.fixture.boundary(self.fixture.bound_units())
        report = self.fixture.teardown_report(fails=True)
        self.assertEqual(report['status'], 'FAIL')
        self.fixture.assert_cleanup_preserved()
        self.assertFalse(any(command[0] == '/usr/bin/rm' for command in self.commands))

    @staticmethod
    def encoded_options(path, extra=b''):
        data = path.encode()
        size = len(data)
        length = bytearray()
        while size >= 128:
            length.append((size & 127) | 128)
            size >>= 7
        length.append(size)
        return {'type_url': 'containerd.runc.v1.Options',
                'value': base64.b64encode(b'\x32' + bytes(length) + data + extra).decode()}

    def live_proof_fixture(self):
        self.container_id = 'b' * 64
        namespace = 'poc04-' + self.fixture.run_id
        self.bundle = self.root / 'containerd-state/io.containerd.runtime.v2.task' / namespace / self.container_id
        self.shim_socket = Path(self.auxiliary['socket_dir']) / ('c' * 64)
        self.inspection = {'Id': self.container_id, 'HostConfig': {'Runtime': 'vra-pinned-runc'}}
        self.metadata = {'ID': self.container_id, 'Runtime': {'Name': 'io.containerd.runc.v2',
                         'Options': self.encoded_options(str(self.root / 'bin/runc'))},
                         'Spec': {'env': ['TEST_ONLY_METADATA_MUST_NOT_BE_PUBLISHED']}}
        metadata = self.fixture.auxiliary_paths
        metadata[str(self.bundle)] = {'exists': True, 'kind': 'directory', 'realpath': str(self.bundle),
                                     'uid': 0, 'gid': 0, 'mode': '0700', 'symlink': False}
        text = {'options.json': json.dumps({'binary_name': str(self.root / 'bin/runc')}),
                'runtime': str(self.root / 'bin/runc'),
                'bootstrap.json': json.dumps({'protocol': 'ttrpc', 'address': 'unix://' + str(self.shim_socket)})}
        for name, value in text.items():
            path = str(self.bundle / name)
            metadata[path] = {'exists': True, 'kind': 'regular', 'realpath': path,
                              'uid': 0, 'gid': 0, 'mode': '0600', 'symlink': False, 'text': value}
        metadata[str(self.shim_socket)] = {'exists': True, 'kind': 'socket', 'realpath': str(self.shim_socket),
                                          'uid': 0, 'gid': 0, 'mode': '0600', 'symlink': False,
                                          'peer': [99999, 0, 0]}
        self.shim_pid = 23456
        self.fixture.process('containerd', self.shim_pid)
        group = '/system.slice/' + self.fixture.unit('containerd')
        self.fixture.group('containerd', (self.shim_pid,))
        self.shim_identity = {'pid': self.shim_pid, 'exe': self.auxiliary['shim'],
                              'sha256': self.instance.member_hash('containerd-shim-runc-v2'),
                              'start_time': '12345', 'boot_id': '00000000-0000-4000-8000-000000000003'}
        expected_command = [str(self.root / 'bin/ctr'), '--address', str(self.root / 'run/containerd.sock'),
                            '--namespace', namespace, 'containers', 'info', self.container_id]
        def execute(command, **kwargs):
            values = list(map(str, command))
            self.commands.append(values)
            self.assertTrue(kwargs.get('privileged'))
            if values == expected_command:
                return json.dumps(self.metadata)
            if values == ['/usr/bin/readlink', str(self.fixture.proc / str(self.shim_pid) / 'exe')]:
                return self.shim_identity['exe']
            if values == ['/usr/bin/readlink', str(self.fixture.proc / str(self.shim_pid) / 'cwd')]:
                return str(self.bundle)
            if values == ['/usr/bin/sha256sum', str(self.fixture.proc / str(self.shim_pid) / 'exe')]:
                return self.shim_identity['sha256'] + '  ' + values[1]
            self.fail('Unexpected proof command: ' + repr(values))
        self.instance.run = execute
        return group

    def test_typed_runc_decoder_requires_absolute_binary_and_rejects_hostile_wire_values(self):
        absolute = str(self.root / 'bin/runc')
        self.assertEqual(runtime.decode_runc_options(self.encoded_options(absolute)),
                         {'binary_name': absolute, 'shim_cgroup': ''})
        variants = [self.encoded_options('runc'), self.encoded_options(absolute, b'\x32\x01x'),
                    self.encoded_options(absolute, b'\x42\x00'),
                    self.encoded_options(absolute, b'\x1a\x06/other'),
                    {'type_url': 'runtimeoptions.v1.Options', 'value': ''},
                    {'type_url': 'containerd.runc.v1.Options', 'value': 'not-base64'},
                    {'type_url': 'containerd.runc.v1.Options', 'value': base64.b64encode(b'\x32\x7fshort').decode()},
                    {'type_url': 'containerd.runc.v1.Options', 'value': base64.b64encode(b'\x30\x01').decode()},
                    {'type_url': 'containerd.runc.v1.Options', 'value': 'A' * 87385}]
        for index, value in enumerate(variants):
            with self.subTest(case=index), self.assertRaises((RuntimeError, ValueError)):
                runtime.decode_runc_options(value)

    def test_live_default_runtime_requires_exact_name_path_and_no_wrappers(self):
        path = self.root / 'bin/runc'
        info = {'DefaultRuntime': 'vra-pinned-runc', 'Runtimes': {'vra-pinned-runc': {'path': str(path)}}}
        runtime.validate_default_runtime(info, path)
        variants = []
        for key, value in (('path', '/usr/bin/runc'), ('runtimeArgs', ['--debug']),
                           ('runtimeType', 'io.containerd.runc.v2'), ('options', {'BinaryName': str(path)})):
            candidate = copy.deepcopy(info)
            candidate['Runtimes']['vra-pinned-runc'][key] = value
            variants.append(candidate)
        variants += [{**info, 'DefaultRuntime': 'runc'}, {**info, 'Runtimes': {}}]
        for index, candidate in enumerate(variants):
            with self.subTest(case=index), self.assertRaisesRegex(RuntimeError, 'Live Docker default Path runtime differs'):
                runtime.validate_default_runtime(candidate, path)

    def test_live_proof_uses_exact_ctr_id_typed_options_bundle_and_serving_shim(self):
        group = self.live_proof_fixture()
        self.instance.container_runtime_proof(self.container_id, self.inspection)
        observation = self.instance.state['shim_observations'][0]
        self.assertEqual(observation, {'identity': self.shim_identity, 'cgroup': group,
                                     'container_id': self.container_id, 'bundle': str(self.bundle),
                                     'socket': str(self.shim_socket), 'runc_path': str(self.root / 'bin/runc')})
        proof = json.loads((self.instance.evidence / 'shim-runtime-proof.json').read_text())
        self.assertEqual(proof['status'], 'PASS')
        self.assertEqual(proof['typed_options']['binary_name'], str(self.root / 'bin/runc'))
        self.assertNotIn('TEST_ONLY_METADATA_MUST_NOT_BE_PUBLISHED', json.dumps(proof))
        self.assertEqual(observation['identity']['pid'], self.shim_pid)
        self.assertNotEqual(observation['identity']['pid'], 99999, 'Transferred listener PID is diagnostic only')
        self.assertTrue(all('list' not in command and 'ls' not in command for command in self.commands))

    def test_live_proof_rejects_wrong_ctr_runtime_id_or_typed_binary(self):
        for case in ('id', 'runtime', 'type', 'binary'):
            with self.subTest(case=case):
                self.live_proof_fixture()
                if case == 'id':
                    self.metadata['ID'] = 'd' * 64
                elif case == 'runtime':
                    self.metadata['Runtime']['Name'] = '/usr/bin/unrelated-shim'
                elif case == 'type':
                    self.metadata['Runtime']['Options']['type_url'] = 'runtimeoptions.v1.Options'
                else:
                    self.metadata['Runtime']['Options'] = self.encoded_options('/usr/bin/runc')
                with self.assertRaises(RuntimeError):
                    self.instance.container_runtime_proof(self.container_id, self.inspection)
                self.assertEqual(self.instance.state['shim_observations'], [])

    def test_live_proof_rejects_unowned_id_or_wrong_docker_runtime_before_ctr_query(self):
        self.live_proof_fixture()
        for inspection in ({'Id': 'd' * 64, 'HostConfig': {'Runtime': 'vra-pinned-runc'}},
                           {'Id': self.container_id, 'HostConfig': {'Runtime': 'runc'}}):
            with self.subTest(inspection=inspection):
                self.commands.clear()
                with self.assertRaisesRegex(RuntimeError, 'Proof container default runtime differs'):
                    self.instance.container_runtime_proof(self.container_id, inspection)
                self.assertEqual(self.commands, [])

    def test_live_proof_rejects_wrong_consumed_binary_bundle_symlink_and_socket_scope(self):
        for case in ('consumed', 'runtime', 'bundle', 'metadata_symlink', 'socket', 'protocol'):
            with self.subTest(case=case):
                self.live_proof_fixture()
                values = self.fixture.auxiliary_paths
                if case == 'consumed':
                    values[str(self.bundle / 'options.json')]['text'] = '{"binary_name":"/usr/bin/runc"}'
                elif case == 'runtime':
                    values[str(self.bundle / 'runtime')]['text'] = '/usr/bin/runc'
                elif case == 'bundle':
                    values[str(self.bundle)]['realpath'] = '/unrelated/bundle'
                elif case == 'metadata_symlink':
                    values[str(self.bundle / 'options.json')]['symlink'] = True
                elif case == 'socket':
                    values[str(self.bundle / 'bootstrap.json')]['text'] = json.dumps(
                        {'protocol': 'ttrpc', 'address': 'unix:///run/containerd/s/' + 'c' * 64})
                else:
                    values[str(self.bundle / 'bootstrap.json')]['text'] = json.dumps(
                        {'protocol': 'grpc', 'address': 'unix://' + str(self.shim_socket)})
                with self.assertRaises(RuntimeError):
                    self.instance.container_runtime_proof(self.container_id, self.inspection)
                self.assertEqual(self.instance.state['shim_observations'], [])

    def test_live_proof_rejects_changed_serving_shim_digest_or_unbound_cwd(self):
        self.live_proof_fixture()
        self.shim_identity['sha256'] = '0' * 64
        with self.assertRaisesRegex(RuntimeError, 'Observed shim executable differs'):
            self.instance.container_runtime_proof(self.container_id, self.inspection)
        self.assertEqual(self.instance.state['shim_observations'], [])
        self.live_proof_fixture()
        original = self.instance.run
        def execute(command, **kwargs):
            if list(map(str, command)) == ['/usr/bin/readlink', str(self.fixture.proc / str(self.shim_pid) / 'cwd')]:
                return '/unrelated/cwd'
            return original(command, **kwargs)
        self.instance.run = execute
        with self.assertRaisesRegex(RuntimeError, 'Unique owned serving shim'):
            self.instance.container_runtime_proof(self.container_id, self.inspection)

    def test_recorded_live_shim_incarnation_blocks_cleanup_without_pid_signaling(self):
        group = self.live_proof_fixture()
        self.instance.state['shim_observations'] = [{'identity': self.shim_identity, 'cgroup': group}]
        with self.assertRaisesRegex(RuntimeError, 'Recorded owned shim process remains'):
            self.instance.shim_process_absence()
        self.assertFalse(any(Path(command[0]).name in ('kill', 'pkill', 'killall') for command in self.commands))
        self.assertTrue((self.root / 'docker-data').is_dir())
        self.assertTrue(self.fixture.auxiliary_paths[self.auxiliary['root']]['exists'])

    def test_owned_cgroup_enumeration_reads_descendants_and_rejects_bad_scope(self):
        group = '/system.slice/' + self.fixture.unit('containerd')
        self.fixture.group('containerd', (23456,), descendant=23457)
        self.assertEqual(self.instance.owned_cgroup_pids(group), [23456, 23457])
        with self.assertRaisesRegex(RuntimeError, 'cgroup authority differs'):
            self.instance.owned_cgroup_pids('/system.slice/other.service')
        (self.fixture.cgroups / group.lstrip('/') / 'children/cgroup.procs').write_text('malformed\n')
        with self.assertRaisesRegex(RuntimeError, 'Malformed shim PID authority'):
            self.instance.owned_cgroup_pids(group)

    def test_hostile_usr_bin_usr_local_bin_old_opt_and_ambient_path_are_not_selected(self):
        for path in ('/usr/bin', '/usr/local/bin', '/opt/containerd/bin', '/TEST_ONLY_AMBIENT/bin'):
            with self.subTest(candidate=path), mock.patch.dict(os.environ, {'PATH': path,
                                                                         'LD_LIBRARY_PATH': path + '/lib'}):
                self.fixture.path_observations.clear()
                proof = self.instance.shim_preconditions()
                self.assertEqual(proof['initial_path'], self.auxiliary['opt_bin'])
                self.assertFalse(any(observed == path + '/containerd-shim-runc-v2'
                                     for observed, *_ in self.fixture.path_observations))

    def test_sealed_startup_config_is_observed_privileged_without_direct_read(self):
        self.configure()
        for filename in ('daemon.json', 'containerd.toml'):
            (self.root / filename).chmod(0o400)
        self.fixture.boundary(self.fixture.bound_units())
        metadata = {str(self.root / name): self.fixture.inspect_owned_path(self.root / name, text=True)
                    for name in ('daemon.json', 'containerd.toml')}
        observed = self.instance.inspect_path
        self.instance.inspect_path = lambda path, **kwargs: copy.deepcopy(metadata[str(path)]) if str(path) in metadata else observed(path, **kwargs)
        original = Path.read_text
        def forbidden(path, *args, **kwargs):
            if str(path) in metadata:
                raise AssertionError('Unprivileged sealed configuration read denied')
            return original(path, *args, **kwargs)
        with mock.patch.object(Path, 'read_text', forbidden):
            with self.assertRaisesRegex(RuntimeError, 'Owned unit already exists'):
                self.instance.start('containerd', [self.root / 'bin/containerd'], bundle_only=True)
        self.assert_no_mutation()

    def test_managed_opt_live_proof_queries_exact_type_and_id_selector(self):
        expected = [str(self.root / 'bin/ctr'), '--address', str(self.root / 'run/containerd.sock'),
                    'plugins', 'ls', '--detailed', 'type==io.containerd.internal.v1,id==opt']
        def execute(command, **kwargs):
            self.commands.append(list(map(str, command)))
            self.assertEqual(self.commands[-1], expected)
            self.assertTrue(kwargs.get('privileged'))
            return 'Type: io.containerd.internal.v1\nID: opt\nExports:\n    path ' + self.auxiliary['opt']
        self.instance.run = execute
        self.instance.managed_opt_proof()
        proof = json.loads((self.instance.evidence / 'managed-opt-effective.json').read_text())
        self.assertEqual(proof['status'], 'PASS')
        self.assertEqual(proof['path'], self.auxiliary['opt'])
        self.assertEqual(proof['plugin'], {'Type': 'io.containerd.internal.v1', 'ID': 'opt'})

    def test_managed_opt_live_proof_rejects_wrong_exports_plugin_or_error(self):
        valid = 'Type: io.containerd.internal.v1\nID: opt\nExports:\n    path ' + self.auxiliary['opt']
        variants = (valid.replace(self.auxiliary['opt'], '/opt/containerd'),
                    valid.replace('ID: opt', 'ID: other'),
                    valid.replace('Type: io.containerd.internal.v1', 'Type: io.containerd.other.v1'),
                    valid + '\nError: TEST_ONLY_PLUGIN_FAILURE',
                    valid + '\n    path ' + self.auxiliary['opt'],
                    valid + '\nID: opt', '', valid.replace('    path ' + self.auxiliary['opt'], ''))
        for index, value in enumerate(variants):
            with self.subTest(case=index):
                self.instance.run = lambda *args, value=value, **kwargs: value
                with self.assertRaises(RuntimeError):
                    self.instance.managed_opt_proof()


class HostedRuntimeAttestationTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory(prefix='vra-hosted-runtime-attestation-')
        self.addCleanup(self.temporary.cleanup)
        self.base = Path(self.temporary.name).resolve()
        self.root = self.base / 'vra-poc04-docker-test'
        (self.root / 'bin').mkdir(parents=True)
        (self.root / 'evidence').mkdir()
        plugins = self.root / 'docker-config/cli-plugins'
        plugins.mkdir(parents=True)
        self.cli = self.root / 'bin/docker'
        self.cli.write_bytes(synthetic_elf())
        self.cli.chmod(0o755)
        self.buildx = plugins / 'docker-buildx'
        self.buildx.write_bytes(synthetic_elf() + b'TEST_ONLY_BUILDX')
        self.buildx.chmod(0o755)
        manifest = runtime.load_manifest()
        next(row for row in manifest['docker']['archive']['members']
             if row['name'] == 'docker/docker')['sha256'] = runtime.digest(self.cli)
        manifest['buildx']['sha256'] = runtime.digest(self.buildx)
        self.manifest = self.base / 'TEST_ONLY_runtime_manifest.json'
        runtime.write(self.manifest, manifest)
        self.attestation = self.root / 'evidence/runtime-attestation.json'
        self.env = {'RUNNER_TEMP': str(self.base), 'PATH': str(self.root / 'bin'),
                    'DOCKER_HOST': 'unix://' + str(self.root / 'run/docker.sock'),
                    'VRA_POC04_DISPOSABLE_TEST_DOCKER_ENDPOINT': 'unix://' + str(self.root / 'run/docker.sock'),
                    'VRA_POC04_EXPECTED_DAEMON_ID': 'TEST_ONLY_ATTESTED_DAEMON',
                    'TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE': str(self.root / 'run/docker.sock'),
                    'TESTCONTAINERS_HOST_OVERRIDE': '127.0.0.1', 'TESTCONTAINERS_RYUK_DISABLED': 'false',
                    'DOCKER_CONFIG': str(self.root / 'docker-config'),
                    'BUILDX_CONFIG': str(self.root / 'buildx-state'),
                    'VRA_POC04_HOSTED_RUNTIME_ATTESTATION': str(self.attestation)}
        contract = {name: value for name, value in self.env.items() if name not in ('RUNNER_TEMP', 'PATH')}
        self.value = {'schema_version': 1, 'status': 'PASS', 'manifest_sha256': runtime.digest(self.manifest),
                      'daemon_id': 'TEST_ONLY_ATTESTED_DAEMON', 'environment': contract,
                      'cli_path': str(self.cli), 'cli_sha256': runtime.digest(self.cli)}
        runtime.write(self.attestation, self.value)

    def verify(self):
        return runtime.verify_hosted_runtime_attestation(self.env, self.manifest)

    def test_exact_external_attestation_and_executable_bytes_are_accepted(self):
        self.assertEqual(self.verify(), self.value)

    def test_attestation_at_unapproved_external_path_is_rejected(self):
        unapproved = self.base / 'other-attestation.json'
        runtime.write(unapproved, self.value)
        self.env['VRA_POC04_HOSTED_RUNTIME_ATTESTATION'] = str(unapproved)
        with self.assertRaisesRegex(RuntimeError, 'attestation must be external'):
            self.verify()

    def test_manifest_identity_mismatch_is_rejected(self):
        self.value['manifest_sha256'] = '0' * 64
        runtime.write(self.attestation, self.value)
        with self.assertRaisesRegex(RuntimeError, 'manifest/attestation differs'):
            self.verify()

    def test_changed_docker_executable_bytes_are_rejected(self):
        self.cli.write_bytes(synthetic_elf() + b'TEST_ONLY_CHANGED_CLI')
        with self.assertRaisesRegex(RuntimeError, 'Executable identity mismatch'):
            self.verify()

    def test_changed_buildx_executable_bytes_are_rejected(self):
        self.buildx.write_bytes(synthetic_elf() + b'TEST_ONLY_CHANGED_BUILDX')
        with self.assertRaisesRegex(RuntimeError, 'Executable identity mismatch'):
            self.verify()

    def test_path_resolution_to_another_cli_is_rejected(self):
        other = self.base / 'other-bin'
        other.mkdir()
        arbitrary = other / 'docker'
        arbitrary.write_bytes(self.cli.read_bytes())
        arbitrary.chmod(0o755)
        self.env['PATH'] = str(other) + os.pathsep + self.env['PATH']
        with self.assertRaisesRegex(RuntimeError, 'Executable identity mismatch'):
            self.verify()

    def test_incoherent_runtime_endpoint_is_rejected(self):
        self.env['DOCKER_HOST'] = 'unix:///var/run/docker.sock'
        with self.assertRaisesRegex(RuntimeError, 'runtime environment differs'):
            self.verify()

    def test_ambient_api_masking_is_rejected(self):
        self.env['DOCKER_API_VERSION'] = '1.48'
        with self.assertRaisesRegex(RuntimeError, 'Ambient Docker override prohibited'):
            self.verify()


if __name__ == '__main__':
    unittest.main(verbosity=2)
