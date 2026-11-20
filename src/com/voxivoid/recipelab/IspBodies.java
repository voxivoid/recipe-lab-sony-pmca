package com.voxivoid.recipelab;

/**
 * Which camera may run a picture profile: the Java side of {@code jni/rlisp/bodies.h}. A row there says where av-cam
 * keeps the two ISP setters on one model and firmware; this list mirrors its model, firmware and enabled flag, and
 * {@code IspBodiesTest} fails when the two disagree.
 *
 * <p>Nothing is hidden on any body: the profile rows stay on screen, and {@link #refusal} says why one cannot apply.
 * Only a {@link #SUPPORTED} body runs the helper's apply, and only it gets the boot loop ({@link #bootAllowed}).
 * The read-only probe runs on any body, from the bug report, so an owner's report can switch a row on.
 */
final class IspBodies {
    private IspBodies() {}

    /** model, firmware, enabled: one row of bodies.h each */
    static final Object[][] ROWS = {
        { "ILCE-6000", "3.21", true },
        { "ILCE-5000", "1.10", false },
        { "ILCE-5100", "3.10", false },
        { "ILCE-6300", "2.01", false },
        { "ILCE-6500", "1.06", false },
        { "ILCE-7", "3.20", false },
        { "ILCE-7R", "3.20", false },
        { "ILCE-7S", "3.20", false },
        { "ILCE-7M2", "4.01", false },
        { "ILCE-7RM2", "4.01", false },
        { "ILCE-7SM2", "3.01", false },
        { "DSC-RX100M3", "2.00", false },
        { "DSC-RX100M4", "2.00", false },
        { "DSC-RX100M5", "2.00", false },
        { "DSC-HX60V", "2.10", false },
        { "DSC-HX60", "2.10", false },
    };

    static final int SUPPORTED = 0, UNTESTED = 1, OLD_FW = 2, NO = 3;

    /** what this camera may do: {@link #SUPPORTED}, {@link #UNTESTED} (probe only), {@link #OLD_FW} or {@link #NO} */
    static int status(String model, String fw) {
        Object[] r = row(model, fw);
        if (r != null) return (Boolean) r[2] ? SUPPORTED : UNTESTED;
        return wantedFirmware(model) != null ? OLD_FW : NO;
    }

    /** the firmware a known model needs; null for a model with no row (or no model at all) */
    static String wantedFirmware(String model) {
        String m = clean(model);
        if (m == null) return null;
        for (Object[] r : ROWS) if (r[0].equals(m)) return (String) r[1];
        return null;
    }

    private static Object[] row(String model, String fw) {
        String m = clean(model), f = clean(fw);
        if (m == null || f == null) return null;
        for (Object[] r : ROWS) if (r[0].equals(m) && r[1].equals(f)) return r;
        return null;
    }

    private static String clean(String s) {
        if (s == null) return null;
        s = s.trim();
        return s.isEmpty() ? null : s;
    }

    /** whether the boot receiver may start the keep-alive loop */
    static boolean bootAllowed(String model, String fw) { return status(model, fw) == SUPPORTED; }

    /** the helper's {@code body} file: model and firmware, one per line ("?" for one the camera does not say) */
    static String bodyFile(String model, String fw) {
        String m = clean(model), f = clean(fw);
        return (m == null ? "?" : m) + "\n" + (f == null ? "?" : f) + "\n";
    }

    /**
     * Why a profile cannot apply on this camera, as a {@link Lang} key and its argument ({@code {key, arg}}, arg may be
     * null); null when it can.
     */
    static String[] refusal(String model, String fw) {
        switch (status(model, fw)) {
            case SUPPORTED: return null;
            case UNTESTED: return new String[] { "isp_untested", null };
            case OLD_FW: return new String[] { "isp_update_fw", wantedFirmware(model) };
            default: return new String[] { "isp_not_yet", null };
        }
    }

    /** the same for a helper's "REFUSE ..." result line (the helper checks for itself); null when it is not a refusal */
    static String[] refusal(String resultLine) {
        if (resultLine == null || !resultLine.startsWith("REFUSE")) return null;
        String kind = resultLine.substring(6).trim();
        if (kind.startsWith("fw ")) return new String[] { "isp_update_fw", kind.substring(3).trim() };
        if (kind.equals("untested")) return new String[] { "isp_untested", null };
        if (kind.equals("isp")) return new String[] { "isp_not_ready", null };
        return new String[] { "isp_not_yet", null };          // body, sig, or anything newer
    }
}
