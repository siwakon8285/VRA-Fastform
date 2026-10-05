import ast,datetime,difflib,hashlib,json,os,pathlib,shutil,subprocess,tempfile,uuid,xml.etree.ElementTree as ET
root=pathlib.Path('/Users/siwakornbundi/Project/VRA-Fastform');previous=json.loads(pathlib.Path('/tmp/vra-poc04-current-round1-collector.json').read_text());prior=pathlib.Path(previous['evidence_dir']);before=json.loads((prior/'FINAL_BINDING.json').read_text());sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest();script='validation/poc-04/scripts/collector-boundary-proof.py'
assert sha(prior/'COLLECTOR_PATH_REMEDIATION_REPORT.md')=='669f2e1d4bd339c51e90fb04d08237b2ae954eb7da67f8de5072ae95cd8cc833'
assert sha(prior/'FINAL_BINDING.json')=='8b9f3c5c18add17d94730acd08c08f10e992b6f07c47f46deda9c53e7e04f43a'
history={x['path']:x['sha256'] for x in before['files']};history[str((prior/'FINAL_BINDING.json').relative_to(root))]=sha(prior/'FINAL_BINDING.json')
for p in (root/'validation/poc-04/evidence/phase1/compose-secrets-diagnosis-2021b28f-c9cc-4fbe-b527-535a078cdae4').rglob('*'):
 if p.is_file():history[str(p.relative_to(root))]=sha(p)
assert all(sha(root/p)==h for p,h in history.items() if p!=script)
run=str(uuid.uuid4());out=root/'validation/poc-04/evidence/phase1'/('round1-compose-secrets-'+run);out.mkdir();support=pathlib.Path(tempfile.mkdtemp(prefix='vra-poc04-compose-secrets-'));os.chmod(support,0o700)
def write(path,value):path.write_text(json.dumps(value,indent=2)+'\n')
def git(*a):r=subprocess.run(['git',*a],cwd=root,text=True,capture_output=True);assert r.returncode==0;return r.stdout.strip()
assert git('branch','--show-current')=='poc/04-security-auth';assert git('rev-parse','HEAD')=='01256d96d85823740a8d6c3a3f99346afbdbef56';assert not git('diff','--cached','--name-only');assert not git('diff','--check')
source=json.loads((prior/'source-binding-final.json').read_text());files=[]
for entry in source['files']:
 e=dict(entry);p=root/e['path'];e.update(sha256=sha(p),size_bytes=p.stat().st_size,mode=f'{p.stat().st_mode&0o777:04o}');files.append(e)
projection=[{k:e[k] for k in ['path','sha256','size_bytes','mode']} for e in files]
content=hashlib.sha256(json.dumps(projection,sort_keys=True,separators=(',',':')).encode()).hexdigest();assert len(files)==272
source.update(files=files,content_sha256=content,branch=git('branch','--show-current'),head=git('rev-parse','HEAD'),tree=git('rev-parse','HEAD^{tree}'),worktree={'tracked_modified_paths':git('diff','--name-only').splitlines(),'untracked_paths':git('ls-files','--others','--exclude-standard').splitlines(),'staged_paths':[]},execution_metadata={'current_run_id':run,'scope':'TEST collector normalized Compose secrets parsing only; focused collector then same-byte final full build and final collector','previous_collector_run_id':previous['run_id']})
write(out/'source-binding.json',source)
jar=support/'external-tc.jar';shutil.copyfile(previous['external_jar'],jar);assert sha(jar)=='ca5bc967d7aebf9555231ef9677f9357866499e2e4c9ebef86a30d3de9a23f7d'
state={'allocations':{},'daemon_id':previous['daemon_id'],'docker_host':previous['docker_host'],'events':[],'next_ordinal':{},'physical_instances':{},'resources':{},'run_id':run,'source_content_sha256':content,'source_head':source['head'],'tool_manifest_sha256':'5814b0ee6eb022acfb458b21b8a009592ea627c3d371cfc7b0963595c05892d8'};sp=support/'run-state.json';write(sp,state);os.chmod(sp,0o600)
env=json.loads((pathlib.Path(previous['support_dir'])/'environment.json').read_text())
for k,v in list(env.items()):
 if isinstance(v,str):env[k]=v.replace(previous['run_id'],run).replace(previous['support_dir'],str(support)).replace(previous['evidence_dir'],str(out)).replace(previous['source_content_sha256'],content)
env.update(VRA_POC04_RUN_STATE=str(sp),VRA_POC04_EXTERNAL_TC_JAR=str(jar));write(support/'environment.json',env);os.chmod(support/'environment.json',0o600)
cmds=[]
def docker(args):
 r=subprocess.run(['docker']+args,env={**os.environ,**env},text=True,capture_output=True);cmds.append({'command':['docker']+args,'exit_status':r.returncode});assert r.returncode==0;return r.stdout
