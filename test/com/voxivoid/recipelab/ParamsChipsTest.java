package com.voxivoid.recipelab;

import static com.voxivoid.recipelab.Fixtures.*;
import static com.voxivoid.recipelab.Params.*;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/** The chip strip: which chips show, how a value steps, where LEFT/RIGHT and UP/DOWN land, what a chip says. */
class ParamsChipsTest {

    @Test void creativeStyleChipsHideUnderAnEffect() {
        int[] e = factoryRows();
        for (int r : new int[] { R_STYLE, R_SAT, R_CON, R_SHARP }) assertTrue(rowVisible(r, e), ROW_NAME[r]);
        assertFalse(rowVisible(R_PP, e), "the Picture Profile row has no chip");
        assertFalse(rowVisible(R_SUB, e));
        assertFalse(rowVisible(R_PE, e), "no effect: LOOK says style, and EFFECT hides");
        assertTrue(rowVisible(R_LOOK, e));
        e[R_PE] = Recipes.PE_RETRO;
        for (int r : new int[] { R_STYLE, R_SAT, R_CON, R_SHARP }) assertFalse(rowVisible(r, e), ROW_NAME[r]);
        assertFalse(rowVisible(R_SUB, e), "Retro has no sub-parameter");
        e[R_PE] = Recipes.PE_TOY;
        assertTrue(rowVisible(R_SUB, e));
        for (int r : new int[] { R_LOOK, R_PE, R_WBMODE, R_AB, R_GM, R_EV, R_DRO, R_QUAL }) assertTrue(rowVisible(r, e), ROW_NAME[r]);
    }

    @Test void kelvinChipOnlyInColourTemperatureMode() {
        int[] e = factoryRows();
        assertFalse(rowVisible(R_KELVIN, e));
        e[R_WBMODE] = WB_KELVIN;
        assertTrue(rowVisible(R_KELVIN, e));
    }

    @Test void choicesWrapNumbersClamp() {
        for (int r : new int[] { R_STYLE, R_PE, R_SUB, R_QUAL, R_DRO, R_WBMODE }) assertTrue(isChoice(r), ROW_NAME[r]);
        for (int r : new int[] { R_SAT, R_CON, R_SHARP, R_KELVIN, R_AB, R_GM, R_EV }) assertFalse(isChoice(r), ROW_NAME[r]);

        int[] e = factoryRows();
        e[R_STYLE] = Recipes.SEPIA; step(e, R_STYLE, +1, Q_FINE); assertEquals(1, e[R_STYLE], "past Sepia comes Standard");
        step(e, R_STYLE, -1, Q_FINE); assertEquals(Recipes.SEPIA, e[R_STYLE]);
        e[R_STYLE] = Recipes.AUTUMN; step(e, R_STYLE, +1, Q_FINE); assertEquals(Recipes.SEPIA, e[R_STYLE], "13 is unidentified and skipped");
        step(e, R_STYLE, -1, Q_FINE); assertEquals(Recipes.AUTUMN, e[R_STYLE], "and skipped backwards too");
        e[R_DRO] = Recipes.DRO_AUTO; step(e, R_DRO, +1, Q_FINE); assertEquals(Recipes.DRO_OFF, e[R_DRO]);
        e[R_QUAL] = Q_STD; step(e, R_QUAL, +1, Q_FINE); assertEquals(Q_RAWJPG, e[R_QUAL], "RAW alone is not offered (#100)");
        e[R_QUAL] = Q_RAWJPG; step(e, R_QUAL, -1, Q_FINE); assertEquals(Q_STD, e[R_QUAL], "and not offered going back either");

        e[R_SAT] = 3; step(e, R_SAT, +1, Q_FINE); assertEquals(3, e[R_SAT], "saturation stops at the menu's +3");
        e[R_SAT] = -3; step(e, R_SAT, -1, Q_FINE); assertEquals(-3, e[R_SAT]);
        e[R_CON] = 3; step(e, R_CON, +1, Q_FINE); assertEquals(3, e[R_CON]);
        e[R_SHARP] = -3; step(e, R_SHARP, -1, Q_FINE); assertEquals(-3, e[R_SHARP]);
        e[R_SAT] = -6; step(e, R_SAT, +1, Q_FINE); assertEquals(-3, e[R_SAT], "a value read from the store outside the range comes back into it");
        e[R_EV] = 0; step(e, R_EV, -1, Q_FINE); assertEquals(-1, e[R_EV]);
        e[R_KELVIN] = 99; step(e, R_KELVIN, +1, Q_FINE); assertEquals(99, e[R_KELVIN]);
        e[R_KELVIN] = 25; step(e, R_KELVIN, -1, Q_FINE); assertEquals(25, e[R_KELVIN]);
        e[R_AB] = 7; step(e, R_AB, +1, Q_FINE); assertEquals(7, e[R_AB]);
    }

