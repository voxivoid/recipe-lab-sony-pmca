package com.voxivoid.recipelab;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

class TranslationResourcesTest {
    private static final String[] LOCALES = {
        "res/values-zh-rCN",
        "res/values-zh-rTW",
        "res/values-zh-rHK"
    };
    private static final Pattern PLACEHOLDER = Pattern.compile("%(?:[1-9][0-9]*\\$)?[a-zA-Z%]");

    @Test void everyLocaleHasExactlyTheEnglishKeysAndPlaceholders() throws Exception {
        Map<String, String> english = read("res/values");
        assertTrue(english.containsKey("row_style"), "generic UI strings belong in the resource contract too");
        for (String path : LOCALES) {
            Map<String, String> translated = read(path);
            assertEquals(english.keySet(), translated.keySet(), path + " keys");
            for (String key : english.keySet()) {
                assertEquals(placeholders(english.get(key)), placeholders(translated.get(key)), path + " placeholder mismatch for " + key);
            }
        }
    }

    @Test void everyRecipeHasAnEnglishResourceThatPreservesItsCanonicalName() throws Exception {
        Map<String, String> english = read("res/values");
        for (Recipes.Recipe recipe : Recipes.ALL) {
            String key = UiText.recipeKey(recipe);
            assertEquals(recipe.name, english.get(key), key);
        }
    }

    @Test void TaiwanAndHongKongAreSeparateResourceFiles() {
        assertTrue(new File(LOCALES[1], "strings.xml").isFile());
        assertTrue(new File(LOCALES[2], "strings.xml").isFile());
        assertNotEquals(new File(LOCALES[1]).getPath(), new File(LOCALES[2]).getPath());
    }

    @Test void bundledRegionalFontsCoverEveryNonAsciiTranslatedCharacter() throws Exception {
        File sc = new File("assets/fonts/RecipeLabCJKsc-Regular.ttf");
        File tc = new File("assets/fonts/RecipeLabCJKtc-Regular.ttf");
        File license = new File("assets/fonts/OFL.txt");
        File manifest = new File("assets/fonts/GLYPHS.txt");
        assertTrue(sc.length() > 1024, "missing Simplified Chinese font subset");
        assertTrue(tc.length() > 1024, "missing Traditional Chinese font subset");
        assertArrayEquals(new byte[] { 0, 1, 0, 0 }, Arrays.copyOf(Files.readAllBytes(sc.toPath()), 4), "SC subset must be static TrueType for old Android");
        assertArrayEquals(new byte[] { 0, 1, 0, 0 }, Arrays.copyOf(Files.readAllBytes(tc.toPath()), 4), "TC subset must be static TrueType for old Android");
        assertTrue(license.isFile(), "missing font license");
        assertTrue(manifest.isFile(), "missing glyph manifest");

        Set<Integer> glyphs = new HashSet<Integer>();
        for (String line : Files.readAllLines(manifest.toPath(), StandardCharsets.UTF_8)) {
            if (line.startsWith("U+")) glyphs.add(Integer.parseInt(line.substring(2), 16));
        }
        for (String locale : LOCALES) {
            for (Map.Entry<String, String> entry : read(locale).entrySet()) {
                String value = entry.getValue();
                for (int offset = 0; offset < value.length();) {
                    int codePoint = value.codePointAt(offset);
                    offset += Character.charCount(codePoint);
                    if (codePoint > 0x7f && !Character.isWhitespace(codePoint))
                        assertTrue(glyphs.contains(codePoint), locale + " " + entry.getKey() + " missing U+" + Integer.toHexString(codePoint).toUpperCase());
                }
            }
        }
    }

    private static Map<String, String> read(String path) throws Exception {
        File directory = new File(path);
        assertTrue(directory.isDirectory(), "missing " + path);
        File[] files = directory.listFiles((dir, name) -> name.endsWith(".xml"));
        assertNotNull(files, "cannot list " + path);
        Arrays.sort(files, Comparator.comparing(File::getName));
        Map<String, String> values = new LinkedHashMap<String, String>();
        for (File file : files) {
            NodeList nodes = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).getDocumentElement().getChildNodes();
            for (int i = 0; i < nodes.getLength(); i++) {
                Node node = nodes.item(i);
                if (!(node instanceof Element) || !"string".equals(node.getNodeName())) continue;
                Element element = (Element) node;
                String name = element.getAttribute("name");
                assertFalse(name.isEmpty(), file + " contains an unnamed string");
                assertNull(values.put(name, element.getTextContent()), path + " duplicates " + name);
            }
        }
        return values;
    }

    private static List<String> placeholders(String value) {
        List<String> found = new ArrayList<String>();
        Matcher matcher = PLACEHOLDER.matcher(value);
        while (matcher.find()) if (!"%%".equals(matcher.group())) found.add(matcher.group());
        return found;
    }
}
