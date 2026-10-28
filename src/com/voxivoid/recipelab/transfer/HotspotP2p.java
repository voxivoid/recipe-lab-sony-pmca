package com.voxivoid.recipelab.transfer;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.NetworkInfo;
import android.net.wifi.p2p.WifiP2pGroup;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Looper;
import java.lang.reflect.Method;

/**
 * The camera's hotspot on bodies whose Direct mode is Sony's WifiP2pExtManager (the "wifip2p" service, Android 4.1
 * bodies such as the A6300) rather than the "wifi-direct" DirectManager {@link Hotspot} drives. The flow is the one
 * Sony's Smart Remote app uses (its WifiP2pManagerJb), as BI2QFA's SonyConnect ports it (tested on an A6300 2.01):
 * Wi-Fi on, then Sony's setDirectEnabled, then — once P2P reports itself enabled — a standard createGroup; the group's
 * name and passphrase come from requestGroupInfo once the group has formed. The camera is still the access point at
 * 192.168.122.1.
 *
 * Only {@link TransferMode} touches this class, and only when {@link Hotspot#create} found nothing, so a body without
 * android.net.wifi.p2p (API 10, the A6000) never loads it. Every callback arrives on the main thread.
 */
final class HotspotP2p {
    /** what the hotspot reports, on the main thread */
    interface Events {
        void step(String msg);
        /** setDirectEnabled was refused, or could not be called */
        void refused(String why);
        void up(String ssid, String password);
        void failed(String why);
        void phoneJoined();
        void phoneLeft();
    }

    static final String SERVICE = "wifip2p";
    private static final String ACTION_P2P_STATE = "android.net.wifi.p2p.STATE_CHANGED";
    private static final String ACTION_P2P_CONNECTION = "android.net.wifi.p2p.CONNECTION_STATE_CHANGE";
    private static final String ACTION_P2P_PEERS = "android.net.wifi.p2p.PEERS_CHANGED";
    private static final int P2P_STATE_ENABLED = 2;   // WifiP2pManager.WIFI_P2P_STATE_ENABLED

    private final Context context;
    private final WifiP2pManager manager;
    private final Method setDirectEnabled;
    private final Events events;
    private WifiP2pManager.Channel channel;
    private boolean receiverRegistered, directRequested, groupRequested, up, phone;

    private HotspotP2p(Context context, WifiP2pManager manager, Method setDirectEnabled, Events events) {
        this.context = context;
        this.manager = manager;
        this.setDirectEnabled = setDirectEnabled;
        this.events = events;
    }

    /** Sony's P2P manager, or null when the body has none (or it is not the class we expect). */
    static HotspotP2p create(Context context, Events events) {
        try {
            Object m = context.getSystemService(SERVICE);
            if (!(m instanceof WifiP2pManager)) return null;
            Method set = m.getClass().getMethod("setDirectEnabled",
                WifiP2pManager.Channel.class, boolean.class, WifiP2pManager.ActionListener.class);
            return new HotspotP2p(context, (WifiP2pManager) m, set, events);
        } catch (Throwable t) {
            return null;
        }
    }

    String managerClass() {
        return manager.getClass().getName();
    }

    /** the channel and the P2P receiver: call before Wi-Fi comes on */
    boolean open() {
        try {
            channel = manager.initialize(context, Looper.getMainLooper(), new WifiP2pManager.ChannelListener() {
                @Override public void onChannelDisconnected() { events.step("p2p channel lost"); }
            });
        } catch (Throwable t) {
            events.step("p2p initialize: " + t);
            return false;
        }
        if (channel == null) {
            events.step("p2p initialize: no channel");
            return false;
        }
        IntentFilter f = new IntentFilter();
        f.addAction(ACTION_P2P_STATE);
        f.addAction(ACTION_P2P_CONNECTION);
        f.addAction(ACTION_P2P_PEERS);
        context.registerReceiver(receiver, f);
        receiverRegistered = true;
        return true;
    }

