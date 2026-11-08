package com.voxivoid.recipelab;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

/** Text fitted to a width, measured here one unit per character: what the camera's Paint measures, made simple. */
class TextFitTest {
    static final TextFit.Width ONE = (s, start, end) -> end - start;

    private static String[] wrap(String s, float max, int maxLines) {
        String[] lines = new String[maxLines];
        int n = TextFit.wrap(s, ONE, max, lines, maxLines);
        return Arrays.copyOf(lines, n);
    }

    @Test void aLineThatFitsIsLeftAsItIs() {
        assertEquals("Velvia", TextFit.ellipsize("Velvia", ONE, 6));
        assertEquals("", TextFit.ellipsize("", ONE, 0));
    }

    @Test void nothingToShowIsEmpty() { assertEquals("", TextFit.ellipsize(null, ONE, 10)); }

    @Test void aLongLineIsCutWithAnEllipsisThatFits() {
        assertEquals("Hello…", TextFit.ellipsize("Hello world", ONE, 6));
        assertEquals("Hello…", TextFit.ellipsize("Hello world", ONE, 7), "the space before the cut is dropped");
        assertEquals("Hello w…", TextFit.ellipsize("Hello world", ONE, 8));
        assertEquals("…", TextFit.ellipsize("Hello", ONE, 1), "only the ellipsis fits");
        assertEquals("…", TextFit.ellipsize("Hello", ONE, 0), "nothing fits: still the ellipsis, never nothing");
    }

    @Test void theEllipsisIsMeasuredToo() {
        TextFit.Width wideDots = (s, start, end) -> s.substring(start, end).equals(TextFit.ELLIPSIS) ? 3 : end - start;
        assertEquals("Hel…", TextFit.ellipsize("Hello world", wideDots, 6));
    }

    @Test void wordsWrapAtSpaces() {
        assertArrayEquals(new String[] { "the quick", "brown fox" }, wrap("the quick brown fox", 10, 3));
        assertArrayEquals(new String[] { "the", "quick", "brown" }, wrap("the quick brown", 5, 3));
    }

    @Test void spacesAtTheStartOfALineAreDropped() {
        assertArrayEquals(new String[] { "ab", "cd" }, wrap("  ab    cd", 2, 3));
    }

    @Test void textWithoutSpacesBreaksBetweenCharacters() {
        assertArrayEquals(new String[] { "一二三", "四五六", "七" }, wrap("一二三四五六七", 3, 3));
        assertArrayEquals(new String[] { "abcd", "efgh" }, wrap("abcdefgh", 4, 3), "a word wider than the line is split");
    }

    @Test void whatIsLeftForTheLastLineIsEllipsized() {
        assertArrayEquals(new String[] { "one two", "three f…" }, wrap("one two three four five", 8, 2));
        assertArrayEquals(new String[] { "一二三", "四五…" }, wrap("一二三四五六七八", 3, 2));
        assertArrayEquals(new String[] { "one two…" }, wrap("one two three", 8, 1), "one line: the whole text cut");
    }

    @Test void neverMoreLinesThanAsked() {
        String[] lines = new String[2];
        assertEquals(2, TextFit.wrap("a b c d e f g h", ONE, 1, lines, 2));
        assertEquals("a", lines[0]);
        assertEquals("…", lines[1]);
    }

    @Test void nothingOrOnlySpacesIsNoLines() {
        assertEquals(0, wrap("", 10, 3).length);
        assertEquals(0, wrap("     ", 10, 3).length);
    }

    @Test void aLineIsAlwaysAtLeastOneCharacterSoWrappingEnds() {
        assertArrayEquals(new String[] { "a", "b", "c" }, wrap("abc", 0, 5));
    }
}
