#!/usr/bin/env python3
"""Root/reader-owned build campaign. Retains exact stage source/graph/test/artifact evidence."""
from pathlib import Path
import datetime,hashlib,json,os,shutil,subprocess,time
R=Path(__file__).resolve().parents[1]
OUT=R/'evidence/builds'/datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%S%fZ');OUT.mkdir(parents=True)
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def run(args,log):
    start=time.monotonic()
    with log.open('w') as f:
        result=subprocess.run([str(R/'gradlew'),*args,'--no-daemon','--no-configuration-cache','--console=plain'],cwd=R,stdout=f,stderr=subprocess.STDOUT)
    elapsed=time.monotonic()-start
    if result.returncode: raise RuntimeError(f'Build failed: {log}')
    return elapsed
rows=[]
for stage in ['bridge','saved','complete','retired']:
    out=OUT/stage;out.mkdir()
    args=[':app:assembleRelease',':app:lintRelease',':contract-tests:test','migrationGraph',f'-PmigrationStage={stage}']
    elapsed=run(args,out/'build.log')
    for src,dest in [('app/build/outputs/apk/release/app-release.apk','app-release.apk'),('build/reports/migration-graph.tsv','migration-graph.tsv')]: shutil.copy2(R/src,out/dest)
    for src,dest in [('app/build/outputs/mapping/release','mapping'),('contract-tests/build/test-results/test','jvm-tests')]:
        shutil.copytree(R/src,out/dest,dirs_exist_ok=True)
    for p in (R/'app/build/reports').glob('lint-results-release.*'):shutil.copy2(p,out/p.name)
    subprocess.run(['python3',str(R/'tools/check-stage.py'),str(out/'migration-graph.tsv'),stage,'--output',str(out/'graph-check.json')],cwd=R,check=True,stdout=subprocess.DEVNULL)
    run([':app:dependencies','--configuration','releaseRuntimeClasspath',f'-PmigrationStage={stage}'],out/'release-runtime-classpath.log')
    rows.append({'stage':stage,'version_code':2901+len(rows),'apk':str((out/'app-release.apk').relative_to(R)),'sha256':sha(out/'app-release.apk'),'arguments':args,'command_elapsed_seconds':elapsed,'timing_scope':'single unpaired build campaign, mixed cache state; not a speedup estimate'})
    (OUT/'progress.json').write_text(json.dumps(rows,indent=2)+'\n');print(stage,rows[-1]['sha256'],flush=True)
(OUT/'result.json').write_text(json.dumps({'passed':True,'artifacts':rows},indent=2)+'\n')
print(OUT,flush=True)
