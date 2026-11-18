package com.voxivoid.recipelab;

/**
 * Picture profiles for the A6000: the published log and video curves (gamma) and gamuts (colour matrix) the camera
 * lacks, written straight into its image processor by the root helper {@code rlisp} (jni/rlisp/rlisp.c). RAM only:
 * the camera is stock again after a power cycle, which is why the app applies the chosen one again on every start.
 *
 * Everything here is camera-free (tools/test.sh): the 96 gamma words, the gamut matrices, the file the helper reads,
 * the root command line and how the helper's log is read back. No android.* import may appear in this class.
 *
 * Only profiles whose formulas and primaries are published are here (Sony S-Log2, S-Log3, S-Gamut, S-Gamut3,
 * S-Gamut3.Cine; ITU-R BT.709; BT.2100 HLG with BT.2020; ARRI LogC3 EI 800 with ALEXA Wide Gamut 3). Sony's own
 * Movie / Still / Cine1-4 / S-Cinetone shapes are not published, so they are not offered.
 */
final class IspProfiles {
    private IspProfiles() {}

    static final int OFF = 0, SLOG2 = 1, SLOG3_CINE = 2, SLOG3 = 3, ITU709 = 4, HLG = 5, LOGC3 = 6, COUNT = 7;

    /** the preference value of each profile; stable, never shown */
    static final String[] CODES = { "off", "slog2", "slog3cine", "slog3", "itu709", "hlg", "logc3" };
    /** the short names the menu row shows (canonical names, no language translates them); Off goes through Lang */
    private static final String[] SHORT = { null, "S-Log2", "S-Log3 Cine", "S-Log3", "ITU709", "HLG", "LogC3" };
    /** the full names: curve and gamut */
    private static final String[] FULL = { null, "S-Log2 / S-Gamut", "S-Log3 / S-Gamut3.Cine", "S-Log3 / S-Gamut3",
            "ITU709 / BT.709", "HLG / BT.2020", "LogC3 EI800 / ARRI Wide Gamut 3" };

    static String label(int p) { return p == OFF ? Lang.t("isp_off") : SHORT[p]; }
    static String fullName(int p) { return p == OFF ? Lang.t("isp_off") : FULL[p]; }

    /** a stored preference back to a profile; anything unknown is Off */
    static int parse(String code) {
        for (int i = 0; i < COUNT; i++) if (CODES[i].equals(code)) return i;
        return OFF;
    }

    /** the profile left / right lands on, wrapping */
    static int next(int p, int dir) { return ((p + dir) % COUNT + COUNT) % COUNT; }

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
    /** ITU-R BT.709 OETF, clipped at 1 */
    static double rec709(double r) {
        r = Math.max(0, Math.min(1, r));
        return r < 0.018 ? 4.5 * r : 1.099 * Math.pow(r, 0.45) - 0.099;
    }
    /** BT.2100 HLG OETF, clipped at 1 */
    static double hlg(double e) {
        e = Math.max(0, Math.min(1, e));
        double a = 0.17883277, b = 0.28466892, c = 0.55991073;
        return e <= 1.0 / 12 ? Math.sqrt(3 * e) : a * Math.log(12 * e - b) + c;
    }
    /** HLG scene light of a reflectance: BT.2408 puts 100% diffuse white at 75% HLG, so r = 1 maps to E = 0.26496 */
    static final double HLG_WHITE = 0.26496;

    /** the 10-bit output of each knot for a profile (Off: null) */
    static int[] gamma(int p) {
        if (p == OFF) return null;
        int[] out = new int[96];
        for (int i = 0; i < 96; i++) {
            double r = 0.18 * KNOTS[i] / GREY_KNOT, code;
            switch (p) {
                case SLOG2: code = 64 + 876 * slog2(r); break;               // S-Log2 is specified in legal range
                case SLOG3: case SLOG3_CINE: code = slog3(r) * 1023; break;  // S-Log3 and LogC3 are code values already
                case LOGC3: code = logc3(r) * 1023; break;
                case ITU709: code = rec709(r) * 1023; break;
                case HLG: code = hlg(r * HLG_WHITE) * 1023; break;
                default: throw new IllegalArgumentException("profile " + p);
            }
            out[i] = (int) Math.max(0, Math.min(1023, Math.round(code)));
            if (i > 0 && out[i] < out[i - 1]) out[i] = out[i - 1];         // monotone: the ISP interpolates between knots
        }
        return out;
    }

    // ------------------------------------------------------------ gamut: 3x3 from BT.709 (the camera's own output) to the target
    static final double[] D65 = { 0.3127, 0.3290 };
    static final double[][] P709 = { { 0.64, 0.33 }, { 0.30, 0.60 }, { 0.15, 0.06 } };
    static final double[][] SGAMUT3 = { { 0.730, 0.280 }, { 0.140, 0.855 }, { 0.100, -0.050 } };   // = S-Gamut primaries
    static final double[][] SGAMUT3_CINE = { { 0.766, 0.275 }, { 0.225, 0.800 }, { 0.089, -0.087 } };
    static final double[][] BT2020 = { { 0.708, 0.292 }, { 0.170, 0.797 }, { 0.131, 0.046 } };
    static final double[][] AWG3 = { { 0.6840, 0.3130 }, { 0.2210, 0.8480 }, { 0.0861, -0.1020 } };

    static double[][] primaries(int p) {
        switch (p) {
            case SLOG2: case SLOG3: return SGAMUT3;
            case SLOG3_CINE: return SGAMUT3_CINE;
            case HLG: return BT2020;
            case LOGC3: return AWG3;
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

    /** the matrix from BT.709 RGB to the profile's gamut: inv(M_target) · M_709 */
    static double[][] gamut(int p) { return multiply(inverse(rgbToXyz(primaries(p), D65)), rgbToXyz(P709, D65)); }

    /** the gamut matrix in Q14, row-major (the helper multiplies the camera's stock matrix by it) */
    static int[] gamutQ14(int p) {
        double[][] t = gamut(p);
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

    static byte[] encode(int p) {
        if (p == OFF) throw new IllegalArgumentException("Off has no file");
        byte[] b = new byte[FILE_SIZE];
        b[0] = 'R'; b[1] = 'L'; b[2] = 'P'; b[3] = 'P';
        int o = put(b, 4, FILE_VERSION);
        o = put(b, o, F_GAMMA | F_MATRIX);                 // always both: a switch to ITU709 must undo the last gamut
        for (int g : gamma(p)) o = put(b, o, g);
        for (int t : gamutQ14(p)) o = put(b, o, t);
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

    /** "ping", "apply", "off" or "status": the line the root shell runs; null when it would be too long */
    static String command(String helper, String op, String dir) {
        String cmd = ROOT_PREFIX + helper + " " + op + " " + ROOT_PREFIX + dir;
        return cmd.length() <= MAX_COMMAND ? cmd : null;
    }

    /** what the helper's log says */
    static final int R_PENDING = 0, R_OK = 1, R_OFF = 2, R_PONG = 3, R_REFUSED = 4, R_MISMATCH = 5, R_ERROR = 6, R_STATUS = 7;

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
