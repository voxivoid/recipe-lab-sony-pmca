#!/usr/bin/env python3
"""Print the Sony picture-profile gamma tables IspProfiles carries (Movie, Still, Cine1-4, ITU709(800%)), read
from the ILCE-6500 v1.06 firmware's av-cam.bin and resampled onto the A6000's 96 gamma knots.

    python3 tools/sony-gamma.py path/to/av-cam.bin          # Java: paste into IspProfiles.java
    python3 tools/sony-gamma.py path/to/av-cam.bin rust     # Rust: paste into Recipe Lab Studio's picture_profile.rs

Both apps carry the same integers, so the camera and Studio's editor render a Sony curve alike; test/fixtures/
isp-golden.txt, shared with Studio, holds both to it.

The firmware is not in this repository; the script refuses any other file (sha256 below).

Where the tables are, and how they are named (av-cam.bin, virtual base 0x43541000):
  * Each gamma curve is 2048 little-endian u16: input 0..2047 linear (the same 11-bit domain as the A6000's
    knots), output a 10-bit video code value. Most are preceded by the words 2,2,3,4,4,4,4,3,3,7,id,flag,1.
  * A pointer table (VA 0x44247d88) holds 64 curves, index = slot * 2 + 1 for the video curve.
  * job_EC_CalcCaptureGamma (VA 0x439e8b38) turns a slot into the "capture gamma" a recording's metadata names,
    and VA 0x4362b286 turns that into the RDD 18 / Sony MXF label. MediaInfo (File_Mxf.cpp,
    Mxf_TransferCharacteristic_Sony) names the labels: 0E06.0401.0101.0301..0304 "CINE1..4 of EX1/EX3", 0504
    "R709 800%", 0508 "S-Log2", 0604/0605 "S-Log3". So slots 24..27 are Cine1..4, 29 ITU709(800%), 28 S-Log2,
    31 S-Log3: the firmware's own names. S-Log2 and S-Log3 match the published formulas (rms 0.45 and 0.52 code
    values), which is why IspProfiles keeps the formulas for them.
  * Slots 10 and 22 carry the plain BT.709 label, slot 0 an unnamed Sony one. Their shapes decide: slot 10 is the
    closest to pure BT.709 (ITU709, which IspProfiles keeps as the formula), 22 is Movie, and 0, the most contrast
    and the camera's curve with picture profiles off, is Still. Inferred, not read from the firmware.

Exposure: a table's input puts 18% grey where the camera's exposure for that gamma puts it. The ITU709 slot,
fitted to the BT.709 formula, puts it at input GREY (about 135); the non-log gammas share Sony's ISO range and
metering, so Movie, Still and Cine1-4 are taken to share it (an assumption: Movie's shadows fit BT.709 there too,
Still's and the Cines' cannot be checked against a formula). ITU709(800%) has its own: its shadows are BT.709 with
grey near input 54, fitted the same way. Each table is resampled so its grey input lands on the A6000's grey knot
(76), where every IspProfiles curve has it: a Sony curve exposes like the others and only its shape differs. Past
the table's end (input 2047) a curve holds its top value.
"""

import hashlib
import math
import struct
import sys

SHA256 = "c885310730c313e1ffb6a48ec74af1ff7c4f8246af8c7fc03eda83d7ea6cd497"   # ILCE-6500 v1.06 av-cam.bin
VA_BASE = 0x43541000
SELECTOR = 0x44247D88

# IspProfiles' constant name -> firmware slot
CURVES = [("MOVIE", 22), ("STILL", 0), ("CINE1", 24), ("CINE2", 25), ("CINE3", 26), ("CINE4", 27),
          ("ITU709_800", 29)]
ITU709_SLOT, SLOG2_SLOT, SLOG3_SLOT = 10, 28, 31
GREY_KNOT = 76.0


def knots():
    k = [2 * i for i in range(1, 33)] + list(range(68, 129, 4)) + list(range(136, 257, 8)) \
        + list(range(272, 513, 16)) + list(range(576, 1025, 64)) + list(range(1152, 1921, 128)) + [2047]
    assert len(k) == 96
    return k


