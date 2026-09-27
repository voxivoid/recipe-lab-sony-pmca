package com.voxivoid.recipelab;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The keys (issue #18): the press / hold gesture, the trash-hold guard, and a legend that only names keys the body has. */
class KeysTest {
    private static final Keys.Caps NONE = new Keys.Caps(false, false), BOTH = new Keys.Caps(true, true);
    private static final int[] EVERY_MODE = { Keys.H_RECIPE, Keys.H_CHIPS, Keys.H_EDIT, Keys.H_BRANDS, Keys.H_RECIPES,
            Keys.H_MENU_TOP, Keys.H_MENU_SUB, Keys.H_PAGE, Keys.H_LOGGER };

    // ---- press / hold
    @Test void aPressReleasedBeforeTheHoldIsShort() {
        Keys.Hold h = new Keys.Hold();
        assertEquals(Keys.Hold.ARM, h.down(0));
        assertEquals(Keys.Hold.SHORT, h.up());
    }

    @Test void aHoldThatFiredSwallowsItsRelease() {
        Keys.Hold h = new Keys.Hold();
        h.down(0);
        assertEquals(Keys.Hold.HOLD, h.fire());
        assertEquals(Keys.Hold.NONE, h.up(), "the release of a hold must not also run the press action");
    }

    @Test void aHoldFiresOncePerPress() {
        Keys.Hold h = new Keys.Hold();
        h.down(0);
        assertEquals(Keys.Hold.HOLD, h.fire());
        assertEquals(Keys.Hold.NONE, h.fire());
    }

    @Test void repeatsAndSecondDownsWhileHeldAreIgnored() {
        Keys.Hold h = new Keys.Hold();
        assertEquals(Keys.Hold.IGNORE, h.down(1), "a key-repeat is never a press of its own");
        assertEquals(Keys.Hold.ARM, h.down(0));
        assertEquals(Keys.Hold.IGNORE, h.down(0), "a repeat the firmware sends without a repeat count");
        assertEquals(Keys.Hold.SHORT, h.up());
    }

    @Test void aReleaseOrATimerWithoutAPressDoesNothing() {
        Keys.Hold h = new Keys.Hold();
        assertEquals(Keys.Hold.NONE, h.up(), "a release whose press went to another screen");
        assertEquals(Keys.Hold.NONE, h.fire(), "a timer left over from a press that was already released");
    }

    @Test void resetForgetsAPressWhoseReleaseNeverCame() {
        Keys.Hold h = new Keys.Hold();
        h.down(0);
        h.reset();
        assertFalse(h.isDown());
        assertEquals(Keys.Hold.ARM, h.down(0), "the next press is a press again");
    }

    // ---- the trash hold may only store factory when the key is known to be down
    @Test void theProbeDecidesWhenItCanAnswer() {
        assertTrue(Keys.trashHoldActs(true, false), "still down per the camera: a hold, even before any key-up was seen");
        assertFalse(Keys.trashHoldActs(false, true), "released per the camera: the key-up got lost, this was a press");
    }

    @Test void withoutTheProbeOnlyABodyThatDeliversKeyUpsGetsTheHold() {
        assertFalse(Keys.trashHoldActs(null, false), "no key-up ever seen: a quick press to hide cannot be told from a hold");
        assertTrue(Keys.trashHoldActs(null, true), "a key-up has arrived before, so one would have cancelled the timer");
    }

    // ---- the legend
    private static List<Integer> iconsOf(Keys.Hints h) {
        List<Integer> out = new ArrayList<Integer>();
        for (int i = 0; i < h.icons.length; i++) { out.add(h.icons[i]); if (h.alts[i] != Keys.I_NONE) out.add(h.alts[i]); }
        return out;
    }

    @Test void everyRowIsWellFormed() {
        for (Keys.Caps caps : new Keys.Caps[] { Keys.Caps.UNKNOWN, NONE, BOTH }) {
            for (int mode : EVERY_MODE) {
                Keys.Hints h = Keys.hints(mode, caps);
                assertEquals(h.icons.length, h.alts.length, "mode " + mode);
                assertEquals(h.icons.length, h.labels.length, "mode " + mode);
                assertTrue(h.icons.length > 0, "mode " + mode);
                for (int i = 0; i < h.icons.length; i++) assertNotEquals(Keys.I_NONE, h.icons[i], "mode " + mode + " item " + i);
            }
        }
    }

    @Test void anUnknownOrBareBodyIsNeverShownFnOrAel() {
        for (Keys.Caps caps : new Keys.Caps[] { Keys.Caps.UNKNOWN, NONE }) {
            for (int mode : EVERY_MODE) {
                List<Integer> icons = iconsOf(Keys.hints(mode, caps));
                assertFalse(icons.contains(Keys.I_FN), "mode " + mode);
                assertFalse(icons.contains(Keys.I_AEL), "mode " + mode);
            }
        }
    }

    @Test void aelOnlyEverSitsNextToTrash() {
        for (int mode : EVERY_MODE) {
            Keys.Hints h = Keys.hints(mode, BOTH);
            for (int i = 0; i < h.icons.length; i++) {
                assertNotEquals(Keys.I_AEL, h.icons[i], "mode " + mode + ": AEL is never an item of its own");
                if (h.alts[i] == Keys.I_AEL) assertEquals(Keys.I_TRASH, h.icons[i], "mode " + mode);
            }
        }
    }

    @Test void theMainPanelNamesEveryFunctionOnUniversalKeys() {
        Keys.Hints h = Keys.hints(Keys.H_RECIPE, Keys.Caps.UNKNOWN);
        List<String> labels = Arrays.asList(h.labels);
        for (String f : new String[] { "pick", "fav (hold)", "hide", "factory (hold)", "menu (hold)", "exit" }) assertTrue(labels.contains(f), f);
        assertEquals(Keys.I_TRASH, h.icons[labels.indexOf("hide")]);
        assertEquals(Keys.I_TRASH, h.icons[labels.indexOf("factory (hold)")]);
        assertEquals(Keys.I_MENU, h.icons[labels.indexOf("menu (hold)")]);
        assertEquals(Keys.I_MENU, h.icons[labels.indexOf("exit")]);
    }

    @Test void fnAndAelJoinTheLegendWhenTheBodyHasThem() {
        Keys.Hints h = Keys.hints(Keys.H_RECIPE, BOTH);
        List<String> labels = Arrays.asList(h.labels);
        assertEquals(Keys.I_FN, h.icons[labels.indexOf("browse")]);
        assertEquals(Keys.I_AEL, h.alts[labels.indexOf("hide")]);
        assertFalse(Arrays.asList(Keys.hints(Keys.H_RECIPE, NONE).labels).contains("browse"), "browse is in the app menu without Fn");
    }

    @Test void theBrowserClosesOnMenuWithFnBesideItWhenPresent() {
        for (int mode : new int[] { Keys.H_BRANDS, Keys.H_RECIPES }) {
            Keys.Hints bare = Keys.hints(mode, NONE), full = Keys.hints(mode, BOTH);
            int close = Arrays.asList(bare.labels).indexOf("close");
            assertEquals(Keys.I_MENU, bare.icons[close]);
            assertEquals(Keys.I_NONE, bare.alts[close]);
            assertEquals(Keys.I_FN, full.alts[close]);
        }
    }

    // ---- the Controls page
    private static List<String> keysOf(String[][] rows) {
        List<String> out = new ArrayList<String>();
        for (String[] r : rows) { assertEquals(2, r.length); assertFalse(r[1].isEmpty(), r[0]); out.add(r[0]); }
        return out;
    }

    @Test void controlsListTheUniversalKeysFirstOnEveryBody() {
        List<String> bare = keysOf(Keys.controls(Keys.Caps.UNKNOWN));
        assertEquals(Arrays.asList("wheel", "left / right", "up / down", "centre", "centre, hold", "trash", "trash, hold",
                "MENU, hold", "MENU", "shutter"), bare);
        List<String> full = keysOf(Keys.controls(BOTH));
        assertEquals(bare, full.subList(0, bare.size()), "the shortcuts come after the universal keys");
        assertEquals(Arrays.asList("Fn", "AEL"), full.subList(bare.size(), full.size()));
    }

    @Test void controlsNeverNameAKeyTheBodyLacksOrOneThatIsNotBound() {
        for (Keys.Caps caps : new Keys.Caps[] { Keys.Caps.UNKNOWN, NONE, new Keys.Caps(true, null), new Keys.Caps(null, true) }) {
            List<String> keys = keysOf(Keys.controls(caps));
            assertEquals(caps.hasFn(), keys.contains("Fn"));
            assertEquals(caps.hasAel(), keys.contains("AEL"));
            assertFalse(keys.contains("C1"));
            assertFalse(keys.contains("DISP"));
        }
    }

    // ---- scan codes
    @Test void theGesturesKeysAreOneShotAndTheWalkingKeysAreNot() {
        for (int k : new int[] { Keys.K_ENTER, Keys.K_MENU, Keys.K_SK1, Keys.K_DELETE, Keys.K_SK2, Keys.K_FN, Keys.K_AEL }) assertTrue(Keys.oneShot(k), "" + k);
        for (int k : new int[] { Keys.K_UP, Keys.K_DOWN, Keys.K_LEFT, Keys.K_RIGHT, Keys.K_WHEEL_CW, Keys.K_DIAL_CCW }) assertFalse(Keys.oneShot(k), "" + k);
    }

    @Test void theSoftKeysStandForMenuAndTrash() {
        assertTrue(Keys.isMenu(Keys.K_SK1));
        assertTrue(Keys.isTrash(Keys.K_SK2));
        assertFalse(Keys.isTrash(Keys.K_MENU));
    }

    @Test void theLoggerNamesTheCodesAReportWillAskAbout() {
        assertEquals("DELETE", Keys.name(595));
        assertEquals("DISP", Keys.name(608));
        assertEquals("MOVIE", Keys.name(515));
        assertEquals("ZOOM_T", Keys.name(610));
        assertEquals("AEL_AFMF", Keys.name(638));
        assertEquals("", Keys.name(9999), "an unknown code has no name; the logger shows its number");
    }

    @Test void theNoticeUsesOnlyCharactersTheCameraFontHas() {
        // the firmware font has no arrows or symbols (Legend); the notice is plain text in a toast
        for (char ch : Keys.NOTICE.toCharArray()) assertTrue(ch < 0x2190, "U+" + Integer.toHexString(ch) + " in the notice");
    }
}
