# Development notes

Reverse-engineering notes, the settings-store map, and how to build Recipe Lab.
For using the app, see the [README](../README.md). For the branch/commit/release rules,
see [CONTRIBUTING.md](CONTRIBUTING.md).

---

**Contents**

- [Source layout](#source-layout)
- [Settings slots](#settings-slots)
- [App menu and developer menu](#app-menu-and-developer-menu)
- [Exit rule](#exit-rule)
- [Live preview](#live-preview)
- [Custom recipes](#custom-recipes)
- [Keys on every body](#keys-on-every-body)
- [Developing on WSL](#developing-on-wsl)
- [Building](#building)
- [Unit tests](#unit-tests)
- [Installing on the camera](#installing-on-the-camera)
- [Versioning](#versioning)
- [Adding recipes](#adding-recipes)

---

## Source layout

```
AndroidManifest.xml            package com.voxivoid.recipelab
src/com/voxivoid/recipelab/
  MainActivity.java            UI state, key handling, the camera (CameraEx via reflection), store + sync
  Params.java                  the parameter rows: slot ids, store encodings, preview parameters, chip
                               navigation, HUD strings — pure functions, no Android, covered by test/
  Recipes.java                 the 77-entry table (the factory look, FACTORY, first in Sony, then the 76 others), brands,
                               GROUP_START / GROUP_COUNT, list navigation
  Favourites.java              the favourites list: stored by name in the app's preferences, and how the browser
                               walks the Favourites and Custom groups — pure functions, no Android, covered by test/
  CustomRecipes.java           custom recipes on the memory card: the versioned file format, what a file may hold,
                               names, file names, and reading / writing the RECIPES folder — no Android, covered by test/
  Library.java                 one index over the table's recipes and the custom ones after it: the wheel, the panel's
                               count — no Android, covered by test/
  NameEntry.java               the name editor: placeholder, capitals, delete, walking the keyboard grid — no Android,
                               covered by test/
  DevTools.java                the app menu and developer menu rows, the About and key-logger lines, and the sample
                               run's delays, messages and manifest — pure functions, no Android, covered by test/
  Keys.java                    scan codes, the press / hold gesture, the trash-hold guard, and the legend for the
                               keys a body has — pure functions, no Android, covered by test/
  KeyProbe.java                asks the camera which keys it has and which model it is (Sony classes by reflection);
                               every answer is null off the camera — no Android, covered by test/
  Lang.java                    the display language: the Language row's choice, what Auto resolves to, and the lookup
                               every word on screen goes through — no Android, covered by test/ (docs/LOCALIZATION.md)
  TextEn.java                  the English text, key by key: the source, and what every other table falls back to
  TextZhHans.java, TextZhHant.java   Simplified and Traditional Chinese, plus the recipe / brand / style names
  UiFont.java                  the typeface of each language: the camera's for English, assets/fonts for Chinese
  res/raw/ids.txt              every settings entry of 16 bytes or less, used by the snapshot/diff tool
  PickerView.java              Canvas-drawn brand browser (Favourites, Custom, then the brands; an empty group says how to fill it)
  KeyboardView.java            Canvas-drawn name editor: the field and NameEntry's keyboard grid
  MenuView.java                Canvas-drawn full-screen list: the app menu, the developer menu (rows with drawn icons, DevTools.icons; some with a
                               value left / right change in place, between drawn arrows), and read-only pages
                               (About, the key logger)
  Legend.java                  Canvas-drawn key icons and the favourite star, wrapped onto more lines when a row does not fit (Keys.lineCounts; camera font has no symbol glyphs).
                               Draws what Keys.hints builds: the four-way and the wheel are left out as self-evident,
                               a hold is its key's icon labelled "(hold)", and Fn, where the body has it, sits before
                               MENU when both close the list ("Fn / MENU close")
  StarView.java                the star next to the recipe name when it is a favourite
  SponsorLine.java             the small "Do you love this project? Sponsor it!" line, with a drawn heart, at the top of
                               the live view on the Full panel (DevTools.sponsorLine, Ko-fi); the About page lists Ko-fi and GitHub Sponsors
  HintBar.java                 legend view under the panel (uses Legend)
  NativeBackup.java            JNI: read / write / attr / sync
jni/jni.cpp                    Backup_read / Backup_write / Backup_sync_all via OpenMemories-Platform
jni/platform/                  git submodule: ma1co/OpenMemories-Platform
res/                           layout, shape drawables, launcher icon
assets/fonts/                  the Chinese fonts: Noto Sans CJK SC / TC cut down to what the tables use (OFL)
test/com/voxivoid/recipelab/   JUnit tests for the camera-free classes (see Unit tests)
test/com/sony/scalar/sysutil/  test doubles of the Sony classes KeyProbe reflects on
build.sh                       the build: ndk-build, aapt, javac, d8, zipalign, apksigner
build.cmd                      the same seven steps on Windows
tools/                         version computation, bumping, the unit tests, the CI gates, and the font subsetter
```

## Settings slots

Found by disassembling the camera app's parameter registration in `libObj.so`):

| setting | id | notes |
|---|---|---|
| Creative Style | `0x01070175` | index in the runtime `color-mode-values` list (verified by menu diff: 1 standard, 2 vivid, 3 neutral, 6 mono, **14 sepia**). 13 is a style the menu never selected for us, so `Recipes.STYLE_NAMES[13]` is `null` and the chip skips it; 4..12 are still guessed from the runtime list order and each needs its own menu diff |
| Contrast | `0x01070178` | signed byte |
| Saturation | `0x01070187` | signed byte, keep it in -3..+3 (see [Adding recipes](#adding-recipes)) |
| Sharpness | `0x0107018a` | signed byte |
| Picture Profile no. | `0x0107031c` | 0 off. No chip: every staged recipe writes 0, clearing the PP3 that the old matrix recipes stored (#38). PP3's alternate matrix did not hold on the A6000, so the app no longer uses it |
| WB mode | `0x01070019` | 1 auto, 14 colour temperature |
| WB Kelvin | `0x01070018` | Kelvin / 100 |
| WB A-B / G-M | `0x01070017` / `0x01070016` + per-mode copies: AWB `0x0107067f` / `0x0107067e`, colour temp `0x01070683` / `0x01070682` | signed, magenta positive (menu G1 = 0xff). The camera applies the per-mode copy (verified end-to-end) |
| Picture Effect | `0x010706f1` | index in `picture-effect-values` (verified: Retro = 4) |
| Effect sub-setting | `0x010709d8` high-key tint · `0x010706f3` toy tone · `0x010706ee` partial-colour hue · `0x010706ef` posterization | index in the runtime value list (high-key tint verified) |
| Exposure bias | `0x010700b8` + copy `0x01070c7f` | 1/3 EV steps, signed (verified: +0.7 = 2); both written |
| DRO | `0x01070104` (+ level byte `0x01070775`) | Off 0, Auto 1, Lv1–5 = 2–6; level byte 1 for Off/Auto, Lv n = n+1 (verified) |
| Quality: file format | `0x01070013` (+ mirror `0x01070aa9`) | RAW = 1, RAW+JPEG = 2, JPEG = 0 (verified) |
| Quality: JPEG level | `0x01070014` (+ mirror `0x01070aaa`) | Std = 0, Fine = 1 (verified) |

## App menu and developer menu

**Holding MENU** (600 ms) on the live screen opens the app menu, full screen (`MenuView`, rows and strings in `DevTools`). Up /
down or the wheel move, the centre button runs a row, a short MENU goes back a level and then closes it. It opens on
**Browse recipes**, so a body without Fn is hold MENU → centre away from the brand list:

| row | what it does |
|---|---|
| **Browse recipes** | the brand list, as Fn opens it |
| **New recipe** | the name editor, then the camera's current *stored* settings become a custom recipe ([Custom recipes](#custom-recipes)) |
| **Panel visibility** | a value — *Full* / *No keys* / *Label* / *Hidden* (`DevTools.PANELS`) — that left / right step through in place, wrapping, with the menu left open; centre steps forward. The same states trash cycles. *No keys* (`OV_QUIET`) is the full panel, chips and all (`Params.panelUp`), without the `HintBar` legend; closing the browser returns to it if it was left on it |
| **Language** | a value — *Auto* / *English* / *简体中文* / *繁體中文* — stepped the same way, kept in the app's preferences under `language` by code (`Lang.CODES`). The app redraws in it at once. *Auto* follows the camera's locale (`Lang.fromLocale`); each language's name is drawn in its own script and font. See [LOCALIZATION.md](LOCALIZATION.md) |
| **Reset settings** | asks `DevTools.resetTitle()` (Cancel highlighted), then stages `Recipes.FACTORY` and stores it (`writeAll`, so the quality prompt still asks when it must). The same look as **Factory**, the first Sony recipe, which is in the list like the others (it was hidden once, and users went looking for it) |
| **About** | the installed version (from `PackageManager` — never a string in the source, `tools/check-version.sh`), `model.name`, `version.platform`, and the source URL |
| **Developer >** | the developer menu below |

A row with a value (`DevTools.appValue` / `rowValue`) draws it at its right edge between two arrows, and the legend
swaps "select" for "change" while such a row is highlighted.

The **developer menu** holds the tools that are not part of using the app:

| row | what it does |
|---|---|
| **Settings snapshot** / **Settings diff** | the snapshot / diff tool below; the row's name says which half is next |
| **Read-only check — 26 slots** | the read-only check below: does this body flag any slot a recipe writes |
| **Shoot samples — 77 recipes** | the sample run below |
| **Settle delay** | a value — the delay the sample run waits after applying a recipe; left / right step through 0.8 / 1.2 / 2.0 / 3.0 / 5.0 s in place (centre steps forward), kept in the app's preferences |
| **Key logger** | every key event on screen, newest first, and appended to `keys.txt` in `getFilesDir()`: scan code, down / up, repeat count, Sony's logic code; the header is the model, the platform and what the probe found. Nothing else happens while it runs; **hold MENU** leaves |

New functions go in as app menu rows, not on new keys — see [Keys on every body](#keys-on-every-body).

### Snapshot / diff tool

How the slots above were found, and how to find the next one. The row runs `snapshotOrDiff()`, over every
id in `res/raw/ids.txt` (each settings entry of 16 bytes or less):

1. **First press** writes `snapshot.bin` into `getFilesDir()` — the current value of every id.
2. Leave the app, change **one** thing in the camera menus, reopen.
3. **Second press** re-reads every id, diffs it against the snapshot, shows the changed ones as
   `id:old>new` (first 14 on screen), appends the same line to `diff.txt` in `getFilesDir()`, and deletes
   `snapshot.bin` — so the next press starts a fresh snapshot.

Whatever shows up is the slot for the menu item you changed. Change one thing at a time or the diff is useless:
the camera rewrites unrelated entries on its own, so a second change means guessing which id belongs to what.

### Read-only check

Whether **backup protection** can stop a recipe on this body, which is the question the old `PROTECTED` badge
got wrong ([#19](https://github.com/voxivoid/recipe-lab-sony-pmca/issues/19)).

Protection is not a global write lock. Each settings slot carries attributes, and `BACKUP_ATTR_READ_ONLY`
(`Params.ATTR_READ_ONLY`) is the one that matters: a slot with that bit is refused with
`-BACKUP_ERROR_READ_ONLY` while protection is on and accepted once OpenMemories-Tweak has turned protection off.
A slot **without** the bit is writable either way. The badge called `Backup_guess_protection()`, which writes one
hardcoded read-only slot (`0x010d008f`) back over itself and reports whether that was refused — a true answer
about that slot, and no answer at all about Creative Style, saturation or white balance.

So the app asks per slot instead. The row reads `NativeBackup.attr(id)` for every entry of `Params.allSlots()` —
the 26 slots any recipe can write — and reports `26 recipe slots checked · none read-only`, or names the rows
that are flagged. The full list goes to `locks.txt` in `getFilesDir()`, one line per slot
(`01070175 STYLE attr=0`), which is what a compatibility report should quote. A slot the camera will not answer
for counts as writable: the write path reports a refusal properly, so a failed probe must not block a recipe.

The same check runs before every write (`Params.lockedFrom`, over the slots that write would touch). When
it finds one, nothing is written at all — a refusal half-way through would leave half a recipe in the store — and
the message names the settings and the Protection tweak. If the camera refuses anyway,
`Params.writeFailedMessage` names the slot that stopped it and how many bytes went in first.

**Run it on a factory body with protection still on.** That is the measurement the removal rests on: if a body
reports nothing read-only there, no recipe can ever be refused on it, whatever the protection flag says.

### Sample run

One frame per recipe, in table order — the capture half of issue #17. The run is a timed loop on the activity's
`Handler`, not a thread:

1. stage recipe *n* as a live preview, forcing **JPEG Fine** (a RAW frame carries no look, and a Picture Effect
   needs JPEG at all); the store is never written, so a run changes nothing permanent
2. wait the **settle delay** so the preview pipeline catches up
3. `takePicture`, then `cancelTakePicture` and `startPreview` `DevTools.SHUTTER_MS` later — the shutter key's
   press / release, automated
4. next recipe, until the table ends

While it runs, a sticky line counts the frames (`Shooting 12 / 77 · Velvia — MENU stops`) and **every key is
swallowed** so nothing walks the table underneath it; **MENU** stops the run. The run also stops in `onPause` — it
cannot outlive the camera it shoots with. When it ends, the recipe the user was on is staged again.

The frames are identified by **order**: the camera names the files, and the run appends its own list to
`samples.txt` in `getFilesDir()`, one line per frame —

```
# recipe-lab samples  ·  77 frames in recipe order  ·  settle 1200 ms  ·  frame|recipe|brand|values
01|FACTORY (ST)|Sony|Standard  0/0
02|Sony PT (portrait)|Sony|Portrait  0/0
```

**What the run cannot tell you.** Whether the settle delay is long enough is a property of the camera: too short and
a frame still carries the previous recipe's look. Shoot a run, then store two or three of the same recipes by hand
and shoot them again — if the frames differ, raise the delay in the menu and shoot again. Same for
`DevTools.SHUTTER_MS` and the `startPreview` between frames: both are what a capture needs on *this* body, and
neither a build nor `tools/test.sh` can say anything about them.

## Exit rule

The camera writes some live parameters (exposure bias, WB fine-tune) straight back into the settings
store, so on exit the app sets the live parameters to the *stored* values rather than to its launch snapshot —
otherwise a freshly stored recipe would be undone the moment the app closes.

## Live preview

Goes through `Camera.Parameters`: `color-mode`, `saturation`, `contrast`, `sharpness`,
`whitebalance`, `color-temperture-white-balance`, `light-balance-for-white-balance`,
`color-compensation-for-white-balance`, `rgb-matrix-mode` (always `false`), `picture-effect`,
`exposure-compensation` (1/3 EV steps), `dro-mode` + `dro-level`.
**Key scan codes** (all in `Keys`, Sony's `ScalarInput` names): wheel 522 / 523, top dial 525 / 526, four-way 103 /
108 / 105 / 106, centre 232, MENU 514 (SK1 229 on the NEX bodies), trash 595 (SK2 513), shutter 516 / 518, Fn 520.
Read but not bound: AEL 532, C1 622, DISP 608, PLAY 207 (all swallowed), MOVIE 515 and the zoom lever 610 / 611 (passed on).

## Custom recipes

Issues [#14](https://github.com/voxivoid/recipe-lab-sony-pmca/issues/14) and
[#15](https://github.com/voxivoid/recipe-lab-sony-pmca/issues/15). The user's own recipes are files on the memory card,
not app storage: they survive an uninstall, show up over USB Mass Storage, and travel by copying. Formatting the card
deletes them.

**Where.** `RECIPES/` at the root of `Environment.getExternalStorageDirectory()` (hence `WRITE_EXTERNAL_STORAGE` in the
manifest), one file per recipe. Apps reach the card through Sony's FUSE layer, `libInfraFuFsys.so`, mounted at
`/android/mnt/sdcard` (`allow_other,direct_io,atomic_o_trunc`) — not the kernel's vfat. It takes **DOS 8.3 names
only**: its `getattr` answers `ENAMETOOLONG` past them, it carries an upper-casing table, and the one PMCA app known to
write to the card, PMCADemo, uses `PMCADEMO/LOG.TXT`. A first build that used `RECIPELAB` (nine letters) got
`cannot create /mnt/sdcard/RECIPELAB` on an A6000. So the folder is seven letters and `CustomRecipes.fileName` makes
`GOLDENHO.TXT` out of "Golden Hour" — the first eight letters and digits, in capitals — and `GOLDENH2.TXT`, `GOLDEN10.TXT`
… when a file already has it (compared ignoring case); the recipe's name lives in the file. The folder is read again on every `onResume`, so a card edited on a computer or swapped is picked up. No card
(`getExternalStorageState() != MEDIA_MOUNTED`): the Custom group says so and saving refuses with a toast.

**Index.** `Library` puts them after the table: `recipe` in `0 .. BASE-1` is `Recipes.ALL`, `BASE ..` a custom recipe, A
to Z. An index moves when a recipe is added, renamed or deleted, so nothing persistent holds one: the last recipe is
kept as `customRecipe` (its name) beside `recipe`, and favourites by name.

**The file.** `key = value`, `#` comments, UTF-8 (a BOM is ignored), English always. `CustomRecipes.encode` writes:

| key | values | missing → |
|---|---|---|
| `format` | the reader to use; this build writes and reads `1` | 1 |
| `name` | 1–24 of `A–Z a–z 0–9` and ` -.'()&+` — what the name editor can type and every bundled font can draw | the file name |
| `made-on` | the model it was saved on (`model.name`), informational — setting ids differ across bodies | — |
| `style` | a runtime name from `Recipes.STYLE_NAMES` (only identified styles) | standard |
| `saturation`, `contrast`, `sharpness` | -3 .. +3 | 0 |
| `effect` | a runtime name from `Recipes.PE_KEYS` | off |
| `effect-option` | one of `Recipes.subValues(effect)`; only read for an effect that has options | the first |
| `white-balance` | `auto`, `keep` (leave the camera's alone — what a preset captured by *New recipe* becomes), or `2500K` .. `9900K` in hundreds | auto |
| `amber-blue`, `green-magenta` | -7 .. +7, amber / green positive | 0 |
| `exposure` | -5.0 .. +5.0 in thirds, as the chip shows it: `+0.7`, `-1.3`, `0` | 0 |
| `dro` | `off`, `auto`, `1` .. `5` | auto |

Quality is not part of a recipe: it follows the Factory base like every other recipe.

**Versions.** The rules that keep old files readable:

- A key a reader does not know is ignored. **Adding** a key never needs a new format; give it a default that means
  "what an older file meant".
- **Changing what a key means** (units, a renamed value) bumps `CustomRecipes.FORMAT`, and `parse` gains a case for the
  new number while the old case stays: every file once written keeps loading.
- A file whose `format` is newer than the build is skipped as a whole (*made by a newer Recipe Lab*), never half-read —
  its values end up in the settings store.

**Untrusted input.** A file may come from anyone. Every value is checked against what the store takes
(`CustomRecipes.problem`, the same ranges as the chips); one bad value skips the file with the reason, nothing is
clamped. A second file with a name already loaded is skipped too, so a card brought in never replaces a recipe. Files
over 8 KB, hidden files and anything but `*.txt` are ignored. Skipped files are toasted once, when the Custom group is
first shown after a load.

**Writing.** `CustomRecipes.save` writes `SAVING.TMP` (removing one a cut-short save left), tries to `fsync` it — the FUSE
layer has no `fsync` handler, so a refusal is ignored — then renames it over the target; an edit, or a rename to a name
with the same 8.3 file, deletes the old file first, any other rename deletes it after. A camera switched off mid-write
leaves the old file or the new one.

**The flows** (`MainActivity`, question text in `CustomRecipes`):

| | |
|---|---|
| a recipe — any recipe — has edits (`Params.differsFromRecipe`) | they stay a preview, saved nowhere: the **EDITED** badge shows, and with the full panel up a row of buttons shows under the chips, `CustomRecipes.editActions(custom, applied)`, in this order: **Save** (custom only: its file rewritten, then written to the camera unless it has the values already — a built-in recipe never changes), **Apply** (the write; left out once the store has the values), **Copy** (name editor on `copyName` — *Velvia 2*, *Golden Hour 2*, the name cleaned of what a name may not hold — then the new recipe is written to the camera too), **Discard** (asks *Discard edits?* first, then `stageRecipe`, and the write too when the edits had been applied). Down from the chips reaches the row (`Params.nextLine`: recipe → chips → buttons → recipe), left / right / the dial choose, centre presses; the legend is `H_ACTIONS` |
| the badges | two, answering two questions: ACTIVE / PREVIEW — does the store have what the chips show (`dirty()`); and beside it **EDITED** (`badge_edited`) — are they not the recipe's own (`edited()`). ACTIVE EDITED is an applied edit, PREVIEW EDITED one not stored yet |
| a write succeeded after edits (**Apply**; badges **ACTIVE** **EDITED**) | the chips keep what was written (`load()` only) instead of re-staging the recipe; a refused write re-stages the recipe |
| centre on the recipe line with edits | *Discard edits to Velvia?* — **Discard** re-stages the recipe and writes it (the pick it asked for), **Cancel** highlighted. Saving and copying live only in the button row |
| leaving an edited recipe — wheel / left / right on the recipe line, Fn or Browse recipes, MENU out of the app — with edits neither saved nor applied (`editsAtRisk`) | the same question; **Discard** goes on with the move (`unlessEditsLost`). After Apply the camera has the edits, so nothing is asked |
| app menu → **New recipe** (`APP_NEW`, below Browse recipes) | name editor, then the camera's *stored* rows (`cur`) become the recipe — set a look in Sony's menus, bottle it. An unidentified style refuses with a toast before the editor opens |
| hold MENU on a custom recipe in the brand list (`onCustomRow`) | **Rename**, **Delete** (asks again), **Cancel** — Cancel highlighted. MENU is press / hold there: the release closes the list, the hold (`HOLD_MS`) opens the options. Hold centre marks a favourite on every recipe, custom ones included. On the live screen hold MENU stays the app menu |

The name editor (`NameEntry`, `KeyboardView`) is a key grid on universal keys: four-way moves (wrapping, keeping the
column across rows of different widths), wheel / dial walk cell by cell, centre types, trash deletes, MENU cancels, the
**OK** cell saves. A new recipe opens on `CustomRecipes.defaultName` (*Untitled*, *Untitled 2* …) drawn dimmed: the
first character replaces it, delete clears it, OK keeps it. A rename opens on the real name. The first letter is a
capital, then lower case; the shift cell gives one more capital. Names are unique ignoring case.

**On the camera, still to prove:** that `getExternalStorageDirectory()` is the memory card on every body, that the folder
is visible over USB Mass Storage, that a file a computer gave a long name is still read (through its 8.3 alias), that
rename survives a power-off mid-save, and — as always — that a custom
recipe picked, then power-cycled, is still the camera's look.

## Keys on every body

Issue #18. The A6000 has Fn, AEL and C1; the A5100 and A5000 have none of them, the A7S II's AEL did nothing in both
reports, and the compacts differ again. So every function has a route on keys **every** body has — wheel, four-way,
centre, MENU, trash, shutter — and Fn is the one shortcut on top:

| function | every body | shortcut |
|---|---|---|
| store the recipe | centre | — |
| favourite | hold centre | — |
| brand list | hold MENU → Browse recipes | Fn |
| hide the panel | trash, or hold MENU → Panel visibility | — |
| reset to factory | hold trash, or hold MENU → Reset settings — both ask first | — |
| developer tools | hold MENU → Developer | — |
| exit | MENU | — |

**Unbound on purpose.** Trash hides the panel on every body, so AEL — missing on the A5100 / A5000, dead on the A7S II —
adds nothing. C1 only ever opened the developer menu, which the app menu now reaches. DISP is dropped because
of what it might be on a body where it is printed on the wheel's top (A6000, A5100, A7 bodies): there the press arrives
as `K_UP` — chip navigation works on the A6000 — but a body that sent 608 for it would have its wheel-up hide the panel
or close the list. All three are swallowed on every screen and shown by the key logger.

**Holds.** Three keys have a press and a hold (`Keys.Hold`, `HOLD_MS` 600 ms): centre (pick / favourite), MENU (exit /
app menu) and trash (hide / ask to reset). The hold is timed with a `Handler.postDelayed` armed on the press and
cancelled on the release, so it does not depend on the firmware delivering key-repeat events. Centre and MENU run their
press action on the **release**; a hold that has fired swallows the release. Trash too: it hides the panel (or closes
the brand list) on the release, and a hold brings up the reset question instead, so holding it never touches the panel.
The question defaults to Cancel, and the legend never names the hold: it is there for whoever needs it, not as an
invitation. Holds are cleared in `onPause`, and the release bookkeeping runs whatever is on screen, so a release that
lands on a prompt does not leave a key stuck.

**The trash hold and a lost release.** A fired timer means the key is still down — the release would have cancelled it
— except on a body that loses the release. So when `ScalarInput.getKeyStatus(scan).status` says the key is already up,
the fired hold counts as a press and hides (`Keys.trashHoldActs`); when the camera will not say, it is a hold, and the
worst case is a question that defaults to Cancel.

**Repeat.** A key-repeat (`getRepeatCount() > 0`) of centre, MENU, trash or Fn is dropped, so a held Fn no longer
opens and then closes the list; the four-way and the dials keep theirs.

**Which keys a body has.** `KeyProbe` asks `com.sony.scalar.sysutil.ScalarInput.getKeyStatus(scan).valid` by reflection,
once per key — how Sony's own app framework counts dials and gates the zoom lever. The legend names Fn only when it
reads 1; when the call is missing or throws, the legend names the universal keys alone. There is no per-model table: nobody can keep one verified. Model and platform come from
`ScalarProperties.getString("model.name" / "version.platform")` — `Build.MODEL` is `ScalarA` on every body — and are
only shown, never branched on. None of this has been checked on a camera yet; the key logger is how it will be.

**Not done yet, until the logger has codes to show:** still review on PLAY (#43), the A7 II AF/MF–AEL lever codes
(589 / 638), what to do with the zoom lever and flash (HX60, #42), and swapping the top dial's
direction on the platform-1 NEX bodies, which Sony's framework does.

On the first launch of a build with these keys a toast says where things went (`Keys.NOTICE`, once, pref
`keysNoticeSeen`).

Favourites live in `getPreferences(MODE_PRIVATE)` under `favourites`, as recipe **names** joined with `|` (so a table
that gains a recipe does not shift the marks); a name the table no longer has is dropped on load. A custom recipe is
stored as `custom:<name>`, and its mark is kept while its card is out (`Favourites.encode` carries over the custom names
it cannot resolve); rename moves it, delete drops it. They are app storage, not the camera settings store: a power cycle
keeps them, an uninstall does not.

## Developing on WSL

This is how the machine is set up: everything except talking to the camera happens inside WSL.

**Keep the repository on the Linux filesystem** — `~/code/...`, never `/mnt/c/...`. Windows
drives are reached over the 9p protocol, where each file operation costs milliseconds instead
of microseconds. A build does thousands of them, so the difference is seconds versus minutes,
and every git command crawls.

Where things live:

| | |
|---|---|
| `~/toolchains/jdk17` | JDK 17 (Temurin) |
| `~/Android/Sdk` | build-tools 30.0.3, platform-28, NDK r16b |
| `~/.keys/recipelab-release.keystore` | the signing key, `chmod 600` |
| `~/code/sony-pmca-re` | Sony-PMCA-RE — dumps, the updater shell, installing |
| `~/code/a6000-dumps` | firmware and settings-store dumps |
| `~/code/pmca-scripts` | camera helper scripts |

Worth putting in `~/.bashrc`:

```bash
export JAVA_HOME=$HOME/toolchains/jdk17
export ANDROID_SDK=$HOME/Android/Sdk
export ANDROID_NDK=$ANDROID_SDK/ndk/16.1.4479499
export PATH="$HOME/.local/bin:$PATH"      # gh lives here
export BROWSER=wsl-browser                # so `gh auth login` opens Windows Firefox
```

To build with the project key instead of a throwaway one:

```bash
export ANDROID_KEYSTORE_B64="$(base64 -w0 ~/.keys/recipelab-release.keystore)"
export ANDROID_KEYSTORE_PASSWORD=android
export ANDROID_KEY_ALIAS=probe
export ANDROID_KEY_PASSWORD=android
```

An APK signed with a different key **cannot be installed over an existing Recipe Lab** — the
app has to be removed first — so use these whenever you are updating a camera that already
has it.

`npm ci` is only needed for the release tooling (semantic-release, `tools/next-version.sh`).
No JavaScript ships in the APK.

### Things that bite

- **`apksigner` and `keytool` exec `java` from `PATH`**, so `JAVA_HOME` on its own is not
  enough; `build.sh` prepends `$JAVA_HOME/bin` for exactly this. Without it, step 6 fails with
  `exec: java: not found`.
- **`BROWSER` is word-split**, so a path containing spaces cannot be used directly.
  `~/.local/bin/wsl-browser` is a two-line wrapper that quotes the Windows Firefox path.
- **`sudo` prompts for a password**, so anything needing root — usbip tools, udev rules —
  cannot be scripted unattended.
- **The camera is invisible from WSL.** See [Installing on the camera](#installing-on-the-camera).

## Building

Toolchain, both platforms: **JDK 17**, Android SDK **build-tools 30.0.3** with a platform jar (**API 28**), and
**NDK r16b** — the last NDK with the GCC toolchain this Android 2.3.7 target needs. `jni/Application.mk` pins
`APP_ABI := armeabi`, `APP_STL := stlport_static`, `APP_PLATFORM := android-14`, `NDK_TOOLCHAIN_VERSION := 4.9`;
that combination is what rules out every later NDK.

```
git clone --recursive https://github.com/voxivoid/recipe-lab-sony-pmca.git
cd recipe-lab-sony-pmca
```

**Linux / WSL / macOS** — `build.sh`. This is what CI runs and the supported way to build:

```bash
export JAVA_HOME=$HOME/toolchains/jdk17
export ANDROID_SDK=$HOME/Android/Sdk
export ANDROID_NDK=$ANDROID_SDK/ndk/16.1.4479499
./build.sh                 # X.Y.Z-dev.N
RELEASE=1 ./build.sh       # X.Y.Z — the tag must match the manifest
```

Setting the toolchain up from nothing, no root required:

```bash
# JDK 17
curl -sL -o jdk.tar.gz "https://api.adoptium.net/v3/binary/latest/17/ga/linux/x64/jdk/hotspot/normal/eclipse"
mkdir -p ~/toolchains/jdk17 && tar xzf jdk.tar.gz -C ~/toolchains/jdk17 --strip-components=1

# Android cmdline-tools, then the three packages
curl -sL -o cmdline.zip "https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip"
mkdir -p ~/Android/Sdk/cmdline-tools && unzip -q cmdline.zip -d /tmp/ct
mv /tmp/ct/cmdline-tools ~/Android/Sdk/cmdline-tools/latest
yes | ~/Android/Sdk/cmdline-tools/latest/bin/sdkmanager --licenses >/dev/null
~/Android/Sdk/cmdline-tools/latest/bin/sdkmanager \
  "build-tools;30.0.3" "platforms;android-28" "ndk;16.1.4479499"
```

About 3 GB installed. `build.cmd` is the Windows equivalent and is kept in step with
`build.sh`, but the toolchain it needs is no longer installed on this machine.

To sign with the project key rather than a throwaway one, set
`ANDROID_KEYSTORE_B64` (`base64 -w0 <keystore>`), `ANDROID_KEYSTORE_PASSWORD`,
`ANDROID_KEY_ALIAS` and `ANDROID_KEY_PASSWORD` — the same four values CI holds as secrets.

Both scripts park the platform's `errno.h` shim (updater-only, it shadows the NDK header), then run ndk-build,
aapt, javac (`-encoding UTF-8`), d8 (invoked as `java -cp d8.jar`, because `d8.bat` uses whatever Java is on
PATH), zipalign and apksigner — **v1 signing only**, since the camera does not understand v2/v3.
`build.sh` restores `errno.h` from an `EXIT` trap, so an aborted build never leaves the submodule dirty.

**Signing.** With no keystore configured, both scripts generate a throwaway key. An APK signed with a
different key **cannot be installed over an existing one** — the camera would need the app removed first.
CI therefore signs with the project key, held as the `ANDROID_KEYSTORE_B64` repo secret; set the same four
`ANDROID_KEYSTORE_*` variables locally if you need a build that updates an existing install in place.

## Unit tests

```bash
export JAVA_HOME=$HOME/toolchains/jdk17
./tools/test.sh              # everything
./tools/test.sh Writes       # only test classes whose name contains "Writes"
```

That is the whole of the `test` CI job on work branches; `dev-build` runs the same script before every
development build and `create-release` before anything is pushed to `main`. It needs a JDK 17 and nothing else: the classes listed in `UNITS`
are compiled against the bare JDK — no `android.jar`, no NDK — then the tests under `test/` are compiled and run
with the JUnit 5 console launcher, one jar fetched from Maven Central into `out/test/` on first use and checked
against a SHA-256 pinned in the script (`JUNIT_JAR=<path>` points it at a copy when offline). Reports land in
`out/test/reports/`.

**What is covered.** Everything that decides without the camera lives in `Params`, `Recipes`, `Favourites`,
`CustomRecipes`, `Library` and `NameEntry`, and the tests pin it down:

| | |
|---|---|
| `RecipesTest` | the table itself — 77 entries (76 listed + the factory look, which navigation skips), group order, every value inside its row's range, kelvin in whole hundreds, sub-parameters that exist for the effect; labels, `summary()`, wrap-around navigation |
| `ParamsCodecTest` | how the store encodes each row (DRO bytes, any Picture Profile reading as on, magenta-positive G-M, the quality pair, signed vs unsigned slots) and how it reads back |
| `ParamsWritesTest` | which bytes ENTER writes for a recipe — golden lists for a few, and every recipe stored over a factory camera, then on top of each other, read back through the same decoder |
| `ParamsPreviewTest` | the `Camera.Parameters` the live preview sets, recipe by recipe |
| `ParamsChipsTest` | chip visibility, stepping (wrap vs clamp, the effect → SUB / quality side effects), LEFT/RIGHT and UP/DOWN landing spots, chip text |
| `ParamsHudTest` | the meta line (warnings only: RAW under an effect, no live preview — the values are on the chips), the minimal pill, the quality prompt |
| `ParamsToolsTest` | the snapshot tool's id list — including that `res/raw/ids.txt` is well formed and lists every slot the app writes — and its diff lines |
| `DevToolsTest` | the app menu and developer menu rows, About and the key logger's lines, settle delays, the sample run's progress / finish lines, and its manifest — a parsable line per recipe, in run order |
| `KeysTest` | the press / hold gesture, the trash-hold guard, and that the legend never names a key the body lacks — every function on a universal key, Fn only when reported, the order pick · browse · save · fav · menu · hide · exit, the reset hold never hinted |
| `KeyProbeTest` | that the key probe answers "unknown" off the camera instead of throwing |
| `KeyProbeCameraTest` | the key probe against test doubles of Sony's `ScalarInput`, `KeyStatus` and `ScalarProperties` (`test/com/sony/scalar/sysutil/`, shaped like the OpenMemories-Framework stubs): the reflection finds the real signatures, only `valid == 1` is a key, only `status == 1` is a press. The doubles throw for anything a test did not set up, which is how the "off the camera" answers stay null |
| `FavouritesTest` | the favourites list — stored by name, unknown names dropped, marking order kept, toggle, the highlight after a removal, custom recipes as `custom:<name>` kept while their card is out — and the browser's group order: Favourites, Custom with its New row, the brands |
| `CustomRecipesTest` | the recipe file: every built-in look survives a round trip, hand-edited files (BOM, CRLF, case, comments, unknown keys) still read, a newer format or any out-of-range value skips the file with the reason, names and default names, file names, and save / edit / rename / delete / load against a temporary folder |
| `LibraryTest` | one index over the table and the custom recipes: the wheel walks into them and wraps, the panel counts them among themselves |
| `NameEntryTest` | the name editor: the placeholder the first character replaces, capitals, delete, the length limit, walking the grid with the four-way and the wheel |
| `LangTest` | the display language: every table has every key and keeps English's placeholders, every key the code asks for exists, the Language row's choices and what Auto resolves to, that samples.txt, locks.txt and favourites stay English in any language, and that each bundled font has a glyph for every character its table uses (read from the font's own cmap) |

**What is not, and cannot be.** `MainActivity` (key dispatch, overlays, the camera and the JNI store), the
Canvas views (`PickerView`, `PromptView`, `MenuView`, `KeyboardView`, `HintBar`, `Legend`), `UiFont` and `jni/jni.cpp` need a running camera or an
Android runtime; there is no Gradle and no Robolectric here, and a mock of `CameraEx` would prove nothing. Those
stay on the [on-camera checklist](CONTRIBUTING.md#on-the-camera). Likewise the slot ids themselves: a
test can show that the app writes `0x01070175 = 6`, not that the camera means B&W by it.

**Keeping it that way.** New logic that does not need the camera goes into `Params` (or `Recipes`, `Favourites`,
`DevTools`, `Keys`, `KeyProbe`, `Lang`, or a new class listed in `UNITS` in `tools/test.sh`) with a test next to it, and is called from `MainActivity`, never the
other way round. `tools/test.sh` compiles those classes without `android.jar` on purpose: an `android.*` import in either fails there before it fails in CI. Tests
are plain JUnit 5 (`org.junit.jupiter.api`), one behaviour per method, no mocking library; `Fixtures` has a
factory-fresh camera as rows and as store bytes and a fake store to write into.

### Installing on the camera

**Build in WSL, install from Windows.** WSL2 is a VM with no USB controller, so the camera is
only reachable from the Windows side. Everything else — building, dumps, git, releases — is
WSL-native.

```bash
./build.sh                              # in the repo
~/code/pmca-scripts/install-to-camera.sh   # copies the APK over and drives Sony-PMCA-RE
```

The camera must be on, with `Setup → USB Connection` set to **Mass Storage**. The script
uses the Windows Python at
`C:\Users\voxiv\AppData\Local\Programs\Python\Python311\python.exe` against the
Sony-PMCA-RE checkout at `C:\Users\voxiv\pmca\src`; those two are the only things this
project still needs on Windows.

If you would rather install from inside WSL as well, `~/code/pmca-scripts/setup-usb-wsl.sh`
sets up the Linux half of USB forwarding (usbip tools, the `054c` udev rule, pyusb). The
Windows half is `usbipd-win`: `winget install dorssel.usbipd-win` once from an Administrator
PowerShell, `usbipd bind --busid <id>` once per camera, then `usbipd.exe attach --wsl --busid
<id>` after each replug. Forwarding a USB device needs a Windows-side driver either way —
that part cannot be removed.

## Versioning

`AndroidManifest.xml` `android:versionName` is the **single source of truth**, and always holds the *next
target release* (`X.Y.Z`, no suffix). Nothing else stores a version — not the README, not the Java source.

```
versionCode = MAJOR*10_000_000 + MINOR*100_000 + PATCH*1_000 + P
P = N    dev prerelease, N = commits since the last v* tag (1..998)
P = 999  release
```

`999` makes a release outrank every prerelease before it, so `1.1.0` installs cleanly over `1.1.0-dev.42`
instead of being refused as a downgrade.

| script | does |
|---|---|
| `tools/version.sh` | computes `VERSION_NAME` / `VERSION_CODE` for a build; sourced by `build.sh` |
| `tools/bump-version.sh <x.y.z>` | opens the next cycle — the only way a version is ever typed |
| `tools/check-version.sh` | CI gate: manifest is consistent and no version mirror has crept back in |
| `tools/dev-notes.js <version>` | prints the notes for a dev build from the commits since the last `v*` tag, using the same semantic-release generator and preset as a release |

A build never mutates the checked-in manifest; it writes `out/AndroidManifest.xml` and points `aapt` there.

## Adding recipes

Adding a recipe is one line in `Recipes.java` inside its brand block. Adding a brand is a new entry in `GROUPS` plus
a block of recipes.

**Saturation, contrast and sharpness stop at ±3.** The live preview takes more (saturation to ±16), which is why a
look beyond the menu range looks right inside the app, but the camera does not keep it: after the app exits the menu
reads a stored Vivid `+5` back as `+1` and a Neutral `-4` as `-1`, and the look goes with it. `Params.ROW_MIN` /
`ROW_MAX` stop the editor at ±3, the preview clamps to it, and `RecipesTest` fails any recipe outside it. For more
punch reach for Vivid and contrast; for less, Neutral at `-3` with DRO off.

**Colour-temperature recipes cannot be judged indoors.** A fixed kelvin renders relative to the light in the room,
not to the recipe's intent: 5600K under warm indoor light comes out amber, and 3200K comes out nearly neutral.
Shoot those eight in daylight before deciding anything about them.
