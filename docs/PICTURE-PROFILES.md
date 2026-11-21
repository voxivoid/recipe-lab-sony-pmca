# Picture profiles: keeping them across a power cycle

How Recipe Lab keeps a picture profile (a gamma curve plus a colour matrix in the image processor) after a power cycle,
without the user opening the app. It took many camera tests to find this. Most of the obvious approaches **do not
work** on the A6000, and this page records why so nobody has to try them again.

Status (2026-11-20, A6000 FW 3.21, app 1.13.0-dev.11): **works**. The profile comes back at power-on without opening
the app. Opening the app is fast (the recipe shows in about 1–2 s), and switching between gamma-on and gamma-off
recipes is fast.

Contents: [Hardware](#hardware-a6000-fw-321) · [How it works now](#how-it-works-now) ·
[Rules that must not break](#rules-that-must-not-break) · [What was tried and failed](#what-was-tried-and-failed) ·
[Open issues](#open-issues) · [Debugging](#debugging-from-the-card)

Credit: writing the ISP's gamma table and colour matrix through `/dev/mem` follows
[ivica00/bionz-x-research](https://github.com/ivica00/bionz-x-research) (`mods/arri_isp`; MIT, © 2026 ivica00).
The A6000 addresses and everything below were found on an A6000.

---

## Hardware (A6000 FW 3.21)

| What | Physical address | Format |
|------|------------------|--------|
| Gamma table | `0xF3215300` | 96 × u32, 10-bit output per knot |
| Colour matrix | `0xF321550C` | 9 × u32, s16 Q10 in the low half, row-major |
| Gamma setter (av-cam, DDR) | `0x83b67414` | first 4 bytes `00 f5 a8 30` |
| Matrix setter (av-cam, DDR) | `0x83b675e0` | first 4 bytes `70 b5 0c 46` |

- Both tables are ISP SRAM. They are **RAM only** and stock again after every power cycle.
- `rlisp` ([jni/rlisp/rlisp.c](../jni/rlisp/rlisp.c)) patches each setter to `bx lr` (`70 47`), then writes the
  tables through `/dev/mem`. Patching first means the camera's own writer can't race us. The setters' addresses and
  their 32 bytes come from that body's row of [jni/rlisp/bodies.h](../jni/rlisp/bodies.h); any other bytes give
  `RESULT REFUSE sig` and nothing is touched. Other bodies: [DEVELOPMENT.md, Bodies](DEVELOPMENT.md#bodies).
- **ISP init** (confirmed): about 30–60 s after boot, the firmware writes **gamma** straight into SRAM. That path does
  **not** go through the patched setter, so one apply at boot is lost. The **matrix is not rewritten** by ISP init:
  after it, `reapply` finds gamma changed but the matrix still equal to what we wrote.
- **Warm vs cold boot:** a quick power cycle right after using the app can keep the DDR patches and skip the full ISP
  init, so the profile "just sticks". A cold boot does the full init. This is why early tests worked once and then
  failed. Always test with real power-offs, and wait.

### The root shell (OSAL `shell_exec_async`)

- Reached through `NativeShell.exec` (JNI). It runs as root **outside** the Android chroot, so every path needs the
  `/android` prefix (`IspProfiles.ROOT_PREFIX`).
- Commands are **255 chars at most** (`IspProfiles.MAX_COMMAND`).
- **One command at a time.** While a command runs (including any `sleep` inside it), every other `exec` is
  **silently dropped**: there's no queue and no error, and the helper never runs, so it writes no log file.
- A foreground command keeps the CPU awake for as long as it runs. Backgrounded children (`&`, double fork) do not
  survive; see below.

### The boot event

- Android's `android.intent.action.BOOT_COMPLETED` is **never delivered** to a PMCA app. `MEDIA_MOUNTED` only fires
  on a USB card remount, not at power-on.
- ScalarA sends its own event, **`com.android.server.DAConnectionManagerService.BootCompleted`**. Sony's
  `AvindexProvider` uses it. `DAConnectionManagerService.registerPackageChangeAndBootCompletedBroadcastReceiver`
  scans packages for receivers of it. It needs `RECEIVE_BOOT_COMPLETED` in the manifest.
- It fires at boot, about 20–25 s after power-on. The card is already mounted by then.
- It **also fires every time the app opens**, and on app open it arrives **after `onResume`** (in LOG.TXT the `OPEN`
  line comes before the `BOOT` line, at the same second). This ordering is what the duplicate guard relies on.
- The receiver's process can be killed between boot and app open, so a static flag set only in the receiver doesn't
  survive.

---

## How it works now

```
SD card  RECIPLAB/ISP/   (persistent; the boot path and the loop use it)
  look.txt     stored profile as text (Params.lookText); deleted when the stored recipe has no profile
  profile.bin  what rlisp reads (IspProfiles.encode)
  body         model and firmware (IspBodies.bodyFile): which row of bodies.h rlisp uses
  base         av-cam's load address, when rlisp had to search for it
  PROBE/       the bug report's read-only probe (body, log)
  state.bin    stock matrix + the matrix last written (rlisp's state)
  log          last rlisp run from the loop
  boot.log     receiver lines (also copied to RECIPLAB/LOGS/LOG.TXT as BOOT)
  app          marker: the app is open, so the loop must let go of the shell
  stop         marker: profile turned Off, so the loop must end

Ramdisk  getFilesDir()/isp/   (the app's own runs; lost at power-off)
  profile.bin  state.bin  log
```

### At power-on (app closed)

1. `BootReceiver.onReceive` reads `look.txt`. If it's missing, nothing happens (the stored recipe has no profile).
   Nor does anything on a body or firmware `IspBodies` does not support (`BOOT not on this body: …`).
2. `BootReceiver.startLoop` writes `profile.bin`, deletes `stop`/`app`/`log`, and sends the **keep-alive loop**
   (`IspProfiles.bootCommand`):

```sh
h=<helper>;d=<card dir>;$h apply $d;
for a in 1 2 3 4 5 6;do for b in 1 2 3 4 5 6 7 8 9 0;do
  sleep 2;[ -f $d/stop -o -f $d/app ]&&exit;$h reapply $d;
done;done
```

- One `apply`, then 60 × (2 s sleep + `reapply`), which is about 2 minutes. That covers ISP init.
- `reapply` compares the 96 gamma words. If they match, it writes `RESULT MATCH` and touches nothing. If not, it runs
  a full `apply` (this is how the ISP-init rewrite gets caught).
- About 227 chars with the real paths. The nested `for` fits 60 iterations into 255 chars; a flat list of 60 numbers
  would not.

### Opening the app

1. `onResume` → `BootReceiver.appOpened()`: sets the static `appSeen` flag and creates `app` on the card.
   - The `BootCompleted` that follows sees `appSeen`, logs `app open, skipping`, and starts **no** second loop.
   - A running loop sees `app` within 2 s and exits, which frees the shell.
2. `pullState()` copies the card's `state.bin` over the ramdisk one (see rule 4 below).
3. `applyPreview()` → `ispSync(false)` → after `ISP_SETTLE_MS` (1 s) → `ispApply` → `rlisp apply <ramdisk>`.
4. `ispCheck` polls the log every 250 ms. **No log file after 4 polls (1 s)** means the shell dropped the command
   (the loop was still ending), so it **sends the command again**, up to 12 times. Once the log has `RESULT OK`, it
   runs `syncState()`, which copies the ramdisk `state.bin` to the card.
5. Every apply from the app also writes `profile.bin` to the card. Off writes `stop` and deletes the card's
   `state.bin`.

### Leaving the app (`onPause`)

- If there's a stored profile, `BootReceiver.startLoop(ispStored)` starts the same 2-minute loop with the **stored**
  look, not the one being previewed. It replaces the single apply of older builds. If the app was opened soon after
  power-on, ISP init can still come after the app closes, and this loop catches it.
- With no stored profile, `ispSync(true)` runs `rlisp off`.

### Stock matrix (`rlisp cmd_apply`)

The profile's matrix is `profile.t` (a Q14 transform) × the **stock** matrix of the creative style, plus a
white-stays-white correction. The stock matrix comes from `state.bin`:

| Condition | Result |
|-----------|--------|
| `state.bin` exists and both setters are original (cold boot) | `STOCK kept`: trust the stock synced from the app |
| live matrix ≠ `state.last` (not ours) | `STOCK captured` / `STOCK recaptured` from the live register |
| live matrix = `state.last` (ours) | `STOCK kept` |

---

## Rules that must not break

1. **Never start a second loop while the app is open.** It holds the shell for 2 minutes, every app apply is
   dropped, and scrolling feels "clunky". This is the reason for `appOpened()` in `onResume` (before the duplicate
   `BootCompleted`) and for `appSeen`.
2. **Never delete `app` after `onResume` created it.** Only `startLoop` deletes it, and that only runs at boot or in
   `onPause`.
3. **Any `exec` can be dropped.** Code that needs an answer must resend when no log appears (`ispCheck`). Don't
   treat a missing log as "the helper failed".
4. **There are two `state.bin` files. The newest wins.** The loop writes the card copy, the app writes the ramdisk
   copy. If the app keeps a stale `last`, rlisp sees our own matrix as "not ours" and recaptures it **as stock**, so
   the colours come out wrong. Hence `pullState()` on resume and `syncState()` after every OK.
5. **Off deletes the card `state.bin`**, so the next capture reads the real stock and not an old creative style's.
6. **The loop must run in the foreground.** See "Backgrounded loop" below.
7. Keep `bootCommand` within 255 chars (`IspProfiles.MAX_COMMAND`). It returns null when too long, and nothing runs.
8. **Never refuse to write because of what the live tables hold.** The A6000 shows all of these in normal use, and a
   check that rejects any one of them breaks the profile (the power-cycle one silently: the boot loop just refuses
   60 times):

   | When | Gamma (first words, max) | Matrix |
   |------|--------------------------|--------|
   | Right after power-on, before ISP init | `5 11 17 23 29 35`, max **1732** (over 10 bits) | identity `1024 0 0 …` |
   | Shortly after *Off*, Standard, no effect | `127 159 175 255 212 255`, not monotone | `1837 -17 -8 / -35 1341 -6 / -4 -2 1950` |
   | After *Off* | `127 159 191 207 244 239`, not monotone | `-66 -1 -81 / -3 1852 -265 / -19 -65 -292` |

   Only the setters' 32-byte signatures decide whether the helper acts. The live matrix decides only **which stock**
   to build on (`stock_ok`, Standard as the fallback), never whether to write.
9. **Every change to `rlisp`, `BootReceiver` or the boot command is tested with a real power-off**, not only in the
   app: switch off with a log profile on, switch on, wait a minute, and check live view. Opening the app re-applies
   the profile and hides a broken boot path.

---

## What was tried and failed

| Attempt | Result | Why |
|---------|--------|-----|
| `android.intent.action.BOOT_COMPLETED` receiver | never fires | ScalarA doesn't send it to PMCA apps |
| `MEDIA_MOUNTED` receiver | fires only on a USB card remount | not a power-on signal (uptimes of ~22 min proved it) |
| One `rlisp apply` at boot | profile dark again seconds later | ISP init rewrites gamma directly, bypassing the setter patch |
| Apply at +5 s, then t = 0/5/10/15 s | sometimes | ISP init lands at 30–60 s; early tests passed only on warm boots |
| Backgrounded loop `(…)&`, double-fork daemon in rlisp | stops after the first run ("stopped at 1") | OSAL doesn't keep detached children alive (or the CPU sleeps) |
| `AlarmManager` retries from the receiver | unreliable | the CPU sleeps between alarms, so retries don't fire |
| Foreground 15 × 5 s loop | boot OK, but "no answer from system shell" in the app | holds the shell for 75 s |
| `stop` file on app open | profile lost later | the loop died and the app applied once, before ISP init |
| Loop keeps running while the app is open | "profile switches back to the old one" | the loop reapplies the stale card `profile.bin` over the app's preview |
| `app` file + a `bootSent` flag only in the receiver | clunky, 6+ `FAIL isp: no log` | the duplicate `BootCompleted` on open deleted `app` and started a 2nd loop; the flag was lost with the process |
| `ISP_SETTLE_MS` = 2000 | UI too slow | a fixed wait isn't the fix (see open issues) |
| Rejected without trying | | writing `/setting/env.txt` (LD_PRELOAD), init scripts, firmware patches: flash writes, brick risk, breaks the "RAM only" promise |

Partition facts found on the way: `/usr` and `/system` are read-only. `/setting` (vfat) is writable but init only runs
`putenv` from `env.txt`. `/data/data` and `/data/system` are ramdisks restored from a backup at boot. `/data/app` is
persistent.

---

## Open issues

- **Matrix not settled after a big creative-style change** (for example Kodak Gold SAT=2 → a log profile, which resets the
  style). Captures seconds apart differ a lot: `801 239 -16`, `488 352 184`, `441 439 144`, `375 525 124` (first row).
  700 ms isn't always enough, so a wrong stock gives slightly wrong colours until the next clean capture. A possible
  fix: read the matrix twice about 200 ms apart and capture only when the two match. Not done yet.
  Those captures all sum to 1024 per row. A worse kind, `1837 -17 -8 / -35 1341 -6 / -4 -2 1950` (rows 1812/1300/1944,
  seen on Standard with no effect, gamma `127 159 175 255 212 255` at the same moment), made a lower saturation swap
  orange and blue once built on. `rlisp` now takes a stock only when every row sums to 1024 ± 16, and falls
  back to the Standard matrix when none is clean (right after *Off*, the transient can last a minute or more).
- **Gamma rewrites after 2 minutes:** none seen so far. If one turns up, lengthen the loop (both the outer and inner
  `for` count toward the 255 chars).
- Proven only on **A6000 FW 3.21**. Other CXD90014 bodies have rows from their updaters, switched on for owners to try
  (their reports, with the probe, confirm each one); the rest are told the profile does not work there yet.

---

## Debugging from the card

The card mounts in Windows (it was `E:` here). From WSL, read it with PowerShell when `/mnt/e` isn't mounted:

```bash
powershell.exe -NoProfile -Command "Get-Content E:\RECIPLAB\ISP\boot.log -Tail 25; Get-Content E:\RECIPLAB\ISP\log; Get-Content E:\RECIPLAB\LOGS\LOG.TXT -Tail 50"
```

| Line | Meaning |
|------|---------|
| `BOOT sent: s-log3/…` | boot loop started |
| `BOOT retired profile, removed` | `look.txt` named LogC3 (test builds only): deleted, the camera stays stock |
| `BOOT app open, skipping` | duplicate event on open, correctly ignored |
| `BOOT no look.txt` | the stored recipe has no profile, so nothing to do (correct) |
| `BOOT not on this body: …` | the body or firmware has no enabled row, so no loop (correct) |
| `INFO isp …: … RESULT OK` | the app's apply worked |
| `FAIL isp …: … RESULT REFUSE …` | `fw <x>` other firmware, `untested` row not on yet, `sig` setter bytes differ, `isp` tables not ready |
| `FAIL isp …: no log` | the shell dropped it 12 times in a row, so something holds the shell (rule 1) |
| ISP `log`: `RESULT MATCH` | loop check, gamma still ours |
| ISP `log`: `SETTER … patched` twice, then `GAMMA …` | `reapply` caught a rewrite and applied again |

S-Log2 gamma starts around `106`. Use this to tell which profile a log is
about.

### Building and installing

```bash
export JAVA_HOME=$HOME/toolchains/jdk17 ANDROID_SDK=$HOME/Android/Sdk ANDROID_NDK=$HOME/Android/Sdk/ndk/16.1.4479499
ANDROID_KEYSTORE_B64=$(base64 -w0 ~/.keys/recipelab-release.keystore) \
ANDROID_KEYSTORE_PASSWORD=$(cat ~/.keys/recipelab-release.password) \
ANDROID_KEY_PASSWORD=$(cat ~/.keys/recipelab-release.password) ANDROID_KEY_ALIAS=probe ./build.sh
~/code/pmca-scripts/install-to-camera.sh ~/code/recipe-lab-sony-pmca/RecipeLab.apk
```

The build script takes the keystore **base64-encoded** (`ANDROID_KEYSTORE_B64`), not as a path. Without it the APK
is signed with a throwaway key, and the camera refuses it as an update.

### Testing

CI can't check any of this. On a real A6000:

1. Pick S-Log3, close the app, **power off**, power on, don't open the app, and wait about 60 s. The live view should
   be flat.
2. Power on and open the app at once. Scrolling should be snappy, and the profile should show in about 1–2 s.
3. Switch between gamma-on and gamma-off recipes. Each switch should take about 1 s.
4. Kodak Gold → S-Log3 → close → power-cycle. Check the colours.
