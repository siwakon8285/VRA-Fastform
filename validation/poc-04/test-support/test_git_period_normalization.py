"""Hermetic TEST-only single-period handling for frozen Git HEAD report metadata."""
import copy
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[3]
spec = importlib.util.spec_from_file_location('phase1_ci_git_period', ROOT / 'validation/poc-04/scripts/phase1-ci.py')
ci = importlib.util.module_from_spec(spec)
spec.loader.exec_module(ci)


class GitPeriodNormalizationTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory(prefix='vra-git-period-fixture-')
        self.addCleanup(self.temporary.cleanup)
        self.fixture_root = Path(self.temporary.name).resolve()
        # Git objects are generated only in this isolated TEST repository. No developer
        # repository, historical remediation directory, hooks or signing settings are used.
        git_environment = {key: value for key, value in os.environ.items() if not key.startswith('GIT_')}
        git_environment.update({'GIT_CONFIG_NOSYSTEM': '1', 'GIT_CONFIG_GLOBAL': os.devnull,
                                'GIT_TERMINAL_PROMPT': '0', 'GIT_AUTHOR_NAME': 'TEST Fixture',
                                'GIT_AUTHOR_EMAIL': 'test-fixture@example.invalid',
                                'GIT_COMMITTER_NAME': 'TEST Fixture',
                                'GIT_COMMITTER_EMAIL': 'test-fixture@example.invalid',
                                'GIT_AUTHOR_DATE': '2000-01-01T00:00:00+0000',
                                'GIT_COMMITTER_DATE': '2000-01-01T00:00:00+0000'})

        def fixture_git(*arguments):
            return subprocess.check_output(
                ['git', '-c', 'core.hooksPath=' + os.devnull, '-c', 'commit.gpgSign=false', *arguments],
                cwd=self.fixture_root, env=git_environment, stderr=subprocess.PIPE).decode().strip()

        fixture_git('init', '--quiet')
        origin_path = 'fixtures/origin.txt'
        origin = self.fixture_root / origin_path
        origin.parent.mkdir(parents=True)
        origin.write_bytes(b'TEST_ONLY_FILE_SHA256_ORIGIN\n')
        origin.chmod(0o644)
        fixture_git('add', '--', origin_path)
        fixture_git('commit', '--quiet', '-m', 'Create isolated TEST metadata origin')
        canonical = fixture_git('rev-parse', 'HEAD')
        tree = fixture_git('rev-parse', 'HEAD^{tree}')
        self.assertRegex(canonical, r'^[0-9a-f]{40}$')

        root_patch = patch.object(ci, 'ROOT', self.fixture_root)
        root_patch.start()
        self.addCleanup(root_patch.stop)
        environment_patch = patch.dict(os.environ, git_environment, clear=True)
        environment_patch.start()
        self.addCleanup(environment_patch.stop)
        self.records = []
        # These reproduce both reviewed sentence structures without making an
        # incidental Round-N directory or report a mandatory test fixture.
        sentences = (
            'Branch: synthetic/hermetic. HEAD: ' + canonical + '. Tree: ' + tree +
            '. Self-contained TEST-only report.',
            'Run UUIDv4: 00000000-0000-4000-8000-000000000001. Branch: synthetic/hermetic. HEAD: ' +
            canonical + '. Exact tree, sorted file hashes/modes and worktree state: source-binding.json.',
        )
        for index, sentence in enumerate(sentences):
            path = 'fixtures/head-report-' + str(index + 1) + '.md'
            document = self.fixture_root / path
            document.write_text(sentence + '\n')
            document.chmod(0o644)
            self.records.append({'path': path, 'document_sha256': hashlib.sha256(document.read_bytes()).hexdigest(), 'line': 1,
                                 'context_sha256': hashlib.sha256(sentence.encode()).hexdigest(),
                                 'rule': 'generic-api-key', 'format': 'FROZEN_REPORT_HEAD', 'field': 'HEAD',
                                 'value': canonical, 'origin_kind': 'GIT_COMMIT', 'origin_target': canonical})

        # A bound file-digest record is needed only to prove that the 40-byte Git
        # delimiter rule does not transfer to the separate 64-byte FILE_SHA256 kind.
        digest = hashlib.sha256(origin.read_bytes()).hexdigest()
        metadata = {'files': [{'path': origin_path, 'sha256': digest,
                               'size_bytes': origin.stat().st_size, 'mode': '0644'}]}
        path = 'fixtures/file-binding.json'
        document = self.fixture_root / path
        document.write_text(json.dumps(metadata, indent=2) + '\n')
        document.chmod(0o644)
        lines = document.read_text().splitlines()
        line = next(index + 1 for index, text in enumerate(lines) if json.dumps(digest) in text)
        self.records.append({'path': path, 'document_sha256': hashlib.sha256(document.read_bytes()).hexdigest(), 'line': line,
                             'context_sha256': hashlib.sha256(lines[line - 1].encode()).hexdigest(),
                             'rule': 'generic-api-key', 'format': 'JSON_FIELD',
                             'pointer': ['files', 0, 'sha256'], 'value': digest,
                             'origin_kind': 'FILE_SHA256', 'origin_target': origin_path})
        self.head_records = [row for row in self.records if row['format'] == 'FROZEN_REPORT_HEAD']
        self.assertEqual(len(self.head_records), 2)
        self.record = self.head_records[0]
        self.value = self.record['value']
        self.observation = self.observe(self.record)

    def observe(self, row):
        return {'path': row['path'], 'file_sha256': row['document_sha256'], 'line': row['line'],
                'context': (self.fixture_root / row['path']).read_text().splitlines()[row['line'] - 1],
                'rule': row['rule'], 'origin_kind': 'TEXT_FILE'}

    def classify(self, value=None, observation=None, records=None):
        return ci.classify_digest_metadata(self.value + '.' if value is None else value,
                                          self.observation if observation is None else observation,
                                          self.records if records is None else records)

    def assert_proof(self, proof, value, canonical, normalization):
        self.assertIsNotNone(proof)
        self.assertEqual(proof['resolved_origin_kind'], 'GIT_COMMIT')
        self.assertEqual(proof['resolved_origin_identity'], canonical)
        self.assertEqual(proof['raw_token_length'], len(value.encode('ascii')))
        self.assertEqual(proof['raw_token_sha256'], hashlib.sha256(value.encode('ascii')).hexdigest())
        self.assertEqual(proof['canonical_identity_length'], 40)
        self.assertEqual(proof['canonical_identity_sha256'], hashlib.sha256(canonical.encode('ascii')).hexdigest())
        self.assertEqual(proof['normalization_applied'], normalization)

    def assert_historical_shape(self, row_index):
        row = self.head_records[row_index]
        # Only the safe 40-byte identity plus one terminal ASCII period is the
        # regression contract. Its origin is resolved from the real fixture Git object.
        raw = row['value'] + '.'
        self.assertEqual(len(row['value'].encode('ascii')), 40)
        self.assertEqual(len(raw.encode('ascii')), 41)
        self.assertEqual(raw.encode('ascii')[40:], b'\x2e')
        proof = self.classify(raw, self.observe(row))
        self.assert_proof(proof, raw, row['value'], 'EXACT_SINGLE_TRAILING_PERIOD')

    def test_exact_approved_commit_is_classified_without_normalization(self):
        proof = self.classify(self.value)
        self.assert_proof(proof, self.value, self.value, 'NONE')

    def test_single_period_in_exact_frozen_head_context_is_classified(self):
        with patch.object(ci, 'git', wraps=ci.git) as resolve:
            proof = self.classify()
        self.assert_proof(proof, self.value + '.', self.value, 'EXACT_SINGLE_TRAILING_PERIOD')
        self.assertIn((('cat-file', '-t', self.value), {}), [(call.args, call.kwargs) for call in resolve.call_args_list])
        self.assertFalse(any(self.value + '.' in call.args for call in resolve.call_args_list))

    def test_first_historical_head_finding_shape(self):
        self.assert_historical_shape(0)

    def test_second_historical_head_finding_shape(self):
        self.assert_historical_shape(1)

    def test_double_period_remains_unresolved(self):
        self.assertIsNone(self.classify(self.value + '..'))

    def test_comma_remains_unresolved(self):
        self.assertIsNone(self.classify(self.value + ','))

    def test_semicolon_remains_unresolved(self):
        self.assertIsNone(self.classify(self.value + ';'))

    def test_colon_remains_unresolved(self):
        self.assertIsNone(self.classify(self.value + ':'))

    def test_leading_period_remains_unresolved(self):
        self.assertIsNone(self.classify('.' + self.value))

    def test_terminal_period_plus_space_remains_unresolved(self):
        self.assertIsNone(self.classify(self.value + '. '))

    def test_extra_hex_character_remains_unresolved(self):
        self.assertIsNone(self.classify(self.value + 'a'))

    def test_embedded_identity_remains_unresolved(self):
        self.assertIsNone(self.classify('TEST_ONLY_PREFIX' + self.value + 'TEST_ONLY_SUFFIX'))

    def test_period_in_credential_context_remains_unresolved(self):
        observation = dict(self.observation, context='credential=' + self.value + '.')
        self.assertIsNone(self.classify(observation=observation))

    def test_period_in_token_or_password_field_remains_unresolved(self):
        for field in ('token', 'password'):
            with self.subTest(field=field):
                observation = dict(self.observation, context=json.dumps({field: self.value + '.'}))
                self.assertIsNone(self.classify(observation=observation))

    def test_period_in_unapproved_path_remains_unresolved(self):
        self.assertIsNone(self.classify(observation=dict(self.observation, path='synthetic/credentials.json')))

    def test_period_with_origin_resolution_failure_remains_unresolved(self):
        failure = subprocess.CalledProcessError(1, ['git', 'cat-file'])
        with patch.object(ci, 'git', side_effect=failure):
            self.assertIsNone(self.classify())

    def test_period_with_ambiguous_origin_remains_unresolved(self):
        self.assertIsNone(self.classify(records=self.records + [copy.deepcopy(self.record)]))

    def test_period_with_different_resolved_commit_remains_unresolved(self):
        # A synthetic object body is not written into Git. Its computed identity differs.
        synthetic_commit = b'TEST_ONLY_DIFFERENT_COMMIT_OBJECT\n'
        with patch.object(ci, 'git', return_value='commit'), \
                patch.object(ci.subprocess, 'check_output', return_value=synthetic_commit):
            self.assertIsNone(self.classify())

    def test_unknown_git_identity_plus_period_remains_unresolved(self):
        unknown = hashlib.sha1(b'TEST_ONLY_UNKNOWN_COMMIT_IDENTIFIER').hexdigest()
        self.assertNotIn(unknown, [row['value'] for row in self.records])
        self.assertIsNone(self.classify(unknown + '.'))

    def test_known_file_sha256_plus_period_does_not_inherit_git_rule(self):
        row = next(row for row in self.records if row['origin_kind'] == 'FILE_SHA256')
        self.assertIsNone(self.classify(row['value'] + '.', self.observe(row)))

    def test_unknown_64_hex_digest_plus_period_does_not_inherit_git_rule(self):
        digest = hashlib.sha256(b'TEST_ONLY_UNKNOWN_DIGEST_IDENTIFIER').hexdigest()
        self.assertIsNone(self.classify(digest + '.'))

    def test_internal_punctuation_remains_unresolved(self):
        self.assertIsNone(self.classify(self.value[:20] + '.' + self.value[20:]))


if __name__ == '__main__':
    unittest.main(verbosity=2)
