---
name: test-feature
description: Validates a work branch — audits behavioral changes for intentionality, verifies new behavior is covered by host-side JUnit tests, audits Android coupling and builds the on-camera verification matrix, then runs pitest (Java) and hand-rolled mutants (shell gates) to prove the tests catch regressions.
argument-hint: "[path or scope hint, e.g. src/com/voxivoid/recipelab/Recipes.java]"
---

# Test Feature — Behavioral Integrity, Camera Matrix & Mutation Testing

Validate that a work branch is actually verified: every change to old behavior is intentional, every
new behavior that *can* be tested off-camera has a test, everything that *cannot* is written down as a
camera step, and mutation testing proves the tests would catch a regression.

The optional argument narrows scope: $ARGUMENTS

## What this repo can and cannot test

There is no test framework in the tree, and none ships in the APK. `build.sh` compiles
`src/com/voxivoid/recipelab/*.java` and nothing else, so tests live **outside** `src/`.

| Layer | How it is verified |
|---|---|
| Framework-free Java (`Recipes.java`, pure statics elsewhere) | Host JVM + JUnit 4 harness in the scratchpad, mutation-tested with pitest |
| Android-coupled Java (`MainActivity`, `PickerView`, `Legend`, `PromptView`, `HintBar`) | Compiles only. `android.jar` methods throw `RuntimeException("Stub!")` at runtime, so behavior must be **extracted into pure statics** to be testable |
| JNI (`jni/jni.cpp`) and `NativeBackup` | Not testable off-camera at all — needs the backup driver |
| `tools/*.sh` gates | Bash fixtures in the scratchpad + hand-rolled mutants |
| `build.sh` / `build.cmd` | Parity review only; `build.cmd` cannot run here |
| Recipes, settings-store IDs, live preview, key handling | **Camera only**, and only after a power cycle |

**Nothing in this skill makes a green build evidence that a recipe or a settings-store write works.**
Say so plainly in every report.

Everything this skill creates lives in the scratchpad. Never write a test into `src/`, and leave
`git status` clean — CI fails the build on a dirty tree.

## Phase 0: Establish Base Branch & Scope

1. Find the branch and its open PR base (this repo is GitHub, and the base is `development`, never `main`):

   ```bash
   BRANCH=$(git branch --show-current)
   TARGET=$(gh pr list --head "$BRANCH" --json baseRefName --jq '.[0].baseRefName' 2>/dev/null)
   TARGET="${TARGET:-development}"
   ```

2. Compute the merge base — **fail if either command errors**, since a stale ref or an empty `$BASE`
   corrupts every later phase:

   ```bash
   git fetch origin "$TARGET" || { echo "ERROR: cannot fetch origin/$TARGET"; exit 1; }
   BASE=$(git merge-base HEAD "origin/$TARGET") || { echo "ERROR: no common ancestor with origin/$TARGET"; exit 1; }
   ```

3. Note the issue number from the branch name — findings belong on that issue:

   ```bash
   ISSUE=$(git branch --show-current | sed -nE 's|^[a-z]+/([0-9]+)-.*|\1|p')
   ```

If `$ARGUMENTS` names a path, set a scope filter and apply it to **every** `git diff`, `find` and
mutation-target command in every phase:

```bash
SCOPE="src/com/voxivoid/recipelab/Recipes.java"   # example — use the actual $ARGUMENTS value
```

When scoped, append `-- "$SCOPE"` to each `git diff` and `-path "*$SCOPE*"` to each `find`. When
unscoped, omit the filter and inspect the whole branch diff. Report `$TARGET` in the Phase 5 summary.

Set up the scratchpad once:

```bash
SP="$CLAUDE_SCRATCHPAD"          # the session scratchpad named in the environment
mkdir -p "$SP/harness"/{classes,test-classes,tsrc,gen,report} ~/.cache/recipe-lab-pit
```

## Phase 1: Identify Changed Behavior

### 1a. Gather the diff

```bash
git diff $BASE..HEAD -- '*.java' '*.cpp' '*.h' '*.mk' 'tools/*.sh' build.sh build.cmd res/ AndroidManifest.xml
```

`out/`, `node_modules/`, `*.apk` and `jni/platform/` never appear in a diff; if one does, stop and say so.

