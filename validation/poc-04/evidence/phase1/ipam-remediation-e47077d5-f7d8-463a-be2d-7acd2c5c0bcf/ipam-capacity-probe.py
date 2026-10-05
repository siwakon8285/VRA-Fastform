import json,pathlib,os,subprocess,ipaddress,uuid,datetime,re
s=json.loads(pathlib.Path('/tmp/vra-poc04-current-ipam.json').read_text());d=pathlib.Path(s['evidence_dir']);env=os.environ.copy();env['DOCKER_HOST']='unix:///Users/siwakornbundi/.colima/vra-poc04-test/docker.sock';pool=ipaddress.ip_network(s['selected_pool']);baseline={x['ID'] for x in s['baseline_networks_after_restart']};block=[ipaddress.ip_network(x['subnet']) for x in json.loads((d/'pool-selection.json').read_text())['specific_observed_routes_and_subnets']]
for x in s['baseline_networks_after_restart']:
 for p in x['IPAM'].get('Config') or []:
  if p.get('Subnet'):block.append(ipaddress.ip_network(p['Subnet']))
post=json.loads((d/'post-restart-attestation.json').read_text())
for rec in post['records']:
 if rec['command']==['netstat','-rn','-f','inet']:
  for l in rec['stdout'].splitlines():
   parts=l.split()
   if not parts or not re.match(r'^\d',parts[0]):continue
   val=parts[0]
   if '/' not in val:chunks=val.split('.');val='.'.join(chunks+['0']*(4-len(chunks)))+'/'+str(8*len(chunks))
   address,prefix=val.split('/');chunks=address.split('.');val='.'.join(chunks+['0']*(4-len(chunks)))+'/'+prefix
   block.append(ipaddress.ip_network(val,strict=False))
 if rec['command'][:6]==['colima','ssh','--profile','vra-poc04-test','--','ip']:
  for val in rec['stdout'].split():
   if re.fullmatch(r'\d+\.\d+\.\d+\.\d+(?:/\d+)?',val):block.append(ipaddress.ip_network(val if '/' in val else val+'/32',strict=False))
def call(c):return subprocess.run(c,env=env,capture_output=True,text=True)
r=call(['docker','info','--format','{{.ID}}']);assert r.returncode==0 and r.stdout.strip()==s['daemon_id'];assert set(call(['docker','network','ls','-q','--no-trunc']).stdout.splitlines())==baseline
proof={'command':['python3','/tmp/vra-poc04-ipam-capacity-probe.py'],'run_id':s['attempt_id'],'started_at_utc':datetime.datetime.now(datetime.timezone.utc).isoformat(),'daemon_id':s['daemon_id'],'docker_host':env['DOCKER_HOST'],'pool':str(pool),'child_prefix':24,'status':'RUNNING','networks':[],'prohibited_specific_subnets':sorted(set(str(x) for x in block))};target=d/'capacity-probe.json'
def save():target.write_text(json.dumps(proof,indent=2)+'\n')
save();failure=None
try:
 for ordinal in range(48):
  labels={'environment':'TEST','poc':'04','purpose':'ipam-capacity-probe','run_id':s['attempt_id'],'dev.vra.environment':'TEST','dev.vra.poc':'04','dev.vra.purpose':'ipam-capacity-probe','dev.vra.run_id':s['attempt_id'],'dev.vra.database_instance_id':str(uuid.uuid5(uuid.UUID(s['attempt_id']),'ipam-capacity-probe/'+str(ordinal))),'dev.vra.source_head':'01256d96d85823740a8d6c3a3f99346afbdbef56','dev.vra.source_content_sha256':s['prior_state']['source_content_sha256'],'dev.vra.tool_manifest_sha256':'5814b0ee6eb022acfb458b21b8a009592ea627c3d371cfc7b0963595c05892d8'};name='vra-poc04-'+s['attempt_id']+'-ipam-probe-'+str(ordinal);cmd=['docker','network','create','--driver','bridge']
  for k,v in labels.items():cmd+=['--label',k+'='+v]
  cmd+=[name];r=call(cmd);assert r.returncode==0,('create_failed',ordinal,r.returncode,r.stderr);rid=r.stdout.strip();assert len(rid)==64;rec={'id':rid,'name':name,'create_command':cmd,'create_exit_status':r.returncode,'expected_labels':labels};proof['networks'].append(rec);save();inspect=['docker','network','inspect',rid];r=call(inspect);assert r.returncode==0;x=json.loads(r.stdout)[0];assert x['Id']==rid and x['Name']==name and x['Driver']=='bridge' and x['Labels']==labels;subnets=[ipaddress.ip_network(p['Subnet']) for p in x['IPAM']['Config']];assert len(subnets)==1 and subnets[0].prefixlen==24 and subnets[0].subnet_of(pool);subnet=subnets[0];assert not any(subnet.overlaps(n) for n in block);assert not any(subnet.overlaps(ipaddress.ip_network(p['subnet'])) for p in proof['networks'][:-1]);rec.update(subnet=str(subnet),inspect_command=inspect,inspect_exit_status=0,actual_labels=x['Labels'],no_overlap=True);save()
 assert len(proof['networks'])==48;proof['capacity_status']='PASS'
except BaseException as e:
 failure=e;proof['capacity_status']='FAIL';proof['failure_type']=type(e).__name__;proof['failure']=str(e);save()
finally:
 for rec in proof['networks']:
  rid=rec['id'];r=call(['docker','network','inspect',rid]);assert r.returncode==0;x=json.loads(r.stdout)[0];assert x['Id']==rid and x['Name']==rec['name'] and x['Labels']==rec['expected_labels'];cmd=['docker','network','rm',rid];r=call(cmd);rec.update(cleanup_command=cmd,cleanup_exit_status=r.returncode);assert r.returncode==0;r=call(['docker','network','inspect',rid]);rec.update(after_cleanup_inspect_exit_status=r.returncode,absent_after_cleanup=r.returncode!=0 and ('not found' in r.stderr.lower() or 'no such' in r.stderr.lower()));assert rec['absent_after_cleanup'];save()
 r=call(['docker','network','ls','-q','--no-trunc']);assert r.returncode==0 and set(r.stdout.splitlines())==baseline;proof.update(cleanup_status='PASS',final_network_ids=sorted(baseline),completed_at_utc=datetime.datetime.now(datetime.timezone.utc).isoformat(),status='FAIL' if failure else 'PASS',exit_status=1 if failure else 0,no_blanket_prune=True,other_daemons_not_accessed=True);save()
print(json.dumps({'capacity_status':proof['capacity_status'],'created_networks':len(proof['networks']),'subnets': [x.get('subnet') for x in proof['networks']],'cleanup_status':proof['cleanup_status']},indent=2),flush=True)
if failure:raise SystemExit(1)
