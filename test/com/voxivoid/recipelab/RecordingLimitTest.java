package com.voxivoid.recipelab;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class RecordingLimitTest {
    @Test void recognisesSonysStandardAndLongValues() {
        assertEquals(RecordingLimit.STANDARD, RecordingLimit.state(0, 29, 50));
        assertEquals(RecordingLimit.LONG, RecordingLimit.state(13, 1, 0));
        assertEquals(RecordingLimit.OTHER, RecordingLimit.state(1, 2, 3));
    }

    @Test void suppliesTheSameValuesAsOpenMemoriesTweak() {
        assertArrayEquals(new int[] { 13, 1, 0 }, RecordingLimit.value(true));
        assertArrayEquals(new int[] { 0, 29, 50 }, RecordingLimit.value(false));
        assertArrayEquals(new int[] { 0x003c0373, 0x003c0374, 0x003c0375 }, RecordingLimit.ids());
        assertArrayEquals(new byte[] { (byte) 0xff, 0x7f }, RecordingLimit.value4k(true));
        assertArrayEquals(new byte[] { 0x2c, 0x01 }, RecordingLimit.value4k(false));
    }

    @Test void theSeparateFiveMinute4kLimitIsOnlyUsedOnTheTwoAppCapableRx100BodiesThatHaveIt() {
        assertTrue(RecordingLimit.hasShort4kLimit("DSC-RX100M4"));
        assertTrue(RecordingLimit.hasShort4kLimit("DSC-RX100M5"));
        assertFalse(RecordingLimit.hasShort4kLimit("ILCE-6300"));
        assertEquals(RecordingLimit.STANDARD, RecordingLimit.state4k(new byte[] { 0x2c, 0x01 }));
        assertEquals(RecordingLimit.LONG, RecordingLimit.state4k(new byte[] { (byte) 0xff, 0x7f }));
        assertEquals(RecordingLimit.UNKNOWN, RecordingLimit.state4k(new byte[] { 0 }));
    }
}
