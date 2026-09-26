package com.voxivoid.recipelab;

import java.util.Locale;
import java.util.LinkedHashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Localized presentation text built from camera-independent data. */
final class UiText {
    private static final String[] GROUP_KEYS = {
        "group_sony", "group_fuji_sim", "group_fuji_film", "group_kodak", "group_cine", "group_ricoh_gr",
        "group_leica", "group_hasselblad", "group_canon_nikon", "group_pana_olympus", "group_other_stocks", "group_ilford"
    };
    private static final String[] ROW_KEYS = {
        "row_recipe", "row_style", "row_saturation", "row_contrast", "row_sharpness", "row_matrix", "row_effect",
        "row_sub", "row_white_balance", "row_kelvin", "row_amber_blue", "row_green_magenta", "row_ev", "row_dro", "row_quality"
    };
    private static final String[] STYLE_KEYS = {
        null, "style_standard", "style_vivid", "style_neutral", "style_portrait", "style_landscape", "style_bw",
        "style_clear", "style_deep", "style_light", "style_sunset", "style_night", "style_autumn", null, "style_sepia"
    };
    private static final String[] EFFECT_KEYS = {
        "effect_off", "effect_toy", "effect_pop", "effect_poster", "effect_retro", "effect_high_key", "effect_partial_color",
        "effect_high_contrast_mono", "effect_soft_focus", "effect_hdr_art", "effect_rich_mono", "effect_miniature",
        "effect_illustration", "effect_watercolor"
    };
    private static final String[] QUALITY_KEYS = {
        "quality_raw", "quality_raw_jpeg", "quality_jpeg_fine", "quality_jpeg_standard"
    };
    private final TextCatalog catalog;

    UiText(TextCatalog catalog) { this.catalog = catalog; }

    String recipeName(Recipes.Recipe recipe) { return catalog.text(recipeKey(recipe)); }

    String text(String key, Object... args) { return catalog.text(key, args); }

    String groupName(int group) {
        if (group == Favourites.GROUP) return catalog.text("group_favourites");
        return group >= 0 && group < GROUP_KEYS.length ? catalog.text(GROUP_KEYS[group]) : "?" + group;
    }

    String rowName(int row) { return row >= 0 && row < ROW_KEYS.length ? catalog.text(ROW_KEYS[row]) : "?" + row; }

    String qualityLabel(int value) {
        return value >= 0 && value < QUALITY_KEYS.length ? catalog.text(QUALITY_KEYS[value]) : "?" + value;
    }

    String styleLabel(int value) {
        return value >= 0 && value < STYLE_KEYS.length && STYLE_KEYS[value] != null ? catalog.text(STYLE_KEYS[value]) : "?" + value;
    }

    String effectLabel(int value) {
        return value >= 0 && value < EFFECT_KEYS.length ? catalog.text(EFFECT_KEYS[value]) : "?" + value;
    }

    String subLabel(int effect, int sub) {
        String[] values = Recipes.subValues(effect);
        if (values == null) return null;
        return sub >= 0 && sub < values.length ? catalog.text("sub_" + slug(values[sub])) : "?" + sub;
    }

    String droLabel(int value) {
        if (value == Recipes.DRO_AUTO) return catalog.text("value_auto");
        if (value == Recipes.DRO_OFF) return catalog.text("value_off");
        return catalog.text("value_level", value);
    }

    String value(int row, int value, int[] edit) {
        switch (row) {
            case Params.R_STYLE: return styleLabel(value);
            case Params.R_MTX: return value == 0 ? catalog.text("value_off") : "PP3";
            case Params.R_WBMODE: return value == Params.WB_AUTO ? catalog.text("value_auto") : value == Params.WB_KELVIN ? catalog.text("value_kelvin") : String.valueOf(value);
            case Params.R_KELVIN: return edit[Params.R_WBMODE] == Params.WB_KELVIN ? (value * 100) + "K" : "-";
            case Params.R_AB: return value == 0 ? "0" : value > 0 ? "A" + value : "B" + (-value);
            case Params.R_GM: return value == 0 ? "0" : value > 0 ? "G" + value : "M" + (-value);
            case Params.R_PE: return effectLabel(value);
            case Params.R_SUB: { String label = subLabel(edit[Params.R_PE], value); return label == null ? "-" : label; }
            case Params.R_EV: return Recipes.evLabel(value);
            case Params.R_DRO: return droLabel(value);
            case Params.R_QUAL: return qualityLabel(value);
            default: return (value > 0 ? "+" : "") + value;
        }
    }

