#!/usr/bin/env python3
"""Admin-operated TEST-only Phase-1 mount probes; no observer or parser.
Current TEST support; execute only after the same run runtime regression has passed.
All create receipts, physical database bindings and teardown use run-harness.py.
Never persist credential inputs, raw container environment, or collector content.
"""
import datetime, hashlib, json, os, re, secrets, select, subprocess, sys, tempfile, time, uuid
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
HARNESS = ROOT/'validation/poc-04/scripts/run-harness.py'
ENDPOINT = 'unix:///Users/siwakornbundi/.colima/vra-poc04-test/docker.sock'
HEAD = '01256d96d85823740a8d6c3a3f99346afbdbef56'
TOOL_SHA = '5814b0ee6eb022acfb458b21b8a009592ea627c3d371cfc7b0963595c05892d8'
PROFILE_SHA = '53675defaacbcb1a36f59a3760a61142b16199d4e8a513e6ac63e44fd9505d68'
PG_REF = 'docker.io/library/postgres@sha256:d74eeac9a635390a49bc21bd49fccd973de707e2a53a76ac49b552b8712ec46f'
ALPINE_REF = 'docker.io/library/alpine@sha256:8fc3dacfb6d69da8d44e42390de777e48577085db99aa4e4af35f483eb08b989'
PLATFORM = 'linux/arm64/v8'
EXPECTED = {
  'validation/poc-04/db/COLLECTOR_TEST.md': '09117a00f800c91748017234be2829a86828895ff26a119beb618327fddd50c1',
  'validation/poc-04/db/collector-test.compose.yaml': 'db3c7dec5fa8b1f6e04512e30a4d6dc9b58e11316c53d3301b82a0712db62c47',
  'validation/poc-04/db/collector-test-entrypoint.sh': '90e6d263e9c121cb2b92dd80e45f49fef71452d5c6f52b1695752d1725fe73d0',
  'validation/poc-04/db/postgresql-collector-test.conf': 'ba1f292521a67965fc3b8f03bf8d39cac460852cdb33f2d8e51d6809036440db',
  'validation/poc-04/db/bootstrap-security-roles.sql': '976cf704687e5bf005de8943c706fdd996d801172c112d640c993e0b193407b5',
  'validation/poc-04/tooling/image-pins.json': '340b51d2910f023eef2f7b8a0a4c4065c4baa8d95b2a95c5aaa6c76b888d123a'
}
ROLES = ['vra_runtime','vra_outbox_worker','vra_reconciliation_worker',
 'vra_async_operator','vra_projection_rebuilder','vra_async_observer',
 'vra_factor_fixture','vra_audit_evidence_reader','vra_security_telemetry_observer']
SETTINGS = {
 'logging_collector':'on','log_destination':'jsonlog','lc_messages':'C',
 'log_error_verbosity':'verbose','log_min_messages':'error',
 'log_min_error_statement':'error','log_statement':'none','log_duration':'off',
 'log_min_duration_statement':'-1','log_min_duration_sample':'-1',
 'log_transaction_sample_rate':'0','log_parameter_max_length':'0',
 'log_parameter_max_length_on_error':'0','log_truncate_on_rotation':'off',
 'log_directory':'/var/log/vra-poc04-collector','log_file_mode':'0640',
 'log_rotation_age':'1d','log_rotation_size':'0'
}

def check(condition, message):
    if not condition: raise AssertionError(message)


def normalized_server_secret_target(entries):
    # Compose may emit the default filename, its absolute container path, or
    # omit the default target. Only this reviewed credential destination is valid.
    source = 'postgres_admin_password'
    destination = '/run/secrets/' + source
    check(isinstance(entries, list) and len(entries) == 1,
          'profile server credential entry count/shape differs')
    entry = entries[0]
    check(isinstance(entry, dict) and set(entry) in ({'source'}, {'source', 'target'}),
          'profile server credential entry fields differ')
    check(entry['source'] == source, 'profile server credential source differs')
    if 'target' in entry:
        check(isinstance(entry['target'], str) and entry['target'] in (source, destination),
              'profile server credential target differs')
    return destination

