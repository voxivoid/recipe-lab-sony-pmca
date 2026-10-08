# Monochrome comparison trials

These four custom recipes preserve the settings proposed in PR #67 for on-camera comparison. They are experiments,
not verified Fujifilm simulations or additions to the built-in list. Existing Acros recipes and their identifiers
remain unchanged.

Fujifilm describes ACROS as having greater tonal variety and enhanced sharpness than MONOCHROME
([Fujifilm explanation](https://www.fujifilm-x.com/en-gb/learning-centre/make-your-images-mono/)). Its yellow, red and
green options change how subject colours become gray
([filter explanation](https://www.fujifilm-x.com/en-gb/learning-centre/using-filters-in-black-white/)). That describes
Fujifilm's processing; lower Sony sharpness and changes to global contrast do not establish equivalent results.
The white-balance settings below are hypotheses carried over from the existing Acros variants, not calibrated
colour filters. No paired photos or persistence checks have been collected for these trials.

| File | Compare with | Trial contrast / sharpness | Existing contrast / sharpness | White balance | Pipeline |
|---|---|---|---|---|---|
| `MONO.YML` | Acros | 0 / 0 | +1 / +1 | Auto for both | B&W Creative Style for both |
| `MONOYE.YML` | Acros +Ye | +1 / 0 | +1 / +1 | 4000K for both | B&W Creative Style for both |
| `MONOR.YML` | Acros +R | +2 / 0 | 0 / 0 | 2500K for both | Trial: B&W Creative Style; existing: High Contrast Mono effect |
| `MONOG.YML` | Acros +G | -1 / 0 | +1 / +1 | 5600K, G+4 for both | B&W Creative Style for both |

The first, second and fourth pairs mainly change contrast or sharpness. The red trial also removes the Picture
Effect, so the app does not require JPEG-only quality for it. This is the strongest functional reason to investigate
it; it does not prove that its JPEG matches Acros +R. RAW records sensor data rather than baking in the B&W look.

## Load and compare

1. Copy the four `.YML` files into `RECIPES` at the root of a memory card. Their filenames fit the camera's eight-character limit.
2. Open Recipe Lab and select them from Custom. Use fixed exposure, framing, focus and lighting for each paired comparison.
3. Photograph a neutral scale, coloured targets, daylight sky/foliage and skin tones. Compare the original Acros recipe
   and the corresponding trial on each scene; keep original JPEGs and record the body and firmware.
4. For the red trial, select RAW+JPEG and verify that both files are actually saved. Compare its JPEG with the existing
   JPEG-only Acros +R, recording the quality difference.
5. Apply each trial, close the app, power-cycle the camera and confirm that the stored settings and resulting look persist.

If the paired results are effectively the same, discuss updating an existing recipe rather than adding a second
family. A change to Acros +R also needs to justify replacing its existing Picture Effect rendering with the
RAW-compatible Creative Style rendering. If the results differ usefully, provide the paired images before proposing
new built-in entries. Unit tests only check parsing and the settings sent; they cannot settle the visual comparison.
