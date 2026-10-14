"""Bounded cold-start / first widget-entry comparison on an explicitly chosen device.

Uses the existing TEST identity and its data (never clears it). Force-stop temporarily
stops reminders/widgets until the subsequent normal launch. Calibrate coordinates
before running; keep orientation, font, data and animation scale identical between runs.
Do not record video during timing. gfxinfo tail percentiles are not app-ready latency.
"""
import argparse
import json
import pathlib
import re
import subprocess
import time


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--adb', required=True)
    p.add_argument('--serial', required=True)
    p.add_argument('--output', type=pathlib.Path, required=True)
    p.add_argument('--mine', type=int, nargs=2, required=True)
    p.add_argument('--widgets', type=int, nargs=2, required=True)
    args = p.parse_args()
    args.output.mkdir(parents=True, exist_ok=False)
    package = 'com.imsx3d.classy.debug'

    def adb(*command):
        return subprocess.check_output([args.adb, '-s', args.serial, *command],
                                       timeout=45).decode('utf-8', errors='replace')

    (args.output / 'conditions.json').write_text(json.dumps({
        'serial': args.serial, 'mine': args.mine, 'widgets': args.widgets,
        'size': adb('shell', 'wm', 'size'), 'density': adb('shell', 'wm', 'density'),
        'rotation': adb('shell', 'settings', 'get', 'system', 'user_rotation'),
        'animator_scale': adb('shell', 'settings', 'get', 'global', 'animator_duration_scale'),
    }, indent=2), encoding='utf-8')
    for i in range(3):
        adb('shell', 'am', 'force-stop', package)
        launch = adb('shell', 'am', 'start', '-W', '-n', package + '/com.imsx3d.classy.MainActivity')
        (args.output / f'start-{i}.txt').write_text(launch, encoding='utf-8')
        if 'Status: ok' not in launch or 'LaunchState: COLD' not in launch:
            raise RuntimeError('Not a successful cold start; inspect evidence')
        time.sleep(2)
        (args.output / f'cold-{i}.txt').write_text(adb('shell', 'dumpsys', 'gfxinfo', package), encoding='utf-8')
        adb('shell', 'input', 'tap', *map(str, args.mine))
        time.sleep(.7)
        adb('shell', 'dumpsys', 'gfxinfo', package, 'reset')
        adb('shell', 'input', 'tap', *map(str, args.widgets))
        time.sleep(1)
        adb('shell', 'input', 'keyevent', '4')
        time.sleep(.7)
        raw = adb('shell', 'dumpsys', 'gfxinfo', package)
        (args.output / f'widget-{i}.txt').write_text(raw, encoding='utf-8')
        print(i, re.findall(r'(?:Total frames rendered|Janky frames:|95th percentile|99th percentile)[^\r\n]+', raw), flush=True)


if __name__ == '__main__':
    main()
