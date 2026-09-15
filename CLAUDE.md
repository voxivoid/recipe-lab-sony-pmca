# Recipe Lab — agent rules

A PlayMemories (PMCA) camera app for the Sony A6000: 77 film-look recipes written straight into the
camera's settings store. Native lib (ndk-build, NDK r16b) + Java, no Gradle.

Read [docs/CONTRIBUTING.md](docs/CONTRIBUTING.md) and [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md) before changing anything.
The rules below are the ones that break things when ignored.

## Branching

- **Never commit to `main`.** `main` is releases only; every commit on it is tagged.
- Branch off `development`: `feat/<issue>-<slug>` or `feat/<slug>`, likewise `fix/`, and also
  `docs/ refactor/ chore/ build/ ci/ perf/ test/`.
- A GitHub issue is not required. If one exists, put its number in the branch name; otherwise drop the
  number and use `<type>/<slug>`. Never open an issue just to have one, and never invent a number.
- **Never open a PR unless asked.** Finish the change, commit, and stop there — pushing the branch
  and opening the PR is the user's call. `/commit-and-pr` is that ask; so is "open a PR".
- **Never merge a PR yourself.** Every PR needs an approving review from a code owner; open it
  and leave the merge to a human.
- **Rebase onto `development` before asking for a merge.** Required checks are strict: a PR whose base
  has moved on is not mergeable until its checks have run against the tip. `git fetch origin && git rebase
  origin/development`, then `git push --force-with-lease`.
- `hotfix/<x.y.z>` off `main`. There is no `release/*` branch — **create-release** merges `development`
  into `main` itself.

## Commits

```
type(scope): subject

Closes #123
```

- **A commit references its issue when there is one**, in a footer: `Closes #N` when it finishes the
  issue, `Refs #N` when it is one step of several. The number comes from the branch name. No issue → no
  footer; do not guess a number.

- Types: `feat fix docs refactor perf test build ci chore revert`.
- Scopes: `ui input browser recipes tools build ci docs deps release`. Optional.
- **Do not write the issue number in the subject.** It goes in the `Closes #N` footer and the branch name;
  GitHub appends the PR number to the subject itself on squash merge.
- Subject ≤ 72 chars, imperative, no trailing period.
- **The PR title decides the release.** A squash merge leaves only the title, so it is what semantic-release
  reads: `fix:` → patch, `feat:` → minor, `!` → major, `chore:`/`docs:` → no release at all.

## Versions

- `AndroidManifest.xml` `android:versionName` is the **only** version in the tree, and holds the *next target
  release* (`X.Y.Z`, never a `-dev` suffix).
- **Never hand-edit a version, and never pick one.** semantic-release derives it from the commits and calls
  `tools/bump-version.sh` itself.
- Never add a version string to `README.md`, `MainActivity.java` or anywhere else —
  `tools/check-version.sh` fails the build if one reappears.
- `versionCode = MAJOR*10_000_000 + MINOR*100_000 + PATCH*1_000 + P`, `P=999` for a release, `P=N` for a
  dev build. A build derives this itself; it never rewrites the checked-in manifest.

## Never commit

- APKs (`dist/` no longer holds one — releases carry the binaries)
- keystores, or anything decoded from `ANDROID_KEYSTORE_B64`
- `out/`, `jni/platform/errno.h.updater_only`

## Building

- Windows: `build.cmd`. Linux/WSL/CI: `./build.sh` (needs `ANDROID_NDK` pointing at **r16b** — later NDKs
  cannot build this target).
- Keep `build.cmd` and `build.sh` in step. A change to one needs the same change in the other.
- The `errno.h` park must stay reversible (`build.sh` does it from an `EXIT` trap). A build that leaves the
  submodule dirty is a bug.
- `./tools/test.sh` runs the unit tests: the `test` CI job, JDK 17 only, no SDK. Logic that needs no camera
  goes in `Params.java` or `Recipes.java` **with a test**, never into `MainActivity`. Both are compiled there
  **without** `android.jar`, so an `android.*` import in either breaks the job.

## What CI cannot check

Nothing about recipes, settings-store IDs, live preview or key handling can be validated by a build. The unit
tests prove which bytes and parameters the app *sends*, not what the camera *does* with them. Those changes
need a real A6000, exercised **and power-cycled** — a look that vanishes after a power cycle was never stored.
Say so plainly rather than implying a green build or a green `test` means the change works.

## Releasing

Run the **create-release** workflow (`gh workflow run create-release.yml`, or `-f dry_run=true` to preview). Do not merge
`development` into `main` by hand unless that workflow is broken — and never squash it if you do.
Full runbook in [docs/RELEASING.md](docs/RELEASING.md).
