package com.voxivoid.recipelab;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/**
 * Picture profiles: the gamma words and gamut matrices the helper is given, its input file, its command line and how its
 * log is read. The golden words come from an independent Python implementation of the same published formulas
 * (a6000-qemu mods/look make_hwgamma.py / ispctl.py), not from this class.
 */
class IspProfilesTest {

    static final int[] SLOG2 = { 106, 120, 133, 145, 157, 167, 177, 186, 195, 204, 212, 219, 227, 234, 240, 247, 253, 259, 265, 270, 276, 281, 286, 291, 295, 300, 305, 309, 313, 317, 321, 325, 333, 340, 347, 354, 360, 367, 373, 378, 384, 389, 395, 400, 405, 409, 414, 418, 427, 435, 443, 451, 458, 465, 471, 478, 484, 490, 495, 501, 506, 511, 516, 521, 530, 539, 547, 555, 563, 570, 577, 584, 590, 596, 602, 608, 613, 619, 624, 629, 648, 664, 680, 694, 706, 718, 730, 740, 759, 776, 791, 806, 819, 831, 842, 852 };
    static final int[] SLOG3 = { 127, 159, 186, 206, 224, 238, 252, 264, 274, 284, 293, 301, 309, 316, 323, 330, 336, 342, 347, 352, 357, 362, 367, 371, 376, 380, 384, 387, 391, 395, 398, 402, 408, 414, 420, 426, 431, 436, 441, 445, 450, 454, 458, 462, 466, 470, 473, 477, 483, 490, 496, 501, 507, 512, 517, 522, 526, 530, 535, 539, 543, 546, 550, 554, 560, 567, 573, 579, 584, 589, 594, 599, 604, 608, 612, 616, 620, 624, 628, 631, 645, 657, 667, 677, 686, 695, 702, 710, 723, 735, 746, 756, 765, 773, 781, 788 };
    static final int[] LOGC3 = { 121, 147, 171, 191, 208, 223, 236, 248, 258, 268, 276, 285, 292, 299, 306, 312, 318, 324, 329, 334, 339, 344, 348, 353, 357, 361, 365, 368, 372, 375, 379, 382, 388, 394, 400, 405, 410, 415, 420, 425, 429, 433, 437, 441, 445, 448, 452, 455, 461, 468, 473, 479, 484, 489, 494, 498, 503, 507, 511, 515, 519, 522, 526, 529, 536, 542, 548, 554, 559, 564, 569, 573, 578, 582, 586, 590, 594, 598, 601, 605, 618, 629, 639, 649, 658, 666, 673, 680, 693, 705, 715, 725, 734, 742, 749, 756 };
    static final int[] ITU709 = { 22, 44, 65, 87, 107, 125, 141, 156, 171, 184, 196, 208, 219, 230, 241, 251, 261, 270, 279, 288, 297, 305, 313, 321, 329, 337, 344, 352, 359, 366, 373, 380, 393, 406, 418, 431, 442, 454, 465, 476, 487, 497, 507, 517, 527, 537, 546, 556, 574, 592, 609, 625, 641, 657, 672, 687, 702, 716, 730, 744, 757, 771, 784, 796, 821, 845, 869, 891, 913, 935, 956, 976, 996, 1016, 1023, 1023, 1023, 1023, 1023, 1023, 1023, 1023, 1023, 1023, 1023, 1023, 1023, 1023, 1023, 1023, 1023, 1023, 1023, 1023, 1023, 1023 };
    static final int[] HLG = { 63, 89, 109, 126, 140, 154, 166, 178, 188, 199, 208, 217, 226, 235, 243, 251, 259, 266, 274, 281, 288, 294, 301, 308, 314, 320, 326, 332, 338, 344, 350, 355, 366, 377, 387, 397, 407, 416, 426, 435, 444, 453, 461, 470, 478, 486, 494, 502, 518, 532, 545, 558, 569, 580, 590, 600, 609, 618, 627, 635, 642, 650, 657, 664, 677, 689, 700, 711, 721, 730, 739, 748, 756, 764, 772, 779, 786, 793, 799, 806, 829, 849, 868, 885, 900, 914, 927, 940, 962, 982, 1000, 1016, 1023, 1023, 1023, 1023 };

