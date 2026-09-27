package com.voxivoid.recipelab;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The app menu, the developer menu and the sample run: the rows, About, the key logger, the settle delay, the progress lines and the manifest. */
class DevToolsTest {

    // ---- menu rows
    @Test void everyRowHasALabelAndADetailLine() {
        for (int r = 0; r < DevTools.ROWS; r++) {
            for (boolean snapshotTaken : new boolean[] { false, true }) {
                assertFalse(DevTools.rowLabel(r, snapshotTaken, DevTools.SETTLE_DEFAULT).isEmpty(), "row " + r);
                assertFalse(DevTools.rowDetail(r, snapshotTaken).isEmpty(), "row " + r);
            }
        }
    }

    @Test void theSnapshotRowSaysWhichHalfOfTheToolItWillRun() {
        assertEquals("Settings snapshot", DevTools.rowLabel(DevTools.ROW_SNAPSHOT, false, 0));
        assertEquals("Settings diff", DevTools.rowLabel(DevTools.ROW_SNAPSHOT, true, 0), "a snapshot is on disk, so the next press diffs against it");
    }

    @Test void theReadOnlyRowCountsTheSlotsItWillTest() {
        assertEquals("Read-only check — 26 slots", DevTools.rowLabel(DevTools.ROW_LOCKS, false, 0));
        assertEquals(Params.allSlots().size(), 26, "the label counts the slots, so the slots are what it must count");
    }

    @Test void theSampleRowNamesTheWholeTable() {
        assertEquals("Shoot samples — 77 recipes", DevTools.rowLabel(DevTools.ROW_SAMPLES, false, 0));
        assertEquals(77, Recipes.ALL.length, "the label counts the table, so the table is what it must count");
    }

    @Test void theDelayRowShowsTheChosenDelay() {
        assertEquals("Settle delay — 0.8 s", DevTools.rowLabel(DevTools.ROW_SETTLE, false, 0));
        assertEquals("Settle delay — 1.2 s", DevTools.rowLabel(DevTools.ROW_SETTLE, false, 1));
    }

    @Test void oneTurnOfTheMenuVisitsEveryRowItDefines() {
        // ROWS is what the menu can reach: a row defined past it is dead, and nothing else would say so
        Set<Integer> visited = new HashSet<Integer>();
        int r = DevTools.ROW_SNAPSHOT;
        for (int i = 0; i < DevTools.ROWS; i++) { visited.add(r); r = DevTools.nextRow(r, +1); }
        assertEquals(new HashSet<Integer>(Arrays.asList(DevTools.ROW_SNAPSHOT, DevTools.ROW_LOCKS, DevTools.ROW_SAMPLES, DevTools.ROW_SETTLE, DevTools.ROW_KEYS)), visited);
        assertEquals(DevTools.ROW_SNAPSHOT, r, "and comes back to the first row");
    }

    @Test void rowsWrapInBothDirections() {
        assertEquals(1, DevTools.nextRow(0, +1));
        assertEquals(0, DevTools.nextRow(DevTools.ROWS - 1, +1));
        assertEquals(DevTools.ROWS - 1, DevTools.nextRow(0, -1));
    }

    @Test void theKeyLoggerRowSaysHowToLeaveIt() {
        assertEquals("Key logger", DevTools.rowLabel(DevTools.ROW_KEYS, false, 0));
        assertTrue(DevTools.rowDetail(DevTools.ROW_KEYS, false).contains("hold MENU"), "every other key is logged, so the way out has to be said");
    }

    // ---- the app menu
    @Test void theAppMenuHasEveryFunctionThatHasNoKeyOnSomeBodies() {
        assertEquals(DevTools.APP_ROWS, DevTools.rows(DevTools.LEVEL_APP));
        assertEquals(DevTools.ROWS, DevTools.rows(DevTools.LEVEL_DEV));
        assertEquals("Browse recipes", DevTools.appLabel(DevTools.APP_BROWSE, Params.OV_FULL));
        assertEquals("Factory settings", DevTools.appLabel(DevTools.APP_FACTORY, Params.OV_FULL));
        assertEquals("Controls", DevTools.appLabel(DevTools.APP_CONTROLS, Params.OV_FULL));
        assertEquals("About", DevTools.appLabel(DevTools.APP_ABOUT, Params.OV_FULL));
        assertTrue(DevTools.appLabel(DevTools.APP_DEV, Params.OV_FULL).startsWith("Developer"));
        assertEquals(DevTools.APP_BROWSE, 0, "the menu opens on Browse, so a body without Fn is one press from the list");
    }

