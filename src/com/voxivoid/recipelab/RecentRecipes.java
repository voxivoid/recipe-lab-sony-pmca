package com.voxivoid.recipelab;

import java.util.ArrayList;
import java.util.List;

/** Recipes successfully stored on the camera, newest first. Previews do not change this list. */
final class RecentRecipes {
    private RecentRecipes() {}

    static final int GROUP = -2;
    static final int LIMIT = 10;
    static final String SEP = "|";

    /** Stored by name so inserting recipes into the table does not change the history. */
    static List<Integer> decode(String stored) {
        List<Integer> recent = new ArrayList<Integer>();
        if (stored == null || stored.isEmpty()) return recent;
        for (String name : stored.split("\\|")) {
            for (int i = 0; i < Recipes.ALL.length; i++) {
                if (i != Recipes.FACTORY && Recipes.ALL[i].name.equals(name) && !recent.contains(i)) {
                    recent.add(i);
                    break;
                }
            }
            if (recent.size() == LIMIT) break;
        }
        return recent;
    }

    static String encode(List<Integer> recent) {
        StringBuilder stored = new StringBuilder();
        for (int i : recent) {
            if (stored.length() > 0) stored.append(SEP);
            stored.append(Recipes.ALL[i].name);
        }
        return stored.toString();
    }

    /** Move a successful pick to the front, discarding the oldest when full. */
    static void record(List<Integer> recent, int recipe) {
        if (recipe == Recipes.FACTORY) return;
        recent.remove(Integer.valueOf(recipe));
        recent.add(0, recipe);
        if (recent.size() > LIMIT) recent.remove(recent.size() - 1);
    }

    static String emptyTitle() { return Lang.t("recent_empty_title"); }
    static String emptyHint() { return Lang.t("recent_empty_hint"); }
}
