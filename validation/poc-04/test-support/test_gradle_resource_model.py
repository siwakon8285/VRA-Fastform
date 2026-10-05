"""TEST-only evaluated Gradle resource authority and observational query contracts."""
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

ROOT = Path(__file__).resolve().parents[3]
spec = importlib.util.spec_from_file_location('phase1_ci_gradle_model', ROOT / 'validation/poc-04/scripts/phase1-ci.py')
ci = importlib.util.module_from_spec(spec)
spec.loader.exec_module(ci)


class GradleResourceModelTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory(prefix='vra-gradle-model-test-')
        self.addCleanup(self.temporary.cleanup)
        self.root = (Path(self.temporary.name) / 'repository').resolve()
        self.build_root = self.root / 'validation/poc-00'
        self.project = self.build_root / 'java-candidate'
        self.file('validation/poc-00/settings.gradle.kts', b'include("java-candidate")\n')
        self.file('validation/poc-00/java-candidate/build.gradle.kts', b'plugins { java }\n')
        self.root_patch = patch.object(ci, 'ROOT', self.root)
        self.root_patch.start()
        self.addCleanup(self.root_patch.stop)
        self.resource = 'db/TEST_ONLY.sql'
        self.origin_root = self.build_root / 'shared'
        self.origin = self.origin_root / self.resource
        self.file(str(self.origin.relative_to(self.root)), b'SELECT 1; -- TEST_ONLY_RESOURCE\n')
        self.destination = 'validation/poc-00/java-candidate/build/resources/main/' + self.resource
        self.file(self.destination, self.origin.read_bytes())
        self.tracked = {'validation/poc-00/settings.gradle.kts',
                        'validation/poc-00/java-candidate/build.gradle.kts',
                        str(self.origin.relative_to(self.root))}

    def file(self, name, content):
        path = self.root / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(content)
        path.chmod(0o644)
        return path

    def path_record(self, path):
        return {'path': str(path), 'canonical_path': str(path.resolve())}

    def raw_model(self, roots=None, source_set='main', files=None):
        roots = [self.origin_root] if roots is None else roots
        files = [self.origin] if files is None else files
        output = str(self.project / 'build/resources' / source_set)
        return {'schema_version': 1, 'gradle_version': '8.14.3', 'build_root': str(self.build_root),
                'projects': [{'project_path': ':java-candidate', 'project_directory': str(self.project),
                              'build_file': str(self.project / 'build.gradle.kts'),
                              'source_sets': [{'name': source_set,
                                               'resource_roots': [self.path_record(p) for p in roots],
                                               'resource_files': [self.path_record(p) for p in files],
                                               'process_resource_files': [self.path_record(p) for p in files],
                                               'output_directory': output,
                                               'process_output_directory': output,
                                               'includes': [], 'excludes': []}]},
                             {'project_path': ':', 'project_directory': str(self.build_root),
                              'build_file': None, 'source_sets': []}]}

    def model(self, raw=None):
        return ci.validate_gradle_resource_model(self.raw_model() if raw is None else raw,
                                                self.root, self.build_root)

    def resolve(self, raw=None, destination=None):
        return ci.resolve_shared_resource(self.destination if destination is None else destination,
                                          tracked=self.tracked, resource_model=self.model(raw))

    def test_single_main_effective_root_resolves_exact_origin(self):
        proof = self.resolve()
        self.assertEqual(proof['configured_origin'], str(self.origin.relative_to(self.root)))
        self.assertTrue(proof['equality'])
        self.assertEqual(proof['origin_sha256'], hashlib.sha256(self.origin.read_bytes()).hexdigest())

    def test_single_test_effective_root_resolves_exact_origin(self):
        destination = self.destination.replace('/resources/main/', '/resources/test/')
        self.file(destination, self.origin.read_bytes())
        proof = self.resolve(self.raw_model(source_set='test'), destination)
        self.assertEqual(proof['configured_origin'], str(self.origin.relative_to(self.root)))

    def test_model_authority_is_independent_of_build_text_layout(self):
        self.file('validation/poc-00/java-candidate/build.gradle.kts',
                  b'// TEST: text does not supply resource authority\nval roots = sourceSets.main.get().resources.srcDirs\n')
        self.assertTrue(self.resolve()['equality'])

    def test_legitimate_main_model_read_does_not_authorize_or_reject(self):
        self.file('validation/poc-00/java-candidate/build.gradle.kts',
                  b'val roots = sourceSets.main.get().resources.srcDirs\n')
        self.assertTrue(self.resolve()['equality'])

    def test_legitimate_test_model_read_does_not_authorize_or_reject(self):
        self.file('validation/poc-00/java-candidate/build.gradle.kts',
                  b'val roots = sourceSets.test.get().resources.srcDirs\n')
        self.assertTrue(self.resolve()['equality'])

    def test_zero_matching_roots_is_rejected(self):
        other = self.build_root / 'other'
        with self.assertRaises(RuntimeError):
            self.resolve(self.raw_model(roots=[other], files=[]))

    def test_two_applicable_roots_are_rejected_even_for_equal_bytes(self):
        other = self.build_root / 'other'
        second = other / self.resource
        self.file(str(second.relative_to(self.root)), self.origin.read_bytes())
        self.tracked.add(str(second.relative_to(self.root)))
        with self.assertRaises(RuntimeError):
            self.resolve(self.raw_model(roots=[self.origin_root, other], files=[self.origin, second]))

    def test_multiple_srcdirs_effective_ambiguity_is_rejected(self):
        self.test_two_applicable_roots_are_rejected_even_for_equal_bytes()

    def test_nested_second_root_effective_ambiguity_is_rejected(self):
        self.test_two_applicable_roots_are_rejected_even_for_equal_bytes()

    def test_duplicate_canonical_roots_are_rejected(self):
        with self.assertRaises(RuntimeError):
            self.model(self.raw_model(roots=[self.origin_root, self.origin_root]))

    def test_missing_project_is_rejected(self):
        raw = self.raw_model()
        raw['projects'] = []
        with self.assertRaises(RuntimeError):
            self.resolve(raw)

    def test_missing_source_set_is_rejected(self):
        raw = self.raw_model()
        raw['projects'][0]['source_sets'] = []
        with self.assertRaises(RuntimeError):
            self.resolve(raw)

    def test_wrong_schema_is_rejected(self):
        raw = self.raw_model()
        raw['schema_version'] = 999
        with self.assertRaises(RuntimeError):
            self.model(raw)

    def test_missing_model_field_is_rejected(self):
        raw = self.raw_model()
        del raw['projects'][0]['project_directory']
        with self.assertRaises(RuntimeError):
            self.model(raw)

    def test_path_traversal_is_rejected(self):
        raw = self.raw_model()
        raw['projects'][0]['source_sets'][0]['resource_roots'][0]['path'] = str(self.build_root / 'shared/../shared')
        with self.assertRaises(RuntimeError):
            self.model(raw)

    def test_symlink_root_is_rejected(self):
        linked = self.build_root / 'linked'
        linked.symlink_to(self.origin_root, target_is_directory=True)
        with self.assertRaises(RuntimeError):
            self.model(self.raw_model(roots=[linked]))

    def test_origin_outside_repository_is_rejected(self):
        outside = Path(self.temporary.name).resolve() / 'outside'
        with self.assertRaises(RuntimeError):
            self.model(self.raw_model(roots=[outside], files=[]))

    def test_origin_file_symlink_is_rejected(self):
        target = self.build_root / 'target.sql'
        target.write_bytes(self.origin.read_bytes())
        self.origin.unlink()
        self.origin.symlink_to(target)
        with self.assertRaises(RuntimeError):
            self.resolve()

    def test_origin_bytes_differ_is_rejected(self):
        self.file(self.destination, self.origin.read_bytes() + b'X')
        with self.assertRaises(RuntimeError):
            self.resolve()

    def test_untracked_origin_is_rejected(self):
        self.tracked.remove(str(self.origin.relative_to(self.root)))
        with self.assertRaises(RuntimeError):
            self.resolve()

    def test_identical_unrelated_bytes_cannot_supply_origin(self):
        other = self.build_root / 'unrelated' / self.resource
        self.file(str(other.relative_to(self.root)), self.origin.read_bytes())
        self.tracked.add(str(other.relative_to(self.root)))
        self.origin.unlink()
        with self.assertRaises(RuntimeError):
            self.resolve(self.raw_model(files=[]))

    def test_textual_shared_declaration_is_not_authority_without_evaluated_root(self):
        self.file('validation/poc-00/java-candidate/build.gradle.kts',
                  b'sourceSets.main { resources.srcDir(rootProject.file("shared")) }\n')
        with self.assertRaises(RuntimeError):
            self.resolve(self.raw_model(roots=[], files=[]))

    def test_excluded_file_cannot_supply_origin(self):
        raw = self.raw_model(files=[])
        raw['projects'][0]['source_sets'][0]['excludes'] = ['**/*.sql']
        with self.assertRaises(RuntimeError):
            self.resolve(raw)

    def test_process_resources_cannot_add_unmodeled_input(self):
        raw = self.raw_model()
        other = self.build_root / 'unrelated.sql'
        self.file(str(other.relative_to(self.root)), b'TEST_ONLY\n')
        raw['projects'][0]['source_sets'][0]['process_resource_files'].append(self.path_record(other))
        with self.assertRaises(RuntimeError):
            self.model(raw)

    def test_process_resources_destination_mismatch_is_rejected(self):
        raw = self.raw_model()
        raw['projects'][0]['source_sets'][0]['process_output_directory'] = str(self.project / 'different-output')
        with self.assertRaises(RuntimeError):
            self.model(raw)

    def query_with_mock_process(self, callback):
        wrapper = self.file('validation/poc-00/gradlew', b'#!/bin/sh\n# TEST_ONLY_NONEXECUTED_WRAPPER\n')
        wrapper.chmod(0o755)
        with patch.object(ci.subprocess, 'run', side_effect=callback):
            return ci.query_gradle_resource_model(self.build_root, repository_root=self.root)

    def test_model_query_explicitly_disables_problems_report(self):
        def emit_model(argv, **kwargs):
            self.assertEqual(argv.count('--no-problems-report'), 1)
            output = next(value.split('=', 1)[1] for value in argv
                          if value.startswith('-Dvra.poc04.resourceModelOutput='))
            Path(output).write_text(json.dumps(self.raw_model()))
            return subprocess.CompletedProcess(argv, 0, '', '')
        model = self.query_with_mock_process(emit_model)
        project = next(p for p in model['projects'] if p['project_path'] == ':java-candidate')
        main = next(s for s in project['source_sets'] if s['name'] == 'main')
        self.assertEqual(main['resource_files'], [self.path_record(self.origin)])
        self.assertEqual(model['query_script_sha256'], ci.GRADLE_RESOURCE_QUERY_SHA256)

    def test_model_query_process_failure_has_no_textual_fallback(self):
        with self.assertRaises(RuntimeError):
            self.query_with_mock_process(lambda *a, **kw: subprocess.CompletedProcess(a[0], 1, '', ''))

    def test_model_query_timeout_has_no_textual_fallback(self):
        def timeout(*a, **kw):
            raise subprocess.TimeoutExpired(a[0], kw.get('timeout'))
        with self.assertRaises(RuntimeError):
            self.query_with_mock_process(timeout)

    def test_model_query_missing_output_is_rejected(self):
        with self.assertRaises(RuntimeError):
            self.query_with_mock_process(lambda *a, **kw: subprocess.CompletedProcess(a[0], 0, '', ''))

    def test_model_query_malformed_json_is_rejected(self):
        def malformed(argv, **kwargs):
            output = next(value.split('=', 1)[1] for value in argv
                          if value.startswith('-Dvra.poc04.resourceModelOutput='))
            Path(output).write_text('{TEST_ONLY_INVALID_JSON')
            return subprocess.CompletedProcess(argv, 0, '', '')
        with self.assertRaises(RuntimeError):
            self.query_with_mock_process(malformed)

    def test_real_gradle_nested_roots_and_model_reads_are_observed(self):
        # Built-in Java plugin only: no application/test task or external plugin.
        # Wrapper bytes are the same tracked POC-00 wrapper, copied into this TEST root.
        for relative in ('gradlew', 'gradle/wrapper/gradle-wrapper.jar', 'gradle/wrapper/gradle-wrapper.properties'):
            source = ROOT / 'validation/poc-00' / relative
            target = self.build_root / relative
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(source, target)
            target.chmod(source.stat().st_mode & 0o777)
        config = b'''plugins { java }
sourceSets {
    named("main") { resources { srcDir(rootProject.file("shared")) } }
    named("test") { resources { srcDir(rootProject.file("test-shared")) } }
}
val mainOutput = sourceSets.main.get().output
val testOutput = sourceSets.test.get().output
val mainResources = sourceSets.main.get().resources.srcDirs
val testResources = sourceSets.test.get().resources.srcDirs
'''
        self.file('validation/poc-00/java-candidate/build.gradle.kts', config)
        test_origin = self.file('validation/poc-00/test-shared/TEST_ONLY.txt', b'TEST_ONLY_TEST_RESOURCE\n')
        model = ci.query_gradle_resource_model(self.build_root, repository_root=self.root)
        self.assertEqual(list(self.build_root.rglob('problems-report.html')), [])
        project = next(p for p in model['projects'] if p['project_path'] == ':java-candidate')
        main = next(s for s in project['source_sets'] if s['name'] == 'main')
        test = next(s for s in project['source_sets'] if s['name'] == 'test')
        self.assertIn(str(self.origin_root), [r['canonical_path'] for r in main['resource_roots']])
        self.assertIn(str(test_origin.parent), [r['canonical_path'] for r in test['resource_roots']])
        self.assertIn(str(self.origin), [r['canonical_path'] for r in main['resource_files']])
        self.assertIn(str(test_origin), [r['canonical_path'] for r in test['resource_files']])
        self.assertTrue(self.resolve(model)['equality'])
        self.assertEqual(model['query_script_sha256'], hashlib.sha256(ci.GRADLE_RESOURCE_QUERY_SOURCE.encode()).hexdigest())


if __name__ == '__main__':
    unittest.main(verbosity=2)
