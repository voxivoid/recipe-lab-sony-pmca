package com.voxivoid.recipelab;

/** Model-specific UI choices. No camera settings or recipe values are changed here. */
final class CameraUi {
    private CameraUi() {}
    static boolean isA5100(String model) {
        return model != null && ("ILCE-5100".equalsIgnoreCase(model.trim()) || "A5100".equalsIgnoreCase(model.trim()));
    }
    static int initialOverlay(boolean a5100) { return a5100 ? Params.OV_PILL : Params.OV_FULL; }
    static int nextOverlay(boolean a5100, int overlay) {
        if (!a5100) return (overlay + 1) % 3;
        if (overlay == Params.OV_PILL) return Params.OV_FULL;
        if (overlay == Params.OV_FULL) return Params.OV_HIDDEN;
        return Params.OV_PILL;
    }
    static int key(boolean a5100, int scanCode) { return a5100 && scanCode == 515 ? 532 : scanCode; }
    // Focus order for the full A5100 panel: recipe, parameters, browser button.
    static int nextTarget(int target, int direction) { return (target + direction + 3) % 3; }
    static boolean canOpenBrowser(boolean a5100, int overlay, boolean prompt, boolean menu, boolean running) {
        return a5100 && overlay == Params.OV_FULL && !prompt && !menu && !running;
    }
}
