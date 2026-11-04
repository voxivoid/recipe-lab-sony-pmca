package com.voxivoid.recipelab;

/**
 * The live histogram's numbers: Sony's luminance bins (CameraEx.AnalizedData.hist.Y, a short[] per preview frame) turned
 * into {@link #BINS} bar heights, and how often and how long they are drawn. No android.* import may appear in this
 * class (tools/test.sh).
 *
 * Nothing here invents data: input that cannot be a histogram — none, one bin, more than {@link #MAX_SOURCE}, all zero —
 * gives null, which the view draws as nothing.
 */
final class HistogramMath {
    private HistogramMath() {}

    /** bars drawn, dark (left) to bright (right) */
    static final int BINS = 64;
    /** the most source bins accepted: anything longer is not a histogram this app knows */
    static final int MAX_SOURCE = 4096;
    /** at most one frame per this many ms is processed and drawn (8 a second) */
    static final long FRAME_MS = 125;
    /** a graph with no new valid frame for this long is cleared rather than left describing an old scene */
    static final long STALE_MS = 2000;
    /** a histogram with no valid frame this long after it was turned on is reported as unavailable (once) */
    static final long FIRST_MS = 3000;

    /**
     * The bars for {@code y}: each the mean of its share of the source bins (read unsigned), scaled so the tallest is 1.
     * Fewer source bins than bars repeat; more are averaged, so a narrow spike is lowered, never dropped. Null when
     * there is nothing true to draw.
     */
    static float[] heights(short[] y) {
        if (y == null || y.length < 2 || y.length > MAX_SOURCE) return null;
        int n = y.length;
        double[] mean = new double[BINS];
        double peak = 0;
        for (int i = 0; i < BINS; i++) {
            int from = i * n / BINS, to = Math.max(from + 1, (i + 1) * n / BINS);
            long sum = 0;
            for (int k = from; k < to; k++) sum += y[k] & 0xffff;
            mean[i] = (double) sum / (to - from);
            peak = Math.max(peak, mean[i]);
        }
        if (peak == 0) return null;
        float[] h = new float[BINS];
        for (int i = 0; i < BINS; i++) h[i] = (float) (mean[i] / peak);
        return h;
    }

    /** whether a frame arriving at {@code now} is drawn, the last one drawn having come at {@code last} (both in ms) */
    static boolean due(long last, long now) { return now - last >= FRAME_MS; }
}
