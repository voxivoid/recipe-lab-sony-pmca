package com.voxivoid.recipelab;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/** The camera-made image a merged picture is saved from: the scaler's sizes, the DSP's geometry and addresses. */
class ImageCanvasTest {
    @Test void aSmallerImageIsOneStep() {
        assertArrayEquals(new int[][] { { 3000, 2000 } }, ImageCanvas.steps(3000, 2000, 6000, 4000));
    }

    @Test void theFramesOwnSizeGoesThroughHalf() {
        assertArrayEquals(new int[][] { { 3000, 2000 }, { 6000, 4000 } }, ImageCanvas.steps(6000, 4000, 6000, 4000));
        assertArrayEquals(new int[][] { { 2, 3 }, { 6, 7 } }, ImageCanvas.steps(6, 7, 6, 7), "half kept even");
    }

    @Test void whatCannotBeMadeIsNull() {
        assertNull(ImageCanvas.steps(6002, 4000, 6000, 4000), "wider than the frame");
        assertNull(ImageCanvas.steps(5999, 4000, 6000, 4000), "an odd width");
        assertNull(ImageCanvas.steps(0, 0, 6000, 4000));
    }

    @Test void addressesAreDecimalOrHexIn30Bits() {
        assertEquals(1234, ImageCanvas.address("1234"));
        assertEquals(0x1000, ImageCanvas.address("0x1000"));
        assertEquals(0x1000, ImageCanvas.address(String.valueOf(0xC0001000L)), "the same memory through another window");
        assertEquals(-1, ImageCanvas.address("n/a"));
        assertEquals(-1, ImageCanvas.address(null));
    }

    @Test void separateAllocationsShareNoByte() {
        assertTrue(ImageCanvas.separate("1000", 100, "1100", 50));
        assertTrue(ImageCanvas.separate("1100", 50, "1000", 100));
        assertFalse(ImageCanvas.separate("1000", 101, "1100", 50), "one byte in common");
        assertFalse(ImageCanvas.separate("1000", 100, String.valueOf(0x40000000L + 1050), 10), "an alias of the same memory");
        assertFalse(ImageCanvas.separate("x", 100, "1100", 50), "unsure");
        assertFalse(ImageCanvas.separate("1000", 0, "1100", 50));
    }

    @Test void geometryReadsTheProperties() {
        assertArrayEquals(new int[] { 6000, 4000, 6016, 4000, 0, 0, 0, 48128000 },
                ImageCanvas.geometry(new String[] { "6000", "4000", "6016", "4000", "0", "0", "0", "48128000" }));
        assertNull(ImageCanvas.geometry(new String[] { "6000" }));
        assertNull(ImageCanvas.geometry(new String[] { "6000", "4000", "6016", "4000", "0", "0", "0", "big" }));
        assertNull(ImageCanvas.geometry(new String[] { "6000", "4000", "6016", "4000", "0", "0", null, "1" }));
    }
}
