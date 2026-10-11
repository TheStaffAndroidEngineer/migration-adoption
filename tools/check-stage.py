#!/usr/bin/env python3
"""Check a concrete declared Gradle graph; no claim of human adoption or full discovery."""
from pathlib import Path
import argparse,json
STAGES=('bridge','saved','complete','retired')
def check(path,stage):
    rows=[line.split('\t') for line in Path(path).read_text().splitlines()]
    assert [r for r in rows if r[0]=='stage']==[['stage',stage]]
    modules={r[1] for r in rows if r[0]=='module'}
    base={':app',':cart-api',':cart-storage',':feature-saved',':feature-cart',':contract-tests'}
    expected_modules=base|({':cart-engine'} if stage=='retired' else {':legacy-cart',':cart-adapter'})
    assert modules==expected_modules, f'module inventory: extra={modules-expected_modules}, missing={expected_modules-modules}'
    edges={tuple(r[1:]) for r in rows if r[0]=='edge' and r[1]!=':contract-tests'}
    expected={(':app','implementation',v) for v in [':cart-storage',':feature-saved',':feature-cart']}
    if stage=='retired':
        expected|={(':app','implementation',v) for v in [':cart-engine',':cart-api']}
        expected|={(':cart-engine','api',':cart-api'),(':cart-engine','implementation',':cart-storage')}
    else:
        expected|={(':app','implementation',v) for v in [':legacy-cart',':cart-adapter']}
        expected|={(':legacy-cart','implementation',':cart-storage'),(':cart-adapter','api',':cart-api'),(':cart-adapter','implementation',':legacy-cart'),(':cart-adapter','implementation',':cart-storage')}
    ports={'bridge':set(),'saved':{'saved'},'complete':{'saved','cart'},'retired':{'saved','cart'}}[stage]
    for feature in ('saved','cart'):
        expected|={(f':feature-{feature}','implementation',v) for v in ([':cart-api'] if feature in ports else [':legacy-cart',':cart-storage'])}
    assert edges==expected,f'production boundary: extra={edges-expected}, missing={expected-edges}'
    # A test dependency must not leak into a production declaration.
    tests=[r for r in rows if r[0]=='edge' and r[1]==':contract-tests']
    assert all(r[2]=='testImplementation' and r[3] in expected_modules for r in tests)
    return {'passed':True,'stage':stage,'modules':sorted(modules),'production_edges':len(edges),'port_consumers':sorted(ports),'known_consumer_denominator':2,'legacy_module_selected':':legacy-cart' in modules,'scope':'declared graph of selected checkpoint; not organizational adoption evidence'}
if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('graph');p.add_argument('stage',choices=STAGES);p.add_argument('--output');a=p.parse_args()
    result=check(a.graph,a.stage);s=json.dumps(result,indent=2)+'\n'
    if a.output: Path(a.output).write_text(s)
    print(s,end='')