    /** Wi-Fi is on: Sony's Direct mode on. P2P_STATE_ENABLED then starts the group. Once only. */
    void enableDirect() {
        if (directRequested || channel == null) return;
        directRequested = true;
        try {
            setDirectEnabled.invoke(manager, channel, true, listener("direct on", true));
        } catch (Throwable t) {
            Throwable cause = t.getCause() != null ? t.getCause() : t;
            events.step("direct on threw " + cause);
            events.refused(cause.getClass().getSimpleName());
        }
    }

    private void startGroup() {
        if (groupRequested || channel == null) return;
        groupRequested = true;
        events.step("p2p createGroup");
        try {
            manager.createGroup(channel, listener("createGroup", false));
        } catch (Throwable t) {
            events.step("createGroup threw " + t);
            events.failed(t.getClass().getSimpleName());
        }
    }

    /** group down, Direct off, receiver gone; Wi-Fi itself is TransferMode's to turn off */
    void stop() {
        if (receiverRegistered) {
            try { context.unregisterReceiver(receiver); } catch (Throwable ignored) {}
            receiverRegistered = false;
        }
        if (channel == null) return;
        if (groupRequested) {
            try { manager.removeGroup(channel, null); } catch (Throwable ignored) {}
        }
        if (directRequested) {
            try { setDirectEnabled.invoke(manager, channel, false, null); } catch (Throwable ignored) {}
        }
        directRequested = groupRequested = up = phone = false;
    }

    /**
     * Logs the answer. A failure of setDirectEnabled ({@code refuses}) is a refusal; of createGroup, a failed group.
     * Android's reasons: 0 error, 1 P2P unsupported, 2 busy.
     */
    private WifiP2pManager.ActionListener listener(final String what, final boolean refuses) {
        return new WifiP2pManager.ActionListener() {
            @Override public void onSuccess() {
                events.step(what + ": ok");
            }

            @Override public void onFailure(int reason) {
                events.step(what + ": failed, reason " + reason);
                if (refuses) events.refused(String.valueOf(reason));
                else events.failed(String.valueOf(reason));
            }
        };
    }

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent intent) {
            try {
                handle(intent);
            } catch (Throwable t) {
                events.step("p2p event " + intent.getAction() + " threw " + t);
            }
        }
    };

    private void handle(Intent intent) {
        String action = intent.getAction();
        if (ACTION_P2P_STATE.equals(action)) {
            int state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1);
            events.step("p2p state " + state);
            // the broadcast is sticky: one from before Direct was asked for says nothing about this start
            if (state == P2P_STATE_ENABLED && directRequested) startGroup();
        } else if (ACTION_P2P_CONNECTION.equals(action)) {
            WifiP2pInfo info = intent.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_INFO);
            NetworkInfo net = intent.getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO);
            boolean formed = info != null && info.groupFormed;
            NetworkInfo.DetailedState detail = net == null ? null : net.getDetailedState();
            events.step("p2p connection " + detail + (formed ? "  group formed  owner " + info.isGroupOwner + "  owner address " + info.groupOwnerAddress : ""));
            if (!groupRequested) return;
            if (formed && detail == NetworkInfo.DetailedState.CONNECTED) readGroup();
            else if (!formed && detail == NetworkInfo.DetailedState.FAILED && !up) events.failed("connection failed");
            else if (up) readGroup();
        } else if (ACTION_P2P_PEERS.equals(action)) {
            if (up) readGroup();
        }
    }

    /** the group's name and passphrase the first time, and whether a phone is in it every time */
    private void readGroup() {
        if (channel == null) return;
        manager.requestGroupInfo(channel, new WifiP2pManager.GroupInfoListener() {
            @Override public void onGroupInfoAvailable(WifiP2pGroup g) {
                if (g == null) return;
                if (!up) {
                    up = true;
                    events.up(g.getNetworkName(), g.getPassphrase());
                }
                boolean joined = g.getClientList() != null && !g.getClientList().isEmpty();
                events.step("p2p group " + g.getNetworkName() + "  interface " + g.getInterface() + "  clients " + (g.getClientList() == null ? 0 : g.getClientList().size()));
                if (joined && !phone) events.phoneJoined();
                else if (!joined && phone) events.phoneLeft();
                phone = joined;
            }
        });
    }
}
