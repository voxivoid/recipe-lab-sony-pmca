package com.voxivoid.recipelab;

import static com.voxivoid.recipelab.FocusOverlayModel.*;
import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The autofocus boxes: which reported areas are drawn, in which state, where on screen, and when they go. */
class FocusOverlayModelTest {
    private static Area area(int index, int l, int t, int r, int b) { return new Area(index, true, l, t, r, b); }

    private static final Area CENTRE = area(7, -100, -100, 100, 100), LEFT = area(3, -900, -50, -700, 50);
    private static final Area[] CATALOG = { CENTRE, LEFT };

    private static FocusOverlayModel pressed() {
        FocusOverlayModel m = new FocusOverlayModel();
        m.open(); m.areas(m.generation(), CATALOG); m.press();
        return m;
    }

    // ---- matching the numbers a result names to the areas
    @Test void anAreaIsFoundByItsIndexWhateverItsPosition() {
        assertEquals(Arrays.asList(LEFT), match(CATALOG, new int[] { 3 }), "index 3 is the second entry, not the fourth");
        assertEquals(Arrays.asList(CENTRE), match(CATALOG, new int[] { 7 }));
    }

    @Test void aNumberNoIndexHasIsTakenAsAPosition() {
        assertEquals(Arrays.asList(LEFT), match(CATALOG, new int[] { 1 }));
        assertEquals(Arrays.asList(CENTRE), match(CATALOG, new int[] { 0 }));
    }

    @Test void anIndexMatchWinsOverThePosition() {
        Area a = area(1, -10, -10, 10, 10), b = area(0, 20, 20, 40, 40);
        assertEquals(Arrays.asList(b), match(new Area[] { a, b }, new int[] { 0 }), "0 is b's index, though a is first");
    }

    @Test void anIndexMatchThatCannotBeDrawnDoesNotFallBackToThePosition() {
        Area off = new Area(0, false, -10, -10, 10, 10), other = area(5, 20, 20, 40, 40);
        assertTrue(match(new Area[] { other, off }, new int[] { 0 }).isEmpty(), "0 names the disabled area, not position 0");
    }

    @Test void unknownNegativeAndMissingNumbersDrawNothing() {
        assertTrue(match(CATALOG, new int[] { 2 }).isEmpty(), "no index 2, and only two positions");
        assertTrue(match(CATALOG, new int[] { -1 }).isEmpty());
        assertTrue(match(CATALOG, new int[0]).isEmpty());
        assertTrue(match(CATALOG, null).isEmpty());
        assertTrue(match(null, new int[] { 7 }).isEmpty());
        assertTrue(match(new Area[] { null }, new int[] { 0 }).isEmpty(), "a hole in the list is skipped");
    }

    @Test void anAreaNamedTwiceIsDrawnOnce() {
        assertEquals(Arrays.asList(CENTRE, LEFT), match(CATALOG, new int[] { 7, 7, 3, 1 }), "7 twice, and 1 is LEFT again by position");
    }

    @Test void disabledInvertedEmptyAndOutOfRangeAreasAreNotDrawn() {
        assertFalse(new Area(0, false, -10, -10, 10, 10).drawable());
        assertFalse(area(0, 10, -10, -10, 10).drawable(), "inverted across");
        assertFalse(area(0, -10, 10, 10, -10).drawable(), "inverted down");
        assertFalse(area(0, 10, -10, 10, 10).drawable(), "no width");
        assertFalse(area(0, -10, 10, 10, 10).drawable(), "no height");
        assertFalse(area(0, -1001, -10, 10, 10).drawable());
        assertFalse(area(0, -10, -1001, 10, 10).drawable());
        assertFalse(area(0, -10, -10, 1001, 10).drawable());
        assertFalse(area(0, -10, -10, 10, 1001).drawable());
        assertTrue(area(0, -1000, -1000, 1000, 1000).drawable(), "the whole range is fine");
    }

