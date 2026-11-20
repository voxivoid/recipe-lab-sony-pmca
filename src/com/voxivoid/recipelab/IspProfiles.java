package com.voxivoid.recipelab;

/**
 * Picture profiles for the A6000: the published log and video curves (gamma) and gamuts (colour matrix) the camera
 * lacks, with Sony's Picture Profile adjustments on them ({@link Look}), written straight into its image processor by
 * the root helper {@code rlisp} (jni/rlisp/rlisp.c). A profile is part of a recipe (its GAMMA chip and the ones after
 * it). RAM only: the camera is stock again after a power cycle, which is why the app applies the stored one again on
 * every start.
 *
 * Everything here is camera-free (tools/test.sh): the 96 gamma words, the matrices, the file the helper reads,
 * the root command line and how the helper's log is read back. No android.* import may appear in this class.
 *
 * Only profiles whose formulas and primaries are published are here (Sony S-Log2, S-Log3, S-Gamut, S-Gamut3,
 * S-Gamut3.Cine; ITU-R BT.709; BT.2100 HLG with BT.2020; ARRI LogC3 EI 800 with ALEXA Wide Gamut 3). Sony's own
 * Movie / Still / Cine1-4 / S-Cinetone shapes are not published, so they are not offered.
 */
final class IspProfiles {
    private IspProfiles() {}

    // ------------------------------------------------------------ the look: every parameter a profile recipe holds
    /** gamma curves; OFF is no profile at all (the camera's own look) */
    static final int OFF = 0, SLOG2 = 1, SLOG3 = 2, LOGC3 = 3, ITU709 = 4, HLG = 5, CURVES = 6;
    /** the file's spelling of each curve (frozen) and what the chip shows (canonical names, no language translates them) */
    static final String[] CURVE_CODES = { "off", "s-log2", "s-log3", "logc3", "itu709", "hlg" };
    private static final String[] CURVE_LABEL = { null, "S-Log2", "S-Log3", "LogC3", "ITU709", "HLG" };

    /** colour modes (gamuts): CAMERA keeps the camera's colour (BT.709) */
    static final int CAMERA = 0, SGAMUT3 = 1, SGAMUT3_CINE = 2, BT2020 = 3, AWG3 = 4, GAMUTS = 5;
    static final String[] GAMUT_CODES = { "camera", "s-gamut3", "s-gamut3-cine", "bt2020", "awg3" };
    private static final String[] GAMUT_LABEL = { null, "S-Gamut3", "S-Gamut3.Cine", "BT.2020", "ARRI WG3" };

    /** black gamma ranges: the share of the output it reshapes, as Sony's Narrow / Middle / Wide */
    static final int NARROW = 0, MIDDLE = 1, WIDE = 2;
    static final String[] RANGE_CODES = { "narrow", "middle", "wide" };
    static final double[] RANGE_TOP = { 0.15, 0.25, 0.40 };

    /** the knee point's value for "no knee"; otherwise it is the point in percent, 75..105 */
    static final int KNEE_OFF = 74, KNEE_MIN = 75, KNEE_MAX = 105;

    /** each parameter's range, as the chips step it and a file must hold it */
    static final int BLACK_MAX = 15, BGAMMA_MAX = 7, SLOPE_MAX = 5, SAT_MAX = 32, PHASE_MAX = 7, CONTRAST_MAX = 7;

    /**
     * A picture profile: a gamma curve with Sony's Picture Profile adjustments on it (black level, black gamma, knee) and
     * a contrast S-curve, and a colour mode with saturation and colour phase. All of it becomes the ISP's two tables:
     * the 96 gamma words and the 3x3 matrix ({@link #encode}). Immutable; two looks are equal when every value is.
     */
    static final class Look {
        final int curve, gamut, black, blackGamma, range, knee, slope, sat, phase, contrast;
        Look(int curve, int gamut, int black, int blackGamma, int range, int knee, int slope, int sat, int phase, int contrast) {
            this.curve = curve; this.gamut = gamut; this.black = black; this.blackGamma = blackGamma; this.range = range;
            this.knee = knee; this.slope = slope; this.sat = sat; this.phase = phase; this.contrast = contrast;
        }
        /** a curve and a colour mode, every adjustment at its neutral value */
        Look(int curve, int gamut) { this(curve, gamut, 0, 0, MIDDLE, KNEE_OFF, 0, 0, 0, 0); }

