"""TEST-only proof that scanner configuration cannot inherit an override."""
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch


ROOT = Path(__file__).resolve().parents[3]
BOOTSTRAP = ROOT / 'validation/poc-04/scripts/phase1-ci.py'
spec = importlib.util.spec_from_file_location('phase1_ci_scanner_configuration', BOOTSTRAP)
ci = importlib.util.module_from_spec(spec)
spec.loader.exec_module(ci)


class ScannerConfigurationBindingTest(unittest.TestCase):
    VERSION = '8.30.1'
    FAKE_BINARY = b'TEST_ONLY_NONEXECUTABLE_PINNED_SCANNER_BYTES\n'
    ALLOWLIST = ('title = "TEST_ONLY_SCANNER_CONFIGURATION"\n'
                 '[allowlist]\n'
                 'regexes = ["TEST_ONLY_GLOBAL_SCAN_SUPPRESSION"]\n')

    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory(prefix='vra-poc04-scanner-config-test-')
        self.addCleanup(self.temporary.cleanup)
        self.directory = Path(self.temporary.name)
        self.snapshot = self.directory / 'restricted-snapshot'
        self.snapshot.mkdir(mode=0o700)
        self.cwd = self.directory / 'caller-directory'
        self.cwd.mkdir(mode=0o700)
        self.binary = self.directory / 'TEST_ONLY_SCANNER_BINARY'
        self.binary.write_bytes(self.FAKE_BINARY)
        self.binary.chmod(0o700)

    def bind(self, parent_env=None):
        self.assertTrue(callable(getattr(ci, 'bound_scanner_environment', None)),
                        'scanner configuration binding has not been implemented')
        original = Path.cwd()
        try:
            os.chdir(self.cwd)
            return ci.bound_scanner_environment(
                self.snapshot, {} if parent_env is None else parent_env,
                self.binary, self.VERSION)
        finally:
            os.chdir(original)

    def assert_rejected_before_execution(self, environment, forbidden_value=None):
        with patch.object(ci.subprocess, 'run') as execution:
            with self.assertRaises(RuntimeError) as rejected:
                self.bind(environment)
            execution.assert_not_called()
        if forbidden_value:
            self.assertNotIn(forbidden_value, str(rejected.exception))

    def test_default_identity_binds_exact_knownfake_binary_and_version(self):
        environment, identity = self.bind({'PATH': '/TEST_ONLY_BIN'})
        self.assertEqual(identity['mode'], 'PINNED_BINARY_DEFAULT')
        self.assertEqual(identity['version'], self.VERSION)
        self.assertEqual(identity['binary_sha256'], hashlib.sha256(self.FAKE_BINARY).hexdigest())
        self.assertEqual(identity['override_presence'],
                         {'GITLEAKS_CONFIG': False, 'GITLEAKS_CONFIG_TOML': False})
        self.assertEqual(environment['PATH'], '/TEST_ONLY_BIN')
        self.assertNotIn('GITLEAKS_CONFIG', environment)
        self.assertNotIn('GITLEAKS_CONFIG_TOML', environment)

    def test_default_identity_is_repeatable_for_same_bytes(self):
        first = self.bind()[1]
        second = self.bind()[1]
        self.assertEqual(first, second)

    def test_different_binary_bytes_have_different_configuration_identity(self):
        first = self.bind()[1]
        changed = b'TEST_ONLY_DIFFERENT_NONEXECUTABLE_SCANNER_BYTES\n'
        self.binary.write_bytes(changed)
        second = self.bind()[1]
        self.assertEqual(second['binary_sha256'], hashlib.sha256(changed).hexdigest())
        self.assertNotEqual(first['binary_sha256'], second['binary_sha256'])

    def test_environment_is_copied_without_mutating_parent(self):
        parent = {'PATH': '/TEST_ONLY_BIN', 'TEST_ONLY_UNRELATED_SETTING': 'retained'}
        before = dict(parent)
        environment, _ = self.bind(parent)
        self.assertEqual(parent, before)
        self.assertIsNot(environment, parent)
        self.assertEqual(environment, parent)

    def test_parent_file_allowlist_override_is_rejected_before_scanner_execution(self):
        allowlist = self.directory / 'TEST_ONLY_ALLOWLIST.toml'
        allowlist.write_text(self.ALLOWLIST)
        self.assert_rejected_before_execution({'GITLEAKS_CONFIG': str(allowlist)}, str(allowlist))

    def test_parent_inline_allowlist_override_is_rejected_before_scanner_execution(self):
        self.assert_rejected_before_execution({'GITLEAKS_CONFIG_TOML': self.ALLOWLIST}, self.ALLOWLIST)

    def test_empty_parent_file_override_is_still_rejected(self):
        self.assert_rejected_before_execution({'GITLEAKS_CONFIG': ''})

    def test_empty_parent_inline_override_is_still_rejected(self):
        self.assert_rejected_before_execution({'GITLEAKS_CONFIG_TOML': ''})

    def test_combined_parent_overrides_are_rejected_without_value_disclosure(self):
        file_value = 'TEST_ONLY_FORBIDDEN_FILE_OVERRIDE_VALUE'
        inline_value = 'TEST_ONLY_FORBIDDEN_INLINE_OVERRIDE_VALUE'
        with patch.object(ci.subprocess, 'run') as execution:
            with self.assertRaises(RuntimeError) as rejected:
                self.bind({'GITLEAKS_CONFIG': file_value, 'GITLEAKS_CONFIG_TOML': inline_value})
            execution.assert_not_called()
        self.assertNotIn(file_value, str(rejected.exception))
        self.assertNotIn(inline_value, str(rejected.exception))

    def test_snapshot_source_configuration_is_rejected_before_execution(self):
        (self.snapshot / '.gitleaks.toml').write_text(self.ALLOWLIST)
        self.assert_rejected_before_execution({})

    def test_empty_snapshot_source_configuration_is_rejected(self):
        (self.snapshot / '.gitleaks.toml').write_bytes(b'')
        self.assert_rejected_before_execution({})

    def test_snapshot_configuration_symlink_is_rejected(self):
        external = self.directory / 'TEST_ONLY_EXTERNAL_CONFIG.toml'
        external.write_text(self.ALLOWLIST)
        (self.snapshot / '.gitleaks.toml').symlink_to(external)
        self.assert_rejected_before_execution({})

    def test_snapshot_broken_configuration_symlink_is_rejected(self):
        (self.snapshot / '.gitleaks.toml').symlink_to(self.directory / 'TEST_ONLY_MISSING_CONFIG.toml')
        self.assert_rejected_before_execution({})

    def test_caller_working_directory_configuration_is_rejected(self):
        (self.cwd / '.gitleaks.toml').write_text(self.ALLOWLIST)
        self.assert_rejected_before_execution({})

    def test_caller_broken_configuration_symlink_is_rejected(self):
        (self.cwd / '.gitleaks.toml').symlink_to(self.directory / 'TEST_ONLY_MISSING_CALLER_CONFIG.toml')
        self.assert_rejected_before_execution({})

    def test_default_configuration_identity_contains_no_configuration_payload(self):
        _, identity = self.bind()
        serialized = json.dumps(identity, sort_keys=True)
        self.assertNotIn(self.ALLOWLIST, serialized)
        self.assertNotIn('TEST_ONLY_GLOBAL_SCAN_SUPPRESSION', serialized)


if __name__ == '__main__':
    unittest.main(verbosity=2)
