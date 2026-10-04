# Localization

Recipe Lab speaks English, Simplified Chinese (简体中文) and Traditional Chinese (繁體中文). The first Chinese
translations came from Cysita ([#50](https://github.com/voxivoid/recipe-lab-sony-pmca/pull/50)); the strings added
since then were translated alongside them and are waiting on a native speaker's review.

## Choosing the language

**Hold MENU → Language.** Left / right (or centre) steps through *Auto*, *English*, *简体中文* and *繁體中文*, and the
app redraws in the new language at once. The choice is kept in the app's preferences (`language`, by code — `auto`,
`en`, `zh-Hans`, `zh-Hant`), so a power cycle keeps it and an uninstall does not.

*Auto* follows the camera's own language (its Android locale):

| camera locale | shows |
|---|---|
| `zh_TW`, `zh_HK`, `zh_MO` | Traditional Chinese |
| any other `zh` | Simplified Chinese |
| anything else | English |

Each language is named in its own script and drawn in its own font, so whoever lands in a language they cannot read
can still find theirs. The Chinese row label also says *Language* for the same reason.

## What changes, and what does not

Every word on screen follows the language: the panel, chips, pill, legend, brand list, menus, prompts and toasts.
Recipe names are translated too; a translated name keeps the canonical English name in small type under it, on the
panel and in the brand list, because that is the name the looks are known by.

What stays the same in every language:

- **The recipe identifiers.** `Recipe.name` is the canonical English. Favourites are stored by it, so switching the
  language never loses a mark.
- **Every file the app writes** — `samples.txt`, `locks.txt`, `keys.txt`, `diff.txt`. Compatibility reports quote them,
  so they stay English and parsable. So do the custom recipe files in `RECIPELAB`: people trade them, and a file has to
  read the same on every camera. A custom recipe's name is the user's own and is never translated; the name editor
  types only what every font the app carries can draw (`CustomRecipes.nameChar`), and the default *Untitled* is data
  like a recipe name, not text. The key logger page is English for the same reason.
- **Technical tokens** — `RAW`, `JPEG`, `DRO`, `EV`, `PE` / `CS`, kelvin, the A / B / G / M fine-tune, slot ids.
- **The app's name**, *RECIPE LAB*.

## How it works

All of it is camera-free and covered by `tools/test.sh`:

| | |
|---|---|
| `Lang.java` | the current language, the Language row's choices, what *Auto* resolves to, and the lookup: `Lang.t(key, args…)` for text, `Lang.label(key, canonical)` for names |
| `TextEn.java` | the English source, one `{ key, text }` row per string. Every other table falls back to it |
| `TextZhHans.java`, `TextZhHant.java` | the translations: the same keys and placeholders, plus the names below |
| `UiFont.java` | Android side: the typeface each language is drawn in, applied to every view |

**Names are not in the English table.** Recipe, brand, style, effect, sub-setting, quality and row names already exist
as canonical English data in `Recipes` and `Params` — that is what the manifest, favourites and the tests read — so a
translation adds them under a key made from that data with `Lang.slug`:

| prefix | made from | example |
|---|---|---|
| `recipe_` | `Recipe.name` | `recipe_kodak_portra_400` |
| `group_` | `Recipes.GROUPS` | `group_fuji_sim` |
| `style_` | `Recipes.STYLE_NAMES` (the runtime name) | `style_red_leaves` |
| `effect_` | `Recipes.PE_KEYS` (the runtime key) | `effect_toy_camera` |
| `sub_` | `Recipes.subValues` | `sub_posterization_bw` |
| `quality_` | `Params.Q_LABEL` | `quality_raw_jpg` |
| `row_` | `Params.ROW_NAME` | `row_sat` |

Renaming a recipe therefore renames its key: `LangTest` names the table that is missing it.

## Fonts

The camera's firmware font has no Chinese glyphs, so the APK carries two: Noto Sans CJK SC and TC (SIL Open Font
License), each cut down to the characters its table uses plus printable ASCII and the English table's characters —
about 105 KB each. One per script, so each gets its own glyph forms. Their line metrics are set to the camera
font's, so a Chinese line is no taller than an English one. They live in `assets/fonts/` with their licence
and provenance ([README.txt](../assets/fonts/README.txt)).

`tools/subset-font.py` rebuilds both from the tables. It fetches the pinned sources once into `out/font-source/`
(about 16 MB each, never committed) and needs `fonttools` (`python3 -m pip install fonttools`). `LangTest` reads each
font's cmap and fails on any character its table uses that the font lacks, so a translation change without a font
rebuild does not get past CI.

If a font cannot be loaded on the camera, the app falls back to the firmware font: the Chinese text is then boxes, but
the app still opens and English still works.

## Changing or adding a string

1. Add the key and its English to `TextEn.java`, and call it with `Lang.t("key", args…)`. Placeholders are Java's
   positional ones, `%1$s`, `%2$d`; plurals are separate keys (`status_picked_one` / `_many`).
2. Add the same key to `TextZhHans.java` and `TextZhHant.java`, keeping every placeholder.
3. `python3 tools/subset-font.py`, then `./tools/test.sh`.

`LangTest` checks that every table has exactly the keys it should, that placeholders match English, that every key
the code asks for exists and every English key is used, and the fonts.

## Adding a language

**Only when someone asks for it.** Every language is another table each new string must be translated into, and
nobody here can review most of them; a language arrives with a request, ideally with the person who will check it. The
camera's firmware font draws Latin, Cyrillic, Greek and Vietnamese, so those need no font; Japanese and Korean would
need one like Chinese (about 100 KB each). Arabic, Persian, Hebrew and Thai cannot be drawn properly: Android 2.3's
Canvas does no shaping or right-to-left layout.

1. Copy `TextEn.java` to `TextXx.java` and translate the values — keep every key and placeholder — then add the name
   keys above (`recipe_…`, `group_…`, …). `lang_name` is the language's name in its own script.
2. Add it to `Lang`: a constant, its table in `TABLES`, its code in `CODES`, a place in `CHOICES`, and a rule in
   `fromLocale` if *Auto* should pick it.
3. Add it to `tools/test.sh` `UNITS` and to `TRANSLATIONS` in `LangTest`.
4. If the camera font lacks its glyphs, add a font: an entry in `UiFont.ASSET`, one in `tools/subset-font.py`, one in
   `LangTest.FONTS`, and its licence in `assets/fonts/`.

## What is not verified

Tests prove which text the app draws, not how the camera draws it. On a camera, still to check: that both fonts load
and render on the bodies people use (a contributor saw Simplified Chinese on a NEX-5R with the earlier version of this
change, #50), that Chinese labels fit the chips, the legend and the menu at the camera's resolution, and what locale
each body reports for *Auto*.
