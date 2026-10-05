import datetime,hashlib,json,os,pathlib,subprocess
root=pathlib.Path('/Users/siwakornbundi/Project/VRA-Fastform');c=json.loads(pathlib.Path('/tmp/vra-poc04-current-compose-remediation.json').read_text());o=pathlib.Path(c['evidence_dir']);support=pathlib.Path(c['support_dir']);env={**os.environ,**json.loads((support/'environment.json').read_text())};env['VRA_POC04_EVIDENCE_DIRECTORY']=str(o)
commands=[]
def call(argv):
 r=subprocess.run(argv,env=env,cwd=root,text=True,capture_output=True);commands.append({'command':argv,'exit_status':r.returncode});return r
info=call(['docker','info','--format','{{json .}}']);assert info.returncode==0;info=json.loads(info.stdout);assert info['ID']==c['daemon_id'] and info['Name']=='colima-vra-poc04-test'
pre=json.loads((o/'preflight.json').read_text());baseline={x['Id'] for x in pre['initial_networks']};state=json.loads(pathlib.Path(c['state_path']).read_text());write=lambda path,data:path.write_text(json.dumps(data,indent=2)+'\n');write(o/'ledger-before-final-cleanup.json',state)
live={}
for kind,args in [('container',['ps','-aq','--no-trunc']),('volume',['volume','ls','-q']),('network',['network','ls','-q','--no-trunc'])]:
 r=call(['docker']+args);assert r.returncode==0;live[kind]=set(r.stdout.splitlines());unexpected={i for i in live[kind] if kind+':'+i not in state['resources'] and not (kind=='network' and i in baseline)};assert not unexpected,('live resource outside current ledger',kind,unexpected)
proof={'status':'RUNNING','run_id':c['run_id'],'daemon_id':c['daemon_id'],'source_content_sha256':c['source_content_sha256'],'authorization':'only exact current run creator ledger; observations never authorize deletion','resources':[],'blanket_prune_used':False}
write(o/'final-exact-ledger-cleanup.json',proof)
for kind in ['container','network','volume']:
 for entry in state['resources'].values():
  if entry['kind']!=kind:continue
  assert entry['authorized_run_id']==c['run_id']
  argv=['python3','-B',str(root/'validation/poc-04/scripts/run-harness.py'),'teardown','--kind',kind,'--id',entry['id']];r=call(argv);rec={'command':argv,'exit_status':r.returncode,'kind':kind,'id':entry['id'],'creator_expected_labels':entry['expected_labels']};proof['resources'].append(rec);write(o/'final-exact-ledger-cleanup.json',proof);assert r.returncode==0,(kind,entry['id'],r.stderr)
  result=json.loads(r.stdout);rec['result']=result;assert result['absent'] is True and result['authorized_run_id']==c['run_id'];write(o/'final-exact-ledger-cleanup.json',proof)
finish=call(['python3','-B',str(root/'validation/poc-04/scripts/run-harness.py'),'finish']);assert finish.returncode==0,finish.stderr;final_consistency=json.loads(finish.stdout);assert final_consistency['status']=='PASS';write(o/'final-identity-consistency.json',final_consistency)
final_state=json.loads(pathlib.Path(c['state_path']).read_text());write(o/'final-run-ledger.json',final_state)
allCids={e['id'] for e in final_state['resources'].values() if e['kind']=='container'}
for line in (o/'docker-resource-events.jsonl').read_text().splitlines():
 d=json.loads(line)
 if d.get('kind')=='container_inspect':allCids.add(d['id'])
 if d.get('kind')=='docker_event' and d.get('type')=='container' and d.get('action')=='create':allCids.add(d['id'])
absences=[]
for cid in sorted(allCids):
 argv=['docker','inspect',cid];r=call(argv);absent=r.returncode!=0 and ('no such' in r.stderr.lower() or 'not found' in r.stderr.lower());assert absent;absences.append({'id':cid,'command':argv,'exit_status':r.returncode,'absent':True})
final_live={}
for kind,args in [('containers',['ps','-aq','--no-trunc']),('volumes',['volume','ls','-q']),('networks',['network','ls','-q','--no-trunc'])]:
 r=call(['docker']+args);assert r.returncode==0;final_live[kind]=r.stdout.splitlines()
assert not final_live['containers'] and not final_live['volumes'] and set(final_live['networks'])==baseline
network_inspects=[]
for nid in final_live['networks']:
 r=call(['docker','network','inspect',nid]);assert r.returncode==0;raw=json.loads(r.stdout)[0];network_inspects.append({k:raw[k] for k in ['Id','Name','Driver','IPAM']})
assert sorted(x['Name'] for x in network_inspects)==['bridge','host','none']
proof.update(status='PASS',recorded_at=datetime.datetime.now(datetime.timezone.utc).isoformat(),resources_absent=len(final_state['resources']),database_instances=len(final_state['physical_instances']),commands=commands,no_Desktop_access=True,no_persistent_vra_poc00_access=True);write(o/'final-exact-ledger-cleanup.json',proof)
write(o/'final-daemon-state.json',{'status':'PASS','recorded_at':datetime.datetime.now(datetime.timezone.utc).isoformat(),'endpoint':env['DOCKER_HOST'],'daemon':{k:info[k] for k in ['ID','Name','ServerVersion','OSType','Architecture','DefaultAddressPools']},'containers':[],'volumes':[],'networks':network_inspects,'recorded_container_absence':absences,'all_recorded_run_resources_absent':True,'baseline_builtin_network_ids_unchanged':True})
(support/'recorder.stop').write_text('stop\n');print(json.dumps({'status':'PASS','ledger_resources_absent':len(final_state['resources']),'physical_database_instances':len(final_state['physical_instances']),'observed_container_ids_absent':len(allCids),'final_containers':0,'final_volumes':0,'final_networks':['bridge','host','none']},indent=2))