    @Test void whiteBalanceTogglesBetweenAutoAndKelvin() {
        int[] e = factoryRows();
        assertFalse(step(e, R_WBMODE, +1, Q_FINE)); assertEquals(WB_KELVIN, e[R_WBMODE]);
        step(e, R_WBMODE, +1, Q_FINE); assertEquals(WB_AUTO, e[R_WBMODE]);
        step(e, R_WBMODE, -1, Q_FINE); assertEquals(WB_KELVIN, e[R_WBMODE]);
        e[R_WBMODE] = 5; step(e, R_WBMODE, +1, Q_FINE); assertEquals(WB_KELVIN, e[R_WBMODE], "any other stored mode goes to kelvin first");
    }

    @Test void subCyclesTheStagedEffectsValues() {
        int[] e = factoryRows(); e[R_PE] = Recipes.PE_HIGHKEY;   // blue, pink, green
        step(e, R_SUB, +1, Q_FINE); assertEquals(1, e[R_SUB]);
        step(e, R_SUB, +1, Q_FINE); assertEquals(2, e[R_SUB]);
        step(e, R_SUB, +1, Q_FINE); assertEquals(0, e[R_SUB]);
        step(e, R_SUB, -1, Q_FINE); assertEquals(2, e[R_SUB]);
        e[R_PE] = Recipes.PE_RETRO; e[R_SUB] = 0;
        step(e, R_SUB, +1, Q_FINE); assertEquals(0, e[R_SUB], "no values to cycle");
    }

    @Test void changingTheEffectResetsSubAndRederivesQuality() {
        int[] e = factoryRows(); e[R_PE] = Recipes.PE_HIGHKEY; e[R_SUB] = 2; e[R_QUAL] = Q_RAW;
        assertFalse(step(e, R_PE, -1, Q_RAW));
        assertEquals(Recipes.PE_RETRO, e[R_PE]);
        assertEquals(0, e[R_SUB]);
        assertEquals(Q_FINE, e[R_QUAL], "an effect cannot shoot RAW");

        e[R_PE] = Recipes.PE_TOY; e[R_QUAL] = Q_FINE;
        step(e, R_PE, -1, Q_RAW);
        assertEquals(Recipes.PE_KEYS.length - 1, e[R_PE], "the effects wrap past off: turning one off is LOOK's");
        step(e, R_PE, +1, Q_STD);
        assertEquals(Recipes.PE_TOY, e[R_PE]);
        assertEquals(Q_STD, e[R_QUAL], "a JPEG base is kept as is");
    }

    @Test void lookTogglesStyleAndEffectAndComesBackToTheEffectLeft() {
        int[] e = factoryRows(); e[R_QUAL] = Q_RAWJPG;
        assertEquals("style", fmt(R_LOOK, e[R_LOOK], e));
        assertFalse(step(e, R_LOOK, +1, Q_RAWJPG));
        assertEquals(Recipes.PE_TOY, e[R_PE], "the first effect when none was left");
        assertEquals(Q_FINE, e[R_QUAL], "an effect cannot shoot RAW");
        assertEquals("effect", fmt(R_LOOK, e[R_LOOK], e));
        step(e, R_PE, +1, Q_RAWJPG); step(e, R_PE, +1, Q_RAWJPG); e[R_SUB] = 1;
        step(e, R_LOOK, -1, Q_RAWJPG);
        assertEquals(Recipes.PE_OFF, e[R_PE]);
        assertEquals(0, e[R_SUB]);
        assertEquals(Q_RAWJPG, e[R_QUAL], "back to the recipe's quality once the effect is off");
        step(e, R_LOOK, +1, Q_RAWJPG);
        assertEquals(3, e[R_PE], "Effect → Style → Effect lands on the effect left");
    }

    @Test void lookIsShownChangedButNeverWrittenOrCounted() {
        int[] cur = factoryRows(); cur[R_QUAL] = Q_FINE; int[] e = cur.clone();
        step(e, R_LOOK, +1, Q_FINE);
        assertTrue(rowDirty(R_LOOK, cur, e, 0));
        assertEquals(1, dirtyRows(cur, e, 0), "EFFECT is the one change; LOOK only shows it");
        for (Write w : writes(cur, e, 0)) assertNotEquals(0, w.id, "no write for LOOK");
        step(e, R_LOOK, +1, Q_FINE);
        assertFalse(rowDirty(R_LOOK, cur, e, 0));
        assertTrue(sameLook(cur, e), "the effect LOOK remembers is not part of the look");
    }