def table(fw, slot):
    va = struct.unpack_from("<I", fw, SELECTOR - VA_BASE + 4 * (2 * slot + 1))[0]
    t = struct.unpack_from("<2048H", fw, va - VA_BASE)
    if any(b < a for a, b in zip(t, t[1:])) or t[2047] > 1023:
        raise SystemExit("slot %d: not a gamma table" % slot)
    return t


def at(t, x):
    """the table at a fractional input, linear between entries, held at the ends"""
    if x <= 0:
        return t[0]
    if x >= 2047:
        return t[2047]
    i = int(x)
    return t[i] + (t[i + 1] - t[i]) * (x - i)


def rec709(r):
    return 1023 * (4.5 * r if r < 0.018 else 1.099 * r ** 0.45 - 0.099)


def slog2(r):
    return 64 + 876 * (0.432699 * math.log10(155 * r / 0.9 / 219 + 0.037584) + 0.616596 + 0.03)


def slog3(r):
    return 420 + math.log10((r + 0.01) / 0.19) * 261.5 if r >= 0.01125 else r * (171.2102946929 - 95) / 0.01125 + 95


def fit(t, f, top):
    """(rms, grey input, offset) of the table against a formula, below code {@code top}"""
    best = None
    for o10 in range(-100, 101, 5):
        o = o10 / 10
        for g10 in range(300, 2000):
            g = g10 / 10
            err = [t[i] - f(0.18 * (i - o) / g) for i in range(1, 2048, 2) if t[i] <= top and i > o]
            rms = math.sqrt(sum(e * e for e in err) / len(err))
            if best is None or rms < best[0]:
                best = (rms, g, o)
    return best


def emit(lang, name, slot, out):
    rows = [", ".join(map(str, out[i:i + 24])) for i in range(0, 96, 24)]
    if lang == "rust":
        body = ",\n".join("    " + r for r in rows)
        return "/// firmware slot %d\nconst %s_TABLE: [u16; 96] = [\n%s,\n];" % (slot, name, body)
    body = ",\n".join("        " + r for r in rows)
    return ("    private static final int[] %s_TABLE = {" % name).ljust(72) + "// firmware slot %d\n%s };" % (slot, body)


def main():
    if len(sys.argv) not in (2, 3) or (len(sys.argv) == 3 and sys.argv[2] not in ("java", "rust")):
        raise SystemExit(__doc__)
    lang = sys.argv[2] if len(sys.argv) == 3 else "java"
    fw = open(sys.argv[1], "rb").read()
    if hashlib.sha256(fw).hexdigest() != SHA256:
        raise SystemExit("not the ILCE-6500 v1.06 av-cam.bin")

    for name, slot, f, top in (("S-Log2", SLOG2_SLOT, slog2, 1000), ("S-Log3", SLOG3_SLOT, slog3, 1000)):
        rms, g, o = fit(table(fw, slot), f, top)
        print("// %s (slot %d) against its formula: rms %.2f code values, grey at input %.1f" % (name, slot, rms, g))
        if rms > 1:
            raise SystemExit("%s does not match its formula: wrong firmware or wrong slot" % name)
    rms, grey, o = fit(table(fw, ITU709_SLOT), rec709, 600)
    print("// ITU709 (slot %d) against BT.709 below code 600: rms %.2f, grey at input %.1f" % (ITU709_SLOT, rms, grey))

    for name, slot in CURVES:
        t = table(fw, slot)
        g = grey
        if name == "ITU709_800":                    # its own exposure: BT.709 below the shoulder, grey far lower
            rms, g, o = fit(t, rec709, 600)
            print("// ITU709(800%%) (slot %d) against BT.709 below code 600: rms %.2f, grey at input %.1f" % (slot, rms, g))
        out = [int(round(at(t, k * g / GREY_KNOT))) for k in knots()]
        print(emit(lang, name, slot, out))


if __name__ == "__main__":
    main()
