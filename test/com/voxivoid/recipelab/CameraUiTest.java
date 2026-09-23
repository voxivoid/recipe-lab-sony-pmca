package com.voxivoid.recipelab;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CameraUiTest {
    @Test void modelDetectionDoesNotTreatOtherSonyBodiesAsA5100() {
        assertTrue(CameraUi.isA5100("ILCE-5100"));
        assertTrue(CameraUi.isA5100(" ilce-5100 "));
        assertTrue(CameraUi.isA5100("A5100"));
        for (String model : new String[] { null, "", "ScalarA", "ILCE-6000", "ILCE-5000", "ILCE-51000" })
            assertFalse(CameraUi.isA5100(model));
    }
    @Test void onlyA5100StartsWithPillAndMapsMovieToAel() {
        assertEquals(Params.OV_PILL, CameraUi.initialOverlay(true));
        assertEquals(Params.OV_FULL, CameraUi.initialOverlay(false));
        assertEquals(532, CameraUi.key(true, 515));
        assertEquals(515, CameraUi.key(false, 515));
        assertEquals(207, CameraUi.key(true, 207));
        for (int key : new int[] { 103, 108, 105, 106, 232, 514, 595, 520, 532, 608, 622, 516, 518, 522, 523 })
            assertEquals(key, CameraUi.key(true, key));
    }
    @Test void movieReachesFullPanelOnFirstPressAfterLaunch() {
        int overlay = CameraUi.initialOverlay(true);
        assertEquals(Params.OV_FULL, overlay = CameraUi.nextOverlay(true, overlay));
        assertEquals(Params.OV_HIDDEN, overlay = CameraUi.nextOverlay(true, overlay));
        assertEquals(Params.OV_PILL, CameraUi.nextOverlay(true, overlay));
        assertEquals(Params.OV_PILL, CameraUi.nextOverlay(false, Params.OV_FULL));
        assertEquals(Params.OV_HIDDEN, CameraUi.nextOverlay(false, Params.OV_PILL));
        assertEquals(Params.OV_FULL, CameraUi.nextOverlay(false, Params.OV_HIDDEN));
    }
    @Test void verticalNavigationReachesEveryEntryInBothDirections() {
        assertEquals(1, CameraUi.nextTarget(0, 1));
        assertEquals(2, CameraUi.nextTarget(1, 1));
        assertEquals(0, CameraUi.nextTarget(2, 1));
        assertEquals(2, CameraUi.nextTarget(0, -1));
        assertEquals(1, CameraUi.nextTarget(2, -1));
        assertEquals(0, CameraUi.nextTarget(1, -1));
    }
    @Test void touchEntryCannotBypassModalDialogsOrSampleRun() {
        assertTrue(CameraUi.canOpenBrowser(true, Params.OV_FULL, false, false, false));
        assertFalse(CameraUi.canOpenBrowser(false, Params.OV_FULL, false, false, false));
        for (int overlay : new int[] { Params.OV_PILL, Params.OV_HIDDEN, Params.OV_BROWSER })
            assertFalse(CameraUi.canOpenBrowser(true, overlay, false, false, false));
        assertFalse(CameraUi.canOpenBrowser(true, Params.OV_FULL, true, false, false));
        assertFalse(CameraUi.canOpenBrowser(true, Params.OV_FULL, false, true, false));
        assertFalse(CameraUi.canOpenBrowser(true, Params.OV_FULL, false, false, true));
    }
}
