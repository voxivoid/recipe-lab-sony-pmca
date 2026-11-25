New
- Picture profiles, for the A6000 on firmware 3.21: a new Pic Profiles group with S-Log2, S-Log3 (S-Gamut3.Cine or S-Gamut3), ITU709 and HLG. They are recipes: the GAMMA chip adds a profile to any recipe, and black level, black gamma, knee, colour saturation, colour phase and an S-curve shape it. Save your own like any custom recipe. A profile covers live view, photos and video, and comes back by itself after the camera is switched off and on, without opening the app (live view can show the normal look for up to a minute after switching on). On other cameras the profile recipes are still listed, and say why they cannot be used there yet.
- The GAMMA chip also has Sony's own Movie, Still, Cine1, Cine2, Cine3, Cine4 and ITU709(800%) curves, taken from the A6500's firmware, and the Pic Profiles group has a ready-made recipe for each. Each puts middle grey where the other curves do, so exposure does not change when you switch between them.
- Recipes with a picture profile can be shared: Share and LINKS.TXT now give them a recipelab.io link too, and recipe files on the card (RECIPES/*.YML) can carry a profile. Links to recipes without one are the same as before.
- The recipe card (Share) is the same picture Recipe Lab Studio makes, word for word: the settings in groups (Look, White balance, Exposure, Picture Profile), only the ones the recipe uses, and the picture profile's settings when it has one.
- A QR code or recipe file made by a newer Recipe Lab now says to update the app, instead of calling it unreadable.

Changed
- The chips are grouped, with a line between the groups. A new LOOK chip switches between a Creative Style and a Picture Effect; the effect chip no longer has an "off" of its own.

Fixed
- A chip now grows to fit its value; a longer value was cut short.

Please try
- Sony's Movie, Still, Cine and ITU709(800%) curves have not been tried on a camera yet. On an A6000, pick each one on the GAMMA chip, check the live view and a photo look right, then switch the camera off and on and check again; report on GitHub what you see.
- Picture profiles on more cameras: they are now switched on for the A5000, A5100, A6300, A6500, A7, A7R, A7S, A7 II, A7R II, A7S II, RX100 III, RX100 IV, RX100 V, HX60 and HX60V on Sony's latest firmware, but nobody has tried them there yet. If you own one: update it to the latest firmware, pick S-Log3 from Pic Profiles, check that the live view goes flat, then switch the camera off and on, wait a minute and check again. Whether it works or not, hold MENU → Save a bug report and attach RECIPLAB/REPORT.TXT to a compatibility report. On older firmware the app tells you which version to install.
