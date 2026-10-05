"""Hermetic TEST-only regressions for frozen security input and disposable builds."""
import copy
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest
from unittest.mock import patch
import zipfile

ROOT = Path(__file__).resolve().parents[3]
spec = importlib.util.spec_from_file_location('phase1_ci_trust_domains',
                                              ROOT / 'validation/poc-04/scripts/phase1-ci.py')
ci = importlib.util.module_from_spec(spec)
spec.loader.exec_module(ci)


class PostbuildTrustDomainTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory(prefix='vra-poc04-domain-test-')
        self.addCleanup(self.temporary.cleanup)
        self.home = Path(self.temporary.name).resolve()
        self.source = self.home / 'source'
        self.source.mkdir()
        self.file(self.source, '.gitignore', b'.gradle/\n**/build/\n')
        self.file(self.source, 'backend/fixture.py', b'print("TEST_ONLY_FIXTURE")\n')
        self.file(self.source, 'validation/poc-04/test-support/digest-metadata-contexts.json',
                  b'{"schema_version":1,"contexts":[]}\n')
        platform = {'Darwin': 'darwin', 'Linux': 'linux'}[ci.platform.system()]
        machine = {'aarch64': 'arm64', 'x86_64': 'amd64', 'arm64': 'arm64'}[ci.platform.machine()]
        asset = 'gitleaks_8.30.1_' + platform + '_' + machine + '.tar.gz'
        tool = {'tools': [{'id': 'Gitleaks', 'version': '8.30.1', 'immutable_identifiers': {
            'artifacts': [{'platform': platform + '/' + machine, 'identifier': asset,
                'url': 'https://github.com/gitleaks/gitleaks/releases/download/v8.30.1/' + asset,
                'expected_sha256': 'a' * 64}]}}]}
        self.file(self.source, 'validation/poc-04/tooling/tool-manifest.json',
                  json.dumps(tool).encode())
        # Real Git objects are confined to this disposable fixture; never canonical Git.
        self.git(self.source, 'init', '-q')
        self.git(self.source, '-c', 'user.name=TEST_ONLY', '-c', 'user.email=test@example.invalid',
                 'add', '.')
        self.git(self.source, '-c', 'user.name=TEST_ONLY', '-c', 'user.email=test@example.invalid',
                 'commit', '-qm', 'TEST_ONLY_FIXTURE')
        names = self.git(self.source, 'ls-files', '-z').decode().split('\0')[:-1]
        self.manifest = ci.freeze_scan_candidate(self.source, names, (), (), (), (),
                                                'validation/poc-04/evidence/phase1/test-run')
        self.manifest.update({'head': self.git(self.source, 'rev-parse', 'HEAD').decode().strip(),
            'head_tree': self.git(self.source, 'rev-parse', 'HEAD^{tree}').decode().strip(),
            'index_entries_sha256': hashlib.sha256(
                self.git(self.source, 'ls-files', '--stage', '-z').decode().strip().encode()).hexdigest(),
            'expected_generated': []})
        ci.seal_scan_manifest(self.manifest)

    @staticmethod
    def file(root, relative, raw=b'TEST_ONLY_GENERATED\n', mode=0o644):
        path = root / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(raw)
        path.chmod(mode)
        return path

    @staticmethod
    def git(root, *args):
        return subprocess.check_output(['git', '--no-optional-locks', '-C', str(root), *args],
                                       stderr=subprocess.DEVNULL)

    def domains(self):
        self.assertTrue(callable(getattr(ci, 'create_execution_domains', None)),
                        'independent execution-domain materialization is missing')
        return ci.create_execution_domains(self.source, self.manifest, self.home / 'domains')

    def verify(self, domains, manifest=None, root=None):
        self.assertTrue(callable(getattr(ci, 'verify_frozen_scan_domain', None)))
        return ci.verify_frozen_scan_domain(root or domains['scan_root'],
                                           manifest or self.manifest, domains['scan_identity'])

    def artifact_selection(self, domains):
        for project in ('runtime', 'migration'):
            path = domains['build_root'] / 'backend' / project / 'build/libs' / (
                project + '-0.1.0-SNAPSHOT.jar')
            path.parent.mkdir(parents=True, exist_ok=True)
            with zipfile.ZipFile(path, 'w') as archive:
                archive.writestr('BOOT-INF/classes/application.yaml', 'TEST_ONLY_APPLICATION\n')
        producer = {'name': 'full-check-build', 'cwd': str(domains['build_root']),
                    'exit_status': 0, 'output': 'full-check-build.txt',
                    'output_sha256': hashlib.sha256(b'TEST_ONLY_BUILD_SUCCESS\n').hexdigest()}
        return ci.select_build_artifacts(domains['build_root'], self.manifest,
                                         'TEST_ONLY_RUN', producer=producer)

    def test_prebuild_candidate_paths_hashes_sizes_modes_are_equal(self):
        domains = self.domains()
        self.assertNotEqual(domains['scan_root'], domains['build_root'])
        self.assertFalse(domains['build_root'].is_relative_to(self.source))
        self.assertEqual(domains['immutable_snapshot_binding'], self.manifest['content_sha256'])
        self.assertEqual(domains['build_workspace_prebuild_binding'], self.manifest['content_sha256'])
        for row in self.manifest['files']:
            for root in (domains['scan_root'], domains['build_root']):
                self.assertEqual(ci.scan_file_record(root, row['path']),
                                 {key: row[key] for key in ('path', 'sha256', 'size', 'mode')})
        self.verify(domains)

    def test_postbuild_lock_does_not_enter_original_scan_or_trigger_live_guard(self):
        # Before implementation, exercise the OLD real secret_scan guard as well.
        if hasattr(ci, 'create_execution_domains'):
            domains = self.domains()
        else:
            domains = {'scan_root': self.home / 'scan', 'build_root': self.home / 'build',
                       'scan_identity': {}}
            for root in (domains['scan_root'], domains['build_root']):
                ci.materialize_scan_candidate(self.source, self.manifest, root)
                shutil.copytree(self.source / '.git', root / '.git')
        run = object.__new__(ci.Run)
        run.source_root = self.source
        run.scan_root = domains['scan_root']
        run.build_root = domains['build_root']
        run.scan_identity = domains['scan_identity']
        run.scan_manifest = self.manifest
        run.support = self.home / 'support'
        run.support.mkdir()
        run.out = self.home / 'evidence'
        run.out.mkdir()
        run.result = {}
        run.env = {key: value for key, value in os.environ.items()
                   if key not in ('GITLEAKS_CONFIG', 'GITLEAKS_CONFIG_TOML')}
        run.scanner_binary = self.file(run.support, 'gitleaks', b'TEST_ONLY_SCANNER_STUB\n', 0o700)
        run.scanner_binary_sha256 = ci.sha(run.scanner_binary)
        payloads = []
        actual_run = subprocess.run

        def external_scan(argv, *args, **kwargs):
            if str(argv[0]) == str(run.scanner_binary):
                self.assertEqual(Path(kwargs['cwd']), domains['scan_root'])
                payloads.append(kwargs['stdin'].read())
                return subprocess.CompletedProcess(argv, 0, b'[]', b'')
            return actual_run(argv, *args, **kwargs)

        with patch.object(ci, 'ROOT', domains['build_root']), \
                patch.object(ci, 'verified_public_identifier_provenance', return_value={}), \
                patch.object(ci.subprocess, 'run', side_effect=external_scan):
            original = run.secret_scan('primary')
            lock = 'backend/.gradle/9.7.1/checksums/checksums.lock'
            self.file(domains['build_root'], lock)
            self.assertNotIn(lock, [row['path'] for row in self.manifest['files']])
            # The old implementation fails here on the live build workspace lock.
            try:
                after = run.secret_scan('postbuild-original')
            except RuntimeError as failure:
                self.fail('Original snapshot scan inspected generated build state: ' + str(failure))
        self.assertEqual(original, after)
        self.assertEqual(payloads[0], payloads[1])
        self.assertFalse((domains['scan_root'] / lock).exists())

    def test_build_workspace_cannot_be_original_snapshot(self):
        domains = self.domains()
        with self.assertRaises(RuntimeError):
            self.verify(domains, root=domains['build_root'])

    def test_mutated_build_walk_cannot_supply_original_scan(self):
        domains = self.domains()
        self.file(domains['build_root'], 'backend/build/unselected.txt')
        with self.assertRaises(RuntimeError):
            self.verify(domains, root=domains['build_root'])

    def reject_added_original_input(self, relative):
        domains = self.domains()
        self.file(domains['scan_root'], relative)
        with self.assertRaises(RuntimeError):
            self.verify(domains)

    def test_lock_added_to_original_input_is_rejected(self):
        self.reject_added_original_input('backend/.gradle/9.7.1/checksums/checksums.lock')

    def test_generated_shell_added_to_original_input_is_rejected(self):
        self.reject_added_original_input('backend/build/arbitrary.sh')

    def test_generated_json_added_to_original_input_is_rejected(self):
        self.reject_added_original_input('backend/build/arbitrary.json')

    def test_altered_manifest_cannot_authorize_added_lock(self):
        domains = self.domains()
        path = 'backend/.gradle/9.7.1/checksums/checksums.lock'
        self.file(domains['scan_root'], path)
        changed = copy.deepcopy(self.manifest)
        changed['files'].append({**ci.scan_file_record(domains['scan_root'], path),
                                 'classification': 'TRACKED_CANDIDATE'})
        ci.seal_scan_manifest(changed)
        with self.assertRaises(RuntimeError):
            self.verify(domains, manifest=changed)

    def test_snapshot_and_build_root_alias_is_rejected(self):
        domains = self.domains()
        domains['scan_root'] = domains['build_root']
        with self.assertRaises(RuntimeError):
            self.verify(domains)

    def test_snapshot_source_changes_after_build_are_rejected(self):
        domains = self.domains()
        self.file(domains['scan_root'], 'backend/fixture.py', b'TEST_ONLY_CHANGED\n')
        with self.assertRaises(RuntimeError):
            self.verify(domains)

    def test_snapshot_source_mode_change_is_rejected(self):
        domains = self.domains()
        (domains['scan_root'] / 'backend/fixture.py').chmod(0o755)
        with self.assertRaises(RuntimeError):
            self.verify(domains)

    def test_build_preexecution_candidate_mismatch_is_rejected(self):
        domains = self.domains()
        self.file(domains['build_root'], 'backend/fixture.py', b'TEST_ONLY_CHANGED\n')
        with self.assertRaises(RuntimeError):
            ci.compare_execution_domain_candidates(domains['scan_root'], domains['build_root'], self.manifest)

    def test_canonical_source_root_cannot_be_build_destination(self):
        self.assertTrue(callable(getattr(ci, 'create_execution_domains', None)))
        with self.assertRaises(RuntimeError):
            ci.create_execution_domains(self.source, self.manifest, self.source)

    def test_source_subdirectory_cannot_be_build_destination(self):
        self.assertTrue(callable(getattr(ci, 'create_execution_domains', None)))
        with self.assertRaises(RuntimeError):
            ci.create_execution_domains(self.source, self.manifest, self.source / 'build')

    def test_git_metadata_escape_is_rejected(self):
        self.assertTrue(callable(getattr(ci, 'create_execution_domains', None)))
        self.git(self.source, 'config', 'core.worktree', str(self.home / 'another-root'))
        with self.assertRaises(RuntimeError):
            ci.create_execution_domains(self.source, self.manifest, self.home / 'domains')

    def test_expanded_selection_contains_only_two_contracted_bootjars(self):
        domains = self.domains()
        self.file(domains['build_root'], 'backend/.gradle/9.7.1/checksums/checksums.lock')
        self.file(domains['build_root'], 'backend/runtime/build/arbitrary.sh')
        self.file(domains['build_root'], 'backend/runtime/build/arbitrary.json')
        selected = self.artifact_selection(domains)
        self.assertEqual({row['path'] for row in selected['files']}, {
            'backend/runtime/build/libs/runtime-0.1.0-SNAPSHOT.jar',
            'backend/migration/build/libs/migration-0.1.0-SNAPSHOT.jar'})
        self.assertTrue(all(row['classification'] == 'GENERATED_BOOTJAR' for row in selected['files']))
        ci.verify_selected_build_artifacts(domains['build_root'], self.manifest, selected)

    def test_expanded_selection_rejects_unselected_build_outputs(self):
        domains = self.domains()
        selected = self.artifact_selection(domains)
        path = 'backend/runtime/build/arbitrary.json'
        self.file(domains['build_root'], path)
        selected['files'].append({**ci.scan_file_record(domains['build_root'], path),
                                  'classification': 'GENERATED_BOOTJAR'})
        selected['selection_sha256'] = ci.canonical_sha({
            key: value for key, value in selected.items() if key != 'selection_sha256'})
        with self.assertRaises(RuntimeError):
            ci.verify_selected_build_artifacts(domains['build_root'], self.manifest, selected)

    def test_selected_artifact_hash_change_is_rejected(self):
        domains = self.domains()
        selected = self.artifact_selection(domains)
        self.file(domains['build_root'], selected['files'][0]['path'], b'TEST_ONLY_CHANGED\n')
        with self.assertRaises(RuntimeError):
            ci.verify_selected_build_artifacts(domains['build_root'], self.manifest, selected)

    def test_selected_artifact_mode_change_is_rejected(self):
        domains = self.domains()
        selected = self.artifact_selection(domains)
        (domains['build_root'] / selected['files'][0]['path']).chmod(0o755)
        with self.assertRaises(RuntimeError):
            ci.verify_selected_build_artifacts(domains['build_root'], self.manifest, selected)

    def test_selected_artifact_wrong_path_is_rejected(self):
        domains = self.domains()
        selected = self.artifact_selection(domains)
        selected['files'][0]['path'] = 'backend/runtime/build/libs/unapproved.jar'
        selected['selection_sha256'] = ci.canonical_sha({
            key: value for key, value in selected.items() if key != 'selection_sha256'})
        with self.assertRaises(RuntimeError):
            ci.verify_selected_build_artifacts(domains['build_root'], self.manifest, selected)

    def test_generated_artifacts_require_successful_producer_gate(self):
        domains = self.domains()
        self.artifact_selection(domains)
        with self.assertRaises(RuntimeError):
            ci.select_build_artifacts(domains['build_root'], self.manifest, 'TEST_ONLY_RUN',
                                     producer={'name': 'full-check-build', 'exit_status': 1})

    def test_generated_artifacts_require_bound_producer_gate(self):
        domains = self.domains()
        self.artifact_selection(domains)
        with self.assertRaises(RuntimeError):
            ci.select_build_artifacts(domains['build_root'], self.manifest, 'TEST_ONLY_RUN')

    def test_original_snapshot_root_symlink_alias_is_rejected(self):
        domains = self.domains()
        alias = self.home / 'scan-alias'
        alias.symlink_to(domains['scan_root'], target_is_directory=True)
        with self.assertRaises(RuntimeError):
            self.verify(domains, root=alias)

    def test_build_workspace_root_symlink_alias_is_rejected(self):
        domains = self.domains()
        alias = self.home / 'build-alias'
        alias.symlink_to(domains['build_root'], target_is_directory=True)
        with self.assertRaises(RuntimeError):
            ci.compare_execution_domain_candidates(domains['scan_root'], alias, self.manifest)

    def test_original_git_metadata_change_is_rejected(self):
        domains = self.domains()
        config = domains['scan_root'] / '.git/config'
        config.write_bytes(config.read_bytes() + b'\n[test-only]\n value = changed\n')
        with self.assertRaises(RuntimeError):
            self.verify(domains)

    def test_effective_global_git_worktree_escape_is_rejected(self):
        other = self.home / 'other-worktree'
        other.mkdir()
        config = self.home / 'test-only-git-config'
        config.write_text('[core]\n worktree = ' + str(other) + '\n')
        with patch.dict(os.environ, {'GIT_CONFIG_GLOBAL': str(config)}):
            with self.assertRaises(RuntimeError):
                ci.create_execution_domains(self.source, self.manifest, self.home / 'domains')

    def test_child_git_root_overrides_are_sanitized_without_changing_parent(self):
        names = ('GIT_DIR', 'GIT_WORK_TREE', 'GIT_INDEX_FILE', 'GIT_COMMON_DIR',
                 'GIT_OBJECT_DIRECTORY', 'GIT_ALTERNATE_OBJECT_DIRECTORIES')
        overrides = {name: str(self.home / 'TEST_ONLY_OVERRIDE') for name in names}
        with patch.dict(os.environ, overrides):
            child = ci.isolated_git_environment()
            self.assertTrue(all(name not in child for name in names))
            self.assertTrue(all(os.environ[name] == value for name, value in overrides.items()))

    def test_selected_bootjar_symlink_escape_is_rejected(self):
        domains = self.domains()
        selected = self.artifact_selection(domains)
        path = domains['build_root'] / selected['files'][0]['path']
        elsewhere = self.home / 'elsewhere.jar'
        path.replace(elsewhere)
        path.symlink_to(elsewhere)
        with self.assertRaises(RuntimeError):
            ci.verify_selected_build_artifacts(domains['build_root'], self.manifest, selected)

    def delivery_run(self, domains, hosted):
        run = object.__new__(ci.Run)
        run.source_root = self.source
        run.build_root = domains['build_root']
        run.scan_root = domains['scan_root']
        run.scan_manifest = self.manifest
        run.selected_artifacts = self.artifact_selection(domains)
        run.out = self.home / 'delivery-evidence'
        run.out.mkdir()
        run.hosted = hosted
        run.env = ({'GITHUB_ACTIONS': 'true', 'RUNNER_ENVIRONMENT': 'github-hosted'}
                   if hosted else {})
        return run

    def test_local_execution_cannot_deliver_artifacts_into_source(self):
        domains = self.domains()
        run = self.delivery_run(domains, False)
        with self.assertRaises(RuntimeError):
            run.deliver_hosted_artifacts()
        self.assertFalse((self.source / 'backend/runtime/build').exists())

    def test_hosted_delivery_preserves_only_two_explicit_artifacts(self):
        domains = self.domains()
        run = self.delivery_run(domains, True)
        self.file(domains['build_root'], 'backend/build/UNSELECTED.json')
        run.deliver_hosted_artifacts()
        for row in run.selected_artifacts['files']:
            self.assertEqual(ci.scan_file_record(self.source, row['path']),
                             {key: row[key] for key in ('path', 'sha256', 'size', 'mode')})
        self.assertFalse((self.source / 'backend/build/UNSELECTED.json').exists())

    def test_hosted_delivery_collision_is_rejected_without_overwrite(self):
        domains = self.domains()
        run = self.delivery_run(domains, True)
        path = run.selected_artifacts['files'][0]['path']
        collision = self.file(self.source, path, b'TEST_ONLY_EXISTING\n')
        with self.assertRaises(RuntimeError):
            run.deliver_hosted_artifacts()
        self.assertEqual(collision.read_bytes(), b'TEST_ONLY_EXISTING\n')

    def test_hosted_delivery_symlink_is_rejected(self):
        domains = self.domains()
        run = self.delivery_run(domains, True)
        path = self.source / run.selected_artifacts['files'][0]['path']
        path.parent.mkdir(parents=True)
        path.symlink_to(self.home / 'never-written')
        with self.assertRaises(RuntimeError):
            run.deliver_hosted_artifacts()
        self.assertFalse((self.home / 'never-written').exists())


if __name__ == '__main__':
    unittest.main(verbosity=2)