# The root execution coordinator creates this restricted state once source bytes
# are stable. A state or observation from another run confers no deletion authority.
state_path = Path(os.environ['VRA_POC04_RUN_STATE'])
check(state_path.is_absolute() and not state_path.is_symlink(), 'invalid run-state path')
check(state_path.parent.stat().st_mode & 0o077 == 0, 'run-state parent is not restricted')
check(state_path.stat().st_mode & 0o077 == 0, 'run-state is not restricted')
state = json.loads(state_path.read_text())
run_id = state['run_id']
check(str(uuid.UUID(run_id)) == run_id and uuid.UUID(run_id).version == 4, 'run ID is not canonical UUIDv4')
check(os.environ.get('VRA_POC04_RUN_ID') == run_id, 'authorized current run ID mismatch')
check(os.environ.get('DOCKER_HOST') == state['docker_host'] == ENDPOINT, 'isolated Docker endpoint mismatch')
check(state['source_head'] == HEAD and state['tool_manifest_sha256'] == TOOL_SHA, 'source/tool authority mismatch')
check(re.fullmatch('[0-9a-f]{64}', state['source_content_sha256']) is not None, 'invalid source-content binding')
check(os.environ.get('TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE') == '/var/run/docker.sock', 'socket override mismatch')
check(os.environ.get('TESTCONTAINERS_HOST_OVERRIDE') == '127.0.0.1', 'host override mismatch')
check(os.environ.get('VRA_POC04_RUNTIME_REGRESSION_PASSED') == run_id, 'runtime regression PASS execution guard absent')
evidence_root = Path(os.environ['VRA_POC04_EVIDENCE_DIRECTORY'])
check(evidence_root.is_absolute() and evidence_root.resolve().is_relative_to(ROOT/'validation/poc-04/evidence/phase1'), 'evidence path is outside Phase-1 evidence')
out = evidence_root/'collector-boundary'
out.mkdir(exist_ok=False)
env = os.environ.copy()
commands, resources, creation_receipts = [], {'containers':{}, 'volumes':{}, 'networks':{}}, []
credential_address = None
secret_dir = secret_file = override = None
passwords = {}
allocation = None
labels = {}
summary = {'scope':'POC-04 Phase-1 collector preparation only', 'run_id':run_id,
 'source_content_sha256':state['source_content_sha256'], 'source_head':HEAD, 'tool_manifest_sha256':TOOL_SHA,
 'docker_endpoint':ENDPOINT, 'status':'RUNNING', 'phase2_started':False,
 'runtime_worker_OS_identity_claim':'No application/worker deployment UID was invented. Reserved reader9404 and synthetic nonmember9405 are temporary TEST probes; actual prepared profile gives runtime/worker no source mount.',
 'business_audit_grant_proof':'Not remigrated in this collector-only DB. Refer to this run fresh/populated V4 catalog and actual-credential grants/denials, bound to the same source bytes.',
 'no_raw_logs_or_credentials_persisted':True}


def write(name, data):
    (out/name).write_text(json.dumps(data,indent=2,sort_keys=True)+'\n')

def invoke(args, data=None, timeout=120, optional=False):
    r = subprocess.run(args,cwd=ROOT,env=env,input=data,text=True,capture_output=True,timeout=timeout)
    commands.append({'command':args,'exit_status':r.returncode,
      'input':'runtime stdin only; omitted' if data is not None else None})
    if not optional: check(r.returncode==0,'command failed (sanitized): '+' '.join(args[:4]))
    return r

def docker(args, **kwargs): return invoke(['docker']+args, **kwargs)

def inspect(kind, identity, optional=False):
    r=docker([kind,'inspect',identity] if kind!='container' else ['inspect',identity],optional=optional)
    if r.returncode:
        check(optional and ('no such' in r.stderr.lower() or 'not found' in r.stderr.lower()), 'Docker inspection failed unexpectedly')
        return None
    return json.loads(r.stdout)[0]

def attest():
    info=json.loads(docker(['info','--format','{{json .}}']).stdout)
    check(info.get('ID')==state['daemon_id'],'dedicated daemon ID mismatch')
    check(info.get('Name')=='colima-vra-poc04-test','unexpected daemon name')
    check(info.get('OSType')=='linux' and info.get('Architecture') in ['aarch64','arm64'],'unexpected platform')
    return {k:info.get(k) for k in ['ID','Name','ServerVersion','OSType','Architecture','DefaultAddressPools']}

def bound(raw, kind):
    actual=raw.get('Config',{}).get('Labels',{}) if kind=='container' else raw.get('Labels',{})
    check(all(actual.get(k)==v for k,v in labels.items()),'exact run/resource label mismatch')
    if kind=='container':
        return {'id':raw['Id'],'name':raw['Name'],'created':raw['Created'],
          'image_reference':raw['Config']['Image'],'image_identity':raw['Image'],
          'manifest_descriptor':raw.get('ImageManifestDescriptor'),
          'state':{**{k:raw.get('State',{}).get(k) for k in ['Status','Running','ExitCode']},'Health':{'Status':raw.get('State',{}).get('Health',{}).get('Status')}},
          'labels':actual,'mounts':[{k:m.get(k) for k in ['Type','Name','Source','Destination','RW']} for m in raw.get('Mounts',[])],
          'user':raw['Config'].get('User'),'network_mode':raw['HostConfig'].get('NetworkMode'),
          'mount_requests':raw['HostConfig'].get('Mounts',[]),
          'privileged':raw['HostConfig'].get('Privileged'),
          'read_only_root':raw['HostConfig'].get('ReadonlyRootfs'),
          'cap_add':raw['HostConfig'].get('CapAdd'),'cap_drop':raw['HostConfig'].get('CapDrop'),
          'security_options':raw['HostConfig'].get('SecurityOpt'),
          'port_bindings':raw['HostConfig'].get('PortBindings'),
          'published_ports':raw['NetworkSettings'].get('Ports'),
          'networks':{n:{k:v.get(k) for k in ['NetworkID','IPAddress']} for n,v in raw['NetworkSettings'].get('Networks',{}).items()}}
    return {k:raw.get(k) for k in (['Name','CreatedAt','Driver','Mountpoint','Scope','Labels'] if kind=='volume' else ['Id','Name','Created','Driver','Scope','Labels','IPAM'])}

