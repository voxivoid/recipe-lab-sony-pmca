package com.voxivoid.recipelab;

/**
 * The image the camera saves a recipe card from (Playback): one it makes itself, so it is one of its own photos. From
 * Recipe Lab Overlay (its Canvas), which proved it on the A6000.
 * The camera decodes a photo it took — so one in its picture database — and its scaler makes a new
 * image of the card's size from it; the card's pixels are then written over that image's
 * (jni/playback.c, jni/canvas.c) and the camera encodes and files it as it files a shot.
 *
 * This decides what can be decided without the camera: the sizes the scaler is asked for, the DSP properties that
 * describe the image's memory, and whether the new image's memory is clear of the frame's.
 *
 * No android.* import may appear in this class (tools/test.sh).
 */
final class ImageCanvas {
    private ImageCanvas() {}

    /** the DSP properties for jni/canvas.h's geometry, in its order */
    static final String[] GEOMETRY = { "image-target-width", "image-target-height", "image-canvas-width", "image-canvas-height",
            "image-data-offset", "image-target-x-offset", "image-target-y-offset", "memory-size" };
    static final int SIZE = 7;

    /** the DSP's addresses are 30 bits: higher bits only say how the same memory is reached */
    private static final long ADDRESS_MASK = 0x3fffffffL;

    /**
     * The sizes to ask the scaler for, in turn, to make a {@code w} × {@code h} image from a {@code fw} × {@code fh}
     * frame, or null when it cannot be made (larger than the frame, or an odd width, which UYVY's pairs cannot hold).
     * Asked for the frame's own size the scaler may hand back the frame itself, which must not be written over; so a
     * same-size image is made through one of half the size.
     */
    static int[][] steps(int w, int h, int fw, int fh) {
        if (w < 2 || h < 2 || (w & 1) != 0 || w > fw || h > fh) return null;
        if (w == fw && h == fh) return new int[][] { { Math.max(2, (w / 2) & ~1), Math.max(2, h / 2) }, { w, h } };
        return new int[][] { { w, h } };
    }

    /** a DSP address, as its property reads (decimal, or 0x hex), in the 30 bits that matter; -1 when it is not a number */
    static long address(String s) {
        if (s == null) return -1;
        try { return Long.decode(s.trim()) & ADDRESS_MASK; } catch (NumberFormatException e) { return -1; }
    }

    /** whether two allocations, at those addresses and of those sizes, have no byte in common; false when unsure */
    static boolean separate(String a, long aSize, String b, long bSize) {
        long x = address(a), y = address(b);
        if (x < 0 || y < 0 || aSize <= 0 || bSize <= 0) return false;
        return x + aSize <= y || y + bSize <= x;
    }

    /** the geometry from the DSP's property strings, in {@link #GEOMETRY}'s order, or null when one is not a number */
    static int[] geometry(String[] values) {
        if (values == null || values.length != GEOMETRY.length) return null;
        int[] g = new int[values.length];
        for (int i = 0; i < g.length; i++) {
            try { g[i] = Integer.parseInt(values[i].trim()); } catch (RuntimeException e) { return null; }
        }
        return g;
    }
}
