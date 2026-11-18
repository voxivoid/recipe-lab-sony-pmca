package com.voxivoid.recipelab;

import static com.voxivoid.recipelab.Fixtures.*;
import static com.voxivoid.recipelab.Params.*;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/**
 * An applied edit kept across a restart: the rows as the preferences store them, and when the camera's values are still
 * the look that was applied — what lets a reopened app show ACTIVE EDITED instead of the recipe's own values.
 */
class ParamsLookTest {

    @Test void rowsSurviveTheirTextForm() {
        int[] rows = staged(recipe("Kodak Vision 200T (Asteroid City)"), factoryRows(), Q_FINE);
        rows[R_SAT] = -3; rows[R_EV] = -15;
        assertArrayEquals(rows, rowsFrom(rowsText(rows)));
    }

    @Test void anythingButRowsTextIsNoLook() {
        assertNull(rowsFrom(null));
        assertNull(rowsFrom(""));
        assertNull(rowsFrom("1,2,3"), "a build with other rows");
        StringBuilder bad = new StringBuilder("x");
        for (int i = 1; i < N; i++) bad.append(",0");
        assertNull(rowsFrom(bad.toString()));
    }

    @Test void theSameLookIgnoresQualityAndRowsTheLookDoesNotShow() {
        int[] a = staged(recipe("Velvia"), factoryRows(), Q_FINE), b = a.clone();
        assertTrue(sameLook(a, b));
        b[R_QUAL] = Q_RAW;
        assertTrue(sameLook(a, b), "quality is no part of a look");
        b[R_KELVIN] = 32;
        assertTrue(sameLook(a, b), "the kelvin row is hidden in auto white balance");
        b[R_SUB] = 2;
        assertTrue(sameLook(a, b), "and the sub-setting without an effect");
        b[R_SAT] = a[R_SAT] - 1;
        assertFalse(sameLook(a, b), "a value the camera would show differently");
    }

    @Test void kelvinAndSubCountWhenTheLookUsesThem() {
        int[] a = staged(recipe("Cinestill 800T"), factoryRows(), Q_FINE), b = a.clone();
        b[R_KELVIN] = a[R_KELVIN] + 1;
        assertFalse(sameLook(a, b), "kelvin mode: the kelvin row is the look");
        int[] e = staged(recipe("Sony SH (soft high-key)"), factoryRows(), Q_FINE), f = e.clone();
        f[R_SUB] = (e[R_SUB] + 1) % 3;
        assertFalse(sameLook(e, f), "an effect's option is the look");
    }

    @Test void anAppliedEditIsNotTheRecipeButIsWhatTheCameraHolds() {
        Recipes.Recipe velvia = recipe("Velvia");
        int[] applied = staged(velvia, factoryRows(), Q_FINE);
        applied[R_SAT] = 1;
        assertTrue(differsFromRecipe(velvia, applied), "EDITED");
        int[] cameraAfterRestart = rowsFrom(rowsText(applied));
        assertTrue(sameLook(applied, cameraAfterRestart), "ACTIVE: the camera still holds it");
    }

    @Test void anAppliedEditShowsOnlyOnItsRecipeWhileTheCameraHoldsIt() {
        int[] applied = staged(recipe("Velvia"), factoryRows(), Q_FINE);
        applied[R_SAT] = 1;
        int[] camera = applied.clone();
        assertTrue(showsAppliedEdit("Velvia", applied, "Velvia", camera), "back on Velvia: ACTIVE EDITED");
        assertFalse(showsAppliedEdit("Velvia", applied, "Provia", camera), "another recipe shows its own values");
        camera[R_CON] = camera[R_CON] + 1;
        assertFalse(showsAppliedEdit("Velvia", applied, "Velvia", camera), "the camera changed since: the recipe as before");
        assertFalse(showsAppliedEdit(null, applied, "Velvia", applied), "nothing applied");
        assertFalse(showsAppliedEdit("Velvia", null, "Velvia", applied), "a stored look this build cannot read");
    }