        /** null when every value is in range; else the first that is not, as the file names it */
        String problem() {
            if (curve < 1 || curve >= CURVES) return "gamma";
            if (gamut < 0 || gamut >= GAMUTS) return "color-mode";
            if (Math.abs(black) > BLACK_MAX) return "black-level";
            if (Math.abs(blackGamma) > BGAMMA_MAX) return "black-gamma";
            if (range < 0 || range >= RANGE_CODES.length) return "black-gamma-range";
            if (knee != KNEE_OFF && (knee < KNEE_MIN || knee > KNEE_MAX)) return "knee";
            if (Math.abs(slope) > SLOPE_MAX) return "knee-slope";
            if (Math.abs(sat) > SAT_MAX) return "color-saturation";
            if (Math.abs(phase) > PHASE_MAX) return "color-phase";
            if (Math.abs(contrast) > CONTRAST_MAX) return "s-curve";
            return null;
        }

        /** the same look with the values that do nothing set to their defaults: a range with no black gamma, a slope with no knee */
        Look normal() {
            return new Look(curve, gamut, black, blackGamma, blackGamma == 0 ? MIDDLE : range, knee, knee == KNEE_OFF ? 0 : slope, sat, phase, contrast);
        }

        /** the curve and colour mode, as the messages name the look: "S-Log3 / S-Gamut3.Cine" */
        String name() { return curveLabel(curve) + " / " + gamutLabel(gamut); }

        @Override public boolean equals(Object o) {
            if (!(o instanceof Look)) return false;
            Look a = normal(), b = ((Look) o).normal();
            return a.curve == b.curve && a.gamut == b.gamut && a.black == b.black && a.blackGamma == b.blackGamma && a.range == b.range
                    && a.knee == b.knee && a.slope == b.slope && a.sat == b.sat && a.phase == b.phase && a.contrast == b.contrast;
        }
        @Override public int hashCode() { Look n = normal(); return ((((n.curve * 31 + n.gamut) * 31 + n.black) * 31 + n.blackGamma) * 31 + n.knee) * 31 + n.sat * 7 + n.phase * 3 + n.contrast; }
        @Override public String toString() {
            return CURVE_CODES[curve] + "/" + GAMUT_CODES[gamut] + " bl" + black + " bg" + blackGamma + RANGE_CODES[range].charAt(0)
                    + " knee" + (knee == KNEE_OFF ? "-" : knee + "/" + slope) + " sat" + sat + " ph" + phase + " con" + contrast;
        }
    }

    static String curveLabel(int c) { return c == OFF ? Lang.t("value_off") : CURVE_LABEL[c]; }
    static String gamutLabel(int g) { return g == CAMERA ? Lang.t("isp_gamut_camera") : GAMUT_LABEL[g]; }
    static String rangeLabel(int r) { return Lang.t(r == NARROW ? "isp_range_narrow" : r == WIDE ? "isp_range_wide" : "isp_range_middle"); }
    static String kneeLabel(int k) { return k == KNEE_OFF ? Lang.t("value_off") : k + "%"; }

    /** a code from {@code codes}, back to its index; -1 for anything else */
    static int indexOf(String[] codes, String code) {
        for (int i = 0; i < codes.length; i++) if (codes[i].equals(code)) return i;
        return -1;
    }

