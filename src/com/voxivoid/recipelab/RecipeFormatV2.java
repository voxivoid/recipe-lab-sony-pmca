package com.voxivoid.recipelab;

import java.util.Map;

/**
 * Format 2 of the custom recipe file: format 1's keys, plus {@code family}, required — the cameras the values are for.
 * Recipe Lab's bodies are all {@link #FAMILY}; a file for another family (a Canon's, written by a later Recipe Lab
 * Studio) is skipped rather than read as Sony's. Recipe Lab Studio reads and writes the same files (its
 * {@code RecipeYaml}), byte for byte.
 *
 * Format 1 files stay readable (their {@link RecipeFormatV1} reader): they are all this family's. Apps up to v1.11.1
 * skip format 2 files as made by a newer version; protocol 5 is how the phone tells a camera that reads them.
 *
 * Once released, this class does not change. No android.* import may appear here (tools/test.sh).
 */
final class RecipeFormatV2 implements RecipeFormat {
    /** The family of every body Recipe Lab runs on: Creative Style, Picture Effect, DRO, white balance fine tune. */
    static final String FAMILY = "sony-creative-style";

    private static final RecipeFormatV1 V1 = new RecipeFormatV1();

    public int version() { return 2; }

    public String write(Recipes.Recipe r, String madeOn) {
        // format 1's file with its format line swapped for this version's and the family below it
        String v1 = V1.write(r, madeOn), from = "\nformat: 1\n";
        int at = v1.indexOf(from);
        if (at < 0) throw new IllegalStateException("format 1 wrote no format line");
        String family = "family: " + FAMILY;
        while (family.length() < 28) family += ' ';
        return v1.substring(0, at) + "\nformat: 2\n" + family + " # the cameras it is for\n" + v1.substring(at + from.length());
    }

    public CustomRecipes.Parsed read(Map<String, String> kv, String fileName) {
        String family = kv.get("family");
        if (family == null || family.isEmpty()) return CustomRecipes.Parsed.fail(Lang.t("custom_no_family"));
        if (!family.equals(FAMILY)) return CustomRecipes.Parsed.fail(Lang.t("custom_other_family", family));
        return V1.read(kv, fileName);
    }
}
