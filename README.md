<p align="center">
  <img src="dist/icon-512.png" width="96" alt="Recipe Lab icon">
</p>

<h1 align="center">Recipe Lab</h1>

<p align="center">
  Film simulations and camera looks for the <b>Sony</b> cameras that support <b>PlayMemories Camera Apps</b>, stored in the camera itself.<br>
  <sub>
    <img src="https://img.shields.io/github/v/release/voxivoid/recipe-lab-sony-pmca?label=version" alt="version"> ·
    <a href="https://github.com/voxivoid/recipe-lab-sony-pmca/releases/latest/download/RecipeLab.apk">Download the app</a> ·
    <a href="docs/SAMPLES.md">See every recipe</a>
  </sub>
</p>

<h3 align="center">Do you love this project? Sponsor it!</h3>

<p align="center">
  <a href="https://github.com/sponsors/voxivoid"><img
    src="https://img.shields.io/badge/Sponsor_on_GitHub-%E2%9D%A4-db61a2?logo=githubsponsors&logoColor=white&style=for-the-badge"
    alt="Sponsor on GitHub"></a>
  <a href="https://ko-fi.com/voxivoid"><img
    src="https://img.shields.io/badge/Tip_on_Ko--fi-%E2%98%95-ff5e5b?logo=kofi&logoColor=white&style=for-the-badge"
    alt="Tip on Ko-fi"></a>
</p>

<p align="center">
  <sub>
    The app is free and stays free. Sponsoring pays for keeping it maintained, for building new features, and for
    buying the cameras it has to be tested on — I currently only own a Sony a6000.
  </sub>
</p>

---

**Contents**

