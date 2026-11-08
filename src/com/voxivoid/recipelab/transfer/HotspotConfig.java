package com.voxivoid.recipelab.transfer;

import java.lang.reflect.Method;

/**
 * The network name and password in a hotspot configuration (a WifiConfiguration, or Sony's DirectConfiguration), read
 * by reflection since neither class can be named here. Null wherever the configuration does not say.
 */
public final class HotspotConfig {
    private HotspotConfig() {}

    /** the hotspot's network name, as the configuration holds it (perhaps in quotes; TransferState takes them off) */
    public static String ssid(Object config) {
        return string(config, "getSsid");
    }

    public static String password(Object config) {
        return string(config, "getPreSharedKey");
    }

    private static String string(Object config, String getter) {
        if (config == null) return null;
        try {
            Method m = config.getClass().getMethod(getter);
            Object v = m.invoke(config);
            return v == null ? null : v.toString();
        } catch (Throwable t) {
            return null;
        }
    }
}