    @Test void everyAppRowHasALabelAndADetailLineInEveryPanelState() {
        for (int r = 0; r < DevTools.APP_ROWS; r++) {
            for (int ov : new int[] { Params.OV_FULL, Params.OV_PILL, Params.OV_HIDDEN }) {
                assertFalse(DevTools.appLabel(r, ov).startsWith("?"), "row " + r);
                assertFalse(DevTools.appDetail(r, ov).isEmpty(), "row " + r);
            }
        }
    }

    @Test void thePanelRowSaysWhichWayItWillGo() {
        assertEquals("Hide panel", DevTools.appLabel(DevTools.APP_PANEL, Params.OV_FULL));
        assertEquals("Hide panel", DevTools.appLabel(DevTools.APP_PANEL, Params.OV_PILL), "the pill hides the rest of the way");
        assertEquals("Show panel", DevTools.appLabel(DevTools.APP_PANEL, Params.OV_HIDDEN));
    }

    @Test void oneTurnOfTheAppMenuVisitsEveryRowAndWraps() {
        Set<Integer> visited = new HashSet<Integer>();
        int r = DevTools.APP_BROWSE;
        for (int i = 0; i < DevTools.APP_ROWS; i++) { visited.add(r); r = DevTools.nextRow(DevTools.LEVEL_APP, r, +1); }
        assertEquals(DevTools.APP_ROWS, visited.size());
        assertEquals(DevTools.APP_BROWSE, r);
        assertEquals(DevTools.APP_DEV, DevTools.nextRow(DevTools.LEVEL_APP, DevTools.APP_BROWSE, -1));
    }

    // ---- About
    @Test void aboutShowsTheVersionItIsGivenAndSaysUnknownForWhatTheCameraWouldNotTell() {
        String[][] a = DevTools.about("9.8.7", null, "", "has Fn");
        assertEquals("9.8.7", a[0][1]);
        assertEquals("unknown", a[1][1], "no model from the camera");
        assertEquals("unknown", a[2][1], "an empty platform string");
        assertEquals("has Fn", a[3][1]);
        for (String[] line : a) assertEquals(2, line.length);
    }

    @Test void theKeysLineSortsPresentAbsentAndUnknown() {
        int[] scans = { Keys.K_FN, Keys.K_AEL, Keys.K_C1, Keys.K_ZOOM_T };
        assertEquals("has FN C1  ·  lacks AEL  ·  unknown ZOOM_T", DevTools.keysFound(scans, new Boolean[] { true, false, true, null }));
        assertEquals("has FN AEL", DevTools.keysFound(new int[] { Keys.K_FN, Keys.K_AEL }, new Boolean[] { true, true }));
        assertEquals("the camera would not say", DevTools.keysFound(scans, new Boolean[4]), "a probe that failed says so, not an empty line");
    }

    // ---- the key logger
    @Test void aLogLineCarriesTheScanCodeFirst() {
        String[] down = DevTools.logLine(true, 595, 0, 1103);
        assertEquals("down 595", down[0]);
        assertEquals("DELETE  ·  repeat 0  ·  logic 1103", down[1]);
        String[] up = DevTools.logLine(false, 9999, 2, null);
        assertTrue(up[0].startsWith("up") && up[0].endsWith("9999"), up[0]);
        assertEquals("?  ·  repeat 2", up[1], "an unknown code and no logic code");
    }

    @Test void theLoggerTitleNamesTheBody() {
        assertEquals("KEY LOGGER  ·  ILCE-5100  ·  2.7", DevTools.logTitle("ILCE-5100", "2.7"));
        assertEquals("KEY LOGGER  ·  unknown  ·  unknown", DevTools.logTitle(null, null));
    }