    String recipeSummary(Recipes.Recipe recipe) {
        StringBuilder summary = new StringBuilder();
        if (recipe.pe != 0) {
            summary.append(effectLabel(recipe.pe));
            String sub = subLabel(recipe.pe, recipe.sub);
            if (sub != null) summary.append(' ').append(sub);
        } else {
            summary.append(styleLabel(recipe.style)).append("  ")
                    .append(recipe.sat > 0 ? "+" : "").append(recipe.sat).append('/')
                    .append(recipe.con > 0 ? "+" : "").append(recipe.con);
        }
        if (recipe.matrix == 1 && recipe.pe == 0) summary.append("  MTX");
        if (recipe.ev != 0) summary.append("  ").append(Recipes.evLabel(recipe.ev));
        if (recipe.dro != Recipes.DRO_AUTO) summary.append("  DRO ").append(droLabel(recipe.dro));
        if (recipe.wbMode == Params.WB_KELVIN) summary.append("  ").append(recipe.kelvin).append('K');
        if (recipe.ab != 0) summary.append("  ").append(recipe.ab > 0 ? "A" + recipe.ab : "B" + (-recipe.ab));
        if (recipe.gm != 0) summary.append("  ").append(recipe.gm > 0 ? "G" + recipe.gm : "M" + (-recipe.gm));
        return summary.toString();
    }

    String[] qualityPrompt(int[] current, int[] edit) {
        return new String[] {
            catalog.text("quality_prompt_title", qualityLabel(current[Params.R_QUAL]), qualityLabel(edit[Params.R_QUAL])),
            catalog.text(edit[Params.R_PE] != 0 ? "quality_prompt_effect" : "quality_prompt_style")
        };
    }

    String miniLine(int recipe, int[] current, int[] edit, boolean dirty) {
        String line = catalog.text("mini_line", edit[Params.R_PE] != 0 ? "PE" : "CS", recipeName(Recipes.ALL[recipe]),
                recipe + 1, Recipes.ALL.length, catalog.text(dirty ? "state_preview" : "state_active"));
        if (edit[Params.R_QUAL] != current[Params.R_QUAL]) line += "   · " + catalog.text("mini_quality", qualityLabel(edit[Params.R_QUAL]));
        return line;
    }

    String lockedMessage(List<Integer> ids) {
        String names = slotNames(ids);
        return catalog.text(ids.size() == 1 ? "locked_one" : "locked_many", names) + " " + catalog.text("unlock_hint");
    }

    String writeFailedMessage(int id, String error, int written) {
        String hex = String.format(Locale.US, "%08x", id);
        return written == 0
                ? catalog.text("write_failed_none", slotName(id), hex, error)
                : catalog.text("write_failed_some", slotName(id), hex, error, written);
    }

    String metaLine(int[] current, int[] edit, String previewError) {
        List<String> parts = new ArrayList<String>();
        if (edit[Params.R_PE] != 0) {
            String effect = effectLabel(edit[Params.R_PE]);
            String sub = subLabel(edit[Params.R_PE], edit[Params.R_SUB]);
            if (sub != null) effect += " " + sub;
            parts.add(catalog.text("meta_picture_effect", effect) + " " + catalog.text("meta_effect_note"));
        } else parts.add(styleLabel(edit[Params.R_STYLE]));
        String wb = edit[Params.R_WBMODE] == Params.WB_KELVIN ? (edit[Params.R_KELVIN] * 100) + "K"
                : edit[Params.R_WBMODE] == Params.WB_AUTO ? catalog.text("value_auto") : String.valueOf(edit[Params.R_WBMODE]);
        parts.add(catalog.text("meta_white_balance", wb));
        if (edit[Params.R_MTX] == 1 && edit[Params.R_PE] == 0) parts.add(catalog.text("meta_pp3_matrix"));
        if (edit[Params.R_EV] != 0) parts.add(catalog.text("meta_ev", Recipes.evLabel(edit[Params.R_EV])));
        if (edit[Params.R_DRO] != Recipes.DRO_AUTO) parts.add(catalog.text("meta_dro", droLabel(edit[Params.R_DRO])));
        if (edit[Params.R_QUAL] != current[Params.R_QUAL]) parts.add(catalog.text("meta_quality_change", qualityLabel(edit[Params.R_QUAL]), qualityLabel(current[Params.R_QUAL])));
        if (edit[Params.R_PE] != 0 && edit[Params.R_QUAL] <= Params.Q_RAWJPG) parts.add(catalog.text("meta_raw_effect_ignored"));
        if (previewError != null) parts.add(catalog.text("meta_no_preview", previewError));
        StringBuilder line = new StringBuilder();
        for (String part : parts) { if (line.length() > 0) line.append("  ·  "); line.append(part); }
        return line.toString();
    }

    String favouriteMessage(Recipes.Recipe recipe, boolean added) {
        return catalog.text(added ? "favourite_added" : "favourite_removed", recipeName(recipe));
    }

