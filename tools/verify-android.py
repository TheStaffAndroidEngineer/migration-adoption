#!/usr/bin/env python3
"""Run the selected composition and real file contract on each debug stage."""
from pathlib import Path
import argparse,datetime,hashlib,json,os,shutil,subprocess,time
R=Path(__file__).resolve().parents[1]
p=argparse.ArgumentParser();p.add_argument('--serial',required=True);a=p.parse_args()
env=os.environ.copy();env['ANDROID_SERIAL']=a.serial
out=R/'evidence/android'/datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%S%fZ');out.mkdir(parents=True)
# Initialize only this task-owned package before installing lower-version bridge.
adb=Path(os.environ.get('ANDROID_HOME', str(Path.home()/'Library/Android/sdk')))/'platform-tools/adb'
with (out/'baseline-uninstall.log').open('w') as log:
    subprocess.run([str(adb),'-s',a.serial,'uninstall','staff.android.adoption'],stdout=log,stderr=subprocess.STDOUT)
rows=[]
for stage in ['bridge','saved','complete','retired']:
    dest=out/stage;dest.mkdir()
    args=[str(R/'gradlew'),f'-PmigrationStage={stage}',':app:connectedDebugAndroidTest','--no-daemon','--no-configuration-cache','--console=plain']
    start=time.monotonic()
    with (dest/'connected.log').open('w') as log:
        code=subprocess.run(args,cwd=R,env=env,stdout=log,stderr=subprocess.STDOUT).returncode
    row={'stage':stage,'returncode':code,'serial':a.serial,'arguments':args[1:],'elapsed_seconds':time.monotonic()-start}
    for src,name in [('app/build/outputs/apk/debug/app-debug.apk','app-debug.apk'),('app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk','app-debug-androidTest.apk')]:
        target=dest/name;shutil.copy2(R/src,target);row[name]={'path':str(target.relative_to(R)),'sha256':hashlib.sha256(target.read_bytes()).hexdigest()}
    for src,name in [('app/build/outputs/androidTest-results/connected/debug','results'),('app/build/reports/androidTests/connected/debug','report')]:
        if (R/src).exists():shutil.copytree(R/src,dest/name,dirs_exist_ok=True)
    rows.append(row);(out/'result.json').write_text(json.dumps({'passed':all(r['returncode']==0 for r in rows),'stages':rows},indent=2)+'\n')
    print(stage,code,flush=True)
    if code:raise RuntimeError(f'Android checks failed: {dest}')
print(out,flush=True)