    // ------------------------------------------------------------ gamma: 96 knots, 10-bit out
    /** the linear input (11-bit) of each of the 96 gamma knots of the A6000's ISP table */
    static final int[] KNOTS = knots();
    private static int[] knots() {
        int[] k = new int[96]; int n = 0;
        for (int i = 1; i <= 32; i++) k[n++] = 2 * i;
        for (int v = 68; v <= 128; v += 4) k[n++] = v;
        for (int v = 136; v <= 256; v += 8) k[n++] = v;
        for (int v = 272; v <= 512; v += 16) k[n++] = v;
        for (int v = 576; v <= 1024; v += 64) k[n++] = v;
        for (int v = 1152; v <= 1920; v += 128) k[n++] = v;
        k[n++] = 2047;
        if (n != 96) throw new IllegalStateException("knots " + n);
        return k;
    }
    /** the knot 18% grey sits on: scene linear r = 0.18 * knot / GREY_KNOT (as measured on the A6000's stock curve) */
    static final double GREY_KNOT = 76.0;

    /** Sony S-Log2 (reflectance in, 0..1 of the legal range out) */
    static double slog2(double r) {
        double x = r / 0.9;
        return 0.432699 * Math.log10(155.0 * x / 219.0 + 0.037584) + 0.616596 + 0.03;
    }
    /** Sony S-Log3: the 10-bit code value / 1023 */
    static double slog3(double r) {
        if (r >= 0.01125) return (420.0 + Math.log10((r + 0.01) / (0.18 + 0.01)) * 261.5) / 1023.0;
        return (r * (171.2102946929 - 95.0) / 0.01125 + 95.0) / 1023.0;
    }
    /** ARRI LogC3 at EI 800: the 10-bit code value / 1023 */
    static double logc3(double r) {
        return r > 0.010591 ? 0.247190 * Math.log10(5.555556 * r + 0.052272) + 0.385537 : 5.367655 * r + 0.092809;
    }
    /** ITU-R BT.709 OETF; above 1 it goes on rising, for the knee to bend (the table clips at 1023) */
    static double rec709(double r) {
        r = Math.max(0, r);
        return r < 0.018 ? 4.5 * r : 1.099 * Math.pow(r, 0.45) - 0.099;
    }
    /** BT.2100 HLG OETF; above 1 it goes on rising, like {@link #rec709} */
    static double hlg(double e) {
        e = Math.max(0, e);
        double a = 0.17883277, b = 0.28466892, c = 0.55991073;
        return e <= 1.0 / 12 ? Math.sqrt(3 * e) : a * Math.log(12 * e - b) + c;
    }
    /** HLG scene light of a reflectance: BT.2408 puts 100% diffuse white at 75% HLG, so r = 1 maps to E = 0.26496 */
    static final double HLG_WHITE = 0.26496;

    /** the base curve's output, 10-bit code value and not clipped, for a scene reflectance */
    static double curveCode(int curve, double r) {
        switch (curve) {
            case SLOG2: return 64 + 876 * slog2(r);                  // S-Log2 is specified in legal range
            case SLOG3: return slog3(r) * 1023;                       // S-Log3 and LogC3 are code values already
            case LOGC3: return logc3(r) * 1023;
            case ITU709: return rec709(r) * 1023;
            case HLG: return hlg(r * HLG_WHITE) * 1023;
            default: throw new IllegalArgumentException("curve " + curve);
        }
    }

    /** Sony's knee: above the point, highlights rise at 1/4 the slope, slope -5..+5 halving or doubling that */
    static double kneeSlope(int slope) { return 0.25 * Math.pow(2, slope / (double) SLOPE_MAX); }
    /** black gamma's exponent on its range: +7 lifts the shadows, -7 sinks them */
    static double blackGammaExponent(int level) { return Math.pow(2, -0.6 * level / BGAMMA_MAX); }
    /** the S-curve's slope at middle grey: +7 about 1.5x, -7 about 0.66x */
    static double contrastSlope(int c) { return Math.pow(2, 0.6 * c / CONTRAST_MAX); }
    /** black level: 10-bit code values per step at black, fading to none at white */
    static final double BLACK_STEP = 3;