    @Test void theKnotsAreTheIspTable() {
        assertEquals(96, IspProfiles.KNOTS.length);
        assertEquals(2, IspProfiles.KNOTS[0]);
        assertEquals(64, IspProfiles.KNOTS[31]);
        assertEquals(68, IspProfiles.KNOTS[32]);
        assertEquals(1920, IspProfiles.KNOTS[94]);
        assertEquals(2047, IspProfiles.KNOTS[95]);
        for (int i = 1; i < 96; i++) assertTrue(IspProfiles.KNOTS[i] > IspProfiles.KNOTS[i - 1]);
    }

    // the six built-in profiles: a curve and a colour mode, no adjustment
    static final IspProfiles.Look P_SLOG2 = new IspProfiles.Look(IspProfiles.SLOG2, IspProfiles.SGAMUT3),
            P_SLOG3_CINE = new IspProfiles.Look(IspProfiles.SLOG3, IspProfiles.SGAMUT3_CINE),
            P_SLOG3 = new IspProfiles.Look(IspProfiles.SLOG3, IspProfiles.SGAMUT3),
            P_ITU709 = new IspProfiles.Look(IspProfiles.ITU709, IspProfiles.CAMERA),
            P_HLG = new IspProfiles.Look(IspProfiles.HLG, IspProfiles.BT2020),
            P_LOGC3 = new IspProfiles.Look(IspProfiles.LOGC3, IspProfiles.AWG3);

    /** a look with one adjustment set: the curve and gamut of {@code base}, everything else neutral but {@code f} */
    static IspProfiles.Look with(IspProfiles.Look b, int black, int bg, int range, int knee, int slope, int sat, int phase, int con) {
        return new IspProfiles.Look(b.curve, b.gamut, black, bg, range, knee, slope, sat, phase, con);
    }

    @Test void gammaMatchesTheReferenceImplementation() {
        assertArrayEquals(SLOG2, IspProfiles.gamma(P_SLOG2));
        assertArrayEquals(SLOG3, IspProfiles.gamma(P_SLOG3));
        assertArrayEquals(SLOG3, IspProfiles.gamma(P_SLOG3_CINE), "one curve, two gamuts");
        assertArrayEquals(LOGC3, IspProfiles.gamma(P_LOGC3));
        assertArrayEquals(ITU709, IspProfiles.gamma(P_ITU709));
        assertArrayEquals(HLG, IspProfiles.gamma(P_HLG));
        assertNull(IspProfiles.gamma(null));
    }

    @Test void middleGreyLandsWhereEachSpecPutsIt() {
        int grey = 34;                                             // knot 76 = 18% grey
        assertEquals(76, IspProfiles.KNOTS[grey]);
        assertEquals(347, IspProfiles.gamma(P_SLOG2)[grey], "S-Log2: 32% IRE = 347");
        assertEquals(420, IspProfiles.gamma(P_SLOG3)[grey], "S-Log3: code 420");
        assertEquals(400, IspProfiles.gamma(P_LOGC3)[grey], "LogC3 EI800: code 400");
        assertEquals(418, IspProfiles.gamma(P_ITU709)[grey], "BT.709: 40.9%");
        assertEquals(387, IspProfiles.gamma(P_HLG)[grey], "HLG: ~38% (BT.2408)");
    }

