"""TEST-only proof that scanner input is explicit, frozen and non-recursive."""
import copy
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
spec = importlib.util.spec_from_file_location('phase1_ci_scan_boundary', BOOTSTRAP)
ci = importlib.util.module_from_spec(spec)
spec.loader.exec_module(ci)


class ScanCandidateBoundaryTest(unittest.TestCase):
    CURRENT = 'validation/poc-04/evidence/phase1/current-run'

    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory(prefix='vra-poc04-scan-test-')
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

    def freeze(self, tracked=(), untracked=(), candidate_paths=(),
               preserved_files=(), selected_evidence=(), current_evidence=None):
        self.assertTrue(callable(getattr(ci, 'freeze_scan_candidate', None)),
                        'explicit scanner-candidate boundary has not been implemented')
        return ci.freeze_scan_candidate(
            self.root, tracked, untracked, candidate_paths,
            preserved_files, selected_evidence,
            self.CURRENT if current_evidence is None else current_evidence)

    def verify(self, manifest):
        self.assertTrue(callable(getattr(ci, 'verify_scan_candidate', None)),
                        'frozen scanner-candidate verification has not been implemented')
        return ci.verify_scan_candidate(self.root, manifest)

    def materialize(self, manifest):
        self.assertTrue(callable(getattr(ci, 'materialize_scan_candidate', None)),
                        'restricted scanner snapshot has not been implemented')
        destination = Path(self.temporary.name) / 'scan-snapshot'
        ci.materialize_scan_candidate(self.root, manifest, destination)
        return destination

    def repository_manifest(self, tracked=(), untracked=(), ignored=(), modified=(),
                            selection=None, selection_hash=None, selection_mode=0o600):
        """Fake only read-only Git inventory; validate actual local bytes and metadata."""
        commands = {
            ('ls-files', '-z'): '\0'.join(tracked),
            ('ls-files', '--others', '--exclude-standard', '-z'): '\0'.join(untracked),
            ('ls-files', '--others', '--ignored', '--exclude-standard', '-z'): '\0'.join(ignored),
            ('ls-files', '--stage', '-z'): '\0'.join(
                '100644 ' + '3' * 40 + ' 0\t' + relative for relative in tracked),
            ('rev-parse', 'HEAD'): '1' * 40,
            ('rev-parse', 'HEAD^{tree}'): '2' * 40,
            ('diff', '--name-only', '-z'): '\0'.join(modified),
        }

        def git_inventory(*args):
            self.assertIn(args, commands, 'adapter requested an unexpected Git operation')
            return commands[args]

        environment = {}
        if selection is not None:
            path = Path(self.temporary.name) / 'restricted-selection.json'
            raw = json.dumps(selection, sort_keys=True).encode()
            path.write_bytes(raw)
            path.chmod(selection_mode)
            environment['VRA_POC04_SCAN_SELECTION'] = str(path)
            environment['VRA_POC04_SCAN_SELECTION_SHA256'] = (
                hashlib.sha256(raw).hexdigest() if selection_hash is None else selection_hash)
        model_query = getattr(self, 'fixture_resource_model', None)
        model_patch = (patch.object(ci, 'query_gradle_resource_model', side_effect=model_query)
                       if model_query is not None else nullcontext())
        with model_patch, patch.object(ci, 'ROOT', self.root), patch.object(ci, 'git', git_inventory), \
                patch.dict(os.environ, environment, clear=True):
            return ci.repository_scan_candidate(self.CURRENT)

    @staticmethod
    def selection(preserved=(), selected=(), generated=()):
        return {'preserved_evidence': list(preserved), 'selected_evidence': list(selected),
                'expected_generated': list(generated)}

    def assertBound(self, manifest, relative, classification='TRACKED_CANDIDATE'):
        row = next(item for item in manifest['files'] if item['path'] == relative)
        expected = self.record(relative)
        for key, value in expected.items():
            self.assertEqual(row[key], value, key)
        self.assertEqual(row['classification'], classification)

    def test_normal_tracked_source_is_bound(self):
        relative = 'backend/runtime/src/main/java/Example.java'
        self.file(relative, b'class Example {}\n')
        manifest = self.freeze(tracked=(relative,))
        self.assertBound(manifest, relative)

    def test_modified_tracked_source_uses_current_working_bytes(self):
        relative = 'backend/runtime/src/main/java/Example.java'
        self.file(relative, b'class Original {}\n')
        self.file(relative, b'class CurrentWorkingCandidate {}\n')
        manifest = self.freeze(tracked=(relative,))
        self.assertBound(manifest, relative)
        copied = self.materialize(manifest)
        self.assertEqual((copied / relative).read_bytes(), b'class CurrentWorkingCandidate {}\n')

    def test_exact_approved_untracked_support_is_bound(self):
        relative = 'validation/poc-04/test-support/new_boundary_test.py'
        self.file(relative, b'"TEST_ONLY_SUPPORT"\n', mode=0o600)
        manifest = self.freeze(untracked=(relative,), candidate_paths=(relative,))
        self.assertBound(manifest, relative, 'CANDIDATE_SOURCE_SUPPORT')

    def test_tracked_historical_evidence_is_scanned(self):
        relative = 'validation/poc-04/evidence/phase1/committed-run/result.json'
        self.file(relative, b'{"test_only_result":"FAIL"}\n')
        manifest = self.freeze(tracked=(relative,))
        self.assertBound(manifest, relative)

    def test_exact_selected_future_evidence_is_scanned(self):
        relative = 'validation/poc-04/evidence/phase1/selected-run/result.json'
        self.file(relative, b'{"test_only_result":"PASS"}\n')
        manifest = self.freeze(untracked=(relative,), selected_evidence=(self.record(relative),))
        self.assertBound(manifest, relative, 'SELECTED_EVIDENCE')

    def test_preserved_evidence_is_bound_separately_and_not_scanned(self):
        source = 'backend/runtime/src/main/java/Example.java'
        history = 'validation/poc-04/evidence/phase1/approved-history/result.json'
        self.file(source)
        self.file(history, b'{"test_only_result":"historical FAIL"}\n')
        record = self.record(history)
        manifest = self.freeze(tracked=(source,), untracked=(history,), preserved_files=(record,))
        self.assertEqual([row['path'] for row in manifest['files']], [source])
        self.assertEqual(manifest['preserved_evidence'], [record])
        copied = self.materialize(manifest)
        self.assertFalse((copied / history).exists())

    def test_manifest_has_independent_canonical_content_binding(self):
        first, second = 'backend/a.py', 'backend/b.py'
        self.file(first, b'a\n')
        self.file(second, b'b\n')
        manifest = self.freeze(tracked=(second, first))
        self.assertEqual([row['path'] for row in manifest['files']], [first, second])
        canonical = json.dumps(manifest['files'], sort_keys=True, separators=(',', ':')).encode()
        self.assertEqual(manifest['content_sha256'], hashlib.sha256(canonical).hexdigest())

    def test_manifest_binds_preserved_inventory_and_classifications(self):
        source, history = 'backend/source.py', 'validation/poc-04/evidence/phase1/history/result.json'
        self.file(source)
        self.file(history)
        manifest = self.freeze(tracked=(source,), untracked=(history,),
                               preserved_files=(self.record(history),))
        payload = {key: value for key, value in manifest.items() if key != 'manifest_sha256'}
        canonical = json.dumps(payload, sort_keys=True, separators=(',', ':')).encode()
        self.assertEqual(manifest['manifest_sha256'], hashlib.sha256(canonical).hexdigest())

    def test_snapshot_preserves_relative_paths_bytes_and_modes(self):
        relative = 'validation/poc-04/scripts/test-only-example.py'
        self.file(relative, b'print("TEST_ONLY")\n', mode=0o755)
        manifest = self.freeze(tracked=(relative,))
        destination = self.materialize(manifest)
        self.assertEqual(destination.stat().st_mode & 0o777, 0o700)
        copied = destination / relative
        self.assertEqual(copied.read_bytes(), b'print("TEST_ONLY")\n')
        self.assertEqual(copied.stat().st_mode & 0o777, 0o755)

    def test_snapshot_never_consumes_arbitrary_neighbor_files(self):
        relative = 'backend/source.py'
        self.file(relative, b'"TEST_ONLY_SOURCE"\n')
        manifest = self.freeze(tracked=(relative,))
        self.file('neighbor-not-in-git.txt', b'TEST_ONLY_NEIGHBOR\n')
        destination = self.materialize(manifest)
        self.assertEqual(sorted(str(path.relative_to(destination))
                                for path in destination.rglob('*') if path.is_file()), [relative])

    def test_unaccounted_untracked_python_source_is_rejected(self):
        relative = 'validation/poc-04/scripts/unexpected.py'
        self.file(relative)
        with self.assertRaises(RuntimeError):
            self.freeze(untracked=(relative,))

    def test_unaccounted_untracked_java_source_is_rejected(self):
        relative = 'backend/runtime/src/main/java/Unexpected.java'
        self.file(relative)
        with self.assertRaises(RuntimeError):
            self.freeze(untracked=(relative,))

    def test_unaccounted_untracked_workflow_is_rejected(self):
        relative = '.github/workflows/unexpected.yml'
        self.file(relative)
        with self.assertRaises(RuntimeError):
            self.freeze(untracked=(relative,))

    def test_unknown_evidence_looking_directory_is_rejected(self):
        relative = 'validation/poc-04/evidence/phase1/unknown-run/result.json'
        self.file(relative)
        with self.assertRaises(RuntimeError):
            self.freeze(untracked=(relative,))

    def test_arbitrary_evidence_root_file_is_rejected(self):
        relative = 'validation/poc-04/evidence/arbitrary.json'
        self.file(relative)
        with self.assertRaises(RuntimeError):
            self.freeze(untracked=(relative,))

    def test_untracked_candidate_must_actually_be_listed_in_repository_inventory(self):
        relative = 'validation/poc-04/test-support/hidden.py'
        self.file(relative)
        with self.assertRaises(RuntimeError):
            self.freeze(candidate_paths=(relative,))

    def test_symlink_escaping_repository_is_rejected(self):
        external = Path(self.temporary.name) / 'outside.txt'
        external.write_bytes(b'TEST_ONLY_OUTSIDE\n')
        relative = 'backend/escaping.py'
        path = self.root / relative
        path.parent.mkdir(parents=True)
        path.symlink_to(external)
        with self.assertRaises(RuntimeError):
            self.freeze(tracked=(relative,))

    def test_symlink_parent_escaping_repository_is_rejected(self):
        external = Path(self.temporary.name) / 'outside'
        external.mkdir()
        (external / 'source.py').write_bytes(b'TEST_ONLY_OUTSIDE\n')
        (self.root / 'backend').symlink_to(external, target_is_directory=True)
        with self.assertRaises(RuntimeError):
            self.freeze(tracked=('backend/source.py',))

    def test_symlink_to_an_in_repository_file_is_still_rejected(self):
        target = self.file('backend/target.py')
        relative = 'backend/alias.py'
        (self.root / relative).symlink_to(target)
        with self.assertRaises(RuntimeError):
            self.freeze(tracked=(relative,))

    def test_path_traversal_is_rejected(self):
        (Path(self.temporary.name) / 'outside.py').write_bytes(b'TEST_ONLY_OUTSIDE\n')
        with self.assertRaises(RuntimeError):
            self.freeze(tracked=('../outside.py',))

    def test_file_changed_after_freeze_is_rejected(self):
        relative = 'backend/source.py'
        self.file(relative, b'ORIGINAL_TEST_ONLY\n')
        manifest = self.freeze(tracked=(relative,))
        self.file(relative, b'CHANGED_TEST_ONLY\n')
        with self.assertRaises(RuntimeError):
            self.materialize(manifest)

    def test_mode_changed_after_freeze_is_rejected(self):
        relative = 'backend/source.py'
        path = self.file(relative, mode=0o644)
        manifest = self.freeze(tracked=(relative,))
        path.chmod(0o600)
        with self.assertRaises(RuntimeError):
            self.verify(manifest)

    def test_preserved_evidence_changed_after_freeze_is_rejected(self):
        relative = 'validation/poc-04/evidence/phase1/approved-history/result.json'
        self.file(relative, b'ORIGINAL_TEST_ONLY\n')
        manifest = self.freeze(untracked=(relative,), preserved_files=(self.record(relative),))
        self.file(relative, b'CHANGED_TEST_ONLY\n')
        with self.assertRaises(RuntimeError):
            self.verify(manifest)

    def test_manifest_content_hash_mismatch_is_rejected(self):
        relative = 'backend/source.py'
        self.file(relative)
        manifest = self.freeze(tracked=(relative,))
        manifest['content_sha256'] = '0' * 64
        with self.assertRaises(RuntimeError):
            self.materialize(manifest)

    def test_manifest_file_binding_tampering_is_rejected(self):
        relative = 'backend/source.py'
        self.file(relative)
        manifest = self.freeze(tracked=(relative,))
        manifest['files'][0]['sha256'] = '0' * 64
        with self.assertRaises(RuntimeError):
            self.verify(manifest)

    def test_preserved_authorization_manifest_tampering_is_rejected(self):
        relative = 'validation/poc-04/evidence/phase1/approved-history/result.json'
        self.file(relative)
        manifest = self.freeze(untracked=(relative,), preserved_files=(self.record(relative),))
        changed = copy.deepcopy(manifest)
        changed['preserved_evidence'] = []
        with self.assertRaises(RuntimeError):
            self.verify(changed)

    def test_manifest_classification_tampering_is_rejected(self):
        relative = 'backend/source.py'
        self.file(relative)
        manifest = self.freeze(tracked=(relative,))
        manifest['files'][0]['classification'] = 'SELECTED_EVIDENCE'
        with self.assertRaises(RuntimeError):
            self.verify(manifest)

    def test_duplicate_candidate_authorization_is_rejected(self):
        relative = 'validation/poc-04/test-support/example.py'
        self.file(relative)
        with self.assertRaises(RuntimeError):
            self.freeze(untracked=(relative,), candidate_paths=(relative, relative))

    def test_duplicate_tracked_inventory_is_rejected(self):
        relative = 'backend/source.py'
        self.file(relative)
        with self.assertRaises(RuntimeError):
            self.freeze(tracked=(relative, relative))

    def test_candidate_and_preserved_classification_conflict_is_rejected(self):
        relative = 'validation/poc-04/test-support/example.py'
        self.file(relative)
        with self.assertRaises(RuntimeError):
            self.freeze(untracked=(relative,), candidate_paths=(relative,),
                        preserved_files=(self.record(relative),))

    def test_selected_and_preserved_classification_conflict_is_rejected(self):
        relative = 'validation/poc-04/evidence/phase1/selected-run/result.json'
        self.file(relative)
        record = self.record(relative)
        with self.assertRaises(RuntimeError):
            self.freeze(untracked=(relative,), selected_evidence=(record,), preserved_files=(record,))

    def test_tracked_and_selected_classification_conflict_is_rejected(self):
        relative = 'validation/poc-04/evidence/phase1/committed-run/result.json'
        self.file(relative)
        with self.assertRaises(RuntimeError):
            self.freeze(tracked=(relative,), selected_evidence=(self.record(relative),))

    def test_current_run_evidence_cannot_enter_tracked_scan_input(self):
        relative = self.CURRENT + '/scanner-result.json'
        self.file(relative)
        with self.assertRaises(RuntimeError):
            self.freeze(tracked=(relative,))

    def test_current_run_evidence_cannot_enter_untracked_candidate_input(self):
        relative = self.CURRENT + '/scanner-result.json'
        self.file(relative)
        with self.assertRaises(RuntimeError):
            self.freeze(untracked=(relative,), candidate_paths=(relative,))

    def test_current_run_evidence_cannot_enter_selected_evidence_input(self):
        relative = self.CURRENT + '/scanner-result.json'
        self.file(relative)
        with self.assertRaises(RuntimeError):
            self.freeze(untracked=(relative,), selected_evidence=(self.record(relative),))

    def test_wrong_selected_evidence_binding_is_rejected(self):
        relative = 'validation/poc-04/evidence/phase1/selected-run/result.json'
        self.file(relative)
        record = self.record(relative)
        record['size'] += 1
        with self.assertRaises(RuntimeError):
            self.freeze(untracked=(relative,), selected_evidence=(record,))

    def test_wrong_preserved_evidence_binding_is_rejected(self):
        relative = 'validation/poc-04/evidence/phase1/approved-history/result.json'
        self.file(relative)
        record = self.record(relative)
        record['sha256'] = '0' * 64
        with self.assertRaises(RuntimeError):
            self.freeze(untracked=(relative,), preserved_files=(record,))

    def test_repository_adapter_clean_inventory_needs_no_optional_selection(self):
        relative = 'backend/runtime/src/main/java/Example.java'
        self.file(relative, b'class CleanCandidate {}\n')
        manifest = self.repository_manifest(tracked=(relative,))
        self.assertBound(manifest, relative)
        self.assertIsNone(manifest['selection_sha256'])
        self.assertEqual(manifest['head'], '1' * 40)
        self.assertEqual(manifest['head_tree'], '2' * 40)

    def test_repository_adapter_binds_modified_working_bytes(self):
        relative = '.github/workflows/backend-ci.yml'
        self.file(relative, b'name: TEST_ONLY_CURRENT_CANDIDATE\n')
        manifest = self.repository_manifest(tracked=(relative,), modified=(relative,))
        self.assertBound(manifest, relative)
        self.assertEqual(manifest['modified_tracked_paths'], [relative])
        self.assertEqual(manifest['classifications']['modified_tracked_candidate'], 1)

    def test_repository_adapter_admits_only_exact_candidate_support_path(self):
        relative = 'validation/poc-04/test-support/test_scan_candidate_boundary.py'
        self.file(relative)
        manifest = self.repository_manifest(untracked=(relative,))
        self.assertBound(manifest, relative, 'CANDIDATE_SOURCE_SUPPORT')

    def test_repository_adapter_ignored_env_outside_generated_roots_is_rejected(self):
        relative = 'backend/.env'
        self.file(relative, b'TEST_ONLY_SYNTHETIC_ENV\n')
        with self.assertRaises(RuntimeError):
            self.repository_manifest(ignored=(relative,))

    def test_repository_adapter_ignored_python_outside_generated_roots_is_rejected(self):
        relative = 'validation/poc-04/scripts/ignored-unapproved.py'
        self.file(relative)
        with self.assertRaises(RuntimeError):
            self.repository_manifest(ignored=(relative,))

    def test_repository_adapter_arbitrary_local_file_is_not_ephemeral(self):
        relative = '.local/unapproved/candidate.log'
        self.file(relative)
        with self.assertRaises(RuntimeError):
            self.repository_manifest(ignored=(relative,))

    def test_repository_adapter_explicit_nonapproved_source_is_not_ephemeral(self):
        relative = '.local/poc-01/unapproved.py'
        self.file(relative)
        with self.assertRaises(RuntimeError):
            self.repository_manifest(ignored=(relative,), selection=self.selection(generated=(relative,)))

    def test_repository_adapter_unknown_evidence_directory_without_binding_is_rejected(self):
        relative = 'validation/poc-04/evidence/phase1/unknown-history/result.json'
        self.file(relative)
        with self.assertRaises(RuntimeError):
            self.repository_manifest(untracked=(relative,))

    def test_repository_adapter_exact_historical_directory_needs_prior_inventory_binding(self):
        relative = ('validation/poc-04/evidence/phase1/'
                    'hosted-ci-round5c-48801624-49df-4c1f-b618-3362a28e7050/result.json')
        self.file(relative, b'{"result":"TEST_ONLY_HISTORICAL_FAIL"}\n')
        with self.assertRaises(RuntimeError):
            self.repository_manifest(untracked=(relative,))

    def test_repository_adapter_similar_historical_directory_name_is_not_authorized(self):
        relative = ('validation/poc-04/evidence/phase1/'
                    'hosted-ci-round5c-48801624-49df-4c1f-b618-3362a28e7050-unapproved/result.json')
        self.file(relative)
        with self.assertRaises(RuntimeError):
            self.repository_manifest(untracked=(relative,))

    def test_repository_adapter_preserved_selection_needs_authoritative_prior_binding(self):
        relative = 'validation/poc-04/evidence/phase1/explicit-history/result.json'
        self.file(relative, b'{"result":"TEST_ONLY_HISTORICAL_FAIL"}\n')
        record = self.record(relative)
        with self.assertRaises(RuntimeError):
            self.repository_manifest(untracked=(relative,),
                                     selection=self.selection(preserved=(record,)))

    def test_repository_adapter_exact_selected_evidence_enters_scan(self):
        relative = 'validation/poc-04/evidence/phase1/selected-history/result.json'
        self.file(relative)
        manifest = self.repository_manifest(untracked=(relative,),
                                            selection=self.selection(selected=(self.record(relative),)))
        self.assertBound(manifest, relative, 'SELECTED_EVIDENCE')

    def test_repository_adapter_selection_with_wrong_sha_is_rejected(self):
        relative = 'validation/poc-04/evidence/phase1/explicit-history/result.json'
        self.file(relative)
        with self.assertRaises(RuntimeError):
            self.repository_manifest(untracked=(relative,), selection_hash='0' * 64,
                                     selection=self.selection(preserved=(self.record(relative),)))

    def test_repository_adapter_selection_with_unrestricted_permissions_is_rejected(self):
        with self.assertRaises(RuntimeError):
            self.repository_manifest(selection=self.selection(), selection_mode=0o644)

    def test_repository_adapter_unrecognized_selection_schema_is_rejected(self):
        selection = self.selection()
        selection['unreviewed_exemption'] = []
        with self.assertRaises(RuntimeError):
            self.repository_manifest(selection=selection)

    def test_repository_adapter_incomplete_selection_binding_is_rejected(self):
        with patch.object(ci, 'ROOT', self.root), patch.object(ci, 'git', return_value=''), \
                patch.dict(os.environ, {'VRA_POC04_SCAN_SELECTION_SHA256': '0' * 64}, clear=True):
            with self.assertRaises(RuntimeError):
                ci.repository_scan_candidate(self.CURRENT)

    def test_repository_adapter_duplicate_preservation_selection_is_rejected(self):
        relative = 'validation/poc-04/evidence/phase1/explicit-history/result.json'
        self.file(relative)
        record = self.record(relative)
        with self.assertRaises(RuntimeError):
            self.repository_manifest(untracked=(relative,),
                                     selection=self.selection(preserved=(record, record)))

    def test_repository_adapter_conflicting_preserved_and_selected_is_rejected(self):
        relative = 'validation/poc-04/evidence/phase1/explicit-history/result.json'
        self.file(relative)
        record = self.record(relative)
        with self.assertRaises(RuntimeError):
            self.repository_manifest(untracked=(relative,),
                                     selection=self.selection(preserved=(record,), selected=(record,)))

    def test_repository_adapter_rejects_current_output_as_selected_evidence(self):
        relative = self.CURRENT + '/scanner-result.json'
        self.file(relative)
        with self.assertRaises(RuntimeError):
            self.repository_manifest(untracked=(relative,),
                                     selection=self.selection(selected=(self.record(relative),)))

    def test_generated_binary_suffix_needs_exact_output_contract(self):
        expected = ('backend/.gradle', 'backend/build', 'backend/migration/build',
                    'backend/runtime/build', 'validation/poc-00/.gradle', 'validation/poc-00/build',
                    'validation/poc-00/java-candidate/build', 'validation/poc-00/kotlin-candidate/build')
        for root in expected:
            with self.subTest(root=root):
                self.assertFalse(ci.expected_generated_path(root + '/TEST_ONLY_SYNTHETIC.class'))
        for root in ('backend/new-module/build', 'other/build', 'validation/poc-04/build',
                     'backend/runtime/building', 'neighbor/backend/build'):
            with self.subTest(root=root):
                self.assertFalse(ci.expected_generated_path(root + '/TEST_ONLY_SYNTHETIC.class'))

    def test_generated_python_java_or_env_is_not_accepted_merely_by_build_root(self):
        for suffix in ('.py', '.java', '.kt', '.env', '.yml', '.yaml', '.sql'):
            with self.subTest(suffix=suffix):
                self.assertFalse(ci.expected_generated_path('backend/build/TEST_ONLY' + suffix))

    def test_repository_adapter_generated_sql_copy_requires_matching_tracked_source(self):
        source = 'backend/migration/src/main/resources/db/migration/TEST_ONLY.sql'
        generated = 'backend/migration/build/resources/main/db/migration/TEST_ONLY.sql'
        self.file(source, b'SELECT 1; -- TEST_ONLY_COPY\n')
        self.file(generated, b'SELECT 1; -- TEST_ONLY_COPY\n')
        manifest = self.repository_manifest(tracked=(source,), ignored=(generated,))
        self.assertEqual([row['path'] for row in manifest['files']], [source])
        self.assertEqual(manifest['expected_generated'],
                         [{'path': generated, 'classification': 'EXPECTED_GENERATED_EPHEMERAL'}])

    def test_repository_adapter_generated_yaml_copy_requires_matching_tracked_source(self):
        source = 'backend/runtime/src/main/resources/TEST_ONLY.yml'
        generated = 'backend/runtime/build/resources/main/TEST_ONLY.yml'
        self.file(source, b'purpose: TEST_ONLY_COPY\n')
        self.file(generated, b'purpose: TEST_ONLY_COPY\n')
        manifest = self.repository_manifest(tracked=(source,), ignored=(generated,))
        self.assertEqual([row['path'] for row in manifest['files']], [source])

    def test_repository_adapter_generated_sql_copy_with_changed_bytes_is_rejected(self):
        source = 'backend/migration/src/main/resources/db/migration/TEST_ONLY.sql'
        generated = 'backend/migration/build/resources/main/db/migration/TEST_ONLY.sql'
        self.file(source, b'SELECT 1; -- TEST_ONLY_SOURCE\n')
        self.file(generated, b'SELECT 2; -- TEST_ONLY_DIFFERENT_BYTES\n')
        with self.assertRaises(RuntimeError):
            self.repository_manifest(tracked=(source,), ignored=(generated,))

    def test_repository_adapter_generated_yaml_without_tracked_source_is_rejected(self):
        source = 'backend/runtime/src/main/resources/TEST_ONLY.yml'
        generated = 'backend/runtime/build/resources/main/TEST_ONLY.yml'
        self.file(source, b'purpose: TEST_ONLY_COPY\n')
        self.file(generated, b'purpose: TEST_ONLY_COPY\n')
        with self.assertRaises(RuntimeError):
            self.repository_manifest(ignored=(generated,))


