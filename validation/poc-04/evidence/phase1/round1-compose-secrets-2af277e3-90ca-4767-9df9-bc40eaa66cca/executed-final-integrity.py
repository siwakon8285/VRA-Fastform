import ast,bisect,hashlib,json,os,re,subprocess
from pathlib import Path
root=Path('/Users/siwakornbundi/Project/VRA-Fastform');c=json.loads(Path('/tmp/vra-poc04-current-compose-remediation.json').read_text());out=Path(c['evidence_dir']);defhash=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
def git(*args):
 r=subprocess.run(['git',*args],cwd=root,text=True,capture_output=True);assert r.returncode==0;return r.stdout
tracked=git('diff','--name-only').splitlines();untracked=git('ls-files','--others','--exclude-standard').splitlines();staged=git('diff','--cached','--name-only').splitlines();assert not staged
branch=git('branch','--show-current').strip();head=git('rev-parse','HEAD').strip();assert branch=='poc/04-security-auth' and head=='01256d96d85823740a8d6c3a3f99346afbdbef56'
r=subprocess.run(['git','diff','--check'],cwd=root,text=True,capture_output=True);assert r.returncode==0
source=json.loads((out/'source-binding.json').read_text());assert all(defhash(root/x['path'])==x['sha256'] for x in source['files'])
historical={p:h for p,h in c['before_hashes'].items() if p!='validation/poc-04/scripts/collector-boundary-proof.py'};assert all((root/p).exists() and defhash(root/p)==h for p,h in historical.items())
paths=sorted(set(tracked+untracked));checks=[];whitespace=[];known={head,'1a4eb4ca8951520294d75c633613f56d3eeccc50'};chunks=[];starts=[];mapping=[];lineno=1
for path in paths:
 p=root/path
 if not p.is_file():continue
 b=p.read_bytes();known.add(hashlib.sha256(b).hexdigest())
 try:t=b.decode('utf8')
 except UnicodeDecodeError:continue
 if '\0' in t:continue
 if p.suffix=='.json':json.loads(t);checks.append({'path':path,'check':'JSON parse','result':'PASS'})
 if p.suffix=='.jsonl':
  for line in t.splitlines():
   if line:json.loads(line)
  checks.append({'path':path,'check':'JSONL parse','result':'PASS'})
 if p.suffix=='.py':ast.parse(t);checks.append({'path':path,'check':'Python AST parse','result':'PASS'})
 if p.suffix in ('.yaml','.yml'):
  q=subprocess.run(['ruby','-e','require "yaml"; YAML.safe_load(File.read(ARGV[0]), aliases: true)',str(p)],capture_output=True,text=True);assert q.returncode==0,q.stderr;checks.append({'path':path,'check':'YAML safe parse with aliases','result':'PASS'})
 lines=[i for i,z in enumerate(t.splitlines(),1) if z.endswith((' ','\t'))];missing=bool(b) and not b.endswith(b'\n')
 if lines or missing:whitespace.append({'path':path,'trailing_whitespace_lines':lines,'missing_final_newline':missing,'preserved_raw_evidence':path.startswith('validation/poc-04/evidence/')})
 starts.append(lineno);mapping.append(path);chunk='FILE '+path+'\n'+t+'\n';chunks.append(chunk);lineno+=chunk.count('\n')
 if p.name in ('source-binding.json','source-binding-final.json'):known.add(json.loads(t)['content_sha256'])
for f in source['files']:known.add(f['sha256'])
known.update(c['before_hashes'].values())
assert all(x['preserved_raw_evidence'] for x in whitespace)
phase2=['V5__guarded_synchronous_idempotency.sql','V6__protected_security_capabilities.sql','PostgresAuditTamperObserver.java','observer.py'];found=[str(p.relative_to(root)) for name in phase2 for p in root.rglob(name) if '.git' not in p.parts and 'build' not in p.parts];assert not found
jdkprod='backend/runtime/src/main/java/dev/vra/platform/configuration/DatabaseConfiguration.java';jdbc=next(p for p in root.glob('backend/**/JdbcReservationIdempotencyRepository.java') if 'build' not in p.parts);relative=str(jdbc.relative_to(root));baseline=subprocess.run(['git','show','HEAD:'+relative],cwd=root,capture_output=True);assert baseline.returncode==0 and baseline.stdout==jdbc.read_bytes()
secret_argv=['/var/folders/6p/8cx8_9294s10q4v934jqdn9h0000gn/T/vra-poc04-gitleaks-3bpa0z2g/gitleaks','stdin','--report-format','json','--report-path','-','--no-banner'];leak=subprocess.run(secret_argv,input=''.join(chunks),text=True,capture_output=True);assert leak.returncode in (0,1)
raw=json.loads(leak.stdout or '[]');matches=[];unknown=[]
for x in raw:
 candidate=x['Secret'].rstrip('.');i=max(0,bisect.bisect_right(starts,x['StartLine'])-1);safe={'rule':x['RuleID'],'path':mapping[i],'line_in_concatenated_input':x['StartLine'],'classification':'exact verified Git/file/source SHA metadata false positive' if candidate in known else 'UNRESOLVED'}
 matches.append(safe)
 if candidate not in known:unknown.append(safe)
scan={'command':secret_argv,'version':'8.30.1','exit_status':leak.returncode,'input':'modified/untracked text bytes through runtime stdin; raw candidate values not persisted','paths_scanned':len(chunks),'matches':matches,'unresolved_candidates':len(unknown),'status':'PASS' if not unknown else 'FAIL'};(out/'secret-scan-delivery.json').write_text(json.dumps(scan,indent=2)+'\n')
assert not unknown,str(unknown)
report={'status':'PASS','branch':branch,'head':head,'tree':git('rev-parse','HEAD^{tree}').strip(),'origin_main':git('rev-parse','origin/main').strip(),'origin_poc04':git('rev-parse','origin/poc/04-security-auth').strip(),'tracked_modified_paths':tracked,'untracked_paths_at_validation':untracked,'staged_paths':staged,'git_diff_check':{'command':['git','diff','--check'],'exit_status':r.returncode,'stdout':r.stdout,'stderr':r.stderr},'source_manifest_count':len(source['files']),'source_content_sha256':source['content_sha256'],'source_hashes_match':True,'historical_files_unchanged':len(historical),'format_parse_checks':checks,'separate_whitespace_inspection':{'authored_source_support_findings':0,'preserved_raw_evidence_findings':whitespace},'phase2_files_present':found,'V5_present':False,'V6_present':False,'phase2_started':False,'existing_JdbcReservationIdempotencyRepository_baseline_unchanged':True,'production_DatabaseConfiguration_sha256':defhash(root/jdkprod),'current_changed_file_hashes':[{'path':p,'sha256':defhash(root/p)} for p in paths if (root/p).is_file()]};(out/'final-source-integrity-delivery.json').write_text(json.dumps(report,indent=2)+'\n');print({'source_integrity':'PASS','tracked_modified_paths':len(tracked),'untracked_paths':len(untracked),'historical_files_unchanged':len(historical),'parse_checks':len(checks),'raw_whitespace_findings':len(whitespace),'secret_matches':len(matches),'unresolved_secret_candidates':len(unknown),'phase2_present':False})