    // ---- where the live image is, and where a box goes on it
    @Test void aPreviewOfTheViewsShapeFillsIt() {
        assertArrayEquals(new int[] { 0, 0, 640, 480 }, content(640, 480, 640, 480));
        assertArrayEquals(new int[] { 0, 0, 640, 480 }, content(640, 480, 1024, 768));
    }

    @Test void aThreeTwoImageIsPillarboxedInASixteenNineView() {
        assertArrayEquals(new int[] { 125, 0, 1350, 900 }, content(1600, 900, 1500, 1000));
    }

    @Test void aSixteenNineImageIsLetterboxedInAFourThreeView() {
        assertArrayEquals(new int[] { 0, 60, 640, 360 }, content(640, 480, 1920, 1080));
    }

    @Test void anUnknownSizePlacesNothing() {
        assertNull(content(0, 480, 640, 480));
        assertNull(content(640, 0, 640, 480));
        assertNull(content(640, 480, 0, 480));
        assertNull(content(640, 480, 640, 0));
    }

    @Test void theCornersOfTheRangeAreTheCornersOfTheImage() {
        int[] c = { 125, 0, 1350, 900 };
        assertArrayEquals(new int[] { 125, 0, 1475, 900 }, map(area(0, -1000, -1000, 1000, 1000), c));
        assertArrayEquals(new int[] { 125, 0, 193, 45 }, map(area(0, -1000, -1000, -900, -900), c), "top left");
        assertArrayEquals(new int[] { 1408, 855, 1475, 900 }, map(area(0, 900, 900, 1000, 1000), c), "bottom right");
    }

    @Test void theCentreIsTheCentreOfTheImageNotOfTheView() {
        assertArrayEquals(new int[] { 288, 216, 352, 264 }, map(CENTRE, content(640, 480, 640, 480)));
        assertArrayEquals(new int[] { 733, 405, 868, 495 }, map(CENTRE, new int[] { 125, 0, 1350, 900 }));
    }

    @Test void anOffCentreAreaLandsOffCentre() {
        int[] b = map(LEFT, content(640, 480, 640, 480));
        assertArrayEquals(new int[] { 32, 228, 96, 252 }, b, "Flexible Spot on the left stays on the left");
    }

    @Test void anAreaTooSmallToSeeOrUndrawableIsNoBox() {
        assertNull(map(area(0, 0, 0, 1, 10), new int[] { 0, 0, 640, 480 }), "rounds to nothing across");
        assertNull(map(area(0, 0, 0, 10, 1), new int[] { 0, 0, 640, 480 }), "rounds to nothing down");
        assertNull(map(new Area(0, false, -10, -10, 10, 10), new int[] { 0, 0, 640, 480 }));
        assertNull(map(null, new int[] { 0, 0, 640, 480 }));
        assertNull(map(CENTRE, null));
    }

    @Test void theBoxesFollowTheViewsSize() {
        FocusOverlayModel m = pressed();
        m.done(m.generation(), STATUS_LOCK, new int[] { 7 });
        assertArrayEquals(new int[] { 288, 216, 352, 264 }, m.boxes(640, 480, 640, 480).get(0));
        assertArrayEquals(new int[] { 576, 432, 704, 528 }, m.boxes(1280, 960, 640, 480).get(0));
        assertTrue(m.boxes(0, 0, 640, 480).isEmpty(), "not laid out yet");
        assertTrue(m.boxes(640, 480, 0, 0).isEmpty(), "no preview size: nothing placed rather than guessed");
    }

    // ---- states
    @Test void idleUntilPressedThenAcquiringWithNoBox() {
        FocusOverlayModel m = new FocusOverlayModel();
        assertEquals(IDLE, m.state());
        m.open(); m.press();
        assertEquals(ACQUIRING, m.state());
        assertTrue(m.boxes(640, 480, 640, 480).isEmpty(), "no area named yet: no centre box");
    }

    @Test void eachStatusHasItsOwnState() {
        assertEquals(IDLE, stateOf(STATUS_CLEAR));
        assertEquals(LOCKED, stateOf(STATUS_LOCK));
        assertEquals(WARNING, stateOf(STATUS_LOCK_WARN));
        assertEquals(ACQUIRING, stateOf(STATUS_WORKING));
        assertEquals(CONTINUOUS, stateOf(STATUS_CONTINUOUS));
        assertEquals(WARNING, stateOf(9), "an unknown status is never a lock");
        assertEquals(WARNING, stateOf(-1));
    }

