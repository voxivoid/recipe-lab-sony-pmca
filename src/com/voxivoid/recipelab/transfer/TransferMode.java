package com.voxivoid.recipelab.transfer;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;
import android.view.WindowManager;
import java.io.File;

/**
 * Transfer to phone, the camera half: starts the camera's own hotspot (Sony's Direct mode — the camera is the access
 * point, at 192.168.122.1), serves the memory card over HTTP for Recipe Lab Studio on the phone ({@link LinkServer},
 * protocol 2), and keeps the camera from powering itself off while it does.
 *
 * MainActivity drives it: {@link #start} when the page opens, {@link #stop} on MENU, on leaving the app and on
 * destroy. Stopping turns the server, the hotspot and Wi-Fi off and gives auto power-off back. What the page says is
 * {@link TransferState}, which has no Android in it.
 */
public final class TransferMode {
    /** told whenever the page should be drawn again: an event, or the once-a-second tick */
    public interface Listener {
        void changed();
    }

    private static final String TAG = "RecipeLabTransfer";
    private static final String ACTION_APO = "com.android.server.DAConnectionManagerService.apo";
    private static final String ACTION_APP_INFO = "com.android.server.DAConnectionManagerService.AppInfoReceive";
    private static final long TICK_MS = 1000, APO_REFRESH_MS = 60000;

    private final Activity activity;
    private final String model;
    private final Listener listener;
    private final Handler handler = new Handler();
    private final Stats stats = new Stats();
    private final TransferState state = new TransferState();
    private final Logger logger = new Logger() {
        @Override public void info(String msg) { Log.i(TAG, msg); }
        @Override public void error(String msg, Throwable t) { Log.e(TAG, msg, t); }
    };
    /** the card log (LOG.TXT): every hotspot step, so a report from a body it fails on shows where it stopped */
    private final Logger card;

