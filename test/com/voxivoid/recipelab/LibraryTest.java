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
        for (int i = 0; i < Library.BASE; i++) {
            assertSame(Recipes.ALL[i], lib.get(i));
            assertEquals(Recipes.next(i, +1), lib.next(i, +1), "the wheel walks as before");
            assertEquals(Recipes.next(i, -1), lib.next(i, -1));
            assertEquals(Recipes.position(i), lib.position(i));
        }
        assertFalse(lib.valid(Library.BASE));
        assertFalse(lib.isCustom(Library.BASE));
    }

    @Test void customRecipesFollowTheTableAToZ() {
        Library lib = FavoritesTest.custom("beta", "Alpha", "Gamma");
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
        Library lib = FavoritesTest.custom("A", "B");
        assertEquals(Library.BASE, lib.next(LAST_BUILT_IN, +1));
        assertEquals(Library.BASE + 1, lib.next(Library.BASE, +1));
        assertEquals(Recipes.FACTORY, lib.next(Library.BASE + 1, +1), "past the last custom recipe: the first one, the factory look");
        assertEquals(Library.BASE + 1, lib.next(Recipes.FACTORY, -1));
        assertEquals(1, lib.next(Recipes.FACTORY, +1));
        int seen = 0, i = 0;
        do { i = lib.next(i, +1); seen++; } while (i != 0);
        assertEquals(Recipes.LISTED + 2, seen, "one turn visits every listed recipe once");
    }

    @Test void thePanelCountsTheCustomRecipesInTheTotal() {
        Library lib = FavoritesTest.custom("A", "B", "C");
        int total = Library.BASE + 3;
        assertEquals((Library.BASE + 2) + " / " + total, lib.position(Library.BASE + 1), "after the table");
        assertEquals("6 / " + total, lib.position(5));
    }

    @Test void anIndexLeftOverFromALongerListIsNotARecipe() {
        Library lib = FavoritesTest.custom("A");
        assertTrue(lib.valid(Library.BASE));
        lib.set(Arrays.<CustomRecipes.Entry>asList());
        assertFalse(lib.valid(Library.BASE), "after the card lost its recipes, MainActivity moves off this index");
        assertEquals(0, lib.next(Library.BASE, +1));
    }

    @Test void fromAnIndexThatIsNoRecipeTheWheelGoesToAnEnd() {
        Library lib = FavoritesTest.custom("A", "B");
        assertEquals(0, lib.next(-1, +1));
        assertEquals(Library.BASE + 1, lib.next(-1, -1), "backwards: the last custom recipe");
        assertEquals(Library.BASE + 1, lib.next(Library.BASE + 2, -1));
        assertEquals(Library.BASE - 1, lib.next(Library.BASE, -1), "from the first custom back into the table");
    }
}