### 1b. Catalog behavioral changes

Classify every hunk:

| Category | Meaning |
|---|---|
| **REMOVED** | Old behavior deleted — a recipe, a key binding, a settings write no longer happens |
| **CHANGED** | Existing behavior modified (different stored value, different label, different key) |
| **ADDED** | Net-new behavior |
| **REFACTOR** | Structure changed, observable behavior identical |
| **UNVERIFIABLE** | Real behavior change that no host-side test can reach — settings-store IDs, JNI, live preview, key scan codes, recipe values |

For each **REMOVED** or **CHANGED** entry note the file, the method or recipe, what it used to do, what
it does now, and whether a test or a camera step covers the new behavior. Produce a markdown table.

Watch for these repo-specific traps, each of which reads as a harmless diff:

- A changed **settings-store ID** (`0x0107....` in `Recipes.java` or `MainActivity.java`) writes to a
  different camera parameter. Cross-check it against the table in
  [docs/DEVELOPMENT.md](../../../docs/DEVELOPMENT.md) and say which slot it now targets.
- A changed **key scan code** silently rebinds a wheel, dial or button.
- Reordering `Recipes.ALL` breaks `GROUP_START` / `GROUP_COUNT`, which assume recipes are listed in
  group order, and shifts every stored recipe index.
- Reordering `STYLE_LABEL`, `PE_LABEL`, `PE_KEYS` or `subValues` changes the meaning of a **stored byte**.
- A change to `build.sh` without the matching change to `build.cmd` (or the reverse).
- Anything that writes a version outside `AndroidManifest.xml` — `tools/check-version.sh` fails on it.

### 1c. Flag unintentional changes

Inspect the **current assertions**, not merely whether a test or fixture file was touched. A test-file
diff does not prove intent, and a deleted or weakened test proves the opposite.

For each **REMOVED** or **CHANGED** behavior:

1. Read the harness test(s) covering that code, if any exist.
2. Check the assertions describe the **new** behavior.
3. Mark **Intentional** only when an assertion or an explicit camera step covers the new behavior.
4. Mark **Unintentional?** when nothing covers it.

Report every flagged item and ask: "These behavioral changes have no matching coverage — are they
intentional?" **Wait for the user before continuing.** They may want tests, a camera step, or a revert.

## Phase 2: New Behavior Coverage Audit

### 2a. Build the host-side harness

Everything below runs on the host JVM, out of the scratchpad, and touches nothing in the repo.

```bash
export JAVA_HOME=$HOME/toolchains/jdk17
export PATH="$JAVA_HOME/bin:$PATH"
AJ="$HOME/Android/Sdk/platforms/android-28/android.jar"
BT="$HOME/Android/Sdk/build-tools/30.0.3"
LIB=~/.cache/recipe-lab-pit                 # jar cache, outside the repo
M=https://repo1.maven.org/maven2
dl() { [ -f "$LIB/$2" ] || curl -sSL --max-time 60 -o "$LIB/$2" "$M/$1"; }
dl junit/junit/4.13.2/junit-4.13.2.jar                                   junit.jar
dl org/hamcrest/hamcrest-core/1.3/hamcrest-core-1.3.jar                  hamcrest.jar
dl org/pitest/pitest/1.15.0/pitest-1.15.0.jar                            pitest.jar
dl org/pitest/pitest-entry/1.15.0/pitest-entry-1.15.0.jar                pitest-entry.jar
dl org/pitest/pitest-command-line/1.15.0/pitest-command-line-1.15.0.jar  pitest-cli.jar
dl org/apache/commons/commons-text/1.11.0/commons-text-1.11.0.jar        commons-text.jar
dl org/apache/commons/commons-lang3/3.14.0/commons-lang3-3.14.0.jar      commons-lang3.jar
```

`commons-text` and `commons-lang3` are not optional — pitest's XML reporter dies with
`NoClassDefFoundError: org/apache/commons/text/StringEscapeUtils` without them.

Generate `R.java` (the sources reference it, and only `aapt` produces it) and compile the app classes:

```bash
"$BT/aapt" package -f -m -J "$SP/harness/gen" -M AndroidManifest.xml -S res -I "$AJ"
javac -encoding UTF-8 -cp "$AJ" -d "$SP/harness/classes" \
  "$SP/harness/gen/com/voxivoid/recipelab/R.java" src/com/voxivoid/recipelab/*.java
```

