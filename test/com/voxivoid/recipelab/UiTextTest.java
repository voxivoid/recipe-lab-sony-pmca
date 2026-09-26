package com.voxivoid.recipelab;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class UiTextTest {
    @Test void localizesRecipeWithoutChangingCanonicalName() {
        Recipes.Recipe recipe = Recipes.ALL[0];
        FakeCatalog catalog = new FakeCatalog().put("recipe_factory_st", "出厂标准");
        UiText text = new UiText(catalog);

        assertEquals("出厂标准", text.recipeName(recipe));
        assertEquals("FACTORY (ST)", text.recipeOriginalName(recipe));
        assertEquals("FACTORY (ST)", recipe.name);
    }

    @Test void hidesDuplicateCanonicalRecipeName() {
        Recipes.Recipe recipe = Fixtures.recipe("Kodak Portra 400");
        UiText text = new UiText(new FakeCatalog().put("recipe_kodak_portra_400", "Kodak Portra 400"));

        assertNull(text.recipeOriginalName(recipe));
    }

    @Test void everyRecipeHasAUniqueStableResourceKey() {
        Set<String> keys = new HashSet<String>();
        for (Recipes.Recipe recipe : Recipes.ALL) {
            String key = UiText.recipeKey(recipe);
            assertTrue(key.matches("recipe_[a-z0-9_]+"), key);
            assertTrue(keys.add(key), "duplicate resource key " + key);
        }
        assertEquals(Recipes.ALL.length, keys.size());
    }

    @Test void localizesGroupsRowsAndEnumeratedValues() {
        UiText text = new UiText(baseCatalog());
        int[] edit = Fixtures.factoryRows();

        assertEquals("收藏", text.groupName(Favourites.GROUP));
        assertEquals("富士模拟", text.groupName(1));
        assertEquals("风格", text.rowName(Params.R_STYLE));
        assertEquals("标准", text.value(Params.R_STYLE, Recipes.STD, edit));
        assertEquals("自动", text.value(Params.R_DRO, Recipes.DRO_AUTO, edit));
        assertEquals("JPEG 精细", text.value(Params.R_QUAL, Params.Q_FINE, edit));
    }

    @Test void localizesRecipeSummaryWhileKeepingTechnicalValuesStable() {
        UiText text = new UiText(baseCatalog());
        Recipes.Recipe recipe = Fixtures.recipe("Classic Negative");

        String summary = text.recipeSummary(recipe);
        assertTrue(summary.startsWith("标准"), summary);
        assertTrue(summary.contains("-3/+3"), summary);
        assertTrue(summary.contains("B1"), summary);
        assertTrue(summary.contains("G1"), summary);
    }

    @Test void localizesQualityPromptAndCompactStatus() {
        FakeCatalog catalog = baseCatalog()
                .put("quality_raw", "RAW")
                .put("quality_raw_jpeg", "RAW+JPEG")
                .put("quality_jpeg_standard", "JPEG 标准")
                .put("quality_prompt_title", "画质：%1$s → %2$s")
                .put("quality_prompt_effect", "应用此配方需要 JPEG。")
                .put("quality_prompt_style", "创意风格配方沿用出厂配方的画质。")
                .put("state_preview", "预览")
                .put("state_active", "已启用")
                .put("mini_line", "%1$s  %2$s  %3$d/%4$d · %5$s")
                .put("mini_quality", "画质 → %1$s");
        UiText text = new UiText(catalog);
        int[] cur = Fixtures.factoryRows(), edit = cur.clone();
        edit[Params.R_QUAL] = Params.Q_STD;

        assertArrayEquals(new String[] { "画质：JPEG 精细 → JPEG 标准", "创意风格配方沿用出厂配方的画质。" }, text.qualityPrompt(cur, edit));
        String line = text.miniLine(0, cur, edit, true);
        assertTrue(line.contains("出厂标准"), line);
        assertTrue(line.contains("预览"), line);
        assertTrue(line.contains("画质 → JPEG 标准"), line);
    }

    @Test void localizesSafetyMessagesButKeepsSlotIdsAndErrorsVerbatim() {
        UiText text = new UiText(baseCatalog()
                .put("row_ev", "曝光补偿")
                .put("locked_one", "未写入——相机将此设置设为只读：%1$s。")
                .put("locked_many", "未写入——相机将这些设置设为只读：%1$s。")
                .put("unlock_hint", "请用 OpenMemories-Tweak 解锁受保护设置后重试。")
                .put("write_failed_none", "写入 %1$s（%2$s）失败：%3$s——尚未写入任何内容")
                .put("write_failed_some", "写入 %1$s（%2$s）失败：%3$s——停止前已写入 %4$d 字节"));

        String locked = text.lockedMessage(Arrays.asList(Params.ID_EV, Params.ID_EV2));
        assertTrue(locked.contains("曝光补偿"), locked);
        assertEquals(1, locked.split("曝光补偿", -1).length - 1, locked);
        assertTrue(locked.contains("OpenMemories-Tweak"), locked);
        assertEquals("写入 曝光补偿（010700b8）失败：boom——尚未写入任何内容", text.writeFailedMessage(Params.ID_EV, "boom", 0));
    }

    @Test void localizesMetadataFavouritesAndDeveloperMenu() {
        FakeCatalog catalog = baseCatalog()
                .put("effect_retro", "复古照片")
                .put("meta_picture_effect", "照片效果 %1$s")
                .put("meta_effect_note", "（忽略创意风格，仅 JPEG）")
                .put("meta_white_balance", "白平衡 %1$s")
                .put("favourite_added", "%1$s 已加入收藏")
                .put("favourite_removed", "%1$s 已从收藏移除")
                .put("dev_settings_snapshot", "设置快照")
                .put("dev_settings_diff", "设置差异")
                .put("dev_read_only_check", "只读检查——%1$d 个槽位")
                .put("dev_shoot_samples", "拍摄样片——%1$d 个配方")
                .put("dev_settle_delay", "稳定延时——%1$s")
                .put("value_seconds", "%1$s 秒");
        UiText text = new UiText(catalog);
        int[] cur = Fixtures.factoryRows(), edit = cur.clone();
        Params.stage(Fixtures.recipe("GR Retro"), edit);

        String meta = text.metaLine(cur, edit, null);
        assertTrue(meta.contains("照片效果 复古照片"), meta);
        assertTrue(meta.contains("白平衡 自动"), meta);
        assertEquals("出厂标准 已加入收藏", text.favouriteMessage(Recipes.ALL[0], true));
        assertEquals("设置快照", text.devRowLabel(DevTools.ROW_SNAPSHOT, false, 0));
        assertEquals("设置差异", text.devRowLabel(DevTools.ROW_SNAPSHOT, true, 0));
        assertEquals("稳定延时——0.8 秒", text.devRowLabel(DevTools.ROW_SETTLE, false, 0));
    }

    @Test void localizesLockReportsAndSampleRunMessages() {
        UiText text = new UiText(baseCatalog()
                .put("lock_report_none", "已检查 %1$d 个配方槽位 · 没有只读项")
                .put("lock_report_some", "已检查 %1$d 个配方槽位 · %2$d 个只读：%3$s")
                .put("lock_report_unreadable", " · %1$d 个无响应")
                .put("dev_progress", "正在拍摄 %1$d / %2$d · %3$s——MENU 停止")
                .put("dev_done", "样片完成——已拍摄 %1$d / %2$d 张，清单位于 %3$s")
                .put("dev_stopped_none", "样片拍摄在第一张之前停止")
                .put("dev_stopped", "样片拍摄已停止——已拍摄 %1$d / %2$d 张，清单位于 %3$s")
                .put("dev_shoot_failed", "第 %1$d 张触发快门失败：%2$s——已拍摄 %3$d 张，清单位于 %4$s"));
        String report = text.lockReport(Arrays.asList(Params.ID_EV, Params.ID_EV2), new int[] { Params.ATTR_READ_ONLY, -1 });
        assertTrue(report.contains("1 个只读：曝光补偿"), report);
        assertTrue(report.endsWith("1 个无响应"), report);
        assertTrue(text.devProgress(1, 77, Recipes.ALL[0]).contains("出厂标准"));
        assertTrue(text.devDoneMessage(77, 77).contains(DevTools.MANIFEST));
        assertFalse(text.devStoppedMessage(0, 77).contains(DevTools.MANIFEST));
        assertTrue(text.devShootFailed(3, 2, "timeout").contains("timeout"));
    }

    private static FakeCatalog baseCatalog() {
        return new FakeCatalog()
                .put("group_favourites", "收藏")
                .put("group_fuji_sim", "富士模拟")
                .put("row_style", "风格")
                .put("row_sub", "子选项")
                .put("row_ev", "曝光补偿")
                .put("style_standard", "标准")
                .put("value_auto", "自动")
                .put("value_off", "关闭")
                .put("value_level", "等级 %1$d")
                .put("recipe_factory_st", "出厂标准")
                .put("quality_jpeg_fine", "JPEG 精细");
    }

    static final class FakeCatalog implements TextCatalog {
        private final Map<String, String> values = new HashMap<String, String>();

        FakeCatalog put(String key, String value) { values.put(key, value); return this; }

        public String text(String key, Object... args) {
            String value = values.get(key);
            if (value == null) throw new IllegalArgumentException("missing text key: " + key);
            return args.length == 0 ? value : String.format(value, args);
        }
    }
}