    @Test void separatorsSetTheGroupsApart() {
        int[] e = factoryRows();
        assertFalse(separatorBefore(R_QUAL, e), "nothing before the first chip");
        assertTrue(separatorBefore(R_LOOK, e));
        assertFalse(separatorBefore(R_STYLE, e));
        assertTrue(separatorBefore(R_WBMODE, e));
        assertFalse(separatorBefore(R_AB, e));
        assertTrue(separatorBefore(R_EV, e));
        assertTrue(separatorBefore(R_GAMMA, e));
        assertFalse(separatorBefore(R_KELVIN, e), "hidden in AWB");
        e[R_PE] = Recipes.PE_RETRO;
        assertFalse(separatorBefore(R_STYLE, e), "hidden under an effect");
        assertFalse(separatorBefore(R_PE, e));
        for (int r : ORDER) assertEquals(chipGroup(r) == G_PROFILE, ispRow(r), ROW_NAME[r]);
    }

    @Test void onlyAQualityStepReportsAChoice() {
        int[] e = factoryRows();
        assertTrue(step(e, R_QUAL, +1, Q_FINE));
        assertFalse(step(e, R_SAT, +1, Q_FINE));
        assertFalse(step(e, R_PE, +1, Q_FINE));
        assertFalse(step(e, R_SUB, +1, Q_FINE));
    }

    @Test void leftRightSkipHiddenChipsAndWrap() {
        int[] e = factoryRows();
        assertEquals(R_LOOK, nextChip(R_QUAL, +1, e));
        assertEquals(R_STYLE, nextChip(R_LOOK, +1, e));
        assertEquals(R_GAMMA, nextChip(R_QUAL, -1, e), "GAMMA closes the strip; with it off the profile chips hide");
        assertEquals(R_GAMMA, nextChip(R_DRO, +1, e));
        assertEquals(R_QUAL, nextChip(R_GAMMA, +1, e));
        assertEquals(R_AB, nextChip(R_WBMODE, +1, e), "kelvin hidden in AWB");
        e[R_WBMODE] = WB_KELVIN;
        assertEquals(R_KELVIN, nextChip(R_WBMODE, +1, e));
        e[R_PE] = Recipes.PE_HIGHKEY;
        assertEquals(R_PE, nextChip(R_LOOK, +1, e), "style chips hidden under an effect");
        assertEquals(R_SUB, nextChip(R_PE, +1, e));
        assertEquals(R_LOOK, nextChip(R_PE, -1, e));
        e[R_PE] = Recipes.PE_RETRO;
        assertEquals(R_WBMODE, nextChip(R_PE, +1, e), "no SUB for Retro");
    }

    @Test void fromTheRecipeLineRightGoesToTheSecondChipLikeTheOriginal() {
        // row 0 is not in ORDER; it counts as position 0 (QUALITY), so RIGHT from it lands after QUALITY
        int[] e = factoryRows();
        assertEquals(R_LOOK, nextChip(R_RECIPE, +1, e));
        assertEquals(R_GAMMA, nextChip(R_RECIPE, -1, e));
    }

    @Test void downFromTheRecipeLineReturnsToTheLastChipIfStillVisible() {
        int[] e = factoryRows();
        assertEquals(R_QUAL, enterChips(R_RECIPE, e), "first time: the first chip");
        assertEquals(R_SAT, enterChips(R_SAT, e));
        e[R_PE] = Recipes.PE_TOY;
        assertEquals(R_QUAL, enterChips(R_SAT, e), "SAT hidden now, so the first visible chip");
        assertEquals(R_KELVIN == enterChips(R_KELVIN, e) ? R_KELVIN : R_QUAL, enterChips(R_KELVIN, e));
        e[R_WBMODE] = WB_KELVIN;
        assertEquals(R_KELVIN, enterChips(R_KELVIN, e));
    }

