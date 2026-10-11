#!/usr/bin/env python3
"""Root-executed C29 signed adoption chain; one baseline uninstall, no later clears."""
from pathlib import Path
import argparse
from capture_common import APP, AdoptionCapture, add_arguments, make_report, validate

STAGES = ('bridge', 'saved', 'complete', 'retired')


def main():
    parser = argparse.ArgumentParser()
    add_arguments(parser)
    for stage in STAGES:
        parser.add_argument('--' + stage + '-apk', type=Path, required=True)
    args = parser.parse_args()
    validate(args)
    apks = {stage: getattr(args, stage + '_apk').resolve() for stage in STAGES}
    if not all(p.is_file() for p in apks.values()):
        parser.error('Every optimized APK must exist.')
    report = make_report(args, Path(__file__), apks['retired'], 'adoption-upgrade',
        'One explicit task-owned bridge baseline followed by2901→2902→2903→2904 signed install-r updates. No app-data clearing between stages. Exact APK readbacks and signed framework instrumentation file witnesses separate persistent-state evidence from independently reviewed light shopping pixels. Messages are captured before instrumentation; the app is relaunched and its prior destination reselected afterward.')
    session = AdoptionCapture(args, report)
    failure = None
    try:
        session.start()
        row = {'config': 'light', 'settings': session.configure('light'), 'status': 'STARTING', 'frames': []}
        report['rows'].append(row)
        # A previous final2904 cannot be downgraded normally; explicitly initialize this task-owned campaign once.
        removed = session.result('uninstall', APP)
        output = (removed.stdout + removed.stderr).decode(errors='replace')
        absent = session.result('shell', 'pm', 'path', APP)
        if absent.stdout.strip():
            raise RuntimeError('Task-owned package still installed after baseline uninstall.')
        report['setupUninstall'] = {'count': 1, 'exit': removed.returncode, 'output': output, 'absenceVerified': True,
            'reason': 'Initialize the isolated bridge baseline; no further uninstall or pm clear is permitted.'}
        for index, stage in enumerate(STAGES):
            session.adb('shell', 'am', 'force-stop', APP)
            session.install(apks[stage], stage, 2901 + index)
            if index == 0:
                session.install_witness(args.witness_apk)
            raw = session.launch()
            if index == 0:
                raw = session.view(row, stage + '-shop', raw, ['nav_shop', 'nav_saved', 'nav_cart'], 'The Common Goods shopping entry keeps engineering checkpoint names out of its product UI.')
            raw = session.guarded_tap(raw, 'nav_saved')
            raw = session.quantity(raw, 3 if index == 0 else 4)
            before_revision = 0 if index == 0 else 2 * index - 1
            revision = 2 * index + 1
            raw = session.view(row, stage + '-saved-entry', raw, 'cart_quantity', 'Seeded3/r0 before the first write.' if index == 0 else f'The signed update preserves already accepted4/r{before_revision} without resetting app data.')
            raw = session.witness(stage + '-entry', 3 if index == 0 else 4, before_revision, absent=index == 0)
            if index == 0:
                raw = session.guarded_tap(raw, 'add_tea')
                raw = session.quantity(raw, 4)
                raw = session.view(row, 'bridge-saved-accepted', raw, 'cart_quantity', 'Bridge Saved accepts one addition and persists4/r1.')
                raw = session.witness('bridge-accepted', 4, 1)
            else:
                raw = session.guarded_tap(raw, 'nav_cart')
                raw = session.guarded_tap(raw, 'remove_tea')
                raw = session.quantity(raw, 3)
                raw = session.view(row, stage + '-cart-removed', raw, ['cart_quantity', 'cart_subtotal'], f'This checkpoint’s actual Cart caller removes1Tea and persists3/r{before_revision + 1}; full subtotal ₹1497 is visible.')
                raw = session.witness(stage + '-cart-removed', 3, before_revision + 1)
                raw = session.guarded_tap(raw, 'nav_saved')
                raw = session.quantity(raw, 3)
                raw = session.guarded_tap(raw, 'add_tea')
                raw = session.quantity(raw, 4)
                raw = session.view(row, stage + '-saved-accepted', raw, 'cart_quantity', f'This checkpoint’s actual Saved caller observes the removal and accepts4/r{revision}.')
                raw = session.witness(stage + '-saved-accepted', 4, revision)
            raw = session.guarded_tap(raw, 'add_tea')
            raw = session.quantity(raw, 4)
            raw = session.refusal(raw)
            raw = session.view(row, stage + '-saved-refused', raw, 'cart_message', f'Saved refuses the fifth Tea and preserves exact4/r{revision} at {stage}.')
            raw = session.witness(stage + '-saved-refused', 4, revision)
            raw = session.guarded_tap(raw, 'view_cart')
            raw = session.quantity(raw, 4)
            raw = session.view(row, stage + '-cart', raw, ['cart_quantity', 'cart_subtotal'], 'Cart reads the same4Tea and complete ₹1996 subtotal at ' + stage + '.')
            raw = session.guarded_tap(raw, 'add_tea')
            raw = session.refusal(raw)
            raw = session.view(row, stage + '-cart-refused', raw, 'cart_message', 'The Cart caller also refuses a fifth Tea through this checkpoint’s actual binding.')
            raw = session.witness(stage + '-cart-refused', 4, revision)
            before_pid = session.adb('shell', 'pidof', APP).decode().strip()
            session.adb('shell', 'am', 'force-stop', APP)
            raw = session.launch()
            after_pid = session.adb('shell', 'pidof', APP).decode().strip()
            if not before_pid or not after_pid or before_pid == after_pid:
                raise RuntimeError('Distinct restart process not observed at ' + stage)
            report.setdefault('restarts', []).append({'stage': stage, 'pidBefore': before_pid, 'pidAfter': after_pid, 'appDataCleared': False})
            raw = session.guarded_tap(raw, 'nav_cart')
            raw = session.quantity(raw, 4)
            raw = session.view(row, stage + '-restarted', raw, ['cart_quantity', 'cart_subtotal'], f'A distinct process reloads exact4/r{revision} and unchanged subtotal at {stage}; no app clear occurred.')
            raw = session.witness(stage + '-restarted', 4, revision)
        session.final_installed()
        row['status'] = report['status'] = 'CAPTURED_NOT_REVIEWED'
    except Exception as error:
        failure = error
        report.update(status='FAILED', error=str(error), failureWitness=session.failure_witness())
    finally:
        session.restore()
    if failure:
        raise failure
    if report['restorationErrors']:
        raise RuntimeError('Restoration failed; inspect result.json.')


if __name__ == '__main__':
    main()
