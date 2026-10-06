package com.voxivoid.recipelab;

import java.util.ArrayList;
import java.util.List;

/**
 * The favourites list and the browser's group order, without the camera: which recipes are marked, in the order
 * they were marked, how the list is kept in the app's preferences, and how the browser walks it — the Favourites
 * group, then the Custom group (the custom recipes, A to Z), then the brands.
 *
 * A favourite is remembered by recipe <em>name</em>, not index, so the marks survive a build that inserts a recipe
 * in the middle of the table; a built-in name the table no longer has is dropped on load. A custom recipe is stored as
 * {@link #CUSTOM_PREFIX} + its name, so it can share a name with a built-in one. Its mark outlives a card that is not
 * in the camera: {@link #encode} keeps the custom names it cannot see, and only deleting the recipe forgets it. The list
 * lives in the app's own storage (SharedPreferences), not in the camera settings store, so it goes with an uninstall.
 *
 * MainActivity owns the list itself; this class decides. No android.* import may appear here (tools/test.sh).
 */
final class Favourites {
    private Favourites() {}

    /** the browser group that lists the favourites; brands are 0..GROUPS.length-1 */
    static final int GROUP = -1;
    /** the browser group that lists the custom recipes */
    static final int CUSTOM = Recipes.CUSTOM;
    /** separates names in the stored string; no recipe name contains it (RecipesTest, CustomRecipes.nameChar) */
    static final String SEP = "|";
    /** marks a custom recipe's name in the stored string */
    static final String CUSTOM_PREFIX = "custom:";
    /** the right column of an empty Favourites or Custom group; Custom without a card says so */
    static String emptyTitle(int group, boolean card) {
        return group != CUSTOM ? Lang.t("favourite_empty_title") : card ? Lang.t("custom_empty_title") : Lang.t("custom_no_card");
    }
    static String emptyHint(int group) { return Lang.t(group == CUSTOM ? "custom_empty_hint" : "favourite_empty_hint"); }

    // ------------------------------------------------------------ storage
    /** how a recipe is kept in the stored string */
    static String key(int recipe, Library lib) {
        Recipes.Recipe r = lib.get(recipe);
        return r.isCustom() ? CUSTOM_PREFIX + r.name : r.name;
    }

    /** the stored string -> recipe indexes in marking order; unknown names and repeats are dropped */
    static List<Integer> decode(String stored, Library lib) {
        List<Integer> favs = new ArrayList<Integer>();
        if (stored == null || stored.isEmpty()) return favs;
        for (String name : stored.split("\\" + SEP)) {
            int i = indexOf(name, lib);
            if (i >= 0 && !favs.contains(i)) favs.add(i);
        }
        return favs;
    }

    /**
     * Recipe indexes -> the stored string. The custom names of {@code previous} that {@code lib} does not hold — their card
     * is out of the camera — follow, so taking the card out never loses a mark.
     */
    static String encode(List<Integer> favs, Library lib, String previous) {
        List<String> keys = new ArrayList<String>();
        for (int i : favs) keys.add(key(i, lib));
        if (previous != null && !previous.isEmpty())
            for (String k : previous.split("\\" + SEP))
                if (k.startsWith(CUSTOM_PREFIX) && indexOf(k, lib) < 0 && !keys.contains(k)) keys.add(k);
        return join(keys);
    }

    /** the stored string after a custom recipe was renamed: its mark moves with it */
    static String renameCustom(String stored, String oldName, String newName) {
        List<String> keys = new ArrayList<String>();
        if (stored != null && !stored.isEmpty())
            for (String k : stored.split("\\" + SEP)) keys.add(k.equalsIgnoreCase(CUSTOM_PREFIX + oldName) ? CUSTOM_PREFIX + newName : k);
        return join(keys);
    }

    /** the stored string after a custom recipe was deleted: its mark goes with it */
    static String forgetCustom(String stored, String name) {
        List<String> keys = new ArrayList<String>();
        if (stored != null && !stored.isEmpty())
            for (String k : stored.split("\\" + SEP)) if (!k.equalsIgnoreCase(CUSTOM_PREFIX + name)) keys.add(k);
        return join(keys);
    }

    private static String join(List<String> keys) {
        StringBuilder s = new StringBuilder();
        for (String k : keys) { if (k.isEmpty()) continue; if (s.length() > 0) s.append(SEP); s.append(k); }
        return s.toString();
    }

    private static int indexOf(String key, Library lib) {
        if (key.startsWith(CUSTOM_PREFIX)) return lib.indexOfCustom(key.substring(CUSTOM_PREFIX.length()));
        for (int i = 0; i < Recipes.ALL.length; i++) if (Recipes.ALL[i].name.equals(key)) return i;
        return -1;
    }

