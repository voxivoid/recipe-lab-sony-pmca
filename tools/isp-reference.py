#!/usr/bin/env python3
"""Write test/fixtures/isp-reference.tsv: the picture-profile curves and gamut matrices, from colour-science.

IspProfiles (src/.../IspProfiles.java) implements the published S-Log2, S-Log3, ARRI LogC3 (EI 800), BT.709 and
BT.2100 HLG curves and the BT.709 -> S-Gamut3 / S-Gamut3.Cine / BT.2020 / ALEXA Wide Gamut 3 matrices by hand.
IspReferenceTest checks them against this file, which comes from an independent library (colour-science), so a
wrong constant or formula fails there and not only on a camera. It says nothing about what the camera does with
the tables: that still needs a real body.

    python3 tools/isp-reference.py            # rewrites the fixture; commit it with the change that needed it

Needs colour-science (python3 -m pip install colour-science). Run it again only when a curve or gamut is added or
the reference is meant to move; the file is checked in so tools/test.sh stays JDK-only.
"""

from pathlib import Path
import warnings

warnings.filterwarnings("ignore")                      # colour warns about optional plotting packages

try:
    import colour
    import numpy as np
    from colour.models import (log_encoding_ARRILogC3, log_encoding_SLog2, log_encoding_SLog3, oetf_BT709,
                               oetf_BT2100_HLG, oetf_inverse_BT2100_HLG)
except ImportError as exc:
    raise SystemExit("colour-science is required: python3 -m pip install colour-science") from exc

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "test" / "fixtures" / "isp-reference.tsv"

# The ISP table's 96 knots and the knot 18% grey sits on (IspProfiles.KNOTS, GREY_KNOT): the inputs that matter
KNOTS = ([2 * i for i in range(1, 33)] + list(range(68, 129, 4)) + list(range(136, 257, 8)) + list(range(272, 513, 16))
         + list(range(576, 1025, 64)) + list(range(1152, 1921, 128)) + [2047])
assert len(KNOTS) == 96
GREY_KNOT = 76.0

# BT.2408: 100% diffuse white at 75% HLG; the scene light that gives it is the reference's, not IspProfiles.HLG_WHITE
HLG_WHITE = float(oetf_inverse_BT2100_HLG(0.75))

CURVES = {                                                       # IspProfiles.CURVE_CODES -> 10-bit code value
    "s-log2": lambda r: log_encoding_SLog2(r) * 1023,             # in_reflection, legal range: Sony's definition
    "s-log3": lambda r: log_encoding_SLog3(r) * 1023,
    "logc3": lambda r: log_encoding_ARRILogC3(r, EI=800) * 1023,
    "itu709": lambda r: oetf_BT709(r) * 1023,
    "hlg": lambda r: oetf_BT2100_HLG(r * HLG_WHITE) * 1023,
}

GAMUTS = {                                                       # IspProfiles.GAMUT_CODES -> colour's colourspace
    "camera": "ITU-R BT.709",
    "s-gamut3": "S-Gamut3",
    "s-gamut3-cine": "S-Gamut3.Cine",
    "bt2020": "ITU-R BT.2020",
    "awg3": "ARRI Wide Gamut 3",
}


def reflectances():
    """every knot's reflectance, plus a log-spaced sweep across the table's range and the curves' breakpoints"""
    top = 0.18 * KNOTS[-1] / GREY_KNOT
    r = {0.0}
    r.update(0.18 * k / GREY_KNOT for k in KNOTS)
    r.update(float(x) for x in np.geomspace(1e-4, top, 160))
    for cut in (0.01125, 0.010591, 0.018):                        # S-Log3, LogC3 and BT.709's linear segments
        r.update((cut * 0.999, cut, cut * 1.001))
    return sorted(r)


def main():
    rows = [f"# written by tools/isp-reference.py from colour-science {colour.__version__}; do not edit by hand",
            "# curve <code> <reflectance> <10-bit code value> | gamut <code> <m00..m22, BT.709 RGB -> the gamut, row-major>"]
    for code, f in CURVES.items():
        for r in reflectances():
            rows.append(f"curve\t{code}\t{r!r}\t{float(f(r))!r}")
    src = colour.RGB_COLOURSPACES["ITU-R BT.709"]
    for code, name in GAMUTS.items():
        m = colour.matrix_RGB_to_RGB(src, colour.RGB_COLOURSPACES[name], chromatic_adaptation_transform=None)
        rows.append("gamut\t" + code + "\t" + "\t".join(repr(float(v)) for v in m.ravel()))
    OUT.write_text("\n".join(rows) + "\n", encoding="utf-8")
    print(f"{OUT.relative_to(ROOT)}: {len(rows) - 2} rows")


if __name__ == "__main__":
    main()
