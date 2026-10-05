package com.voxivoid.recipelab;

import static com.voxivoid.recipelab.Fixtures.indexOf;
import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The favourites list: how it is stored, how a mark toggles, and how the browser walks the Favourites group. */
class FavouritesTest {

    private static List<Integer> favs(String... names) {
        List<Integer> f = new ArrayList<Integer>();
        for (String n : names) f.add(indexOf(n));
        return f;
    }

    @Test void noRecipeNameContainsTheSeparator() {
        for (Recipes.Recipe r : Recipes.ALL) assertFalse(r.name.contains(Favourites.SEP), r.name);
    }

    @Test void storedByNameInMarkingOrder() {
        List<Integer> f = favs("Kodak Portra 400", "Velvia", "Acros");
        assertEquals("Kodak Portra 400|Velvia|Acros", Favourites.encode(f));
        assertEquals(f, Favourites.decode("Kodak Portra 400|Velvia|Acros"));
    }

    @Test void nothingStoredMeansNoFavourites() {
        assertTrue(Favourites.decode(null).isEmpty());
        assertTrue(Favourites.decode("").isEmpty());
        assertEquals("", Favourites.encode(new ArrayList<Integer>()));
    }

    @Test void unknownAndRepeatedNamesAreDroppedOnLoad() {
        assertEquals(favs("Velvia", "Acros"), Favourites.decode("Velvia|Kodak Portra 9000|Velvia||Acros"),
                "a recipe that left the table, a repeat and an empty field all vanish; the rest keep their order");
    }

    @Test void toggleAppendsThenRemoves() {
        List<Integer> f = favs("Velvia");
        int portra = indexOf("Kodak Portra 400");
        assertTrue(Favourites.toggle(f, portra));
        assertEquals(favs("Velvia", "Kodak Portra 400"), f, "a new mark goes to the end");
        assertFalse(Favourites.toggle(f, indexOf("Velvia")));
        assertEquals(favs("Kodak Portra 400"), f);
        assertFalse(Favourites.toggle(f, portra));
        assertTrue(f.isEmpty());
    }

    @Test void afterRemovalTheHighlightMovesToTheNextOneOrTheNewLast() {
        List<Integer> f = favs("Velvia", "Acros", "Provia");
        Favourites.toggle(f, indexOf("Acros"));          // removed the middle one, position 1
        assertEquals(indexOf("Provia"), Favourites.afterRemoval(f, 1));
        Favourites.toggle(f, indexOf("Provia"));         // removed the last one, position 1
        assertEquals(indexOf("Velvia"), Favourites.afterRemoval(f, 1));
        Favourites.toggle(f, indexOf("Velvia"));
        assertEquals(-1, Favourites.afterRemoval(f, 0));
    }

    @Test void decodeReturnsAListTheCallerCanMarkInto() {
        // MainActivity keeps the decoded list and adds to it on the first mark -- an immutable one would throw there
        for (String stored : new String[] { null, "", "Velvia", "nope" }) {
            List<Integer> f = Favourites.decode(stored);
            Favourites.toggle(f, indexOf("Acros"));
            assertTrue(f.contains(indexOf("Acros")), "decode(" + stored + ") returned a list that cannot be added to");
        }
    }

    @Test void toggleMessage() {
        assertEquals("Velvia added to Favourites", Favourites.toggleMessage("Velvia", true));
        assertEquals("Velvia removed from Favourites", Favourites.toggleMessage("Velvia", false));
    }

    @Test void recentAndFavouritesPrecedeBrandsAndTheColumnWraps() {
        int last = Recipes.GROUPS.length - 1;
        assertEquals(0, Favourites.nextGroup(Favourites.GROUP, +1), "down from Favourites is the first brand");
        assertEquals(Favourites.GROUP, Favourites.nextGroup(0, -1), "up from the first brand is Favourites");
        assertEquals(RecentRecipes.GROUP, Favourites.nextGroup(last, +1), "down from the last brand wraps to Recent");
        assertEquals(last, Favourites.nextGroup(RecentRecipes.GROUP, -1));
        assertEquals(RecentRecipes.GROUP, Favourites.nextGroup(Favourites.GROUP, -1));
        assertEquals(0, Favourites.groupRow(RecentRecipes.GROUP));
        assertEquals(1, Favourites.groupRow(Favourites.GROUP));
        assertEquals(2, Favourites.groupRow(0));
        assertEquals("Recent", Favourites.groupName(RecentRecipes.GROUP));
        assertEquals("Favourites", Favourites.groupName(Favourites.GROUP));
        assertEquals(Recipes.GROUPS[3], Favourites.groupName(3));
    }

