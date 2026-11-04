package com.voxivoid.recipelab;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Hold MENU → Display: its rows (the panel first), defaults, ON / OFF rules, Restore defaults, the menu's routing and the grid's rectangle. */
class DisplayOptionsTest {
    @AfterEach void english() { Lang.use(Lang.EN); }

    /** the options by row: the panel's entry first, never read */
    private static boolean[] on(boolean histogram, boolean grid, boolean counter) { return new boolean[] { false, histogram, grid, counter }; }

    @Test void aFreshInstallLooksAsBefore() {
        boolean[] d = DisplayOptions.defaults();
        assertArrayEquals(on(false, false, true), d, "histogram and grid off, the counter on");
        assertEquals(DisplayOptions.KEYS.length, d.length, "one default per row with a key");
        assertEquals(DisplayOptions.DEFAULTS, DisplayOptions.KEYS.length, "every row before Restore defaults has its place in KEYS");
        d[DisplayOptions.HISTOGRAM] = true;
        assertFalse(DisplayOptions.defaults()[DisplayOptions.HISTOGRAM], "a caller cannot change the defaults");
    }

    @Test void eachOptionHasItsOwnPreferenceKeyAndThePanelNone() {
        assertNull(DisplayOptions.KEYS[DisplayOptions.PANEL], "the panel is a state the activity keeps, not an ON / OFF option");
        assertEquals(Arrays.asList(null, "display.histogram", "display.grid", "display.counter"), Arrays.asList(DisplayOptions.KEYS));
    }

    @Test void leftIsOffRightIsOnAndRepeatingChangesNothing() {
        boolean[] o = DisplayOptions.defaults();
        assertTrue(DisplayOptions.set(o, DisplayOptions.GRID, true));
        assertTrue(DisplayOptions.set(o, DisplayOptions.GRID, true));
        assertArrayEquals(on(false, true, true), o, "only the grid changed, and it stays on");
        assertTrue(DisplayOptions.set(o, DisplayOptions.COUNTER, false));
        assertTrue(DisplayOptions.set(o, DisplayOptions.COUNTER, false));
        assertArrayEquals(on(false, true, false), o);
    }

    @Test void centreTogglesOneOptionAndLeavesTheOthers() {
        for (int row = DisplayOptions.HISTOGRAM; row < DisplayOptions.DEFAULTS; row++) {
            boolean[] o = DisplayOptions.defaults(), want = DisplayOptions.defaults();
            want[row] = !want[row];
            assertTrue(DisplayOptions.toggle(o, row));
            assertArrayEquals(want, o, "row " + row);
            assertTrue(DisplayOptions.toggle(o, row));
            assertArrayEquals(DisplayOptions.defaults(), o, "and back, row " + row);
        }
    }

    @Test void thePanelAndRestoreDefaultsAreNotOnOffOptions() {
        boolean[] o = on(true, true, false);
        for (int row : new int[] { DisplayOptions.PANEL, DisplayOptions.DEFAULTS, -1 }) {
            assertFalse(DisplayOptions.hasValue(row), "row " + row);
            assertFalse(DisplayOptions.set(o, row, true), "left / right set nothing, row " + row);
            assertFalse(DisplayOptions.toggle(o, row), "row " + row);
        }
        assertArrayEquals(on(true, true, false), o, "nothing changed");
        assertNull(DisplayOptions.value(DisplayOptions.DEFAULTS, o, Params.OV_FULL), "an action, not a value");
        assertTrue(DisplayOptions.hasValue(DisplayOptions.HISTOGRAM));
        assertTrue(DisplayOptions.hasValue(DisplayOptions.COUNTER));
    }

    @Test void thePanelRowShowsThePanelState() {
        boolean[] o = DisplayOptions.defaults();
        assertEquals("Full", DisplayOptions.value(DisplayOptions.PANEL, o, Params.OV_FULL));
        assertEquals("No keys", DisplayOptions.value(DisplayOptions.PANEL, o, Params.OV_QUIET));
        assertEquals("Label", DisplayOptions.value(DisplayOptions.PANEL, o, Params.OV_PILL));
        assertEquals("Hidden", DisplayOptions.value(DisplayOptions.PANEL, o, Params.OV_HIDDEN));
        assertEquals("Panel visibility", DisplayOptions.label(DisplayOptions.PANEL));
        assertEquals(DisplayOptions.PANEL, 0, "the submenu opens on it: it is the one most changed");
    }

    @Test void restoreDefaultsPutsBackTheOptions() {
        boolean[] o = on(true, true, false);
        DisplayOptions.restore(o);
        assertArrayEquals(DisplayOptions.defaults(), o);
    }

    @Test void everyRowHasALabelADetailAndAValueWhereItShould() {
        for (int lang = 0; lang < Lang.COUNT; lang++) {
            Lang.use(lang);
            Set<String> labels = new HashSet<String>();
            for (int row = 0; row < DisplayOptions.ROWS; row++) {
                assertFalse(DisplayOptions.label(row).isEmpty(), Lang.CODES[lang] + " row " + row);
                assertFalse(DisplayOptions.detail(row).isEmpty(), Lang.CODES[lang] + " row " + row);
                labels.add(DisplayOptions.label(row));
            }
            assertEquals(DisplayOptions.ROWS, labels.size(), Lang.CODES[lang] + ": every row is named apart");
            assertFalse(DisplayOptions.title().isEmpty());
            boolean[] o = DisplayOptions.defaults();
            assertEquals(Lang.t("value_off"), DisplayOptions.value(DisplayOptions.HISTOGRAM, o, Params.OV_FULL));
            assertEquals(Lang.t("value_on"), DisplayOptions.value(DisplayOptions.COUNTER, o, Params.OV_FULL));
            assertEquals(DevTools.panelLabel(Params.OV_PILL), DisplayOptions.value(DisplayOptions.PANEL, o, Params.OV_PILL));
            assertNotEquals(Lang.t("value_on"), Lang.t("value_off"), Lang.CODES[lang]);
        }
    }

