import pathlib,json,hashlib,subprocess,tempfile,shutil,re,xml.etree.ElementTree as ET
root=pathlib.Path.cwd();s=json.loads(pathlib.Path('/tmp/vra-poc04-current-remediation.json').read_text());d=pathlib.Path(s['evidence_dir']);oldstate=json.loads(pathlib.Path('/tmp/vra-poc04-final-state.json').read_text());fixture='backend/migration/src/test/java/dev/vra/migration/Poc04FoundationMigrationIntegrationTest.java';other=[]
for entry in oldstate['source_files']:
 if entry['path']==fixture:assert hashlib.sha256((root/fixture).read_bytes()).hexdigest()==s['new_fixture_sha256']
 elif entry['path']=='backend/runtime/src/test/java/dev/vra/async/AsyncVisibilityIntegrationTest.java':
  assert hashlib.sha256((root/entry['path']).read_bytes()).hexdigest()==s['preserved_workspace_drift'][0]['current_sha256'];assert hashlib.sha256((root/entry['path']).read_bytes()[24:]).hexdigest()==entry['sha256']
 else:assert hashlib.sha256((root/entry['path']).read_bytes()).hexdigest()==entry['sha256'];other.append(entry)
assert len(other)==25
historical={'validation/poc-04/tooling/image-pins.json':'340b51d2910f023eef2f7b8a0a4c4065c4baa8d95b2a95c5aaa6c76b888d123a','validation/poc-04/IMPLEMENTATION_PLAN.md':'c169ad62d947841ec08987d2561f16b75935168d99ea46a34143004d3da3d7c5','validation/poc-04/SHARED_SPEC.md':'c035e627c087b41cb4533b00f0bdaf8fa208a5b55881358cf1f85b6809b2ed2d','validation/poc-04/evidence/phase1/IMPLEMENTATION_REPORT.previous-b772942a.md':'b772942af3dc367941cd7e2d553ca2f990542964114fdd3002a8c994941cdd65','validation/poc-04/evidence/phase1/IMPLEMENTATION_REPORT.previous-e5df43af.md':'e5df43afd289c8fc9a87cfe0bc25fdf182d6195ea8f8f6bfedf160df4caf0e3e','validation/poc-04/evidence/phase1/IMPLEMENTATION_REPORT.previous-93baf961.md':'93baf961f3370311e1d669e3594ed9c0c2d925ef66d0df70c62f33e61d127a57'}
for name,digest in historical.items():assert hashlib.sha256((root/name).read_bytes()).hexdigest()==digest,name
manifest=json.loads((root/'validation/poc-04/tooling/source-manifest.json').read_text());bindings={f['path']:f['sha256'] for f in manifest['files']};prior=[]
for p in sorted((root/'backend/migration/src/main/resources/db/migration').glob('V[123]__*.sql')):
 rel=str(p.relative_to(root));digest=hashlib.sha256(p.read_bytes()).hexdigest();assert bindings[rel]==digest==hashlib.sha256(subprocess.check_output(['git','show','HEAD:'+rel])).hexdigest();prior.append({'path':rel,'sha256':digest,'phase0_equal':True,'HEAD_equal':True})
status=subprocess.check_output(['git','status','--porcelain=v1','-uall'],text=True).splitlines();changed=[x[3:] for x in status];formats={'json':0,'jsonl':0,'xml':0};bad=[]
for name in changed:
 p=root/name;b=p.read_bytes()
 if not b.endswith(b'\n'):bad.append({'path':name,'issue':'missing final newline','classification':'EXACT_RAW_IMMUTABLE_MANIFEST_BYTES' if p.name in ('postgres-index.json','postgres-arm64-manifest.json') else 'UNEXPECTED'})
 if b'\r' in b:bad.append({'path':name,'issue':'CR bytes','classification':'UNEXPECTED'})
 for n,line in enumerate(b.splitlines(),1):
  if line.endswith((b' ',b'\t')):
   classification='PRESERVED_RAW_GRADLE_OUTPUT' if p.name.endswith('gradle-output.txt') and n==2 else ('UNIFIED_DIFF_BLANK_CONTEXT_MARKER' if p.suffix=='.patch' and line==b' ' else ('PRESERVED_RAW_JUNIT_OUTPUT' if p.name=='TEST-dev.vra.async.AsyncPermissionIntegrationTest.xml' and n==13 and b'<system-out><![CDATA[' in b else 'UNEXPECTED'))
   bad.append({'path':name,'issue':'trailing whitespace','line':n,'classification':classification})
 if p.suffix=='.json':json.loads(b);formats['json']+=1
 if p.suffix=='.jsonl':
  for line in b.splitlines():json.loads(line)
  formats['jsonl']+=1
 if p.suffix=='.xml':ET.fromstring(b);formats['xml']+=1
