package com.voxivoid.recipelab;

import static com.voxivoid.recipelab.Fixtures.indexOf;
import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The favourites list: how it is stored, how a mark toggles, and how the browser walks the Favourites and Custom groups. */
class FavouritesTest {
    /** no custom recipes: the table alone */
    private static final Library LIB = new Library();

    private static List<Integer> favs(String... names) {
        List<Integer> f = new ArrayList<Integer>();
        for (String n : names) f.add(indexOf(n));
        return f;
    }

    /** a library with custom recipes of these names, A to Z */
    static Library custom(String... names) {
        List<CustomRecipes.Entry> e = new ArrayList<CustomRecipes.Entry>();
        for (String n : names) e.add(new CustomRecipes.Entry(CustomRecipes.recipe(n, Fixtures.factoryRows()), n + ".txt"));
        Library lib = new Library();
        lib.set(e);
        return lib;
    }

    @Test void noRecipeNameContainsTheSeparator() {
        for (Recipes.Recipe r : Recipes.ALL) assertFalse(r.name.contains(Favourites.SEP), r.name);
        for (char c : Favourites.SEP.toCharArray()) assertFalse(CustomRecipes.nameChar(c), "nor can a custom recipe's");
    }

    @Test void storedByNameInMarkingOrder() {
        List<Integer> f = favs("Kodak Portra 400", "Velvia", "Acros");
        assertEquals("Kodak Portra 400|Velvia|Acros", Favourites.encode(f, LIB, ""));
        assertEquals(f, Favourites.decode("Kodak Portra 400|Velvia|Acros", LIB));
    }

    @Test void nothingStoredMeansNoFavourites() {
        assertTrue(Favourites.decode(null, LIB).isEmpty());
        assertTrue(Favourites.decode("", LIB).isEmpty());
        assertEquals("", Favourites.encode(new ArrayList<Integer>(), LIB, null));
    }

    @Test void unknownAndRepeatedNamesAreDroppedOnLoad() {
        assertEquals(favs("Velvia", "Acros"), Favourites.decode("Velvia|Kodak Portra 9000|Velvia||Acros", LIB),
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
            List<Integer> f = Favourites.decode(stored, LIB);
            Favourites.toggle(f, indexOf("Acros"));
            assertTrue(f.contains(indexOf("Acros")), "decode(" + stored + ") returned a list that cannot be added to");
        }
    }

    @Test void toggleMessage() {
        assertEquals("Velvia added to Favourites", Favourites.toggleMessage("Velvia", true));
        assertEquals("Velvia removed from Favourites", Favourites.toggleMessage("Velvia", false));
    }

    @Test void favouritesThenCustomSitFirstInTheBrandColumnAndTheColumnWraps() {
        int last = Recipes.GROUPS.length - 1;
        assertEquals(Favourites.CUSTOM, Favourites.nextGroup(Favourites.GROUP, +1), "down from Favourites is Custom");
        assertEquals(0, Favourites.nextGroup(Favourites.CUSTOM, +1), "down from Custom is the first brand");
        assertEquals(Favourites.CUSTOM, Favourites.nextGroup(0, -1));
        assertEquals(Favourites.GROUP, Favourites.nextGroup(last, +1), "down from the last brand wraps to Favourites");
        assertEquals(last, Favourites.nextGroup(Favourites.GROUP, -1));
        assertEquals(0, Favourites.groupRow(Favourites.GROUP));
        assertEquals(1, Favourites.groupRow(Favourites.CUSTOM));
        assertEquals(2, Favourites.groupRow(0));
        for (int row = 0; row < Favourites.groupRows(); row++) assertEquals(row, Favourites.groupRow(Favourites.groupAt(row)));
        assertEquals("Favourites", Favourites.groupName(Favourites.GROUP));
        assertEquals("Custom", Favourites.groupName(Favourites.CUSTOM));
        assertEquals(Recipes.GROUPS[3], Favourites.groupName(3));
    }

