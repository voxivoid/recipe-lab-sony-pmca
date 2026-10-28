package com.voxivoid.recipelab.transfer;

import android.content.Context;
import android.content.Intent;
import java.lang.reflect.Method;
import java.util.List;

/**
 * Sony's camera-as-access-point ("Direct") mode, reached by reflection on com.sony.wifi.direct.DirectManager so
 * the app still loads on a body without it. Names and values are from OpenMemories-Framework's DirectManager
 * stub and PMCADemo's WifiDirectActivity. Seen working on an A6000; every call fails soft on a body without it.
 */
final class Hotspot {
    static final String SERVICE = "wifi-direct";
    static final String ACTION_STATE_CHANGED = "com.sony.wifi.direct.DIRECT_STATE_CHANGED_ACTION";
    static final String ACTION_GROUP_CREATE_SUCCESS = "com.sony.wifi.direct.GROUP_CREATE_SUCCESS_ACTION";
    static final String ACTION_GROUP_CREATE_FAILURE = "com.sony.wifi.direct.GROUP_CREATE_FAILURE_ACTION";
    static final String ACTION_STA_CONNECTED = "com.sony.wifi.direct.STA_CONNECTED_ACTION";
    static final String ACTION_STA_DISCONNECTED = "com.sony.wifi.direct.STA_DISCONNECTED_ACTION";
    static final String EXTRA_STATE = "direct_state";
    static final String EXTRA_CONFIG = "direct_config";
    static final String EXTRA_ERROR_CODE = "error_code";
    static final String EXTRA_STA_ADDR = "sta_addr";
    static final int STATE_UNKNOWN = -1, STATE_ENABLED = 4;

    private final Object manager;
    /** the configuration {@link #startGroupOwner} started: its name and password, if the success broadcast lacks them */
    private Object started;
    /** what went wrong in the last {@link #startGroupOwner} that threw: the exception's class */
    private String error;
    /** the same exception in full, for the card log */
    private String errorDetail;

    private Hotspot(Object manager) {
        this.manager = manager;
    }

    /** The camera's Direct manager, or null when the body (or firmware) has none. */
    static Hotspot create(Context context) {
        try {
            Object m = context.getSystemService(SERVICE);
            if (m == null) return null;
            m.getClass().getMethod("setDirectEnabled", boolean.class);   // is it the class we expect?
            return new Hotspot(m);
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * For the card log when {@link #create} found nothing: what the "wifi-direct" service is (if anything), and what
     * the "wifip2p" one is — Sony's WifiP2pExtManager, which OpenMemories-Framework's stubs list as the Direct API of
     * later bodies.
     */
    static String probe(Context context) {
        return ApiProbe.describe(SERVICE, service(context, SERVICE)) + "\n" + ApiProbe.describe(P2P_SERVICE, service(context, P2P_SERVICE));
    }

    static final String P2P_SERVICE = "wifip2p";

    private static Object service(Context context, String name) {
        try {
            return context.getSystemService(name);
        } catch (Throwable t) {
            return "threw " + t;
        }
    }

    /** the manager's class name, for the card log */
    String managerClass() {
        return manager.getClass().getName();
    }

    /** how many saved Direct configurations the camera has, or -1 when the call failed */
    int configurationCount() {
        try {
            List<?> configs = (List<?>) manager.getClass().getMethod("getConfigurations").invoke(manager);
            return configs == null ? 0 : configs.size();
        } catch (Throwable t) {
            return -1;
        }
    }

    boolean setEnabled(boolean on) {
        try {
            Object r = manager.getClass().getMethod("setDirectEnabled", boolean.class).invoke(manager, on);
            return !(r instanceof Boolean) || (Boolean) r;
        } catch (Throwable t) {
            return false;
        }
    }

    int state() {
        try {
            return (Integer) manager.getClass().getMethod("getDirectState").invoke(manager);
        } catch (Throwable t) {
            return STATE_UNKNOWN;
        }
    }

    /**
     * Starts a group owner (the hotspot) on the last saved configuration, as PMCADemo does. Returns -1 on success, or
     * the {@link TransferState} status that says why not ({@link #error()} has the detail of ERROR).
     */
    int startGroupOwner() {
        try {
            List<?> configs = (List<?>) manager.getClass().getMethod("getConfigurations").invoke(manager);
            if (configs == null || configs.isEmpty()) return TransferState.NO_CONFIG;
            Object last = configs.get(configs.size() - 1);
            started = last;
            int networkId = (Integer) last.getClass().getMethod("getNetworkId").invoke(last);
            Object r = manager.getClass().getMethod("startGo", int.class).invoke(manager, networkId);
            if (r instanceof Boolean && !(Boolean) r) return TransferState.REFUSED;
            return -1;
        } catch (Throwable t) {
            Throwable cause = t.getCause() != null ? t.getCause() : t;   // reflection wraps what the camera threw
            error = cause.getClass().getSimpleName();
            errorDetail = String.valueOf(cause);
            return TransferState.ERROR;
        }
    }

    /** the configuration the group was started on, or null */
    Object startedConfig() {
        return started;
    }

    String error() {
        return error;
    }

    String errorDetail() {
        return errorDetail;
    }

    void removeGroup() {
        try {
            manager.getClass().getMethod("removeGroup").invoke(manager);
        } catch (Throwable ignored) {
        }
    }

    /** The DirectConfiguration carried by GROUP_CREATE_SUCCESS_ACTION, or null. */
    static Object config(Intent intent, ClassLoader loader) {
        try {
            return intent.getParcelableExtra(EXTRA_CONFIG);
        } catch (Throwable first) {
            try {
                intent.setExtrasClassLoader(loader);
                return intent.getParcelableExtra(EXTRA_CONFIG);
            } catch (Throwable t) {
                return null;
            }
        }
    }

    /** the hotspot's network name, as the configuration holds it (perhaps in quotes; TransferState takes them off) */
    static String ssid(Object config) {
        return string(config, "getSsid");
    }

    static String password(Object config) {
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