    /**
     * One output code value through the adjustments, in the order a Sony Picture Profile applies them: knee on the
     * curve's own values, then black gamma, the S-curve around middle grey ({@code grey}, the code it would have), and
     * black level last. Each keeps 0 and 1023 where they are, so a neutral look is the curve itself.
     */
    static double adjust(Look l, double code, double grey) {
        if (l.knee != KNEE_OFF) {
            double kp = l.knee / 100.0 * 1023;
            if (code > kp) code = kp + (code - kp) * kneeSlope(l.slope);
        }
        code = Math.max(0, Math.min(1023, code));
        if (l.blackGamma != 0) {
            double top = RANGE_TOP[l.range] * 1023;
            if (code < top) code = top * Math.pow(code / top, blackGammaExponent(l.blackGamma));
        }
        if (l.contrast != 0) {
            double g = contrastSlope(l.contrast), p = Math.max(1, Math.min(1022, grey));
            code = code < p ? p * Math.pow(code / p, g) : 1023 - (1023 - p) * Math.pow((1023 - code) / (1023 - p), g);
        }
        if (l.black != 0) code += l.black * BLACK_STEP * (1 - code / 1023);
        return code;
    }

    /** the 10-bit output of each knot for a look (no look: null) */
    static int[] gamma(Look l) {
        if (l == null) return null;
        double grey = 0;
        if (l.contrast != 0) {                                        // the pivot: grey after knee and black gamma
            Look before = new Look(l.curve, l.gamut, 0, l.blackGamma, l.range, l.knee, l.slope, 0, 0, 0);
            grey = adjust(before, curveCode(l.curve, 0.18), 0);
        }
        int[] out = new int[96];
        for (int i = 0; i < 96; i++) {
            double r = 0.18 * KNOTS[i] / GREY_KNOT;
            double code = adjust(l, curveCode(l.curve, r), grey);
            out[i] = (int) Math.max(0, Math.min(1023, Math.round(code)));
            if (i > 0 && out[i] < out[i - 1]) out[i] = out[i - 1];         // monotone: the ISP interpolates between knots
        }
        return out;
    }

    // ------------------------------------------------------------ gamut: 3x3 from BT.709 (the camera's own output) to the target
    static final double[] D65 = { 0.3127, 0.3290 };
    static final double[][] P709 = { { 0.64, 0.33 }, { 0.30, 0.60 }, { 0.15, 0.06 } };
    static final double[][] SGAMUT3_XY = { { 0.730, 0.280 }, { 0.140, 0.855 }, { 0.100, -0.050 } };   // = S-Gamut primaries
    static final double[][] SGAMUT3_CINE_XY = { { 0.766, 0.275 }, { 0.225, 0.800 }, { 0.089, -0.087 } };
    static final double[][] BT2020_XY = { { 0.708, 0.292 }, { 0.170, 0.797 }, { 0.131, 0.046 } };
    static final double[][] AWG3_XY = { { 0.6840, 0.3130 }, { 0.2210, 0.8480 }, { 0.0861, -0.1020 } };

    static double[][] primaries(int gamut) {
        switch (gamut) {
            case SGAMUT3: return SGAMUT3_XY;                         // S-Gamut has the same primaries
            case SGAMUT3_CINE: return SGAMUT3_CINE_XY;
            case BT2020: return BT2020_XY;
            case AWG3: return AWG3_XY;
            default: return P709;
        }
    }

    /** RGB → XYZ of a set of primaries and a white point */
    static double[][] rgbToXyz(double[][] prim, double[] white) {
        double[][] m = new double[3][3];
        for (int j = 0; j < 3; j++) {
            double x = prim[j][0], y = prim[j][1];
            m[0][j] = x / y; m[1][j] = 1; m[2][j] = (1 - x - y) / y;
        }
        double[] w = { white[0] / white[1], 1, (1 - white[0] - white[1]) / white[1] };
        double[][] inv = inverse(m);
        for (int j = 0; j < 3; j++) {
            double s = inv[j][0] * w[0] + inv[j][1] * w[1] + inv[j][2] * w[2];
            for (int i = 0; i < 3; i++) m[i][j] *= s;
        }
        return m;
    }