    @Test void aLockShowsItsAreas() {
        FocusOverlayModel m = pressed();
        assertTrue(m.done(m.generation(), STATUS_LOCK, new int[] { 3 }));
        assertEquals(LOCKED, m.state());
        assertEquals(Arrays.asList(LEFT), m.shown());
        assertEquals(1, m.boxes(640, 480, 640, 480).size());
    }

    @Test void aWarningAndContinuousAreNotALock() {
        FocusOverlayModel m = pressed();
        m.done(m.generation(), STATUS_LOCK_WARN, new int[] { 7 });
        assertEquals(WARNING, m.state());
        assertEquals(1, m.shown().size());
        m.done(m.generation(), STATUS_CONTINUOUS, new int[] { 7 });
        assertEquals(CONTINUOUS, m.state());
        assertEquals(1, m.shown().size());
    }

    @Test void aClearResultRemovesTheBoxesButKeepsListening() {
        FocusOverlayModel m = pressed();
        m.done(m.generation(), STATUS_LOCK, new int[] { 7 });
        assertTrue(m.done(m.generation(), STATUS_CLEAR, new int[] { 7 }));
        assertEquals(IDLE, m.state());
        assertTrue(m.shown().isEmpty());
        assertTrue(m.boxes(640, 480, 640, 480).isEmpty());
        assertTrue(m.done(m.generation(), STATUS_LOCK, new int[] { 7 }), "S1 is still held");
        assertEquals(LOCKED, m.state());
    }

    @Test void aShownAreaTooSmallToSeeIsNoBox() {
        FocusOverlayModel m = new FocusOverlayModel();
        m.open(); m.areas(m.generation(), new Area[] { area(0, 0, 0, 1, 1) }); m.press();
        m.done(m.generation(), STATUS_LOCK, new int[] { 0 });
        assertEquals(1, m.shown().size());
        assertTrue(m.boxes(640, 480, 640, 480).isEmpty(), "never a null box");
    }

    @Test void aLockOnNumbersThatMatchNothingDrawsNothing() {
        FocusOverlayModel m = pressed();
        m.done(m.generation(), STATUS_LOCK, new int[] { 42 });
        assertTrue(m.boxes(640, 480, 640, 480).isEmpty(), "no guessed box");
    }

    @Test void aNewAfStartDropsTheLastResult() {
        FocusOverlayModel m = pressed();
        m.done(m.generation(), STATUS_LOCK, new int[] { 7 });
        assertTrue(m.started(m.generation()));
        assertEquals(ACQUIRING, m.state());
        assertTrue(m.shown().isEmpty());
    }

    @Test void aRepeatedPressKeepsTheResult() {
        FocusOverlayModel m = pressed();
        m.done(m.generation(), STATUS_LOCK, new int[] { 7 });
        m.press();
        assertEquals(LOCKED, m.state(), "S1's key repeat is not a new half-press");
        assertEquals(1, m.shown().size());
    }

    @Test void releasingHidesTheBoxesAtOnce() {
        FocusOverlayModel m = pressed();
        m.done(m.generation(), STATUS_LOCK, new int[] { 7 });
        m.clear();
        assertEquals(IDLE, m.state());
        assertTrue(m.boxes(640, 480, 640, 480).isEmpty());
    }

    @Test void aResultAfterTheReleaseDoesNotBringTheBoxBack() {
        FocusOverlayModel m = pressed();
        m.clear();
        assertFalse(m.done(m.generation(), STATUS_LOCK, new int[] { 7 }));
        assertFalse(m.started(m.generation()));
        assertEquals(IDLE, m.state());
        assertTrue(m.shown().isEmpty());
    }

