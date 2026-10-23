# FAQ

The questions people actually ask. For the step-by-step install, the recipe list and the undo instructions, see the
[README](../README.md).

---

## Will this touch my RAW files?

No. Recipe Lab sets the same camera settings you could set by hand in the menus, and the camera treats them the way
it always has: the look is baked into the **JPEG**, the RAW stays a RAW. Shoot RAW+JPEG and you get a processed JPEG
next to an untouched raw file, exactly as before.

The one catch is the recipes marked **JPEG only**, which are built on a Picture Effect. The A6000 refuses to apply a Picture
Effect unless Quality is a JPEG setting — with RAW or RAW+JPEG selected it silently drops the effect. So when storing
one of those would change your Quality, the app asks first:

```
Quality: RAW+JPG → JPG Fine — JPEG is needed to apply this recipe
```

*Cancel* stores nothing and leaves your Quality alone. Pick a Creative Style recipe instead and you keep shooting RAW.

**RAW alone is not offered.** The camera applies a look only to a JPEG, so with RAW alone there is nothing to see, and
the in-app shutter restarts the A6000 on it (#100). Use **RAW+JPG** for the raw file plus the processed JPEG. A camera
left on RAW alone is moved to RAW+JPG when you store a recipe, after the app asks. To go back to RAW alone, use the
camera's own menu.

## Can this damage or brick my camera?

Installing an app is the mechanism Sony itself shipped: the camera has an app menu, and `Sony-PMCA-RE` talks to it the
same way Sony's app store did before it closed. No firmware is replaced, nothing is unlocked, no "jailbreak".

Recipe Lab then writes only values the menus can already write — Creative Style and its sliders, white balance,
exposure bias, Picture Effect, DRO, Quality. Any of it can be undone from the camera's own
[`Setup → Setting Reset → Camera Settings Reset`](../README.md#uninstalling).

The honest caveat is **other bodies**. The setting IDs were reverse engineered on an A6000, and the same value can
live in a different slot on a different model — so on an untested body a recipe could land somewhere unintended.
That is why the [compatibility table](../README.md#compatibility) only marks a camera ✅ once someone has actually
run it. If your body is a ❔, expect to check the app's chips against your menus before storing anything.

## Do I need Wi-Fi, a Sony account, or the app store?

No. Sony's PlayMemories app store closed in 2021 and none of this goes near it. The app arrives over the USB cable
from your computer.

## Does the look stay with the app closed?

Yes — that is the whole point. Storing a recipe writes it into the camera's settings, so it applies in every mode,
photo and video, with Recipe Lab closed and after a power cycle. What the app shows *before* you store is only a live
preview, and that disappears when you leave the app.

The exception is the mode dial's **MR** / **1** / **2** (Memory Recall) positions: they load the settings registered
there at power-on, over the stored recipe. Shoot from P, A, S or M, or register the recipe to the slot from the
camera's menu.

## Will it work on my camera?

If your camera can run PlayMemories camera apps, it should install. Whether the stored values land correctly is the
open question, body by body — see [Compatibility](../README.md#compatibility), which lists every app-capable model,
which ones people have confirmed, and which Sony bodies cannot run apps at all.

If you try one, please file a
[compatibility report](https://github.com/voxivoid/recipe-lab-sony-pmca/issues/new?template=compatibility_report.yml).

## My camera is newer than that — is there a version for it?

There cannot be. Sony's last app-capable bodies are from late 2016, the A6500 and the A99 II. Everything since —
A6100, A6400, A6600, A6700, A7 III onwards, A9, A1, the ZV and FX cameras, RX100 VA and later — has signed firmware
and no `MENU → Application`, so nothing can be installed on it by anyone.

Those cameras do have Picture Profiles and Creative Looks in the menus, which is the feature Recipe Lab exists to make
up for on the A6000.

## Can I make my own recipes and save them?

Yes. Change a recipe's chips and the app offers to save the result as a **custom recipe**, under a name you type on
an on-screen keyboard; **hold MENU → New recipe** keeps whatever the camera has stored right now. They
are YAML files in a `RECIPES` folder on the memory card, so you can back them up, edit them on a computer and share
them by copying the files. See [Custom recipes](../README.md#custom-recipes).

They live on the card, not in the app: removing the app keeps them, **formatting the card deletes them**, and a
different card has its own.

## Can I shoot inside the app?

You can, but it is not what the app is for. The shutter works inside Recipe Lab only so you can try a recipe on a
real frame while you scroll through them. Recipe Lab does not replace the camera's own shooting: it writes the recipe
into the camera's settings, then you leave the app (**MENU**) and shoot with the camera's normal modes — P, A, S, M,
video, silent shutter, drive modes, whatever your body offers. The look stays with you.

So the in-app view is a preview, and it can differ from the camera's own (some bodies show Movie mode differently, for
example). Bugs that appear only while shooting inside the app matter less than anything that goes wrong in the stored
settings, and features such as other shutter modes belong to the camera, not to this app.

## Why do some recipes have identical settings?

Because the camera has only a handful of knobs to turn: a Creative Style, contrast, saturation, sharpness, white
balance and a few more. Two films that differ in grain, tone curve or halation can land on the same values once
squeezed into those, and then they render the same JPEG. Examples are Acros, Ilford FP4 and Ilford Delta 100, or Sony
NT and Canon Faithful. They are deliberate aliases: the name is there so you can find the look you know, not because
the app does anything extra behind it. Nothing else differentiates them — Recipe Lab never processes an image.

## Can it use LUTs, like JPEG.CAM does?

No, and not by accident. Recipe Lab never processes an image — it picks camera settings and gets out of the way,
which is why the look survives with the app closed and why it applies to video too. The A6000's settings store has no
LUT, no tone curve and no gamma to write, so there is nothing to point a LUT at.

Applying a LUT would mean the app taking the picture itself and re-processing the file, which is a different kind of
tool — [JPEG.CAM](https://www.jpeg.cam) does exactly that, and is worth a look if that is what you want. It cannot be
real time, and it cannot touch video.

## What about grain, halation, bloom, or light leaks?

Same wall. Those are image processing, and this app does no image processing — only what the camera's own hardware
does while it writes the JPEG. Film grain in particular is not a setting the A6000 has.

## Can I map a custom button to launch the app?

No. The camera's custom-key list is fixed in firmware and apps cannot add themselves to it, so the app opens from
`MENU → Application → Application List`. Changing that would mean custom firmware, which this project does not do.

## Can I get S-Log, V-Log or a flat profile?

Not on this body. The A6000 has no Picture Profile menu and cannot store a gamma curve — the log profiles simply are
not there to switch on. Sony's camcorder *Cinematone* gamma does exist inside the firmware, but the A6000's camera
layer neither lists nor accepts it. The flattest thing available is a low-contrast Creative Style recipe, which is
not the same thing.

## Are there sample photos of each recipe?

Yes — **[on the website](https://recipelab.io/recipes)**, each next to Standard; how they were shot is in
[SAMPLES.md](SAMPLES.md). All 77 recipes on one subject, in one light, at one fixed exposure (1/30, f/4.5, ISO 250),
straight out of the camera and not edited, downscaled to 900 px so the page stays light. Nothing that touches colour
was done to them.

The scene has no skin tone, foliage or sky, so it judges the portrait-leaning recipes worst; for those the app's own
live preview is still the better sample — turn the wheel and the image on the screen is what the camera will write.

## Something went wrong

**To report a bug, save a report file first.** It holds everything needed to find the problem, so you attach one
file instead of answering a long list of questions:

1. Right after the problem happens, open Recipe Lab.
2. Hold **MENU** until the app menu opens, choose **Save a bug report** and press the centre button.
3. Connect the camera to your computer with its USB cable, or put the memory card in a card reader. If the card does
   not show up, set `MENU → Setup → USB Connection` to *Mass Storage* on the camera and plug it in again.
4. Open the **RECIPLAB** folder on the card and attach **REPORT.TXT** to a
   [bug report](https://github.com/voxivoid/recipe-lab-sony-pmca/issues/new?template=bug_report.yml).


The [Troubleshooting table](../README.md#troubleshooting) covers the common ones. Install and USB problems belong to
[Sony-PMCA-RE](https://github.com/ma1co/Sony-PMCA-RE/issues), which is what puts apps on the camera; anything about
the recipes or the app itself belongs [here](https://github.com/voxivoid/recipe-lab-sony-pmca/issues).