assert all(x['classification']!='UNEXPECTED' for x in bad),bad
scan=pathlib.Path(tempfile.mkdtemp(prefix='vra-poc04-image-fix-finalscan-'));dest=scan/'source';dest.mkdir()
for rel in changed:
 q=dest/rel;q.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(root/rel,q)
cmd=['/var/folders/6p/8cx8_9294s10q4v934jqdn9h0000gn/T/vra-poc04-gitleaks-3bpa0z2g/gitleaks','dir',str(dest),'--no-banner','--redact=100','--report-format','json','--report-path',str(scan/'findings.json'),'--no-color','--log-level','error'];r=subprocess.run(cmd,capture_output=True,text=True);findings=json.loads((scan/'findings.json').read_text());safe=[];head=subprocess.check_output(['git','rev-parse','HEAD'],text=True).strip()
for f in findings:
 rel=str(pathlib.Path(f['File']).relative_to(dest));line=(root/rel).read_text().splitlines()[f['StartLine']-1];assert f['RuleID']=='generic-api-key' and 'origin/poc/04-security-auth' in line and head in line,(f['RuleID'],rel,f['StartLine']);safe.append({'rule_id':f['RuleID'],'file':rel,'line':f['StartLine'],'classification':'FALSE_POSITIVE_KNOWN_GIT_HEAD_SHA1'})
assert r.returncode==(1 if findings else 0)
diff=subprocess.run(['git','diff','--check'],capture_output=True,text=True);assert diff.returncode==0;assert not subprocess.check_output(['git','diff','--cached','--name-only'])
allfiles=subprocess.check_output(['rg','--files','backend','validation/poc-04'],text=True).splitlines();forbidden=[p for p in allfiles if pathlib.Path(p).name.startswith(('V5__','V6__')) or pathlib.Path(p).name in ('observer.py','PostgresAuditTamperObserver.java')];assert not forbidden
report=root/'validation/poc-04/evidence/phase1/IMPLEMENTATION_REPORT.md';hashclaims=re.findall(r'^\| `([^`]+)` \| `([0-9a-f]{64})` \|$',report.read_text(),re.M)
for rel,digest in hashclaims:assert hashlib.sha256((root/rel).read_bytes()).hexdigest()==digest,rel
obj={'scope':'Post-failure artifact/source validation; PostgreSQL work stopped','command':['python3','/tmp/vra-poc04-mount-remediation-final-validate.py'],'validation_command_exit_status':0,'changed_files_scanned':len(changed),'tracked_modified':sum(not x.startswith('??') for x in status),'untracked':sum(x.startswith('??') for x in status),'staged':[],'source_modified_this_resume':[fixture],'other_source_hashes_unchanged':25,'preserved_workspace_drift':s['preserved_workspace_drift'],'fixture_sha256':s['new_fixture_sha256'],'canonical_source_sha256':s['source_content_sha256'],'source_files':s['source_files'],'historical_hashes':historical,'v1_v2_v3_hash_comparison':prior,'formats_parsed':formats,'whitespace_exceptions':bad,'git_diff_check_command':['git','diff','--check'],'git_diff_check_exit_status':diff.returncode,'gitleaks_command':cmd,'gitleaks_exit_status':r.returncode,'gitleaks_finding_count':len(findings),'reviewed_findings':safe,'actual_secrets_found':0,'ignore_rules_added':False,'no_further_source_fixes':True,'phase2_paths_found':forbidden,'preserved_artifact_hash_claims_verified':len(hashclaims),'previous_report_preserved_sha256':s['prior_report_sha256']}
pathlib.Path('/tmp/vra-poc04-mount-remediation-validation.json').write_text(json.dumps(obj,indent=2)+'\n');print(json.dumps({k:v for k,v in obj.items() if k not in ['gitleaks_command','historical_hashes','v1_v2_v3_hash_comparison','whitespace_exceptions']},indent=2))
