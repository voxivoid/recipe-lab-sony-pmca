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
}
