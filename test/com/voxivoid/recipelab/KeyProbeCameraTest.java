package com.voxivoid.recipelab;

import static org.junit.jupiter.api.Assertions.*;

import com.sony.scalar.sysutil.KeyStatus;
import com.sony.scalar.sysutil.ScalarInput;
import com.sony.scalar.sysutil.ScalarProperties;
import org.junit.jupiter.api.Test;

/**
 * The key probe against test doubles of Sony's classes (test/com/sony/scalar/sysutil), shaped like the
 * OpenMemories-Framework stubs: proves the reflection finds the calls and fields the camera has, and reads them the way
 * Sony's own framework does. Scan codes here are ones no other test asks about, since {@link KeyProbe#has} caches.
 */
class KeyProbeCameraTest {

    @Test void onlyAValidKeyIsPresent() {
        ScalarInput.setKey(700, KeyStatus.VALID, KeyStatus.STATUS_OFF);
        ScalarInput.setKey(701, KeyStatus.INVALID, KeyStatus.STATUS_INVALID);
        ScalarInput.setKey(702, 2, KeyStatus.STATUS_OFF);
        assertEquals(Boolean.TRUE, KeyProbe.has(700));
        assertEquals(Boolean.FALSE, KeyProbe.has(701));
        assertEquals(Boolean.FALSE, KeyProbe.has(702), "only VALID (1) means the body has it");
    }

    @Test void presenceIsAskedOncePerKey() {
        ScalarInput.setKey(703, KeyStatus.VALID, KeyStatus.STATUS_OFF);
        assertEquals(Boolean.TRUE, KeyProbe.has(703));
        ScalarInput.setKey(703, KeyStatus.INVALID, KeyStatus.STATUS_OFF);
        assertEquals(Boolean.TRUE, KeyProbe.has(703), "the answer is cached: a body does not grow or lose buttons");
    }

    @Test void aKeyIsDownOnlyWhenValidAndOn() {
        ScalarInput.setKey(704, KeyStatus.VALID, KeyStatus.STATUS_ON);
        assertEquals(Boolean.TRUE, KeyProbe.isDown(704));
        ScalarInput.setKey(704, KeyStatus.VALID, KeyStatus.STATUS_OFF);
        assertEquals(Boolean.FALSE, KeyProbe.isDown(704), "asked live, not cached: the trash hold needs the current state");
        ScalarInput.setKey(704, KeyStatus.VALID, KeyStatus.STATUS_INVALID);
        assertEquals(Boolean.FALSE, KeyProbe.isDown(704), "STATUS_INVALID is not a press");
        ScalarInput.setKey(705, KeyStatus.INVALID, KeyStatus.STATUS_ON);
        assertNull(KeyProbe.isDown(705), "a key the body does not have is neither down nor up");
    }

    @Test void theLogicCodeIsAskedForAPress() {
        ScalarInput.setLogic(706, 1103);
        assertEquals(Integer.valueOf(1103), KeyProbe.logic(706, 4));
    }

    @Test void propertiesComeBackAsStringsAndEmptyMeansUnknown() {
        ScalarProperties.set("test.model", "ILCE-5100");
        ScalarProperties.set("test.empty", "");
        assertEquals("ILCE-5100", KeyProbe.prop("test.model"));
        assertNull(KeyProbe.prop("test.empty"));
        assertNull(KeyProbe.prop("test.unset"), "a property the camera throws for");
    }
}
