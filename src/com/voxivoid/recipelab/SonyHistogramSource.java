package com.voxivoid.recipelab;

import android.os.Handler;
import android.os.SystemClock;

import com.voxivoid.recipelab.transfer.Logger;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The live histogram's data: Sony's own preview analysis, {@code CameraEx.setPreviewAnalizeListener}, whose callback
 * hands over {@code AnalizedData.hist.Y} — luminance bins of the live image — all by reflection, as the rest of the
 * app reaches CameraEx. Registered only while the histogram is on and shown; {@link #stop} detaches it.
 *
 * The callback may come on any thread. It takes at most one frame per {@link HistogramMath#FRAME_MS}, turns it into
 * bar heights there, and hands the newest to the UI thread — never a queue of them. It calls nothing on the camera.
 * A callback from an earlier registration (the camera released, the histogram turned off and on) carries an old
 * generation and is dropped. Without the API, or without valid frames, the view is left empty and the reason goes to
 * the card's log once: there is no fallback that computes a histogram from preview frames.
 */
final class SonyHistogramSource {
    private static final String LISTENER = "com.sony.scalar.hardware.CameraEx$PreviewAnalizeListener";

    private final Handler ui;
    private final HistogramView view;
    private final Logger log;
    private Object cameraEx;                                       // the camera the listener is registered on; null when not
    private Method setter;
    private boolean unavailable;                                   // registration failed: not retried until the next stop()
    private boolean sawFrame;                                      // a valid frame came since start()
    private volatile int generation;
    private volatile boolean toldMalformed;
    private long lastFrame;                                        // callback side: when the last frame was taken
    private float[] latest;                                        // guarded by this: the newest heights, not yet drawn
    private final AtomicBoolean posted = new AtomicBoolean();

    private final Runnable deliver = new Runnable() { public void run() {
        posted.set(false);
        float[] h;
        synchronized (SonyHistogramSource.this) { h = latest; latest = null; }
        if (h == null || cameraEx == null) return;
        sawFrame = true;
        view.setHeights(h);
        ui.removeCallbacks(stale);
        ui.postDelayed(stale, HistogramMath.STALE_MS);
    } };

    /** no valid frame for a while: clear the graph rather than show an old scene; said once if none ever came */
    private final Runnable stale = new Runnable() { public void run() {
        view.setHeights(null);
        if (!sawFrame) log.info("histogram: no data from the camera's preview analysis in " + HistogramMath.FIRST_MS + " ms");
    } };

    SonyHistogramSource(Handler ui, HistogramView view, Logger log) { this.ui = ui; this.view = view; this.log = log; }

    boolean running() { return cameraEx != null; }

    /** registers on {@code cx}; does nothing while registered, or after a failure until the next {@link #stop} */
    void start(Object cx) {
        if (cx == null || cameraEx != null || unavailable) return;
        final int gen;
        synchronized (this) { gen = ++generation; latest = null; }
        sawFrame = false; toldMalformed = false; lastFrame = 0;
        Method m = null;
        try {
            Class<?> li = Class.forName(LISTENER);
            m = cx.getClass().getMethod("setPreviewAnalizeListener", li);
            Object listener = Proxy.newProxyInstance(li.getClassLoader(), new Class<?>[] { li }, new InvocationHandler() {
                public Object invoke(Object proxy, Method method, Object[] args) {
                    String name = method.getName();
                    if (name.equals("equals")) return args != null && args.length == 1 && proxy == args[0];
                    if (name.equals("hashCode")) return System.identityHashCode(proxy);
                    if (name.equals("toString")) return "RecipeLab histogram listener";
                    if (name.equals("onAnalizedData") && args != null && args.length > 0) frame(gen, args[0]);
                    return null;
                }
            });
            m.invoke(cx, listener);
            cameraEx = cx; setter = m;
            ui.postDelayed(stale, HistogramMath.FIRST_MS);
        } catch (Throwable t) {
            unavailable = true;
            if (m != null) try { m.invoke(cx, (Object) null); } catch (Throwable ignored) {}   // in case it half attached
            log.error("histogram unavailable", t);
        }
    }

    /** detaches from the camera it registered on, drops anything not yet drawn and clears the graph */
    void stop() {
        unavailable = false;
        if (cameraEx == null) return;
        synchronized (this) { generation++; latest = null; }
        ui.removeCallbacks(deliver); ui.removeCallbacks(stale); posted.set(false);
        try { setter.invoke(cameraEx, (Object) null); } catch (Throwable t) { log.error("histogram listener not detached", t); }
        cameraEx = null; setter = null;
        view.setHeights(null);
    }

    /** the callback thread: one frame of Sony's analysis */
    private void frame(int gen, Object data) {
        if (gen != generation || data == null) return;
        long now = SystemClock.uptimeMillis();
        if (!HistogramMath.due(lastFrame, now)) return;
        short[] y;
        try {
            Object hist = data.getClass().getField("hist").get(data);
            y = hist == null ? null : (short[]) hist.getClass().getField("Y").get(hist);
        } catch (Throwable t) {
            if (!toldMalformed) { toldMalformed = true; final Throwable why = t; ui.post(new Runnable() { public void run() { log.error("histogram data unreadable", why); } }); }
            return;
        }
        float[] h = HistogramMath.heights(y);
        if (h == null) return;                                     // nothing valid: the last graph stands until it goes stale
        lastFrame = now;
        synchronized (this) { if (gen != generation) return; latest = h; }
        if (posted.compareAndSet(false, true)) ui.post(deliver);
    }
}
