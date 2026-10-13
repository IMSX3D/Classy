"""Repeat the Chinese Settings > Display/General > back benchmark.

Start the benchmark app on its Settings page. Uses one UI dump before timing;
group 0 is warmup. Do not interact with the device or record video during sampling.
"""
import argparse
from pathlib import Path
import re
import subprocess
import time
import xml.etree.ElementTree as ET


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--adb', required=True)
    parser.add_argument('--serial', required=True)
    parser.add_argument('--output', type=Path, required=True,
                        help='New output directory; existing evidence is never overwritten')
    args = parser.parse_args()
    package = 'com.imsx3d.classy.benchmark'

    def adb(*command):
        return subprocess.check_output([args.adb, '-s', args.serial, *command])

    adb('shell', 'uiautomator', 'dump', '/sdcard/classy-performance-window.xml')
    root = ET.fromstring(adb('shell', 'cat', '/sdcard/classy-performance-window.xml'))
    coords = {}
    for node in root.iter('node'):
        if node.get('package') == package and node.get('text') in ('课表显示', '通用'):
            left, top, right, bottom = map(int, re.findall(r'\d+', node.get('bounds')))
            coords[node.get('text')] = ((left + right) // 2, (top + bottom) // 2)
    if len(coords) != 2:
        raise SystemExit('Open Chinese benchmark My > Settings before running.')
    args.output.mkdir(parents=True, exist_ok=False)
    for group in range(4):
        adb('shell', 'dumpsys', 'gfxinfo', package, 'reset')
        for _ in range(4):
            for title in ('课表显示', '通用'):
                x, y = coords[title]
                adb('shell', 'input', 'tap', str(x), str(y))
                time.sleep(.7)
                adb('shell', 'input', 'keyevent', '4')
                time.sleep(.7)
        raw = adb('shell', 'dumpsys', 'gfxinfo', package).decode('utf-8', errors='replace')
        (args.output / f'group-{group}.txt').write_text(raw, encoding='utf-8')
        print(group, '; '.join(re.findall(
            r'(?:Total frames rendered|Janky frames|95th percentile|99th percentile)[^\r\n]+',
            raw)), flush=True)


if __name__ == '__main__':
    main()
