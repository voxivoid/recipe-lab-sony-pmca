package com.voxivoid.recipelab;

/**
 * JNI binding to jni/playback.c: rows of a picture into an image the camera holds in its own memory (an
 * OptimizedImage laid out as {@link ImageCanvas#GEOMETRY} says), for the camera to save it as one of its photos
 * ({@link Playback}). Open, hand the rows over in order, close.
 */
final class NativePlayback {
    static { System.loadLibrary("recipelab"); }

    private NativePlayback() {}

    /** the image, ready for a {@code w} × {@code h} picture; a handle for {@link #rows} and {@link #close}. Throws why not */
    static native int open(Object image, int[] geometry, int w, int h);

    /** {@code count} rows of ARGB pixels, {@code width} each, the first being row {@code y} */
    static native void rows(int handle, int[] argb, int width, int y, int count);

    /** the last rows in: null when all {@code rows} went in, else why not. The handle is gone after */
    static native String close(int handle, int rows);
}