def remember(kind, raw):
    safe=bound(raw,kind)
    identity=raw['Name'] if kind=='volume' else raw['Id']
    resources[kind+'s'][identity]=safe
    write('resources.json',resources)
    return safe

def sql(admin_id, statement, role=None, expected_state=None):
    base=['exec','-i']
    if role is None:
        args=base+['--user','postgres',admin_id,'psql','-X','-qAt','-v','ON_ERROR_STOP=1','-d','vra_poc01']
        data=statement
    else:
        args=base+[admin_id,'sh','-c',
          'IFS= read -r PGPASSWORD; export PGPASSWORD; exec psql -X -qAt -w -h "$2" -U "$1" -d vra_poc01 -v ON_ERROR_STOP=1','credential-probe',role,credential_address]
        data=passwords[role]+'\n'+statement
    r=docker(args,data='\set VERBOSITY verbose\n'+data if role is None else passwords[role]+'\n'+'\set VERBOSITY verbose\n'+statement,optional=True)
    if expected_state:
        states=re.findall(r'(?:ERROR|FATAL):\s+([0-9A-Z]{5}):',r.stderr)
        check(r.returncode!=0 and expected_state in states,'SQL denial or SQLSTATE mismatch')
        return {'role':role,'expected_sqlstate':expected_state,'observed_sqlstates':states,'exit_status':r.returncode,'denial':'PASS'}
    check(r.returncode==0,'SQL command failed; details suppressed')
    return r.stdout.strip()

def harness(args, optional=False):
    result = invoke([sys.executable, str(HARNESS)] + args, optional=optional)
    payload = json.loads(result.stdout) if result.stdout.strip() else None
    if not optional:
        check(payload is not None and not payload.get('denied'), 'run-harness refused operation')
    return result, payload


def create_resource(kind, args):
    # Only this successful creation call may register the returned exact identity.
    # Never discover matching resources and treat observation as a create receipt.
    result = docker(args)
    identity = result.stdout.strip()
    check(bool(identity) and '\n' not in identity, 'Docker creation did not return one exact identity')
    if kind != 'volume':
        check(re.fullmatch('[0-9a-f]{64}', identity) is not None, 'noncanonical created resource ID')
    receipt = {'kind':kind, 'id':identity, 'database_instance_id':instance_id,
               'logical_name':allocation['logical_name'], 'creator_registered':False}
    creation_receipts.append(receipt)
    write('creation-receipts.json', creation_receipts)
    _, registered = harness(['register', '--kind', kind, '--id', identity, '--instance', instance_id])
    check(registered['id'] == identity and registered['authorized_run_id'] == run_id and
          registered['database_instance_id'] == instance_id, 'creator registration binding mismatch')
    receipt['creator_registered'] = True
    receipt['ledger_entry'] = registered
    write('creation-receipts.json', creation_receipts)
    remember(kind, inspect(kind, identity))
    return identity


def label_args():
    return [part for key, value in labels.items() for part in ('--label', key + '=' + value)]


def start_healthy(identity):
    # Wait on Docker health events rather than sleep-based correctness timing.
    # --since replays events if start wins the initial event subscription race.
    since = datetime.datetime.now(datetime.timezone.utc).isoformat()
    args = ['docker', 'events', '--since', since, '--filter', 'container=' + identity,
            '--filter', 'event=health_status', '--format', '{{json .}}']
    watcher = subprocess.Popen(args, cwd=ROOT, env=env, stdout=subprocess.PIPE,
                               stderr=subprocess.DEVNULL)
    events = []
    try:
        docker(['start', identity])
        deadline = time.monotonic() + 120
        while True:
            health = inspect('container', identity).get('State', {}).get('Health', {}).get('Status')
            if health == 'healthy':
                summary['postgres_health'] = {'status':'healthy', 'bounded_healthcheck':True,
                                               'safe_events':events}
                break
            check(health != 'unhealthy', 'Docker PostgreSQL healthcheck failed')
            remaining = deadline - time.monotonic()
            check(remaining > 0 and watcher.poll() is None, 'Docker PostgreSQL health event timeout')
            ready, _, _ = select.select([watcher.stdout], [], [], remaining)
            check(bool(ready), 'Docker PostgreSQL health event timeout')
            line = watcher.stdout.readline()
            check(bool(line), 'Docker PostgreSQL health event stream ended')
            event = json.loads(line)
            event_id = event.get('Actor', {}).get('ID', event.get('id'))
            check(event_id == identity, 'health event exact container ID mismatch')
            action = event.get('Action', event.get('status'))
            check(action in ('health_status: healthy', 'health_status: unhealthy'), 'unexpected health event')
            events.append({'container_id':event_id, 'action':action, 'time_nano':event.get('timeNano')})
    finally:
        watcher.terminate()
        try:
            watcher.wait(timeout=5)
        except subprocess.TimeoutExpired:
            watcher.kill()
            watcher.wait(timeout=5)
        commands.append({'command':args, 'exit_status':watcher.returncode,
                         'observer_process':False, 'purpose':'Docker administrative readiness only',
                         'intentional_event_subscription_termination':True})
        watcher.stdout.close()

