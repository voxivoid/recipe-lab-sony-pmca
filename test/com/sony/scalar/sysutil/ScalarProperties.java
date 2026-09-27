package com.sony.scalar.sysutil;

import java.util.HashMap;
import java.util.Map;

/** Test double of the camera's {@code ScalarProperties}: {@code getString(String)}; a property nobody set up throws. */
public class ScalarProperties {
    private static final Map<String, String> PROPS = new HashMap<String, String>();

    public static void set(String key, String value) { PROPS.put(key, value); }

    public static String getString(String key) {
        if (!PROPS.containsKey(key)) throw new UnsupportedOperationException("no property " + key);
        return PROPS.get(key);
    }
}