    String devRowLabel(int row, boolean snapshotTaken, int settle) {
        switch (row) {
            case DevTools.ROW_SNAPSHOT: return catalog.text(snapshotTaken ? "dev_settings_diff" : "dev_settings_snapshot");
            case DevTools.ROW_LOCKS: return catalog.text("dev_read_only_check", Params.allSlots().size());
            case DevTools.ROW_SAMPLES: return catalog.text("dev_shoot_samples", Recipes.ALL.length);
            case DevTools.ROW_SETTLE: return catalog.text("dev_settle_delay", catalog.text("value_seconds", DevTools.settleLabel(settle).replace(" s", "")));
            default: return "?" + row;
        }
    }

    String devRowDetail(int row, boolean snapshotTaken) {
        switch (row) {
            case DevTools.ROW_SNAPSHOT: return catalog.text(snapshotTaken ? "dev_snapshot_compare" : "dev_snapshot_store");
            case DevTools.ROW_LOCKS: return catalog.text("dev_locks_detail");
            case DevTools.ROW_SAMPLES: return catalog.text("dev_samples_detail");
            case DevTools.ROW_SETTLE: return catalog.text("dev_settle_detail");
            default: return "";
        }
    }

    String lockReport(List<Integer> ids, int[] attrs) {
        List<Integer> locked = new ArrayList<Integer>();
        int unreadable = 0;
        for (int i = 0; i < ids.size(); i++) {
            if (attrs[i] < 0) unreadable++;
            else if (Params.slotLocked(attrs[i])) locked.add(ids.get(i));
        }
        String report = locked.isEmpty()
                ? catalog.text("lock_report_none", ids.size())
                : catalog.text("lock_report_some", ids.size(), locked.size(), slotNames(locked));
        if (unreadable > 0) report += catalog.text("lock_report_unreadable", unreadable);
        return report;
    }

    String devProgress(int frame, int total, Recipes.Recipe recipe) {
        return catalog.text("dev_progress", frame, total, recipeName(recipe));
    }

    String devDoneMessage(int shot, int total) { return catalog.text("dev_done", shot, total, DevTools.MANIFEST); }

    String devStoppedMessage(int shot, int total) {
        return shot == 0 ? catalog.text("dev_stopped_none") : catalog.text("dev_stopped", shot, total, DevTools.MANIFEST);
    }

    String devShootFailed(int frame, int shot, String error) {
        return catalog.text("dev_shoot_failed", frame, error, shot, DevTools.MANIFEST);
    }

    private String slotNames(List<Integer> ids) {
        Set<String> names = new LinkedHashSet<String>();
        for (int id : ids) names.add(slotName(id));
        StringBuilder joined = new StringBuilder();
        for (String name : names) { if (joined.length() > 0) joined.append(", "); joined.append(name); }
        return joined.toString();
    }

    private String slotName(int id) {
        switch (id) {
            case Params.ID_EV: case Params.ID_EV2: return rowName(Params.R_EV);
            case Params.ID_QFMT: case Params.ID_QJPG: case Params.ID_QFMT2: case Params.ID_QJPG2: return rowName(Params.R_QUAL);
            case Params.ID_WB_AB: case Params.ID_WB_AB_AWB: case Params.ID_WB_AB_K: return rowName(Params.R_AB);
            case Params.ID_WB_GM: case Params.ID_WB_GM_AWB: case Params.ID_WB_GM_K: return rowName(Params.R_GM);
            case Params.ID_DRO: case Params.ID_DRO_LVL: return rowName(Params.R_DRO);
        }
        for (int row = 1; row < Params.N; row++) if (Params.ROW_ID[row] > 0 && Params.ROW_ID[row] == id) return rowName(row);
        for (int effect = 0; effect < Recipes.PE_KEYS.length; effect++) {
            int subId = Recipes.subId(effect);
            if (subId != 0 && subId == id) return rowName(Params.R_SUB) + " " + effectLabel(effect);
        }
        return String.format(Locale.US, "%08x", id);
    }

    /** The canonical name shown as a secondary label, or null when localization did not change it. */
    String recipeOriginalName(Recipes.Recipe recipe) {
        return recipe.name.equals(recipeName(recipe)) ? null : recipe.name;
    }

    static String recipeKey(Recipes.Recipe recipe) {
        return "recipe_" + slug(recipe.name);
    }

    private static String slug(String value) {
        String name = value.toLowerCase(Locale.US);
        StringBuilder slug = new StringBuilder();
        boolean separator = false;
        for (int i = 0; i < name.length(); i++) {
            char ch = name.charAt(i);
            if ((ch >= 'a' && ch <= 'z') || (ch >= '0' && ch <= '9')) {
                if (separator && slug.length() > 0) slug.append('_');
                slug.append(ch);
                separator = false;
            } else separator = true;
        }
        return slug.toString();
    }
}
