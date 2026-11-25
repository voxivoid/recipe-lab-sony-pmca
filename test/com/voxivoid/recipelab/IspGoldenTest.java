package com.voxivoid.recipelab;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * test/fixtures/isp-golden.txt: the gamma words and matrix this app writes for a set of looks, one per line. Recipe Lab
 * Studio keeps the same file (rust/tests/fixtures/isp-golden.txt) and checks its port of this class against it, so
 * the editor renders what the camera shoots. The file is written from this class: after a change to a curve or
 * matrix, run the tests with RECIPELAB_WRITE_GOLDEN=1 and copy the file to Studio.
 */
class IspGoldenTest {

    static final Path FILE = Paths.get("test/fixtures/isp-golden.txt");
    static final String HEAD =
            "# Recipe Lab (camera) IspProfiles.gamma and .matrixQ14 for each look; the same file is in Recipe Lab Studio.\n"
          + "# Written by IspGoldenTest (RECIPELAB_WRITE_GOLDEN=1 tools/test.sh IspGolden), never by hand.\n"
          + "# name|gamma,color-mode,black-level,black-gamma,black-gamma-range,knee (0 = off),knee-slope,color-saturation,color-phase,s-curve|96 gamma words|Q14 matrix\n";

    /** every curve neutral, then looks with each adjustment both ways and at its ends, on formula and table curves */
    static final String[][] LOOKS = {
        { "slog2", "s-log2,camera,0,0,middle,0,0,0,0,0" },
        { "slog3", "s-log3,camera,0,0,middle,0,0,0,0,0" },
        { "itu709", "itu709,camera,0,0,middle,0,0,0,0,0" },
        { "hlg", "hlg,camera,0,0,middle,0,0,0,0,0" },
        { "night", "s-log3,s-gamut3-cine,-4,3,wide,90,-2,12,-3,5" },
        { "lowkey", "itu709,bt2020,7,-5,narrow,80,4,-20,6,-6" },
        { "maxup", "hlg,bt2020,-15,7,middle,105,-5,32,-7,7" },
        { "maxdown", "s-log2,s-gamut3,15,-7,wide,75,5,-32,7,-7" },
        { "rounding", "s-log3,camera,0,0,middle,0,0,-32,-7,0" },     // a row that rounds to 16385: the white fix takes it back
        { "scurve", "s-log2,camera,0,0,middle,0,0,0,0,3" },
        { "movie", "movie,camera,0,0,middle,0,0,0,0,0" },
        { "still", "still,camera,0,0,middle,0,0,0,0,0" },
        { "cine1", "cine1,camera,0,0,middle,0,0,0,0,0" },
        { "cine2", "cine2,camera,0,0,middle,0,0,0,0,0" },
        { "cine3", "cine3,camera,0,0,middle,0,0,0,0,0" },
        { "cine4", "cine4,camera,0,0,middle,0,0,0,0,0" },
        { "itu709-800", "itu709-800,camera,0,0,middle,0,0,0,0,0" },
        { "cine4-night", "cine4,s-gamut3-cine,-4,3,wide,90,-2,12,-3,5" },
        { "movie-lowkey", "movie,bt2020,7,-5,narrow,80,4,-20,6,-6" },
        { "still-maxup", "still,camera,-15,7,middle,105,-5,32,-7,7" },
        { "itu709-800-maxdown", "itu709-800,s-gamut3,15,-7,wide,75,5,-32,7,-7" },
        { "cine1-scurve", "cine1,camera,0,0,middle,0,0,0,0,3" },
    };

    /** a look from the file's spelling of it */
    static IspProfiles.Look look(String spec) {
        String[] f = spec.split(",");
        int knee = Integer.parseInt(f[5]);
        IspProfiles.Look l = new IspProfiles.Look(IspProfiles.indexOf(IspProfiles.CURVE_CODES, f[0]),
                IspProfiles.indexOf(IspProfiles.GAMUT_CODES, f[1]), Integer.parseInt(f[2]), Integer.parseInt(f[3]),
                IspProfiles.indexOf(IspProfiles.RANGE_CODES, f[4]), knee == 0 ? IspProfiles.KNEE_OFF : knee,
                Integer.parseInt(f[6]), Integer.parseInt(f[7]), Integer.parseInt(f[8]), Integer.parseInt(f[9]));
        assertNull(l.problem(), spec);
        return l;
    }

    static String join(int[] v) {
        StringBuilder s = new StringBuilder();
        for (int i = 0; i < v.length; i++) s.append(i == 0 ? "" : ",").append(v[i]);
        return s.toString();
    }

    static String row(String name, String spec) {
        IspProfiles.Look l = look(spec);
        return name + "|" + spec + "|" + join(IspProfiles.gamma(l)) + "|" + join(IspProfiles.matrixQ14(l));
    }

    @Test void theFileIsWhatThisAppWrites() throws Exception {
        StringBuilder want = new StringBuilder(HEAD);
        for (String[] l : LOOKS) want.append(row(l[0], l[1])).append('\n');
        if ("1".equals(System.getenv("RECIPELAB_WRITE_GOLDEN"))) Files.write(FILE, want.toString().getBytes(StandardCharsets.UTF_8));
        assertEquals(want.toString(), new String(Files.readAllBytes(FILE), StandardCharsets.UTF_8),
                "run RECIPELAB_WRITE_GOLDEN=1 tools/test.sh IspGolden, then copy the file to Studio");
    }

    @Test void everyCurveIsInTheFile() throws Exception {
        List<String> seen = new ArrayList<>();
        for (String line : Files.readAllLines(FILE, StandardCharsets.UTF_8)) {
            if (line.startsWith("#")) continue;
            String gamma = line.split("\\|")[1].split(",")[0];
            if (!seen.contains(gamma)) seen.add(gamma);
        }
        for (int c = 1; c < IspProfiles.CURVES; c++) assertTrue(seen.contains(IspProfiles.CURVE_CODES[c]), IspProfiles.CURVE_CODES[c]);
    }

    @Test void theFileKeepsStudiosOldRows() throws Exception {
        // the rows Studio's test had before the file carried its looks: the same words, so nothing it renders moved
        String slog3 = "127,159,186,206,224,238,252,264,274,284,293,301,309,316,323,330,336,342,347,352,357,362,367,371,376,380,384,387,391,395,398,402,408,414,420,426,431,436,441,445,450,454,458,462,466,470,473,477,483,490,496,501,507,512,517,522,526,530,535,539,543,546,550,554,560,567,573,579,584,589,594,599,604,608,612,616,620,624,628,631,645,657,667,677,686,695,702,710,723,735,746,756,765,773,781,788";
        assertEquals("slog3|s-log3,camera,0,0,middle,0,0,0,0,0|" + slog3 + "|16384,0,0,0,16384,0,0,0,16384", row("slog3", LOOKS[1][1]));
        assertTrue(row("night", LOOKS[4][1]).endsWith("|13555,2746,83,-206,12338,4252,1043,-2444,17785"));
        assertTrue(row("rounding", LOOKS[8][1]).endsWith("|1435,11778,3171,1436,11777,3171,1436,11778,3170"));
    }
}