info=json.loads(docker(['info','--format','{{json .}}']));assert info['ID']==state['daemon_id'] and info['Name']=='colima-vra-poc04-test'
containers=docker(['ps','-aq','--no-trunc']).splitlines();volumes=docker(['volume','ls','-q']).splitlines();networks=[json.loads(docker(['network','inspect',n]))[0] for n in docker(['network','ls','-q','--no-trunc']).splitlines()];assert not containers and not volumes and sorted(x['Name'] for x in networks)==['bridge','host','none']
write(out/'preflight.json',{'status':'PASS','commands':cmds,'daemon':{k:info.get(k) for k in ['ID','Name','ServerVersion','OSType','Architecture','DefaultAddressPools']},'initial_containers':containers,'initial_volumes':volumes,'initial_networks':[{k:x[k] for k in ['Id','Name','Driver','IPAM']} for x in networks],'docker_version':json.loads(docker(['version','--format','{{json .}}'])),'compose_version':json.loads(docker(['compose','version','--format','json'])),'environment':{k:env[k] for k in ['DOCKER_HOST','TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE','TESTCONTAINERS_HOST_OVERRIDE','TESTCONTAINERS_RYUK_DISABLED']}})
health=json.loads(pathlib.Path('/tmp/vra-poc04-current-round1-health-remediation.json').read_text());ho=pathlib.Path(health['evidence_dir']);hc=json.loads((ho/'full-check-build-command.json').read_text());assert hc['exit_status']==0;counts={k:0 for k in ['tests','skipped','failures','errors']}
for p in (ho/'full-check-build-results/runtime/integrationTest').glob('TEST-*.xml'):
 e=ET.parse(p).getroot()
 for k in counts:counts[k]+=int(e.get(k,'0'))
assert counts=={'tests':113,'skipped':0,'failures':0,'errors':0}
write(out/'focused-collector-prerequisite.json',{'status':'PASS for focused-only authorization','explicit_user_order':'focused collector before final full build','previous_runtime_command':str((ho/'full-check-build-command.json').relative_to(root)),'previous_runtime_counts_reparsed_from_raw_XML':counts,'all_backend_source_bytes_unchanged_from_previous_green_build':all(sha(root/e['path'])==e['sha256'] for e in json.loads((ho/'source-binding.json').read_text())['files'] if e['path'].startswith('backend/')),'same_current_run_runtime_suite_executed_before_focus':False,'guard_scope':'current focused execution authorized by user; previous result is a prerequisite only and never final same-run substitute','final_obligation':'new full build must precede final collector on these exact source bytes'})
c={**previous,'attempt_id':'round1-compose-secrets','run_id':run,'evidence_dir':str(out),'support_dir':str(support),'state_path':str(sp),'external_jar':str(jar),'source_content_sha256':content,'before_hashes':history,'previous_run_id':previous['run_id'],'previous_report_path':str(prior/'COLLECTOR_PATH_REMEDIATION_REPORT.md'),'previous_report_sha256':sha(prior/'COLLECTOR_PATH_REMEDIATION_REPORT.md'),'previous_binding_path':str(prior/'FINAL_BINDING.json'),'previous_binding_sha256':sha(prior/'FINAL_BINDING.json'),'migration_classpath':previous['migration_classpath'].replace(previous['external_jar'],str(jar)),'started_at':datetime.datetime.now(datetime.timezone.utc).isoformat()};write(pathlib.Path('/tmp/vra-poc04-current-compose-remediation.json'),c)
execute=(pathlib.Path(previous['support_dir'])/'execute.py').read_text().replace('/tmp/vra-poc04-current-round1-collector.json','/tmp/vra-poc04-current-compose-remediation.json');(support/'execute.py').write_text(execute)
# AST-only parser testing never executes the top-level Docker proof.
tree=ast.parse((root/script).read_text());nodes=[n for n in tree.body if isinstance(n,ast.FunctionDef) and n.name in ['check','normalized_server_secret_target']];space={};exec(compile(ast.Module(body=nodes,type_ignores=[]),script,'exec'),space);parse=space['normalized_server_secret_target'];destination='/run/secrets/postgres_admin_password';valid=[[{'source':'postgres_admin_password','target':destination}],[{'source':'postgres_admin_password','target':'postgres_admin_password'}],[{'source':'postgres_admin_password'}]];results=[]
for i,value in enumerate(valid):assert parse(value)==destination;results.append({'case':'accepted-exact-form-'+str(i),'result':'PASS'})
invalid=[None,{},[],['postgres_admin_password'],[None],[{},{}],[{}],[{'source':'other'}],[{'source':None}],[{'source':['postgres_admin_password']}]]
for target in [None,'','other','/run/secrets/other','../postgres_admin_password','/run/secrets//postgres_admin_password','./postgres_admin_password',{},[]]:invalid.append([{'source':'postgres_admin_password','target':target}])
for key in ['uid','gid','mode','x-extension','unknown']:invalid.append([{'source':'postgres_admin_password',key:'unexpected'}])
for i,value in enumerate(invalid):
 try:parse(value)
 except AssertionError:results.append({'case':'denied-malformed-or-unapproved-'+str(i),'result':'PASS'})
 else:raise AssertionError('negative parser case accepted')
write(out/'parser-focused-cases.json',{'status':'PASS','python_syntax_AST':'PASS','accepted':len(valid),'denied':len(invalid),'results':results,'actual_compose_model':'validation/poc-04/evidence/phase1/compose-secrets-diagnosis-2021b28f-c9cc-4fbe-b527-535a078cdae4/normalized-secrets-model.json','actual_expected_target':destination,'prior_equality_rejects_actual_absolute_target':True,'script_sha256':sha(root/script)})
write(out/'PLAN.json',{'scope':'narrow normalized service.secrets parser and safe structural evidence','steps':['preserved pre-edit safe Compose diagnosis; CASE A exact full target matches reviewed profile','strict parser/syntax/diff checks','focused collector; STOP on failure','only after focused PASS: full check build --rerun-tasks; require 193/0/0/0','final collector on same source hash; exact cleanup/new binding'],'historical_failure_preservation':{'report':c['previous_report_sha256'],'binding':c['previous_binding_sha256']},'phase2_authorized':False})
print(json.dumps({k:c[k] for k in ['run_id','evidence_dir','support_dir','source_content_sha256']},indent=2))
