#!/usr/bin/env python3
"""Freeze reproducible C29 source/evidence identities after execution and service cleanup."""
from pathlib import Path
import datetime,hashlib,json,shutil
R=Path(__file__).resolve().parents[1]
O=R/'evidence/final-runtime'
if O.exists(): raise SystemExit('Refusing to overwrite an existing runtime snapshot')
O.mkdir(parents=True)
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def row(p):return {'path':str(p.relative_to(R)),'sha256':sha(p),'bytes':p.stat().st_size}
excluded={'build','.gradle','.kotlin','__pycache__','.git','evidence'}
sources=[]
for p in sorted(R.rglob('*')):
    parts=p.relative_to(R).parts
    if not p.is_file() or set(parts)&excluded or p.name in {'local.properties','README.md','verification.md'} or p.suffix=='.md':continue
    target=O/'sources'/p.relative_to(R);target.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(p,target)
    entry=row(p);entry['snapshot']=str(target.relative_to(R));assert sha(target)==entry['sha256'];sources.append(entry)
artifacts=[];evidence=[]
for p in sorted((R/'evidence').rglob('*')):
    if not p.is_file() or O in p.parents or '__pycache__' in p.parts:continue
    (artifacts if p.suffix in {'.apk','.jar'} else evidence).append(row(p))
manifest={'status':'FROZEN','utc':datetime.datetime.now(datetime.timezone.utc).isoformat(),'sources':sources,'artifacts':artifacts,'evidence_files':evidence,
'scope':'Four original selected stage builds; separate comparison test graph; per-stage Android storage/composition tests; actual optimized update and original appearance acquisition. A separately built retired layout-repair candidate has four-configuration visual journeys without private-file instrumentation. Original campaign sources are preserved separately. See verification.md for outcomes and limitations. Docs bind at chapter completion.'}
(O/'manifest.json').write_text(json.dumps(manifest,indent=2)+'\n')
print(json.dumps({'sources':len(sources),'artifacts':len(artifacts),'evidence_files':len(evidence),'manifest':str(O/'manifest.json')}))
