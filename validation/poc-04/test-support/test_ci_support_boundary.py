"""TEST-only regression proof for the final, non-diagnostic CI support boundary."""
import ast
import importlib.util
from pathlib import Path
import re
import shlex
import subprocess
import tempfile
import unittest
import zipfile

ROOT = Path(__file__).resolve().parents[3]
SUPPORT = ROOT / 'validation/poc-04/test-support'
BOOTSTRAP = ROOT / 'validation/poc-04/scripts/phase1-ci.py'
ACCEPTED_COMMIT = 'ae9bd1f53a7e3e8612f2f4cd9f778e27d7cdb0db'
CORE = ('PinnedImages', 'RunOwnedPostgreSQLContainer', 'RunResources')
SERVICES = ('org.testcontainers.core.CreateContainerCmdModifier',
            'org.testcontainers.utility.ImageNameSubstitutor')
DIAGNOSTIC = 'Transport' + 'DiagnosticDriver'
spec = importlib.util.spec_from_file_location('phase1_ci_support_boundary', BOOTSTRAP)
ci = importlib.util.module_from_spec(spec)
spec.loader.exec_module(ci)


def checkout_contract(source):
    """Inspect the existing workflow's checkout scalar mapping, failing on ambiguity."""
    lines = source.splitlines()
    checkouts = []
    for number, line in enumerate(lines):
        match = re.fullmatch(r'( +)uses: (actions/checkout@[^\s]+)', line)
        if match:
            checkouts.append((number, len(match.group(1)), match.group(2)))
    if len(checkouts) != 1:
        raise ValueError('Exactly one checkout step is required')
    number, indentation, action = checkouts[0]
    end = len(lines)
    for position in range(number + 1, len(lines)):
        line = lines[position]
        if line.strip() and not line.lstrip().startswith('#'):
            if len(line) - len(line.lstrip(' ')) < indentation:
                end = position
                break
    inputs = [position for position in range(number + 1, end)
              if lines[position] == ' ' * indentation + 'with:']
    if len(inputs) != 1:
        raise ValueError('Exactly one checkout input mapping is required')
    values = {}
    for line in lines[inputs[0] + 1:end]:
        if not line.strip() or line.lstrip().startswith('#'):
            continue
        match = re.fullmatch(r' {' + str(indentation + 2) + r'}([a-z][a-z-]*): (.+)', line)
        if not match or match.group(1) in values:
            raise ValueError('Unsupported or duplicate checkout input')
        values[match.group(1)] = match.group(2)
    expected = {
        'ref': '${{ github.event.pull_request.head.sha || github.sha }}',
        'persist-credentials': 'false',
        'fetch-depth': '0',
    }
    if action != 'actions/checkout@v7.0.1' or any(values.get(key) != value
                                                 for key, value in expected.items()):
        raise ValueError('Checkout must retain the reviewed revision and full-history contract')
    return {'uses': action, 'with': values}


