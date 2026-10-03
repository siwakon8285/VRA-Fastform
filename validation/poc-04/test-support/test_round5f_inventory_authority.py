"""TEST-only regressions for exact generated and preserved inventory authority."""
import hashlib
import importlib.util
import json
import os
from contextlib import nullcontext
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch


ROOT = Path(__file__).resolve().parents[3]
BOOTSTRAP = ROOT / 'validation/poc-04/scripts/phase1-ci.py'
spec = importlib.util.spec_from_file_location('phase1_ci_round5f_authority', BOOTSTRAP)
ci = importlib.util.module_from_spec(spec)
spec.loader.exec_module(ci)


class InventoryAuthorityTest(unittest.TestCase):
    CURRENT = 'validation/poc-04/evidence/phase1/current-round5f-test'
    HISTORY = ('validation/poc-04/evidence/phase1/'
               'hosted-ci-round5c-48801624-49df-4c1f-b618-3362a28e7050')
    SETTINGS = 'validation/poc-00/settings.gradle.kts'
    CONFIGURATION = 'validation/poc-00/java-candidate/build.gradle.kts'
    SHARED_DECLARATION = 'sourceSets.main { resources.srcDir(rootProject.file("shared")) }\n'
    GENERATED_ROOTS = (
        'backend/.gradle', 'backend/build', 'backend/migration/build',
        'backend/runtime/build', 'validation/poc-00/.gradle', 'validation/poc-00/build',
        'validation/poc-00/java-candidate/build', 'validation/poc-00/kotlin-candidate/build')

    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory(prefix='vra-poc04-round5f-test-')
        self.addCleanup(self.temporary.cleanup)
        self.root = (Path(self.temporary.name) / 'repository').resolve()
        self.root.mkdir(mode=0o700)

    def file(self, relative, content=b'TEST_ONLY_NON_SECRET\n', mode=0o644):
        path = self.root / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(content)
        path.chmod(mode)
        return path

    def record(self, relative):
        path = self.root / relative
        raw = path.read_bytes()
        return {'path': relative, 'sha256': hashlib.sha256(raw).hexdigest(),
                'size': len(raw), 'mode': format(path.stat().st_mode & 0o777, '04o')}

    def repository_manifest(self, tracked=(), untracked=(), ignored=(), selection=None,
                            extra_environment=None, authority=None):
        """Fake read-only Git inventory while checking real fixture bytes and modes."""
        commands = {
            ('ls-files', '-z'): '\0'.join(tracked),
            ('ls-files', '--others', '--exclude-standard', '-z'): '\0'.join(untracked),
            ('ls-files', '--others', '--ignored', '--exclude-standard', '-z'): '\0'.join(ignored),
            ('ls-files', '--stage', '-z'): '\0'.join(
                '100644 ' + '3' * 40 + ' 0\t' + relative for relative in tracked),
            ('rev-parse', 'HEAD'): '1' * 40,
            ('rev-parse', 'HEAD^{tree}'): '2' * 40,
            ('diff', '--name-only', '-z'): '',
        }

        def git_inventory(*args):
            self.assertIn(args, commands, 'Unexpected Git operation')
            return commands[args]

        environment = dict(extra_environment or {})
        if selection is not None:
            path = Path(self.temporary.name) / 'restricted-selection.json'
            raw = json.dumps(selection, sort_keys=True).encode()
            path.write_bytes(raw)
            path.chmod(0o600)
            environment.update({'VRA_POC04_SCAN_SELECTION': str(path),
                                'VRA_POC04_SCAN_SELECTION_SHA256': hashlib.sha256(raw).hexdigest()})
        authority_patch = (patch.object(ci, 'HISTORICAL_AUTHORITY', authority)
                           if authority is not None else nullcontext())
        model_patch = (patch.object(ci, 'query_gradle_resource_model', side_effect=self.fixture_resource_model)
                       if hasattr(self, 'model_roots') else nullcontext())
        with authority_patch, model_patch, patch.object(ci, 'ROOT', self.root), patch.object(ci, 'git', git_inventory), \
                patch.dict(os.environ, environment, clear=True):
            return ci.repository_scan_candidate(self.CURRENT)

    @staticmethod
    def selection(preserved=(), selected=(), generated=()):
        return {'preserved_evidence': list(preserved), 'selected_evidence': list(selected),
                'expected_generated': list(generated)}

    def shared_fixture(self, additional_configuration=''):
        self.file(self.SETTINGS, b'rootProject.name = "vra-poc00"\ninclude("java-candidate")\n')
        self.file(self.CONFIGURATION, (self.SHARED_DECLARATION + additional_configuration).encode())
        origin = 'validation/poc-00/shared/db/migration/nested/TEST_ONLY.sql'
        destination = 'validation/poc-00/java-candidate/build/resources/main/db/migration/nested/TEST_ONLY.sql'
        self.file(origin, b'SELECT 1; -- TEST_ONLY_SHARED\n')
        self.file(destination, b'SELECT 1; -- TEST_ONLY_SHARED\n')
        self.model_roots = ['validation/poc-00/java-candidate/src/main/resources',
                            'validation/poc-00/shared']
        self.model_excludes = []
        self.model_error = None
        return destination, (self.SETTINGS, self.CONFIGURATION, origin)

    def fixture_resource_model(self, build_root, repository_root=None, evidence_directory=None):
        """Only the external evaluated-model boundary is substituted."""
        self.assertEqual(Path(build_root), self.root / 'validation/poc-00')
        if self.model_error is not None:
            raise RuntimeError(self.model_error)
        roots = []
        source_files = {}
        for relative in self.model_roots:
            root = self.root / relative
            roots.append({'path': str(root), 'canonical_path': str(root.resolve())})
            if root.is_dir():
                for source in root.rglob('*'):
                    if source.is_file() and not self.model_excludes:
                        source_files[str(source)] = {'path': str(source),
                                                     'canonical_path': str(source.resolve())}
        project = self.root / 'validation/poc-00/java-candidate'
        output = project / 'build/resources/main'
        raw = {'schema_version': 1, 'gradle_version': '9.7.1',
               'build_root': str(self.root / 'validation/poc-00'),
               'projects': [{'project_path': ':',
                             'project_directory': str(self.root / 'validation/poc-00'),
                             'build_file': None, 'source_sets': []},
                            {'project_path': ':java-candidate', 'project_directory': str(project),
                             'build_file': str(self.root / self.CONFIGURATION),
                             'source_sets': [{'name': 'main', 'resource_roots': roots,
                                              'resource_files': list(source_files.values()),
                                              'process_resource_files': list(source_files.values()),
                                              'output_directory': str(output),
                                              'process_output_directory': str(output),
                                              'includes': [], 'excludes': self.model_excludes}]}]}
        return ci.validate_gradle_resource_model(raw, self.root, self.root / 'validation/poc-00')

    def prior_inventory_fixture(self):
        historical = self.HISTORY + '/TEST_ONLY_BOUND_RESULT.json'
        self.file(historical, b'{"result":"TEST_ONLY_HISTORICAL_FAIL"}\n')
        prior_run = ('validation/poc-04/evidence/phase1/'
                     'hosted-ci-round5e-4b837db3-57bc-4b2d-bf6e-7cca3d864f78')
        frozen_path = prior_run + '/FROZEN_SCAN_MANIFEST.json'
        frozen = ci.seal_scan_manifest({
            'schema': 'poc04-scan-candidate-v1', 'current_evidence': prior_run,
            'files': [], 'preserved_evidence': [self.record(historical)], 'classifications': {}})
        self.file(frozen_path, json.dumps(frozen, sort_keys=True, indent=2).encode())
        binding_path = prior_run + '/FINAL_BINDING.json'
        evidence_files = [self.record(frozen_path)]
        final = {'schema': 'POC04_ROUND5E_FINAL_EVIDENCE_BINDING_V1',
                 'binding_self_reference_excluded': binding_path,
                 'evidence_files': evidence_files, 'evidence_file_count': len(evidence_files),
                 'evidence_binding_content_sha256': ci.canonical_sha(evidence_files)}
        self.file(binding_path, json.dumps(final, sort_keys=True, indent=2).encode())
        authority = {'binding': self.record(binding_path), 'linked_manifest_path': frozen_path}
        return authority, historical, frozen_path, binding_path

    def load_prior_inventory(self, authority):
        return ci.historical_preservation_inventory(self.root, authority=authority)

    def test_build_root_name_cannot_exclude_arbitrary_source_or_configuration(self):
        names = ('TEST_ONLY.py', 'TEST_ONLY.java', 'TEST_ONLY.kt', 'TEST_ONLY.sql',
                 'application.yml', 'application.yaml', '.env', 'application.properties',
                 'settings.xml', 'package.json', 'config.toml', 'config.ini', 'config.conf',
                 'bootstrap.sh', 'bootstrap.bash', 'bootstrap.zsh', 'bootstrap.ps1',
                 'TEST_ONLY.js', 'TEST_ONLY.ts', 'index.html', 'style.css',
                 'build.gradle', 'build.gradle.kts', 'dependency.lock')
        with patch.object(ci, 'ROOT', self.root), patch.object(ci, 'git', return_value=''):
            for root in self.GENERATED_ROOTS:
                for name in names:
                    relative = root + '/' + name
                    self.file(relative)
                    with self.subTest(path=relative):
                        self.assertFalse(ci.expected_generated_path(relative))

    def test_arbitrary_nested_config_is_not_generated_by_directory_or_suffix(self):
        for relative in ('backend/.gradle/caches/TEST_ONLY/application.properties',
                         'backend/runtime/build/unreviewed/nested/config.xml',
                         'backend/migration/build/unreviewed/nested/test-report.json',
                         'validation/poc-00/java-candidate/build/unreviewed/package.json',
                         'validation/poc-00/kotlin-candidate/build/unreviewed/build.gradle.kts'):
            self.file(relative)
            with self.subTest(path=relative):
                with self.assertRaises(RuntimeError):
                    self.repository_manifest(ignored=(relative,))

    def test_restricted_selection_cannot_grant_generated_status_to_configuration(self):
        relative = 'backend/runtime/build/unreviewed/application.properties'
        self.file(relative)
        with self.assertRaises(RuntimeError):
            self.repository_manifest(ignored=(relative,),
                                     selection=self.selection(generated=(relative,)))

    def test_generated_resource_configuration_without_tracked_origin_is_rejected(self):
        for filename in ('application.properties', 'settings.xml', 'config.toml', 'package.json'):
            relative = 'backend/runtime/build/resources/main/' + filename
            self.file(relative)
            with self.subTest(path=relative):
                with self.assertRaises(RuntimeError):
                    self.repository_manifest(ignored=(relative,))

    def test_generated_resource_configuration_with_changed_bytes_is_rejected(self):
        for filename in ('application.properties', 'settings.xml', 'config.toml', 'package.json'):
            source = 'backend/runtime/src/main/resources/' + filename
            generated = 'backend/runtime/build/resources/main/' + filename
            self.file(source, b'TEST_ONLY_ORIGINAL_CONFIG\n')
            self.file(generated, b'TEST_ONLY_CHANGED_CONFIG\n')
            with self.subTest(path=generated):
                with self.assertRaises(RuntimeError):
                    self.repository_manifest(tracked=(source,), ignored=(generated,))

    def test_approved_history_directory_name_does_not_authorize_new_current_bytes(self):
        relative = self.HISTORY + '/TEST_ONLY_NEW_FILE.json'
        self.file(relative, b'{"result":"TEST_ONLY_NEW_BYTES"}\n')
        with self.assertRaises(RuntimeError):
            self.repository_manifest(untracked=(relative,))

    def test_prior_inventory_resolves_exact_files_from_bound_documents(self):
        authority, historical, frozen_path, binding_path = self.prior_inventory_fixture()
        inventory = self.load_prior_inventory(authority)
        expected = [self.record(path) for path in (historical, frozen_path, binding_path)]
        self.assertEqual(sorted(inventory['files'], key=lambda row: row['path']),
                         sorted(expected, key=lambda row: row['path']))
        self.assertTrue(inventory['origins'])

    def test_exact_prior_inventory_preserves_known_history(self):
        authority, historical, _, _ = self.prior_inventory_fixture()
        manifest = self.repository_manifest(untracked=(historical,), authority=authority)
        self.assertEqual(manifest['files'], [])
        self.assertEqual(manifest['preserved_evidence'], [self.record(historical)])

    def test_new_file_under_bound_historical_directory_is_rejected(self):
        authority, _, _, _ = self.prior_inventory_fixture()
        unknown = self.HISTORY + '/TEST_ONLY_ADDED_AFTER_PRIOR_RUN.json'
        self.file(unknown)
        with self.assertRaises(RuntimeError):
            self.repository_manifest(untracked=(unknown,), authority=authority)

    def test_bound_historical_file_with_changed_bytes_is_rejected(self):
        authority, historical, _, _ = self.prior_inventory_fixture()
        self.file(historical, b'{"result":"TEST_ONLY_CURRENT_REPLACEMENT"}\n')
        with self.assertRaises(RuntimeError):
            self.repository_manifest(untracked=(historical,), authority=authority)

    def test_bound_historical_file_with_changed_mode_is_rejected(self):
        authority, historical, _, _ = self.prior_inventory_fixture()
        (self.root / historical).chmod(0o600)
        with self.assertRaises(RuntimeError):
            self.repository_manifest(untracked=(historical,), authority=authority)

    def test_current_selection_snapshot_cannot_replace_prior_inventory_binding(self):
        authority, historical, _, _ = self.prior_inventory_fixture()
        self.file(historical, b'{"result":"TEST_ONLY_CURRENT_REPLACEMENT"}\n')
        with self.assertRaises(RuntimeError):
            self.repository_manifest(untracked=(historical,), authority=authority,
                                     selection=self.selection(preserved=(self.record(historical),)))

    def test_current_selection_snapshot_cannot_authorize_unknown_history(self):
        authority, _, _, _ = self.prior_inventory_fixture()
        unknown = self.HISTORY + '/TEST_ONLY_ADDED_AFTER_PRIOR_RUN.json'
        self.file(unknown)
        with self.assertRaises(RuntimeError):
            self.repository_manifest(untracked=(unknown,), authority=authority,
                                     selection=self.selection(preserved=(self.record(unknown),)))

    def test_present_bootstrap_with_changed_bytes_has_no_directory_fallback(self):
        authority, historical, _, binding_path = self.prior_inventory_fixture()
        self.file(binding_path, b'{"schema":"TEST_ONLY_ALTERED_AUTHORITY"}\n')
        with self.assertRaises(RuntimeError):
            self.repository_manifest(untracked=(historical,), authority=authority)

    def test_linked_inventory_with_changed_bytes_has_no_directory_fallback(self):
        authority, historical, frozen_path, _ = self.prior_inventory_fixture()
        self.file(frozen_path, b'{"preserved_evidence":[]}\n')
        with self.assertRaises(RuntimeError):
            self.repository_manifest(untracked=(historical,), authority=authority)

    def test_bound_final_inventory_metadata_is_validated_before_use(self):
        authority, _, _, binding_path = self.prior_inventory_fixture()
        original = json.loads((self.root / binding_path).read_text())
        mutations = (('schema', 'TEST_ONLY_UNKNOWN_SCHEMA'),
                     ('evidence_file_count', 2),
                     ('evidence_binding_content_sha256', '0' * 64),
                     ('binding_self_reference_excluded', self.HISTORY + '/WRONG_BINDING.json'))
        for field, value in mutations:
            document = dict(original)
            document[field] = value
            self.file(binding_path, json.dumps(document, sort_keys=True, indent=2).encode())
            current_authority = dict(authority, binding=self.record(binding_path))
            with self.subTest(field=field):
                with self.assertRaises(RuntimeError):
                    self.load_prior_inventory(current_authority)

    def test_absent_prior_binding_cannot_authorize_explicit_current_snapshot(self):
        relative = self.HISTORY + '/TEST_ONLY_FRESH_RESULT.json'
        self.file(relative)
        with self.assertRaises(RuntimeError):
            self.repository_manifest(untracked=(relative,),
                                     selection=self.selection(preserved=(self.record(relative),)))

    def test_additional_nested_resource_roots_make_shared_origin_unresolved(self):
        configurations = (
            'sourceSets { named("main") { resources { srcDir(rootProject.file("other")) } } }\n',
            'sourceSets { named("main") { resources { srcDirs(rootProject.file("other")) } } }\n',
            'sourceSets { named("main") { resources { setSrcDirs(listOf(rootProject.file("other"))) } } }\n',
            'sourceSets.main { resources { srcDir(rootProject.file("other")) } }\n',
            'sourceSets.main { resources { srcDirs(rootProject.file("other")) } }\n',
            'sourceSets.main { resources { setSrcDirs(listOf(rootProject.file("other"))) } }\n',
        )
        for configuration in configurations:
            destination, tracked = self.shared_fixture(configuration)
            second = 'validation/poc-00/other/db/migration/nested/TEST_ONLY.sql'
            self.file(second, (self.root / destination).read_bytes())
            tracked += (second,)
            self.model_roots.append('validation/poc-00/other')
            with self.subTest(configuration=configuration):
                with patch.object(ci, 'ROOT', self.root), \
                        patch.object(ci, 'query_gradle_resource_model', side_effect=self.fixture_resource_model):
                    with self.assertRaises(RuntimeError):
                        ci.resolve_shared_resource(destination, tracked=tracked)

    def test_nested_resource_transformations_cannot_keep_exact_shared_proof(self):
        configurations = (
            ('sourceSets { named("main") { resources { exclude("**/*.sql") } } }\n', 'excluded'),
            ('sourceSets { named("main") { resources { include("other/**") } } }\n', 'excluded'),
            ('sourceSets { named("main") { resources { filter { line -> line } } } }\n', 'unsupported'),
            ('sourceSets { named("main") { resources { source(rootProject.file("other")) } } }\n', 'ambiguous'),
        )
        for configuration, effective_model in configurations:
            destination, tracked = self.shared_fixture(configuration)
            if effective_model == 'excluded':
                self.model_excludes = ['**/*.sql']
            elif effective_model == 'unsupported':
                self.model_error = 'Unsupported evaluated resource transformation'
            else:
                second = 'validation/poc-00/other/db/migration/nested/TEST_ONLY.sql'
                self.file(second, (self.root / destination).read_bytes())
                tracked += (second,)
                self.model_roots.append('validation/poc-00/other')
            with self.subTest(configuration=configuration):
                with self.assertRaises(RuntimeError):
                    self.repository_manifest(tracked=tracked, ignored=(destination,))


if __name__ == '__main__':
    unittest.main(verbosity=2)
