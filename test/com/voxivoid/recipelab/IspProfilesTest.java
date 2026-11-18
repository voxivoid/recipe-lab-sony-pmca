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

    @Test void gammaMatchesTheReferenceImplementation() {
        assertArrayEquals(SLOG2, IspProfiles.gamma(IspProfiles.SLOG2));
        assertArrayEquals(SLOG3, IspProfiles.gamma(IspProfiles.SLOG3));
        assertArrayEquals(SLOG3, IspProfiles.gamma(IspProfiles.SLOG3_CINE), "one curve, two gamuts");
        assertArrayEquals(LOGC3, IspProfiles.gamma(IspProfiles.LOGC3));
        assertArrayEquals(ITU709, IspProfiles.gamma(IspProfiles.ITU709));
        assertArrayEquals(HLG, IspProfiles.gamma(IspProfiles.HLG));
        assertNull(IspProfiles.gamma(IspProfiles.OFF));
    }

    @Test void middleGreyLandsWhereEachSpecPutsIt() {
        int grey = 34;                                             // knot 76 = 18% grey
        assertEquals(76, IspProfiles.KNOTS[grey]);
        assertEquals(347, IspProfiles.gamma(IspProfiles.SLOG2)[grey], "S-Log2: 32% IRE = 347");
        assertEquals(420, IspProfiles.gamma(IspProfiles.SLOG3)[grey], "S-Log3: code 420");
        assertEquals(400, IspProfiles.gamma(IspProfiles.LOGC3)[grey], "LogC3 EI800: code 400");
        assertEquals(418, IspProfiles.gamma(IspProfiles.ITU709)[grey], "BT.709: 40.9%");
        assertEquals(387, IspProfiles.gamma(IspProfiles.HLG)[grey], "HLG: ~38% (BT.2408)");
    }

    @Test void everyCurveIsMonotoneAndTenBit() {
        for (int p = 1; p < IspProfiles.COUNT; p++) {
            int[] g = IspProfiles.gamma(p);
            for (int i = 0; i < 96; i++) {
                assertTrue(g[i] >= 0 && g[i] <= 1023, "profile " + p);
                if (i > 0) assertTrue(g[i] >= g[i - 1], "profile " + p + " knot " + i);
            }
        }
    }

    @Test void gamutMatricesMatchTheReference() {
        assertArrayEquals(new int[] { 9274, 5616, 1494, 1261, 13092, 2031, 366, 1779, 14239 }, IspProfiles.gamutQ14(IspProfiles.SLOG3));
        assertArrayEquals(IspProfiles.gamutQ14(IspProfiles.SLOG3), IspProfiles.gamutQ14(IspProfiles.SLOG2), "S-Gamut = S-Gamut3 primaries");
        assertArrayEquals(new int[] { 10579, 4245, 1560, 1434, 12447, 2503, 606, 2118, 13660 }, IspProfiles.gamutQ14(IspProfiles.SLOG3_CINE));
        assertArrayEquals(new int[] { 10279, 5395, 710, 1132, 15066, 186, 269, 1442, 14673 }, IspProfiles.gamutQ14(IspProfiles.HLG));
        assertArrayEquals(new int[] { 10343, 4437, 1604, 603, 12993, 2788, 285, 2438, 13661 }, IspProfiles.gamutQ14(IspProfiles.LOGC3));
        assertArrayEquals(new int[] { 16384, 0, 0, 0, 16384, 0, 0, 0, 16384 }, IspProfiles.gamutQ14(IspProfiles.ITU709), "709 keeps the camera's colour");
    }

    @Test void everyGamutKeepsWhite() {
        for (int p = 1; p < IspProfiles.COUNT; p++) {
            int[] q = IspProfiles.gamutQ14(p);
            for (int r = 0; r < 3; r++) assertEquals(16384, q[r * 3] + q[r * 3 + 1] + q[r * 3 + 2], "profile " + p + " row " + r);
        }
    }

    @Test void theFileIsWhatTheHelperReads() {
        byte[] b = IspProfiles.encode(IspProfiles.SLOG2);
        assertEquals(432, b.length);
        assertEquals(IspProfiles.FILE_SIZE, b.length);
        assertEquals("RLPP", new String(b, 0, 4, java.nio.charset.StandardCharsets.US_ASCII));
        assertEquals(1, le(b, 4));
        assertEquals(IspProfiles.F_GAMMA | IspProfiles.F_MATRIX, le(b, 8));
        assertEquals(106, le(b, 12));
        assertEquals(852, le(b, 12 + 95 * 4));
        assertEquals(9274, le(b, 12 + 96 * 4));
        int cine = le(IspProfiles.encode(IspProfiles.SLOG3_CINE), 12 + 96 * 4 + 7 * 4);
        assertEquals(2118, cine);
        assertThrows(IllegalArgumentException.class, () -> IspProfiles.encode(IspProfiles.OFF));
    }

    @Test void everyProfileFileCarriesItsCurveAndGamut() {
        for (int p = 1; p < IspProfiles.COUNT; p++) {
            byte[] e = IspProfiles.encode(p);
            int[] g = IspProfiles.gamma(p), t = IspProfiles.gamutQ14(p);
            for (int i = 0; i < 96; i++) assertEquals(g[i], le(e, 12 + i * 4), "profile " + p);
            for (int i = 0; i < 9; i++) assertEquals(t[i], le(e, 12 + 96 * 4 + i * 4), "profile " + p);
        }
    }

    @Test void theCommandRunsOutsideTheChroot() {
        assertEquals("/android/data/app-lib/x/librlisp.so apply /android/data/data/x/files/isp",
                IspProfiles.command("/data/app-lib/x/librlisp.so", "apply", "/data/data/x/files/isp"));
        StringBuilder longDir = new StringBuilder("/");
        for (int i = 0; i < 300; i++) longDir.append('d');
        assertNull(IspProfiles.command("/h", "apply", longDir.toString()), "the shell takes 255 characters at most");
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
    }

    @Test void preferencesRoundTripAndUnknownIsOff() {
        for (int p = 0; p < IspProfiles.COUNT; p++) assertEquals(p, IspProfiles.parse(IspProfiles.CODES[p]));
        assertEquals(IspProfiles.OFF, IspProfiles.parse(null));
        assertEquals(IspProfiles.OFF, IspProfiles.parse("cine4"));
    }

    @Test void leftAndRightWalkEveryProfileAndWrap() {
        assertEquals(IspProfiles.SLOG2, IspProfiles.next(IspProfiles.OFF, +1));
        assertEquals(IspProfiles.LOGC3, IspProfiles.next(IspProfiles.OFF, -1));
        assertEquals(IspProfiles.OFF, IspProfiles.next(IspProfiles.LOGC3, +1));
        for (int p = 1; p < IspProfiles.COUNT; p++) assertFalse(IspProfiles.label(p).isEmpty());
        assertEquals("Off", IspProfiles.label(IspProfiles.OFF));
    }

    private static int le(byte[] b, int o) { return (b[o] & 0xFF) | (b[o + 1] & 0xFF) << 8 | (b[o + 2] & 0xFF) << 16 | b[o + 3] << 24; }
}
