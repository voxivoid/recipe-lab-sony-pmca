package com.voxivoid.recipelab;

import java.util.Map;

/**
 * One version of the custom recipe file format: its number, how to read a file of that version into a recipe, and how
 * to write a recipe as one. {@link RecipeFormats} holds every version the app knows; a file is read by the version its
 * {@code format} line names, and the app writes with the newest.
 *
 * A reader gets the file already split into {@code key = value} pairs (the envelope every version shares, see
 * {@link RecipeFormats#keys}) and must check every value against what the settings store takes
 * ({@link CustomRecipes#problem}): a file is untrusted input that ends up in the camera. A writer's output must read
 * back through its own reader to the same recipe (RecipeFormatsTest).
 *
 * No android.* import may appear here (tools/test.sh).
 */
interface RecipeFormat {
    /** the number a file of this version carries in its {@code format} line */
    int version();

    /** a file of this version, as key → value: the recipe, or why there is none. {@code fileName} names a nameless one */
    CustomRecipes.Parsed read(Map<String, String> keys, String fileName);

    /** a recipe as a file of this version; {@code madeOn} is the camera model it was saved on, null when unknown */
    String write(Recipes.Recipe r, String madeOn);
}