def hosted_runtime_workflow_contract(source):
    """Inspect the narrow runtime lifecycle steps without executing their commands."""
    lines = source.splitlines()
    starts = [(position, match.group(1)) for position, line in enumerate(lines)
              if (match := re.fullmatch(r'      - name: (.+)', line))]
    if len({name for _, name in starts}) != len(starts):
        raise ValueError('Workflow step names must be unique')
    steps = {}
    order = []
    for number, (position, name) in enumerate(starts):
        end = starts[number + 1][0] if number + 1 < len(starts) else len(lines)
        steps[name] = lines[position + 1:end]
        order.append(name)

    def scalar(name, field):
        values = [line[len('        ' + field + ': '):] for line in steps[name]
                  if line.startswith('        ' + field + ': ')]
        if len(values) != 1:
            raise ValueError('Runtime lifecycle scalar missing or ambiguous: ' + field)
        return values[0]

    def command(name):
        if scalar(name, 'run') != '|':
            raise ValueError('Runtime command must use an explicit literal block')
        body = steps[name][steps[name].index('        run: |') + 1:]
        if any(line.strip() and not line.startswith('          ') for line in body):
            raise ValueError('Unsupported runtime command block')
        return shlex.split('\n'.join(line[10:] for line in body).replace('\\\n', ''))

    provision = 'Provision pinned hosted Docker runtime'
    bootstrap = 'Run disposable Phase-1 verification'
    teardown = 'Stop job-owned hosted Docker runtime'
    upload = 'Preserve external hosted Docker runtime evidence'
    required = ('Check out repository', 'Set up Java 21', provision,
                'Verify Gradle wrapper', bootstrap,
                'Prove TEST service-loader isolation in deployable artifacts', teardown, upload)
    if any(name not in steps for name in required):
        raise ValueError('Hosted runtime lifecycle step missing')
    if [order.index(name) for name in required] != sorted(order.index(name) for name in required):
        raise ValueError('Hosted runtime lifecycle order changed')
    if '    runs-on: ubuntu-24.04' not in lines:
        raise ValueError('Hosted runtime requires the reviewed runner family')
    if 'permissions:\n  contents: read\n' not in source:
        raise ValueError('Workflow permissions changed')
    helper = ['python3', '-B', 'validation/poc-04/scripts/hosted-docker-runtime.py']
    runtime_root = '$RUNNER_TEMP/vra-poc04-docker-${GITHUB_RUN_ID}-${GITHUB_RUN_ATTEMPT}'
    if command(provision) != helper + ['provision', '--root', runtime_root,
                                     '--github-env', '$GITHUB_ENV', '--github-path', '$GITHUB_PATH']:
        raise ValueError('Provisioning must publish only the verified external runtime contract')
    if command(teardown) != helper + ['teardown', '--root', runtime_root]:
        raise ValueError('Teardown must use the same exact job-owned runtime root')
    if scalar(provision, 'shell') != 'bash' or scalar(teardown, 'shell') != 'bash':
        raise ValueError('Runtime lifecycle requires the explicit reviewed shell')
    if scalar(teardown, 'if') != 'always()' or scalar(upload, 'if') != 'always()':
        raise ValueError('Teardown and runtime evidence preservation must run after failure')
    if scalar(upload, 'uses') != ('actions/upload-artifact@'
                                 'ea165f8d65b6e75b540449e92b4886f43607fa02 # v4.6.2'):
        raise ValueError('Runtime evidence action pin changed')
    expected_path = ('          path: ${{ runner.temp }}/vra-poc04-docker-'
                     '${{ github.run_id }}-${{ github.run_attempt }}/evidence/')
    if expected_path not in steps[upload]:
        raise ValueError('Runtime evidence must remain outside repository candidate input')
    if source.count('python3 -B validation/poc-04/scripts/phase1-ci.py') != 1:
        raise ValueError('There must be exactly one authoritative Phase-1 bootstrap')
    if '--gate full' not in '\n'.join(steps[bootstrap]):
        raise ValueError('The existing full Phase-1 gate must remain authoritative')
    for forbidden in ('continue-on-error:', '|| true', 'docker version',
                      'systemctl start docker', 'systemctl restart docker'):
        if forbidden in source:
            raise ValueError('Unexpected fallback or failure suppression in workflow')
    return {'root': runtime_root, 'provision': command(provision), 'teardown': command(teardown)}


class HostedRuntimeWorkflowContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.source = (ROOT / '.github/workflows/backend-ci.yml').read_text()

    def test_workflow_binds_one_external_pinned_runtime_lifecycle(self):
        contract = hosted_runtime_workflow_contract(self.source)
        self.assertTrue(contract['root'].startswith('$RUNNER_TEMP/'))
        self.assertEqual(contract['provision'][3], 'provision')
        self.assertEqual(contract['teardown'][3], 'teardown')

    def test_repository_runtime_root_is_rejected(self):
        source = self.source.replace('$RUNNER_TEMP/vra-poc04-docker-',
                                     'validation/poc-04/evidence/runtime-')
        with self.assertRaises(ValueError):
            hosted_runtime_workflow_contract(source)

    def test_privileged_whole_helper_execution_is_rejected(self):
        source = self.source.replace('python3 -B validation/poc-04/scripts/hosted-docker-runtime.py',
                                     'sudo python3 -B validation/poc-04/scripts/hosted-docker-runtime.py')
        with self.assertRaises(ValueError):
            hosted_runtime_workflow_contract(source)

    def test_success_only_teardown_is_rejected(self):
        source = self.source.replace('      - name: Stop job-owned hosted Docker runtime\n'
                                     '        if: always()',
                                     '      - name: Stop job-owned hosted Docker runtime\n'
                                     '        if: success()')
        with self.assertRaises(ValueError):
            hosted_runtime_workflow_contract(source)

    def test_repository_runtime_evidence_upload_is_rejected(self):
        source = self.source.replace('path: ${{ runner.temp }}/vra-poc04-docker-',
                                     'path: validation/poc-04/evidence/runtime-')
        with self.assertRaises(ValueError):
            hosted_runtime_workflow_contract(source)

    def test_second_bootstrap_execution_is_rejected(self):
        source = self.source + '\n          python3 -B validation/poc-04/scripts/phase1-ci.py --gate full\n'
        with self.assertRaises(ValueError):
            hosted_runtime_workflow_contract(source)

    def test_system_daemon_fallback_is_rejected(self):
        source = self.source + '\n          systemctl restart docker\n'
        with self.assertRaises(ValueError):
            hosted_runtime_workflow_contract(source)


class CheckoutHistoryContractTest(unittest.TestCase):
    SYNTHETIC_WORKFLOW = '''steps:
  - name: Check out repository
    uses: actions/checkout@v7.0.1
    with:
      ref: ${{ github.event.pull_request.head.sha || github.sha }}
      persist-credentials: false
      fetch-depth: 0
'''

    def test_workflow_uses_pinned_full_history_without_persisted_credentials(self):
        try:
            contract = checkout_contract((ROOT / '.github/workflows/backend-ci.yml').read_text())
        except ValueError as error:
            self.fail(str(error))
        self.assertEqual(contract['uses'], 'actions/checkout@v7.0.1')
        self.assertEqual(contract['with']['fetch-depth'], '0')
        self.assertEqual(contract['with']['persist-credentials'], 'false')

    def test_omitted_fetch_depth_is_rejected(self):
        source = self.SYNTHETIC_WORKFLOW.replace('      fetch-depth: 0\n', '')
        with self.assertRaises(ValueError):
            checkout_contract(source)

    def test_shallow_fetch_depth_is_rejected(self):
        source = self.SYNTHETIC_WORKFLOW.replace('fetch-depth: 0', 'fetch-depth: 1')
        with self.assertRaises(ValueError):
            checkout_contract(source)

    def test_persisted_credentials_are_rejected(self):
        source = self.SYNTHETIC_WORKFLOW.replace('persist-credentials: false',
                                               'persist-credentials: true')
        with self.assertRaises(ValueError):
            checkout_contract(source)

    def test_changed_checkout_pin_is_rejected(self):
        source = self.SYNTHETIC_WORKFLOW.replace('actions/checkout@v7.0.1',
                                               'actions/checkout@v7.0.0')
        with self.assertRaises(ValueError):
            checkout_contract(source)