    /** the matrix from BT.709 RGB to a colour mode's gamut: inv(M_target) · M_709 */
    static double[][] gamut(int g) { return multiply(inverse(rgbToXyz(primaries(g), D65)), rgbToXyz(P709, D65)); }

    /**
     * A colour mode's luminance weights: the Y row of its RGB → XYZ matrix, what saturation keeps. Saturation runs on
     * the colour mode's own RGB, so BT.709's weights there would brighten or darken colours (S-Gamut3 blue 2.4x at -32).
     */
    static double[] luma(int gamut) { return rgbToXyz(primaries(gamut), D65)[1]; }
    /** colour phase: degrees of hue rotation per step */
    static final double PHASE_DEGREES = 3;

    /** saturation -32..+32: -32 is black and white, +32 twice the colour; grey stays grey */
    static double[][] saturation(int sat, int gamut) {
        double s = 1 + sat / (double) SAT_MAX;
        double[] y = luma(gamut);
        double[][] m = new double[3][3];
        for (int i = 0; i < 3; i++) for (int j = 0; j < 3; j++) m[i][j] = (1 - s) * y[j] + (i == j ? s : 0);
        return m;
    }

    /** colour phase: a rotation of every hue about the grey axis; grey stays grey */
    static double[][] phase(int steps) {
        double a = Math.toRadians(steps * PHASE_DEGREES), c = Math.cos(a), s = Math.sin(a) / Math.sqrt(3), k = (1 - c) / 3;
        return new double[][] { { c + k, k - s, k + s }, { k + s, c + k, k - s }, { k - s, k + s, c + k } };
    }

    /** the whole colour side of a look: gamut, then phase, then saturation */
    static double[][] matrix(Look l) { return multiply(saturation(l.sat, l.gamut), multiply(phase(l.phase), gamut(l.gamut))); }

    /** the look's matrix in Q14, row-major (the helper multiplies the camera's stock matrix by it) */
    static int[] matrixQ14(Look l) {
        double[][] t = matrix(l);
        int[] q = new int[9];
        for (int i = 0; i < 9; i++) q[i] = (int) Math.round(t[i / 3][i % 3] * 16384);
        for (int r = 0; r < 3; r++) q[r * 4] += 16384 - (q[r * 3] + q[r * 3 + 1] + q[r * 3 + 2]);   // rows sum to 1: white stays white
        return q;
    }

    static double[][] multiply(double[][] a, double[][] b) {
        double[][] m = new double[3][3];
        for (int i = 0; i < 3; i++) for (int j = 0; j < 3; j++) for (int k = 0; k < 3; k++) m[i][j] += a[i][k] * b[k][j];
        return m;
    }

    static double[][] inverse(double[][] m) {
        double a = m[0][0], b = m[0][1], c = m[0][2], d = m[1][0], e = m[1][1], f = m[1][2], g = m[2][0], h = m[2][1], i = m[2][2];
        double det = a * (e * i - f * h) - b * (d * i - f * g) + c * (d * h - e * g);
        return new double[][] {
            { (e * i - f * h) / det, (c * h - b * i) / det, (b * f - c * e) / det },
            { (f * g - d * i) / det, (a * i - c * g) / det, (c * d - a * f) / det },
            { (d * h - e * g) / det, (b * g - a * h) / det, (a * e - b * d) / det } };
    }

    // ------------------------------------------------------------ the helper's input file
    static final int FILE_VERSION = 1, F_GAMMA = 1, F_MATRIX = 2;
    /** profile.bin: "RLPP", version, flags, 96 gamma words, 9 Q14 matrix words; all little-endian 32-bit */
    static final int FILE_SIZE = 4 + 4 + 4 + 96 * 4 + 9 * 4;