    @Test void landingOnAGroupHighlightsItsFirstRecipe() {
        List<Integer> f = favs("Acros", "Velvia");
        List<Integer> recent = favs("Velvia", "Acros");
        assertEquals(indexOf("Acros"), Favourites.landing(Favourites.GROUP, f, recent), "the first marked, not the first in the table");
        assertEquals(indexOf("Velvia"), Favourites.landing(RecentRecipes.GROUP, f, recent));
        assertEquals(Recipes.GROUP_START[3], Favourites.landing(3, f, recent));
        assertEquals(-1, Favourites.landing(Favourites.GROUP, new ArrayList<Integer>(), recent));
        assertEquals(-1, Favourites.landing(RecentRecipes.GROUP, f, new ArrayList<Integer>()));
    }

    @Test void nextWalksTheMarkingOrderAndWraps() {
        List<Integer> f = favs("Acros", "Velvia", "Kodak Gold 200");
        assertEquals(indexOf("Velvia"), Favourites.next(f, indexOf("Acros"), +1));
        assertEquals(indexOf("Acros"), Favourites.next(f, indexOf("Kodak Gold 200"), +1));
        assertEquals(indexOf("Kodak Gold 200"), Favourites.next(f, indexOf("Acros"), -1));
        assertEquals(indexOf("Acros"), Favourites.next(f, indexOf("Provia"), +1), "an unmarked recipe steps onto the first favourite");
        assertEquals(indexOf("Acros"), Favourites.next(f, indexOf("Provia"), -1), "backwards too, rather than into the middle of the list");
        assertEquals(-1, Favourites.next(new ArrayList<Integer>(), 0, +1));
    }

    @Test void theBrowserOpensOnFavouritesWhenTheRecipeIsOne() {
        List<Integer> f = favs("Velvia");
        List<Integer> recent = favs("Velvia", "Acros");
        assertEquals(Favourites.GROUP, Favourites.openingGroup(f, recent, indexOf("Velvia")));
        assertEquals(RecentRecipes.GROUP, Favourites.openingGroup(f, recent, indexOf("Acros")));
        assertEquals(Recipes.ALL[indexOf("Provia")].group, Favourites.openingGroup(f, recent, indexOf("Provia")));
        assertEquals(0, Favourites.openingGroup(new ArrayList<Integer>(), recent, 0));
    }

    @Test void onlyANonEmptyGroupHasARecipeColumn() {
        List<Integer> f = favs("Velvia");
        List<Integer> empty = new ArrayList<Integer>();
        assertTrue(Favourites.hasRecipes(Favourites.GROUP, f, empty));
        assertFalse(Favourites.hasRecipes(Favourites.GROUP, empty, empty));
        assertFalse(Favourites.hasRecipes(RecentRecipes.GROUP, f, empty));
        assertTrue(Favourites.hasRecipes(0, empty, empty), "a brand always has recipes");
    }

    @Test void aGroupListsItsRecipesByPosition() {
        List<Integer> f = favs("Velvia", "Acros");
        List<Integer> recent = favs("Acros", "Velvia");
        assertEquals(2, Favourites.groupCount(Favourites.GROUP, f, recent));
        assertEquals(2, Favourites.groupCount(RecentRecipes.GROUP, f, recent));
        assertEquals(Recipes.GROUP_COUNT[0], Favourites.groupCount(0, f, recent));
        assertEquals(indexOf("Acros"), Favourites.recipeAt(Favourites.GROUP, 1, f, recent));
        assertEquals(indexOf("Velvia"), Favourites.recipeAt(RecentRecipes.GROUP, 1, f, recent));
        assertEquals(Recipes.GROUP_START[3] + 2, Favourites.recipeAt(3, 2, f, recent));
        assertEquals(1, Favourites.positionIn(Favourites.GROUP, indexOf("Acros"), f, recent));
        assertEquals(0, Favourites.positionIn(RecentRecipes.GROUP, indexOf("Acros"), f, recent));
        assertEquals(-1, Favourites.positionIn(Favourites.GROUP, indexOf("Provia"), f, recent));
        assertEquals(2, Favourites.positionIn(3, Recipes.GROUP_START[3] + 2, f, recent));
        assertEquals(-1, Favourites.positionIn(3, Recipes.GROUP_START[0], f, recent), "a recipe of another brand");
    }

    @Test void everyListedRecipeCanBeMarkedAndReadBack() {
        List<Integer> all = new ArrayList<Integer>();
        for (int i = 1; i < Recipes.ALL.length; i++) all.add(i);
        assertEquals(all, Favourites.decode(Favourites.encode(all)));
        assertEquals(Arrays.asList(Recipes.ALL.length - 1, 1), Favourites.decode(Favourites.encode(Arrays.asList(Recipes.ALL.length - 1, 1))));
    }

    @Test void theFactoryLookIsNeverAFavourite() {
        List<Integer> favs = new ArrayList<Integer>();
        assertFalse(Favourites.markable(Recipes.FACTORY));
        assertFalse(Favourites.toggle(favs, Recipes.FACTORY), "only Reset settings reaches it, so it has no place in a list");
        assertTrue(favs.isEmpty());
        assertEquals(Arrays.asList(3), Favourites.decode(Recipes.ALL[Recipes.FACTORY].name + "|" + Recipes.ALL[3].name),
                "a mark stored by an older build is dropped on load");
    }
}