    @Test void theRowsSayWhatTheyAre() {
        Lang.use(Lang.EN);
        assertEquals("DISPLAY", DisplayOptions.title());
        assertEquals("Live histogram", DisplayOptions.label(DisplayOptions.HISTOGRAM));
        assertEquals("Composition grid", DisplayOptions.label(DisplayOptions.GRID));
        assertEquals("Recipe counter", DisplayOptions.label(DisplayOptions.COUNTER));
        assertEquals("Restore defaults", DisplayOptions.label(DisplayOptions.DEFAULTS));
        assertEquals("Full panel, histogram and grid off, counter on", DisplayOptions.detail(DisplayOptions.DEFAULTS));
        assertEquals("Rule-of-thirds lines over the image", DisplayOptions.detail(DisplayOptions.GRID));
        assertEquals("on", DisplayOptions.value(DisplayOptions.GRID, on(false, true, false), Params.OV_FULL));
        assertEquals("off", DisplayOptions.value(DisplayOptions.GRID, on(true, false, true), Params.OV_FULL));
    }

    // ---- the app menu routes to it
    @Test void theAppMenuHasOneDisplayRow() {
        assertEquals("Display  >", DevTools.appLabel(DevTools.APP_DISPLAY));
        assertEquals("Panel, histogram, grid and recipe counter", DevTools.appDetail(DevTools.APP_DISPLAY));
        assertNull(DevTools.appValue(DevTools.APP_DISPLAY, Lang.AUTO, false), "it opens a submenu, it has no value");
        assertEquals(DevTools.IC_PANEL, DevTools.icons(DevTools.LEVEL_APP)[DevTools.APP_DISPLAY]);
        for (int r = 0; r < DevTools.APP_ROWS; r++)
            assertNotEquals("Panel visibility", DevTools.appLabel(r), "the panel is a row of Display, not of the app menu");
    }

    @Test void theSubmenuIsItsOwnLevelNotTheDeveloperMenu() {
        assertEquals(3, new HashSet<Integer>(Arrays.asList(DevTools.LEVEL_APP, DevTools.LEVEL_DEV, DevTools.LEVEL_DISPLAY)).size());
        assertEquals(DisplayOptions.ROWS, DevTools.rows(DevTools.LEVEL_DISPLAY));
        assertEquals(DevTools.ROWS, DevTools.rows(DevTools.LEVEL_DEV));
        assertEquals(DevTools.APP_ROWS, DevTools.rows(DevTools.LEVEL_APP));
        assertNull(DevTools.icons(DevTools.LEVEL_DISPLAY), "its rows are drawn without icons");
        Set<Integer> visited = new HashSet<Integer>();
        int r = DisplayOptions.PANEL;
        for (int i = 0; i < DisplayOptions.ROWS; i++) { visited.add(r); r = DevTools.nextRow(DevTools.LEVEL_DISPLAY, r, +1); }
        assertEquals(DisplayOptions.ROWS, visited.size(), "one turn reaches every row, Restore defaults included");
        assertEquals(DisplayOptions.PANEL, r);
        assertEquals(DisplayOptions.DEFAULTS, DevTools.nextRow(DevTools.LEVEL_DISPLAY, DisplayOptions.PANEL, -1));
    }

    // ---- where the grid goes
    @Test void theGridFollowsThePreviewImageNotTheBars() {
        assertArrayEquals(new int[] { 0, 0, 640, 480 }, DisplayOptions.contentRect(640, 480, 640, 480), "same shape: the whole view");
        assertArrayEquals(new int[] { 0, 0, 640, 480 }, DisplayOptions.contentRect(640, 480, 1280, 960), "the size does not matter, the shape does");
        assertArrayEquals(new int[] { 0, 27, 640, 453 }, DisplayOptions.contentRect(640, 480, 3, 2), "a 3:2 image on a 4:3 view: bars top and bottom");
        assertArrayEquals(new int[] { 0, 60, 640, 420 }, DisplayOptions.contentRect(640, 480, 16, 9), "16:9 on 4:3");
        assertArrayEquals(new int[] { 80, 0, 560, 480 }, DisplayOptions.contentRect(640, 480, 1, 1), "a square image: bars left and right");
        assertArrayEquals(new int[] { 102, 0, 742, 480 }, DisplayOptions.contentRect(845, 480, 4, 3), "4:3 on a wider view");
    }

    @Test void anUnknownPreviewSizeIsTheWholeView() {
        assertArrayEquals(new int[] { 0, 0, 640, 480 }, DisplayOptions.contentRect(640, 480, 0, 0));
        assertArrayEquals(new int[] { 0, 0, 640, 480 }, DisplayOptions.contentRect(640, 480, 640, 0));
        assertArrayEquals(new int[] { 0, 0, 640, 480 }, DisplayOptions.contentRect(640, 480, 0, 480));
        assertArrayEquals(new int[] { 0, 0, 640, 480 }, DisplayOptions.contentRect(640, 480, -3, 2));
        assertArrayEquals(new int[] { 0, 0, 640, 480 }, DisplayOptions.contentRect(640, 480, 3, -2));
    }

    @Test void largeSizesDoNotOverflow() {
        assertArrayEquals(new int[] { 0, 0, 100000, 75000 }, DisplayOptions.contentRect(100000, 75000, 40000, 30000));
    }
}
