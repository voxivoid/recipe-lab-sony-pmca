package com.voxivoid.recipelab;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ComparePreviewTest {
    @Test void startsOnTheRecipeAndOnlyThatSideCanBePicked() {
        ComparePreview compare = new ComparePreview();
        int[] stored = { 1, 2 }, recipe = { 3, 4 };
        assertSame(recipe, compare.values(stored, recipe));
        assertFalse(compare.camera());
        assertTrue(compare.canPick());
    }

    @Test void switchesSidesWithoutChangingEitherSetOfValues() {
        ComparePreview compare = new ComparePreview();
        int[] stored = { 1, 2 }, recipe = { 3, 4 };
        compare.showCamera();
        assertSame(stored, compare.values(stored, recipe));
        assertTrue(compare.camera());
        assertFalse(compare.canPick());
        assertArrayEquals(new int[] { 1, 2 }, stored);
        assertArrayEquals(new int[] { 3, 4 }, recipe);

        compare.showRecipe();
        assertSame(recipe, compare.values(stored, recipe));
        assertTrue(compare.canPick());
    }
}