    // ------------------------------------------------------------ marking
    /** marks an unmarked recipe (at the end) or unmarks a marked one; returns whether it is a favourite now */
    static boolean toggle(List<Integer> favs, int recipe) {
        int pos = favs.indexOf(recipe);
        if (pos >= 0) { favs.remove(pos); return false; }
        favs.add(recipe);
        return true;
    }

    /** the favourite to highlight after the one at {@code pos} was removed: the next one, else the new last, -1 when none */
    static int afterRemoval(List<Integer> favs, int pos) {
        if (favs.isEmpty()) return -1;
        return favs.get(Math.min(Math.max(0, pos), favs.size() - 1));
    }

    /** the toast after a toggle */
    static String toggleMessage(String recipeName, boolean on) { return Lang.t(on ? "favourite_added" : "favourite_removed", recipeName); }

    // ------------------------------------------------------------ browser navigation
    /** how many rows the brand column has: Favourites, Custom, the brands */
    static int groupRows() { return Recipes.GROUPS.length + 2; }

    /** the brand column's row for a group: Favourites first, Custom second */
    static int groupRow(int group) { return group == GROUP ? 0 : group == CUSTOM ? 1 : group + 2; }

    /** the group on a row of the brand column */
    static int groupAt(int row) { return row == 0 ? GROUP : row == 1 ? CUSTOM : row - 2; }

    /** the group above / below in the brand column, wrapping */
    static int nextGroup(int group, int dir) {
        int n = groupRows();
        return groupAt((groupRow(group) + n + dir) % n);
    }

    /** the recipe the highlight lands on when the brand column moves onto {@code group}; -1 when that group is empty */
    static int landing(int group, List<Integer> favs, Library lib) {
        if (group == GROUP) return favs.isEmpty() ? -1 : favs.get(0);
        if (group == CUSTOM) return lib.customCount() == 0 ? -1 : Library.BASE;
        return Recipes.GROUP_START[group];
    }

    /** the favourite after / before {@code recipe} in marking order, wrapping; the first one when it is not marked; -1 when none */
    static int next(List<Integer> favs, int recipe, int dir) {
        if (favs.isEmpty()) return -1;
        int pos = favs.indexOf(recipe);
        if (pos < 0) return favs.get(0);
        return favs.get((pos + favs.size() + dir) % favs.size());
    }

    /** the custom recipe after / before {@code recipe}, A to Z, wrapping; the first one from anywhere else; -1 when none */
    static int nextCustom(int recipe, int dir, Library lib) {
        int n = lib.customCount();
        if (n == 0) return -1;
        if (!lib.isCustom(recipe)) return Library.BASE;
        return Library.BASE + (recipe - Library.BASE + n + dir) % n;
    }

    /** the group the browser opens on: Favourites when the recipe is one, else Custom or its brand */
    static int openingGroup(List<Integer> favs, int recipe, Library lib) {
        if (favs.contains(recipe)) return GROUP;
        return lib.isCustom(recipe) ? CUSTOM : Recipes.ALL[recipe].group;
    }

    /** the group's name as the brand column shows it, in the display language */
    static String groupName(int group) { return group == GROUP ? Lang.t("group_favourites") : Recipes.groupLabel(group); }

    /** how many recipes a group lists — what the brand column counts */
    static int groupCount(int group, List<Integer> favs, Library lib) {
        return group == GROUP ? favs.size() : group == CUSTOM ? lib.customCount() : Recipes.GROUP_COUNT[group];
    }

    /** whether a group has anything in its recipe column — an empty Favourites or Custom list has not */
    static boolean hasRecipes(int group, List<Integer> favs, Library lib) { return groupCount(group, favs, lib) > 0; }

    /** the recipe on row k of a group's column */
    static int recipeAt(int group, int k, List<Integer> favs, Library lib) {
        if (group == GROUP) return favs.get(k);
        if (group == CUSTOM) return Library.BASE + k;
        return Recipes.GROUP_START[group] + k;
    }

    /** the row of a recipe inside a group's column, -1 when it is not there */
    static int positionIn(int group, int sel, List<Integer> favs, Library lib) {
        if (group == GROUP) return favs.indexOf(sel);
        if (group == CUSTOM) return lib.isCustom(sel) ? sel - Library.BASE : -1;
        return sel >= 0 && sel < Library.BASE && Recipes.ALL[sel].group == group ? sel - Recipes.GROUP_START[group] : -1;
    }
}
