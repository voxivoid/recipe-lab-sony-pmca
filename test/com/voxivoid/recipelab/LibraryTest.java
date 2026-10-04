package com.voxivoid.recipelab;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

/** One index over the table's recipes and the custom ones from the card, as the wheel and the panel use it. */
class LibraryTest {
    private static final int LAST_BUILT_IN = Library.BASE - 1;

    @Test void withoutCustomRecipesItIsTheTable() {
        Library lib = new Library();
        assertEquals(Recipes.ALL.length, Library.BASE);
        for (int i = 1; i < Library.BASE; i++) {
            assertSame(Recipes.ALL[i], lib.get(i));
            assertEquals(Recipes.next(i, +1), lib.next(i, +1), "the wheel walks as before");
            assertEquals(Recipes.next(i, -1), lib.next(i, -1));
            assertEquals(Recipes.position(i), lib.position(i));
        }
        assertFalse(lib.valid(Library.BASE));
        assertFalse(lib.isCustom(Library.BASE));
    }

    @Test void customRecipesFollowTheTableAToZ() {
        Library lib = FavouritesTest.custom("beta", "Alpha", "Gamma");
        assertTrue(lib.isCustom(Library.BASE));
        assertFalse(lib.isCustom(LAST_BUILT_IN));
        assertEquals(Arrays.asList("Alpha", "beta", "Gamma"), lib.customNames(), "ignoring case");
        assertEquals("beta", lib.get(Library.BASE + 1).name);
        assertEquals("beta.txt", lib.entry(Library.BASE + 1).file);
        assertEquals(Library.BASE + 2, lib.indexOfCustom("GAMMA"));
        assertEquals(-1, lib.indexOfCustom("Velvia"), "a built-in name is not a custom one");
        assertEquals(-1, lib.indexOfCustom(null));
    }

    @Test void theWheelWalksOnFromTheTableIntoTheCustomRecipesAndWraps() {
        Library lib = FavouritesTest.custom("A", "B");
        assertEquals(Library.BASE, lib.next(LAST_BUILT_IN, +1));
        assertEquals(Library.BASE + 1, lib.next(Library.BASE, +1));
        assertEquals(1, lib.next(Library.BASE + 1, +1), "past the last custom recipe: the first listed one, never the factory look");
        assertEquals(Library.BASE + 1, lib.next(1, -1));
        assertEquals(1, lib.next(Recipes.FACTORY, +1));
        assertEquals(Library.BASE + 1, lib.next(Recipes.FACTORY, -1));
        int seen = 0, i = 1;
        do { i = lib.next(i, +1); seen++; } while (i != 1);
        assertEquals(Recipes.LISTED + 2, seen, "one turn visits every listed recipe once");
    }

    @Test void thePanelCountsCustomRecipesAmongThemselves() {
        Library lib = FavouritesTest.custom("A", "B", "C");
        assertEquals("2 / 3", lib.position(Library.BASE + 1));
        assertEquals(Recipes.position(5), lib.position(5));
    }

    @Test void anIndexLeftOverFromALongerListIsNotARecipe() {
        Library lib = FavouritesTest.custom("A");
        assertTrue(lib.valid(Library.BASE));
        lib.set(Arrays.<CustomRecipes.Entry>asList());
        assertFalse(lib.valid(Library.BASE), "after the card lost its recipes, MainActivity moves off this index");
        assertEquals(1, lib.next(Library.BASE, +1));
    }
}
