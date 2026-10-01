package com.voxivoid.recipelab;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * The display language: that every table is complete and keeps English's placeholders, that the menu choice resolves
 * the way it says, that what the app writes to files stays English whatever is on screen, and that each bundled font
 * has a glyph for every character its table uses.
 */
class LangTest {
    private static final int[] TRANSLATIONS = { Lang.ZH_HANS, Lang.ZH_HANT };
    private static final String[] FONTS = { null, "assets/fonts/RecipeLabCJKsc-Regular.ttf", "assets/fonts/RecipeLabCJKtc-Regular.ttf" };
    private static final Pattern PLACEHOLDER = Pattern.compile("%(?:\\d+\\$)?[a-zA-Z]");

    @AfterEach void english() { Lang.use(Lang.EN); }

    // ---- the tables
    /** the names English keeps as canonical data, which a translation must add under a key made from that data */
    static Set<String> canonicalKeys() {
        Set<String> k = new LinkedHashSet<String>();
        for (String r : Params.ROW_NAME) k.add("row_" + Lang.slug(r));
        for (int s = 0; s < Recipes.STYLE_NAMES.length; s++) if (Recipes.styleKnown(s)) k.add("style_" + Lang.slug(Recipes.STYLE_NAMES[s]));
        for (String pe : Recipes.PE_KEYS) k.add("effect_" + Lang.slug(pe));
        for (int pe = 0; pe < Recipes.PE_KEYS.length; pe++) { String[] v = Recipes.subValues(pe); if (v != null) for (String s : v) k.add("sub_" + Lang.slug(s)); }
        for (String q : Params.Q_LABEL) k.add("quality_" + Lang.slug(q));
        for (String g : Recipes.GROUPS) k.add("group_" + Lang.slug(g));
        for (Recipes.Recipe r : Recipes.ALL) k.add("recipe_" + Lang.slug(r.name));
        return k;
    }

    private static Set<String> keys(int lang) {
        Set<String> k = new LinkedHashSet<String>();
        for (String[] kv : Lang.table(lang)) {
            assertEquals(2, kv.length, "a table row is {key, text}");
            assertTrue(k.add(kv[0]), code(lang) + " lists " + kv[0] + " twice");
        }
        return k;
    }

    private static String code(int lang) { return Lang.CODES[lang]; }

    private static String text(int lang, String key) {
        for (String[] kv : Lang.table(lang)) if (kv[0].equals(key)) return kv[1];
        return null;
    }

    @Test void englishHoldsNoCanonicalName() {
        Set<String> en = keys(Lang.EN);
        for (String k : canonicalKeys()) assertFalse(en.contains(k), k + " is canonical data in Recipes / Params, not English table text");
    }

    @Test void everyTranslationHasEveryKeyAndNoOther() {
        Set<String> want = new TreeSet<String>(keys(Lang.EN));
        want.addAll(canonicalKeys());
        for (int lang : TRANSLATIONS) assertEquals(want, new TreeSet<String>(keys(lang)), code(lang));
    }

    @Test void everyTranslationKeepsEnglishsPlaceholders() {
        for (int lang : TRANSLATIONS)
            for (String[] kv : Lang.table(Lang.EN))
                assertEquals(placeholders(kv[1]), placeholders(text(lang, kv[0])), code(lang) + " " + kv[0]);
        for (int lang : TRANSLATIONS)
            for (String k : canonicalKeys()) assertEquals(new ArrayList<String>(), placeholders(text(lang, k)), code(lang) + " " + k);
    }

    private static List<String> placeholders(String s) {
        List<String> p = new ArrayList<String>();
        Matcher m = PLACEHOLDER.matcher(s);
        while (m.find()) p.add(m.group());
        java.util.Collections.sort(p);
        return p;
    }

    @Test void everyKeyTheCodeAsksForIsInEnglish() throws Exception {
        File dir = new File("src/com/voxivoid/recipelab");
        assumeTrue(dir.isDirectory(), "run from the repository root to scan the sources");
        Set<String> en = keys(Lang.EN), asked = new HashSet<String>();
        Pattern key = Pattern.compile("\"([a-z][a-z0-9]*_[a-z0-9_]+)\"");
        for (File f : dir.listFiles()) {
            if (!f.getName().endsWith(".java") || f.getName().startsWith("Text")) continue;
            for (String line : Files.readAllLines(f.toPath(), StandardCharsets.UTF_8)) {
                if (!line.contains("Lang.t(")) continue;
                Matcher m = key.matcher(line);
                while (m.find()) { asked.add(m.group(1)); assertTrue(en.contains(m.group(1)), f.getName() + " asks for " + m.group(1)); }
            }
        }
        assertTrue(asked.size() > 100, "the scan found the keys (" + asked.size() + ")");
    }

