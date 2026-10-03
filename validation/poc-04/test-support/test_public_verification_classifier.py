"""TEST-only fail-closed proof for the CI scanner's bounded public identifier triage."""
import copy
import hashlib
import importlib.util
import json
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[3]
spec = importlib.util.spec_from_file_location('phase1_ci', ROOT / 'validation/poc-04/scripts/phase1-ci.py')
ci = importlib.util.module_from_spec(spec)
spec.loader.exec_module(ci)
IDENTIFIER = '7169605F62C751356D054A26A821E680E5FA6305'
PROVENANCE = {
    'repository': 'docker-library/python',
    'revision': '688a0b86bb44289df16a363e9f41d90514c1a5f9',
    'path': '3.13/slim-bookworm/Dockerfile',
    'sha256': '9167a49bf538f7df8fb2a7f04c0a4ff2ad157a317ac2ae54e22c761e718f9b20',
    'purpose': 'public Python source signature verification identifier',
}


class PublicVerificationClassifierTest(unittest.TestCase):
    def setUp(self):
        path = ROOT / 'validation/poc-04/tooling/web-python-pins.json'
        self.observation = {'path': str(path.relative_to(ROOT)), 'line': 228,
                            'file_sha256': hashlib.sha256(path.read_bytes()).hexdigest(),
                            'context': path.read_text().splitlines()[227], 'rule': 'generic-api-key'}

    def classify(self, value=IDENTIFIER, observation=None, provenance=PROVENANCE):
        # Before the correction, the scanner leaves all public identifiers unresolved.
        classifier = getattr(ci, 'classify_public_verification_identifier', lambda *args: False)
        return classifier(value, self.observation if observation is None else observation, provenance)

    def test_exact_amd64_frozen_metadata_is_public(self):
        self.assertTrue(self.classify())

    def test_exact_arm64_frozen_metadata_is_public(self):
        row = dict(self.observation, line=259)
        self.assertTrue(self.classify(observation=row))

    def test_one_character_modified_identifier_remains_finding(self):
        self.assertFalse(self.classify(IDENTIFIER[:-1] + '6'))

    def test_known_identifier_in_unapproved_paths_remains_finding(self):
        for path in ('credentials.json', 'runtime/secrets.json', 'private-key.pem',
                     'validation/poc-04/evidence/unapproved.json'):
            with self.subTest(path=path):
                self.assertFalse(self.classify(observation=dict(self.observation, path=path)))

    def test_known_identifier_in_unapproved_context_remains_finding(self):
        self.assertFalse(self.classify(observation=dict(self.observation, context='runtime credential')))
        self.assertFalse(self.classify(observation=dict(self.observation, line=229)))

    def test_unknown_hex_value_remains_finding(self):
        self.assertFalse(self.classify('0123456789ABCDEF' * 2 + '01234567'))

    def test_private_marker_remains_finding(self):
        self.assertFalse(self.classify('-----BEGIN PRIVATE KEY-----'))

    def test_synthetic_token_and_password_values_remain_findings(self):
        for value in ('TEST_ONLY_TOKEN_MARKER', 'TEST_ONLY_PASSWORD_MARKER'):
            self.assertFalse(self.classify(value))

    def test_known_identifier_with_secret_material_remains_finding(self):
        row = dict(self.observation, context=self.observation['context'] + ' TEST_ONLY_PASSWORD_MARKER')
        self.assertFalse(self.classify(observation=row))
        # Even a separate insertion anywhere in the frozen file invalidates the whole-file binding.
        self.assertFalse(self.classify(observation=dict(self.observation, file_sha256='0' * 64)))

    def test_missing_provenance_remains_finding(self):
        self.assertFalse(self.classify(provenance=None))
        proof = copy.deepcopy(PROVENANCE)
        del proof['revision']
        self.assertFalse(self.classify(provenance=proof))

    def test_mutable_or_unbound_provenance_remains_finding(self):
        for field, value in (('revision', 'main'), ('sha256', '0' * 64), ('purpose', 'authentication')):
            with self.subTest(field=field):
                self.assertFalse(self.classify(provenance=dict(PROVENANCE, **{field: value})))

    def test_other_detector_remains_finding(self):
        self.assertFalse(self.classify(observation=dict(self.observation, rule='private-key')))


if __name__ == '__main__':
    unittest.main(verbosity=2)
