# Evidence from the published sample scene

These observations use the existing images in `docs/samples`, not newly captured trial results.
See [the gallery's capture notes](../../SAMPLES.md) for the recorded body, exposure, lighting and processing.
The gallery records fixed manual exposure, indoor evening lighting and downscaled JPEGs.

The [Factory Standard image](../../samples/01-factory-st.jpg) identifies the subject colours: an orange jacket,
green ear flaps, a teal hem, pale face fabric, a wooden floor and a pale wall. The face is a toy, not real skin.

| Sample | Orange jacket | Green ear | Teal hem | Face fabric | Floor | Wall |
|---|---:|---:|---:|---:|---:|---:|
| [Acros](../../samples/20-acros.jpg) | 90.4 | 35.4 | 37.1 | 157.5 | 138.9 | 168.4 |
| [Acros +Ye](../../samples/21-acros-plus-ye-yellow-filter.jpg) | 93.9 | 37.3 | 39.7 | 161.4 | 142.4 | 171.8 |
| [Acros +R](../../samples/22-acros-plus-r-red-filter.jpg) | 77.8 | 15.9 | 17.6 | 153.6 | 126.1 | 152.6 |
| [Acros +G](../../samples/23-acros-plus-g-green-filter.jpg) | 92.5 | 35.7 | 36.9 | 157.3 | 138.3 | 167.1 |

Values are mean encoded JPEG brightness on a 0–255 scale, not linear light, exposure stops or a colour-calibrated
measurement. For these B&W patches R, G and B have identical means. The exact rectangles and RGB means are in
[sample-measurements.json](sample-measurements.json). Coordinates use the published 900 × 600 images, origin at the
upper left, bounds `[left, top, right, bottom)`:

| Region | Rectangle |
|---|---|
| Orange jacket | 465, 345, 490, 385 |
| Green ear | 337, 215, 349, 250 |
| Teal hem | 375, 407, 415, 416 |
| Face fabric | 475, 250, 505, 285 |
| Floor | 650, 465, 750, 505 |
| Wall | 650, 65, 750, 105 |

## What the images support

- **Acros +Ye is close to Acros here.** All six patches are slightly brighter, by about 2–4 levels. The measurements
  do not isolate a distinctive colour-filter response; a broad brightness shift is also consistent with this result.
- **Acros +G is also close to Acros.** Green ear brightness is 35.7 versus 35.4, and teal is 36.9 versus 37.1.
  This scene does not demonstrate a useful green-filter separation. The face fabric changes by only -0.2.
- **Acros +R has the strongest shadow change.** Green drops by 19.5 levels, teal by 19.5, orange by 12.6 and face
  fabric by only 3.9. The green/face brightness ratio changes from about 0.225 to 0.104, showing greater tonal
  separation even relative to the face. Visually, the ear, hat and lower clothing have deeper blacks and less visible
  shadow detail. This is consistent with its High Contrast Mono pipeline, but these images cannot separate that
  effect from the changed white balance or establish a calibrated red-filter response.

## Consequence for PR #67

The samples support the maintainer's concern about overlap: the existing plain, yellow and green variants already
look close in this scene. The red variant has a distinct rendering that should be preserved until a paired test
shows whether the RAW-compatible Creative Style trial is a suitable replacement. Removing the Picture Effect is
not automatically an equivalent look.

There are no images of `MONO.YML`, `MONOYE.YML`, `MONOR.YML` or `MONOG.YML` in this gallery. These measurements cannot
prove that the trials match the existing recipes, or that four new built-ins are justified. The scene has no sky,
foliage or real skin; daylight and portrait filter claims remain untested. Single captures, white-balance differences,
JPEG processing and small registration changes also limit interpretation of differences of only a few levels.
