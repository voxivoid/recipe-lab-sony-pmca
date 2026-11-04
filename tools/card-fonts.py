#!/usr/bin/env python3
"""Build the fonts Share's card is drawn in, from Recipe Lab Studio's own, so the camera's card looks like Studio's.

Studio draws its card (lib/src/recipes/recipe_card.dart, lib/src/theme/app_theme.dart) in variable fonts the camera
cannot read (Android 2.3 has no font variations and shapes no OpenType features). Each style the card uses becomes one
static TrueType file here, cut down to Latin text (English and Vietnamese, the recipe name, the values); a line with a
character a font lacks (Chinese, Japanese, Korean) is drawn in the language's CJK font instead (CardPainter).

    python3 tools/card-fonts.py                                   # sources: ../recipe-lab-studio/assets/fonts
    python3 tools/card-fonts.py --source-dir <dir>

Inter's tabular figures (Studio sets them on, as `tnum`) are made the plain digits' glyphs. Needs fonttools.
"""

from argparse import ArgumentParser
from hashlib import sha256
from pathlib import Path

try:
    from fontTools import subset
    from fontTools.ttLib import TTFont
    from fontTools.varLib.instancer import instantiateVariableFont
except ImportError as exc:
    raise SystemExit("fonttools is required: python3 -m pip install fonttools") from exc

ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "assets" / "fonts"
LATIN = (
    set(range(0x20, 0x7F))                    # ASCII
    | set(range(0xA0, 0x180))                 # Latin-1, Latin Extended-A
    | {0x1A0, 0x1A1, 0x1AF, 0x1B0}            # Ơ ơ Ư ư
    | set(range(0x300, 0x304)) | {0x309, 0x323}  # the combining marks Vietnamese uses
    | set(range(0x1EA0, 0x1EFA))              # Vietnamese
    | {0x2013, 0x2014, 0x2018, 0x2019, 0x201C, 0x201D, 0x2022, 0x2026, 0x2192, 0x2212}
)

# output, source, axes, characters, family; the styles are Lab.* in Studio's app_theme.dart
CARD_FONTS = [
    ("CardInter-SemiBold650.ttf", "Inter.ttf", {"wght": 650, "opsz": 14}, LATIN, "Recipe Lab Card Title"),   # Lab.title
    ("CardInter-SemiBold.ttf", "Inter.ttf", {"wght": 600, "opsz": 14}, LATIN, "Recipe Lab Card Value"),      # Lab.label w600
    ("CardInter-Medium.ttf", "Inter.ttf", {"wght": 500, "opsz": 14}, LATIN, "Recipe Lab Card Label"),        # Lab.label
    ("CardArchivo-Wordmark.ttf", "Archivo.ttf", {"wght": 800, "wdth": 122}, set(map(ord, "RECIPE LAB")), "Recipe Lab Card Wordmark"),  # Lab.brandTitle
    ("CardSpaceMono-Bold.ttf", "SpaceMono-Bold.ttf", None, LATIN, "Recipe Lab Card Plate"),                 # Lab.brandLabel
]


def digest(path):
    return sha256(path.read_bytes()).hexdigest()


def tabular(font):
    """the digits' cmap entries pointed at their tnum glyphs, so the figures are tabular without shaping"""
    if "GSUB" not in font:
        return 0
    gsub = font["GSUB"].table
    lookups = set()
    for record in gsub.FeatureList.FeatureRecord:
        if record.FeatureTag == "tnum":
            lookups.update(record.Feature.LookupListIndex)
    mapping = {}
    for i in lookups:
        lookup = gsub.LookupList.Lookup[i]
        for sub in lookup.SubTable:
            if lookup.LookupType == 7:
                sub = sub.ExtSubTable
            if hasattr(sub, "mapping"):
                mapping.update(sub.mapping)
    changed = 0
    for table in font["cmap"].tables:
        if not table.isUnicode():
            continue
        for code in range(0x30, 0x3A):
            glyph = table.cmap.get(code)
            if glyph in mapping:
                table.cmap[code] = mapping[glyph]
                changed += 1
    return changed


def rename(font, family):
    """a modified font under its own name, as the Open Font License asks"""
    for record in font["name"].names:
        if record.nameID in (1, 4, 16):
            record.string = family
        elif record.nameID == 6:
            record.string = family.replace(" ", "")
        elif record.nameID in (2, 17):
            record.string = "Regular"
    font["name"].names = [r for r in font["name"].names if r.nameID not in (16, 17, 25)]


def build(source_dir):
    OUTPUT.mkdir(parents=True, exist_ok=True)
    for out, name, axes, chars, family in CARD_FONTS:
        src = source_dir / name
        if not src.is_file():
            raise SystemExit(f"missing {src}: Recipe Lab Studio's assets/fonts, or pass --source-dir")
        font = TTFont(src)
        if axes:
            font = instantiateVariableFont(font, axes, updateFontNames=False)
        swapped = tabular(font)
        options = subset.Options()
        options.layout_features = []          # Android 2.3 shapes nothing
        options.name_IDs = ["*"]
        options.name_languages = ["*"]
        options.notdef_outline = True
        options.hinting = False
        options.glyph_names = False
        sub = subset.Subsetter(options)
        sub.populate(unicodes=sorted(chars))
        sub.subset(font)
        for table in ("GSUB", "GPOS", "GDEF", "STAT", "HVAR", "MVAR", "avar", "fvar", "gvar"):
            if table in font:
                del font[table]
        rename(font, family)
        path = OUTPUT / out
        font.save(path)
        print(f"{out:28} {path.stat().st_size:7} bytes  {digest(path)}" + (f"  ({swapped} tabular digits)" if swapped else ""))


def main():
    parser = ArgumentParser(description=__doc__.split("\n\n")[0])
    parser.add_argument("--source-dir", type=Path, default=ROOT.parent / "recipe-lab-studio" / "assets" / "fonts")
    build(parser.parse_args().source_dir)


if __name__ == "__main__":
    main()