    @Test void chipTextPerRow() {
        int[] e = factoryRows();
        assertEquals("Standard", fmt(R_STYLE, Recipes.STD, e));
        assertEquals("?0", fmt(R_STYLE, 0, e));
        assertEquals("+3", fmt(R_SAT, 3, e));
        assertEquals("-2", fmt(R_CON, -2, e));
        assertEquals("0", fmt(R_SHARP, 0, e));
        assertEquals("off", fmt(R_PP, 0, e));
        assertEquals("on", fmt(R_PP, 1, e));
        assertEquals("auto", fmt(R_WBMODE, WB_AUTO, e));
        assertEquals("kelvin", fmt(R_WBMODE, WB_KELVIN, e));
        assertEquals("3", fmt(R_WBMODE, 3, e));
        assertEquals("-", fmt(R_KELVIN, 55, e), "meaningless in AWB");
        e[R_WBMODE] = WB_KELVIN;
        assertEquals("5500K", fmt(R_KELVIN, 55, e));
        assertEquals("0", fmt(R_AB, 0, e)); assertEquals("A2", fmt(R_AB, 2, e)); assertEquals("B3", fmt(R_AB, -3, e));
        assertEquals("0", fmt(R_GM, 0, e)); assertEquals("G4", fmt(R_GM, 4, e)); assertEquals("M1", fmt(R_GM, -1, e));
        assertEquals("off", fmt(R_PE, 0, e)); assertEquals("Retro", fmt(R_PE, Recipes.PE_RETRO, e)); assertEquals("?14", fmt(R_PE, 14, e));
        assertEquals("-", fmt(R_SUB, 0, e), "no sub for the staged effect");
        e[R_PE] = Recipes.PE_HIGHKEY;
        assertEquals("pink", fmt(R_SUB, 1, e));
        assertEquals("?7", fmt(R_SUB, 7, e));
        assertEquals("+0.7", fmt(R_EV, 2, e));
        assertEquals("Lv2", fmt(R_DRO, 2, e));
        assertEquals("RAW+JPG", fmt(R_QUAL, Q_RAWJPG, e));
        assertEquals("?4", fmt(R_QUAL, 4, e));
    }

    // ------------------------------------------------------------ the picture profile rows
    @Test void theProfileChipsShowOnlyWithAProfileAndTheirOwnOnlyWhenTheyDoSomething() {
        int[] e = factoryRows();
        assertTrue(rowVisible(R_GAMMA, e));
        for (int r = R_GAMUT; r <= R_SCURVE; r++) assertFalse(rowVisible(r, e), ROW_NAME[r] + " with GAMMA off");
        e[R_GAMMA] = IspProfiles.SLOG3;
        for (int r = R_GAMUT; r <= R_SCURVE; r++) assertEquals(r != R_BRANGE && r != R_KSLOPE, rowVisible(r, e), ROW_NAME[r]);
        e[R_BGAMMA] = 2;
        assertTrue(rowVisible(R_BRANGE, e), "the range with black gamma");
        e[R_KNEE] = 90;
        assertTrue(rowVisible(R_KSLOPE, e), "the slope with a knee");
        assertEquals(R_GAMUT, nextChip(R_GAMMA, +1, e));
        assertEquals(R_QUAL, nextChip(R_SCURVE, +1, e));
    }

    @Test void theProfileChipsStepAndLabel() {
        int[] e = factoryRows();
        step(e, R_GAMMA, +1, Q_FINE);
        assertEquals(IspProfiles.SLOG2, e[R_GAMMA]);
        step(e, R_GAMMA, -1, Q_FINE); step(e, R_GAMMA, -1, Q_FINE);
        assertEquals(IspProfiles.HLG, e[R_GAMMA], "a choice: wraps through Off");
        assertEquals("HLG", fmt(R_GAMMA, e[R_GAMMA], e));
        assertEquals(Lang.t("value_off"), fmt(R_GAMMA, IspProfiles.OFF, e));
        assertEquals("S-Gamut3.Cine", fmt(R_GAMUT, IspProfiles.SGAMUT3_CINE, e));
        assertEquals("Camera", fmt(R_GAMUT, IspProfiles.CAMERA, e));
        assertEquals("Narrow", fmt(R_BRANGE, IspProfiles.NARROW, e));
        assertEquals(Lang.t("value_off"), fmt(R_KNEE, IspProfiles.KNEE_OFF, e));
        step(e, R_KNEE, +1, Q_FINE);
        assertEquals(75, e[R_KNEE], "Off, then 75%");
        assertEquals("75%", fmt(R_KNEE, e[R_KNEE], e));
        e[R_KNEE] = 105; step(e, R_KNEE, +1, Q_FINE);
        assertEquals(105, e[R_KNEE], "a number: clamps");
        e[R_CSAT] = 32; step(e, R_CSAT, +1, Q_FINE);
        assertEquals(32, e[R_CSAT]);
        assertEquals("+5", fmt(R_SCURVE, 5, e));
        assertEquals("-12", fmt(R_CSAT, -12, e));
    }

    @Test void gammaIsOnEveryBody() {
        int[] e = factoryRows();
        assertTrue(rowVisible(R_GAMMA, e), "a body that cannot run a profile says why, it does not hide the chip");
        e[R_GAMMA] = IspProfiles.SLOG3;
        assertTrue(rowVisible(R_GAMMA, e));
    }
}