    @Test void gamutMatricesMatchTheReference() {
        assertArrayEquals(new int[] { 9274, 5616, 1494, 1261, 13092, 2031, 366, 1779, 14239 }, IspProfiles.matrixQ14(P_SLOG3));
        assertArrayEquals(IspProfiles.matrixQ14(P_SLOG3), IspProfiles.matrixQ14(P_SLOG2), "S-Gamut = S-Gamut3 primaries");
        assertArrayEquals(new int[] { 10579, 4245, 1560, 1434, 12447, 2503, 606, 2118, 13660 }, IspProfiles.matrixQ14(P_SLOG3_CINE));
        assertArrayEquals(new int[] { 10279, 5395, 710, 1132, 15066, 186, 269, 1442, 14673 }, IspProfiles.matrixQ14(P_HLG));
        assertArrayEquals(new int[] { 10343, 4437, 1604, 603, 12993, 2788, 285, 2438, 13661 }, IspProfiles.matrixQ14(P_LOGC3));
        assertArrayEquals(new int[] { 16384, 0, 0, 0, 16384, 0, 0, 0, 16384 }, IspProfiles.matrixQ14(P_ITU709), "the camera's colour, untouched");
    }

    // ------------------------------------------------------------ the adjustments
    @Test void theKneeBendsOnlyAboveItsPoint() {
        int[] base = IspProfiles.gamma(P_ITU709), knee = IspProfiles.gamma(with(P_ITU709, 0, 0, 1, 90, 0, 0, 0, 0));
        int kp = (int) Math.round(0.90 * 1023);
        boolean bent = false;
        for (int i = 0; i < 96; i++) {
            if (base[i] <= kp) assertEquals(base[i], knee[i], "below the point: knot " + i);
            else { assertTrue(knee[i] <= base[i], "above: knot " + i); bent |= knee[i] < base[i]; }
        }
        assertTrue(bent);
        assertTrue(knee[73] < 1023, "a 709 highlight that clipped now rolls off");
        int[] steep = IspProfiles.gamma(with(P_ITU709, 0, 0, 1, 90, IspProfiles.SLOPE_MAX, 0, 0, 0));
        int[] flat = IspProfiles.gamma(with(P_ITU709, 0, 0, 1, 90, -IspProfiles.SLOPE_MAX, 0, 0, 0));
        assertTrue(steep[72] > knee[72] && knee[72] > flat[72], "slope +5 keeps more of the highlight than -5");
    }

    @Test void blackGammaReshapesOnlyItsRange() {
        int[] base = IspProfiles.gamma(P_SLOG3);
        for (int range = 0; range < 3; range++) {
            int[] up = IspProfiles.gamma(with(P_SLOG3, 0, 7, range, IspProfiles.KNEE_OFF, 0, 0, 0, 0));
            int[] down = IspProfiles.gamma(with(P_SLOG3, 0, -7, range, IspProfiles.KNEE_OFF, 0, 0, 0, 0));
            double top = IspProfiles.RANGE_TOP[range] * 1023;
            for (int i = 0; i < 96; i++) {
                if (base[i] >= top) { assertEquals(base[i], up[i], "above the range: knot " + i); assertEquals(base[i], down[i]); }
                else { assertTrue(up[i] >= base[i]); assertTrue(down[i] <= base[i]); }
            }
            assertTrue(up[0] > base[0] && down[0] < base[0], "range " + range);
        }
    }

    @Test void theSCurvePivotsOnGrey() {
        int[] base = IspProfiles.gamma(P_ITU709), more = IspProfiles.gamma(with(P_ITU709, 0, 0, 1, IspProfiles.KNEE_OFF, 0, 0, 0, 7)),
                less = IspProfiles.gamma(with(P_ITU709, 0, 0, 1, IspProfiles.KNEE_OFF, 0, 0, 0, -7));
        assertEquals(base[34], more[34], 1, "grey stays");
        assertEquals(base[34], less[34], 1);
        assertTrue(more[10] < base[10] && less[10] > base[10], "shadows: deeper with more contrast");
        assertTrue(more[60] > base[60] && less[60] < base[60], "highlights: brighter with more contrast");
    }