A deprecation note from `MainActivity.java` is expected; an error is not.

### 2b. List new behaviors and check coverage

From the Phase 1 catalog take the **ADDED** entries. For each, state the function, the behavior
(what input produces what output or effect) and the edge cases — boundaries, negative values, the
`?N` fallbacks, null returns, array bounds.

Then classify each as **Covered** (a harness test asserts it), **Partially covered** (happy path only),
**Uncovered**, or **Camera-only** (Phase 3 handles it). Produce a checklist:

```markdown
## New Behavior Coverage

- [x] `Recipes.evLabel` — thirds render as .0/.3/.7 → covered
- [ ] `Recipes.evLabel` — negative bias sign → NO TEST
- [ ] `MainActivity.recipeQuality` — effect recipe forces JPEG → needs extraction, see Phase 3
- [~] `MainActivity.applyPreview` — camera-only, see the matrix
```

**If behaviors are uncovered**, ask: "Want me to write tests for the uncovered behaviors?"

### 2c. Write and run tests

Test sources go in `$SP/harness/tsrc/com/voxivoid/recipelab/`, package `com.voxivoid.recipelab`, one
behavior per `@Test`, named `*Test`.

```bash
javac -encoding UTF-8 -cp "$SP/harness/classes:$LIB/junit.jar:$AJ" \
  -d "$SP/harness/test-classes" "$SP/harness/tsrc/com/voxivoid/recipelab/"*.java
java -cp "$SP/harness/classes:$SP/harness/test-classes:$LIB/junit.jar:$LIB/hamcrest.jar:$AJ" \
  org.junit.runner.JUnitCore com.voxivoid.recipelab.RecipesTest
```

Fix failures before continuing. If a test fails with `RuntimeException: Stub!`, it reached an
`android.jar` stub: that code is Android-coupled and belongs in Phase 3, not here.

Useful invariants worth asserting whenever `Recipes.java` changes:

- `GROUP_COUNT` sums to `ALL.length`, and every group has a `GROUP_START >= 0`.
- `ALL` is in group order (group index never decreases).
- Every `style` indexes `STYLE_LABEL`, every `pe` indexes `PE_LABEL`, every `sub` indexes
  `subValues(pe)` when one exists.
- `kelvin` is non-zero exactly when `wbMode == 14`.
- `summary()` renders without an exception for every recipe in `ALL`.

### 2d. Shell gates

If the branch touched `tools/*.sh`, `build.sh` or `AndroidManifest.xml`, run the real gates:

```bash
./tools/check-version.sh
./tools/check-commit-msg.sh --range "$BASE..HEAD"
```

For changed gate logic, build a fixture instead of trusting the gate against the live tree:

```bash
rm -rf "$SP/fixture" && mkdir -p "$SP/fixture/tools"
cp AndroidManifest.xml README.md "$SP/fixture/" && cp tools/*.sh "$SP/fixture/tools/"
cp -r src "$SP/fixture/"
( cd "$SP/fixture" && git init -q . && git add -A \
  && git -c user.email=t@t -c user.name=t commit -qm init && ./tools/check-version.sh )
```

The fixture needs its own git repo because `check-version.sh` calls `git describe`, and it needs
`README.md` and `src/` because the same gate greps them for a version mirror. Never mutate the real
tree to test a gate.

## Phase 3: Testability & the On-Camera Verification Matrix

This repo has no Storybook and no way to screenshot the UI from CI — it is a Canvas UI on a camera.
The equivalent deliverable is twofold: make code testable off-camera where possible, and write the
camera steps down where it is not.

### 3a. Audit Android coupling

```bash
git diff $BASE..HEAD --name-only -- 'src/com/voxivoid/recipelab/*.java'
```

For each changed method, decide which bucket it is in:

- **Pure** — no `android.*` type in its signature or body. Testable now.
- **Extractable** — decision logic wrapped in framework calls. The logic can move to a `static` method
  taking plain values, leaving the framework call a one-liner.
- **Framework-bound** — genuinely about `Canvas`, `View`, `Camera.Parameters` or key dispatch. Camera-only.