    @Test void everyEnglishKeyIsAskedFor() throws Exception {
        File dir = new File("src/com/voxivoid/recipelab");
        assumeTrue(dir.isDirectory(), "run from the repository root to scan the sources");
        StringBuilder src = new StringBuilder();
        for (File f : dir.listFiles()) if (f.getName().endsWith(".java") && !f.getName().startsWith("Text")) src.append(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8));
        for (String k : keys(Lang.EN)) assertTrue(src.indexOf("\"" + k + "\"") >= 0, k + " is in the tables but nothing shows it");
    }

    @Test void theLanguageRowSaysLanguageInEveryLanguage() {
        // whoever lands in a language they cannot read still has to find the way out
        for (int lang = 0; lang < Lang.COUNT; lang++) assertTrue(text(lang, "menu_language").contains("Language"), code(lang));
    }

    // ---- lookups
    @Test void textFollowsTheCurrentLanguageAndFallsBackToEnglish() {
        assertEquals("Picked — 3 values written, power-cycle the camera to apply everywhere", Lang.t("status_picked_many", 3));
        Lang.use(Lang.ZH_HANS);
        assertEquals("已选用——写入 3 项；重启相机后将全面生效", Lang.t("status_picked_many", 3));
        assertEquals("no_such_key", Lang.t("no_such_key"), "a missing key shows itself rather than crashing the camera app");
    }

    @Test void aLabelIsTheCanonicalTextInEnglishAndTheTranslationElsewhere() {
        Recipes.Recipe r = Fixtures.recipe("Classic Chrome");
        assertEquals("Classic Chrome", Recipes.displayName(r));
        assertNull(Recipes.originalName(r), "nothing to show under a name that is already the canonical one");
        Lang.use(Lang.ZH_HANS);
        assertEquals("经典正片", Recipes.displayName(r));
        assertEquals("Classic Chrome", Recipes.originalName(r));
        assertEquals("Classic Chrome", r.name, "the identifier favourites and the manifest use never changes");
        assertEquals("柯达", Recipes.groupLabel(3));
        assertEquals("饱和度", Params.rowName(Params.R_SAT));
        assertEquals("自动", Recipes.droLabel(Recipes.DRO_AUTO));
        Lang.use(Lang.ZH_HANT);
        assertEquals("經典正片", Recipes.displayName(r));
    }

    @Test void theHudAndTheLegendFollowTheLanguage() {
        int[] cur = Fixtures.factoryRows();
        Lang.use(Lang.ZH_HANS);
        assertEquals("标准  ·  白平衡 自动", Params.metaLine(cur, cur.clone(), null));
        assertEquals("选用", Keys.hints(Keys.H_RECIPE, Keys.Caps.UNKNOWN).labels[0]);
        assertEquals("收藏", Favourites.groupName(Favourites.GROUP));
        Lang.use(Lang.ZH_HANT);
        assertEquals("標準  ·  白平衡 自動", Params.metaLine(cur, cur.clone(), null));
    }

    @Test void slugsAreStableKeys() {
        assertEquals("kodak_tri_x_400", Lang.slug("Kodak Tri-X 400"));
        assertEquals("gr_hi_contrast_b_w", Lang.slug("GR Hi-Contrast B&W"));
        assertEquals("posterization_bw", Lang.slug("posterization-bw"));
        assertEquals("canon_nikon", Lang.slug("Canon / Nikon"));
        assertEquals("raw_jpg", Lang.slug("RAW+JPG"));
        Set<String> seen = new HashSet<String>();
        for (Recipes.Recipe r : Recipes.ALL) assertTrue(seen.add(Lang.slug(r.name)), "two recipes share the key of " + r.name);
    }

    // ---- the menu choice
    @Test void theChoiceWalksAutoThenEachLanguageAndWraps() {
        assertEquals(Lang.EN, Lang.nextChoice(Lang.AUTO, +1));
        assertEquals(Lang.ZH_HANS, Lang.nextChoice(Lang.EN, +1));
        assertEquals(Lang.ZH_HANT, Lang.nextChoice(Lang.ZH_HANS, +1));
        assertEquals(Lang.AUTO, Lang.nextChoice(Lang.ZH_HANT, +1));
        assertEquals(Lang.ZH_HANT, Lang.nextChoice(Lang.AUTO, -1));
    }

    @Test void aChoiceIsStoredByCodeAndAnythingUnknownIsAuto() {
        for (int c : Lang.CHOICES) assertEquals(c, Lang.parseChoice(Lang.choiceCode(c)), Lang.choiceCode(c));
        assertEquals(Lang.AUTO, Lang.parseChoice(null), "nothing stored yet");
        assertEquals(Lang.AUTO, Lang.parseChoice("tlh"), "a language this build does not have");
    }

    @Test void autoFollowsTheCameraAndFallsBackToEnglish() {
        assertEquals(Lang.ZH_HANS, Lang.resolve(Lang.AUTO, "zh", "CN"));
        assertEquals(Lang.ZH_HANS, Lang.resolve(Lang.AUTO, "zh", ""));
        assertEquals(Lang.ZH_HANS, Lang.resolve(Lang.AUTO, "zh", "SG"));
        assertEquals(Lang.ZH_HANT, Lang.resolve(Lang.AUTO, "zh", "TW"));
        assertEquals(Lang.ZH_HANT, Lang.resolve(Lang.AUTO, "zh", "HK"));
        assertEquals(Lang.ZH_HANT, Lang.resolve(Lang.AUTO, "zh", "MO"));
        assertEquals(Lang.EN, Lang.resolve(Lang.AUTO, "ja", "JP"), "no table for it, so English");
        assertEquals(Lang.EN, Lang.resolve(Lang.AUTO, null, null), "a camera that will not say");
        assertEquals(Lang.ZH_HANT, Lang.resolve(Lang.ZH_HANT, "en", "US"), "a choice overrides the camera");
        assertEquals(Lang.EN, Lang.resolve(Lang.EN, "zh", "CN"));
    }

    @Test void theAutoLabelIsInTheCurrentLanguageAndTheLanguagesInTheirOwn() {
        Lang.use(Lang.ZH_HANT);
        assertEquals("自動", Lang.choiceLabel(Lang.AUTO));
        assertEquals(Lang.ZH_HANT, Lang.choiceScript(Lang.AUTO));
        assertEquals("简体中文", Lang.choiceLabel(Lang.ZH_HANS));
        assertEquals(Lang.ZH_HANS, Lang.choiceScript(Lang.ZH_HANS), "drawn in the font of the language it names");
        assertEquals("English", Lang.choiceLabel(Lang.EN));
    }

    // ---- files stay English
    @Test void theSampleManifestIsEnglishWhateverTheMenuShows() {
        String en = DevTools.manifestLine(12, 11);
        Lang.use(Lang.ZH_HANS);
        assertEquals(en, DevTools.manifestLine(12, 11));
        assertEquals(Lang.ZH_HANS, Lang.current(), "and the menu's language comes back after");
    }

    @Test void theLockFileIsEnglishWhateverTheMenuShows() {
        List<Integer> ids = Params.allSlots();
        int[] attrs = new int[ids.size()];
        String en = Params.lockLines(ids, attrs);
        Lang.use(Lang.ZH_HANT);
        assertEquals(en, Params.lockLines(ids, attrs));
        assertEquals(Lang.ZH_HANT, Lang.current());
        assertTrue(en.startsWith("01070175 STYLE attr=0"), en);
        assertEquals("風格", Params.slotName(Params.ID_STYLE), "while a message on screen names the slot in the menu's language");
    }

    @Test void favouritesAreStoredByTheCanonicalNameInAnyLanguage() {
        List<Integer> favs = new ArrayList<Integer>(Arrays.asList(Fixtures.indexOf("Velvia")));
        String en = Favourites.encode(favs);
        Lang.use(Lang.ZH_HANS);
        assertEquals(en, Favourites.encode(favs));
        assertEquals(favs, Favourites.decode(en));
    }

    // ---- fonts
    @Test void eachFontHasAGlyphForEveryCharacterItsTableUses() throws Exception {
        for (int lang : TRANSLATIONS) {
            File f = new File(FONTS[lang]);
            assumeTrue(f.isFile(), "run from the repository root to check " + f);
            byte[] ttf = Files.readAllBytes(f.toPath());
            assertEquals(0x00010000, ByteBuffer.wrap(ttf).getInt(0), f + " must be a static TrueType font: Android 2.3 reads nothing else");
            Set<Integer> cmap = cmap(ttf);
            Set<Integer> need = new TreeSet<Integer>();
            for (int c = 0x20; c < 0x7f; c++) need.add(c);                 // untranslated data: recipe names, codes, errors
            for (String[] kv : Lang.table(Lang.EN)) addChars(need, kv[1]);  // the fallback, should a key ever be missing
            for (String[] kv : Lang.table(lang)) addChars(need, kv[1]);
            List<String> missing = new ArrayList<String>();
            for (int c : need) if (!cmap.contains(c)) missing.add(String.format("U+%04X %s", c, new String(Character.toChars(c))));
            assertEquals(new ArrayList<String>(), missing, f + " lacks glyphs -- run tools/subset-font.py");
        }
    }

    @Test void noFontIsTallerThanTheCameras() throws Exception {
        // a TextView sizes each line by the font's ascent / descent and pads it to the bounding box: a font that reports
        // more than the camera's Droid Sans (2048 units: hhea 1900 / -500, bbox -555 .. 2163) makes every line taller
        for (int lang : TRANSLATIONS) {
            File f = new File(FONTS[lang]);
            assumeTrue(f.isFile(), "run from the repository root to check " + f);
            byte[] ttf = Files.readAllBytes(f.toPath());
            ByteBuffer b = ByteBuffer.wrap(ttf);
            int head = table(ttf, "head"), hhea = table(ttf, "hhea");
            double upm = b.getShort(head + 18) & 0xffff;
            double line = (b.getShort(hhea + 4) - b.getShort(hhea + 6) + b.getShort(hhea + 8)) / upm;
            double box = (b.getShort(head + 42) - b.getShort(head + 38)) / upm;
            assertTrue(line <= 2400 / 2048.0 + 0.001, f + " line height " + line + " em -- run tools/subset-font.py");
            assertTrue(box <= 2718 / 2048.0, f + " bounding box " + box + " em -- run tools/subset-font.py");
        }
    }

    /** where a TrueType table starts */
    private static int table(byte[] ttf, String tag) {
        ByteBuffer b = ByteBuffer.wrap(ttf);
        for (int i = 0; i < (b.getShort(4) & 0xffff); i++)
            if (new String(ttf, 12 + 16 * i, 4, StandardCharsets.US_ASCII).equals(tag)) return b.getInt(12 + 16 * i + 8);
        fail("no " + tag + " table");
        return -1;
    }

    private static void addChars(Set<Integer> into, String s) {
        for (int i = 0; i < s.length(); ) { int c = s.codePointAt(i); i += Character.charCount(c); into.add(c); }
    }

    /** the characters a TrueType font maps to a glyph, from its Unicode cmap subtables (formats 4 and 12) */
    static Set<Integer> cmap(byte[] ttf) {
        ByteBuffer b = ByteBuffer.wrap(ttf);
        int cmap = table(ttf, "cmap");
        Set<Integer> out = new HashSet<Integer>();
        int subtables = b.getShort(cmap + 2) & 0xffff;
        for (int i = 0; i < subtables; i++) {
            int platform = b.getShort(cmap + 4 + 8 * i) & 0xffff, encoding = b.getShort(cmap + 6 + 8 * i) & 0xffff;
            int st = cmap + b.getInt(cmap + 8 + 8 * i), format = b.getShort(st) & 0xffff;
            boolean unicode = platform == 0 || (platform == 3 && (encoding == 1 || encoding == 10));
            if (!unicode) continue;
            if (format == 4) {
                int segs = (b.getShort(st + 6) & 0xffff) / 2, ends = st + 14, starts = ends + 2 * segs + 2, deltas = starts + 2 * segs, ranges = deltas + 2 * segs;
                for (int s = 0; s < segs; s++) {
                    int end = b.getShort(ends + 2 * s) & 0xffff, start = b.getShort(starts + 2 * s) & 0xffff;
                    int delta = b.getShort(deltas + 2 * s), range = b.getShort(ranges + 2 * s) & 0xffff;
                    for (int c = start; c <= end && c != 0xffff; c++) {
                        int g = range == 0 ? c + delta : b.getShort(ranges + 2 * s + range + 2 * (c - start)) & 0xffff;
                        if (range != 0 && g != 0) g += delta;
                        if ((g & 0xffff) != 0) out.add(c);
                    }
                }
            } else if (format == 12) {
                int groups = b.getInt(st + 12);
                for (int g = 0; g < groups; g++) {
                    int start = b.getInt(st + 16 + 12 * g), end = b.getInt(st + 20 + 12 * g), glyph = b.getInt(st + 24 + 12 * g);
                    for (int c = start; c <= end; c++) if (glyph + (c - start) != 0) out.add(c);
                }
            }
        }
        return out;
    }
}