    static byte[] encode(Look l) {
        if (l == null || l.problem() != null) throw new IllegalArgumentException("no look: " + l);
        byte[] b = new byte[FILE_SIZE];
        b[0] = 'R'; b[1] = 'L'; b[2] = 'P'; b[3] = 'P';
        int o = put(b, 4, FILE_VERSION);
        o = put(b, o, F_GAMMA | F_MATRIX);                 // always both: a switch to ITU709 must undo the last gamut
        for (int g : gamma(l)) o = put(b, o, g);
        for (int t : matrixQ14(l)) o = put(b, o, t);
        return b;
    }
    private static int put(byte[] b, int o, int v) {
        b[o] = (byte) v; b[o + 1] = (byte) (v >> 8); b[o + 2] = (byte) (v >> 16); b[o + 3] = (byte) (v >> 24);
        return o + 4;
    }

    // ------------------------------------------------------------ the root command and its answer
    /** the root shell lives outside Android's root: what the app sees as /x it sees as /android/x */
    static final String ROOT_PREFIX = "/android";
    /** the platform's shell takes 255 characters at most */
    static final int MAX_COMMAND = 255;

    /** "ping", "apply", "off", "status" or "probe": the line the root shell runs; null when it would be too long */
    static String command(String helper, String op, String dir) {
        String cmd = ROOT_PREFIX + helper + " " + op + " " + ROOT_PREFIX + dir;
        return cmd.length() <= MAX_COMMAND ? cmd : null;
    }

    /**
     * The keep-alive command: apply once, then reapply every 2 s for about 2 minutes. The loop keeps the OSAL shell (and
     * the CPU) alive; {@code reapply} is a no-op when gamma already matches. A {@code stop} file (Off) or {@code app}
     * file (the app is open and needs the shell) ends it within 2 s. Null when too long.
     */
    static String bootCommand(String helper, String dir) {
        String h = ROOT_PREFIX + helper, d = ROOT_PREFIX + dir;
        String cmd = "h=" + h + ";d=" + d + ";$h apply $d;for a in 1 2 3 4 5 6;do for b in 1 2 3 4 5 6 7 8 9 0;do sleep 2;[ -f $d/stop -o -f $d/app ]&&exit;$h reapply $d;done;done";
        return cmd.length() <= MAX_COMMAND ? cmd : null;
    }

    /** polls (of the app's 250 ms) with no log before a command counts as dropped, and how often it is sent again */
    static final int RESEND_POLLS = 4, RESENDS = 12;

    /**
     * Whether to send the helper's command again: the helper writes its log first thing, so none after
     * {@link #RESEND_POLLS} polls means the shell was busy (it runs one command at a time) and dropped it.
     */
    static boolean resend(String log, int polls, int resends) {
        return log == null && polls >= RESEND_POLLS && resends < RESENDS;
    }

    /** what the helper's log says */
    static final int R_PENDING = 0, R_OK = 1, R_OFF = 2, R_PONG = 3, R_REFUSED = 4, R_MISMATCH = 5, R_ERROR = 6, R_STATUS = 7, R_PROBE = 8;

    /** the outcome in a log; R_PENDING while there is no complete RESULT line yet (the helper still running, or never started) */
    static int result(String log) {
        String line = resultLine(log);
        if (line == null) return R_PENDING;
        if (line.equals("OK")) return R_OK;
        if (line.equals("OFF")) return R_OFF;
        if (line.startsWith("PONG")) return R_PONG;
        if (line.startsWith("REFUSE")) return R_REFUSED;
        if (line.startsWith("MISMATCH")) return R_MISMATCH;
        if (line.startsWith("STATUS")) return R_STATUS;
        if (line.startsWith("PROBE")) return R_PROBE;
        return R_ERROR;
    }

    /** the text after "RESULT " on the log's complete last result line; null when there is none */
    static String resultLine(String log) {
        if (log == null) return null;
        int at = log.lastIndexOf("RESULT ");
        if (at < 0 || (at > 0 && log.charAt(at - 1) != '\n')) return null;
        int end = log.indexOf('\n', at);
        return end < 0 ? null : log.substring(at + 7, end).trim();
    }
}