config={}
try:
    summary['preflight_daemon']=attest()
    summary['current_support_sha256']={str(Path(__file__).resolve().relative_to(ROOT)):hashlib.sha256(Path(__file__).read_bytes()).hexdigest(), str(HARNESS.relative_to(ROOT)):hashlib.sha256(HARNESS.read_bytes()).hexdigest()}
    for path, expected in EXPECTED.items():
        check(hashlib.sha256((ROOT/path).read_bytes()).hexdigest()==expected, 'collector/source byte drift: '+path)
    check(hashlib.sha256((ROOT/'validation/poc-04/tooling/tool-manifest.json').read_bytes()).hexdigest()==TOOL_SHA, 'tool manifest byte drift')
    check(invoke(['git','rev-parse','HEAD']).stdout.strip()==HEAD, 'source HEAD changed')
    check(docker(['ps','-aq','--no-trunc']).stdout.strip()=='', 'collector requires no current containers')
    _, allocation = harness(['allocate','postgres-authority'])
    instance_id = allocation['database_instance_id']
    labels = dict(allocation['labels'])
    labels['dev.vra.purpose'] = 'phase1-collector-mount-proof'
    project = 'vra-poc04-'+run_id+'-collector-'+str(allocation['ordinal'])
    summary.update(database_instance_id=instance_id, logical_database_instance=allocation['logical_name'], allocation=allocation)
    env.update(POC04_RUN_ID=run_id, POC04_DATABASE_INSTANCE_ID=instance_id,
               POC04_SOURCE_HEAD=HEAD, POC04_SOURCE_CONTENT_SHA256=state['source_content_sha256'],
               POC04_TOOL_MANIFEST_SHA256=TOOL_SHA)
    profile_path = Path.home()/'.colima/vra-poc04-test/colima.yaml'
    check(hashlib.sha256(profile_path.read_bytes()).hexdigest()==PROFILE_SHA, 'saved isolated profile byte drift')
    check(re.search(r'^mounts:\s*\[\]\s*$',profile_path.read_text(),re.M) is not None, 'saved profile mount configuration differs')
    secret_dir = Path(tempfile.mkdtemp(prefix='vra-poc04-collector-'+run_id+'-secrets-',dir=str(profile_path.parent)))
    secret_dir.chmod(0o700)
    secret_file = secret_dir/'postgres-admin-password'
    with os.fdopen(os.open(secret_file,os.O_WRONLY|os.O_CREAT|os.O_EXCL,0o600),'w') as stream:
        stream.write(secrets.token_urlsafe(48)+'\n')
    check(secret_file.stat().st_mode & 0o777 == 0o600, 'ephemeral credential file mode is not0600')
    summary['profile_mount_binding']={'profile_config':str(profile_path), 'profile_sha256':PROFILE_SHA,
      'saved_mounts':[], 'effective_default':'home directory mounted writable via configured virtiofs',
      'ephemeral_secret_parent':'isolated profile directory outside repository',
      'secret_directory_mode':'0700','secret_file_mode':'0600'}
    env['POC04_ADMIN_PASSWORD_FILE'] = str(secret_file)
    passwords = {role:secrets.token_urlsafe(48) for role in ROLES+['vra_migrator']}
    pins=json.loads((ROOT/'validation/poc-04/tooling/image-pins.json').read_text())
    pin_map={r['id']:r for r in pins['records']}
    expected_platform={}
    for key,ref in [('postgresql_test',PG_REF),('testcontainers_tinyimage',ALPINE_REF)]:
        record=pin_map[key]
        check(record['execution_identifier']==ref,'execution reference differs from reviewed pin')
        chosen=[m for m in record['platform_manifests'] if m['platform']=={'os':'linux','architecture':'arm64','variant':'v8'}]
        check(len(chosen)==1,'unrecorded execution platform')
        chosen=chosen[0]
        raw=docker(['buildx','imagetools','inspect',ref.split('@')[0]+'@'+chosen['digest'],'--raw']).stdout.encode()
        # These raw JSON objects are nonsecret immutable registry metadata.
        check('sha256:'+hashlib.sha256(raw).hexdigest()==chosen['digest'],'immutable platform manifest hash mismatch')
        manifest=json.loads(raw)
        check(manifest['config']['digest']==chosen['config_digest'],'manifest-to-config linkage differs')
        expected_platform[key]=chosen
        image=json.loads(docker(['image','inspect','--platform',PLATFORM,ref]).stdout)[0]
        descriptor=image.get('Descriptor',{})
        check(descriptor.get('digest')==chosen['digest'],'daemon selected manifest mismatch')
        summary.setdefault('image_provenance',{})[key]={'execution_reference':ref,'execution_platform':PLATFORM,
          'platform_manifest_digest':chosen['digest'],'config_digest':manifest['config']['digest'],'daemon_descriptor':descriptor}
    override=state_path.parent/('collector-local-platform-'+str(allocation['ordinal'])+'.override.json')
    override.write_text(json.dumps({'services':{'postgres':{'platform':PLATFORM,
      'labels':labels,'healthcheck':{'test':['CMD-SHELL','pg_isready -q -h 127.0.0.1 -U postgres -d vra_poc01'],
      'interval':'1s','timeout':'1s','retries':120,'start_period':'1s'}}},
      'volumes':{name:{'labels':labels} for name in ['postgres_data','collector_source']},
      'networks':{'test':{'labels':labels}}},indent=2)+'\n')
    compose=['compose','--project-name',project,'-f',str(ROOT/'validation/poc-04/db/collector-test.compose.yaml'),'-f',str(override),'--profile','poc04-collector-test']
    config=json.loads(docker(compose+['config','--format','json']).stdout)
    service=config['services']['postgres']
    secret_target=normalized_server_secret_target(service.get('secrets'))
    check(isinstance(config.get('secrets'),dict) and set(config['secrets'])=={'postgres_admin_password'}, 'profile credential declarations differ')
    secret=config['secrets']['postgres_admin_password']
    check(isinstance(secret,dict) and set(secret) in ({'file'},{'file','name'}), 'profile credential source kind/fields differ')
    check(secret.get('file')==str(secret_file), 'profile external credential file binding differs')
    if 'name' in secret:
        check(isinstance(secret['name'],str) and bool(secret['name']), 'profile credential logical name shape differs')
    write('normalized-secrets-model.json',{
      'service_names':list(config['services']),
      'services':{'postgres':{'secrets_node_type':'list','entry_count':1,
        'entries':[{'node_type':'dict','keys':sorted(service['secrets'][0]),
          'source':service['secrets'][0]['source'],
          'target_present':'target' in service['secrets'][0],
          'target':service['secrets'][0].get('target'),
          'effective_target':secret_target}]}},
      'top_level_secrets':{'postgres_admin_password':{'structural_keys':sorted(secret),
        'source_kind':'file','file_equals_exact_ephemeral_input':True}},
      'secret_contents_captured':False,'environment_values_captured':False})
    summary['normalized_credential_boundary']={'authorized_service':'postgres',
      'source':'postgres_admin_password','effective_target':secret_target,
      'other_services_present':set(config['services'])!={'postgres'},'source_kind':'external ephemeral file'}
    check(service['image']==PG_REF and service['platform']==PLATFORM,'resolved profile execution mismatch')
    check(not service.get('ports'),'collector profile publishes a port')
    check(set(config['services'])=={'postgres'},'observer/other service unexpectedly present')
    future=config.get('x-poc04-future-observer',{})
    check(set(future)=={'user','source_mount'}, 'unexpected future observer authority fields')
    check(set(future.get('source_mount',{}))=={'type','source','target','read_only','volume'}, 'unexpected future observer mount authority fields')
    check(future.get('user')=='9404:9404' and future.get('source_mount',{}).get('read_only') is True and future.get('source_mount',{}).get('volume',{}).get('nocopy') is True,'reserved future observer mount/user boundary differs')
    check(set(config['volumes'])=={'postgres_data','collector_source'} and set(config['networks'])=={'test'}, 'profile unexpected resource declarations')
    for section,kind in [('volumes','volume'),('networks','network')]:
        for entry in config[section].values():
            check(inspect(kind,entry['name'],optional=True) is None,'collector resource already existed')
    summary['resolved_profile_safe']={'project':project,'service_names':list(config['services']),
      'image':service['image'],'platform':service['platform'],'published_ports':service.get('ports',[]),
      'volume_names':{k:v['name'] for k,v in config['volumes'].items()},
      'network_names':{k:v['name'] for k,v in config['networks'].items()},
      'future_observer_extension':config.get('x-poc04-future-observer')}
    # Render the unchanged prepared profile as authority, then issue explicit
    # resource creates so each returned ID has an immediate creator registration.
    network_name=config['networks']['test']['name']
    network_id=create_resource('network', ['network','create','--driver','bridge']+label_args()+[network_name])
    for key in ['postgres_data','collector_source']:
        name=config['volumes'][key]['name']
        returned=create_resource('volume',['volume','create']+label_args()+[name])
        check(returned==name, 'created volume exact name mismatch')
    pgdata_name=config['volumes']['postgres_data']['name']
    collector_name=config['volumes']['collector_source']['name']
    expected_mounts={
      '/var/lib/postgresql/data':('volume','postgres_data',False,False),
      '/var/log/vra-poc04-collector':('volume','collector_source',False,True),
      '/etc/poc04/postgresql-collector-test.conf':('bind',str(ROOT/'validation/poc-04/db/postgresql-collector-test.conf'),True,False),
      '/etc/poc04/collector-test-entrypoint.sh':('bind',str(ROOT/'validation/poc-04/db/collector-test-entrypoint.sh'),True,False)}
    check(len(service['volumes'])==4, 'profile unexpected server mount count')
    args=['create','--pull','never','--name',project+'-postgres','--platform',PLATFORM,
          '--network',network_id,'--health-cmd','pg_isready -q -h 127.0.0.1 -U postgres -d vra_poc01',
          '--health-interval','1s','--health-timeout','1s','--health-retries','120','--health-start-period','1s']+label_args()
    for mount in service['volumes']:
        target=mount['target']
        check(target in expected_mounts, 'unapproved prepared profile server mount')
        kind, source, readonly, nocopy=expected_mounts[target]
        check(mount['type']==kind and mount['source']==source and bool(mount.get('read_only',False))==readonly and bool(mount.get('volume',{}).get('nocopy',False))==nocopy, 'profile server mount shape mismatch')
        if kind=='volume': source=config['volumes'][source]['name']
        spec='type='+kind+',src='+source+',dst='+target
        if readonly: spec+=',readonly'
        if nocopy: spec+=',volume-nocopy'
        args+=['--mount',spec]
    check(service['entrypoint']==['bash','/etc/poc04/collector-test-entrypoint.sh'] and service['command']==['postgres','-c','config_file=/etc/poc04/postgresql-collector-test.conf'], 'profile prepared server entrypoint/command differs')
    check(service['environment']=={'POSTGRES_DB':'vra_poc01','POSTGRES_USER':'postgres','POSTGRES_PASSWORD_FILE':'/run/secrets/postgres_admin_password'}, 'profile server environment differs')
    args+=['--mount','type=bind,src='+str(secret_file)+',dst='+secret_target+',readonly']
    for key, value in service['environment'].items(): args+=['--env',key+'='+value]
    args+=['--entrypoint','bash',PG_REF,'/etc/poc04/collector-test-entrypoint.sh']+service['command']
    pgid=create_resource('container',args)
    check(len(resources['containers'])==1 and len(resources['volumes'])==2 and len(resources['networks'])==1, 'collector creation resource inventory mismatch')
    start_healthy(pgid)
    pg=remember('container',inspect('container',pgid))
    check(pg['state']['Running'] and pg['state']['Health']['Status']=='healthy','PG health not ready')
    check(pg['image_reference']==PG_REF and pg['manifest_descriptor']['digest']==expected_platform['postgresql_test']['digest'],'running platform identity mismatch')
    check(not pg['privileged'] and not pg['port_bindings'],'unexpected PostgreSQL host authority/published port')
    check(all('docker.sock' not in str(m['Source']) for m in pg['mounts']),'unexpected Docker socket mount')
    password_mount=[m for m in pg['mounts'] if m['Destination']=='/run/secrets/postgres_admin_password']
    check(len(password_mount)==1 and password_mount[0]['Type']=='bind' and password_mount[0]['Source']==str(secret_file) and password_mount[0]['RW'] is False and secret_file.is_file(),'exact external credential file mount differs')
    summary['ephemeral_admin_mount_identity']='PASS; exact external runtime0600 file; server only; contents omitted'
    collector_name=config['volumes']['collector_source']['name']
    pgdata_name=config['volumes']['postgres_data']['name']
    src=inspect('volume',collector_name)
    collector_mount=[m for m in pg['mounts'] if m['Destination']=='/var/log/vra-poc04-collector']
    check(len(collector_mount)==1 and collector_mount[0]['Type']=='volume' and collector_mount[0]['Name']==collector_name and collector_mount[0]['RW'] and collector_mount[0]['Source']==src['Mountpoint'],'server source volume boundary mismatch')
    pgdata=inspect('volume',pgdata_name)
    data_mount=[m for m in pg['mounts'] if m['Destination']=='/var/lib/postgresql/data']
    check(len(data_mount)==1 and data_mount[0]['Type']=='volume' and data_mount[0]['Name']==pgdata_name and data_mount[0]['Source']==pgdata['Mountpoint'] and data_mount[0]['RW'] is True, 'exact run-owned PGDATA volume mount mismatch')
    _, physical=harness(['bind-physical','--instance',instance_id,'--container',pgid,'--database','vra_poc01'])
    summary['physical_database_binding']=physical
    identity=sql(pgid,"SELECT json_build_object('database',current_database(),'system_identifier',(SELECT system_identifier::text FROM pg_control_system()),'server_version_num',current_setting('server_version_num'),'server_version',current_setting('server_version'));")
    summary['postgres_identity']=json.loads(identity)
    check(summary['postgres_identity']['server_version_num']=='170011' and summary['postgres_identity']['database']=='vra_poc01' and summary['postgres_identity']['system_identifier']==physical['system_identifier'],'runtime PostgreSQL17.11/physical database binding differs')
    for key,expected in SETTINGS.items():
        actual=sql(pgid,"SHOW "+key+";")
        check(actual==expected,'effective logging setting differs: '+key)
        summary.setdefault('effective_logging_settings',{})[key]=actual
    log_filename=sql(pgid,'SHOW log_filename;')
    check(re.fullmatch(r'postgresql-%Y-%m-%d_%H%M%S-[0-9a-f-]{36}\.log',log_filename) is not None,'unique per-server boot log filename shape differs')
    summary['effective_logging_settings']['log_filename']=log_filename
    # Metadata only. Reading raw collector content into published evidence is forbidden.
    metadata=docker(['exec',pgid,'sh','-c',
      'id -u postgres; stat -c "%u|%g|%a|%F" /var/log/vra-poc04-collector; for f in /var/log/vra-poc04-collector/*.json; do test -f "$f" || exit 7; stat -c "%u|%g|%a|%F|%n" "$f"; done']).stdout.strip().splitlines()
    server_uid=int(metadata[0]); directory=metadata[1].split('|')
    check(directory==[str(server_uid),'9404','2750','directory'],'collector directory ownership or mode mismatch')
    files=[]
    for line in metadata[2:]:
        owner,group,mode,typ,name=line.split('|',4)
        check(owner==str(server_uid) and group=='9404' and mode=='640' and typ in ['regular file','regular empty file'],'collector file ownership/mode mismatch')
        check(name.startswith('/var/log/vra-poc04-collector/postgresql-') and name.endswith('.json'),'unexpected collector file identity')
        files.append({'owner_uid':int(owner),'gid':int(group),'mode':mode,'type':typ,'path':name})
    check(files,'no PostgreSQL jsonlog segment present')
    summary['collector_metadata']={'server_uid':server_uid,'directory':{'uid':server_uid,'gid':9404,'mode':'2750'},'files':files,'raw_contents_exported':False}
    # Actual LOGIN role authentication via the exact disposable server's bridge IP.
    # This avoids initdb's loopback trust rules; a wrong-password probe independently rejects authentication.
    check(len(pg['networks'])==1,'collector server network identity differs')
    credential_address=next(iter(pg['networks'].values()))['IPAddress']
    check(bool(credential_address) and not credential_address.startswith('127.'),'credential probe must not use loopback/trust')
    summary['credential_probe_address']=credential_address
    # Passwords travel only via process STDIN; nothing is supplied in argv or published evidence.
    variables={'migrator_password':passwords['vra_migrator'],'runtime_password':passwords['vra_runtime'],
     'outbox_worker_password':passwords['vra_outbox_worker'],'reconciliation_worker_password':passwords['vra_reconciliation_worker'],
     'async_operator_password':passwords['vra_async_operator'],'projection_rebuilder_password':passwords['vra_projection_rebuilder'],
     'async_observer_password':passwords['vra_async_observer']}
    assignments=''.join('\set '+name+' '+value+'\n' for name,value in variables.items())
    bootstrap=assignments+(ROOT/'validation/poc-01/db/bootstrap.sql').read_text()+'\n'+(ROOT/'validation/poc-03/db/bootstrap-async-roles.sql').read_text()+'\n'+(ROOT/'validation/poc-04/db/bootstrap-security-roles.sql').read_text()
    sql(pgid,bootstrap)
    for role in ['vra_factor_fixture','vra_audit_evidence_reader','vra_security_telemetry_observer']:
        sql(pgid,'ALTER ROLE '+role+" PASSWORD '"+passwords[role]+"';")
    wrong=docker(['exec','-i',pgid,'sh','-c',
      'IFS= read -r PGPASSWORD; export PGPASSWORD; exec psql -X -qAt -w -h "$2" -U "$1" -d vra_poc01 -v ON_ERROR_STOP=1','credential-probe','vra_runtime',credential_address],
      data=secrets.token_urlsafe(48)+'\nSELECT current_user;\n',optional=True)
    check(wrong.returncode!=0 and 'password authentication failed' in wrong.stderr,'authentication did not reject wrong ephemeral password')
    summary['wrong_password_rejected']={'role':'vra_runtime','exit_status':wrong.returncode,'password_authentication_failure':True,'sqlstate_claim':None}
    summary['role_bootstrap']='PASS; exact accepted source scripts; runtime credentials stdin only'
    denials=[]
    for role in ROLES:
        check(sql(pgid,'SELECT current_user;',role)==role,'actual LOGIN credential identity mismatch')
        grants=json.loads(sql(pgid,"SELECT json_build_object('read_server_files',pg_has_role(current_user,'pg_read_server_files','MEMBER'),'pg_monitor',pg_has_role(current_user,'pg_monitor','MEMBER'),'set_log_statement',has_parameter_privilege(current_user,'log_statement','SET'));",role))
        check(not any(grants.values()),'ordinary/helper server-file,monitor,parameter privilege found')
        for case,statement in [('server_file_read',"SELECT pg_read_file('/var/log/vra-poc04-collector',0,0);"),('log_directory_read','SELECT pg_ls_logdir();'),('log_setting_mutation',"SET log_statement='all';")]:
            result=sql(pgid,statement,role,'42501');result['case']=case;result['catalog_privileges']=grants;denials.append(result)
    summary['actual_credential_sql_denials']=denials
    # This proves the mount/OS preparation only. No observer process, parser or append function exists.
    reader_code='set -eu; test "$(id -u)" = 9404; test "$(id -g)" = 9404; test -r /collector; set -- /collector/*.json; test -f "$1"; head -c 1 "$1" >/dev/null; if (printf probe >/collector/forbidden-probe) 2>/dev/null; then exit 12; fi; printf "%s\\n" "reader_uid=9404 gid=9404 read=PASS write=DENIED"'
    deny_code='set -eu; test "$(id -u)" = 9405; test "$(id -g)" = 9405; if ls /collector >/dev/null 2>&1; then exit 13; fi; if (printf probe >/collector/forbidden-probe) 2>/dev/null; then exit 14; fi; printf "%s\\n" "synthetic_nonmember_uid=9405 gid=9405 read=DENIED write=DENIED"'
    for name,user,code,expected in [('reserved-reader','9404:9404',reader_code,'reader_uid=9404 gid=9404 read=PASS write=DENIED'),('synthetic-nonmember','9405:9405',deny_code,'synthetic_nonmember_uid=9405 gid=9405 read=DENIED write=DENIED')]:
        args=['create','--pull','never','--name',project+'-'+name,'--platform',PLATFORM,'--network','none','--user',user,
          '--cap-drop','ALL','--security-opt','no-new-privileges:true','--read-only','--mount',
          'type=volume,src='+collector_name+',dst=/collector,readonly,volume-nocopy']
        for key,value in labels.items():args+=['--label',key+'='+value]
        args += [ALPINE_REF,'sh','-c',code]
        cid=create_resource('container',args);raw_probe=inspect('container',cid);probe=remember('container',raw_probe)
        check(all(e.partition('=')[0] in ['PATH'] for e in raw_probe['Config'].get('Env',[])),'OS probe received unexpected environment/credential')
        check(probe['user']==user and probe['network_mode']=='none' and probe['read_only_root'] and not probe['privileged'],'OS probe process authority differs')
        check(probe['manifest_descriptor']['digest']==expected_platform['testcontainers_tinyimage']['digest'],'OS probe image manifest differs')
        check(probe['cap_drop']==['ALL'] and 'no-new-privileges:true' in probe['security_options'],'OS probe capabilities differ')
        check(len(probe['mounts'])==1 and probe['mounts'][0]['Type']=='volume' and probe['mounts'][0]['Name']==collector_name and probe['mounts'][0]['Destination']=='/collector' and probe['mounts'][0]['RW'] is False,'OS probe read-only exact source mount differs')
        requests=probe['mount_requests']
        check(len(requests)==1 and requests[0].get('Type')=='volume' and requests[0].get('Source')==collector_name and requests[0].get('Target')=='/collector' and requests[0].get('ReadOnly') is True and requests[0].get('VolumeOptions',{}).get('NoCopy') is True,'OS probe daemon mount nocopy/read-only request differs')
        check(not probe['port_bindings'],'OS probe unexpected port')
        docker(['start',cid]);status=docker(['wait',cid]).stdout.strip();check(status=='0','OS/mount probe failed')
        result=docker(['logs',cid]).stdout.strip();check(result==expected,'OS probe safe output mismatch')
        summary.setdefault('OS_probes',[]).append({'name':name,'container_id':cid,'user':user,'exit_status':int(status),'result':result,
          'authority':'no network, no capabilities, read-only root, only ro/nocopy collector mount; no Docker socket, PGDATA, admin secret or DB credential'})
        remember('container',inspect('container',cid))
    summary['status']='PASS'
