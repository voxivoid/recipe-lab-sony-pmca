package com.voxivoid.recipelab;

import static org.junit.jupiter.api.Assertions.*;

import com.sony.scalar.hardware.CameraEx;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Sony's autofocus callbacks through reflection, against a test double of CameraEx (test/com/sony/scalar/hardware,
 * shaped like the OpenMemories-Framework stubs): the listeners are found and set, their events copied and tagged with
 * the session, and a body without them, or a callback that throws, costs nothing but a line in the log.
 */
class FocusAreaBridgeTest {
    /** what the sink was handed, in order */
    static final class Recorder implements FocusAreaBridge.Sink {
        final List<String> events = new ArrayList<String>();
        FocusOverlayModel.Area[] areas;
        int[] ids;
        boolean throwOnDone;

        public void areas(int gen, FocusOverlayModel.Area[] a) { areas = a; events.add("areas " + gen + " " + a.length); }
        public void started(int gen) { events.add("started " + gen); }
        public void done(int gen, int status, int[] ids) {
            if (throwOnDone) throw new IllegalStateException("boom");
            this.ids = ids; events.add("done " + gen + " " + status);
        }
        public void failed(String what) { events.add("failed " + what); }
    }

    /** a body with only the AF done listener */
    public static class OnlyDone {
        public CameraEx.AutoFocusDoneListener done;
        public void setAutoFocusDoneListener(CameraEx.AutoFocusDoneListener l) { done = l; }
    }

    private static CameraEx.FocusAreaInfos infos(CameraEx.FocusAreaRectInfo... rects) {
        CameraEx.FocusAreaInfos i = new CameraEx.FocusAreaInfos();
        i.focusAreaMode = "flexible-spot"; i.rectInfos = rects;
        return i;
    }

    @Test void allThreeListenersAreSet() {
        CameraEx cam = new CameraEx();
        assertNull(FocusAreaBridge.register(cam, 1, new Recorder()));
        assertNotNull(cam.focusArea);
        assertNotNull(cam.start);
        assertNotNull(cam.done);
    }

    @Test void theAreasAreCopiedWithTheirSession() throws Exception {
        CameraEx cam = new CameraEx();
        Recorder r = new Recorder();
        FocusAreaBridge.register(cam, 5, r);
        CameraEx.Rect rect = new CameraEx.Rect(-900, -50, -700, 50);
        CameraEx.FocusAreaRectInfo info = new CameraEx.FocusAreaRectInfo(3, true, rect);
        cam.focusArea.onChanged(infos(info, new CameraEx.FocusAreaRectInfo(4, false, new CameraEx.Rect(1, 2, 3, 4))), cam);
        assertEquals("areas 5 2", r.events.get(0));
        FocusOverlayModel.Area a = r.areas[0];
        assertEquals(3, a.index);
        assertTrue(a.enabled);
        assertEquals(-900, a.left); assertEquals(-50, a.top); assertEquals(-700, a.right); assertEquals(50, a.bottom);
        assertFalse(r.areas[1].enabled);
        assertEquals(4, r.areas[1].index);
        assertEquals(1, r.areas[1].left); assertEquals(2, r.areas[1].top); assertEquals(3, r.areas[1].right); assertEquals(4, r.areas[1].bottom);
        rect.left = 0; info.index = 9;
        assertEquals(-900, a.left, "a copy: Sony may reuse its objects");
        assertEquals(3, a.index);
    }

    @Test void aMissingEntryOrRectStaysAHole() throws Exception {
        FocusOverlayModel.Area[] a = FocusAreaBridge.areas(infos(null, new CameraEx.FocusAreaRectInfo(1, true, null),
                new CameraEx.FocusAreaRectInfo(2, true, new CameraEx.Rect(0, 0, 10, 10))));
        assertEquals(3, a.length);
        assertNull(a[0]);
        assertNull(a[1]);
        assertEquals(2, a[2].index);
    }

    @Test void noInfosOrNoRectsIsAnEmptyList() throws Exception {
        assertEquals(0, FocusAreaBridge.areas(null).length);
        assertEquals(0, FocusAreaBridge.areas(infos((CameraEx.FocusAreaRectInfo[]) null)).length);
        Recorder r = new Recorder();
        CameraEx cam = new CameraEx();
        FocusAreaBridge.register(cam, 1, r);
        cam.focusArea.onChanged(null, cam);
        assertEquals("areas 1 0", r.events.get(0));
    }

