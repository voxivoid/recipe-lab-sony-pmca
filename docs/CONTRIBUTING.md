# Contributing

How work moves through this repo. CI enforces most of it, so reading this saves you a red build.

- [Branches](#branches)
- [Commits](#commits)
- [Pull requests](#pull-requests)
- [Issues and milestones](#issues-and-milestones)
- [Testing](#testing)
- [Releases](#releases)

## Branches

| Branch | Role |
|---|---|
| `main` | **Releases only.** Every commit on it is a release, tagged `vX.Y.Z`. Never commit here directly. |
| `development` | Default branch. Integration. Every push builds a `-dev.N` APK attached to the rolling [`dev` prerelease](https://github.com/voxivoid/recipe-lab-sony-pmca/releases/tag/dev), whose notes list every change since the last release. |
| work branches | One per issue, cut from `development`, merged back into it. |

```
main         ──●──────────────────●──────────────●──   releases only
                \                /              /
development   ───●──●──●──●──●──●──●──●──●──●──●───    integration
                    \      /   \     /
work branches    feat/12-…   fix/19-…
```

Naming — the prefix is the commit type, so the branch says what kind of change it carries:

```
feat/<issue>-<slug>        feat/123-brand-jump-top-dial
fix/<issue>-<slug>         fix/131-wb-finetune-sign
docs/ refactor/ chore/ build/ ci/ perf/ test/   same shape
hotfix/<x.y.z>             branched off main → PR to main → back-merged to development
```

Every branch except `hotfix/*` carries its issue number. There is no `release/*` branch:
**create-release** merges `development` into `main` itself.

## Commits

[Conventional Commits](https://www.conventionalcommits.org/en/v1.0.0/). Checked by the `commit-lint` job.

```
feat(browser): jump to a brand with the top dial

The picker had no fast path past 77 recipes.

Closes #123
```

**Types:** `feat` `fix` `docs` `refactor` `perf` `test` `build` `ci` `chore` `revert`.
A breaking change takes `!` after the scope and a `BREAKING CHANGE:` footer.

**Scopes** (optional — plenty of `docs:` commits carry none):

| scope | covers |
|---|---|
| `ui` | panel, chips, badges, toast, layout |
| `input` | key handling, wheel and dial, scan codes |
| `browser` | the brand/recipe picker |
| `recipes` | the recipe table itself |
| `tools` | the snapshot/diff tool, `tools/` scripts |
| `build` | `build.cmd`, `build.sh`, NDK, toolchain |
| `ci` | `.github/workflows` |
| `docs` | documentation |
| `deps` | the `jni/platform` submodule |
| `release` | `chore(release): x.y.z` only |

**Issue linkage — every commit carries its issue.** Put it in a footer, never in the subject:

- `Closes #123` when the commit finishes the issue. On a branch with several commits, the last one closes.
- `Refs #123` when it is one step of several.

The number lives in the branch name, so you never have to look it up:

```bash
git branch --show-current | sed -nE 's|^[a-z]+/([0-9]+)-.*|\1|p'
```

`commit-lint` warns (it does not fail) about any commit on the branch that omits the reference — release
commits and back-merges legitimately have none.

Keep it out of the subject: GitHub appends the **PR** number there automatically on squash merge, so the
commit on `development` reads `feat(browser): jump to a brand with the top dial (#45)`. Two bare `#N` in one
subject would be ambiguous, since issues and PRs share a number space.

## Pull requests

Working in Claude Code? `/commit-and-pr` walks the whole flow below — branch, gates, commit
message, push, PR — and stops before merging.

**The PR title becomes the commit message — and the release.** A squash merge leaves only the title, so it
is the string semantic-release reads to decide the next version. A PR titled `chore:` releases nothing
however large its diff; `fix:` makes a patch, `feat:` a minor, `!` a major. The `pr-title` check exists for
exactly this reason.

- work branch → `development`: **squash merge**. One commit per issue; your WIP never surfaces.
- `development` → `main`: **merge commit**, never squash. Squashing would put a commit on `main` that is not
  on `development` and the branches would diverge permanently.

**Every PR needs an approving review from a code owner** ([.github/CODEOWNERS](../.github/CODEOWNERS))
before it can merge, and review threads must be resolved.

Required checks: `build`, `test`, `version-consistency`, `commit-lint`, `pr-title`. They are **strict**: the checks
have to have run with `development` at its current tip, so a PR that has fallen behind cannot merge until it
is brought up to date. Rebase it — that keeps the branch a clean series on top of `development` and keeps the
squashed commit honest:

```sh
git fetch origin
git rebase origin/development
git push --force-with-lease
```

GitHub's **Update branch** button does the same job by merging `development` in; it is fine when a rebase
would be painful, since the merge only ever squashes down to one commit anyway.

> GitHub does not let you approve your own pull request. While `@voxivoid` is the only code
> owner, their own PRs cannot be approved by anyone else and have to be merged using the
> repository-admin bypass. Adding a second code owner is what makes the rule bite.

## Issues and milestones

- Every unit of work gets an issue before a branch.
- **Milestones are versions** (`v1.1.0`, `v1.2.0`) plus a permanent `Backlog`. The release workflow closes a
  milestone when its tag ships.
- Labels: `type: …` mirrors the commit type, `scope: …` mirrors the commit scope, plus `priority: p1|p2|p3`
  and `status: blocked|needs-triage`.
- **`needs-on-camera-verification`** is the important one. Nothing about recipes, settings-store IDs or live
  preview can be validated by CI. A green build is not evidence the change works.

## Testing

`./tools/test.sh` runs the unit tests — the `test` CI job, and the first step of every `development` build
and of a release — against a bare JDK 17 in a few seconds. They cover
what the app decides without the camera: the recipe table, how each value is encoded in the settings store, the
bytes ENTER writes, the live-preview parameters, chip navigation and the overlay text
([details](DEVELOPMENT.md#unit-tests)). Logic of that kind goes into `Params.java` or `Recipes.java` with a
test next to it; `MainActivity` and the views cannot be tested off the camera.

### On the camera

A green build only proves it compiles, and a green `test` only that the app would write the bytes it means to
— not that the camera means the same thing by them. Before asking for a merge:

1. Grab the APK — your PR's workflow artifact, or `RecipeLab-dev.apk` from the `dev` prerelease.
2. Install it: `pmca-console.py install -d native -f RecipeLab.apk` (or PMCA-GUI → Install App).
3. Exercise the change, then **power-cycle the camera** and confirm the setting survived. The store is
   written through the backup driver; a look that vanishes after a power cycle was never really stored.
4. Say in the PR what you tested and on which body/firmware.

Never commit an APK or a keystore. Both are gitignored; releases carry the binaries.

## Releases

**Actions → create-release → Run workflow** (`-f dry_run=true` to just see what would ship). semantic-release
reads the commits, decides the version, builds, tags and publishes; the workflow merges `development` into
`main` around it and fast-forwards back. Nobody picks a version number.
Details: **[RELEASING.md](RELEASING.md)**.

Handy aliases:

```bash
git config alias.releases 'log --first-parent --oneline main'
git config alias.since-release '!git log $(git describe --tags --abbrev=0)..HEAD --no-merges --oneline'
```
