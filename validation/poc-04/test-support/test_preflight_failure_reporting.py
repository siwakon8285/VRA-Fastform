"""Hermetic TEST-only evidence for early Phase-1 precondition failure reporting."""
import contextlib
import importlib.util
import io
import json
from pathlib import Path
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import Mock, patch


ROOT = Path(__file__).resolve().parents[3]
spec = importlib.util.spec_from_file_location(
    'phase1_ci_preflight_failure', ROOT / 'validation/poc-04/scripts/phase1-ci.py')
ci = importlib.util.module_from_spec(spec)
spec.loader.exec_module(ci)


class PreflightFailureReportingTest(unittest.TestCase):
    primary_failure = 'Docker Engine API >=1.49 required'

    def setUp(self):
        temporary = tempfile.TemporaryDirectory(prefix='vra-poc04-preflight-test-')
        self.addCleanup(temporary.cleanup)
        self.home = Path(temporary.name).resolve()
        self.run = object.__new__(ci.Run)
        self.run.args = SimpleNamespace(gate='full')
        self.run.out = self.home / 'evidence'
        self.run.out.mkdir()
        self.run.execution_directory = self.home / 'execution'
        self.run.support = self.run.execution_directory / 'support'
        self.run.support.mkdir(parents=True)
        self.run.scan_root = self.home / 'immutable-input'
        self.run.scan_manifest = {}
        self.run.scan_identity = {}
        self.run.run_id = 'TEST_ONLY_RUN'
        self.run.recorder = None
        self.run.result = {'status': 'RUNNING', 'source_binding_status': 'NOT EXECUTED',
                           'secret_scan_status': 'NOT EXECUTED', 'scanner_execution': 'NOT EXECUTED',
                           'cleanup_execution': 'NOT EXECUTED'}
        self.run.preflight = Mock(side_effect=RuntimeError(self.primary_failure))
        self.run.secret_scan = Mock(side_effect=AssertionError('Unexpected scanner execution'))
        self.run.cleanup = Mock()

    def execute(self):
        with contextlib.redirect_stdout(io.StringIO()):
            status = self.run.execute()
        self.assertEqual(status, 1)
        result = json.loads((self.run.out / 'result.json').read_text())
        self.assertEqual(result['status'], 'FAIL')
        self.assertEqual(result['execution_status'], 'FAIL')
        self.assertEqual(result['failure'], self.primary_failure)
        self.assertNotIn('source_or_scan_failure', result)
        self.assertEqual(result['scanner_execution'], 'NOT EXECUTED')
        self.run.secret_scan.assert_not_called()
        return result

    def authority(self):
        self.run.state_path = self.run.support / 'run-state.json'
        self.run.state_path.write_text(json.dumps({'resources': {}, 'run_id': 'TEST_ONLY_RUN'}))

    def test_absent_source_reports_precondition_failed_without_attribute_error(self):
        result = self.execute()
        self.assertFalse(hasattr(self.run, 'source'))
        self.assertEqual(result['source_binding_status'], 'NOT EXECUTED / PRECONDITION FAILED')
        self.assertEqual(result['secret_scan_status'], 'NOT EXECUTED / PRECONDITION FAILED')
        self.assertFalse((self.run.out / 'source-binding-final.json').exists())
        evidence = json.loads((self.run.out / 'EVIDENCE_BINDING.json').read_text())
        self.assertEqual(evidence['status'], 'FAIL')
        self.assertIsNone(evidence['source_content_sha256'])

    def test_no_resource_authority_does_not_execute_cleanup(self):
        result = self.execute()
        self.run.cleanup.assert_not_called()
        self.assertEqual(result['cleanup_status'], 'PASS')
        self.assertEqual(result['cleanup_execution'], 'NOT EXECUTED / NO RESOURCE AUTHORITY')

    def test_existing_resource_authority_executes_cleanup(self):
        self.authority()
        result = self.execute()
        self.run.cleanup.assert_called_once_with()
        self.assertEqual(result['cleanup_execution'], 'EXECUTED')
        self.assertEqual(result['cleanup_status'], 'PASS')

    def test_cleanup_failure_does_not_replace_primary_preflight_failure(self):
        self.authority()
        self.run.cleanup.side_effect = RuntimeError('TEST_ONLY_EXACT_CLEANUP_FAILURE')
        result = self.execute()
        self.assertEqual(result['cleanup_status'], 'FAIL')
        self.assertEqual(result['cleanup_failure'], 'TEST_ONLY_EXACT_CLEANUP_FAILURE')
        self.assertTrue((self.run.out / 'cleanup-failure-ledger.json').is_file())

    def test_absent_source_does_not_attempt_final_source_or_scanner_checks(self):
        with patch.object(ci, 'verify_frozen_scan_domain') as frozen, \
                patch.object(ci, 'binding') as binding, \
                patch.object(ci, 'verify_scan_candidate') as candidate:
            self.execute()
        frozen.assert_not_called()
        binding.assert_not_called()
        candidate.assert_not_called()

    def test_actual_api_floor_failure_keeps_primary_cause_and_unexecuted_gates(self):
        self.run.preflight = lambda: ci.Run.preflight(self.run)
        self.run.env = {'DOCKER_HOST': 'unix:///TEST_ONLY/docker.sock',
                        'VRA_POC04_DISPOSABLE_TEST_DOCKER_ENDPOINT': 'unix:///TEST_ONLY/docker.sock'}
        self.run.docker = Mock(side_effect=[json.dumps({'ApiVersion': '1.48'}), '{}'])
        result = self.execute()
        self.assertEqual(self.run.docker.call_count, 2)
        self.assertEqual(result['source_binding_status'], 'NOT EXECUTED / PRECONDITION FAILED')
        self.assertEqual(result['secret_scan_status'], 'NOT EXECUTED / PRECONDITION FAILED')

    def test_established_source_still_finalizes_after_later_preflight_failure(self):
        self.run.source = {'head': 'TEST_ONLY_HEAD', 'tree': 'TEST_ONLY_TREE',
                           'content_sha256': 'TEST_ONLY_CONTENT'}
        with patch.object(ci, 'verify_frozen_scan_domain') as frozen, \
                patch.object(ci, 'binding', return_value=dict(self.run.source)) as binding:
            result = self.execute()
        frozen.assert_called_once_with(self.run.scan_root, self.run.scan_manifest, self.run.scan_identity)
        binding.assert_called_once_with(self.run.scan_root)
        self.assertEqual(result['source_binding_status'], 'PASS')
        self.assertEqual(result['secret_scan_status'], 'NOT EXECUTED')
        self.assertTrue((self.run.out / 'source-binding-final.json').is_file())


if __name__ == '__main__':
    unittest.main()
