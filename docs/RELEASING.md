# Releasing

The version is not a decision any more — it is computed from the commit messages by
[semantic-release](https://semantic-release.gitbook.io/). One button does the rest.

A release is a **tag on `main`**. There is no release branch and nothing gets merged: the
workflow ships whatever is on `main` when you run it. Releasing is a decision about *timing*.

## The button

Write the notes first: `release-notes/next.md` is the top of the release (see [Release notes](#release-notes)). The
`/create-release` skill in Claude Code checks it, then runs the steps below.

**Actions → create-release → Run workflow**, or:

```bash
gh workflow run create-release.yml                    # release
gh workflow run create-release.yml -f dry_run=true    # just report what would ship
```

| input | meaning |
|---|---|
| `dry_run` | Report the version that would be released and change nothing. |
| `allow_failing_checks` | Release even though the last `main` build failed. |

It refuses if the last build failed (that build runs the unit tests first), or if no commit
since the last tag carries a releasable type. Then it runs the unit tests once more, and
semantic-release decides the version, writes `AndroidManifest.xml` via `tools/bump-version.sh`,
moves `release-notes/next.md` to `vX.Y.Z.md`, builds the APK, tags, publishes the release and commits the manifest bump back to `main`.
Finally the rolling `dev` prerelease is rebuilt so its tag follows the new tip.

## Release notes

A release's body starts with notes written for camera owners, with the generated commit list folded below them
(`<details>`). The notes live in `release-notes/`:

- `release-notes/next.md` holds the notes of the next release. The `/release-notes` skill drafts it from the commits
  since the last tag, and every commit keeps it true (`CLAUDE.md`). The rolling `dev` prerelease shows it too.
- At release, `.releaserc.js` puts it on top of the body (two small `@semantic-release/exec` steps around the notes
  generator open and close the fold) and moves it to `release-notes/vX.Y.Z.md` in the `chore(release):` commit.
- No `next.md`: the body is the generated commit list alone, as before.

Plain text, one heading line per group (`New`, `Changed`, `Fixed`, `Please try`) and `- ` lines under it;
`tools/release-notes.mjs` turns the headings into markdown. To fix the notes of a release already out, change
`release-notes/vX.Y.Z.md` on `main`, then:

```bash
node tools/release-notes.mjs github vX.Y.Z                 # the body it would get; the commit list is kept
node tools/release-notes.mjs github vX.Y.Z > body.md && gh release edit vX.Y.Z --notes-file body.md
```

## What decides the version

| commit type | bump |
|---|---|
| `fix:` `perf:` `refactor:` | patch |
| `feat:` | minor |
| any type with `!`, or a `BREAKING CHANGE:` footer | major |
| `docs:` `chore:` `ci:` `test:` `build:` | nothing |

**A squash merge leaves only the PR title**, so the PR title is what semantic-release
reads. A PR titled `chore:` contributes nothing releasable however large its diff — which
is why `pr-title` is a check. Get the type right on the PR, not on the commits inside it.

**Still verify on a camera.** A green build says it compiles. Install the published APK
over the previous version — it must succeed *without uninstalling*, which is what proves
the signing key is unchanged.

## Doing it by hand

If the workflow is broken:

```bash
git switch main && git pull
./tools/test.sh
GITHUB_TOKEN=$(gh auth token) npx semantic-release
```

`semantic-release` reads the branch from `GITHUB_REF` when it detects GitHub Actions. Running
it locally there is no such variable, so it uses the checked-out branch — which is why the
command above works as written, and why the workflow has to `export GITHUB_REF=refs/heads/main`
before its own invocation.

## Hotfix

There is no hotfix branch. Cut an ordinary `fix/` branch off `main`, PR it with a `fix:` title,
squash-merge it, and run **create-release**. The `fix:` title is what makes it a patch release.

## If something goes wrong

- **Nothing was released.** No commit since the last tag carried a releasable type — most often a PR
  titled `chore:` or `docs:`. Land a `fix:` or `feat:` PR, or merge with a corrected title.
- **The manifest and the tag disagree.** `version-consistency` fails if the manifest falls behind the last
  release, which means the `chore(release):` commit did not land. Re-run the release; semantic-release is
  idempotent about a version it has already published.
- **A release was published with a bad APK.** Fix forward with a patch release. Do not move a tag — the
  ruleset blocks it, and anyone who already downloaded has the old bytes.