- [What it is](#what-it-is)
- [The recipes](#the-recipes)
- [Compatibility](#compatibility)
  - [Cameras that run PlayMemories apps](#cameras-that-run-playmemories-apps)
  - [Cameras that cannot run camera apps](#cameras-that-cannot-run-camera-apps)
- [Installing](#installing)
- [Using it](#using-it)
- [What it changes](#what-it-changes)
- [Uninstalling](#uninstalling)
- [Troubleshooting](#troubleshooting)
- [For developers](#for-developers)
- [Credits](#credits)

---

## What it is

Recipe Lab is a small app that runs on the Sony A6000 itself. It comes with 76 colour recipes that recreate the looks
of other cameras — Fuji film simulations, Ricoh GR image controls, Leica, Hasselblad, Canon and Nikon colour, Sony's
newer Creative Looks — and of classic film stocks from Kodak, Fuji, Cinestill, Agfa and Ilford.

You turn the wheel, watch the live image change, press a button. From then on the camera shoots that way in **every
mode**, photo and video, with the app closed. Turn it off and on, it is still there.

> **Honest note.** The A6000 has no Picture Profile menu and cannot store tone curves. Every recipe is built only from
> what this camera *can* keep: Creative Style, saturation, contrast, sharpness, white balance, exposure bias, Picture
> Effect and one hidden colour setting Sony never exposed. So these are approximations of a look, not copies of another brand's colour science.

## The recipes

| brand | recipes |
|---|---|
| **Sony** | PT, NT, VV, VV2, FL, IN, SH — plus the factory look, under **Reset settings** |
| **Fuji simulations** | Provia, Velvia, Astia, Classic Chrome, Classic Negative, Nostalgic Neg, Reala Ace, Pro Neg Std / Hi, Eterna, Eterna Bleach Bypass, Acros, Acros +Ye / +R / +G, Sepia |
| **Fuji film** | Pro 400H, Fortia 50, Superia 400, C200, Natura 1600 |
| **Kodak** | Portra 160 / 400 / 800, Gold 200, Ultra Max 400, Color Plus 200, Ektar 100, Ektachrome E100, Kodachrome 64, Vision3 500T, Vision 200T (Asteroid City), Tri-X 400, Tri-X 1600 (pushed), T-Max |
| **Cine** | Cinestill 50D, Cinestill 800T, Classic Cinema, Rec709 Video |
| **Ricoh GR** | Positive Film, Negative Film, Bleach Bypass, Retro, Cross Process, Hi-Contrast B&W, Hard Monotone, Soft Monotone |
| **Leica** | Contemporary, Classic, Eternal, Monochrom |
| **Hasselblad** | HNCS Natural |
| **Canon / Nikon** | Canon Standard / Portrait / Faithful, Nikon Flat / Vivid |
| **Panasonic / Olympus** | L.Monochrome D, L.ClassicNeo, Pop Art, Pale & Light |
| **Other stocks** | Agfa Vista 200, Agfa Ultra 100, Polaroid / Instax |
| **Ilford** | HP5, FP4, Delta 100, Delta 3200, Pan F 50 |

**[See every recipe on the same subject →](docs/SAMPLES.md)** — 77 frames, one scene, one exposure, straight out of
the camera.

Recipes marked **PE** in the app (Acros +R, Tri-X 1600, GR Retro, GR Hi-Contrast B&W, Sony SH, Polaroid) are built on
a Picture Effect because, against the reference frames, its tone
curve gets closer than Creative Style can; everything else stays Creative Style on purpose.

Not included, because the camera simply cannot do them: log profiles (S-Log, V-Log, Blackmagic Film, Cinelike D) and
tinted black & white (selenium, cyanotype). Sony's camcorder *Cinematone* gamma exists in the firmware but the A6000's
camera layer neither lists nor accepts it, so that door is closed too.

## Compatibility

Recipe Lab has no model check in it, and every Sony body that runs PlayMemories apps has the same settings store — so
it should install and work beyond the A6000.

The catch: the setting IDs were found on an A6000 and may sit elsewhere on another body, so a recipe could land in the
wrong place. A camera gets a ✅ only once someone has stored a recipe on it and power-cycled the camera.

Tried one? File a
[compatibility report](https://github.com/voxivoid/recipe-lab-sony-pmca/issues/new?template=compatibility_report.yml).
Failures are as useful as successes.

### Cameras that run PlayMemories apps

✅ someone has run it on that body · ❔ app-capable, nobody has reported back yet

| camera | model code | status | comment |
|---|---|---|---|
| **A6000** | ILCE-6000 | ✅ | built and tested on it |
| **A6500** | ILCE-6500 | ✅ |  |
| **A5100** | ILCE-5100 | ✅ | no **Fn** button: the brand list is under **hold MENU → Browse recipes** |
| **A7 II** | ILCE-7M2 | ✅ |  |
| **A7** | ILCE-7 | ✅ |  |
| **NEX-5T** | NEX-5T | ✅ | the stored values did not all match, and some menu labels read differently ([#22](https://github.com/voxivoid/recipe-lab-sony-pmca/issues/22)) |
| **A6300** | ILCE-6300 | ✅ |  |
| **A7R** | ILCE-7R | ✅ |  |
| **A7R II** | ILCE-7RM2 | ✅ |  |
| **RX100 V** | DSC-RX100M5 | ✅ |  |
| **HX60 / HX60V** | DSC-HX60 | ✅ | zoom and flash could not be used ([#42](https://github.com/voxivoid/recipe-lab-sony-pmca/issues/42)) |
| **A7S II** | ILCE-7SM2 | ✅ | the image stabiliser misbehaved with a manual lens while the app was open ([#48](https://github.com/voxivoid/recipe-lab-sony-pmca/issues/48)) |
| **RX100 III** | DSC-RX100M3 | ✅ |  |
| **RX1R II** | DSC-RX1RM2 | ✅ |  |
| **A5000** | ILCE-5000 | ✅ | no **Fn** button: the brand list is under **hold MENU → Browse recipes**; one sample run froze the camera after about eight frames, and only pulling the battery brought it back ([#58](https://github.com/voxivoid/recipe-lab-sony-pmca/issues/58)) |
| **HX90 / HX90V** | DSC-HX90 | ✅ |  |
| A7S | ILCE-7S | ❔ |  |
| NEX-5R | NEX-5R | ❔ |  |
| NEX-6 | NEX-6 | ❔ | menus differ a lot from the A6000 generation, so the settings are the least likely to sit in the same place |
| A68 | ILCA-68 | ❔ | not in the installer's device table, so even the install is untested |
| A77 II | ILCA-77M2 | ❔ |  |
| A99 II | ILCA-99M2 | ❔ |  |
| RX100 IV | DSC-RX100M4 | ❔ |  |
| RX10 II | DSC-RX10M2 | ❔ |  |
| RX10 III | DSC-RX10M3 | ❔ |  |
| HX400 / HX400V | DSC-HX400 | ❔ | compact; no control wheel of the kind the app is driven with |
| WX500 | DSC-WX500 | ❔ | compact; no control wheel of the kind the app is driven with |

Two more are unclear: the **RX100 II** and the original **RX10** had Sony's app store, but are missing from the
installer's device table, so even the install is untested.

App-capable but pointless: the QX lens cameras (ILCE-QX1, DSC-QX10/QX30/QX100), with no screen or wheel to drive the
app, and the Handycams and action cams, with no Creative Style to write.

### Cameras that cannot run camera apps

Sony's last app-capable bodies are the ones above, from late 2016 — the A6500 and the A99 II. Everything since has
signed firmware and no `MENU → Application`, so nothing can be installed on it: not this app, not Sony's own store,
which closed in 2021.

| | |
|---|---|
| **E-mount, APS-C** | A6100, A6400, A6600, A6700, ZV-E10, ZV-E10 II, FX30 |
| **E-mount, full frame** | A7 III, A7R III, A7R IV / IVA, A7R V, A7S III, A7C, A7C II, A7CR, A9, A9 II, A9 III, A1, A1 II, ZV-E1, FX3 |
| **Cyber-shot** | RX100 VA, RX100 VI, RX100 VII, RX10 IV, RX0, RX0 II, HX99, ZV-1, ZV-1F, ZV-1 II |

…and everything released since. The menu is the test: no `MENU → Application` on your camera, no app — so reports of
Recipe Lab running on an A6400 or similar are mistaken.

## Installing

Takes about ten minutes, once. You need the camera, its USB cable, a memory card and a computer (Windows, Mac or
Linux).

Rather watch it done? [u/wemax141](https://www.reddit.com/user/wemax141/) made a video walkthrough:

<p align="center">
  <a href="https://www.youtube.com/watch?v=-FpsmLYZcF8"><img src="https://img.youtube.com/vi/-FpsmLYZcF8/hqdefault.jpg"
    alt="How to Install Recipe Lab on a Sony A6000 (or Any Compatible Camera)"></a>
</p>

**1. Get the installer tool.** It is called *Sony-PMCA-RE*, made by ma1co. It puts apps on Sony cameras the same way
Sony's own app store did before it closed.

- *Windows:* download `pmca-gui.exe` from the
  [releases page](https://github.com/ma1co/Sony-PMCA-RE/releases). Nothing to install, just run it.
- *macOS:* the same page has a macOS build, less tested than the Windows one. Close anything that holds USB devices —
  Photos, Dropbox, Google Drive — or it takes the camera first.
- *Linux, or if the binary misbehaves:* Python 3 and libusb, then in a terminal:

  ```
  git clone https://github.com/ma1co/Sony-PMCA-RE.git
  cd Sony-PMCA-RE
  pip install -r requirements.txt
  ```

**2. Download the app:** [`RecipeLab.apk`](https://github.com/voxivoid/recipe-lab-sony-pmca/releases/latest/download/RecipeLab.apk)
— that link always serves the newest release, so it is the one to use. The
[releases page](https://github.com/voxivoid/recipe-lab-sony-pmca/releases) has older versions and the
version-stamped copies.

**3. Prepare the camera.** Battery charged, memory card inside. In the camera menu go to
`Setup (toolbox icon) → USB Connection` and choose **Mass Storage**. Turn the camera on and plug it into the computer.
The camera screen should say *USB Mode*.

**4. Install.**

- *GUI:* open `pmca-gui.exe` → **Install app from file** → choose `RecipeLab.apk` → wait.
- *Terminal,* from the Sony-PMCA-RE folder (Linux: put `sudo` in front):

  ```
  python pmca-console.py install -f RecipeLab.apk
  ```

The camera will flicker, go black and switch modes a couple of times on its own. That is normal — do not press
anything. After about a minute the computer prints `Task completed successfully`. **Go by the computer.** The camera
is usually left on its own `Application Download / Connecting via USB...` screen, which looks stuck and is not.

**5. Unplug, then turn the camera off and on.** The app now lives under
`MENU → Application → Application List → Recipe Lab`.

## Using it

Open **Recipe Lab** from the Application List. You get the live image with a panel at the bottom, then:

| key | what it does |
|---|---|
| **wheel** | scroll recipes, from anywhere — the live image changes at once, and that is what the camera will write |
| **left / right**, **top dial** | scroll recipes too, but only on the recipe line; on the chip row they walk the chips |
| **centre** | pick the recipe you are looking at — the camera keeps it. A message confirms it |
| **hold centre** | mark the recipe as a favourite, or unmark it. Works on the main screen and inside the brand list |
| **up / down** | move between the recipe line and the row of value chips |
| **TRASH** | hide the panel — once for a small label, twice for nothing, a third time to bring it back. The wheel still works |
| **hold TRASH** | reset to the factory look — the app asks first |
| **hold MENU** | open the menu: browse recipes, panel visibility, language, reset settings, about |
| **shutter** | take a picture of what you are previewing |
| **MENU** | leave the app |
| **Fn** *(if your camera has one)* | open the brand list — the same as **hold MENU → Browse recipes** |

Every function works on keys every camera has; **Fn** is a shortcut on the bodies that have it, and the legend at the
bottom of the screen only names it when the camera reports it. **AEL**, **C1** and **DISP** do nothing in the app.

**The menu.** Hold **MENU** for a moment and the menu fills the screen; a short press still leaves the app. Up / down
or the wheel move, centre picks, **MENU** goes back.

- **Browse recipes** opens the brand list: **Recent**, **Favourites**, then the brands, on the left; recipes on the right.
  Left / right switches column (the active one is amber), wheel or up / down scrolls, centre picks.
- **Panel visibility** — *Full*, *Label* or *Hidden* — changes with left / right, right there in the menu.
- **Language** — *Auto*, *English*, *简体中文* (Simplified Chinese) or *繁體中文* (Traditional Chinese) — changes the
  same way, and the whole app switches at once. *Auto* follows the camera's own language and falls back to English.
  Recipe names are translated too, with the original name kept in small type underneath; favourites and everything the
  app writes to the memory card stay the same in every language.
- **Reset settings** puts the camera back on its factory look, after asking. The factory look is not in the recipe
  list; this is the way to it.
- **About** shows the app version, the camera model, its platform version and where the source lives.
- **Developer** holds tools for testing and for compatibility reports.

Then **turn the camera off and on**. The look is now the camera's default in every mode — P, A, S, M, movie — with
the app closed, and the app reopens on that recipe.

**Favourites.** Hold the **centre button** on a recipe and it joins the **Favourites** group near the top of the brand
list, with a star next to its name; hold again to drop it. The group lists your picks in the order you marked them,
and the brand list opens straight on it whenever the recipe you are on is one of them. The wheel on the main screen
still walks all 76 — favourites shorten the list in the browser, not the scroll. The marks are kept by the app, not in
the camera's settings, so they survive a power cycle but go with the app if you remove it.

**Recent.** The browser also keeps up to 10 recipes you successfully picked, newest first. Previewing a look or a
failed write does not add it. Picking one again moves it to the top. The list survives a power cycle, but is kept in
the app's own storage and disappears if you uninstall the app.

**The chips.** In the chip row, **left / right** walks the chips, **centre** focuses one (it turns amber),
**up / down** changes its value, **centre** leaves it. The wheel keeps changing recipes throughout. A recipe only shows the chips it uses: **CS** recipes show style,
saturation, contrast and sharpness; **PE** recipes show the effect and its sub-setting. Quality, white
balance, EV and DRO are always there. The legend at the bottom of the screen follows whatever you are doing.

**The badge** next to the recipe name says where you stand:

| badge | meaning |
|---|---|
| **ACTIVE** | the camera already has these values |
| **PREVIEW** | you are only looking; press **centre** to pick it |

## What it changes

Only camera settings you could set by hand: Creative Style and its saturation, contrast and sharpness sliders, white
balance and its fine-tune, exposure compensation, DRO, Picture Effect. No firmware is touched, nothing is unlocked.

**Picture Effect recipes** (marked **PE**) behave like the menu item does: the camera ignores Creative Style while one
is on, and it only works with **Quality = JPEG** — set to RAW or RAW+JPEG, the camera drops the effect silently.

**Quality** therefore follows you rather than being dictated by a recipe. The Factory recipe starts as whatever the
camera is set to, and every Creative Style recipe uses that. Change it any time with the `QUALITY` chip. Only when a
recipe needs JPEG and you are on RAW does the app ask:

```
Quality: RAW+JPG → JPG Fine — JPEG is needed to apply this recipe
```

*Cancel* changes nothing.

## Uninstalling

**Is it permanent?** The look stays until you change it — on purpose, that is what makes it work in every mode
without the app. It is not permanent in the sense of damage. Undo it any time, three ways:

- In the app: **hold TRASH** (or **hold MENU → Reset settings**), confirm, then turn the camera off and on.
- In the menus: set Creative Style back to *Standard* 0 / 0 / 0 and White Balance to *Auto*.
- Or use the camera's own `Setup → Setting Reset → Camera Settings Reset`.

**Removing the app.** `MENU → Application → Application Management → Manage and Remove → Recipe Lab`. This does
**not** put the colour settings back, so undo the look first and remove the app after.

**Worth knowing:**

- The preview inside the app is temporary; closing the app removes it. Only what you *picked* stays.
- Built and tested on the A6000 with firmware 3.21. Several other bodies have been reported working — see
  [Compatibility](#compatibility) — but on anything still marked ❔ there, compare what the chips show with your menus
  before picking a recipe.

## Troubleshooting

The common questions — RAW files, damage, LUTs, newer bodies — are in the **[FAQ](docs/FAQ.md)**.

| what you see | what to do |
|---|---|
| `No devices found` | USB Connection must be *Mass Storage*; card inserted; camera on and showing *USB Mode*; try another cable or port |
| Stuck at `Waiting for camera to switch...` | Unplug, turn the camera off and on, reconnect, run again |
| `Not written — the camera holds these settings read-only` or `WRITE FAILED` | The camera refused the recipe. Install [OpenMemories-Tweak](https://github.com/ma1co/OpenMemories-Tweak), open **Protection**, tick *Unlock protected settings* until it reads *Protection disabled*, then pick the recipe again |
| Look not applied after picking a recipe | Turn the camera off and on |
| Look gone after a power cycle, or the recipe will not store, with the mode dial on **MR** / **1** / **2** | Memory Recall loads the settings registered to that position at power-on, over whatever the app stored. Turn the dial to P, A, S or M, store the recipe again, and register it to the MR slot from the camera's menu if you want it there ([#59](https://github.com/voxivoid/recipe-lab-sony-pmca/issues/59)) |
| Look right in the app, gone once you leave it | If your camera has a **Picture Profile** menu (the A6000 does not), set `MENU → Picture Profile` to *Off* and pick the recipe again. While a Picture Profile runs, the camera ignores Creative Style and its sliders. Older recipes that used the colour matrix could switch PP3 on themselves on these bodies ([#38](https://github.com/voxivoid/recipe-lab-sony-pmca/issues/38)); picking any recipe now switches it off |
| `no live preview: ...` in the panel | Something else is holding the camera; close and reopen the app |
| Text shows `Â·` | Old build; install the APK from the [latest release](https://github.com/voxivoid/recipe-lab-sony-pmca/releases/latest) |

## For developers

The reverse-engineering notes — source layout, the settings-store ID map, the exit rule, live-preview
parameters, key scan codes and how to build — live in **[docs/DEVELOPMENT.md](docs/DEVELOPMENT.md)**.

To contribute, read **[docs/CONTRIBUTING.md](docs/CONTRIBUTING.md)** first: branch naming, commit format and the
release flow are all enforced by CI.

## Credits

**Author:** [André Domingues (voxivoid)](https://github.com/voxivoid) — reverse engineering of the A6000 settings
store (backup IDs, PP flag behaviour, colour-matrix measurement), the app, the recipes, the icon.

**Huge thanks to [ma1co](https://github.com/ma1co).** None of this would exist without his years of work reverse
engineering Sony's PlayMemories camera platform:

- [Sony-PMCA-RE](https://github.com/ma1co/Sony-PMCA-RE) — the app-install channel, the updater shell used to dump
  this camera's firmware and settings, and `fwtool`.
- [OpenMemories-Platform](https://github.com/ma1co/OpenMemories-Platform) — the backup driver / OSAL bindings this
  app links against (vendored here as a git submodule).
- [OpenMemories-Tweak](https://github.com/ma1co/OpenMemories-Tweak) and
  [OpenMemories-Framework](https://github.com/ma1co/OpenMemories-Framework) — reference for `Backup_read/write`,
  the `ScalarInput` key codes and the `CameraEx` API.
- The earlier nex-hack community research he built on and kept documented.

He figured out how these cameras work, documented it openly and licensed it permissively — this project just stands
on that.

**Thanks also to [Veres Deni Alex](https://www.veresdenialex.com/).** His Sony film-simulation recipes and the side-by-side
reference frames on his site were the inspiration and the benchmark for many of the looks here (Kodak, Fuji, Cinestill,
Ilford, Cinema…). The values in this app are re-derived for what the A6000 can store and are not his recipes.

License: MIT (this repository). OpenMemories-Platform: MIT, © 2017 ma1co.
