#!/usr/bin/env python3
"""Root-executed C29 final retired release appearance, with explicit isolated resets."""
from pathlib import Path
import argparse
from capture_common import APP, AdoptionCapture, add_arguments, make_report, validate


def main():
    parser = argparse.ArgumentParser()
    add_arguments(parser)
    parser.add_argument('--apk', type=Path, required=True)
    parser.add_argument('--configs', nargs='+', choices=['light', 'dark', 'large', 'wide'], default=['light', 'dark', 'large', 'wide'])
    args = parser.parse_args()
    validate(args)
    apk = args.apk.resolve()
    if not apk.is_file():
        parser.error('APK does not exist.')
    report = make_report(args, Path(__file__), apk, 'retired-release',
        'Final optimized retired2904 shopping journey in four display configurations. A deliberate task-owned app reset starts each independent appearance journey. No clear occurs inside its force-stop persistence check. Pixels are not acceptance until independently inspected.')
    session = AdoptionCapture(args, report)
    failure = None
    try:
        session.start()
        session.install(apk, 'retired', 2904)
        session.install_witness(args.witness_apk)
        for config in args.configs:
            row = {'config': config, 'settings': session.configure(config), 'status': 'STARTING', 'frames': []}
            report['rows'].append(row)
            session.reset('Explicit independent ' + config + ' appearance baseline, before any history is created.')
            raw = session.launch()
            raw = session.view(row, 'shop-top', raw, ['nav_shop', 'nav_saved', 'nav_cart'], 'The Common Goods Shop entry and three ordinary shopping destinations are visible; no migration diagnostics appear.')
            raw = session.view(row, 'shop-photo', raw, 'tea_photo', 'The complete original Tea photograph is framed independently of its lower controls.')
            raw = session.view(row, 'shop-price', raw, ['product_name', 'product_price'], 'Tea and its full ₹499 price are visible.')
            raw = session.view(row, 'shop-action', raw, 'open_saved', 'The complete Saved entry action is reachable at this display size.')
            raw = session.guarded_tap(raw, 'open_saved')
            raw = session.quantity(raw, 3)
            raw = session.view(row, 'saved-initial', raw, 'cart_quantity', 'Saved reads seeded3/r0 before any persisted accepted mutation.')
            raw = session.witness(config + '-seed', 3, 0, absent=True)
            raw = session.view(row, 'saved-action', raw, 'add_tea', 'Full Add1Tea action is framed separately from the accepted quantity at large text.')
            raw = session.guarded_tap(raw, 'add_tea')
            raw = session.quantity(raw, 4)
            raw = session.view(row, 'saved-accepted', raw, 'cart_quantity', 'One addition is accepted: quantity4, with persisted revision1.')
            raw = session.view(row, 'saved-success', raw, 'cart_message', 'The completed action has visible shopper-facing success feedback.')
            raw = session.witness(config + '-accepted', 4, 1)
            raw = session.guarded_tap(raw, 'add_tea')
            raw = session.quantity(raw, 4)
            raw = session.refusal(raw)
            raw = session.view(row, 'saved-refused', raw, 'cart_message', 'The second addition is refused; its readable explanation accompanies unchanged accepted quantity4.')
            raw = session.view(row, 'saved-refused-quantity', raw, 'cart_quantity', 'The refused attempt does not replace the accepted quantity with5.')
            raw = session.witness(config + '-saved-refused', 4, 1)
            raw = session.guarded_tap(raw, 'view_cart')
            raw = session.quantity(raw, 4)
            raw = session.view(row, 'cart-accepted', raw, ['cart_quantity', 'cart_subtotal'], 'Cart reads the same accepted4Tea and complete ₹1996 subtotal.')
            raw = session.view(row, 'cart-add', raw, 'add_tea', 'The Cart consumer exposes its own Add1Tea action.')
            raw = session.guarded_tap(raw, 'add_tea')
            raw = session.refusal(raw)
            raw = session.view(row, 'cart-refused', raw, 'cart_message', 'Cart also refuses the over-limit addition without changing the saved record.')
            raw = session.witness(config + '-cart-refused', 4, 1)
            row['pidBeforeRestart'] = session.adb('shell', 'pidof', APP).decode().strip()
            session.adb('shell', 'am', 'force-stop', APP)
            raw = session.launch()
            row['pidAfterRestart'] = session.adb('shell', 'pidof', APP).decode().strip()
            if not row['pidBeforeRestart'] or not row['pidAfterRestart'] or row['pidBeforeRestart'] == row['pidAfterRestart']:
                raise RuntimeError('Distinct restarted process was not observed.')
            raw = session.guarded_tap(raw, 'nav_cart')
            raw = session.quantity(raw, 4)
            raw = session.view(row, 'cart-restarted', raw, ['cart_quantity', 'cart_subtotal'], 'A distinct process reloads quantity4/revision1 and the same subtotal; no app data was cleared for restart.')
            raw = session.witness(config + '-restarted', 4, 1)
            raw = session.view(row, 'cart-remove', raw, 'remove_tea', 'The complete Remove1Tea action is reachable for ordinary recovery.')
            raw = session.guarded_tap(raw, 'remove_tea')
            raw = session.quantity(raw, 3)
            raw = session.view(row, 'cart-recovered', raw, ['cart_quantity', 'cart_subtotal'], 'Removing one restores quantity3 and the ₹1497 subtotal through the accepted mutation path.')
            raw = session.witness(config + '-removed', 3, 2)
            raw = session.guarded_tap(raw, 'nav_saved')
            raw = session.guarded_tap(raw, 'add_tea')
            raw = session.quantity(raw, 4)
            raw = session.view(row, 'saved-recovered', raw, 'cart_quantity', 'Saved observes Cart removal and can accept a new addition to4 at revision3.')
            raw = session.witness(config + '-readded', 4, 3)
            raw = session.guarded_tap(raw, 'nav_cart')
            for quantity in [3, 2, 1, 0]:
                raw = session.guarded_tap(raw, 'remove_tea')
                if quantity:
                    raw = session.quantity(raw, quantity)
            raw = session.view(row, 'cart-empty', raw, 'empty_cart', 'Removing the final Tea presents a designed empty cart, not stale quantity content.')
            raw = session.view(row, 'empty-action', raw, 'browse_saved', 'Empty-cart recovery exposes the complete Explore your saved items action.')
            raw = session.witness(config + '-empty', 0, 7)
            raw = session.guarded_tap(raw, 'browse_saved')
            raw = session.quantity(raw, 0)
            raw = session.view(row, 'saved-from-empty', raw, 'add_tea', 'The shopper can continue from the empty cart to Saved and its available add action.')
            row['status'] = 'CAPTURED_NOT_REVIEWED'
            session.save()
            print(config + ' captured', flush=True)
        session.final_installed()
        report['status'] = 'CAPTURED_NOT_REVIEWED'
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