    @Test void blackLevelMovesBlackAndLeavesWhite() {
        int[] base = IspProfiles.gamma(P_SLOG2), up = IspProfiles.gamma(with(P_SLOG2, 15, 0, 1, IspProfiles.KNEE_OFF, 0, 0, 0, 0)),
                down = IspProfiles.gamma(with(P_SLOG2, -15, 0, 1, IspProfiles.KNEE_OFF, 0, 0, 0, 0));
        double shift = 15 * IspProfiles.BLACK_STEP * (1 - base[0] / 1023.0);
        assertEquals(base[0] + shift, up[0], 1);
        assertEquals(base[0] - shift, down[0], 1);
        assertTrue(Math.abs(up[95] - base[95]) < Math.abs(up[0] - base[0]), "less at the top than at black");
    }

    @Test void saturationAndPhaseKeepGrey() {
        for (int g = 0; g < IspProfiles.GAMUTS; g++)
            for (int sat : new int[] { -32, -10, 0, 17, 32 })
                for (int ph : new int[] { -7, 0, 3, 7 }) {
                    int[] q = IspProfiles.matrixQ14(new IspProfiles.Look(IspProfiles.SLOG3, g, 0, 0, 1, IspProfiles.KNEE_OFF, 0, sat, ph, 0));
                    for (int r = 0; r < 3; r++) assertEquals(16384, q[r * 3] + q[r * 3 + 1] + q[r * 3 + 2], "gamut " + g + " sat " + sat + " phase " + ph);
                }
    }

    @Test void saturationMinus32IsBlackAndWhite() {
        int[] q = IspProfiles.matrixQ14(with(P_ITU709, 0, 0, 1, IspProfiles.KNEE_OFF, 0, -32, 0, 0));
        for (int r = 1; r < 3; r++) for (int c = 0; c < 3; c++) assertEquals(q[c], q[r * 3 + c], 1, "every row is luma");
        assertEquals(Math.round(0.7152 * 16384), q[1], 1);
    }

    @Test void phaseTurnsHues() {
        double[][] m = IspProfiles.phase(7);
        double[] red = { m[0][0], m[1][0], m[2][0] };                 // where pure red goes
        assertTrue(red[0] > red[1] && red[0] > red[2], "still mostly red");
        assertNotEquals(red[1], red[2], 1e-6, "but turned");
        assertArrayEquals(new double[] { 1, 0, 0 }, IspProfiles.phase(0)[0], 1e-12);
    }

    @Test void everyCombinationIsMonotoneAndTenBit() {
        int[][] values = { { -15, 0, 15 }, { -7, 0, 7 }, { 0, 1, 2 }, { IspProfiles.KNEE_OFF, 75, 105 }, { -5, 5 }, { -7, 0, 7 } };
        for (int c = 1; c < IspProfiles.CURVES; c++)
            for (int bl : values[0]) for (int bg : values[1]) for (int rg : values[2]) for (int k : values[3]) for (int sl : values[4]) for (int con : values[5]) {
                IspProfiles.Look l = new IspProfiles.Look(c, IspProfiles.CAMERA, bl, bg, rg, k, sl, 0, 0, con);
                assertNull(l.problem());
                int[] g = IspProfiles.gamma(l);
                for (int i = 0; i < 96; i++) {
                    assertTrue(g[i] >= 0 && g[i] <= 1023, l + " knot " + i);
                    if (i > 0) assertTrue(g[i] >= g[i - 1], l + " knot " + i);
                }
            }
    }

