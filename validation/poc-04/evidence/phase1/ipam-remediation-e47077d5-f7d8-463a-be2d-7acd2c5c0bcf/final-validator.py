import pathlib,json,hashlib,subprocess,tempfile,shutil,re,xml.etree.ElementTree as E,os
root=pathlib.Path.cwd();s=json.loads(pathlib.Path('/tmp/vra-poc04-current-ipam.json').read_text());d=pathlib.Path(s['evidence_dir']);report=root/'validation/poc-04/evidence/phase1/IMPLEMENTATION_REPORT.md';archive=report.with_name('IMPLEMENTATION_REPORT.previous-7e6d7582.md');assert hashlib.sha256(archive.read_bytes()).hexdigest()==s['prior_report_sha256'];assert report.read_bytes()[:s['prior_report_length']]==archive.read_bytes()
for p,h in s['source_hashes_before'].items():assert hashlib.sha256((root/p).read_bytes()).hexdigest()==h,p
binding=json.loads((d/'final-source-binding.json').read_text());assert binding['canonical_source_sha256_before_after']==s['source_content_sha256']
for p,h in binding['authority_hashes_unchanged'].items():assert hashlib.sha256((root/p).read_bytes()).hexdigest()==h,p
for x in binding['V1_V3_hashes']:assert hashlib.sha256((root/x['path']).read_bytes()).hexdigest()==x['sha256']
assert hashlib.sha256(pathlib.Path(s['profile_config']).read_bytes()).hexdigest()==s['profile_after_sha256']
status=subprocess.check_output(['git','status','--porcelain=v1','-uall'],text=True).splitlines();changed=[x[3:] for x in status];assert not subprocess.check_output(['git','diff','--cached','--name-only']);formats={'json':0,'jsonl':0,'xml':0};ws=[]
for name in changed:
 p=root/name;b=p.read_bytes()
 if not b.endswith(b'\n'):ws.append({'path':name,'issue':'missing final newline','classification':'EXACT_RAW_IMMUTABLE_MANIFEST_BYTES' if p.name in ['postgres-index.json','postgres-arm64-manifest.json'] else 'UNEXPECTED'})
 if b'\r' in b:ws.append({'path':name,'issue':'CR bytes','classification':'UNEXPECTED'})
 for n,line in enumerate(b.splitlines(),1):
  if line.endswith((b' ',b'\t')):
   cls='PRESERVED_RAW_GRADLE_OUTPUT' if p.name.endswith('gradle-output.txt') and n==2 else ('UNIFIED_DIFF_BLANK_CONTEXT_MARKER' if p.suffix=='.patch' and line==b' ' else ('PRESERVED_RAW_JUNIT_STDOUT' if p.suffix=='.xml' and b'<system-out><![CDATA[' in b and b'Connected to docker: ' in line else 'UNEXPECTED'))
   ws.append({'path':name,'issue':'trailing whitespace','line':n,'classification':cls})
 if p.suffix=='.json':json.loads(b);formats['json']+=1
 if p.suffix=='.jsonl':
  for line in b.splitlines():json.loads(line)
  formats['jsonl']+=1
 if p.suffix=='.xml':E.fromstring(b);formats['xml']+=1
assert all(x['classification']!='UNEXPECTED' for x in ws),[(x['path'],x.get('line'),x['issue']) for x in ws if x['classification']=='UNEXPECTED']
ycmd=['ruby','-rpsych','-e','ARGV.each { |p| Psych.safe_load(File.read(p), [], [], true) }; puts "YAML parse PASS"',str(root/'validation/poc-04/db/collector-test.compose.yaml'),s['profile_config']];yaml=subprocess.run(ycmd,capture_output=True,text=True);assert yaml.returncode==0
scan=pathlib.Path(tempfile.mkdtemp(prefix='vra-poc04-ipam-finalscan-'));dest=scan/'source';dest.mkdir()
for p in changed:
 target=dest/p;target.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(root/p,target)
