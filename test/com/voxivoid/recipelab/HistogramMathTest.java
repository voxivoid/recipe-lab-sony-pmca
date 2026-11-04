package com.voxivoid.recipelab;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/** The live histogram's numbers: Sony's Y bins to bar heights, what is refused rather than drawn, and the frame rate. */
class HistogramMathTest {
    private static final float EPS = 1e-6f;

    private static short[] bins(int n, int value) { short[] y = new short[n]; java.util.Arrays.fill(y, (short) value); return y; }

    @Test void nothingTrueToDrawIsNull() {
        assertNull(HistogramMath.heights(null));
        assertNull(HistogramMath.heights(new short[0]));
        assertNull(HistogramMath.heights(new short[] { 500 }), "one bin is no distribution");
        assertNull(HistogramMath.heights(bins(256, 0)), "an all-dark analysis of nothing: no peak to scale by");
        assertNull(HistogramMath.heights(bins(HistogramMath.MAX_SOURCE + 1, 7)), "longer than any histogram the app knows");
    }

    @Test void theLimitsThemselvesAreAccepted() {
        assertNotNull(HistogramMath.heights(new short[] { 0, 1 }));
        float[] h = HistogramMath.heights(bins(HistogramMath.MAX_SOURCE, 3));
        assertEquals(HistogramMath.BINS, h.length);
        for (float v : h) assertEquals(1f, v, EPS);
    }

    @Test void barsRunDarkToBrightAndTheTallestIsOne() {
        short[] y = new short[256];
        for (int i = 0; i < y.length; i++) y[i] = (short) i;      // more and more toward the bright end
        float[] h = HistogramMath.heights(y);
        assertEquals(HistogramMath.BINS, h.length);
        for (int i = 1; i < h.length; i++) assertTrue(h[i] > h[i - 1], "bar " + i);
        assertEquals(1f, h[h.length - 1], EPS);
        assertEquals(1.5f / 253.5f, h[0], EPS, "the first bar is the mean of bins 0..3");
    }

    @Test void binsAreReadUnsigned() {
        short[] y = bins(64, 1000);
        y[10] = (short) 0xffff;                                    // 65535, not -1
        float[] h = HistogramMath.heights(y);
        assertEquals(1f, h[10], EPS);
        assertEquals(1000f / 65535f, h[0], EPS);
    }

    @Test void everyMaximumBinSumsWithoutOverflow() {
        float[] h = HistogramMath.heights(bins(HistogramMath.MAX_SOURCE, 0xffff));
        for (float v : h) assertEquals(1f, v, EPS);
    }

    @Test void aNarrowSpikeIsLoweredNotLost() {
        short[] y = bins(256, 0);
        y[201] = 400;                                              // one source bin of four in bar 50
        y[0] = 100;
        float[] h = HistogramMath.heights(y);
        assertEquals(1f, h[50], EPS);
        assertEquals(0.25f, h[0], EPS, "bar 0 holds 100 over four bins: a quarter of bar 50's mean");
        for (int i = 1; i < h.length; i++) if (i != 50) assertEquals(0f, h[i], EPS, "bar " + i);
    }

    @Test void fewerBinsThanBarsAreRepeatedInOrder() {
        float[] h = HistogramMath.heights(new short[] { 2, 4 });
        for (int i = 0; i < 32; i++) assertEquals(0.5f, h[i], EPS, "bar " + i);
        for (int i = 32; i < 64; i++) assertEquals(1f, h[i], EPS, "bar " + i);
        float[] odd = HistogramMath.heights(new short[] { 0, 0, 9 });
        assertEquals(0f, odd[42], EPS);
        assertEquals(1f, odd[43], EPS, "bar 43 is the first to land on the third bin");
        assertEquals(1f, odd[63], EPS);
    }

    @Test void barsWithUnevenSharesAreMeansNotSums() {
        short[] y = bins(100, 10);                                 // 100 bins over 64 bars: shares of one and two
        float[] h = HistogramMath.heights(y);
        for (float v : h) assertEquals(1f, v, EPS, "a flat input stays flat");
    }

    @Test void everyHeightIsInsideTheGraph() {
        short[] y = new short[300];
        for (int i = 0; i < y.length; i++) y[i] = (short) (i * 7919 % 65536);
        for (float v : HistogramMath.heights(y)) assertTrue(v >= 0f && v <= 1f, "height " + v);
    }

    @Test void atMostEightFramesASecond() {
        assertEquals(125, HistogramMath.FRAME_MS);
        assertTrue(HistogramMath.due(0, 125));
        assertFalse(HistogramMath.due(0, 124));
        assertTrue(HistogramMath.due(1000, 1200));
        assertFalse(HistogramMath.due(1000, 1000), "a second frame in the same instant is dropped");
    }

    @Test void oldGraphsGoAndSilenceIsReported() {
        assertEquals(2000, HistogramMath.STALE_MS);
        assertEquals(3000, HistogramMath.FIRST_MS);
    }
}