    // ------------------------------------------------------------ the look as a value
    @Test void aLookOutOfRangeSaysWhichValue() {
        assertNull(P_SLOG3.problem());
        assertEquals("gamma", new IspProfiles.Look(IspProfiles.OFF, 0).problem());
        assertEquals("gamma", new IspProfiles.Look(IspProfiles.CURVES, 0).problem());
        assertEquals("color-mode", new IspProfiles.Look(1, IspProfiles.GAMUTS).problem());
        assertEquals("black-level", with(P_SLOG3, 16, 0, 1, 74, 0, 0, 0, 0).problem());
        assertEquals("black-gamma", with(P_SLOG3, 0, -8, 1, 74, 0, 0, 0, 0).problem());
        assertEquals("black-gamma-range", with(P_SLOG3, 0, 0, 3, 74, 0, 0, 0, 0).problem());
        assertEquals("knee", with(P_SLOG3, 0, 0, 1, 106, 0, 0, 0, 0).problem());
        assertEquals("knee", with(P_SLOG3, 0, 0, 1, 50, 0, 0, 0, 0).problem());
        assertEquals("knee-slope", with(P_SLOG3, 0, 0, 1, 90, 6, 0, 0, 0).problem());
        assertEquals("color-saturation", with(P_SLOG3, 0, 0, 1, 74, 0, 33, 0, 0).problem());
        assertEquals("color-phase", with(P_SLOG3, 0, 0, 1, 74, 0, 0, -8, 0).problem());
        assertEquals("s-curve", with(P_SLOG3, 0, 0, 1, 74, 0, 0, 0, 8).problem());
    }

    @Test void valuesThatDoNothingDoNotMakeTwoLooksDiffer() {
        assertEquals(P_SLOG3, with(P_SLOG3, 0, 0, IspProfiles.WIDE, IspProfiles.KNEE_OFF, 3, 0, 0, 0), "a range with no black gamma, a slope with no knee");
        assertEquals(P_SLOG3.hashCode(), with(P_SLOG3, 0, 0, IspProfiles.WIDE, IspProfiles.KNEE_OFF, 3, 0, 0, 0).hashCode());
        assertNotEquals(P_SLOG3, with(P_SLOG3, 0, 2, IspProfiles.WIDE, IspProfiles.KNEE_OFF, 0, 0, 0, 0));
        assertNotEquals(P_SLOG3, P_SLOG3_CINE);
        assertArrayEquals(IspProfiles.gamma(P_SLOG3), IspProfiles.gamma(with(P_SLOG3, 0, 0, IspProfiles.WIDE, IspProfiles.KNEE_OFF, 3, 0, 0, 0)));
    }

    @Test void labels() {
        assertEquals("S-Log3 / S-Gamut3.Cine", P_SLOG3_CINE.name());
        assertEquals("ITU709 / Camera", P_ITU709.name());
        assertEquals(Lang.t("value_off"), IspProfiles.curveLabel(IspProfiles.OFF));
        assertEquals(Lang.t("value_off"), IspProfiles.kneeLabel(IspProfiles.KNEE_OFF));
        assertEquals("95%", IspProfiles.kneeLabel(95));
        assertEquals("Wide", IspProfiles.rangeLabel(IspProfiles.WIDE));
        assertEquals(IspProfiles.CURVES, IspProfiles.CURVE_CODES.length);
        assertEquals(IspProfiles.GAMUTS, IspProfiles.GAMUT_CODES.length);
        assertEquals(2, IspProfiles.indexOf(IspProfiles.CURVE_CODES, "s-log3"));
        assertEquals(-1, IspProfiles.indexOf(IspProfiles.CURVE_CODES, "cine4"));
    }

    @Test void theFileIsWhatTheHelperReads() {
        byte[] b = IspProfiles.encode(P_SLOG2);
        assertEquals(432, b.length);
        assertEquals(IspProfiles.FILE_SIZE, b.length);
        assertEquals("RLPP", new String(b, 0, 4, java.nio.charset.StandardCharsets.US_ASCII));
        assertEquals(1, le(b, 4));
        assertEquals(IspProfiles.F_GAMMA | IspProfiles.F_MATRIX, le(b, 8));
        assertEquals(106, le(b, 12));
        assertEquals(852, le(b, 12 + 95 * 4));
        assertEquals(9274, le(b, 12 + 96 * 4));
        assertEquals(2118, le(IspProfiles.encode(P_SLOG3_CINE), 12 + 96 * 4 + 7 * 4));
        assertThrows(IllegalArgumentException.class, () -> IspProfiles.encode(null));
        assertThrows(IllegalArgumentException.class, () -> IspProfiles.encode(with(P_SLOG3, 99, 0, 1, 74, 0, 0, 0, 0)));
    }

