package com.voxivoid.recipelab;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/**
 * The key probe off the camera: every Sony call fails (the test doubles in test/com/sony/scalar/sysutil throw for
 * anything not set up, as a missing class or firmware call would), so every answer is "unknown" and nothing throws.
 */
class KeyProbeTest {

    @Test void withoutTheCameraEveryKeyIsUnknown() {
        for (int k : KeyProbe.REPORTED) assertNull(KeyProbe.has(k), Keys.name(k));
        assertNull(KeyProbe.has(Keys.K_FN), "asked twice, from the cache");
    }

    @Test void withoutTheCameraNoKeyIsKnownToBeDown() { assertNull(KeyProbe.isDown(Keys.K_DELETE)); }

    @Test void withoutTheCameraThereIsNoModelAndNoLogicCode() {
        assertNull(KeyProbe.prop("model.name"));
        assertNull(KeyProbe.logic(Keys.K_FN, 0));
    }

    @Test void withoutTheCameraTheCapsAreUnknownSoOnlyUniversalKeysAreNamed() {
        Keys.Caps caps = KeyProbe.caps();
        assertNull(caps.fn);
        assertNull(caps.ael);
        assertFalse(caps.hasFn());
        assertFalse(caps.hasAel());
    }

    @Test void theReportedKeysIncludeEveryShortcutAndTheUnboundOnes() {
        java.util.List<Integer> r = new java.util.ArrayList<Integer>();
        for (int k : KeyProbe.REPORTED) r.add(k);
        for (int k : new int[] { Keys.K_FN, Keys.K_AEL, Keys.K_C1, Keys.K_DISP, Keys.K_DELETE, Keys.K_SK2 }) assertTrue(r.contains(k), Keys.name(k));
    }
}