    @Test void landingOnAGroupHighlightsItsFirstRecipe() {
        List<Integer> f = favs("Acros", "Velvia");
        assertEquals(indexOf("Acros"), Favourites.landing(Favourites.GROUP, f, LIB), "the first marked, not the first in the table");
        assertEquals(Recipes.GROUP_START[3], Favourites.landing(3, f, LIB));
        assertEquals(-1, Favourites.landing(Favourites.GROUP, new ArrayList<Integer>(), LIB), "nothing to land on: the highlight stays put");
        assertEquals(-1, Favourites.landing(Favourites.CUSTOM, f, LIB), "a Custom group with only its New row previews nothing");
        assertEquals(Library.BASE, Favourites.landing(Favourites.CUSTOM, f, custom("Zed", "Alpha")), "the first custom recipe, A to Z");
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
        assertEquals(Favourites.GROUP, Favourites.openingGroup(f, indexOf("Velvia"), LIB));
        assertEquals(Recipes.ALL[indexOf("Acros")].group, Favourites.openingGroup(f, indexOf("Acros"), LIB));
        assertEquals(0, Favourites.openingGroup(new ArrayList<Integer>(), 0, LIB));
        Library lib = custom("Mine");
        assertEquals(Favourites.CUSTOM, Favourites.openingGroup(f, Library.BASE, lib), "a custom recipe opens its own group");
        f.add(Library.BASE);
        assertEquals(Favourites.GROUP, Favourites.openingGroup(f, Library.BASE, lib), "unless it is a favourite");
    }

    @Test void onlyANonEmptyGroupHasARecipeColumn() {
        List<Integer> f = favs("Velvia");
        assertTrue(Favourites.hasRecipes(Favourites.GROUP, f, LIB));
        assertFalse(Favourites.hasRecipes(Favourites.GROUP, new ArrayList<Integer>(), LIB));
        assertTrue(Favourites.hasRecipes(0, new ArrayList<Integer>(), LIB), "a brand always has recipes");
    }

    @Test void aGroupListsItsRecipesByPosition() {
        List<Integer> f = favs("Velvia", "Acros");
        assertEquals(2, Favourites.groupCount(Favourites.GROUP, f, LIB));
        assertEquals(Recipes.GROUP_COUNT[0], Favourites.groupCount(0, f, LIB));
        assertEquals(indexOf("Acros"), Favourites.recipeAt(Favourites.GROUP, 1, f, LIB));
        assertEquals(Recipes.GROUP_START[3] + 2, Favourites.recipeAt(3, 2, f, LIB));
        assertEquals(1, Favourites.positionIn(Favourites.GROUP, indexOf("Acros"), f, LIB));
        assertEquals(-1, Favourites.positionIn(Favourites.GROUP, indexOf("Provia"), f, LIB));
        assertEquals(2, Favourites.positionIn(3, Recipes.GROUP_START[3] + 2, f, LIB));
        assertEquals(-1, Favourites.positionIn(3, Recipes.GROUP_START[0], f, LIB), "a recipe of another brand");
    }

    @Test void theCustomGroupListsTheCustomRecipesAToZ() {
        Library lib = custom("Beta", "Alpha");
        List<Integer> f = new ArrayList<Integer>();
        assertEquals(2, Favourites.groupCount(Favourites.CUSTOM, f, lib));
        assertEquals(Library.BASE + 1, Favourites.recipeAt(Favourites.CUSTOM, 1, f, lib));
        assertEquals("Beta", lib.get(Favourites.recipeAt(Favourites.CUSTOM, 1, f, lib)).name, "A to Z");
        assertEquals(1, Favourites.positionIn(Favourites.CUSTOM, Library.BASE + 1, f, lib));
        assertEquals(-1, Favourites.positionIn(Favourites.CUSTOM, indexOf("Velvia"), f, lib));
        assertEquals(-1, Favourites.positionIn(3, Library.BASE, f, lib), "a custom recipe is in no brand");
        assertFalse(Favourites.hasRecipes(Favourites.CUSTOM, f, LIB), "an empty Custom group has no recipe column, like Favourites");
    }

    @Test void theCustomColumnWalksItsRecipesAndWraps() {
        Library lib = custom("A", "B");
        assertEquals(Library.BASE + 1, Favourites.nextCustom(Library.BASE, +1, lib));
        assertEquals(Library.BASE, Favourites.nextCustom(Library.BASE + 1, +1, lib), "past the last one, back to the first");
        assertEquals(Library.BASE + 1, Favourites.nextCustom(Library.BASE, -1, lib));
        assertEquals(Library.BASE, Favourites.nextCustom(indexOf("Velvia"), +1, lib), "from outside the group: the first one");
        assertEquals(-1, Favourites.nextCustom(Library.BASE, +1, LIB), "none at all");
    }

