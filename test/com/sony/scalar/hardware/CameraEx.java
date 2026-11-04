package com.sony.scalar.hardware;

/**
 * Test double of the camera's {@code CameraEx}, its autofocus part only, shaped like the OpenMemories-Framework stubs:
 * the three listener setters and the area classes. It keeps the listeners set, so a test can call them as the camera
 * would. {@link Rect} stands in for android.graphics.Rect, with the same public fields.
 */
public class CameraEx {
    public interface FocusAreaListener { void onChanged(FocusAreaInfos infos, CameraEx camera); }
    public interface AutoFocusStartListener { void onStart(CameraEx camera); }
    public interface AutoFocusDoneListener {
        int STATUS_CLEAR = 0, STATUS_LOCK = 1, STATUS_LOCK_WARM = 2, STATUS_LOCK_WARN = 2, STATUS_WORKING = 3, STATUS_CONTINUOUS = 4;
        void onDone(int status, int[] areas, CameraEx camera);
    }

    public static class FocusAreaInfos {
        public String focusAreaMode;
        public FocusAreaRectInfo[] rectInfos;
    }

    public static class FocusAreaRectInfo {
        public boolean enable;
        public int index;
        public Rect rect;

        public FocusAreaRectInfo(int index, boolean enable, Rect rect) { this.index = index; this.enable = enable; this.rect = rect; }
    }

    public static class Rect {
        public int left, top, right, bottom;

        public Rect(int left, int top, int right, int bottom) { this.left = left; this.top = top; this.right = right; this.bottom = bottom; }
    }

    public FocusAreaListener focusArea;
    public AutoFocusStartListener start;
    public AutoFocusDoneListener done;

    public final void setFocusAreaListener(FocusAreaListener l) { focusArea = l; }
    public void setAutoFocusStartListener(AutoFocusStartListener l) { start = l; }
    public void setAutoFocusDoneListener(AutoFocusDoneListener l) { done = l; }
}
