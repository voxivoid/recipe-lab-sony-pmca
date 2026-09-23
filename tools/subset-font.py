#!/usr/bin/env python3
"""Build/check the UI font with fonttools==4.60.1. Never changes camera settings or Java logic."""
import argparse
import hashlib
import json
from pathlib import Path
import re
from fontTools import subset
from fontTools.ttLib import TTFont

ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / 'assets/fonts/recipe-zh.ttf'

def ui_codepoints():
    points = set(range(0x20, 0x7f))
    for path in (ROOT / 'src/com/voxivoid/recipelab').glob('*.java'):
        for literal in re.findall(r'"(?:[^"\\]|\\.)*"', path.read_text()):
            try:
                text = json.loads(literal)
            except ValueError:
                continue
            points.update(ord(char) for char in text if ord(char) >= 0x20)
    return points

def check():
    font = TTFont(OUTPUT)
    missing = ui_codepoints() - set(font.getBestCmap())
    if missing:
        raise SystemExit('Missing glyphs: ' + ' '.join('U+%04X' % p for p in sorted(missing)))
    if OUTPUT.stat().st_size >= 256 * 1024:
        raise SystemExit('Font exceeds the 256 KiB camera asset budget')
    print('UI font: %d requested glyphs covered; %d bytes' % (len(ui_codepoints()), OUTPUT.stat().st_size))

parser = argparse.ArgumentParser()
parser.add_argument('--source', type=Path, help='Unmodified AOSP Gingerbread DroidSansFallback.ttf')
args = parser.parse_args()
if args.source:
    source = args.source.read_bytes()
    expected = 'f63694f2d0910e2f31d00a818e23b13c8e3a24af04672789c19c1fb1c9da24ce'
    if hashlib.sha256(source).hexdigest() != expected:
        raise SystemExit('Source font does not match the reviewed AOSP font')
    font = TTFont(args.source)
    missing = ui_codepoints() - set(font.getBestCmap())
    if missing:
        raise SystemExit('Source missing: ' + ' '.join('U+%04X' % p for p in sorted(missing)))
    opts = subset.Options()
    opts.recalc_timestamp = False
    opts.name_IDs = ['*']
    opts.name_languages = ['*']
    opts.notdef_outline = True
    opts.recommended_glyphs = True
    sub = subset.Subsetter(options=opts)
    sub.populate(unicodes=ui_codepoints())
    sub.subset(font)
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    font.save(OUTPUT)
check()
