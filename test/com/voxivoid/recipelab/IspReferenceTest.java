package com.voxivoid.recipelab;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * The picture-profile curves and gamut matrices against colour-science, an independent library
 * (test/fixtures/isp-reference.tsv, written by tools/isp-reference.py). IspProfilesTest's golden words come from
 * the same formulas typed in again; this catches a formula or constant that is wrong in both. It proves the tables
 * are what the specifications define, not what the camera does with them.
 */
class IspReferenceTest {

    /**
     * 10-bit code values; the table rounds to whole codes, so this is far inside what could show. Every curve but HLG
     * agrees to 1e-12; HLG is off by up to 0.0025 because HLG_WHITE is 0.26496, the reference's 0.2649626 rounded.
     */
    static final double CODE_TOLERANCE = 0.01;
    /** matrix entries; colour-science uses the published normalised primary matrices, which are rounded */
    static final double MATRIX_TOLERANCE = 1e-4;

    static List<String> rows() throws Exception {
        return Files.readAllLines(Paths.get("test/fixtures/isp-reference.tsv"), StandardCharsets.UTF_8);
    }

    @Test void everyCurveMatchesColourScience() throws Exception {
        int[] seen = new int[IspProfiles.CURVES];
        for (String line : rows()) {
            if (line.startsWith("#") || !line.startsWith("curve\t")) continue;
            String[] f = line.split("\t");
            int curve = IspProfiles.indexOf(IspProfiles.CURVE_CODES, f[1]);
            assertTrue(curve > 0, "unknown curve " + f[1]);
            double r = Double.parseDouble(f[2]), want = Double.parseDouble(f[3]);
            double got = IspProfiles.curveCode(curve, r);
            assertEquals(want, got, CODE_TOLERANCE, f[1] + " at reflectance " + r);
            seen[curve]++;
        }
        for (int c = 1; c < IspProfiles.CURVES; c++) assertTrue(seen[c] > 200, IspProfiles.CURVE_CODES[c] + ": " + seen[c] + " rows");
    }

    @Test void theHlgWhiteIsBt2408s() {
        // the reference derives it (75% HLG back through the inverse OETF); the class hard-codes it
        assertEquals(0.75, IspProfiles.hlg(IspProfiles.HLG_WHITE), 1e-5);
    }

    @Test void everyGamutMatchesColourScience() throws Exception {
        int seen = 0;
        for (String line : rows()) {
            if (!line.startsWith("gamut\t")) continue;
            String[] f = line.split("\t");
            int g = IspProfiles.indexOf(IspProfiles.GAMUT_CODES, f[1]);
            assertTrue(g >= 0, "unknown gamut " + f[1]);
            double[][] m = IspProfiles.gamut(g);
            for (int i = 0; i < 9; i++)
                assertEquals(Double.parseDouble(f[2 + i]), m[i / 3][i % 3], MATRIX_TOLERANCE, f[1] + " entry " + i);
            seen++;
        }
        assertEquals(IspProfiles.GAMUTS, seen, "every colour mode has a reference");
    }
}