class CiSupportBoundaryTest(unittest.TestCase):
    def test_compiler_includes_exact_core_sources(self):
        source = (SUPPORT / 'runner.init.gradle').read_text()
        match = re.search(r'source = root\.files\(\[(.*?)\]\s*\.collect', source, re.S)
        self.assertIsNotNone(match)
        self.assertEqual(re.findall(r"'([^']+\.java)'", match.group(1)),
                         [name + '.java' for name in CORE])

    def test_custom_compiler_retains_explicit_java_21(self):
        source = (SUPPORT / 'runner.init.gradle').read_text()
        for required in ('javaCompiler.set(migration.javaToolchains.compilerFor',
                         'languageVersion.set(JavaLanguageVersion.of(21))',
                         'sourceCompatibility = JavaVersion.VERSION_21.toString()',
                         'targetCompatibility = JavaVersion.VERSION_21.toString()',
                         'options.release.set(21)'):
            with self.subTest(required=required):
                self.assertIn(required, source)

    def test_run_owned_fixture_matches_accepted_non_diagnostic_bytes(self):
        relative = 'validation/poc-04/test-support/src/dev/vra/poc04/external/RunOwnedPostgreSQLContainer.java'
        accepted = subprocess.check_output(['git', '--no-optional-locks', 'show',
                                            ACCEPTED_COMMIT + ':' + relative], cwd=ROOT)
        self.assertEqual((ROOT / relative).read_bytes(), accepted)

    def test_diagnostic_implementation_absent(self):
        self.assertFalse((SUPPORT / 'src/dev/vra/poc04/external' / (DIAGNOSTIC + '.java')).exists())

    def test_runner_has_no_transport_environment_or_hook_wiring(self):
        source = (SUPPORT / 'runner.init.gradle').read_text()
        for forbidden in (DIAGNOSTIC, 'VRA_POC04_TRANSPORT_DIAGNOSTIC',
                          'VRA_POC04_DIAGNOSTIC_CLASSPATH'):
            self.assertNotIn(forbidden, source)

    def test_bootstrap_has_no_executable_transport_gate(self):
        source = BOOTSTRAP.read_text()
        tree = ast.parse(source)
        self.assertFalse(any(isinstance(node, (ast.FunctionDef, ast.AsyncFunctionDef))
                             and node.name == 'transport' for node in ast.walk(tree)))
        for forbidden in ('VRA_POC04_TRANSPORT_DIAGNOSTIC', 'VRA_POC04_DIAGNOSTIC_CLASSPATH'):
            self.assertNotIn(forbidden, source)
        gate_options = [node for node in ast.walk(tree) if isinstance(node, ast.Call)
                        and isinstance(node.func, ast.Attribute) and node.func.attr == 'add_argument'
                        and node.args and isinstance(node.args[0], ast.Constant)
                        and node.args[0].value == '--gate']
        self.assertEqual(len(gate_options), 1)
        choices = next(keyword.value for keyword in gate_options[0].keywords if keyword.arg == 'choices')
        self.assertEqual(ast.literal_eval(choices), ('scanner', 'compile', 'boundary', 'full'))

    def prove_artifact(self, extra=(), missing=()):
        # Synthetic, non-executable entries exercise the actual bootstrap's exact-content validator.
        with tempfile.TemporaryDirectory(prefix='vra-poc04-support-test-') as temporary:
            directory = Path(temporary)
            artifact = directory / 'support.jar'
            with zipfile.ZipFile(artifact, 'w') as jar:
                jar.writestr('META-INF/MANIFEST.MF', 'Manifest-Version: 1.0\n')
                for name in CORE:
                    entry = 'dev/vra/poc04/external/' + name + '.class'
                    if entry not in missing:
                        jar.writestr(entry, b'TEST_ONLY_SYNTHETIC_CLASS_ENTRY')
                for name in SERVICES:
                    entry = 'META-INF/services/' + name
                    if entry not in missing:
                        jar.writestr(entry, (SUPPORT / 'resources' / entry).read_bytes())
                for entry in extra:
                    jar.writestr(entry, b'TEST_ONLY_SYNTHETIC_UNAUTHORIZED_ENTRY')
            run = object.__new__(ci.Run)
            run.env = {'VRA_POC04_EXTERNAL_TC_JAR': str(artifact)}
            run.out = directory
            run.support_proof()
            self.assertTrue((directory / 'support-artifact.json').is_file())

    def test_support_validator_accepts_exact_core_and_services(self):
        self.prove_artifact()

    def test_support_validator_denies_diagnostic_class(self):
        with self.assertRaises(RuntimeError):
            self.prove_artifact(extra=('dev/vra/poc04/external/' + DIAGNOSTIC + '.class',))

    def test_support_validator_denies_missing_core_class(self):
        with self.assertRaises(RuntimeError):
            self.prove_artifact(missing=('dev/vra/poc04/external/RunResources.class',))

    def test_support_validator_denies_unapproved_service_provider(self):
        with self.assertRaises(RuntimeError):
            self.prove_artifact(extra=('META-INF/services/TEST_ONLY_UNAPPROVED_PROVIDER',))


if __name__ == '__main__':
    unittest.main(verbosity=2)
