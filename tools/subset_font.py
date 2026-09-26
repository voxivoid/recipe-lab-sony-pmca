#!/usr/bin/env python3
"""Build the small static CJK fonts bundled by Recipe Lab.

The inputs are pinned Noto Sans CJK variable TrueType fonts. FontTools first
instantiates the Regular weight, then keeps only characters used by the shipped
Chinese Android resources. This avoids depending on the camera firmware's glyph
coverage while retaining regional Simplified/Traditional glyph forms.
"""

from argparse import ArgumentParser
from hashlib import sha256
from pathlib import Path
from urllib.request import urlretrieve
import xml.etree.ElementTree as ET

try:
    from fontTools import subset
    from fontTools.varLib.instancer import instantiateVariableFont
except ImportError as exc:
    raise SystemExit("fonttools is required: python -m pip install fonttools==4.66.0") from exc


ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "assets" / "fonts"
COMMIT = "f8d157532fbfaeda587e826d4cd5b21a49186f7c"
SOURCES = {
    "sc": (
        "NotoSansSC-VF.ttf",
        "Sans/Variable/TTF/Subset/NotoSansSC-VF.ttf",
        "d68bafcb48a2707749396aa12bbbd833cb70401f3a9a689fd2902c7e0d295964",
    ),
    "tc": (
        "NotoSansTC-VF.ttf",
        "Sans/Variable/TTF/Subset/NotoSansTC-VF.ttf",
        "ac091cc8cd19e848202afc8fe6d3809b4526c8fdbdb4be82da20c4f785949591",
    ),
}
BASE_CHARACTERS = set("ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789 ·/()+-→—:.,?%|_&")


def digest(path):
    hasher = sha256()
    with path.open("rb") as source:
        for block in iter(lambda: source.read(1024 * 1024), b""):
            hasher.update(block)
    return hasher.hexdigest()


def source_file(source_dir, region):
    filename, repository_path, expected = SOURCES[region]
    path = source_dir / filename
    if not path.is_file():
        source_dir.mkdir(parents=True, exist_ok=True)
        url = "https://raw.githubusercontent.com/notofonts/noto-cjk/%s/%s" % (COMMIT, repository_path)
        print("Downloading %s" % url)
        urlretrieve(url, path)
    actual = digest(path)
    if actual.lower() != expected:
        raise SystemExit("unexpected SHA-256 for %s: %s" % (path, actual))
    return path


def resource_characters(locales):
    characters = set(BASE_CHARACTERS)
    for locale in locales:
        for resource in sorted((ROOT / "res" / locale).glob("*.xml")):
            for element in ET.parse(resource).getroot().iter("string"):
                characters.update("".join(element.itertext()))
    return characters


def build(source, output_name, family, postscript, characters):
    options = subset.Options()
    options.layout_features = ["*"]
    options.name_IDs = ["*"]
    options.name_legacy = True
    options.name_languages = ["*"]
    options.notdef_glyph = True
    options.notdef_outline = True
    options.recommended_glyphs = True
    options.hinting = True

    font = subset.load_font(str(source), options)
    instantiateVariableFont(font, {"wght": 400}, inplace=True)
    worker = subset.Subsetter(options=options)
    worker.populate(text="".join(sorted(characters)))
    worker.subset(font)

    replacements = {
        1: family,
        2: "Regular",
        3: family + " Regular",
        4: family + " Regular",
        6: postscript,
        18: family + " Regular",
    }
    for name_id, value in replacements.items():
        font["name"].setName(value, name_id, 3, 1, 0x409)
        font["name"].setName(value, name_id, 1, 0, 0)

    missing = sorted(ord(ch) for ch in characters if ord(ch) not in font.getBestCmap())
    if missing:
        raise SystemExit("missing glyphs in %s: %s" % (source.name, ", ".join("U+%04X" % cp for cp in missing)))

    output = OUTPUT / output_name
    font.save(str(output), reorderTables=True)
    print("%s  %d bytes  SHA-256 %s" % (output, output.stat().st_size, digest(output).upper()))


def main():
    parser = ArgumentParser()
    parser.add_argument("--source-dir", type=Path, default=ROOT / "out" / "font-source")
    args = parser.parse_args()

    OUTPUT.mkdir(parents=True, exist_ok=True)
    sc_characters = resource_characters(("values-zh-rCN",))
    tc_characters = resource_characters(("values-zh-rTW", "values-zh-rHK"))
    build(source_file(args.source_dir, "sc"), "RecipeLabCJKsc-Regular.ttf", "Recipe Lab CJK SC", "RecipeLabCJKsc-Regular", sc_characters)
    build(source_file(args.source_dir, "tc"), "RecipeLabCJKtc-Regular.ttf", "Recipe Lab CJK TC", "RecipeLabCJKtc-Regular", tc_characters)

    all_characters = sc_characters | tc_characters
    (OUTPUT / "GLYPHS.txt").write_text(
        "\n".join("U+%04X" % ord(ch) for ch in sorted(all_characters)) + "\n",
        encoding="utf-8",
        newline="\n",
    )


if __name__ == "__main__":
    main()