class ConfiguredSharedResourceBoundaryTest(unittest.TestCase):
    """Resource authority comes from declared Gradle source roots, never byte search."""
    CURRENT = ScanCandidateBoundaryTest.CURRENT
    setUp = ScanCandidateBoundaryTest.setUp
    file = ScanCandidateBoundaryTest.file
    record = ScanCandidateBoundaryTest.record
    repository_manifest = ScanCandidateBoundaryTest.repository_manifest
    assertBound = ScanCandidateBoundaryTest.assertBound
    verify = ScanCandidateBoundaryTest.verify

    SETTINGS = 'validation/poc-00/settings.gradle.kts'
    RESOURCE = 'db/migration/TEST_ONLY.sql'

    def configured_fixture(self, projects=('java-candidate',), relative=None,
                           content=b'SELECT 1; -- TEST_ONLY_SHARED\n'):
        relative = self.RESOURCE if relative is None else relative
        self.file(self.SETTINGS, ('rootProject.name = "vra-poc00"\n' + ''.join(
            'include("' + project + '")\n' for project in projects)).encode())
        self.tracked = {self.SETTINGS}
        self.model_projects = {project: ['validation/poc-00/' + project + '/src/main/resources',
                                         'validation/poc-00/shared'] for project in projects}
        self.model_root_extra = {}
        origins = []
        destinations = []
        for project in projects:
            config = 'validation/poc-00/' + project + '/build.gradle.kts'
            self.file(config, b'sourceSets.main { resources.srcDir(rootProject.file("shared")) }\n')
            self.tracked.add(config)
            origin = 'validation/poc-00/shared/' + relative
            self.file(origin, content)
            self.tracked.add(origin)
            destination = 'validation/poc-00/' + project + '/build/resources/main/' + relative
            self.file(destination, content)
            origins.append(origin)
            destinations.append(destination)
        return destinations, origins

    def fixture_resource_model(self, build_root, repository_root=None, evidence_directory=None):
        """Model the external Gradle response; real path/byte authority remains under test."""
        self.assertEqual(Path(build_root), self.root / 'validation/poc-00')
        projects = [{'project_path': ':',
                     'project_directory': str(self.root / 'validation/poc-00'),
                     'build_file': None, 'source_sets': []}]
        for project, roots in self.model_projects.items():
            source_files = {}
            root_rows = []
            for relative_root in roots:
                root = self.root / relative_root
                row = {'path': str(root), 'canonical_path': str(root.resolve())}
                row.update(self.model_root_extra)
                root_rows.append(row)
                if root.is_dir() and root.resolve().is_relative_to(self.root.resolve()):
                    for source in root.rglob('*'):
                        if source.is_file():
                            source_files[str(source)] = {'path': str(source),
                                                         'canonical_path': str(source.resolve())}
            project_root = self.root / 'validation/poc-00' / project
            output = project_root / 'build/resources/main'
            projects.append({'project_path': ':' + project, 'project_directory': str(project_root),
                             'build_file': str(project_root / 'build.gradle.kts'),
                             'source_sets': [{'name': 'main', 'resource_roots': root_rows,
                                              'resource_files': list(source_files.values()),
                                              'process_resource_files': list(source_files.values()),
                                              'output_directory': str(output),
                                              'process_output_directory': str(output),
                                              'includes': [], 'excludes': []}]})
        raw = {'schema_version': 1, 'gradle_version': '9.7.1',
               'build_root': str(self.root / 'validation/poc-00'), 'projects': projects}
        return ci.validate_gradle_resource_model(raw, self.root, self.root / 'validation/poc-00')

    def resolve(self, destination):
        with patch.object(ci, 'ROOT', self.root), \
                patch.object(ci, 'query_gradle_resource_model', side_effect=self.fixture_resource_model):
            proof = ci.resolve_shared_resource(destination, tracked=self.tracked)
        if proof is None:
            raise RuntimeError('Unresolved shared resource cannot enter the candidate')
        return proof

    def assertSharedProof(self, proof, destination, origin):
        self.assertEqual(proof['path'], destination)
        self.assertEqual(proof['origin_kind'], 'shared')
        self.assertEqual(proof['configured_origin'], origin)
        self.assertEqual(proof['classification'], 'candidate/shared-resource')
        self.assertTrue(proof['equality'])
        self.assertEqual(proof['destination_sha256'], self.record(destination)['sha256'])
        self.assertEqual(proof['origin_sha256'], self.record(origin)['sha256'])
        configurations = {row['path']: row for row in proof['configuration_files']}
        expected = {self.SETTINGS, destination.split('/build/resources/main/')[0] + '/build.gradle.kts'}
        self.assertEqual(set(configurations), expected)
        for path, row in configurations.items():
            self.assertEqual(row, self.record(path))

    def test_exact_declared_shared_resource_resolves_configured_tracked_origin(self):
        destinations, origins = self.configured_fixture()
        self.assertSharedProof(self.resolve(destinations[0]), destinations[0], origins[0])

    def test_multiple_shared_project_resources_resolve_independently(self):
        destinations, origins = self.configured_fixture(('java-candidate', 'kotlin-candidate'))
        for destination, origin in zip(destinations, origins):
            with self.subTest(destination=destination):
                self.assertSharedProof(self.resolve(destination), destination, origin)

    def test_actual_poc00_shared_directory_semantics_preserve_nested_resource_paths(self):
        destinations, origins = self.configured_fixture(relative='db/migration/nested/TEST_ONLY.sql')
        self.assertSharedProof(self.resolve(destinations[0]), destinations[0], origins[0])

    def test_missing_configured_shared_origin_is_rejected(self):
        destinations, origins = self.configured_fixture()
        (self.root / origins[0]).unlink()
        with self.assertRaises(RuntimeError):
            self.resolve(destinations[0])

    def test_destination_differing_by_one_byte_is_rejected(self):
        destinations, _ = self.configured_fixture()
        self.file(destinations[0], b'SELECT 2; -- TEST_ONLY_SHARED\n')
        with self.assertRaises(RuntimeError):
            self.resolve(destinations[0])

    def test_wrong_configured_origin_does_not_use_matching_unrelated_bytes(self):
        destinations, origins = self.configured_fixture()
        unrelated = 'validation/poc-00/shared/db/migration/OTHER_TEST_ONLY.sql'
        self.file(unrelated, (self.root / destinations[0]).read_bytes())
        self.tracked.add(unrelated)
        self.file(origins[0], b'SELECT 2; -- TEST_ONLY_WRONG_ORIGIN\n')
        with self.assertRaises(RuntimeError):
            self.resolve(destinations[0])

    def test_default_and_shared_roots_with_same_resource_are_ambiguous(self):
        destinations, _ = self.configured_fixture()
        default = 'validation/poc-00/java-candidate/src/main/resources/' + self.RESOURCE
        self.file(default, (self.root / destinations[0]).read_bytes())
        self.tracked.add(default)
        with self.assertRaises(RuntimeError):
            self.resolve(destinations[0])

    def test_configured_origin_outside_repository_is_rejected(self):
        destinations, _ = self.configured_fixture()
        self.file('validation/poc-00/java-candidate/build.gradle.kts',
                  b'sourceSets.main { resources.srcDir(rootProject.file("../../outside")) }\n')
        self.model_projects['java-candidate'] = ['../outside']
        with self.assertRaises(RuntimeError):
            self.resolve(destinations[0])

    def test_shared_origin_symlink_escape_is_rejected(self):
        destinations, origins = self.configured_fixture()
        outside = Path(self.temporary.name) / 'outside.sql'
        outside.write_bytes((self.root / destinations[0]).read_bytes())
        (self.root / origins[0]).unlink()
        (self.root / origins[0]).symlink_to(outside)
        with self.assertRaises(RuntimeError):
            self.resolve(destinations[0])

    def test_untracked_shared_origin_is_rejected(self):
        destinations, origins = self.configured_fixture()
        self.tracked.remove(origins[0])
        with self.assertRaises(RuntimeError):
            self.resolve(destinations[0])

    def test_same_basename_at_wrong_path_is_not_an_origin(self):
        destinations, origins = self.configured_fixture()
        (self.root / origins[0]).unlink()
        unrelated = 'validation/poc-00/shared/elsewhere/TEST_ONLY.sql'
        self.file(unrelated, (self.root / destinations[0]).read_bytes())
        self.tracked.add(unrelated)
        with self.assertRaises(RuntimeError):
            self.resolve(destinations[0])

    def test_identical_unrelated_tracked_bytes_do_not_resolve_missing_origin(self):
        destinations, origins = self.configured_fixture()
        (self.root / origins[0]).unlink()
        unrelated = 'backend/migration/src/main/resources/db/migration/TEST_ONLY.sql'
        self.file(unrelated, (self.root / destinations[0]).read_bytes())
        self.tracked.add(unrelated)
        with self.assertRaises(RuntimeError):
            self.resolve(destinations[0])

    def test_unknown_resource_origin_type_is_rejected(self):
        destinations, _ = self.configured_fixture()
        self.file('validation/poc-00/java-candidate/build.gradle.kts',
                  b'sourceSets.main { resources.srcDir(rootProject.file("other-origin")) }\n')
        self.model_root_extra = {'origin_kind': 'UNKNOWN_TEST_ONLY'}
        with self.assertRaises(RuntimeError):
            self.resolve(destinations[0])

    def test_undeclared_generated_sql_file_is_rejected(self):
        destinations, _ = self.configured_fixture()
        undeclared = destinations[0].replace('TEST_ONLY.sql', 'UNDECLARED_TEST_ONLY.sql')
        self.file(undeclared, (self.root / destinations[0]).read_bytes())
        with self.assertRaises(RuntimeError):
            self.resolve(undeclared)

    def test_duplicate_conflicting_origin_declarations_are_rejected(self):
        destinations, _ = self.configured_fixture()
        self.file('validation/poc-00/java-candidate/build.gradle.kts',
                  b'sourceSets.main { resources.srcDir(rootProject.file("shared")) }\n'
                  b'sourceSets.main { resources.srcDir(rootProject.file("different")) }\n')
        other = 'validation/poc-00/different/' + self.RESOURCE
        self.file(other, (self.root / destinations[0]).read_bytes())
        self.tracked.add(other)
        self.model_projects['java-candidate'].append('validation/poc-00/different')
        with self.assertRaises(RuntimeError):
            self.resolve(destinations[0])

    def test_shared_source_change_after_manifest_freeze_is_rejected(self):
        destinations, origins = self.configured_fixture()
        manifest = self.repository_manifest(tracked=sorted(self.tracked), ignored=destinations)
        self.file(origins[0], b'SELECT 2; -- TEST_ONLY_CHANGED_AFTER_FREEZE\n')
        with self.assertRaises(RuntimeError):
            self.verify(manifest)

    def test_destination_copied_from_unrelated_tracked_resource_is_rejected(self):
        destinations, _ = self.configured_fixture()
        unrelated = 'validation/poc-00/shared/db/migration/UNRELATED_TEST_ONLY.sql'
        self.file(unrelated, b'SELECT 7; -- TEST_ONLY_UNRELATED\n')
        self.tracked.add(unrelated)
        self.file(destinations[0], (self.root / unrelated).read_bytes())
        with self.assertRaises(RuntimeError):
            self.resolve(destinations[0])

    def test_missing_shared_resource_configuration_entry_is_rejected(self):
        destinations, _ = self.configured_fixture()
        self.file('validation/poc-00/java-candidate/build.gradle.kts', b'plugins { java }\n')
        self.model_projects['java-candidate'] = ['validation/poc-00/java-candidate/src/main/resources']
        with self.assertRaises(RuntimeError):
            self.resolve(destinations[0])

    def test_untracked_configuration_cannot_authorize_shared_origin(self):
        destinations, _ = self.configured_fixture()
        self.tracked.remove('validation/poc-00/java-candidate/build.gradle.kts')
        with self.assertRaises(RuntimeError):
            self.resolve(destinations[0])

    def test_undeclared_project_cannot_authorize_shared_origin(self):
        destinations, _ = self.configured_fixture()
        self.file(self.SETTINGS, b'rootProject.name = "vra-poc00"\ninclude("kotlin-candidate")\n')
        self.model_projects.clear()
        with self.assertRaises(RuntimeError):
            self.resolve(destinations[0])

    def test_shared_destination_symlink_is_rejected(self):
        destinations, origins = self.configured_fixture()
        (self.root / destinations[0]).unlink()
        (self.root / destinations[0]).symlink_to(self.root / origins[0])
        with self.assertRaises(RuntimeError):
            self.resolve(destinations[0])

    def test_repository_adapter_scans_shared_resources_as_candidate_not_ephemeral(self):
        destinations, origins = self.configured_fixture(('java-candidate', 'kotlin-candidate'))
        manifest = self.repository_manifest(tracked=sorted(self.tracked), ignored=destinations)
        self.assertEqual(manifest['expected_generated'], [])
        self.assertEqual(manifest['classifications']['configured_shared_resources'], 2)
        self.assertEqual(len(manifest['configured_shared_resources']), 2)
        for destination, origin in zip(destinations, origins):
            self.assertBound(manifest, destination, 'candidate/shared-resource')
            proof = next(row for row in manifest['configured_shared_resources'] if row['path'] == destination)
            self.assertSharedProof(proof, destination, origin)

    def test_shared_destination_change_after_freeze_is_rejected(self):
        destinations, _ = self.configured_fixture()
        manifest = self.repository_manifest(tracked=sorted(self.tracked), ignored=destinations)
        self.file(destinations[0], b'SELECT 2; -- TEST_ONLY_CHANGED_AFTER_FREEZE\n')
        with self.assertRaises(RuntimeError):
            self.verify(manifest)


if __name__ == '__main__':
    unittest.main(verbosity=2)