    @Test void nothingIsTakenBeforeAPress() {
        FocusOverlayModel m = new FocusOverlayModel();
        m.open(); m.areas(m.generation(), CATALOG);
        assertFalse(m.done(m.generation(), STATUS_LOCK, new int[] { 7 }));
        assertEquals(IDLE, m.state());
    }

    @Test void callbacksFromAnEarlierSessionAreDropped() {
        FocusOverlayModel m = pressed();
        int old = m.generation();
        m.close();
        int now = m.open();
        assertNotEquals(old, now);
        assertEquals(now, m.generation());
        m.press();
        assertFalse(m.areas(old, CATALOG), "the old camera's catalog");
        assertFalse(m.started(old));
        assertFalse(m.done(old, STATUS_LOCK, new int[] { 7 }));
        assertEquals(ACQUIRING, m.state());
    }

    @Test void aNewSessionForgetsTheAreasAndThePress() {
        FocusOverlayModel m = pressed();
        m.done(m.generation(), STATUS_LOCK, new int[] { 7 });
        m.open();
        assertEquals(IDLE, m.state());
        m.press();
        m.done(m.generation(), STATUS_LOCK, new int[] { 7 });
        assertTrue(m.shown().isEmpty(), "the catalog went with the old camera");
    }

    @Test void theCatalogIsACopy() {
        FocusOverlayModel m = new FocusOverlayModel();
        m.open();
        Area[] mine = { CENTRE };
        assertTrue(m.areas(m.generation(), mine));
        mine[0] = LEFT;
        m.press(); m.done(m.generation(), STATUS_LOCK, new int[] { 0 });
        assertEquals(Arrays.asList(CENTRE), m.shown());
    }

    @Test void noCatalogMeansNoBoxes() {
        FocusOverlayModel m = new FocusOverlayModel();
        m.open(); m.areas(m.generation(), null); m.press();
        m.done(m.generation(), STATUS_LOCK, new int[] { 0 });
        assertTrue(m.shown().isEmpty());
    }

    @Test void theLogLineHasTheSizesTheAreaAndWhereItWent() {
        FocusOverlayModel m = pressed();
        m.done(m.generation(), STATUS_LOCK, new int[] { 7 });
        assertEquals("preview 640x480 view 640x480 state 2  #7 -100,-100,100,100 > 288,216,352,264", m.describe(640, 480, 640, 480));
        assertEquals("preview 0x0 view 640x480 state 2  #7 -100,-100,100,100 > -", m.describe(640, 480, 0, 0));
    }

    @Test void theModelHasNoAndroidInIt() throws Exception {
        List<String> lines = Files.readAllLines(Paths.get("src/com/voxivoid/recipelab/FocusOverlayModel.java"), StandardCharsets.UTF_8);
        lines.addAll(Files.readAllLines(Paths.get("src/com/voxivoid/recipelab/FocusAreaBridge.java"), StandardCharsets.UTF_8));
        for (String l : lines) assertFalse(l.startsWith("import android."), l);
    }

    @Test void theAreaListIsDescribedAsSonySentIt() {
        assertEquals("no area list", FocusOverlayModel.describeAreas(null));
        assertEquals("0 areas, 0 drawable:", FocusOverlayModel.describeAreas(new FocusOverlayModel.Area[0]));
        FocusOverlayModel.Area[] a = {
            new FocusOverlayModel.Area(0, true, -100, -100, 100, 100),
            new FocusOverlayModel.Area(1, false, -50, -50, 50, 50),
            null,
            new FocusOverlayModel.Area(3, true, 0, 0, 40, 40),
        };
        assertEquals("4 areas, 2 drawable: #0 on [-100,-100,100,100] #1 off [-50,-50,50,50] null", FocusOverlayModel.describeAreas(a),
                "the off one is not drawable; only the first three are written out");
        FocusOverlayModel.Area[] pixels = { new FocusOverlayModel.Area(0, true, 200, 150, 1400, 1100) };
        assertEquals("1 areas, 0 drawable: #0 on [200,150,1400,1100]", FocusOverlayModel.describeAreas(pixels),
                "a range other than -1000..1000 shows up as nothing drawable");
    }
}