    // ---- the settle delay
    @Test void delaysAreOrderedAndLabelledToOneDecimal() {
        for (int i = 1; i < DevTools.SETTLE_MS.length; i++) assertTrue(DevTools.SETTLE_MS[i] > DevTools.SETTLE_MS[i - 1], "delay " + i);
        assertEquals("2.0 s", DevTools.settleLabel(2));
        assertEquals("5.0 s", DevTools.settleLabel(DevTools.SETTLE_MS.length - 1));
    }

    @Test void aStoredDelayFromAnotherBuildFallsBackToTheDefault() {
        assertEquals(DevTools.SETTLE_DEFAULT, DevTools.clampSettle(-1));
        assertEquals(DevTools.SETTLE_DEFAULT, DevTools.clampSettle(DevTools.SETTLE_MS.length));
        assertEquals(2, DevTools.clampSettle(2));
    }

    @Test void theDelayCyclesThroughTheTable() {
        int idx = 0;
        for (int i = 0; i < DevTools.SETTLE_MS.length; i++) idx = DevTools.nextSettle(idx, +1);
        assertEquals(0, idx, "one turn through every delay comes back to the first");
        assertEquals(DevTools.SETTLE_MS.length - 1, DevTools.nextSettle(0, -1));
        assertEquals(DevTools.SETTLE_DEFAULT + 1, DevTools.nextSettle(DevTools.SETTLE_MS.length, +1), "an out-of-table index falls back to the default before it steps");
    }

    // ---- what the run says while it walks the table
    @Test void progressCountsFramesFromOneAndNamesTheWayOut() {
        String p = DevTools.progress(1, 77, "FACTORY (ST)");
        assertTrue(p.startsWith("Shooting 1 / 77"), p);
        assertTrue(p.contains("FACTORY (ST)"), p);
        assertTrue(p.contains("MENU"), p);
    }

    @Test void theEndOfARunSaysHowManyFramesAndWhereTheListIs() {
        assertTrue(DevTools.doneMessage(77, 77).contains("77 of 77"), DevTools.doneMessage(77, 77));
        assertTrue(DevTools.doneMessage(77, 77).contains(DevTools.MANIFEST));
        assertTrue(DevTools.stoppedMessage(12, 77).contains("12 of 77"), DevTools.stoppedMessage(12, 77));
        assertTrue(DevTools.stoppedMessage(0, 77).contains("before the first frame"), "nothing was shot, so there is nothing to point at");
        assertFalse(DevTools.stoppedMessage(0, 77).contains(DevTools.MANIFEST));
        assertTrue(DevTools.shootFailed(13, 12, "timeout").contains("frame 13"), DevTools.shootFailed(13, 12, "timeout"));
    }

    // ---- the manifest
    @Test void theHeaderRecordsTheFrameCountTheOrderAndTheDelay() {
        String h = DevTools.manifestHeader(77, 1200);
        assertTrue(h.startsWith("#"), h);
        assertTrue(h.contains("77 frames in recipe order"), h);
        assertTrue(h.contains("settle 1200 ms"), h);
        assertTrue(h.contains("frame" + DevTools.SEP + "recipe"), "the header names the columns of the lines below it");
    }

    @Test void aLineIsFrameRecipeBrandValues() {
        assertEquals("01" + DevTools.SEP + "FACTORY (ST)" + DevTools.SEP + "Sony" + DevTools.SEP + Recipes.ALL[0].summary(),
                DevTools.manifestLine(1, 0));
        assertTrue(DevTools.manifestLine(10, 9).startsWith("10" + DevTools.SEP), "frames past nine are not padded further");
    }

    @Test void everyRecipeMakesOneParsableLineAndNoTwoNameTheSameRecipe() {
        Set<String> names = new HashSet<String>();
        for (int i = 0; i < Recipes.ALL.length; i++) {
            String[] f = DevTools.manifestLine(i + 1, i).split("\\" + DevTools.SEP);
            assertEquals(4, f.length, "line " + i + " must split into exactly the four columns");
            assertEquals(i + 1, Integer.parseInt(f[0]), "the frame number is the position in the run");
            assertEquals(Recipes.ALL[i].name, f[1]);
            assertEquals(Recipes.GROUPS[Recipes.ALL[i].group], f[2]);
            assertTrue(names.add(f[1]), f[1] + " is listed twice");
        }
        assertEquals(Recipes.ALL.length, names.size());
    }
}
