package com.voxivoid.recipelab.transfer;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.WifiManager;
import android.os.Environment;
import android.os.Handler;
import android.os.SystemClock;
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

    private WifiManager wifi;
    private Hotspot hotspot;
    private LinkServer server;
    private Thread.UncaughtExceptionHandler crashHandler, previousCrashHandler;
    private boolean running, receiverRegistered, groupRequested;
    private long lastApoMs;
    private double rate;

    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            tick();
            handler.postDelayed(this, TICK_MS);
        }
    };

    /** {@code model} is the camera's model code as /api/info reports it, e.g. ILCE-6000. */
    public TransferMode(Activity activity, String model, Listener listener) {
        this.activity = activity;
        this.model = model == null || model.length() == 0 ? "unknown" : model;
        this.listener = listener;
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
        wifi = (WifiManager) activity.getSystemService(Context.WIFI_SERVICE);
        hotspot = Hotspot.create(activity);
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
            logger.error("server start", t);
            state.serverProblem(TransferState.SERVER_PORT, String.valueOf(t.getMessage()));
            server = null;
        }
    }

    // ---- the hotspot: Wi-Fi on, Direct on, then a group owner on the camera's saved Direct configuration

    private void startHotspot() {
        groupRequested = false;
        if (hotspot == null) {
            state.failed(TransferState.UNAVAILABLE, null);
            return;
        }
        try { wifi.setWifiEnabled(true); } catch (Throwable t) { logger.error("wifi on", t); }
        if (!hotspot.setEnabled(true)) {
            state.failed(TransferState.REFUSED, null);
            return;
        }
        if (hotspot.state() == Hotspot.STATE_ENABLED) startGroup();
    }

    private void startGroup() {
        if (groupRequested || state.status() != TransferState.STARTING) return;
        groupRequested = true;
        int failed = hotspot.startGroupOwner();
        if (failed >= 0) state.failed(failed, hotspot.error());
        changed();
    }

    private void stopHotspot() {
        if (hotspot == null) return;
        if (groupRequested) hotspot.removeGroup();
        hotspot.setEnabled(false);
        groupRequested = false;
    }

    private final BroadcastReceiver directReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (!running) return;
            String action = intent.getAction();
            if (Hotspot.ACTION_STATE_CHANGED.equals(action)) {
                if (intent.getIntExtra(Hotspot.EXTRA_STATE, Hotspot.STATE_UNKNOWN) == Hotspot.STATE_ENABLED) startGroup();
            } else if (Hotspot.ACTION_GROUP_CREATE_SUCCESS.equals(action)) {
                Object config = Hotspot.config(intent, activity.getClassLoader());
                String ssid = Hotspot.ssid(config), pass = Hotspot.password(config);
                Object started = hotspot.startedConfig();             // the broadcast's copy could not be read: ours
                if (ssid == null) ssid = Hotspot.ssid(started);
                if (pass == null) pass = Hotspot.password(started);
                state.up(ssid, pass);
                changed();
            } else if (Hotspot.ACTION_GROUP_CREATE_FAILURE.equals(action)) {
                state.failed(TransferState.FAILED, String.valueOf(intent.getIntExtra(Hotspot.EXTRA_ERROR_CODE, -1)));
                changed();
            } else if (Hotspot.ACTION_STA_CONNECTED.equals(action)) {
                state.note(TransferState.NOTE_PHONE_JOINED);
                changed();
            } else if (Hotspot.ACTION_STA_DISCONNECTED.equals(action)) {
                state.note(TransferState.NOTE_PHONE_LEFT);
                changed();
            }
        }
    };

    private void registerReceiver() {
        if (receiverRegistered || hotspot == null) return;
        IntentFilter d = new IntentFilter();
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
        rate = stats.sampleRate(now);
        changed();
    }
}