except BaseException as error:
    # Never serialize SQL input/output, exception stderr or secret file contents.
    summary['status']='FAIL'
    summary['failure']={'type':type(error).__name__,'safe_message':str(error) if isinstance(error,AssertionError) else 'execution exception; sensitive details suppressed'}
finally:
    cleanup=[]
    try:
        summary['cleanup_daemon']=attest()
        write('summary-before-cleanup.json',summary)
        write('resources.json',resources)
        write('creation-receipts.json',creation_receipts)
        for kind in ['container','volume','network']:
            for receipt in reversed(creation_receipts):
                if receipt['kind'] != kind: continue
                identity=receipt['id']
                result, proof=harness(['teardown','--kind',kind,'--id',identity],optional=True)
                cleanup.append({'kind':kind,'exact_identity':identity,'exit_status':result.returncode,'ledger_teardown':proof})
                check(result.returncode==0 and proof is not None and proof.get('absent') is True and proof.get('authorized_run_id')==run_id, 'shared authorized teardown failed; exact receipt preserved')
        summary['cleanup']={'result':'PASS','resources':cleanup,'blanket_prune_used':False}
    except BaseException as error:
        summary['cleanup']={'result':'FAIL','resources':cleanup,'error_type':type(error).__name__}
        summary['status']='FAIL'
    # Explicit deletion of the one ephemeral external credential file; no credentials in reports.
    try:
        if secret_file is not None: secret_file.unlink(missing_ok=True)
        if secret_dir is not None: secret_dir.rmdir()
        if override is not None: override.unlink(missing_ok=True)
        summary['ephemeral_admin_credential_file_absent']=secret_file is None or not secret_file.exists()
    except OSError:
        summary['ephemeral_admin_credential_file_absent']=False;summary['status']='FAIL'
    passwords.clear()
    write('creation-receipts.json',creation_receipts)
    write('commands.json',commands)
    write('summary.json',summary)
    print(json.dumps({'status':summary['status'],'evidence_directory':str(out),'cleanup':summary.get('cleanup',{}).get('result')}))
    sys.exit(0 if summary['status']=='PASS' else 1)
