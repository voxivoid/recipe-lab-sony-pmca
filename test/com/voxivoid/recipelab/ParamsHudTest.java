package com.voxivoid.recipelab;

import static com.voxivoid.recipelab.Fixtures.*;
import static com.voxivoid.recipelab.Params.*;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/** The overlay text: the meta line under the recipe name, the minimal pill, the quality prompt. */
class ParamsHudTest {

    @Test void theMetaLineIsEmptyWhenTheChipsSayItAll() {
        int[] cur = factoryRows();
        for (String r : new String[] { "Kodak Portra 400", "Velvia", "Kodak Vision3 500T (daylight)", "Sony SH (soft high-key)" })
            assertEquals("", metaLine(staged(recipe(r), cur, Q_FINE), null), r);
    }

    @Test void theMetaLineWarnsOfAnEffectRawWouldDrop() {
        int[] cur = factoryRows(); cur[R_QUAL] = Q_RAW;
        int[] e = staged(recipe("GR Retro"), cur, Q_RAW);
        assertEquals("", metaLine(e, null), "the recipe switches to JPEG on its own");
        e[R_QUAL] = Q_RAW;   // the user forced RAW back on
        assertEquals("RAW is on: effect ignored", metaLine(e, null));
    }

    @Test void theMetaLineShowsThePreviewError() {
        int[] e = factoryRows();
        assertEquals("no live preview: CameraEx not found", metaLine(e, "CameraEx not found"));
        e[R_PE] = Recipes.PE_RETRO; e[R_QUAL] = Q_RAW;
        assertEquals("RAW is on: effect ignored  ·  no live preview: x", metaLine(e, "x"));
    }

    @Test void miniPill() {
        int[] cur = factoryRows();
        int i = indexOf("Kodak Portra 400");
        int[] e = staged(Recipes.ALL[i], cur, Q_FINE);
        String pos = Recipes.position(i);
        assertEquals("Kodak Portra 400   " + (i + 1) + " / 77   · preview", miniLine(Recipes.ALL[i], pos, cur, e, true), "counted as the panel counts");
        assertEquals("Kodak Portra 400   " + (i + 1) + " / 77   · active", miniLine(Recipes.ALL[i], pos, cur, e, false));
        i = indexOf("GR Retro"); cur[R_QUAL] = Q_RAW;
        e = staged(Recipes.ALL[i], cur, Q_RAW);
        assertEquals("JPEG only  GR Retro   " + (i + 1) + " / 77   · preview   · quality → JPG Fine", miniLine(Recipes.ALL[i], Recipes.position(i), cur, e, true));
    }

    @Test void miniPillOfACustomRecipe() {
        int[] cur = factoryRows();
        Recipes.Recipe mine = CustomRecipes.recipe("Golden Hour", cur);
        assertEquals("Golden Hour   2 / 5   · active", miniLine(mine, "2 / 5", cur, cur, false));
    }

    @Test void qualityPromptExplainsWhyTheQualityMoves() {
        int[] cur = factoryRows(); cur[R_QUAL] = Q_RAW;
        int[] e = staged(recipe("GR Retro"), cur, Q_RAW);
        assertArrayEquals(new String[] { "Quality: RAW  →  JPG Fine", "JPEG is needed to apply this recipe." }, qualityPrompt(cur, e));
        e = cur.clone(); e[R_QUAL] = Q_STD;
        assertArrayEquals(new String[] { "Quality: RAW  →  JPG Std", "Creative Style recipes use the Factory recipe's quality." }, qualityPrompt(cur, e));
    }
}
