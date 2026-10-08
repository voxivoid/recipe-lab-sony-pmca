package com.voxivoid.recipelab;

/**
 * Which half of the A/B preview is on screen. The camera half uses the settings most recently read from the store;
 * the recipe half uses the staged recipe (including edits). This class owns no arrays and never changes them.
 *
 * No android.* import may appear here: tools/test.sh exercises the state changes without a camera.
 */
final class ComparePreview {
    private boolean camera;

    boolean camera() { return camera; }
    boolean canPick() { return !camera; }
    int[] values(int[] stored, int[] recipe) { return camera ? stored : recipe; }
    void showCamera() { camera = true; }
    void showRecipe() { camera = false; }
}
