---
name: create-release
description: Cuts a Recipe Lab release - checks main is ready, writes and gets approval for the release notes (/release-notes), lands them on main through a PR, runs the create-release GitHub Action and reports the release. Use when the user asks to release, cut a release, or run create-release.
argument-hint: "[--dry-run to only report the version that would be released]"
disable-model-invocation: true
---

# Create a release

The CI workflow (`.github/workflows/create-release.yml`, see `docs/RELEASING.md`) does the version, build, tag and
GitHub release. This skill prepares `main` for it. Arguments: $ARGUMENTS

Ask the user before each step marked **confirm**. Never skip the notes.

## Steps
1. **Preflight**
   - `git fetch origin`; `git status --short` is clean (otherwise stop and say what is dirty).
   - `gh run list --workflow dev-build.yml --branch main --limit 1`: the latest build of `main` passed. If not, stop.
   - Releasable commits since the last tag (`git log $(git describe --tags --abbrev=0 --match 'v*')..origin/main
     --format=%s`): at least one `feat`, `fix`, `perf`, `refactor` or `!`. If none, say there is nothing to release
     and stop.
2. **Notes**: if `release-notes/next.md` on `origin/main` is missing or out of date, run the `/release-notes` skill;
   the user approves it.
3. **Land the notes** (**confirm**), only if they changed: `main` takes no direct commits, so branch
   `docs/release-notes` off `origin/main`, commit `docs(release): notes for the next release` (a `docs` commit
   releases nothing by itself), push, and open the PR. The user merges it; wait for that before going on.
4. **Dry run**: `gh workflow run create-release.yml -f dry_run=true`, watch it (`gh run watch`), report the version it
   would release. With `--dry-run` in the arguments, stop here.
5. **Release** (**confirm**, shows the version): `gh workflow run create-release.yml`, find the run
   (`gh run list --workflow create-release.yml --limit 1`), `gh run watch <id> --exit-status`.
6. **Report**: the tag and release URL (`gh release view --json url`), and that the release body starts with the
   notes. Remind the user that nothing about recipes or keys is proven until the published APK is installed over the
   previous one on a camera and power-cycled.

## Rules
- Never force-push `main`, never edit `AndroidManifest.xml`'s version (semantic-release owns it), never tag by hand.
- The notes commit is the only commit this skill makes.
