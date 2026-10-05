package com.voxivoid.recipelab;

import static com.voxivoid.recipelab.Fixtures.indexOf;
import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class RecentRecipesTest {
    @Test void successfulPicksAreNewestFirstWithoutDuplicates() {
        List<Integer> recent = new ArrayList<Integer>();
        int velvia = indexOf("Velvia"), acros = indexOf("Acros");
        RecentRecipes.record(recent, velvia);
        RecentRecipes.record(recent, acros);
        RecentRecipes.record(recent, velvia);
        assertEquals(Arrays.asList(velvia, acros), recent);
        RecentRecipes.record(recent, Recipes.FACTORY);
        assertEquals(Arrays.asList(velvia, acros), recent);
    }

    @Test void historyKeepsTenAndStoresNames() {
        List<Integer> recent = new ArrayList<Integer>();
        for (int i = 1; i <= RecentRecipes.LIMIT + 1; i++) RecentRecipes.record(recent, i);
        assertEquals(RecentRecipes.LIMIT, recent.size());
        assertEquals(Integer.valueOf(RecentRecipes.LIMIT + 1), recent.get(0));
        assertFalse(recent.contains(1));
        assertEquals(recent, RecentRecipes.decode(RecentRecipes.encode(recent)));
    }

    @Test void missingRenamedRepeatedAndFactoryNamesAreDiscarded() {
        String stored = "Velvia|missing|Velvia|" + Recipes.ALL[Recipes.FACTORY].name + "|Acros";
        assertEquals(Arrays.asList(indexOf("Velvia"), indexOf("Acros")), RecentRecipes.decode(stored));
        assertTrue(RecentRecipes.decode(null).isEmpty());
        assertTrue(RecentRecipes.decode("").isEmpty());
    }

    @Test void recipeNamesCannotBreakHistoryFormat() {
        for (Recipes.Recipe recipe : Recipes.ALL) assertFalse(recipe.name.contains(RecentRecipes.SEP));
    }
}
