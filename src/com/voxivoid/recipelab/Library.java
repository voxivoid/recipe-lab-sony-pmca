package com.voxivoid.recipelab;

import java.util.ArrayList;
import java.util.List;

/**
 * Every recipe the app can show, under one index: the table's at {@code 0 .. BASE-1} (the factory look at 0), the
 * custom recipes from the memory card after it, A to Z, at {@code BASE ..}. MainActivity's {@code recipe} is such an
 * index. A custom recipe's index moves when one is added, renamed or deleted, so nothing that outlives the list stores
 * one: the preferences keep its name instead.
 *
 * No android.* import may appear here (tools/test.sh).
 */
final class Library {
    /** the first custom recipe's index: the table ends before it */
    static final int BASE = Recipes.ALL.length;

    private final List<CustomRecipes.Entry> custom = new ArrayList<CustomRecipes.Entry>();

    /** replaces the custom recipes, keeping them A to Z */
    void set(List<CustomRecipes.Entry> entries) {
        custom.clear();
        custom.addAll(entries);
        CustomRecipes.sort(custom);
    }

    int customCount() { return custom.size(); }

    /** whether an index is a recipe at all */
    boolean valid(int i) { return i >= 0 && i < BASE + custom.size(); }

    boolean isCustom(int i) { return i >= BASE && i < BASE + custom.size(); }

    Recipes.Recipe get(int i) { return isCustom(i) ? custom.get(i - BASE).recipe : Recipes.ALL[i]; }

    /** a custom recipe with the file it is kept in */
    CustomRecipes.Entry entry(int i) { return custom.get(i - BASE); }

    /** the index of the custom recipe called {@code name}, ignoring case as the names do; -1 when there is none */
    int indexOfCustom(String name) {
        if (name == null) return -1;
        for (int k = 0; k < custom.size(); k++) if (custom.get(k).recipe.name.equalsIgnoreCase(name)) return BASE + k;
        return -1;
    }

    /** the custom recipes' names, A to Z */
    List<String> customNames() {
        List<String> n = new ArrayList<String>();
        for (CustomRecipes.Entry e : custom) n.add(e.recipe.name);
        return n;
    }

    /**
     * The recipe after / before i, as the wheel walks them: the table's, then the custom ones, wrapping. They are the
     * indexes 0 .. BASE + customCount - 1, with no gap where the table ends; from an index that is no recipe, the ends.
     */
    int next(int i, int dir) {
        int listed = Recipes.LISTED + custom.size();
        if (!valid(i)) return dir > 0 ? 0 : listed - 1;
        return (i + listed + dir) % listed;
    }

    /** where a recipe sits, as the panel shows it: "12 / 77" in the table, "2 / 5" among the custom ones */
    String position(int i) {
        return isCustom(i) ? (i - BASE + 1) + " / " + custom.size() : Recipes.position(i);
    }
}
