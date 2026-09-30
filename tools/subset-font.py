#!/usr/bin/env python3
"""Rebuild the two CJK fonts the APK bundles, from the text tables that use them.

The camera's firmware font has no Chinese glyphs, so the app carries its own: Noto Sans CJK SC and TC, cut down to
the characters their tables (src/.../TextZhHans.java, TextZhHant.java) actually use, plus printable ASCII and the
English table's characters, which still appear on a Chinese screen (recipe names, codes, error text). Run it after
any change to a translation; LangTest fails a character a font lacks.

    python3 tools/subset-font.py              # sources are fetched once into out/font-source/ (about 16 MB each)
    python3 tools/subset-font.py --source-dir <dir>

Needs fonttools (python3 -m pip install fonttools). The sources are pinned by commit and SHA-256, and are not committed.
Adapted from the tool in PR #50 by Cysita.
"""

from argparse import ArgumentParser
from hashlib import sha256
from pathlib import Path
from urllib.request import urlretrieve
import re

try:
    from fontTools import subset
    from fontTools.varLib.instancer import instantiateVariableFont
except ImportError as exc:
    raise SystemExit("fonttools is required: python3 -m pip install fonttools") from exc

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "src" / "com" / "voxivoid" / "recipelab"
OUTPUT = ROOT / "assets" / "fonts"
COMMIT = "f8d157532fbfaeda587e826d4cd5b21a49186f7c"   # notofonts/noto-cjk
FONTS = [
    # table, source file, its path in noto-cjk, its SHA-256, output file, family name
    ("TextZhHans.java", "NotoSansSC-VF.ttf", "Sans/Variable/TTF/Subset/NotoSansSC-VF.ttf",
     "d68bafcb48a2707749396aa12bbbd833cb70401f3a9a689fd2902c7e0d295964", "RecipeLabCJKsc-Regular.ttf", "Recipe Lab CJK SC"),
    ("TextZhHant.java", "NotoSansTC-VF.ttf", "Sans/Variable/TTF/Subset/NotoSansTC-VF.ttf",
     "ac091cc8cd19e848202afc8fe6d3809b4526c8fdbdb4be82da20c4f785949591", "RecipeLabCJKtc-Regular.ttf", "Recipe Lab CJK TC"),
]
ROW = re.compile(r'\{\s*"([^"]+)",\s*"((?:[^"\\]|\\.)*)"\s*\}')


def digest(path):
    h = sha256()
    with path.open("rb") as f:
        for block in iter(lambda: f.read(1 << 20), b""):
            h.update(block)
    return h.hexdigest()


def source(source_dir, name, repo_path, expected):
    path = source_dir / name
    if not path.is_file():
        source_dir.mkdir(parents=True, exist_ok=True)
        url = "https://raw.githubusercontent.com/notofonts/noto-cjk/%s/%s" % (COMMIT, repo_path)
        print("fetching %s" % url)
        urlretrieve(url, path)
    if digest(path) != expected:
        raise SystemExit("%s does not match its pinned SHA-256" % path)
    return path


def table_chars(java):
    """every character of every text in a table: the values of its { "key", "text" } rows"""
    rows = ROW.findall((SRC / java).read_text(encoding="utf-8"))
    if not rows:
        raise SystemExit("no rows found in %s" % java)
    chars = set()
    for _, text in rows:
        chars.update(text.replace('\\"', '"').replace("\\\\", "\\"))
    return chars


def build(src, output, family, chars):
    options = subset.Options()
    options.layout_features = ["*"]
    options.name_IDs = ["*"]
    options.name_legacy = True
    options.name_languages = ["*"]
    options.notdef_glyph = True
    options.notdef_outline = True
    options.recommended_glyphs = True
    options.hinting = True

    font = subset.load_font(str(src), options)
    instantiateVariableFont(font, {"wght": 400}, inplace=True)   # Android 2.3 reads static TrueType only
    worker = subset.Subsetter(options=options)
    worker.populate(text="".join(sorted(chars)))
    worker.subset(font)

    # a modified OFL font may not carry the upstream name
    postscript = family.replace(" ", "") + "-Regular"
    for name_id, value in {1: family, 2: "Regular", 3: family + " Regular", 4: family + " Regular", 6: postscript, 16: family, 17: "Regular"}.items():
        font["name"].setName(value, name_id, 3, 1, 0x409)
        font["name"].setName(value, name_id, 1, 0, 0)

    cmap = font.getBestCmap()
    missing = sorted(ord(c) for c in chars if ord(c) not in cmap)
    if missing:
        raise SystemExit("%s lacks: %s" % (src.name, " ".join("U+%04X" % c for c in missing)))
    out = OUTPUT / output
    font.save(str(out), reorderTables=True)
    print("%s  %d glyphs requested  %d bytes  SHA-256 %s" % (out.relative_to(ROOT), len(chars), out.stat().st_size, digest(out)))


def main():
    parser = ArgumentParser()
    parser.add_argument("--source-dir", type=Path, default=ROOT / "out" / "font-source")
    args = parser.parse_args()
    OUTPUT.mkdir(parents=True, exist_ok=True)
    common = set(chr(c) for c in range(0x20, 0x7f)) | table_chars("TextEn.java")
    for java, name, repo_path, expected, output, family in FONTS:
        build(source(args.source_dir, name, repo_path, expected), output, family, common | table_chars(java))


if __name__ == "__main__":
    main()
