#!/usr/bin/env python3
"""Pre-publication checks; never reads or prints the private Wi-Fi header."""
from pathlib import Path
from xml.etree import ElementTree
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
firmware = ROOT / 'firmware/quest-fan/quest-fan.ino'
web = ROOT / 'web/index.html'
readme = ROOT / 'README.md'
required = [
    firmware, web, readme, ROOT / 'LICENSE',
    ROOT / 'firmware/quest-fan/secrets.example.h',
    ROOT / 'android/dist/QuestFan-1.0.apk',
    ROOT / 'android/app/src/main/AndroidManifest.xml',
    ROOT / 'android/scripts/build.sh',
    ROOT / 'android/tests/FanProtocolTest.java',
    ROOT / 'hardware/wiring.svg',
    *sorted((ROOT / 'hardware/photos').glob('*.jpg')),
]
assert len(list((ROOT / 'hardware/photos').glob('*.jpg'))) == 4, 'Expected four user-provided photos'
for p in required:
    assert p.is_file() and p.stat().st_size > 0, f'Missing or empty: {p.relative_to(ROOT)}'
text = firmware.read_text(encoding='utf-8')
assert text.count('R"HTML(') == 1 and text.count(')HTML";') == 1, 'Unexpected firmware page marker'
embedded = text.split('R"HTML(', 1)[1].split(')HTML";', 1)[0]
assert web.read_text(encoding='utf-8') == embedded, 'web/index.html differs from the firmware PAGE'
ElementTree.parse(ROOT / 'hardware/wiring.svg')
ElementTree.parse(ROOT / 'android/app/src/main/AndroidManifest.xml')
markdown = readme.read_text(encoding='utf-8')
links = re.findall(r'\]\(([^)]+)\)', markdown) + re.findall(r'<img\s+src="([^"]+)"', markdown)
for link in links:
    if link.startswith(('http://', 'https://', '#')): continue
    assert (ROOT / link).exists(), f'Broken README link: {link}'
for p in ROOT.rglob('*'):
    if '.git' in p.parts or not p.is_file(): continue
    assert p.name != 'secrets.h', 'Private Wi-Fi header must not be packaged'
    assert p.suffix not in {'.keystore', '.jks', '.pem', '.p12', '.pfx', '.idsig'}, f'Private/generated artifact: {p}'
    assert 'build' not in p.relative_to(ROOT).parts, f'Generated build output: {p}'
print('PASS: assets, README links, SVG/manifest, embedded webpage parity, no private header or build output')
