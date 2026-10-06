package com.voxivoid.recipelab;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * What the camera says about its keys and itself, asked through reflection so the app needs no Sony stub jar
 * (issue #18). Sony's own app framework decides which keys a body has from
 * {@code ScalarInput.getKeyStatus(scan).valid}, and reads the model from {@code ScalarProperties} —
 * {@code Build.MODEL} is "ScalarA" on every body.
 *
 * Every answer is null when the call is not there or throws: off the camera (tools/test.sh), and on any firmware that
 * lacks it. Callers treat null as "unknown" and fall back to the universal keys. Nothing here has been checked on a
 * camera yet. No android.* import may appear in this class (tools/test.sh).
 */
final class KeyProbe {
    private KeyProbe() {}

    private static final String INPUT = "com.sony.scalar.sysutil.ScalarInput", PROPS = "com.sony.scalar.sysutil.ScalarProperties";
    private static final Map<Integer, Boolean> HAS = new HashMap<Integer, Boolean>();

    /** the keys the logger and About report on, in display order */
    static final int[] REPORTED = { Keys.K_FN, Keys.K_AEL, Keys.K_DISP, Keys.K_C1, Keys.K_DELETE, Keys.K_SK2, Keys.K_PLAY,
            Keys.K_MOVIE, Keys.K_ZOOM_T, Keys.K_WHEEL_STATUS, Keys.K_DIAL_STATUS, Keys.K_AEL_LEVER, Keys.K_AEL_AFMF };

    /** whether the body has the key: TRUE, FALSE, or null when the camera would not say. Asked once per key. */
    static synchronized Boolean has(int scan) {
        if (HAS.containsKey(scan)) return HAS.get(scan);
        Integer valid = statusField(scan, "valid");
        Boolean v = valid == null ? null : Boolean.valueOf(valid == 1);
        HAS.put(scan, v);
        return v;
    }

    /** whether the key is held right now: null when the camera would not say, or does not know the key */
    static Boolean isDown(int scan) {
        Integer valid = statusField(scan, "valid"), status = statusField(scan, "status");
        if (valid == null || status == null || valid != 1) return null;
        return status == 1;
    }

    /** a ScalarProperties string, e.g. "model.name" → "ILCE-6000", "version.platform" → "2.4"; null when unavailable */
    static String prop(String key) {
        try {
            Object v = Class.forName(PROPS).getMethod("getString", String.class).invoke(null, key);
            return v == null || String.valueOf(v).isEmpty() ? null : String.valueOf(v);
        } catch (Throwable t) { return null; }
    }

    /**
     * The camera's firmware version, e.g. "3.21" — not a property but ScalarProperties.getFirmwareVersion() (seen in the
     * A6000 firmware's framework); null when unavailable, as off the camera.
     */
    static String firmware() {
        try {
            Object v = Class.forName(PROPS).getMethod("getFirmwareVersion").invoke(null);
            return v == null || String.valueOf(v).trim().isEmpty() ? null : String.valueOf(v).trim();
        } catch (Throwable t) { return null; }
    }

    /**
     * The function the firmware assigns to a key, as Sony's KeyLogicCode (e.g. 1081 DISP, 1073 Fn, 1103 delete), for
     * the key logger only; {@code mode} is Sony's key-logic mode (0 P … 4 other shooting). null before platform API 3,
     * which is every NEX body.
     */
    static Integer logic(int scan, int mode) {
        try {
            Object v = Class.forName(INPUT).getMethod("getKeyLogicCode", int.class, int.class, int.class).invoke(null, scan, 1, mode);
            return v instanceof Number ? ((Number) v).intValue() : null;
        } catch (Throwable t) { return null; }
    }

    /** the shortcut key the legend may name, from {@link #has} */
    static Keys.Caps caps() { return new Keys.Caps(has(Keys.K_FN)); }

    private static Integer statusField(int scan, String field) {
        try {
            Method m = Class.forName(INPUT).getMethod("getKeyStatus", int.class);
            Object s = m.invoke(null, scan);
            return s == null ? null : s.getClass().getField(field).getInt(s);
        } catch (Throwable t) { return null; }
    }
}
