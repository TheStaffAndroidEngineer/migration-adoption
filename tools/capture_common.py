#!/usr/bin/env python3
"""Guarded C29 acquisition primitives. Imported only; root owns device execution."""
from pathlib import Path
import hashlib
import json
import re
import subprocess
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(ROOT / 'projects/commerce-app/tools'))
from capture_checkpoint_appearance import CaptureSession, area, bounds, matches, panes, pane_key, find_pane, signature

APP = 'staff.android.adoption'
REMOTE_RECORD = '/data/data/' + APP + '/files/cart-v1.txt'
WITNESS_PACKAGE = APP + '.witness'
WITNESS_RUNNER = WITNESS_PACKAGE + '/staff.android.adoption.witness.StateWitness'
sha = lambda path: hashlib.sha256(path.read_bytes()).hexdigest()


class AdoptionCapture(CaptureSession):
    def __init__(self, args, report):
        super().__init__(args)
        self.report = report
        self.stage = None
        self.screen = 'Shop'

    def save(self):
        (self.args.output / 'result.json').write_text(json.dumps(self.report, indent=2) + '\n')

    def result(self, *parts, timeout=90):
        run = subprocess.run([str(self.adb_path), '-s', self.args.serial, *map(str, parts)], capture_output=True, timeout=timeout)
        self.commands.append({'args': list(map(str, parts)), 'exit': run.returncode})
        return run

    def start(self):
        avd = self.adb('emu', 'avd', 'name').decode().strip()
        if self.args.expected_avd not in avd.splitlines():
            raise RuntimeError('Unexpected emulator: ' + avd)
        self.report['avd'] = avd
        self.report['originalSettings'] = self.settings()
        self.report['originalReverse'] = self.adb('reverse', '--list').decode()
        self.save()

    def install_witness(self, apk):
        self.adb('install', '-r', apk)
        remote = self.adb('shell', 'pm', 'path', WITNESS_PACKAGE).decode().strip().removeprefix('package:')
        copied = self.args.output / 'installed-witness.apk'
        self.adb('pull', remote, copied)
        if sha(copied) != sha(apk):
            raise RuntimeError('Installed witness APK differs from the supplied artifact.')
        self.report['witnessApk'] = {'path': str(apk), 'sha256': sha(apk), 'installedReadback': copied.name,
            'package': WITNESS_PACKAGE, 'runner': WITNESS_RUNNER,
            'scope': 'Separate same-signer Java framework instrumentation. Every invocation may terminate the app; the helper relaunches and selects the prior destination using fresh XML.'}
        self.save()

    def install(self, apk, stage, version):
        self.adb('install', '-r', apk)
        self.stage = stage
        remote = self.adb('shell', 'pm', 'path', APP).decode().strip().removeprefix('package:')
        if not remote or '\n' in remote:
            raise RuntimeError('Expected one installed base APK.')
        copied = self.args.output / ('installed-' + stage + '.apk')
        self.adb('pull', remote, copied)
        if sha(copied) != sha(apk):
            raise RuntimeError('Installed APK differs from supplied artifact: ' + stage)
        dump = self.adb('shell', 'dumpsys', 'package', APP)
        path = self.args.output / (stage + '-package.txt')
        path.write_bytes(dump)
        if not re.search(rb'versionCode=' + str(version).encode() + rb'\b', dump):
            raise RuntimeError('Unexpected installed version for ' + stage)
        self.report['frameArtifacts'][stage] = {'path': str(apk), 'sha256': sha(apk), 'versionCode': version,
            'installedReadback': copied.name, 'packageDump': path.name, 'packageDumpSha256': sha(path)}
        self.save()

    def final_installed(self):
        remote = self.adb('shell', 'pm', 'path', APP).decode().strip().removeprefix('package:')
        self.adb('pull', remote, self.args.output / 'installed.apk')
        if sha(self.args.output / 'installed.apk') != self.report['apkSha256']:
            raise RuntimeError('Final installed artifact changed.')

    def configure(self, config):
        self.adb('shell', 'am', 'force-stop', APP)
        self.adb('shell', 'wm', 'size', '1920x1200' if config == 'wide' else 'reset')
        self.adb('shell', 'wm', 'density', '240' if config == 'wide' else 'reset')
        self.adb('shell', 'settings', 'put', 'system', 'font_scale', '2.0' if config == 'large' else '1.0')
        self.adb('shell', 'cmd', 'uimode', 'night', 'yes' if config == 'dark' else 'no')
        current = self.settings()
        assert float(current['font']) == (2 if config == 'large' else 1)
        assert 'Night mode: ' + ('yes' if config == 'dark' else 'no') in current['night']
        if config == 'wide':
            assert 'Override size: 1920x1200' in current['size'] and 'Override density: 240' in current['density']
        else:
            assert 'Override' not in current['size'] and 'Override' not in current['density']
        return current

    def reset(self, reason):
        self.adb('shell', 'am', 'force-stop', APP)
        output = self.adb('shell', 'pm', 'clear', APP).decode().strip()
        if output != 'Success':
            raise RuntimeError('Explicit task-owned reset failed: ' + output)
        self.report.setdefault('resets', []).append({'package': APP, 'reason': reason, 'output': output})
        self.save()

    def launch(self):
        self.adb('shell', 'am', 'start', '-W', '-n', APP + '/.MainActivity', '-f', '0x10008000')
        self.screen = 'Shop'
        return self.settled(exact=['nav_shop', 'nav_saved', 'nav_cart'])

    def swipe(self, pane, direction):
        left, top, right, bottom = bounds(pane)
        if not area((left, top, right, bottom)):
            raise RuntimeError('Fresh pane is empty.')
        high, low = top + (bottom - top) * 35 // 100, top + (bottom - top) * 65 // 100
        start, end = (low, high) if direction == 'down' else (high, low)
        self.adb('shell', 'input', 'swipe', (left + right) // 2, start, (left + right) // 2, end, 180)
        return self.settled()

    @staticmethod
    def framed(raw, labels):
        tree = ET.fromstring(raw)
        parents = {child: parent for parent in tree.iter() for child in parent}
        for label in labels:
            found = False
            for node in tree.iter('node'):
                if node.get('package') != APP or not matches(node, label):
                    continue
                left, top, right, bottom = bounds(node)
                if not area((left, top, right, bottom)):
                    continue
                valid, parent = True, node
                while parent in parents:
                    parent = parents[parent]
                    if parent.get('scrollable') == 'true':
                        pl, pt, pr, pb = bounds(parent)
                        if left < pl or right > pr or top < pt + 12 or bottom > pb - 12:
                            valid = False
                if valid:
                    found = True
                    break
            if not found:
                return False
        return True

    def reveal(self, raw, labels):
        labels = [labels] if isinstance(labels, str) else labels
        if self.framed(raw, labels):
            return raw
        for key in [pane_key(p) for p in panes(ET.fromstring(raw), APP)]:
            for direction in ['down', 'up']:
                for _ in range(self.args.max_scrolls):
                    pane = find_pane(ET.fromstring(raw), APP, key)
                    if pane is None:
                        break
                    before = signature(pane)
                    raw = self.swipe(pane, direction)
                    if self.framed(raw, labels):
                        return raw
                    after = find_pane(ET.fromstring(raw), APP, key)
                    if after is None or signature(after) == before:
                        break
        raise RuntimeError('Could not fully frame ' + repr(labels))

    def guarded_tap(self, raw, label):
        self.reveal(raw, label)
        raw = self.xml()
        target = self.target(ET.fromstring(raw), label)
        if target is None:
            raise RuntimeError('Fresh guarded enabled target missing: ' + label)
        left, top, right, bottom = bounds(target)
        self.adb('shell', 'input', 'tap', (left + right) // 2, (top + bottom) // 2)
        self.screen = {'nav_shop': 'Shop', 'nav_saved': 'Saved', 'nav_cart': 'Cart',
            'open_saved': 'Saved', 'view_cart': 'Cart', 'browse_saved': 'Saved'}.get(label, self.screen)
        return self.settled()

    def text_at(self, raw, label):
        return [n.get('text', '') for n in ET.fromstring(raw).iter('node') if n.get('package') == APP and matches(n, label)]

    def quantity(self, raw, expected):
        raw = self.reveal(raw, 'cart_quantity')
        if self.text_at(raw, 'cart_quantity') != [f'In your cart: {expected}']:
            raise RuntimeError('Unexpected displayed accepted quantity: ' + repr(self.text_at(raw, 'cart_quantity')))
        return raw

    def refusal(self, raw):
        raw = self.reveal(raw, 'cart_message')
        expected = 'You can add up to 4 Tea. Your cart is unchanged.'
        if self.text_at(raw, 'cart_message') != [expected]:
            raise RuntimeError('Expected complete refusal message, found: ' + repr(self.text_at(raw, 'cart_message')))
        return raw

    def view(self, row, name, raw, labels, condition):
        raw = self.reveal(raw, labels)
        if 'cart_subtotal' in labels:
            quantity = self.text_at(raw, 'cart_quantity')
            amount = self.text_at(raw, 'cart_subtotal')
            if len(quantity) != 1 or not re.fullmatch(r'In your cart: [0-4]', quantity[0]):
                raise RuntimeError('Subtotal proof requires its exact quantity.')
            expected = int(quantity[0].rsplit(' ', 1)[1]) * 499
            if amount != [f'Subtotal ₹{expected}']:
                raise RuntimeError('Unexpected visible subtotal: ' + repr(amount))
        frame = self.capture(row['config'] + '-' + name, raw)
        artifact = self.report['frameArtifacts'][self.stage]
        frame.update(condition=condition, surfacePackage=APP, artifactKey=self.stage, apkSha256=artifact['sha256'])
        row['frames'].append(frame)
        self.save()
        return raw

    def witness(self, name, quantity, revision, absent=False):
        # Call after all phase-specific message/quantity PNGs. Instrumentation can
        # end the Activity; a subsequently reselected screen is a new viewport.
        destination = self.screen
        run = self.result('shell', 'am', 'instrument', '-w', WITNESS_RUNNER)
        text = (run.stdout + run.stderr).decode(errors='replace')
        instrument_path = self.args.output / (name + '-instrumentation.txt')
        instrument_path.write_text(text)
        values = re.findall(r'^INSTRUMENTATION_(?:STATUS|RESULT): installed_state=(.+)$', text, re.M)
        if run.returncode or len(values) != 1 or not re.search(r'^INSTRUMENTATION_CODE:\s*-1\s*$', text, re.M):
            raise RuntimeError('Installed-state instrumentation failed: ' + name)
        value = json.loads(values[0])
        artifact = self.report['frameArtifacts'][self.stage]
        if value['package'] != APP or value['versionCode'] != artifact['versionCode']:
            raise RuntimeError('Witness target identity does not match the installed stage.')
        exists = value['exists']
        if absent:
            if exists or value['record'] is not None:
                raise RuntimeError('Fresh seeded state unexpectedly has a persisted file.')
            payload = None
        else:
            if not exists:
                raise RuntimeError('Accepted cart file is missing: ' + name)
            payload = value['record'].encode()
            if payload != f'1|{quantity}|{revision}'.encode():
                raise RuntimeError('Unexpected persisted canonical record: ' + repr(payload))
        raw_path = self.args.output / (name + '-storage.txt')
        raw_path.write_bytes(b'ABSENT: seeded 3/r0, not yet persisted\n' if payload is None else payload)
        row = {'phase': name, 'artifactKey': self.stage,
            'apkSha256': self.report['frameArtifacts'][self.stage]['sha256'],
            'remotePath': REMOTE_RECORD, 'exists': exists, 'quantity': quantity, 'revision': revision,
            'seededNotPersisted': absent, 'raw': raw_path.name, 'sha256': sha(raw_path),
            'instrumentationRaw': instrument_path.name, 'instrumentationRawSha256': sha(instrument_path),
            'installedState': value, 'reselectedDestination': destination, 'appDataCleared': False,
            'scope': 'Same-signer test instrumentation; separately installed and outside the production graph. Message pixels precede invocation; UI is reacquired afterward, with no claim of transient UI-state retention.'}
        self.report.setdefault('installedStateWitnesses', []).append(row)
        self.save()
        raw = self.launch()
        if destination != 'Shop':
            raw = self.guarded_tap(raw, 'nav_' + destination.lower())
        row['reacquiredAfterInstrumentation'] = True
        self.save()
        return raw

    def restore(self):
        original = self.report.get('originalSettings')
        commands = [('shell', 'am', 'force-stop', APP)]
        if original:
            commands.append(('shell', 'settings', 'delete', 'system', 'font_scale') if original['font'] in ('null', '')
                else ('shell', 'settings', 'put', 'system', 'font_scale', original['font']))
            night = re.search(r'Night mode:\s*(\w+)', original['night'])
            if night:
                commands.append(('shell', 'cmd', 'uimode', 'night', night.group(1)))
            for field in ['size', 'density']:
                override = re.search(r'Override ' + field + r':\s*(\S+)', original[field])
                commands.append(('shell', 'wm', field, override.group(1) if override else 'reset'))
        for command in commands:
            try:
                self.adb(*command)
            except Exception as error:
                self.report['restorationErrors'].append(str(error))
        try:
            self.report['restoredSettings'] = self.settings()
            if original and self.report['restoredSettings'] != original:
                self.report['restorationErrors'].append('Display settings differ.')
            self.report['restoredReverse'] = self.adb('reverse', '--list').decode()
            if sorted(self.report['restoredReverse'].splitlines()) != sorted(self.report.get('originalReverse', '').splitlines()):
                self.report['restorationErrors'].append('Reverse mappings changed despite no network use.')
        except Exception as error:
            self.report['restorationErrors'].append(str(error))
        if self.report['restorationErrors'] and self.report['status'] != 'FAILED':
            self.report['status'] = 'CAPTURED_RESTORE_FAILED'
        self.save()
        (self.args.output / 'commands.json').write_text(json.dumps(self.commands, indent=2) + '\n')
        (self.args.output / 'tap-guards.json').write_text(json.dumps(self.tap_guards, indent=2) + '\n')


def make_report(args, helper, apk, candidate, scope):
    if args.output.exists():
        raise ValueError('Use a fresh evidence directory; retain all prior attempts.')
    args.output.mkdir(parents=True)
    (args.output / 'capture-script.py').write_bytes(helper.read_bytes())
    dependencies = [Path(__file__), ROOT / 'projects/commerce-app/tools/capture_checkpoint_appearance.py',
        ROOT / 'projects/catalog-network/tools/fresh_android_xml.py']
    return {'status': 'STARTING', 'package': APP, 'serial': args.serial, 'candidate': candidate,
        'apk': str(apk), 'apkSha256': sha(apk), 'captureScriptSha256': sha(helper),
        'captureDependencies': {str(p.relative_to(ROOT)): sha(p) for p in dependencies},
        'rows': [], 'frameArtifacts': {}, 'restorationErrors': [], 'scope': scope}


def add_arguments(parser):
    parser.add_argument('--out', '--output', dest='output', type=Path, required=True)
    parser.add_argument('--serial', default='emulator-5554')
    parser.add_argument('--expected-avd', default='StaffAndroid_API_36')
    parser.add_argument('--max-scrolls', type=int, default=18)
    parser.add_argument('--settle-timeout', type=float, default=45)
    parser.add_argument('--witness-apk', type=Path, required=True)


def validate(args):
    args.package = APP
    args.witness_apk = args.witness_apk.resolve()
    if not args.witness_apk.is_file():
        raise ValueError('The separate framework witness APK must exist.')
    if not 1 <= args.max_scrolls <= 24 or not 1 <= args.settle_timeout <= 120:
        raise ValueError('Use bounded capture limits.')
