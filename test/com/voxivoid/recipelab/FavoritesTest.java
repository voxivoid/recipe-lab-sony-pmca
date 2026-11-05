package com.voxivoid.recipelab;

import static com.voxivoid.recipelab.Fixtures.indexOf;
import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The favorites list: how it is stored, how a mark toggles, and how the browser walks the Favorites and Custom groups. */
class FavoritesTest {
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
        for (Recipes.Recipe r : Recipes.ALL) assertFalse(r.name.contains(Favorites.SEP), r.name);
        for (char c : Favorites.SEP.toCharArray()) assertFalse(CustomRecipes.nameChar(c), "nor can a custom recipe's");
    }

    @Test void storedByNameInMarkingOrder() {
        List<Integer> f = favs("Kodak Portra 400", "Velvia", "Acros");
        assertEquals("Kodak Portra 400|Velvia|Acros", Favorites.encode(f, LIB, ""));
        assertEquals(f, Favorites.decode("Kodak Portra 400|Velvia|Acros", LIB));
    }

    @Test void nothingStoredMeansNoFavorites() {
        assertTrue(Favorites.decode(null, LIB).isEmpty());
        assertTrue(Favorites.decode("", LIB).isEmpty());
        assertEquals("", Favorites.encode(new ArrayList<Integer>(), LIB, null));
    }

    @Test void unknownAndRepeatedNamesAreDroppedOnLoad() {
        assertEquals(favs("Velvia", "Acros"), Favorites.decode("Velvia|Kodak Portra 9000|Velvia||Acros", LIB),
                "a recipe that left the table, a repeat and an empty field all vanish; the rest keep their order");
    }

    @Test void toggleAppendsThenRemoves() {
        List<Integer> f = favs("Velvia");
        int portra = indexOf("Kodak Portra 400");
        assertTrue(Favorites.toggle(f, portra));
        assertEquals(favs("Velvia", "Kodak Portra 400"), f, "a new mark goes to the end");
        assertFalse(Favorites.toggle(f, indexOf("Velvia")));
        assertEquals(favs("Kodak Portra 400"), f);
        assertFalse(Favorites.toggle(f, portra));
        assertTrue(f.isEmpty());
    }

    @Test void afterRemovalTheHighlightMovesToTheNextOneOrTheNewLast() {
        List<Integer> f = favs("Velvia", "Acros", "Provia");
        Favorites.toggle(f, indexOf("Acros"));          // removed the middle one, position 1
        assertEquals(indexOf("Provia"), Favorites.afterRemoval(f, 1));
        Favorites.toggle(f, indexOf("Provia"));         // removed the last one, position 1
        assertEquals(indexOf("Velvia"), Favorites.afterRemoval(f, 1));
        Favorites.toggle(f, indexOf("Velvia"));
        assertEquals(-1, Favorites.afterRemoval(f, 0));
    }

    @Test void decodeReturnsAListTheCallerCanMarkInto() {
        // MainActivity keeps the decoded list and adds to it on the first mark -- an immutable one would throw there
        for (String stored : new String[] { null, "", "Velvia", "nope" }) {
            List<Integer> f = Favorites.decode(stored, LIB);
            Favorites.toggle(f, indexOf("Acros"));
            assertTrue(f.contains(indexOf("Acros")), "decode(" + stored + ") returned a list that cannot be added to");
        }
    }

    @Test void toggleMessage() {
        assertEquals("Velvia added to Favorites", Favorites.toggleMessage("Velvia", true));
        assertEquals("Velvia removed from Favorites", Favorites.toggleMessage("Velvia", false));
    }

    @Test void allThenFavoritesThenCustomSitFirstInTheBrandColumnAndTheColumnWraps() {
        int last = Recipes.GROUPS.length - 1;
        assertEquals(Favorites.GROUP, Favorites.nextGroup(Favorites.ALL, +1), "down from All is Favorites");
        assertEquals(Favorites.CUSTOM, Favorites.nextGroup(Favorites.GROUP, +1), "down from Favorites is Custom");
        assertEquals(0, Favorites.nextGroup(Favorites.CUSTOM, +1), "down from Custom is the first brand");
        assertEquals(Favorites.CUSTOM, Favorites.nextGroup(0, -1));
        assertEquals(Favorites.ALL, Favorites.nextGroup(last, +1), "down from the last brand wraps to All");
        assertEquals(last, Favorites.nextGroup(Favorites.ALL, -1));
        assertEquals(0, Favorites.groupRow(Favorites.ALL));
        assertEquals(1, Favorites.groupRow(Favorites.GROUP));
        assertEquals(2, Favorites.groupRow(Favorites.CUSTOM));
        assertEquals(3, Favorites.groupRow(0));
        assertEquals(Recipes.GROUPS.length + 3, Favorites.groupRows());
        assertEquals("All", Favorites.groupName(Favorites.ALL));
        for (int row = 0; row < Favorites.groupRows(); row++) assertEquals(row, Favorites.groupRow(Favorites.groupAt(row)));
        assertEquals("Favorites", Favorites.groupName(Favorites.GROUP));
        assertEquals("Custom", Favorites.groupName(Favorites.CUSTOM));
        assertEquals(Recipes.GROUPS[3], Favorites.groupName(3));
    }

    @Test void landingOnAGroupHighlightsItsFirstRecipe() {
        List<Integer> f = favs("Acros", "Velvia");
        assertEquals(indexOf("Acros"), Favorites.landing(Favorites.GROUP, f, LIB), "the first marked, not the first in the table");
        assertEquals(Recipes.GROUP_START[3], Favorites.landing(3, f, LIB));
        assertEquals(-1, Favorites.landing(Favorites.GROUP, new ArrayList<Integer>(), LIB), "nothing to land on: the highlight stays put");
        assertEquals(-1, Favorites.landing(Favorites.CUSTOM, f, LIB), "a Custom group with only its New row previews nothing");
        assertEquals(Library.BASE, Favorites.landing(Favorites.CUSTOM, f, custom("Zed", "Alpha")), "the first custom recipe, A to Z");
        assertEquals(-1, Favorites.landing(Favorites.ALL, f, LIB), "All holds the highlighted recipe already: it stays put");
    }

    @Test void nextWalksTheMarkingOrderAndWraps() {
        List<Integer> f = favs("Acros", "Velvia", "Kodak Gold 200");
        assertEquals(indexOf("Velvia"), Favorites.next(f, indexOf("Acros"), +1));
        assertEquals(indexOf("Acros"), Favorites.next(f, indexOf("Kodak Gold 200"), +1));
        assertEquals(indexOf("Kodak Gold 200"), Favorites.next(f, indexOf("Acros"), -1));
        assertEquals(indexOf("Acros"), Favorites.next(f, indexOf("Provia"), +1), "an unmarked recipe steps onto the first favorite");
        assertEquals(indexOf("Acros"), Favorites.next(f, indexOf("Provia"), -1), "backwards too, rather than into the middle of the list");
        assertEquals(-1, Favorites.next(new ArrayList<Integer>(), 0, +1));
    }

    @Test void theBrowserOpensOnFavoritesWhenTheRecipeIsOne() {
        List<Integer> f = favs("Velvia");
        assertEquals(Favorites.GROUP, Favorites.openingGroup(f, indexOf("Velvia"), LIB, 0));
        assertEquals(Recipes.ALL[indexOf("Acros")].group, Favorites.openingGroup(f, indexOf("Acros"), LIB, Favorites.GROUP));
        assertEquals(0, Favorites.openingGroup(new ArrayList<Integer>(), 0, LIB, Favorites.CUSTOM));
        Library lib = custom("Mine");
        assertEquals(Favorites.CUSTOM, Favorites.openingGroup(f, Library.BASE, lib, Favorites.GROUP), "a custom recipe opens its own group");
        f.add(Library.BASE);
        assertEquals(Favorites.GROUP, Favorites.openingGroup(f, Library.BASE, lib, 0), "unless it is a favorite");
    }

    @Test void onlyANonEmptyGroupHasARecipeColumn() {
        List<Integer> f = favs("Velvia");
        assertTrue(Favorites.hasRecipes(Favorites.GROUP, f, LIB));
        assertFalse(Favorites.hasRecipes(Favorites.GROUP, new ArrayList<Integer>(), LIB));
        assertTrue(Favorites.hasRecipes(0, new ArrayList<Integer>(), LIB), "a brand always has recipes");
    }

    @Test void aGroupListsItsRecipesByPosition() {
        List<Integer> f = favs("Velvia", "Acros");
        assertEquals(2, Favorites.groupCount(Favorites.GROUP, f, LIB));
        assertEquals(Recipes.GROUP_COUNT[0], Favorites.groupCount(0, f, LIB));
        assertEquals(indexOf("Acros"), Favorites.recipeAt(Favorites.GROUP, 1, f, LIB));
        assertEquals(Recipes.GROUP_START[3] + 2, Favorites.recipeAt(3, 2, f, LIB));
        assertEquals(1, Favorites.positionIn(Favorites.GROUP, indexOf("Acros"), f, LIB));
        assertEquals(-1, Favorites.positionIn(Favorites.GROUP, indexOf("Provia"), f, LIB));
        assertEquals(2, Favorites.positionIn(3, Recipes.GROUP_START[3] + 2, f, LIB));
        assertEquals(-1, Favorites.positionIn(3, Recipes.GROUP_START[0], f, LIB), "a recipe of another brand");
    }

    @Test void theCustomGroupListsTheCustomRecipesAToZ() {
        Library lib = custom("Beta", "Alpha");
        List<Integer> f = new ArrayList<Integer>();
        assertEquals(2, Favorites.groupCount(Favorites.CUSTOM, f, lib));
        assertEquals(Library.BASE + 1, Favorites.recipeAt(Favorites.CUSTOM, 1, f, lib));
        assertEquals("Beta", lib.get(Favorites.recipeAt(Favorites.CUSTOM, 1, f, lib)).name, "A to Z");
        assertEquals(1, Favorites.positionIn(Favorites.CUSTOM, Library.BASE + 1, f, lib));
        assertEquals(-1, Favorites.positionIn(Favorites.CUSTOM, indexOf("Velvia"), f, lib));
        assertEquals(-1, Favorites.positionIn(3, Library.BASE, f, lib), "a custom recipe is in no brand");
        assertFalse(Favorites.hasRecipes(Favorites.CUSTOM, f, LIB), "an empty Custom group has no recipe column, like Favorites");
    }

    @Test void theCustomColumnWalksItsRecipesAndWraps() {
        Library lib = custom("A", "B");
        assertEquals(Library.BASE + 1, Favorites.nextCustom(Library.BASE, +1, lib));
        assertEquals(Library.BASE, Favorites.nextCustom(Library.BASE + 1, +1, lib), "past the last one, back to the first");
        assertEquals(Library.BASE + 1, Favorites.nextCustom(Library.BASE, -1, lib));
        assertEquals(Library.BASE, Favorites.nextCustom(indexOf("Velvia"), +1, lib), "from outside the group: the first one");
        assertEquals(-1, Favorites.nextCustom(Library.BASE, +1, LIB), "none at all");
    }

    @Test void anEmptyGroupSaysHowToFillIt() {
        assertEquals("No custom recipes yet", Favorites.emptyTitle(Favorites.CUSTOM, true));
        assertTrue(Favorites.emptyHint(Favorites.CUSTOM).contains("New recipe"), "the app menu row");
        assertTrue(Favorites.emptyTitle(Favorites.CUSTOM, false).startsWith("No memory card"));
        assertEquals("No favorites yet", Favorites.emptyTitle(Favorites.GROUP, true));
    }

    @Test void everyListedRecipeCanBeMarkedAndReadBack() {
        List<Integer> all = new ArrayList<Integer>();
        for (int i = 1; i < Recipes.ALL.length; i++) all.add(i);
        assertEquals(all, Favorites.decode(Favorites.encode(all, LIB, ""), LIB));
        List<Integer> two = Arrays.asList(Recipes.ALL.length - 1, 1);
        assertEquals(two, Favorites.decode(Favorites.encode(two, LIB, ""), LIB));
    }

    @Test void theFactoryLookCanBeAFavorite() {
        List<Integer> favs = new ArrayList<Integer>();
        assertTrue(Favorites.toggle(favs, Recipes.FACTORY), "a recipe like the others");
        assertEquals(Arrays.asList(Recipes.FACTORY), favs);
    }

    // ---- custom recipes as favorites
    @Test void aCustomRecipeIsStoredByPrefixedNameAndMayShareABuiltInName() {
        Library lib = custom("Velvia");                           // a user may call their own recipe after a built-in one
        List<Integer> f = new ArrayList<Integer>(Arrays.asList(indexOf("Velvia"), Library.BASE));
        String stored = Favorites.encode(f, lib, "");
        assertEquals("Velvia|custom:Velvia", stored);
        assertEquals(f, Favorites.decode(stored, lib), "both come back, each to its own recipe");
    }

    @Test void aCustomMarkSurvivesACardThatIsOut() {
        Library in = custom("Mine"), out = LIB;
        String stored = Favorites.encode(Arrays.asList(indexOf("Velvia"), Library.BASE), in, "");
        List<Integer> f = Favorites.decode(stored, out);
        assertEquals(favs("Velvia"), f, "without the card the custom recipe is not shown");
        Favorites.toggle(f, indexOf("Acros"));
        String again = Favorites.encode(f, out, stored);
        assertEquals("Velvia|Acros|custom:Mine", again, "a mark made meanwhile keeps the one it cannot see");
        assertEquals(Arrays.asList(indexOf("Velvia"), indexOf("Acros"), Library.BASE), Favorites.decode(again, in), "and the card brings it back");
    }

    @Test void renameMovesTheMarkAndDeleteForgetsIt() {
        String stored = "Velvia|custom:Old|Acros";
        assertEquals("Velvia|custom:New|Acros", Favorites.renameCustom(stored, "Old", "New"));
        assertEquals("Velvia|Acros", Favorites.forgetCustom(stored, "old"), "names are matched ignoring case, as they are unique that way");
        assertEquals("Velvia|Acros", Favorites.forgetCustom(stored + "|", "Old"));
        assertEquals("", Favorites.renameCustom(null, "a", "b"));
    }

    // ---- what mutation testing found unasserted
    @Test void theCustomColumnStepsBackAndForthOverThree() {
        Library lib = custom("A", "B", "C");
        assertEquals(Library.BASE + 1, Favorites.nextCustom(Library.BASE + 2, -1, lib));
        assertEquals(Library.BASE + 2, Favorites.nextCustom(Library.BASE, -1, lib), "back from the first wraps to the last");
        assertEquals(Library.BASE + 2, Favorites.nextCustom(Library.BASE + 1, +1, lib));
    }

    @Test void theFactoryLookIsStoredAndReadBack() {
        List<Integer> f = new ArrayList<Integer>(Arrays.asList(Recipes.FACTORY));
        String stored = Favorites.encode(f, LIB, "");
        assertEquals(Recipes.ALL[Recipes.FACTORY].name, stored);
        assertEquals(f, Favorites.decode(stored, LIB), "index 0 is a recipe like the others");
    }

    @Test void emptyMarksInTheStoredStringAreDropped() {
        assertEquals("Velvia|Acros|custom:Gone", Favorites.encode(favs("Velvia", "Acros"), LIB, "||custom:Gone|"),
                "an unknown custom mark is kept, the empty ones are not");
    }

    @Test void theLastRecipeOfTheTableIsInItsBrand() {
        int last = Library.BASE - 1, g = Recipes.ALL[last].group;
        assertEquals(Recipes.GROUP_COUNT[g] - 1, Favorites.positionIn(g, last, new ArrayList<Integer>(), LIB));
    }

    // ------------------------------------------------------------ the wheel's loop (issue #155)
    @Test void theBrowserOpensOnTheWheelsGroupWhenItHoldsTheRecipe() {
        List<Integer> f = favs("Velvia");
        int acros = indexOf("Acros"), brand = Recipes.ALL[acros].group;
        assertEquals(Favorites.ALL, Favorites.openingGroup(f, indexOf("Velvia"), LIB, Favorites.ALL), "All holds every recipe");
        assertEquals(brand, Favorites.openingGroup(f, acros, LIB, brand));
        f.add(acros);
        assertEquals(brand, Favorites.openingGroup(f, acros, LIB, brand), "the wheel's brand, though Acros is a favorite too");
        assertEquals(Favorites.GROUP, Favorites.openingGroup(f, acros, LIB, Favorites.GROUP));
    }

    @Test void theAllGroupListsTheTableThenTheCustomRecipes() {
        Library lib = custom("Mine", "Yours");
        List<Integer> f = favs("Velvia");
        assertEquals(Library.BASE + 2, Favorites.groupCount(Favorites.ALL, f, lib));
        assertEquals(Recipes.FACTORY, Favorites.recipeAt(Favorites.ALL, 0, f, lib));
        assertEquals(Library.BASE + 1, Favorites.recipeAt(Favorites.ALL, Library.BASE + 1, f, lib));
        assertEquals(Library.BASE + 1, Favorites.positionIn(Favorites.ALL, Library.BASE + 1, f, lib));
        assertEquals(-1, Favorites.positionIn(Favorites.ALL, Library.BASE + 2, f, lib), "no recipe there");
        assertTrue(Favorites.hasRecipes(Favorites.ALL, f, LIB));
    }

    @Test void theWheelLoopsThroughAllByDefault() {
        List<Integer> f = favs("Velvia");
        assertEquals(1, Favorites.wheel(Favorites.ALL, 0, +1, f, LIB));
        assertEquals(Library.BASE - 1, Favorites.wheel(Favorites.ALL, 0, -1, f, LIB), "wrapping");
    }

    @Test void theWheelLoopsThroughTheFavoritesOnly() {
        List<Integer> f = favs("Acros", "Velvia", "Kodak Gold 200");
        assertEquals(indexOf("Velvia"), Favorites.wheel(Favorites.GROUP, indexOf("Acros"), +1, f, LIB));
        assertEquals(indexOf("Acros"), Favorites.wheel(Favorites.GROUP, indexOf("Kodak Gold 200"), +1, f, LIB), "wrapping");
        assertEquals(indexOf("Kodak Gold 200"), Favorites.wheel(Favorites.GROUP, indexOf("Acros"), -1, f, LIB));
        assertEquals(indexOf("Acros"), Favorites.wheel(Favorites.GROUP, indexOf("Provia"), +1, f, LIB), "from an unmarked one: the first");
    }

    @Test void theWheelLoopsThroughABrandOnly() {
        int g = 3, start = Recipes.GROUP_START[g], n = Recipes.GROUP_COUNT[g];
        List<Integer> f = new ArrayList<Integer>();
        assertEquals(start + 1, Favorites.wheel(g, start, +1, f, LIB));
        assertEquals(start + n - 1, Favorites.wheel(g, start, -1, f, LIB), "wrapping inside the brand");
        assertEquals(start, Favorites.wheel(g, start + n - 1, +1, f, LIB));
        assertEquals(start, Favorites.wheel(g, Recipes.GROUP_START[0], +1, f, LIB), "from another brand: the brand's first");
        assertEquals(start, Favorites.wheel(g, Library.BASE, +1, f, custom("Mine")), "from a custom recipe too");
    }

    @Test void theWheelLoopsThroughTheCustomRecipesOnly() {
        Library lib = custom("Alpha", "Beta");
        List<Integer> f = new ArrayList<Integer>();
        assertEquals(Library.BASE + 1, Favorites.wheel(Favorites.CUSTOM, Library.BASE, +1, f, lib));
        assertEquals(Library.BASE, Favorites.wheel(Favorites.CUSTOM, Library.BASE + 1, +1, f, lib), "wrapping");
    }

    @Test void anEmptyLoopFallsBackToAll() {
        assertEquals(1, Favorites.wheel(Favorites.GROUP, 0, +1, new ArrayList<Integer>(), LIB), "the last favorite unmarked");
        assertEquals(1, Favorites.wheel(Favorites.CUSTOM, 0, +1, new ArrayList<Integer>(), LIB), "the card out");
    }

    @Test void thePanelCountsTheWheelsLoop() {
        List<Integer> f = favs("Acros", "Velvia");
        int velvia = indexOf("Velvia"), g = Recipes.ALL[velvia].group;
        String brand = Recipes.groupLabel(g).toUpperCase();
        assertEquals(brand + "   " + Recipes.position(velvia), Favorites.wheelCount(Favorites.ALL, velvia, f, LIB), "as before");
        assertEquals("FAVORITES   2 / 2", Favorites.wheelCount(Favorites.GROUP, velvia, f, LIB));
        assertEquals("2 / 2", Favorites.wheelPosition(Favorites.GROUP, velvia, f, LIB));
        assertEquals(brand + "   " + (velvia - Recipes.GROUP_START[g] + 1) + " / " + Recipes.GROUP_COUNT[g],
                Favorites.wheelCount(g, velvia, f, LIB));
        int provia = indexOf("Provia");
        assertEquals(Recipes.groupLabel(Recipes.ALL[provia].group).toUpperCase() + "   " + Recipes.position(provia),
                Favorites.wheelCount(Favorites.GROUP, provia, f, LIB), "outside the loop: where it sits among them all");
        assertEquals(Recipes.position(provia), Favorites.wheelPosition(Favorites.GROUP, provia, f, LIB));
    }

    @Test void theWheelsGroupIsStoredByName() {
        for (int g = 0; g < Recipes.GROUPS.length; g++) assertEquals(g, Favorites.wheelGroup(Favorites.wheelKey(g)));
        for (int g : new int[] { Favorites.ALL, Favorites.GROUP, Favorites.CUSTOM })
            assertEquals(g, Favorites.wheelGroup(Favorites.wheelKey(g)));
        assertEquals("Sony", Favorites.wheelKey(0));
        assertEquals(Favorites.ALL, Favorites.wheelGroup(null), "nothing stored: everything, as before");
        assertEquals(Favorites.ALL, Favorites.wheelGroup("A brand long gone"));
    }
}
