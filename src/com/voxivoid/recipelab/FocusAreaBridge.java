package com.voxivoid.recipelab;

import java.lang.reflect.Array;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

/**
 * Sony's autofocus callbacks on CameraEx — the AF areas, AF start and AF done — through reflection and dynamic
 * proxies, as the Sony classes exist on the camera only. Each event is copied into plain values on the thread it came
 * on (often Sony's own) and handed to a {@link Sink}, tagged with the camera session it was registered for; the sink
 * moves it to the UI thread. Everything is best effort: a body without a listener, or a callback that throws, leaves
 * the live view and the shutter as they were. No Android, covered by test/ against doubles of the Sony classes.
 */
final class FocusAreaBridge {
    /** where the copied events go; called on whatever thread the camera calls back on */
    interface Sink {
        void areas(int gen, FocusOverlayModel.Area[] areas);
        void started(int gen);
        void done(int gen, int status, int[] ids);
        /** a callback could not be read; said once per kind by the caller's log */
        void failed(String what);
    }

    private static final String CX = "com.sony.scalar.hardware.CameraEx";
    /** setter, listener interface and its one method, for each of the three */
    private static final String[][] LISTENERS = {
        { "setFocusAreaListener", CX + "$FocusAreaListener", "onChanged" },
        { "setAutoFocusStartListener", CX + "$AutoFocusStartListener", "onStart" },
        { "setAutoFocusDoneListener", CX + "$AutoFocusDoneListener", "onDone" },
    };

    private FocusAreaBridge() {}

    /**
     * Registers the three listeners on {@code cameraEx} for session {@code gen}. Returns null when all three took, else
     * what did not, for the log; a missing one never stops the others.
     */
    static String register(Object cameraEx, final int gen, final Sink sink) {
        StringBuilder missing = new StringBuilder();
        for (final String[] l : LISTENERS) {
            try {
                Class<?> type = Class.forName(l[1]);
                Object proxy = Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] { type }, new InvocationHandler() {
                    public Object invoke(Object self, Method m, Object[] args) {
                        if (m.getDeclaringClass() == Object.class) return objectMethod(self, m, args);
                        if (!m.getName().equals(l[2])) return null;
                        try { deliver(l[2], args, gen, sink); }
                        catch (Throwable t) { sink.failed(l[2] + ": " + t); }   // never into Sony's thread
                        return null;
                    }
                });
                cameraEx.getClass().getMethod(l[0], type).invoke(cameraEx, proxy);
            } catch (Throwable t) {
                missing.append(missing.length() == 0 ? "" : ", ").append(l[0]).append(": ").append(t);
            }
        }
        return missing.length() == 0 ? null : missing.toString();
    }

    /** takes the three listeners off again (each set to null), where the body has them; never throws */
    static void unregister(Object cameraEx) {
        for (String[] l : LISTENERS) {
            try { cameraEx.getClass().getMethod(l[0], Class.forName(l[1])).invoke(cameraEx, (Object) null); }
            catch (Throwable t) {}
        }
    }

    private static void deliver(String method, Object[] args, int gen, Sink sink) throws Exception {
        if (method.equals("onChanged")) sink.areas(gen, areas(args[0]));
        else if (method.equals("onStart")) sink.started(gen);
        else sink.done(gen, ((Integer) args[0]).intValue(), args[1] == null ? new int[0] : ((int[]) args[1]).clone());
    }

    /** equals / hashCode / toString on a listener proxy: identity, as Sony may keep it in a set or log it */
    private static Object objectMethod(Object self, Method m, Object[] args) {
        if (m.getName().equals("equals")) return Boolean.valueOf(args != null && self == args[0]);
        if (m.getName().equals("hashCode")) return Integer.valueOf(System.identityHashCode(self));
        return "RecipeLab AF listener";
    }

    /**
     * A copy of CameraEx.FocusAreaInfos' rectInfos — each FocusAreaRectInfo's index, enable and rect (an Android Rect,
     * read by its public fields) — so nothing of Sony's mutable arrays is kept. A null entry or rect stays a null hole,
     * so positions still count; no infos is an empty list; a shape this does not know throws, and the proxy reports it.
     */
    static FocusOverlayModel.Area[] areas(Object infos) throws Exception {
        List<FocusOverlayModel.Area> out = new ArrayList<FocusOverlayModel.Area>();
        Object rects = infos == null ? null : infos.getClass().getField("rectInfos").get(infos);
        int n = rects == null ? 0 : Array.getLength(rects);
        for (int i = 0; i < n; i++) {
            Object info = Array.get(rects, i);
            Object r = info == null ? null : info.getClass().getField("rect").get(info);
            if (r == null) { out.add(null); continue; }          // kept as a hole: the position fallback counts it
            Class<?> c = info.getClass(), rc = r.getClass();
            out.add(new FocusOverlayModel.Area(c.getField("index").getInt(info), c.getField("enable").getBoolean(info),
                    rc.getField("left").getInt(r), rc.getField("top").getInt(r), rc.getField("right").getInt(r), rc.getField("bottom").getInt(r)));
        }
        return out.toArray(new FocusOverlayModel.Area[out.size()]);
    }
}