    private WifiManager wifi;
    private Hotspot hotspot;
    /** the hotspot on bodies with Sony's P2P manager instead of the Direct one (the A6300); null otherwise */
    private HotspotP2p p2p;
    private LinkServer server;
    private Thread.UncaughtExceptionHandler crashHandler, previousCrashHandler;
    private boolean running, receiverRegistered, groupRequested, timeoutLogged;
    private long lastApoMs;
    private double rate;

    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            tick();
            handler.postDelayed(this, TICK_MS);
        }
    };

    /**
     * {@code model} is the camera's model code as /api/info reports it, e.g. ILCE-6000; {@code card} writes to the card
     * log (never the password).
     */
    public TransferMode(Activity activity, String model, Logger card, Listener listener) {
        this.activity = activity;
        this.model = model == null || model.length() == 0 ? "unknown" : model;
        this.card = card == null ? Logger.NONE : card;
        this.listener = listener;
    }

    /** a hotspot step: to logcat and to the card log */
    private void step(String msg) {
        logger.info(msg);
        try { card.info(msg); } catch (Throwable ignored) {}
    }

    private void stepFailed(String msg, Throwable t) {
        logger.error(msg, t);
        try { card.error(msg, t); } catch (Throwable ignored) {}
    }

    public TransferState state() { return state; }
    public Stats stats() { return stats; }
    /** bytes per second, as the last tick measured it */
    public double rate() { return rate; }
    public boolean running() { return running; }

    // ---- lifecycle

    /** The server up, the camera kept awake, then Wi-Fi and the hotspot on. */
    public void start() {
        if (running) return;
        running = true;
        timeoutLogged = false;
        wifi = (WifiManager) activity.getSystemService(Context.WIFI_SERVICE);
        hotspot = Hotspot.create(activity);
        p2p = hotspot == null && Build.VERSION.SDK_INT >= 14 ? HotspotP2p.create(activity, p2pEvents) : null;
        step("start  wifi " + (wifi == null ? "none" : "state " + wifiState()) + "  airplane " + airplane()
            + "  direct " + (hotspot != null ? hotspot.managerClass() : p2p != null ? p2p.managerClass() : "none"));
        installCrashHandler();
        try { activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); } catch (Throwable t) { logger.error("screen on", t); }
        state.start(now());
        registerReceiver();
        notifyAppInfo();
        setAutoPowerOff(false);
        startServer();
        startHotspot();
        handler.removeCallbacks(ticker);
        handler.post(ticker);
        changed();
    }

    /** Idempotent: stop serving, drop the hotspot and Wi-Fi, give the camera back its auto power-off. */
    public void stop() {
        if (!running) return;
        running = false;
        step("stop  status " + state.status());
        handler.removeCallbacks(ticker);
        unregisterReceiver();
        try { if (server != null) server.stop(); } catch (Throwable t) { logger.error("server stop", t); }
        server = null;
        stopHotspot();
        try { if (wifi != null) wifi.setWifiEnabled(false); } catch (Throwable t) { logger.error("wifi off", t); }
        setAutoPowerOff(true);
        try { activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); } catch (Throwable t) { logger.error("screen on", t); }
        removeCrashHandler();
    }

    private void changed() {
        if (running) listener.changed();
    }

    private static long now() {
        return SystemClock.elapsedRealtime();
    }

    // ---- camera plumbing

    private static Intent apoIntent(boolean enable) {
        Intent i = new Intent(ACTION_APO);
        i.putExtra("apo_info", enable ? "APO/NORMAL" : "APO/NO");
        return i;
    }

    private void setAutoPowerOff(boolean enable) {
        try { activity.sendBroadcast(apoIntent(enable)); } catch (Throwable t) { logger.error("apo", t); }
        lastApoMs = now();
    }

    /** Tells the camera's app framework which activity is in front (as PMCADemo does before it uses Wi-Fi). */
    private void notifyAppInfo() {
        try {
            Intent i = new Intent(ACTION_APP_INFO);
            i.putExtra("package_name", activity.getComponentName().getPackageName());
            i.putExtra("class_name", activity.getComponentName().getClassName());
            activity.sendBroadcast(i);
        } catch (Throwable ignored) {
        }
    }

    /** While serving, a crash must not leave the camera with Wi-Fi on and auto power-off off. */
    private void installCrashHandler() {
        final Context app = activity.getApplicationContext();
        final Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        previousCrashHandler = previous;
        crashHandler = new Thread.UncaughtExceptionHandler() {
            @Override public void uncaughtException(Thread thread, Throwable e) {
                try { Log.e(TAG, "crash in " + thread.getName(), e); } catch (Throwable ignored) {}
                try { if (hotspot != null) hotspot.setEnabled(false); } catch (Throwable ignored) {}
                try { ((WifiManager) app.getSystemService(Context.WIFI_SERVICE)).setWifiEnabled(false); } catch (Throwable ignored) {}
                try { app.sendBroadcast(apoIntent(true)); } catch (Throwable ignored) {}
                if (previous != null) previous.uncaughtException(thread, e);
                else System.exit(0);
            }
        };
        Thread.setDefaultUncaughtExceptionHandler(crashHandler);
    }

    private void removeCrashHandler() {
        if (crashHandler != null && Thread.getDefaultUncaughtExceptionHandler() == crashHandler)
            Thread.setDefaultUncaughtExceptionHandler(previousCrashHandler);
        crashHandler = null;
        previousCrashHandler = null;
    }

    private void startServer() {
        File card = Environment.getExternalStorageDirectory();
        String mounted = Environment.getExternalStorageState();
        if (!Environment.MEDIA_MOUNTED.equals(mounted) && !Environment.MEDIA_MOUNTED_READ_ONLY.equals(mounted))
            state.serverProblem(TransferState.SERVER_NO_CARD, mounted);
        try {
            server = new LinkServer(new StorageRoot(card), model, Protocol.friendlyName(model), stats, logger);
            server.start(Protocol.HTTP_PORT);
        } catch (Throwable t) {
            stepFailed("server start", t);
            state.serverProblem(TransferState.SERVER_PORT, String.valueOf(t.getMessage()));
            server = null;
        }
    }

    // ---- the hotspot: Wi-Fi on, Direct on, then a group owner on the camera's saved Direct configuration

    private void startHotspot() {
        groupRequested = false;
        if (hotspot == null && p2p == null) {
            String probe;
            try { probe = Hotspot.probe(activity); } catch (Throwable t) { probe = "probe threw " + t; }
            step("no Direct manager\n" + probe);
            state.failed(TransferState.UNAVAILABLE, null);
            return;
        }
        if (hotspot == null) {
            startP2p();
            return;
        }
        try { step("wifi on: " + wifi.setWifiEnabled(true) + "  state " + wifiState()); } catch (Throwable t) { stepFailed("wifi on", t); }
        boolean enabled = hotspot.setEnabled(true);
        int direct = hotspot.state();
        step("direct on: " + enabled + "  state " + direct);
        if (!enabled) {
            state.failed(TransferState.REFUSED, null);
            return;
        }
        if (direct == Hotspot.STATE_ENABLED) startGroup();
    }

    /** Sony's P2P manager: its channel first, then Wi-Fi; Direct goes on once Wi-Fi says it is enabled */
    private void startP2p() {
        if (!p2p.open()) {
            state.failed(TransferState.REFUSED, null);
            return;
        }
        try { step("wifi on: " + wifi.setWifiEnabled(true) + "  state " + wifiState()); } catch (Throwable t) { stepFailed("wifi on", t); }
        if (wifiEnabled()) p2p.enableDirect();
    }

    private boolean wifiEnabled() {
        try { return wifi.getWifiState() == WifiManager.WIFI_STATE_ENABLED; } catch (Throwable t) { return false; }
    }

    private final HotspotP2p.Events p2pEvents = new HotspotP2p.Events() {
        @Override public void step(String msg) {
            TransferMode.this.step(msg);
        }

        @Override public void refused(String why) {
            if (!running || state.ready()) return;
            state.failed(TransferState.REFUSED, why);
            changed();
        }

        @Override public void up(String ssid, String password) {
            if (!running) return;
            step("group up: " + ssid + (password == null ? "  no password" : ""));   // the password stays off the card
            state.up(ssid, password);
            changed();
        }

        @Override public void failed(String why) {
            if (!running || state.ready()) return;
            state.failed(TransferState.FAILED, why);
            changed();
        }

        @Override public void phoneJoined() {
            if (!running) return;
            step("phone joined");
            state.note(TransferState.NOTE_PHONE_JOINED);
            changed();
        }

        @Override public void phoneLeft() {
            if (!running) return;
            step("phone left");
            state.note(TransferState.NOTE_PHONE_LEFT);
            changed();
        }
    };

    private String airplane() {
        try { return String.valueOf(Settings.System.getInt(activity.getContentResolver(), Settings.System.AIRPLANE_MODE_ON, 0)); } catch (Throwable t) { return "?"; }
    }

    private String wifiState() {
        try { return String.valueOf(wifi.getWifiState()); } catch (Throwable t) { return "?"; }
    }

    private void startGroup() {
        if (groupRequested || state.status() != TransferState.STARTING) return;
        groupRequested = true;
        step("group: " + hotspot.configurationCount() + " configurations");
        int failed = hotspot.startGroupOwner();
        step("group start: " + (failed < 0 ? "requested" : "status " + failed + (hotspot.errorDetail() == null ? "" : "  " + hotspot.errorDetail())));
        if (failed >= 0) state.failed(failed, hotspot.error());
        changed();
    }

    private void stopHotspot() {
        if (p2p != null) {
            p2p.stop();
            p2p = null;
        }
        if (hotspot == null) return;
        if (groupRequested) hotspot.removeGroup();
        hotspot.setEnabled(false);
        groupRequested = false;
    }

    private final BroadcastReceiver directReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (!running) return;
            String action = intent.getAction();
            if (WifiManager.WIFI_STATE_CHANGED_ACTION.equals(action)) {
                int wifiState = intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE, WifiManager.WIFI_STATE_UNKNOWN);
                step("wifi state " + wifiState);
                if (p2p != null && wifiState == WifiManager.WIFI_STATE_ENABLED) p2p.enableDirect();   // once: it says so itself
            } else if (hotspot == null) {
                return;                                               // no Direct manager: only Wi-Fi's own state is logged
            } else if (Hotspot.ACTION_STATE_CHANGED.equals(action)) {
                int direct = intent.getIntExtra(Hotspot.EXTRA_STATE, Hotspot.STATE_UNKNOWN);
                step("direct state " + direct);
                if (direct == Hotspot.STATE_ENABLED) startGroup();
            } else if (Hotspot.ACTION_GROUP_CREATE_SUCCESS.equals(action)) {
                Object config = Hotspot.config(intent, activity.getClassLoader());
                String ssid = Hotspot.ssid(config), pass = Hotspot.password(config);
                Object started = hotspot.startedConfig();             // the broadcast's copy could not be read: ours
                if (ssid == null) ssid = Hotspot.ssid(started);
                if (pass == null) pass = Hotspot.password(started);
                step("group up: " + ssid + (pass == null ? "  no password" : ""));   // the password stays off the card
                state.up(ssid, pass);
                changed();
            } else if (Hotspot.ACTION_GROUP_CREATE_FAILURE.equals(action)) {
                step("group failed: error " + intent.getIntExtra(Hotspot.EXTRA_ERROR_CODE, -1));
                state.failed(TransferState.FAILED, String.valueOf(intent.getIntExtra(Hotspot.EXTRA_ERROR_CODE, -1)));
                changed();
            } else if (Hotspot.ACTION_STA_CONNECTED.equals(action)) {
                step("phone joined");
                state.note(TransferState.NOTE_PHONE_JOINED);
                changed();
            } else if (Hotspot.ACTION_STA_DISCONNECTED.equals(action)) {
                step("phone left");
                state.note(TransferState.NOTE_PHONE_LEFT);
                changed();
            }
        }
    };

    private void registerReceiver() {
        if (receiverRegistered) return;
        IntentFilter d = new IntentFilter();
        d.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
        d.addAction(Hotspot.ACTION_STATE_CHANGED);
        d.addAction(Hotspot.ACTION_GROUP_CREATE_SUCCESS);
        d.addAction(Hotspot.ACTION_GROUP_CREATE_FAILURE);
        d.addAction(Hotspot.ACTION_STA_CONNECTED);
        d.addAction(Hotspot.ACTION_STA_DISCONNECTED);
        activity.registerReceiver(directReceiver, d);
        receiverRegistered = true;
    }

    private void unregisterReceiver() {
        if (!receiverRegistered) return;
        try { activity.unregisterReceiver(directReceiver); } catch (Throwable ignored) {}
        receiverRegistered = false;
    }

    // ---- the tick

    private void tick() {
        if (!running) return;
        long now = now();
        if (now - lastApoMs > APO_REFRESH_MS) setAutoPowerOff(false);
        state.tick(now);
        if (state.status() == TransferState.TIMEOUT && !timeoutLogged) {
            timeoutLogged = true;
            step("timed out  wifi state " + wifiState() + "  direct state " + (hotspot != null ? String.valueOf(hotspot.state()) : p2p != null ? "p2p" : "none"));
        }
        rate = stats.sampleRate(now);
        changed();
    }
}