```markdown
## Testability Audit

| Method | Bucket | Note |
|---|---|---|
| `Recipes.evLabel` | Pure | Tested |
| `MainActivity.recipeQuality` | Extractable | Reads `prefs`; take `baseQuality` as a parameter |
| `MainActivity.applyPreview` | Framework-bound | `Camera.Parameters`, camera-only |
| `Legend.draw` | Framework-bound | Canvas geometry, camera-only |
```

Suggest specific extractions, but **do not refactor without the user's approval** — a refactor here is
its own issue, its own branch and its own PR under
[docs/CONTRIBUTING.md](../../../docs/CONTRIBUTING.md).

### 3b. Build the camera matrix

Group the camera steps into logical scenarios rather than one line per keystroke:

| Group by | Example |
|---|---|
| **State** | Panel default, focused row, browser open, prompt open |
| **User flow** | stage recipe → edit a row → ENTER to store → power-cycle → reopen |
| **Persistence** | every stored slot the change touches, re-read after a power cycle |
| **Edge values** | the bounds of the changed value — `sat ±16`, EV extremes, DRO off vs auto |
| **Regression** | one unrelated recipe, to prove nothing else moved |

Every group that writes to the settings store **must** end in a power cycle. A look that vanishes after
a power cycle was never stored, and that is the only way to tell.

```markdown
## On-Camera Verification Matrix

- [ ] Install: `pmca-console.py install -d native -f RecipeLab.apk`
- [ ] Browser: top dial jumps brand groups, wheel moves within a group
- [ ] Store `Classic Chrome`, confirm the panel badge turns OK
- [ ] **Power-cycle**, reopen: stored values still read back
- [ ] Live preview matches the stored look (creative style, WB, DRO)
- [ ] Regression: `FACTORY (ST)` still stages and stores
- [ ] Body and firmware recorded in the PR
```

Put the matrix in the PR test plan. If the change cannot be tested on a camera now, label the issue so
it is not lost:

```bash
gh issue edit "$ISSUE" --add-label "needs-on-camera-verification"
```

## Phase 4: Mutation Testing

A mutant that survives means no test would have caught that regression.

### 4a. Identify mutation targets

Take the changed, still-existing production Java files, excluding generated `R.java`:

```bash
git diff $BASE..HEAD --name-only --diff-filter=d -- 'src/com/voxivoid/recipelab/*.java'
```

Map each file to its class glob (`Recipes.java` → `com.voxivoid.recipelab.Recipes*`, which covers
`Recipes$Recipe`). Drop any class whose tests all hit `Stub!` — pitest cannot kill a mutant in code the
harness cannot execute. If nothing remains, **skip to Phase 4c** and report "No mutation targets — all
changed code is Android-coupled, JNI or generated." That is a finding, not a pass.

### 4b. Run pitest

```bash
CP="$LIB/pitest-cli.jar:$LIB/pitest.jar:$LIB/pitest-entry.jar:$LIB/junit.jar:$LIB/hamcrest.jar:$LIB/commons-text.jar:$LIB/commons-lang3.jar:$SP/harness/classes:$SP/harness/test-classes"
java -cp "$CP" org.pitest.mutationtest.commandline.MutationCoverageReport \
  --classPath "$SP/harness/classes,$SP/harness/test-classes,$LIB/junit.jar,$LIB/hamcrest.jar" \
  --sourceDirs "$PWD/src" \
  --targetClasses 'com.voxivoid.recipelab.Recipes*' \
  --targetTests 'com.voxivoid.recipelab.*Test' \
  --reportDir "$SP/harness/report" --outputFormats XML --timestampedReports false \
  --mutators DEFAULTS --threads 2
```

Read `$SP/harness/report/mutations.xml`. Each `<mutation>` carries `status='KILLED' | 'SURVIVED' |
'NO_COVERAGE' | 'TIMED_OUT'`, plus `mutatedMethod`, `lineNumber`, `mutator` and a `<description>`.

```bash
grep -o "status='[A-Z_]*'" "$SP/harness/report/mutations.xml" | sort | uniq -c
```

`NO_COVERAGE` is not `SURVIVED`: it means no test reaches that line at all. Report the two separately —
a large `NO_COVERAGE` count on a changed file is the strongest signal in this whole skill.

