"""TEST-only fail-closed regression cases for reviewed immutable digest metadata."""
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
spec = importlib.util.spec_from_file_location('phase1_ci_digest', ROOT / 'validation/poc-04/scripts/phase1-ci.py')
ci = importlib.util.module_from_spec(spec)
spec.loader.exec_module(ci)


class DigestMetadataClassifierTest(unittest.TestCase):
    def setUp(self):
        # Only schema data is required: a real file origin, Git objects, and bound
        # metadata lines. No developer evidence directory supplies test authority.
        temporary = tempfile.TemporaryDirectory(prefix='vra-digest-fixture-')
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name).resolve()
        root_patch = patch.object(ci, 'ROOT', self.root)
        root_patch.start()
        self.addCleanup(root_patch.stop)
        self.git_environment = {key: value for key, value in os.environ.items() if not key.startswith('GIT_')}
        self.git_environment.update({'GIT_CONFIG_NOSYSTEM': '1', 'GIT_CONFIG_GLOBAL': os.devnull,
                                     'GIT_AUTHOR_NAME': 'TEST ONLY', 'GIT_AUTHOR_EMAIL': 'test@example.invalid',
                                     'GIT_COMMITTER_NAME': 'TEST ONLY', 'GIT_COMMITTER_EMAIL': 'test@example.invalid',
                                     'GIT_AUTHOR_DATE': '2000-01-01T00:00:00+00:00',
                                     'GIT_COMMITTER_DATE': '2000-01-01T00:00:00+00:00'})
        environment_patch = patch.dict(os.environ, self.git_environment, clear=True)
        environment_patch.start()
        self.addCleanup(environment_patch.stop)
        self.git_command('init', '--quiet')
        tree = self.git_command('mktree', input=b'')
        commit = self.git_command('commit-tree', tree, input=b'TEST ONLY metadata origin\n')
        origin = self.file('fixture/source.txt', b'TEST_ONLY_NON_SECRET_CONTENT\n')
        file_entry = {'path': 'fixture/source.txt', 'sha256': hashlib.sha256(origin.read_bytes()).hexdigest(),
                      'size_bytes': origin.stat().st_size, 'mode': '0644'}
        content = json.dumps([file_entry], sort_keys=True, separators=(',', ':')).encode()
        document = {'schema_version': 1, 'head': commit, 'tree': tree, 'files': [file_entry],
                    'content_sha256': hashlib.sha256(content).hexdigest()}
        metadata = self.file('fixture/metadata.json', (json.dumps(document, indent=2) + '\n').encode())
        records = [self.record(metadata, ['files', 0, 'sha256'], 'FILE_SHA256', 'fixture/source.txt'),
                   self.record(metadata, ['content_sha256'], 'SOURCE_CONTENT_SHA256', 'fixture/metadata.json'),
                   self.record(metadata, ['head'], 'GIT_COMMIT', commit),
                   self.record(metadata, ['tree'], 'GIT_TREE', tree)]
        for name, sentence in (('head-report.md', f'HEAD: {commit}.\n'),
                               ('context-report.md', f'TEST ONLY report. HEAD: {commit}.\n')):
            path = self.file('fixture/' + name, sentence.encode())
            records.append({'path': str(path.relative_to(self.root)),
                            'document_sha256': hashlib.sha256(path.read_bytes()).hexdigest(),
                            'line': 1, 'context_sha256': hashlib.sha256(sentence.rstrip('\n').encode()).hexdigest(),
                            'rule': 'generic-api-key', 'format': 'FROZEN_REPORT_HEAD', 'field': 'HEAD',
                            'value': commit, 'origin_kind': 'GIT_COMMIT', 'origin_target': commit})
        self.file('validation/poc-04/test-support/digest-metadata-contexts.json',
                  json.dumps({'schema_version': 1, 'contexts': records}).encode())
        self.records = ci.approved_digest_contexts()
        self.record = next(row for row in self.records if row['origin_kind'] == 'FILE_SHA256')
        self.value = self.record['value']
        self.observation = self.observe(self.record)

    def git_command(self, *args, input=None):
        return subprocess.check_output(['git', *args], input=input, cwd=self.root,
                                       env=self.git_environment).decode().strip()

    def file(self, name, content):
        path = self.root / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(content)
        path.chmod(0o644)
        return path

    def record(self, path, pointer, kind, target):
        document = json.loads(path.read_text())
        value = document
        for part in pointer:
            value = value[part]
        pair = json.dumps(pointer[-1]) + ': ' + json.dumps(value)
        lines = path.read_text().splitlines()
        line = next(index + 1 for index, text in enumerate(lines) if text.strip().rstrip(',') == pair)
        return {'path': str(path.relative_to(self.root)),
                'document_sha256': hashlib.sha256(path.read_bytes()).hexdigest(),
                'line': line, 'context_sha256': hashlib.sha256(lines[line - 1].encode()).hexdigest(),
                'rule': 'generic-api-key', 'format': 'JSON_FIELD', 'pointer': pointer,
                'value': value, 'origin_kind': kind, 'origin_target': target}

    def observe(self, row):
        return {'path': row['path'], 'file_sha256': row['document_sha256'], 'line': row['line'],
                'context': (self.root / row['path']).read_text().splitlines()[row['line'] - 1],
                'rule': row['rule'], 'origin_kind': 'TEXT_FILE'}

    def classify(self, value=None, observation=None, records=None):
        return ci.classify_digest_metadata(self.value if value is None else value,
                                          self.observation if observation is None else observation,
                                          self.records if records is None else records)

    def test_exact_file_digest_in_reviewed_structured_context(self):
        proof = self.classify()
        self.assertEqual(proof['resolved_origin_kind'], 'FILE_SHA256')
        self.assertEqual(proof['resolved_origin_identity'], self.value)
        self.assertEqual(proof['resolved_origin_target'], self.record['origin_target'])

    def test_exact_source_content_in_existing_manifest_schema(self):
        row = next(row for row in self.records if row['origin_kind'] == 'SOURCE_CONTENT_SHA256')
        proof = self.classify(row['value'], self.observe(row))
        self.assertEqual(proof['metadata_field'], '/content_sha256')
        self.assertEqual(proof['resolved_origin_identity'], row['value'])

    def test_exact_existing_git_metadata_resolves_immutable_commit(self):
        row = next(row for row in self.records if row['origin_kind'] == 'GIT_COMMIT' and row['format'] == 'JSON_FIELD')
        proof = self.classify(row['value'], self.observe(row))
        self.assertEqual(proof['resolved_origin_kind'], 'GIT_COMMIT')
        self.assertEqual(proof['resolved_origin_identity'], row['value'])

    def test_every_reviewed_frozen_tuple_resolves_independently(self):
        self.assertEqual({row['origin_kind'] for row in self.records},
                         {'FILE_SHA256', 'SOURCE_CONTENT_SHA256', 'GIT_COMMIT', 'GIT_TREE'})
        for row in self.records:
            with self.subTest(path=row['path'], line=row['line']):
                self.assertIsNotNone(self.classify(row['value'], self.observe(row)))

    def test_missing_authoritative_file_remains_finding(self):
        (self.root / self.record['origin_target']).unlink()
        self.assertIsNone(self.classify())

    def test_authoritative_file_changed_by_one_byte_remains_finding(self):
        path = self.root / self.record['origin_target']
        path.write_bytes(path.read_bytes() + b'X')
        self.assertIsNone(self.classify())

    def test_same_digest_at_wrong_authoritative_path_remains_finding(self):
        record = dict(self.record, origin_target='fixture/missing-source.txt')
        self.assertIsNone(self.classify(records=[record]))

    def test_known_digest_in_synthetic_password_remains_finding(self):
        self.assertIsNone(self.classify(observation=dict(self.observation, context='"password": "' + self.value + '"')))

    def test_known_digest_in_synthetic_token_remains_finding(self):
        self.assertIsNone(self.classify(observation=dict(self.observation, context='"token": "' + self.value + '"')))

    def test_known_digest_in_unapproved_source_path_remains_finding(self):
        for path in ('runtime/secrets.json', 'synthetic-config.json', 'backend/runtime/SyntheticSource.java'):
            self.assertIsNone(self.classify(observation=dict(self.observation, path=path)))

    def test_missing_origin_remains_finding(self):
        self.assertIsNone(ci.classify_digest_metadata(self.value, None, self.records))
        self.assertIsNone(self.classify(observation=dict(self.observation, origin_kind='UNKNOWN')))

    def test_ambiguous_origin_remains_finding(self):
        self.assertIsNone(self.classify(records=self.records + [copy.deepcopy(self.record)]))
        origin = {'path': self.record['path'], 'file_sha256': self.record['document_sha256'],
                  'start': 1, 'end': 1, 'lines': [self.observation['context'].encode()]}
        item = {'StartLine': 1, 'EndLine': 1, 'RuleID': 'generic-api-key'}
        self.assertIsNone(ci.resolve_scan_observation(item, [origin, dict(origin)]))

    def test_wrong_metadata_field_remains_finding(self):
        self.assertIsNone(self.classify(observation=dict(self.observation, context='"unapproved_sha": "' + self.value + '"')))

    def test_one_character_modified_digest_remains_finding(self):
        changed = self.value[:-1] + ('0' if self.value[-1] != '0' else '1')
        self.assertIsNone(self.classify(changed))

    def test_unknown_sha256_shaped_value_remains_finding(self):
        self.assertIsNone(self.classify(hashlib.sha256(b'TEST_ONLY_UNKNOWN_IDENTIFIER').hexdigest()))

    def test_unknown_git_shaped_value_remains_finding(self):
        self.assertIsNone(self.classify(hashlib.sha1(b'TEST_ONLY_UNKNOWN_IDENTIFIER').hexdigest()))

    def test_known_digest_adjacent_to_synthetic_secret_remains_finding(self):
        self.assertIsNone(self.classify(observation=dict(self.observation,
                         context=self.observation['context'] + ' TEST_ONLY_PASSWORD_MARKER')))

    def test_frozen_report_head_is_exact_not_generic_prose(self):
        for row in (row for row in self.records if row['format'] == 'FROZEN_REPORT_HEAD'):
            observation = self.observe(row)
            self.assertIsNotNone(self.classify(row['value'], observation))
            for change in ({'path': 'unapproved-report.md'}, {'context': observation['context'] + ' extra'},
                           {'context': observation['context'].replace('HEAD:', 'password:')},
                           {'file_sha256': '0' * 64}, {'line': row['line'] + 1}):
                self.assertIsNone(self.classify(row['value'], dict(observation, **change)))

    def test_unknown_document_identity_remains_finding(self):
        self.assertIsNone(self.classify(observation=dict(self.observation, file_sha256='0' * 64)))

    def test_wrong_detector_remains_finding(self):
        self.assertIsNone(self.classify(observation=dict(self.observation, rule='private-key')))

    def test_duplicate_json_keys_are_rejected(self):
        with self.assertRaises(ValueError):
            ci.strict_json('{"head":"TEST_ONLY_A","head":"TEST_ONLY_B"}')

    def test_unresolved_and_multiline_scanner_origins_are_rejected(self):
        item = {'StartLine': 1, 'EndLine': 1, 'RuleID': 'generic-api-key'}
        self.assertIsNone(ci.resolve_scan_observation(item, []))
        self.assertIsNone(ci.resolve_scan_observation(dict(item, EndLine=2), []))

    def test_single_scanner_origin_has_exact_context_identity(self):
        origin = {'path': self.record['path'], 'file_sha256': self.record['document_sha256'],
                  'start': 1, 'end': 1, 'lines': [self.observation['context'].encode()]}
        item = {'StartLine': 1, 'EndLine': 1, 'RuleID': 'generic-api-key'}
        observation = ci.resolve_scan_observation(item, [origin])
        self.assertEqual(observation['origin_kind'], 'TEXT_FILE')
        self.assertEqual(observation['context'], self.observation['context'])


if __name__ == '__main__':
    unittest.main(verbosity=2)
