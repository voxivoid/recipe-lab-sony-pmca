# Releasing

The version is not a decision any more — it is computed from the commit messages by
[semantic-release](https://semantic-release.gitbook.io/). One button does the rest.

## The button

**Actions → create-release → Run workflow**, or:

```bash
gh workflow run create-release.yml                    # release
gh workflow run create-release.yml -f dry_run=true    # just report what would ship
```

| input | meaning |
|---|---|
| `dry_run` | Report the version that would be released and change nothing. |
| `allow_failing_checks` | Release even though the last `development` build failed. |

It refuses if `development` has nothing `main` lacks, if the last development build
failed (that build runs the unit tests first), or if no commit since the last tag carries
a releasable type. Then it merges `development` into `main` with a **merge commit**, runs
the unit tests once more on the merged tree before pushing, and semantic-release decides the
version, writes `AndroidManifest.xml` via `tools/bump-version.sh`, builds the APK, tags,
publishes the release and commits the manifest back. Finally `development` is
fast-forwarded onto `main` and the rolling `dev` prerelease is rebuilt.

## What decides the version

| commit type | bump |
|---|---|
| `fix:` `perf:` `refactor:` | patch |
| `feat:` | minor |
| any type with `!`, or a `BREAKING CHANGE:` footer | major |
| `docs:` `chore:` `ci:` `test:` `build:` | nothing |

**A squash merge leaves only the PR title**, so the PR title is what semantic-release
reads. A PR titled `chore:` contributes nothing releasable however large its diff — which
is why `pr-title` is a required check. Get the type right on the PR, not on the commits
inside it.

**Still verify on a camera.** A green build says it compiles. Install the published APK
over the previous version — it must succeed *without uninstalling*, which is what proves
the signing key is unchanged.

## Doing it by hand

If the workflow is broken:

```bash
git switch main && git pull
git merge --no-ff development -m "chore(release): merge development"
./tools/test.sh
git push origin main
GITHUB_TOKEN=$(gh auth token) npx semantic-release
git switch development && git merge --ff-only main && git push
```

## Hotfix

```bash
git switch -c hotfix/1.1.1 main
# fix, PR → main titled "fix: ...", squash-merge it, then run semantic-release on main:
GITHUB_TOKEN=$(gh auth token) npx semantic-release
git switch development && git merge --no-ff main && git push
```

The `fix:` title is what makes it a patch release. The back-merge is `--no-ff`, not
`--ff-only`: `development` has moved on by then.

## If something goes wrong

- **Nothing was released.** No commit since the last tag carried a releasable type — most often a PR
  titled `chore:` or `docs:`. Land a `fix:` or `feat:` PR, or merge with a corrected title.
- **The manifest and the tag disagree.** `version-consistency` fails if the manifest falls behind the last
  release, which means a release commit did not land. Re-run the release; semantic-release is idempotent
  about a version it has already published.
- **A release was published with a bad APK.** Fix forward with a patch release. Do not move a tag — the
  ruleset blocks it, and anyone who already downloaded has the old bytes.