    @Test void theFileCarriesTheAdjustedTables() {
        IspProfiles.Look l = new IspProfiles.Look(IspProfiles.HLG, IspProfiles.BT2020, -4, 3, IspProfiles.WIDE, 92, 2, 10, -3, 4);
        byte[] e = IspProfiles.encode(l);
        int[] g = IspProfiles.gamma(l), t = IspProfiles.matrixQ14(l);
        for (int i = 0; i < 96; i++) assertEquals(g[i], le(e, 12 + i * 4));
        for (int i = 0; i < 9; i++) assertEquals(t[i], le(e, 12 + 96 * 4 + i * 4));
    }

    @Test void theCommandRunsOutsideTheChroot() {
        assertEquals("/android/data/app-lib/x/librlisp.so apply /android/data/data/x/files/isp",
                IspProfiles.command("/data/app-lib/x/librlisp.so", "apply", "/data/data/x/files/isp"));
        StringBuilder longDir = new StringBuilder("/");
        for (int i = 0; i < 300; i++) longDir.append('d');
        assertNull(IspProfiles.command("/h", "apply", longDir.toString()), "the shell takes 255 characters at most");
    }

    /** the paths the A6000 gave (boot.log): the command must fit them */
    static final String CAMERA_HELPER = "/data/lib/com.voxivoid.recipelab/lib/librlisp.so", CARD_DIR = "/mnt/sdcard/RECIPLAB/ISP";

    @Test void theKeepAliveLoopFitsTheShellWithTheCamerasPaths() {
        String cmd = IspProfiles.bootCommand(CAMERA_HELPER, CARD_DIR);
        assertNotNull(cmd);
        assertTrue(cmd.length() <= IspProfiles.MAX_COMMAND, cmd.length() + " characters");
        assertTrue(cmd.startsWith("h=/android" + CAMERA_HELPER + ";d=/android" + CARD_DIR + ";"), "outside the chroot");
        StringBuilder longDir = new StringBuilder("/");
        for (int i = 0; i < 200; i++) longDir.append('d');
        assertNull(IspProfiles.bootCommand(CAMERA_HELPER, longDir.toString()), "too long: null, never cut");
    }

    @Test void theKeepAliveLoopAppliesOnceThenReappliesForTwoMinutes() {
        String cmd = IspProfiles.bootCommand(CAMERA_HELPER, CARD_DIR);
        int apply = cmd.indexOf("$h apply $d"), loop = cmd.indexOf("for ");
        assertTrue(apply >= 0 && apply < loop, "one apply before the loop");
        assertEquals(1, count(cmd, "$h apply $d"));
        assertEquals(1, count(cmd, "$h reapply $d"), "the loop only reapplies (a no-op while gamma matches)");
        assertFalse(cmd.replace("&&", "").contains("&"), "foreground: a backgrounded loop does not survive the shell");
        // iterations = product of the two for-lists; the ISP init rewrites gamma 30-60 s after boot
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("for \\w in ([0-9 ]+);do").matcher(cmd);
        int iterations = 1, lists = 0;
        while (m.find()) { iterations *= m.group(1).trim().split(" ").length; lists++; }
        assertEquals(2, lists);
        java.util.regex.Matcher s = java.util.regex.Pattern.compile("sleep (\\d+)").matcher(cmd);
        assertTrue(s.find());
        int sleep = Integer.parseInt(s.group(1));
        assertTrue(sleep <= 2, "the app waits at most this long for the loop to let go of the shell");
        assertTrue(iterations * sleep >= 90, "covers ISP init with margin: " + iterations * sleep + " s");
    }