gcmd=['/var/folders/6p/8cx8_9294s10q4v934jqdn9h0000gn/T/vra-poc04-gitleaks-3bpa0z2g/gitleaks','dir',str(dest),'--no-banner','--redact=100','--report-format','json','--report-path',str(scan/'findings.json'),'--no-color','--log-level','error'];r=subprocess.run(gcmd,capture_output=True,text=True);findings=json.loads((scan/'findings.json').read_text());triage=[];head=subprocess.check_output(['git','rev-parse','HEAD'],text=True).strip()
for f in findings:
 rel=str(pathlib.Path(f['File']).relative_to(dest));line=(root/rel).read_text().splitlines()[f['StartLine']-1]
 if not(f['RuleID']=='generic-api-key' and 'origin/poc/04-security-auth' in line and head in line):raise AssertionError((f['RuleID'],rel,f['StartLine'],'untriaged finding; values not printed'))
 triage.append({'rule':f['RuleID'],'path':rel,'line':f['StartLine'],'classification':'FALSE_POSITIVE_KNOWN_GIT_HEAD_SHA1'})
assert r.returncode==(1 if findings else 0)
diff=subprocess.run(['git','diff','--check'],capture_output=True,text=True);assert diff.returncode==0
files=subprocess.check_output(['rg','--files','backend','validation/poc-04'],text=True).splitlines();forbidden=[p for p in files if pathlib.Path(p).name.startswith(('V5__','V6__')) or pathlib.Path(p).name in ['observer.py','PostgresAuditTamperObserver.java']];assert not forbidden
legacy='backend/runtime/src/main/java/dev/vra/inventory/adapter/out/persistence/JdbcReservationIdempotencyRepository.java';assert (root/legacy).read_bytes()==subprocess.check_output(['git','show','HEAD:'+legacy])
claims=re.findall(r'^\| `([^`]+)` \| `([0-9a-f]{64})` \|$',report.read_text(),re.M)
for name,h in claims:assert hashlib.sha256((root/name).read_bytes()).hexdigest()==h,name
clean=json.loads((d/'cleanup-proof.json').read_text());assert clean['result']=='PASS' and all(x['absent_after_cleanup'] for x in clean['resources']);env=os.environ.copy();env['DOCKER_HOST']='unix:///Users/siwakornbundi/.colima/vra-poc04-test/docker.sock';assert subprocess.check_output(['docker','info','--format','{{.ID}}'],env=env,text=True).strip()==s['daemon_id'];assert not subprocess.check_output(['docker','ps','-aq'],env=env,text=True).strip();assert not subprocess.check_output(['docker','volume','ls','-q'],env=env,text=True).strip();assert set(subprocess.check_output(['docker','network','ls','-q','--no-trunc'],env=env,text=True).splitlines())==set(clean['final_network_ids'])
obj={'command':['python3','/tmp/vra-poc04-ipam-final-validate.py'],'exit_status':0,'source_files_unchanged':len(s['source_hashes_before']),'canonical_source_sha256':s['source_content_sha256'],'previous_report_prefix_and_archive_byte_exact':s['prior_report_sha256'],'changed_files_scanned':len(changed),'tracked_modified_paths':[x[3:] for x in status if not x.startswith('??')],'untracked_paths':[x[3:] for x in status if x.startswith('??')],'staged_paths':[],'format_parse_counts':formats,'yaml_parse_command':ycmd,'yaml_parse_exit_status':yaml.returncode,'whitespace_inspection':ws,'git_diff_check_command':['git','diff','--check'],'git_diff_check_exit_status':0,'gitleaks_command':gcmd,'gitleaks_unfiltered_exit_status':r.returncode,'gitleaks_findings':triage,'actual_secrets_found':0,'ignore_rules_added':False,'phase2_paths_found':[],'existing_legacy_repository_HEAD_unchanged':True,'all_recorded_run_ids_absent':True,'final_containers':0,'final_volumes':0,'final_network_ids':clean['final_network_ids'],'current_file_hash_claims_verified':len(claims),'profile_config_sha256':s['profile_after_sha256']}
pathlib.Path('/tmp/vra-poc04-ipam-validation.json').write_text(json.dumps(obj,indent=2)+'\n');print(json.dumps({k:v for k,v in obj.items() if k not in ['tracked_modified_paths','untracked_paths','whitespace_inspection','gitleaks_command','yaml_parse_command']},indent=2))