    @Test void startAndDoneArePassedOn() {
        CameraEx cam = new CameraEx();
        Recorder r = new Recorder();
        FocusAreaBridge.register(cam, 2, r);
        cam.start.onStart(cam);
        int[] ids = { 7, 3 };
        cam.done.onDone(CameraEx.AutoFocusDoneListener.STATUS_LOCK, ids, cam);
        assertEquals("started 2", r.events.get(0));
        assertEquals("done 2 1", r.events.get(1));
        assertArrayEquals(new int[] { 7, 3 }, r.ids);
        ids[0] = 0;
        assertEquals(7, r.ids[0], "the numbers are a copy");
    }

    @Test void noNumbersAreAnEmptyList() {
        CameraEx cam = new CameraEx();
        Recorder r = new Recorder();
        FocusAreaBridge.register(cam, 2, r);
        cam.done.onDone(CameraEx.AutoFocusDoneListener.STATUS_LOCK_WARN, null, cam);
        assertEquals("done 2 2", r.events.get(0));
        assertEquals(0, r.ids.length);
    }

    @Test void aCallbackThatThrowsIsReportedNotThrown() {
        CameraEx cam = new CameraEx();
        Recorder r = new Recorder();
        r.throwOnDone = true;
        FocusAreaBridge.register(cam, 2, r);
        cam.done.onDone(1, new int[0], cam);                    // must not reach the camera's thread
        assertEquals(1, r.events.size());
        assertTrue(r.events.get(0).startsWith("failed onDone: java.lang.IllegalStateException"), r.events.get(0));
    }

    @Test void theListenersBehaveAsObjects() {
        CameraEx cam = new CameraEx();
        FocusAreaBridge.register(cam, 1, new Recorder());
        Object l = cam.done;
        assertTrue(l.equals(l));
        assertFalse(l.equals(cam.start));
        assertFalse(l.equals(null));
        assertEquals(System.identityHashCode(l), l.hashCode());
        assertEquals("RecipeLab AF listener", l.toString());
    }

    @Test void aMethodTheListenerDoesNotKnowIsIgnored() throws Throwable {
        CameraEx cam = new CameraEx();
        Recorder r = new Recorder();
        FocusAreaBridge.register(cam, 1, r);
        InvocationHandler h = Proxy.getInvocationHandler(cam.done);
        assertNull(h.invoke(cam.done, Runnable.class.getMethod("run"), null));
        assertEquals(Boolean.FALSE, h.invoke(cam.done, Object.class.getMethod("equals", Object.class), null),
                "equals with no argument is false, not a crash");
        assertTrue(r.events.isEmpty());
    }

    @Test void aBodyWithoutTheListenersSaysWhichAreMissing() {
        String missing = FocusAreaBridge.register(new Object(), 1, new Recorder());
        assertNotNull(missing);
        assertTrue(missing.startsWith("setFocusAreaListener: "), missing);
        assertTrue(missing.contains(", setAutoFocusStartListener: "), missing);
        assertTrue(missing.contains(", setAutoFocusDoneListener: "), missing);
    }

    @Test void aMissingListenerDoesNotStopTheOthers() {
        OnlyDone cam = new OnlyDone();
        Recorder r = new Recorder();
        String missing = FocusAreaBridge.register(cam, 3, r);
        assertNotNull(cam.done);
        assertFalse(missing.contains("setAutoFocusDoneListener"), missing);
        assertTrue(missing.contains("setFocusAreaListener"), missing);
        cam.done.onDone(1, new int[] { 0 }, null);
        assertEquals("done 3 1", r.events.get(0));
    }

    @Test void unregisteringSetsEveryListenerToNull() {
        CameraEx cam = new CameraEx();
        FocusAreaBridge.register(cam, 1, new Recorder());
        FocusAreaBridge.unregister(cam);
        assertNull(cam.focusArea);
        assertNull(cam.start);
        assertNull(cam.done);
    }

    @Test void unregisteringWhatIsNotThereThrowsNothing() {
        FocusAreaBridge.unregister(null);
        FocusAreaBridge.unregister(new Object());
        OnlyDone cam = new OnlyDone();
        FocusAreaBridge.register(cam, 1, new Recorder());
        FocusAreaBridge.unregister(cam);
        assertNull(cam.done, "the ones it has are still taken off");
    }
}