    @Test void theKeepAliveLoopEndsOnStopOrAppBeforeEveryReapply() {
        String cmd = IspProfiles.bootCommand(CAMERA_HELPER, CARD_DIR);
        int check = cmd.indexOf("[ -f $d/stop -o -f $d/app ]&&exit"), reapply = cmd.indexOf("$h reapply $d");
        assertTrue(check >= 0, "Off (stop) and the open app (app) end it");
        assertTrue(cmd.indexOf("sleep") < check && check < reapply, "sleep, check, then reapply: never a write after the app opened");
        assertFalse(cmd.substring(0, cmd.indexOf("for ")).contains("$d/app"),
                "no early exit on app: a stale file must not stop the boot apply");
    }

    @Test void aDroppedCommandIsSentAgain() {
        assertFalse(IspProfiles.resend(null, IspProfiles.RESEND_POLLS - 1, 0), "the helper gets a moment to start");
        assertTrue(IspProfiles.resend(null, IspProfiles.RESEND_POLLS, 0), "no log: the shell dropped it");
        assertTrue(IspProfiles.resend(null, IspProfiles.RESEND_POLLS, IspProfiles.RESENDS - 1));
        assertFalse(IspProfiles.resend(null, IspProfiles.RESEND_POLLS, IspProfiles.RESENDS), "then it gives up and says so");
        assertFalse(IspProfiles.resend("", IspProfiles.RESEND_POLLS, 0), "an empty log: the helper is running");
        assertFalse(IspProfiles.resend("RLISP 1 apply\n", IspProfiles.RESEND_POLLS, 0), "running: never twice");
        assertTrue(IspProfiles.RESEND_POLLS * IspProfiles.RESENDS * 250 >= 2 * 2000 + 1000,
                "resends outlast the loop's longest sleep plus a reapply");
    }

    private static int count(String s, String part) {
        int n = 0;
        for (int i = s.indexOf(part); i >= 0; i = s.indexOf(part, i + 1)) n++;
        return n;
    }

    @Test void theLogIsReadByItsResultLine() {
        assertEquals(IspProfiles.R_PENDING, IspProfiles.result(null));
        assertEquals(IspProfiles.R_PENDING, IspProfiles.result(""));
        assertEquals(IspProfiles.R_PENDING, IspProfiles.result("RLISP 1 apply\nSETTER gamma original\n"));
        assertEquals(IspProfiles.R_PENDING, IspProfiles.result("RLISP 1 apply\nRESULT O"), "a line still being written");
        assertEquals(IspProfiles.R_OK, IspProfiles.result("RLISP 1 apply\nSETTER gamma original\nRESULT OK\n"));
        assertEquals(IspProfiles.R_OFF, IspProfiles.result("RLISP 1 off\nRESULT OFF\n"));
        assertEquals(IspProfiles.R_PONG, IspProfiles.result("RLISP 1 ping\nRESULT PONG uid=0\n"));
        assertEquals("PONG uid=0", IspProfiles.resultLine("RLISP 1 ping\nRESULT PONG uid=0\n"));
        assertEquals(IspProfiles.R_REFUSED, IspProfiles.result("RLISP 1 apply\nSETTER gamma UNKNOWN\nRESULT REFUSE fw\n"));
        assertEquals(IspProfiles.R_MISMATCH, IspProfiles.result("MISMATCH 3\nRESULT MISMATCH\n"));
        assertEquals(IspProfiles.R_ERROR, IspProfiles.result("RLISP 1 apply\nRESULT ERR open /dev/mem\n"));
        assertEquals(IspProfiles.R_STATUS, IspProfiles.result("RESULT STATUS on\n"));
        assertEquals(IspProfiles.R_PROBE, IspProfiles.result("RLISP 2 probe\nBODY NEX-6 1.03\nRESULT PROBE fail\n"));
        assertEquals(IspProfiles.R_REFUSED, IspProfiles.result("RESULT REFUSE fw 3.21\n"));
    }

    private static int le(byte[] b, int o) { return (b[o] & 0xFF) | (b[o + 1] & 0xFF) << 8 | (b[o + 2] & 0xFF) << 16 | b[o + 3] << 24; }
}
