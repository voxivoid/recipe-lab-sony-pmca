package com.voxivoid.recipelab;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ChineseTest {
    @Test void everyRecipeHasChineseDisplayWithoutChangingFavouriteIds() {
        for (Recipes.Recipe recipe : Recipes.ALL) {
            assertTrue(Zh.text(recipe.name).matches(".*[\\u4e00-\\u9fff].*"), recipe.name);
            assertFalse(recipe.name.matches(".*[\\u4e00-\\u9fff].*"));
        }
        java.util.List<Integer> favourites = new java.util.ArrayList<Integer>();
        favourites.add(12);
        String stored = Favourites.encode(favourites);
        assertEquals(Recipes.ALL[12].name, stored);
        assertEquals(favourites, Favourites.decode(stored));
    }
    @Test void displayLabelsDoNotTranslateInsideCameraKeysOrExceptionNames() {
        assertEquals("color-mode", Zh.text("color-mode"));
        assertEquals("java.lang.IllegalStateException", Zh.text("java.lang.IllegalStateException"));
        assertEquals("预览中", Zh.text("PREVIEW"));
        assertEquals("JPEG 精细", Zh.text("JPG Fine"));
    }
    @Test void dynamicSuccessAndErrorsRetainCountsAndMeaning() {
        assertEquals("已保存：2 项参数，关机再开机后全面生效",
            Zh.text("Picked — 2 values written, power-cycle the camera to apply everywhere"));
        assertTrue(Zh.text(Params.writeFailedMessage(Params.ROW_ID[1], "error -3", 2)).contains("2"));
        assertTrue(Zh.text(DevTools.progress(3, 77, "Velvia")).contains("MENU 停止"));
    }
    @Test void originalHoldHintStillMeansFavourite() {
        assertEquals("长按收藏", Zh.text("fav (hold)"));
        assertEquals("选中配方，长按中央键收藏", Zh.text(Favourites.EMPTY_HINT));
    }
}
