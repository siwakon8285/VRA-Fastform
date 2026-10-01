import json,pathlib,os,subprocess,uuid,datetime
s=json.loads(pathlib.Path('/tmp/vra-poc04-current-ipam.json').read_text());d=pathlib.Path(s['evidence_dir']);env=os.environ.copy();env['DOCKER_HOST']='unix:///Users/siwakornbundi/.colima/vra-poc04-test/docker.sock'
def run(cmd):return subprocess.run(cmd,env=env,capture_output=True,text=True)
a=run(['docker','info','--format','{{json .}}']);assert a.returncode==0;info=json.loads(a.stdout);assert info['ID']==s['daemon_id'];pre=json.loads((d/'post-restart-attestation.json').read_text());baseline={x['ID'] for x in pre['baseline_networks_after_restart']};created={'container':{},'volume':{},'network':{}};inspected={'container':{},'volume':{},'network':{}}
for line in (d/'docker-resource-events.jsonl').read_text().splitlines():
 x=json.loads(line)
 if x.get('kind')=='docker_event' and x.get('action')=='create' and x.get('type') in created:created[x['type']][x['id']]=x
 elif x.get('kind')=='container_inspect':inspected['container'][x['id']]=x
 elif x.get('kind')=='volume_inspect':inspected['volume'][x['Name']]=x
 elif x.get('kind')=='network_inspect':inspected['network'][x['Id']]=x
resources=[];expected={'dev.vra.environment':'TEST','dev.vra.poc':'04','dev.vra.source_head':'01256d96d85823740a8d6c3a3f99346afbdbef56','dev.vra.source_content_sha256':s['source_content_sha256'],'dev.vra.tool_manifest_sha256':'5814b0ee6eb022acfb458b21b8a009592ea627c3d371cfc7b0963595c05892d8'};live={}
for kind,command in [('container',['docker','ps','-aq','--no-trunc']),('volume',['docker','volume','ls','-q']),('network',['docker','network','ls','-q','--no-trunc'])]:
 r=run(command);assert r.returncode==0;live[kind]=set(r.stdout.splitlines())
 for rid in live[kind]:
  if kind=='network' and rid in baseline:continue
  assert rid in created[kind],('Live resource lacks current run creation record',kind,rid)
for kind in ['container','volume','network']:
 for rid,event in created[kind].items():
  assert rid not in baseline;cmd=['docker','inspect',rid] if kind=='container' else ['docker',kind,'inspect',rid];r=run(cmd);rec={'kind':kind,'id':rid,'creation_event':event,'inspect_command':cmd,'before_cleanup_inspect_exit_status':r.returncode}
  captured=inspected[kind].get(rid)
  if captured:
   beforeLabels=captured.get('labels' if kind=='container' else 'Labels') or {};assert all(beforeLabels.get(k)==v for k,v in expected.items()),('Recorded ownership mismatch',kind,rid);rec['captured_labels']=beforeLabels
  if r.returncode==0:
   x=json.loads(r.stdout)[0];labels=(x.get('Config',{}).get('Labels') if kind=='container' else x.get('Labels')) or {};assert all(labels.get(k)==v for k,v in expected.items()),('Unexpected live ownership',kind,rid);assert captured is not None,('Live resource lacks independent safe inspect evidence',kind,rid)
   assert uuid.UUID(labels['dev.vra.run_id']).version==4 and uuid.UUID(labels['dev.vra.database_instance_id']).version==5;assert labels==beforeLabels;rec.update(labels=labels,name=x.get('Name'),created=x.get('Created',x.get('CreatedAt')))
   if kind=='volume':rec['mountpoint']=x.get('Mountpoint')
   if kind=='network':rec['ipam']=x.get('IPAM')
  else:
   assert rid not in live[kind] and ('no such' in r.stderr.lower() or 'not found' in r.stderr.lower()),('Unexpected inspection error',kind,rid);rec['already_absent']=True
  resources.append(rec)
proof={'command':['python3','/var/folders/6p/8cx8_9294s10q4v934jqdn9h0000gn/T/vra-poc04-ipam-support-cjjo53er/runtime-exact-cleanup.py'],'timestamp_utc':datetime.datetime.now(datetime.timezone.utc).isoformat(),'daemon_id':info['ID'],'docker_host':env['DOCKER_HOST'],'daemon_default_address_pools':info.get('DefaultAddressPools'),'baseline_network_ids':sorted(baseline),'resource_count_by_kind':{k:len(v) for k,v in created.items()},'resources':resources,'teardown_scope':'Only exact current-run recorded physical resource IDs; all live ownership validated against captured and current labels before mutations'};(d/'runtime-cleanup-proof.json').write_text(json.dumps(proof,indent=2)+'\n');print(json.dumps({'recorded':proof['resource_count_by_kind'],'live_before_cleanup':{k:sum(x['kind']==k and not x.get('already_absent') for x in resources) for k in created}},indent=2),flush=True)
for rec in resources:
 if rec.get('already_absent'):continue
 kind=rec['kind'];rid=rec['id'];cmd=['docker','rm','-f',rid] if kind=='container' else ['docker',kind,'rm',rid];r=run(cmd);rec['cleanup_command']=cmd;rec['cleanup_exit_status']=r.returncode;assert r.returncode==0,('Exact cleanup failed',kind,rid)
for rec in resources:
 r=run(rec['inspect_command']);rec['after_cleanup_inspect_exit_status']=r.returncode;rec['absent_after_cleanup']=r.returncode!=0 and ('no such' in r.stderr.lower() or 'not found' in r.stderr.lower());assert rec['absent_after_cleanup']
container=run(['docker','ps','-aq','--no-trunc']);volumes=run(['docker','volume','ls','-q']);nets=run(['docker','network','ls','-q','--no-trunc']);assert all(x.returncode==0 for x in [container,volumes,nets]);assert not container.stdout.strip() and not volumes.stdout.strip();assert set(nets.stdout.splitlines())==baseline
proof.update(result='PASS',exit_status=0,all_recorded_ids_absent=True,final_containers=[],final_volumes=[],final_network_ids=sorted(baseline),no_blanket_prune=True,no_desktop_access=True,no_persistent_vra_poc00_access=True);(d/'runtime-cleanup-proof.json').write_text(json.dumps(proof,indent=2)+'\n');print('Cleanup PASS: every recorded ID absent; 0 containers/0 volumes; original 3 networks unchanged',flush=True)