### 4c. Mutate the shell gates by hand

pitest cannot touch bash, and the gates are the only thing standing between a bad version and a
release. If the branch changed `tools/*.sh`, apply each mutant to the **fixture copy** from Phase 2d,
never to the repo, and record whether the gate still fails:

| Mutant | Gate must |
|---|---|
| `versionName` given a `-dev.1` suffix | fail |
| `versionCode` set to a wrong number | fail |
| `versionName` set to `1.1` | fail |
| A version string added to `README.md` or `MainActivity.java` | fail |
| Subject `bad subject with no type` | fail |
| Subject with an unknown scope, e.g. `feat(nope): x` | fail |
| Subject longer than 72 characters | fail |
| A valid subject, `feat(ui): add a thing` | pass |

Restore the fixture between mutants with `git checkout -q .` inside it. A mutant the gate accepts is a
hole in CI and should be reported as actionable.

### 4d. Report and classify

```markdown
## Mutation Testing Results

**Java** — score 62% (15/24 killed), 34 no-coverage, 5 survived

| File | Line | Mutator | Why it matters |
|---|---|---|---|
| Recipes.java | 77 | conditional boundary | `evLabel` sign flip unasserted — `-0.3` would render `+0.3` |

**Shell** — 7/8 mutants rejected by the gates; the over-72-character subject was accepted.
```

Classify survivors as **Actionable** (a real gap) or **Acceptable** (label text, a toast string, a
defensive branch with no observable effect). If actionable survivors exist, ask: "Want me to write
tests to kill these mutants?" Re-run pitest after writing them to confirm.

Abort mutation testing if it runs past 10 minutes, clean up per Phase 4e, and report partial results
with a suggestion to narrow the scope argument.

### 4e. Clean up — mandatory

Run this on success, failure or timeout:

1. Confirm the repo is untouched: `git status --porcelain` must be empty and `git diff --exit-code`
   must pass. That is the same check CI runs.
2. Confirm the submodule is not parked mid-build: `jni/platform/errno.h` exists and
   `jni/platform/errno.h.updater_only` does not.
3. Delete `$SP/fixture` and `$SP/harness/report`. Keep `~/.cache/recipe-lab-pit` — it is outside the
   repo and saves the download next time.
4. The harness tests live in the scratchpad and vanish with the session. If they are worth keeping, say
   so and stop: adding a `test/` tree needs its own issue and PR, and must keep `build.sh` compiling
   only `src/com/voxivoid/recipelab/*.java` so the APK is unaffected.

## Phase 5: Summary

```markdown
## Test Feature Report

**Base**: compared against `development` (merge-base `abc1234`), issue #NN

### Behavioral Changes
- X changed, Y intentional, Z flagged, W unverifiable off-camera
- [list unresolved flags]

### Coverage
- X new behaviors, Y covered by host tests, Z uncovered, W camera-only
- Shell gates: [pass/fail per gate that was run]

### Testability
- X methods audited: Y pure, Z extractable, W framework-bound
- [extraction suggestions, none applied without approval]

### Mutation Score
- Java: X% (Y/Z killed), N no-coverage, M actionable survivors
- Shell: N/M mutants correctly rejected

### Camera Verification
- Matrix written: yes/no. Executed on a body: yes/no (model + firmware)
- Power-cycle confirmed: yes/no

### Verdict
[PASS / NEEDS WORK — specific items]
```

State the camera line honestly. If the change touches recipes, settings-store IDs, live preview or key
handling and has not been run on an A6000 and power-cycled, the verdict is **NEEDS WORK** no matter what
the mutation score says.

## Important Notes

- **Never skip the Phase 1 confirmation.** Unintentional behavior changes outrank everything else here.
- **Never imply a green build validates a recipe or a store write.** It validates compilation.
- **Scope is enforced everywhere.** With a `$ARGUMENTS` path, every `git diff`, `find` and pitest target
  stays inside that subtree.
- **The repo stays clean.** No test in `src/`, no dependency in `package.json`, no APK, no keystore, no
  edit to `AndroidManifest.xml`. Everything this skill builds lives in the scratchpad or `~/.cache`.
- **Do not commit or merge from this skill.** Hand findings to `/commit-and-pr`, and leave the merge to
  a code owner.
