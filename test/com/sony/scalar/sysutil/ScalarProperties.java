package com.sony.scalar.sysutil;

import java.util.HashMap;
import java.util.Map;

/** Test double of the camera's {@code ScalarProperties}: {@code getString(String)}; a property nobody set up throws. */
public class ScalarProperties {
    private static final Map<String, String> PROPS = new HashMap<String, String>();

    public static void set(String key, String value) { PROPS.put(key, value); }

    private static String firmware;
    private static boolean firmwareSet;
    public static void setFirmware(String v) { firmware = v; firmwareSet = true; }
    public static void clearFirmware() { firmware = null; firmwareSet = false; }

    public static String getFirmwareVersion() {
        if (!firmwareSet) throw new UnsupportedOperationException("no firmware version");
        return firmware;
    }

    public static String getString(String key) {
        if (!PROPS.containsKey(key)) throw new UnsupportedOperationException("no property " + key);
        return PROPS.get(key);
    }
}
