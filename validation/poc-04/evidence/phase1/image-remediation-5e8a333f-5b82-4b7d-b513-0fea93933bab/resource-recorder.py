import datetime,json,os,pathlib,subprocess,threading,time
s=json.loads(pathlib.Path('/tmp/vra-poc04-current-remediation.json').read_text());out=pathlib.Path(s['evidence_dir']);env=os.environ.copy();env['DOCKER_HOST']='unix:///Users/siwakornbundi/.colima/vra-poc04-test/docker.sock'
stop=pathlib.Path(s['support_dir'])/'recorder.stop';ready=pathlib.Path(s['support_dir'])/'recorder.ready';lock=threading.Lock();records=out/'docker-resource-events.jsonl'
def emit(x):
 with lock:
  with records.open('a') as f:f.write(json.dumps(x,sort_keys=True)+'\n')
def inspect(kind,rid):
 cmd=['docker',kind,'inspect',rid] if kind!='container' else ['docker','inspect',rid]
 r=subprocess.run(cmd,env=env,capture_output=True,text=True)
 if r.returncode:return
 x=json.loads(r.stdout)[0]
 if kind=='container':
  safe={'kind':'container_inspect','id':x['Id'],'name':x['Name'],'created':x.get('Created'),'state':{k:x.get('State',{}).get(k) for k in ('Status','Running','ExitCode')},'image_id':x.get('Image'),'image':x.get('Config',{}).get('Image'),'manifest_descriptor':x.get('ImageManifestDescriptor'),'labels':x.get('Config',{}).get('Labels') or {},'mounts':[{k:m.get(k) for k in ('Type','Name','Source','Destination','RW')} for m in x.get('Mounts',[])],'ports':x.get('NetworkSettings',{}).get('Ports'),'networks':{k:{'network_id':v.get('NetworkID')} for k,v in (x.get('NetworkSettings',{}).get('Networks') or {}).items()}}
 elif kind=='volume':safe={'kind':'volume_inspect',**{k:x.get(k) for k in ('Name','CreatedAt','Driver','Mountpoint','Labels','Scope')}}
 else:safe={'kind':'network_inspect',**{k:x.get(k) for k in ('Id','Name','Created','Driver','Labels','Scope')}}
 emit(safe)
def identity(cid):
 cmd=['docker','exec',cid,'psql','-U','postgres','-d','vra_poc01','-Atq','-c',"SELECT system_identifier::text||'|'||current_database()||'|'||current_setting('server_version_num') FROM pg_control_system()"]
 for n in range(200):
  if stop.exists():return
  r=subprocess.run(cmd,env=env,capture_output=True,text=True)
  if r.returncode==0 and r.stdout.strip():emit({'kind':'postgres_identity','container_id':cid,'command':cmd,'exit_status':0,'system_identifier_database_version':r.stdout.strip()});return
  time.sleep(.05)
attest=subprocess.run(['docker','info','--format','{{.ID}}'],env=env,capture_output=True,text=True)
if attest.returncode or attest.stdout.strip()!=s['daemon_id']:raise RuntimeError('Dedicated daemon attestation failed before recorder')
p=subprocess.Popen(['docker','events','--format','{{json .}}','--filter','type=container','--filter','type=volume','--filter','type=network'],env=env,stdout=subprocess.PIPE,stderr=subprocess.DEVNULL,text=True,bufsize=1)
def consume():
 for line in p.stdout:
  try:x=json.loads(line)
  except ValueError:continue
  actor=x.get('Actor') or {};attrs=actor.get('Attributes') or {};kind=x.get('Type');action=(x.get('Action') or '').split(':',1)[0];rid=actor.get('ID') or x.get('id')
  # Exec argv can contain ephemeral bootstrap passwords: NEVER persist it.
  emit({'kind':'docker_event','type':kind,'action':action,'id':rid,'time_nano':x.get('timeNano'),'attributes':{k:v for k,v in attrs.items() if k in ('name','image','container','driver','type') or k.startswith(('dev.vra.','org.testcontainers.','com.docker.compose.'))}})
  if action=='create':threading.Thread(target=inspect,args=(kind,rid),daemon=True).start()
  if kind=='container' and action=='start':
   threading.Thread(target=inspect,args=(kind,rid),daemon=True).start()
   if 'postgres' in attrs.get('image',''):threading.Thread(target=identity,args=(rid,),daemon=True).start()
threading.Thread(target=consume,daemon=True).start();ready.write_text('ready\n');print('Safe isolated-daemon metadata recorder active; exec arguments excluded',flush=True)
while not stop.exists():time.sleep(.2)
p.terminate();p.wait(timeout=5);print('Recorder stopped; safe evidence preserved',flush=True)