    @Test void anEmptyGroupSaysHowToFillIt() {
        assertEquals("No custom recipes yet", Favourites.emptyTitle(Favourites.CUSTOM, true));
        assertTrue(Favourites.emptyHint(Favourites.CUSTOM).contains("New recipe"), "the app menu row");
        assertTrue(Favourites.emptyTitle(Favourites.CUSTOM, false).startsWith("No memory card"));
        assertEquals("No favourites yet", Favourites.emptyTitle(Favourites.GROUP, true));
    }

    @Test void everyListedRecipeCanBeMarkedAndReadBack() {
        List<Integer> all = new ArrayList<Integer>();
        for (int i = 1; i < Recipes.ALL.length; i++) all.add(i);
        assertEquals(all, Favourites.decode(Favourites.encode(all, LIB, ""), LIB));
        List<Integer> two = Arrays.asList(Recipes.ALL.length - 1, 1);
        assertEquals(two, Favourites.decode(Favourites.encode(two, LIB, ""), LIB));
    }

    @Test void theFactoryLookCanBeAFavourite() {
        List<Integer> favs = new ArrayList<Integer>();
        assertTrue(Favourites.toggle(favs, Recipes.FACTORY), "a recipe like the others");
        assertEquals(Arrays.asList(Recipes.FACTORY), favs);
    }

    // ---- custom recipes as favourites
    @Test void aCustomRecipeIsStoredByPrefixedNameAndMayShareABuiltInName() {
        Library lib = custom("Velvia");                           // a user may call their own recipe after a built-in one
        List<Integer> f = new ArrayList<Integer>(Arrays.asList(indexOf("Velvia"), Library.BASE));
        String stored = Favourites.encode(f, lib, "");
        assertEquals("Velvia|custom:Velvia", stored);
        assertEquals(f, Favourites.decode(stored, lib), "both come back, each to its own recipe");
    }

    @Test void aCustomMarkSurvivesACardThatIsOut() {
        Library in = custom("Mine"), out = LIB;
        String stored = Favourites.encode(Arrays.asList(indexOf("Velvia"), Library.BASE), in, "");
        List<Integer> f = Favourites.decode(stored, out);
        assertEquals(favs("Velvia"), f, "without the card the custom recipe is not shown");
        Favourites.toggle(f, indexOf("Acros"));
        String again = Favourites.encode(f, out, stored);
        assertEquals("Velvia|Acros|custom:Mine", again, "a mark made meanwhile keeps the one it cannot see");
        assertEquals(Arrays.asList(indexOf("Velvia"), indexOf("Acros"), Library.BASE), Favourites.decode(again, in), "and the card brings it back");
    }

    @Test void renameMovesTheMarkAndDeleteForgetsIt() {
        String stored = "Velvia|custom:Old|Acros";
        assertEquals("Velvia|custom:New|Acros", Favourites.renameCustom(stored, "Old", "New"));
        assertEquals("Velvia|Acros", Favourites.forgetCustom(stored, "old"), "names are matched ignoring case, as they are unique that way");
        assertEquals("Velvia|Acros", Favourites.forgetCustom(stored + "|", "Old"));
        assertEquals("", Favourites.renameCustom(null, "a", "b"));
    }

    // ---- what mutation testing found unasserted
    @Test void theCustomColumnStepsBackAndForthOverThree() {
        Library lib = custom("A", "B", "C");
        assertEquals(Library.BASE + 1, Favourites.nextCustom(Library.BASE + 2, -1, lib));
        assertEquals(Library.BASE + 2, Favourites.nextCustom(Library.BASE, -1, lib), "back from the first wraps to the last");
        assertEquals(Library.BASE + 2, Favourites.nextCustom(Library.BASE + 1, +1, lib));
    }

    @Test void theFactoryLookIsStoredAndReadBack() {
        List<Integer> f = new ArrayList<Integer>(Arrays.asList(Recipes.FACTORY));
        String stored = Favourites.encode(f, LIB, "");
        assertEquals(Recipes.ALL[Recipes.FACTORY].name, stored);
        assertEquals(f, Favourites.decode(stored, LIB), "index 0 is a recipe like the others");
    }

    @Test void emptyMarksInTheStoredStringAreDropped() {
        assertEquals("Velvia|Acros|custom:Gone", Favourites.encode(favs("Velvia", "Acros"), LIB, "||custom:Gone|"),
                "an unknown custom mark is kept, the empty ones are not");
    }

    @Test void theLastRecipeOfTheTableIsInItsBrand() {
        int last = Library.BASE - 1, g = Recipes.ALL[last].group;
        assertEquals(Recipes.GROUP_COUNT[g] - 1, Favourites.positionIn(g, last, new ArrayList<Integer>(), LIB));
    }
}
