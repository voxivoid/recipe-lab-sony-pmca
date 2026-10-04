package com.voxivoid.recipelab;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/** The name editor: the placeholder the first character replaces, capitals, delete, and walking the keyboard. */
class NameEntryTest {

    /** highlights the cell holding {@code c} by stepping, as the wheel would */
    private static void go(NameEntry e, char c) {
        for (int i = 0; i < 100; i++) { if (e.current() == c) return; e.step(+1); }
        fail("no cell " + (int) c);
    }

    private static void type(NameEntry e, String s) { for (char c : s.toCharArray()) { go(e, Character.toUpperCase(c)); e.press(); } }

    @Test void theFirstCharacterReplacesThePlaceholder() {
        NameEntry e = NameEntry.blank("Untitled 2");
        assertTrue(e.isPlaceholder());
        assertEquals("Untitled 2", e.shown());
        type(e, "G");
        assertFalse(e.isPlaceholder());
        assertEquals("G", e.shown(), "nothing to delete first");
    }

    @Test void okOnTheUntouchedPlaceholderKeepsIt() {
        NameEntry e = NameEntry.blank("Untitled");
        go(e, NameEntry.OK);
        assertEquals(NameEntry.DONE, e.press());
        assertEquals("Untitled", e.value());
    }

    @Test void theFirstLetterIsACapitalThenLowerCase() {
        NameEntry e = NameEntry.blank("Untitled");
        type(e, "GOLD");
        assertEquals("Gold", e.shown());
        go(e, NameEntry.SHIFT); e.press();
        type(e, "R");
        assertEquals("GoldR", e.shown(), "shift gives one capital");
        type(e, "x");
        assertEquals("GoldRx", e.shown());
    }

    @Test void deleteTakesTheLastCharacterOrTheWholePlaceholder() {
        NameEntry e = NameEntry.blank("Untitled");
        e.backspace();
        assertEquals("", e.shown(), "delete on the placeholder clears it");
        assertFalse(e.isPlaceholder());
        type(e, "AB");
        e.backspace();
        assertEquals("A", e.shown());
        e.backspace();
        assertTrue(e.upper(), "an empty name starts with a capital again");
        e.backspace();
        assertEquals("", e.shown(), "nothing left to delete");
    }

    @Test void aRenameEditsTheRealName() {
        NameEntry e = NameEntry.of("Golden Hour");
        assertFalse(e.isPlaceholder());
        assertFalse(e.upper());
        type(e, "S");
        assertEquals("Golden Hours", e.shown(), "typing appends, it does not replace");
    }

    @Test void theNameStopsAtTheMaximum() {
        NameEntry e = NameEntry.blank("x");
        for (int i = 0; i < CustomRecipes.NAME_MAX + 5; i++) type(e, "A");
        assertEquals(CustomRecipes.NAME_MAX, e.shown().length());
    }

    @Test void valueIsTrimmed() {
        NameEntry e = NameEntry.blank("x");
        type(e, "A ");
        assertEquals("A", e.value());
    }

    @Test void theFourWayWrapsAndKeepsTheColumnOverRowsOfOtherWidths() {
        NameEntry e = NameEntry.blank("x");
        assertEquals('A', e.current());
        e.move(0, -1);
        assertEquals('M', e.current(), "left from the first cell wraps to the end of the row");
        e.move(-1, 0);
        assertEquals(NameEntry.OK, e.current(), "up from the top row wraps to the bottom one, under the same part of the screen");
        e.move(+1, 0);
        assertEquals('M', e.current());
        for (int r = 0; r < NameEntry.rows(); r++) e.move(+1, 0);
        assertEquals('M', e.current(), "a full turn of rows comes back");
    }

    @Test void theWheelReadsTheKeyboardInOrderAndWraps() {
        NameEntry e = NameEntry.blank("x");
        e.step(-1);
        assertEquals(NameEntry.OK, e.current(), "back from the first cell is the last");
        e.step(+1);
        assertEquals('A', e.current());
        e.step(13);
        assertEquals('N', e.current(), "the second row follows the first");
    }

    @Test void anyKeyClearsTheError() {
        NameEntry e = NameEntry.blank("x");
        e.setError("Type a name first");
        e.step(+1);
        assertNull(e.error());
    }

    @Test void theGridHasEveryControl() {
        String all = String.join("", NameEntry.GRID);
        for (char c : new char[] { NameEntry.SHIFT, NameEntry.BACK, NameEntry.OK, ' ' }) assertTrue(all.indexOf(c) >= 0, "cell " + (int) c);
    }
}