    // ------------------------------------------------------------ the picture profile rows
    @Test void aProfileRecipeStagesItsLookAndAnyOtherClearsIt() {
        int[] e = factoryRows();
        Params.stage(recipe("S-Log3 Cine"), e);
        assertEquals(new IspProfiles.Look(IspProfiles.SLOG3, IspProfiles.SGAMUT3_CINE), Params.look(e));
        assertEquals(Recipes.DRO_OFF, e[R_DRO], "DRO off: nothing reshapes the curve");
        Params.stage(recipe("Velvia"), e);
        assertNull(Params.look(e), "a recipe without a profile turns it off");
        assertArrayEquals(factoryLookRows(), java.util.Arrays.copyOfRange(e, R_GAMMA, R_SCURVE + 1), "and leaves the profile rows neutral");
    }

    private static int[] factoryLookRows() { return java.util.Arrays.copyOfRange(factoryRows(), R_GAMMA, R_SCURVE + 1); }

    @Test void profileRowsAreNeverStoreWritesButDoMakeTheRecipeDirty() {
        int[] cur = factoryRows(), e = cur.clone();
        e[R_GAMMA] = IspProfiles.SLOG2; e[R_CSAT] = 4;
        assertTrue(rowDirty(R_GAMMA, cur, e, 0));
        assertTrue(rowDirty(R_CSAT, cur, e, 0));
        assertEquals(2, dirtyRows(cur, e, 0));
        assertTrue(writes(cur, e, 0).isEmpty(), "the image processor's, not the store's");
    }

    @Test void aProfileRowThatDoesNothingIsNotDirty() {
        int[] cur = factoryRows(), e = cur.clone();
        e[R_CSAT] = 9;
        assertFalse(rowDirty(R_CSAT, cur, e, 0), "no profile on either side");
        cur[R_GAMMA] = e[R_GAMMA] = IspProfiles.SLOG3;
        e[R_BRANGE] = IspProfiles.WIDE; e[R_KSLOPE] = 3;
        assertFalse(rowDirty(R_BRANGE, cur, e, 0), "a range with no black gamma");
        assertFalse(rowDirty(R_KSLOPE, cur, e, 0), "a slope with no knee");
        assertTrue(rowDirty(R_CSAT, cur, e, 0));
    }

    @Test void theSameLookIgnoresProfileRowsThatDoNothing() {
        int[] a = factoryRows(), b = a.clone();
        b[R_PHASE] = 3;
        assertTrue(sameLook(a, b), "no profile: the adjustments are not the look");
        a[R_GAMMA] = b[R_GAMMA] = IspProfiles.LOGC3;
        assertFalse(sameLook(a, b));
        b[R_PHASE] = 0; b[R_KSLOPE] = 2;
        assertTrue(sameLook(a, b), "a slope with no knee");
        assertTrue(differsFromRecipe(recipe("LogC3"), withPhase(recipe("LogC3"), 1)));
    }

    private static int[] withPhase(Recipes.Recipe r, int phase) {
        int[] e = factoryRows();
        Params.stage(r, e);
        e[R_PHASE] = phase;
        return e;
    }

    @Test void theKeptLookRoundTripsAndAnythingElseIsNone() {
        IspProfiles.Look l = new IspProfiles.Look(IspProfiles.HLG, IspProfiles.BT2020, -3, 4, IspProfiles.NARROW, 88, -2, 12, 5, -6);
        assertEquals(l, Params.lookFrom(Params.lookText(l)));
        assertEquals("0", Params.lookText(null));
        assertNull(Params.lookFrom("0"));
        assertNull(Params.lookFrom(null));
        assertNull(Params.lookFrom("1,2,3"), "a build with other rows");
        assertNull(Params.lookFrom("2,0,99,0,1,74,0,0,0,0"), "out of range");
        assertNull(Params.lookFrom("2,0,x,0,1,74,0,0,0,0"));
    }

    @Test void aCustomRecipeKeepsTheStagedLook() {
        int[] e = factoryRows();
        Params.stage(recipe("S-Log3"), e);
        e[R_BLACK] = 5; e[R_KSLOPE] = 4;                           // the slope does nothing without a knee
        Recipes.Recipe r = CustomRecipes.recipe("Mine", e);
        assertEquals(new IspProfiles.Look(IspProfiles.SLOG3, IspProfiles.SGAMUT3, 5, 0, IspProfiles.MIDDLE, IspProfiles.KNEE_OFF, 0, 0, 0, 0), r.look);
        assertEquals(0, r.look.slope, "kept as the neutral value");
        assertNull(CustomRecipes.problem(r));
        assertNull(CustomRecipes.recipe("Plain", factoryRows()).look);
    }
}

